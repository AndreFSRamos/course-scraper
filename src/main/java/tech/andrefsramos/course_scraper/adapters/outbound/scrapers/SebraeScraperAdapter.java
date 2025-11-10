package tech.andrefsramos.course_scraper.adapters.outbound.scrapers;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import tech.andrefsramos.course_scraper.adapters.outbound.http.HttpSession;
import tech.andrefsramos.course_scraper.core.domain.Course;
import tech.andrefsramos.course_scraper.core.domain.Platform;
import tech.andrefsramos.course_scraper.core.ports.ScraperPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Component
public class SebraeScraperAdapter implements ScraperPort {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SebraeScraperAdapter.class);

    private static final String DEFAULT_BASE = "https://www.sebrae.com.br";
    private static final String COMPONENT_ID = "3263d864e639a610VgnVCM1000004c00210aRCRD";
    private static final int STEP = 12;

    @Override
    public boolean supports(Platform p) {
        return p != null && "sebrae".equalsIgnoreCase(p.name());
    }

    @Override
    public List<Course> fetchBatch(Platform platform, int maxPages) {
        final String base = normalizeBase(platform != null ? platform.baseUrl() : null);
        final int maxItems = Math.max(STEP, Math.min(maxPages, 50) * 60);

        HttpSession ses = new HttpSession(25000, 2, 800);
        // warm-ups: homepage e área de cursos online (ganha cookies anti-bot)
        ses.warmUp(base + "/");
        ses.warmUp(base + "/sites/PortalSebrae/cursosonline");

        int qtd = 24;     // 12/24/36…
        String order = "2";
        String filters = "";

        Map<String, String> hrefToTitle = new LinkedHashMap<>();
        int total = -1;
        boolean hasNext = true;
        int emptyStreak = 0; // para abortar se o endpoint ficar retornando vazio

        while (hasNext && hrefToTitle.size() < maxItems) {
            String url = base + "/sites/render/component"
                    + "?vgnextcomponentid=" + COMPONENT_ID
                    + "&qtd=" + qtd
                    + "&order=" + order
                    + "&filters=" + filters
                    + "&_cb=" + System.nanoTime();

            Document doc = ses.get(url);
            if (doc == null) {
                log.warn("[Sebrae] Null document URL={}", url);
                emptyStreak++;
                if (emptyStreak >= 2) break;
                continue;
            }

            int pageQtd  = parseInt(doc.selectFirst("#qtd"), 0);
            int pageTot  = parseInt(doc.selectFirst("#total"), -1);
            boolean next = parseBool(doc.selectFirst("#hasNext"), true);

            if (total < 0 && pageTot >= 0) total = pageTot;

            // seletor principal
            Elements cards = doc.select("#list-cards .sb-components__card a[href^=\"/sites/PortalSebrae/cursosonline/\"]");
            // fallback: alguns templates usam .card a[href*='cursosonline']
            if (cards.isEmpty()) {
                cards = doc.select(".card a[href*=\"/sites/PortalSebrae/cursosonline/\"]");
            }

            int add = 0;
            for (Element a : cards) {
                String abs = absUrl(base, a.attr("href"));
                String title = sanit(a.text());
                if (abs.isBlank() || title.isBlank()) continue;
                if (hrefToTitle.putIfAbsent(abs, title) == null) add++;
            }

            log.info("[Sebrae] qtd={} total={} added={} url={}", pageQtd, total, add, url);

            if (add == 0) {
                emptyStreak++;
                if (emptyStreak >= 2) break;
            } else {
                emptyStreak = 0;
            }

            hasNext = next && (total < 0 || hrefToTitle.size() < total);
            if (!hasNext) break;

            qtd += STEP;
            if (hrefToTitle.size() >= maxItems) break;

            try { Thread.sleep(320); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }

        List<Course> out = new ArrayList<>(hrefToTitle.size());
        for (Map.Entry<String,String> e : hrefToTitle.entrySet()) {
            String abs = e.getKey();
            String title = e.getValue();

            out.add(new Course(
                    null, null, sha256(title + "|" + abs), title, abs,
                    "Sebrae", mapArea(title),
                    true,
                    null, null,
                    "Online (EAD)",
                    "",
                    null, null
            ));
        }

        log.info("[Sebrae] total extracted={} (reported total={})", out.size(), total);
        return out;
    }

    /* helpers */
    private static int parseInt(Element hidden, int fb) {
        if (hidden == null) return fb;
        try { return Integer.parseInt(hidden.attr("value").trim()); }
        catch (Exception ignored) { return fb; }
    }
    private static boolean parseBool(Element hidden, boolean fb) {
        if (hidden == null) return fb;
        String v = hidden.attr("value").trim().toLowerCase(Locale.ROOT);
        return "true".equals(v) || "1".equals(v) || "yes".equals(v);
    }
    private static String normalizeBase(String b) {
        String base = (b != null && !b.isBlank()) ? b.trim() : DEFAULT_BASE;
        if (base.equalsIgnoreCase("https://sebrae.com.br")) return "https://sebrae.com.br";
        if (base.equalsIgnoreCase("https://www.sebrae.com.br")) return "https://www.sebrae.com.br";
        return base;
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
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
