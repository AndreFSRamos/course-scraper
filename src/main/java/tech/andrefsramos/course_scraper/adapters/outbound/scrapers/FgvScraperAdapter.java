package tech.andrefsramos.course_scraper.adapters.outbound.scrapers;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.andrefsramos.course_scraper.adapters.outbound.http.HttpFetch;
import tech.andrefsramos.course_scraper.core.domain.Course;
import tech.andrefsramos.course_scraper.core.domain.Platform;
import tech.andrefsramos.course_scraper.core.ports.ScraperPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/*
 * Finalidade

 * Extrair cursos listados em “Cursos Gratuitos” da FGV Educação Executiva
 * (zero-based): https://educacao-executiva.fgv.br/cursos/gratuitos?page=N

 * Como funciona

 * - Determina a base (Platform.baseUrl ou DEFAULT_BASE).
 * - Página inicial: page=0 (zero-based).
 * - Detecta a última página pelo paginador (ul.pagination a[href*="page="]); usa cap se não detectar.
 * - Em cada página:
 *   * Seleciona anchors que apontam para detalhe de curso: a[href^="/cursos/online/"] (dentro de <main>, com fallback).
 *   * Filtra apenas “rotas de detalhe” (>= 4 segmentos após "/cursos/online/").
 *   * Deduplica por URL absoluta; tenta recuperar título de elementos pais (h2/h3) se o <a> tiver texto curto.
 * - Constrói Course com:
 *   * provider="FGV", freeFlag=true (a listagem é “Gratuitos”), statusText/priceText vazios.
 */

@Component
public class FgvScraperAdapter implements ScraperPort {
    private static final Logger log = LoggerFactory.getLogger(FgvScraperAdapter.class);
    private static final String DEFAULT_BASE = "https://educacao-executiva.fgv.br";
    private static final String LIST_PATH   = "/cursos/gratuitos?page=";
    private static final int TIMEOUT_MS       = 25_000;
    private static final int FETCH_RETRIES    = 2;
    private static final long FETCH_BACKOFFMS = 400;
    private static final int PAGE_CAP_MIN = 5;
    private static final int PAGE_CAP_MAX = 200;
    private static final int ITEM_CAP_HARD = 10_000;
    private static final long PAGE_SLEEP_MS = 220;
    private static final String UA_BROWSER =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    @Override
    public boolean supports(Platform p) {
        return p != null && "fgv".equalsIgnoreCase(p.name());
    }

    @Override
    public List<Course> fetchBatch(Platform platform, int maxPages) {
        final long t0 = System.nanoTime();

        final String base = (platform != null && platform.baseUrl() != null && !platform.baseUrl().isBlank())
                ? platform.baseUrl().trim() : DEFAULT_BASE;

        final int startPage = 0;
        final int pageCap = Math.min(Math.max(maxPages, PAGE_CAP_MIN), PAGE_CAP_MAX);
        final int itemCap = ITEM_CAP_HARD;

        log.info("FGV: início da coleta base={} startPage={} pageCap={} itemCap={}", base, startPage, pageCap, itemCap);

        final String p0 = base + LIST_PATH + startPage;
        Document first = getDoc(p0);
        if (first == null) {
            log.warn("FGV: primeira página nula url={}", p0);
            return List.of();
        }

        int last = detectLastPage(first);
        if (last < 0) {
            last = startPage + pageCap - 1;
            log.debug("FGV: última página não detectada; usando fallback last={}", last);
        } else {
            last = Math.min(last, startPage + pageCap - 1);
            log.debug("FGV: última página detectada last={} (após cap)", last);
        }

        // 3) extrai anchors de detalhe página a página
        final Map<String, String> seen = new LinkedHashMap<>();
        String stopReason = "OK";

        for (int page = startPage; page <= last; page++) {
            final String url = base + LIST_PATH + page;
            final Document doc = (page == startPage) ? first : getDoc(url);

            if (doc == null) {
                log.warn("FGV: doc nulo page={} url={} (parando)", page, url);
                stopReason = "DOC_NULL";
                break;
            }

            Elements anchors = doc.select("main a[href^=\"/cursos/online/\"]");
            if (anchors.isEmpty()) {
                anchors = doc.select("a[href^=\"/cursos/online/\"]");
            }

            final int anchorsTot = anchors.size();
            if (anchorsTot == 0) {
                log.warn("FGV: page={} sem anchors '/cursos/online/'. Encerrando varredura.", page);
                stopReason = "NO_ANCHORS";
                break;
            }

            int add = 0;
            for (Element a : anchors) {
                String href = sanit(a.attr("href"));
                String title = sanit(a.text());
                if (href.isBlank()) continue;

                String abs = absUrl(base, href);

                if (!isDetailCoursePath(href)) continue;

                if (title.length() < 5) {
                    Element h3 = a.parents().select("h3").first();
                    Element h2 = a.parents().select("h2").first();
                    if (h3 != null && sanit(h3.text()).length() > 5) title = sanit(h3.text());
                    else if (h2 != null && sanit(h2.text()).length() > 5) title = sanit(h2.text());
                }
                if (title.isBlank()) continue;

                if (seen.putIfAbsent(abs, title) == null) {
                    add++;
                }
                if (seen.size() >= itemCap) {
                    stopReason = "ITEM_CAP";
                    break;
                }
            }

            final String pageTitle = sanit(doc.title());
            log.info("FGV: page={} added={} totalSoFar={} (anchorsTot={}) title=\"{}\"",
                    page, add, seen.size(), anchorsTot, pageTitle);

            if ("ITEM_CAP".equals(stopReason)) {
                log.warn("FGV: atingiu itemCap={} — interrompendo.", itemCap);
                break;
            }

            if (add == 0 && page > startPage) {
                stopReason = "ADDED_ZERO";
                break;
            }

            try {
                Thread.sleep(PAGE_SLEEP_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                stopReason = "INTERRUPTED";
                log.warn("FGV: loop interrompido por Thread.interrupt().");
                break;
            }
        }

        final List<Course> out = new ArrayList<>(seen.size());
        seen.forEach((href, title) -> out.add(new Course(
                null, null,
                sha256(title + "|" + href),
                title,
                href,
                "FGV",
                mapArea(title),
                true,
                null, null,
                "", "",
                null, null
        )));

        final long tookMs = (System.nanoTime() - t0) / 1_000_000;
        if (out.isEmpty()) {
            log.warn("FGV: nenhum curso extraído. stopReason={} tookMs={}ms", stopReason, tookMs);
        } else {
            log.info("FGV: total extraído={} stopReason={} tookMs={}ms (zeroBased=true, startPage={}, lastPage={})",
                    out.size(), stopReason, tookMs, startPage, last);
        }
        return out;
    }

    private Document getDoc(String url) {
        try {
            Document doc = HttpFetch.get(url, TIMEOUT_MS, FETCH_RETRIES, FETCH_BACKOFFMS);
            if (doc != null) return doc;
        } catch (Throwable t) {
            log.warn("FGV: HttpFetch falhou url={} - {}", url, t.toString());
        }

        try {
            return Jsoup.connect(url)
                    .userAgent(UA_BROWSER)
                    .referrer("https://www.google.com")
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
                    .get();
        } catch (Exception e) {
            log.warn("FGV: fallback Jsoup falhou url={} - {}", url, e.toString());
            return null;
        }
    }

    private static int detectLastPage(Document doc) {
        int max = -1;
        if (doc == null) return max;
        for (Element a : doc.select("ul.pagination a[href*=\"page=\"]")) {
            String h = a.attr("href");
            int idx = h.lastIndexOf("page=");
            if (idx < 0) continue;
            String tail = h.substring(idx + 5);
            int amp = tail.indexOf('&');
            String num = (amp >= 0) ? tail.substring(0, amp) : tail;
            try {
                int p = Integer.parseInt(num.replaceAll("\\D+", ""));
                max = Math.max(max, p);
            } catch (Exception ignored) { /* segue */ }
        }
        return max;
    }

    private static boolean isDetailCoursePath(String href) {
        if (href == null || href.isBlank()) return false;
        if (!href.startsWith("/cursos/online/")) return false;
        String[] parts = href.split("/");
        return parts.length >= 5;
    }

    private static String sanit(String s) {
        return s == null ? "" : s.trim();
    }

    private static String absUrl(String base, String href) {
        if (href == null || href.isBlank()) return "";
        if (href.startsWith("http")) return href;
        if (!href.startsWith("/")) href = "/" + href;
        return base + href;
    }

    private static String mapArea(String t) {
        t = t == null ? "" : t.toLowerCase(Locale.ROOT);
        if (t.contains("dados") || t.contains("ciência de dados") || t.contains("inteligência artificial") || t.contains("ia"))
            return "Dados & IA";
        if (t.contains("finan")) return "Finanças & Contabilidade";
        if (t.contains("gest") || t.contains("administra") || t.contains("executiva")) return "Gestão & Negócios";
        if (t.contains("marketing") || t.contains("vendas")) return "Marketing & Vendas";
        if (t.contains("direito")) return "Direito";
        if (t.contains("tecnolog")) return "Tecnologia";
        return null;
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
