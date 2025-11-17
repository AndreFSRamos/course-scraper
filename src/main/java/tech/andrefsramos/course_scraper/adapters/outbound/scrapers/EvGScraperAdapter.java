package tech.andrefsramos.course_scraper.adapters.outbound.scrapers;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.cache.annotation.Cacheable;
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

 * Extrair cursos do catálogo da Escola Virtual de Governo (EVG).

 * Como funciona

 * - Determina a URL base (platform.baseUrl ou https://www.escolavirtual.gov.br).
 * - Heurística de paginação 0/1-based:
 *   * Tenta carregar page=1; se não houver cards, tenta page=0.
 * - Detecta a última página pelo paginador (ul.pagination a[href*='page=']) e aplica cap.
 * - Para cada página:
 *   * Seleciona cards por um conjunto de seletores amplos (".card", "article.card", "a[href*='/curso/']").
 *   * Coleta título + href absolutos e deduplica por URL.
 *   * Respeita limites de páginas/itens e aplica pequeno atraso entre páginas (backoff leve).
 * - Constrói objetos Course assumindo:
 *   * freeFlag=true (catálogo EVG geralmente gratuito);
 *   * statusText="Online (EAD)";
 *   * provider="EVG";
 *   * priceText vazio (não exibir no Discord).
 */

@Component
public class EvGScraperAdapter implements ScraperPort {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EvGScraperAdapter.class);

    private static final int DEFAULT_TIMEOUT_MS = 25_000;
    private static final int DEFAULT_RETRIES = 2;
    private static final long DEFAULT_BACKOFF_MS = 600;
    private static final long PAGE_SLEEP_MS = 180;
    private static final int PAGE_CAP_MIN = 5;
    private static final int PAGE_CAP_MAX = 200;
    private static final int ITEM_CAP_HARD = 10_000;

    private static final String CARD_SELECTORS = ".card, .resultado-cursos .card, article.card, a[href*=\"/curso/\"]";

    @Cacheable(cacheNames = "evgPageHtml")
    public Document fetchPageHtml(String url) {
        return HttpFetch.get(url, DEFAULT_TIMEOUT_MS, DEFAULT_RETRIES, DEFAULT_BACKOFF_MS);
    }

    @Override
    public boolean supports(Platform p) {
        return p != null && "evg".equalsIgnoreCase(p.name());
    }

    @Override
    public List<Course> fetchBatch(Platform platform, int maxPages) {
        final long t0 = System.nanoTime();

        final String base = (platform != null && platform.baseUrl() != null && !platform.baseUrl().isBlank())
                ? platform.baseUrl()
                : "https://www.escolavirtual.gov.br";
        final String path = "/catalogo?page=";

        final int pageCap = Math.min(Math.max(maxPages, PAGE_CAP_MIN), PAGE_CAP_MAX);
        final int itemCap = ITEM_CAP_HARD;

        log.info("EVG: início da coleta base={} pageCap={} itemCap={}", base, pageCap, itemCap);

        final Map<String, String> seen = new LinkedHashMap<>();
        int startPage = 1;

        Document first = fetchPageHtml(base + path + startPage);
        boolean cardOnFirst = hasCards(first);
        if (!cardOnFirst) {
            log.warn("EVG: nenhum card em page=1 — tentando paginação 0-based.");
            startPage = 0;
            first = HttpFetch.get(base + path + startPage, DEFAULT_TIMEOUT_MS, DEFAULT_RETRIES, DEFAULT_BACKOFF_MS);
        }
        if (first == null) {
            log.warn("EVG: documento nulo nas páginas {} e {}. Abortando coleta.", 1, 0);
            return List.of();
        }

        int last = detectLastPage(first);
        if (last < 0) {
            last = startPage + pageCap - 1;
            log.debug("EVG: última página não detectada; usando fallback last={}", last);
        } else {
            last = Math.min(last, startPage + pageCap - 1);
            log.debug("EVG: última página detectada last={} (após cap)", last);
        }

        int totalAdded = 0;
        String stopReason = "OK";
        for (int page = startPage; page <= last; page++) {
            final String url = base + path + page;
            final Document doc = (page == startPage)
                    ? first
                    : fetchPageHtml(url);

            if (doc == null) {
                log.warn("EVG: doc nulo page={} url={}. Encerrando.", page, url);
                stopReason = "DOC_NULL";
                break;
            }

            final Elements cards = doc.select(CARD_SELECTORS);
            if (cards.isEmpty()) {
                log.info("EVG: page={} sem cards. Encerrando varredura.", page);
                stopReason = "NO_CARDS";
                break;
            }

            int addedThisPage = 0;
            for (Element c : cards) {
                Element a = "a".equalsIgnoreCase(c.tagName()) ? c : c.selectFirst("a[href]");
                if (a == null) continue;

                final String href = HttpFetch.absUrl(base, a.attr("href"));
                final String title = HttpFetch.sanit(a.text());
                if (href.isBlank() || title.isBlank()) continue;

                if (seen.putIfAbsent(href, title) == null) {
                    addedThisPage++;
                    totalAdded++;
                }
                if (seen.size() >= itemCap) {
                    stopReason = "ITEM_CAP";
                    break;
                }
            }

            log.info("EVG: page={} added={} totalSoFar={}", page, addedThisPage, seen.size());

            if (addedThisPage == 0) {
                stopReason = "ADDED_ZERO";
                break;
            }
            if (seen.size() >= itemCap) {
                log.warn("EVG: atingiu itemCap={} — interrompendo.", itemCap);
                break;
            }

            try {
                Thread.sleep(PAGE_SLEEP_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                stopReason = "INTERRUPTED";
                log.warn("EVG: loop interrompido por Thread.interrupt().");
                break;
            }
        }

        final List<Course> out = new ArrayList<>(seen.size());
        seen.forEach((href, title) -> out.add(new Course(
                null,
                null,
                sha256(title + "|" + href),
                title,
                href,
                "EVG",
                mapArea(title),
                true,
                null,
                null,
                "Online (EAD)",
                "",
                null,
                null
        )));

        final long tookMs = (System.nanoTime() - t0) / 1_000_000;
        if (out.isEmpty()) {
            log.warn("EVG: nenhum curso extraído. stopReason={} tookMs={}", stopReason, tookMs);
        } else {
            log.info("EVG: total extraído={} stopReason={} tookMs={}ms", out.size(), stopReason, tookMs);
        }

        return out;
    }

    private static boolean hasCards(Document d) {
        try {
            return d != null && !d.select(CARD_SELECTORS).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static int detectLastPage(Document doc) {
        int max = -1;
        if (doc == null) return max;

        for (Element a : doc.select("ul.pagination a[href*=\"page=\"]")) {
            final String h = a.attr("href");
            final int idx = h.lastIndexOf("page=");
            if (idx < 0) continue;
            try {
                final String tail = h.substring(idx + 5);
                final int amp = tail.indexOf('&');
                final String num = (amp >= 0) ? tail.substring(0, amp) : tail;
                final String onlyDigits = num.replaceAll("\\D+", "");
                if (!onlyDigits.isEmpty()) {
                    max = Math.max(max, Integer.parseInt(onlyDigits));
                }
            } catch (Exception err) {
                log.error("EVG: [detectLastPage]:[{}]", err.getMessage());
            }
        }
        return max;
    }

    private static String mapArea(String t) {
        t = t == null ? "" : t.toLowerCase(Locale.ROOT);
        if (t.contains("dados") || t.contains("ia")) return "Dados & IA";
        if (t.contains("finan")) return "Finanças & Contabilidade";
        if (t.contains("gest") || t.contains("empreend")) return "Gestão & Negócios";
        if (t.contains("marketing") || t.contains("vendas")) return "Marketing & Vendas";
        if (t.contains("saúde")) return "Saúde & Bem-estar";
        if (t.contains("tecnolog")) return "Tecnologia";
        return null;
    }

    private static String sha256(String s) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
