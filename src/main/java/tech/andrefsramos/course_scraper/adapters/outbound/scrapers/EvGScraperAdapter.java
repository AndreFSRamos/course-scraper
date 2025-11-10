package tech.andrefsramos.course_scraper.adapters.outbound.scrapers;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import tech.andrefsramos.course_scraper.adapters.outbound.http.HttpFetch;
import tech.andrefsramos.course_scraper.core.domain.Course;
import tech.andrefsramos.course_scraper.core.domain.Platform;
import tech.andrefsramos.course_scraper.core.ports.ScraperPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Component
public class EvGScraperAdapter implements ScraperPort {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EvGScraperAdapter.class);

    @Override public boolean supports(Platform p) { return p != null && "evg".equalsIgnoreCase(p.name()); }

    @Override
    public List<Course> fetchBatch(Platform platform, int maxPages) {
        final String base = (platform != null && platform.baseUrl()!=null && !platform.baseUrl().isBlank())
                ? platform.baseUrl() : "https://www.escolavirtual.gov.br";
        final String path = "/catalogo?page=";

        final int hardPageCap = Math.min(Math.max(maxPages, 5), 200);
        final int hardItemCap = 10_000;

        Map<String,String> seen = new LinkedHashMap<>();

        // tenta page=1 e volta para page=0 se necessário (sites oscilam 0/1-based)
        int startPage = 1;
        Document first = HttpFetch.get(base + path + startPage, 25000, 2, 600);
        if (first == null || first.select(".card, .resultado-cursos .card, article.card, a[href*=\"/curso/\"]").isEmpty()) {
            startPage = 0;
            first = HttpFetch.get(base + path + startPage, 25000, 2, 600);
        }
        if (first == null) {
            log.warn("EVG: doc nulo nas páginas 1 e 0");
            return List.of();
        }

        int last = detectLastPage(first);
        if (last < 0) last = startPage + hardPageCap - 1;
        else last = Math.min(last, startPage + hardPageCap - 1);

        int added;
        for (int page = startPage; page <= last; page++) {
            String url = base + path + page;
            Document doc = (page == startPage) ? first : HttpFetch.get(url, 25000, 2, 600);
            if (doc == null) { log.warn("EVG: doc nulo page={} url={}", page, url); break; }

            Elements cards = doc.select(".card, .resultado-cursos .card, article.card, a[href*=\"/curso/\"]");
            if (cards.isEmpty()) {
                log.info("EVG: page={} sem cards -> stop", page);
                break;
            }

            added = 0;
            for (Element c : cards) {
                Element a = "a".equalsIgnoreCase(c.tagName()) ? c : c.selectFirst("a[href]");
                if (a == null) continue;

                String href = HttpFetch.absUrl(base, a.attr("href"));
                String title = HttpFetch.sanit(a.text());
                if (href.isBlank() || title.isBlank()) continue;

                if (seen.putIfAbsent(href, title) == null) added++;
                if (seen.size() >= hardItemCap) break;
            }

            log.info("EVG: page={} added={} totalSoFar={}", page, added, seen.size());
            if (added == 0) break;
            if (seen.size() >= hardItemCap) break;

            try { Thread.sleep(180); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }

        List<Course> out = new ArrayList<>(seen.size());
        seen.forEach((href, title) -> out.add(new Course(
                null, null, sha256(title + "|" + href), title, href,
                "EVG", mapArea(title),
                true, // EVG catálogo geralmente gratuito; ajuste se necessário
                null, null,
                "Online (EAD)", // status/formato
                "",             // priceText vazio (não exibir no Discord)
                null, null
        )));
        log.info("EVG: total extraído={}", out.size());
        return out;
    }

    private static int detectLastPage(Document doc) {
        int max = -1;
        for (Element a : doc.select("ul.pagination a[href*=\"page=\"]")) {
            String h = a.attr("href");
            int idx = h.lastIndexOf("page=");
            if (idx < 0) continue;
            try {
                String tail = h.substring(idx + 5);
                int amp = tail.indexOf('&');
                String num = (amp >= 0) ? tail.substring(0, amp) : tail;
                max = Math.max(max, Integer.parseInt(num.replaceAll("\\D+", "")));
            } catch (Exception ignored) {}
        }
        return max;
    }

    private static String mapArea(String t){
        t = t==null?"":t.toLowerCase(Locale.ROOT);
        if (t.contains("dados")||t.contains("ia")) return "Dados & IA";
        if (t.contains("finan")) return "Finanças & Contabilidade";
        if (t.contains("gest")||t.contains("empreend")) return "Gestão & Negócios";
        if (t.contains("marketing")||t.contains("vendas")) return "Marketing & Vendas";
        if (t.contains("saúde")) return "Saúde & Bem-estar";
        if (t.contains("tecnolog")) return "Tecnologia";
        return null;
    }

    private static String sha256(String s){
        try{
            var md = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        }catch(Exception e){ return Integer.toHexString(s.hashCode()); }
    }
}
