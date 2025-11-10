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

/**
 * Scraper FGV - Cursos Gratuitos (online)
 * Lista em https://educacao-executiva.fgv.br/cursos/gratuitos?page=N (N = 0,1,2,...)
 */
@Component
public class FgvScraperAdapter implements ScraperPort {

    private static final Logger log = LoggerFactory.getLogger(FgvScraperAdapter.class);

    private static final String DEFAULT_BASE = "https://educacao-executiva.fgv.br";
    private static final String LIST_PATH   = "/cursos/gratuitos?page="; // zero-based
    private static final int TIMEOUT_MS     = 25000;

    // UA de fallback quando HttpFetch não atender
    private static final String UA_BROWSER =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    @Override
    public boolean supports(Platform p) {
        return p != null && "fgv".equalsIgnoreCase(p.name());
    }

    @Override
    public List<Course> fetchBatch(Platform platform, int maxPages) {
        final String base = (platform != null && platform.baseUrl() != null && !platform.baseUrl().isBlank())
                ? platform.baseUrl().trim() : DEFAULT_BASE;

        // paginação zero-based
        final int startPage   = 0;
        final int hardPageCap = Math.min(Math.max(maxPages, 5), 200); // segurança

        // 1) primeira página
        String p0 = base + LIST_PATH + startPage;
        Document first = getDoc(p0);
        if (first == null) {
            log.warn("FGV: primeira página nula url={}", p0);
            return List.of();
        }

        // 2) tenta detectar última página pelo componente de paginação (max page=)
        int last = detectLastPage(first);
        if (last < 0) {
            // não achou paginação — usa teto de segurança
            last = startPage + hardPageCap - 1;
        } else {
            // respeita teto adicional
            last = Math.min(last, startPage + hardPageCap - 1);
        }

        // 3) extrai anchors de detalhe "/cursos/online/..." página a página
        Map<String, String> seen = new LinkedHashMap<>();
        for (int page = startPage; page <= last; page++) {
            String url = base + LIST_PATH + page;
            Document doc = (page == startPage) ? first : getDoc(url);
            if (doc == null) {
                log.info("FGV: doc nulo page={} url={} (parando)", page, url);
                break;
            }

            // Seletores:
            //   - a[href^="/cursos/online/"]  -> links de detalhe
            //   - filtragem por texto do anchor e pelo "profundidade" da rota para evitar tabs/menu
            Elements anchors = doc.select("main a[href^=\"/cursos/online/\"]");
            if (anchors.isEmpty()) {
                // fallback mais amplo, ainda restrito ao prefixo
                anchors = doc.select("a[href^=\"/cursos/online/\"]");
            }

            int anchorsTot = anchors.size();
            int add = 0;

            for (Element a : anchors) {
                String href = sanit(a.attr("href"));
                String title = sanit(a.text());

                if (href.isBlank()) continue;

                // normaliza href -> absoluto
                String abs = absUrl(base, href);

                // Evita falsos positivos: exige "rota de detalhe"
                // (algo como /cursos/online/<segmento1>/<slug> => 4+ segmentos)
                if (!isDetailCoursePath(href)) continue;

                // Caso o <a> tenha texto curto, tenta pegar título de ancestros previsíveis
                if (title.length() < 5) {
                    Element h2 = a.parents().select("h2").first();
                    Element h3 = a.parents().select("h3").first();
                    if (h3 != null && sanit(h3.text()).length() > 5) title = sanit(h3.text());
                    else if (h2 != null && sanit(h2.text()).length() > 5) title = sanit(h2.text());
                }

                if (title.isBlank()) continue;

                if (seen.putIfAbsent(abs, title) == null) add++;
            }

            // logs para diagnóstico
            String pageTitle = sanit(doc.title());
            log.info("FGV: page={} added={} totalSoFar={} (anchorsTot={}) title=\"{}\"",
                    page, add, seen.size(), anchorsTot, pageTitle);

            // estratégia de parada: nenhuma novidade => encerra
            if (add == 0 && page > startPage) break;

            // pequena pausa de polidez
            sleep(220);
        }

        // 4) monta saída
        List<Course> out = new ArrayList<>(seen.size());
        seen.forEach((href, title) -> out.add(new Course(
                null, null,
                sha256(title + "|" + href),
                title,
                href,
                "FGV",
                mapArea(title),
                true,           // página já é “Cursos Online » Gratuitos”
                null, null,
                "", "",         // statusText / priceText
                null, null
        )));
        log.info("FGV: total extraído={} (zeroBased=true, startPage={}, lastPage={})", out.size(), startPage, last);
        return out;
    }

    /* ===================== Helpers ===================== */

    private Document getDoc(String url) {
        // 1) tenta via HttpFetch (seu util)
        Document doc = null;
        try {
            doc = HttpFetch.get(url, TIMEOUT_MS, 2, 400);
        } catch (Throwable ignore) {
            // segue para fallback
        }
        if (doc != null) return doc;

        // 2) fallback: Jsoup direto com UA de navegador e cabeçalhos
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

    /** Varre paginação e pega o maior "page=" encontrado (zero-based). */
    private static int detectLastPage(Document doc) {
        int max = -1;
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
            } catch (Exception ignored) {}
        }
        return max;
    }

    /** Aceita apenas caminhos com profundidade de detalhe, para evitar links de categoria/menu. */
    private static boolean isDetailCoursePath(String href) {
        // exemplo válido: /cursos/online/curta-media-duracao-online/introducao-a-filosofia-50-horas
        if (href == null || href.isBlank()) return false;
        if (!href.startsWith("/cursos/online/")) return false;
        // conta segmentos
        String[] parts = href.split("/");
        // ["", "cursos", "online", "<segmento>", "<slug...>"] => >= 5
        return parts.length >= 5;
    }

    private static String sanit(String s) { return s == null ? "" : s.trim(); }

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
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
}
