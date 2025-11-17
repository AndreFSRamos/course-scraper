package tech.andrefsramos.course_scraper.adapters.outbound.scrapers;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import tech.andrefsramos.course_scraper.adapters.outbound.http.HttpSession;
import tech.andrefsramos.course_scraper.core.domain.Course;
import tech.andrefsramos.course_scraper.core.domain.Platform;
import tech.andrefsramos.course_scraper.core.ports.ScraperPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/*
 * Finalidade

 * Coletar cursos online e gratuitos expostos pelo componente dinâmico do portal Sebrae:
 *   GET {base}/sites/render/component
 *     ?vgnextcomponentid=3263d864e639a610VgnVCM1000004c00210aRCRD
 *     &qtd=24&order=2&filters=&_cb=<timestamp>
 *
 * Como funciona

 * - "Aquece" a sessão (homepage e /sites/PortalSebrae/cursosonline) para obter cookies anti-bot/WAF.
 * - Pagina por incremento no parâmetro "qtd" (12 em 12) e usa os campos ocultos retornados (#qtd, #total, #hasNext)
 *   para controlar encerramento.
 * - Seleciona cartões em:
 *     #list-cards .sb-components__card a[href^="/sites/PortalSebrae/cursosonline/"]
 *   com fallback:
 *     .card a[href*="/sites/PortalSebrae/cursosonline/"]
 * - Deduplica por URL absoluta e monta Course assumindo "Online (EAD)" e gratuito.
 */

@Component
public class SebraeScraperAdapter implements ScraperPort {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SebraeScraperAdapter.class);
    private static final String DEFAULT_BASE = "https://www.sebrae.com.br";
    private static final String COMPONENT_ID = "3263d864e639a610VgnVCM1000004c00210aRCRD";
    private static final int STEP             = 12;
    private static final int TIMEOUT_MS       = 25_000;
    private static final int RETRIES          = 2;
    private static final long BACKOFF_MS      = 800;
    private static final long SLEEP_BETWEEN   = 320;
    private static final int NULL_DOC_STREAK_CAP = 2;

    @Cacheable(cacheNames = "sebraePageHtml", key = "#root.target.normalizeCacheKey(#url)")
    public Document fetchPageHtml(HttpSession ses, String url) {
        log.debug("[Sebrae] Fetching HTML real: {}", url);
        return ses.get(url);
    }

    @Override
    public boolean supports(Platform p) {
        return p != null && "sebrae".equalsIgnoreCase(p.name());
    }

    @Override
    public List<Course> fetchBatch(Platform platform, int maxPages) {
        final long t0 = System.nanoTime();

        final String base = normalizeBase(platform != null ? platform.baseUrl() : null);
        final int maxItems = Math.max(STEP, Math.min(Math.max(maxPages, 1), 50) * 60);

        log.info("[Sebrae] início da coleta base={} maxItems={}", Optional.of(base), Optional.of(maxItems));
        if (platform == null) {
            log.warn("[Sebrae] Platform nula (usando base padrão).");
        }

        final HttpSession ses = new HttpSession(TIMEOUT_MS, RETRIES, BACKOFF_MS);
        try {
            ses.warmUp(base + "/");
            ses.warmUp(base + "/sites/PortalSebrae/cursosonline");
        } catch (Throwable t) {
            log.warn("[Sebrae] warmUp falhou (seguindo mesmo assim): {}", t.toString());
        }

        int qtd = 24;
        String order = "2";
        String filters = "";

        final Map<String, String> hrefToTitle = new LinkedHashMap<>();
        int reportedTotal = -1;
        boolean hasNext = true;
        int emptyStreak = 0;
        String stopReason = "OK";

        while (hasNext && hrefToTitle.size() < maxItems) {
            final String url = base + "/sites/render/component"
                    + "?vgnextcomponentid=" + COMPONENT_ID
                    + "&qtd=" + qtd
                    + "&order=" + order
                    + "&filters=" + filters
                    + "&_cb=" + System.nanoTime();

            final Document doc = fetchPageHtml(ses, url);
            if (doc == null) {
                log.warn("[Sebrae] Null document URL={}", url);
                emptyStreak++;
                if (emptyStreak >= NULL_DOC_STREAK_CAP) {
                    stopReason = "NULL_DOC_STREAK";
                    break;
                }
                continue;
            }

            final int pageQtd  = parseInt(doc.selectFirst("#qtd"), 0);
            final int pageTot  = parseInt(doc.selectFirst("#total"), -1);
            final boolean next = parseBool(doc.selectFirst("#hasNext"));

            if (reportedTotal < 0 && pageTot >= 0) reportedTotal = pageTot;

            Elements cards = doc.select("#list-cards .sb-components__card a[href^=\"/sites/PortalSebrae/cursosonline/\"]");
            if (cards.isEmpty()) {
                cards = doc.select(".card a[href*=\"/sites/PortalSebrae/cursosonline/\"]");
                if (cards.isEmpty()) {
                    log.warn("[Sebrae] Nenhuma âncora de curso encontrada na resposta (qtd={}, url={})", Optional.of(pageQtd), Optional.of(url));
                }
            }

            int added = 0;
            for (Element a : cards) {
                final String abs = absUrl(base, a.attr("href"));
                final String title = sanit(a.text());
                if (abs.isBlank() || title.isBlank()) continue;
                if (hrefToTitle.putIfAbsent(abs, title) == null) added++;
            }

            log.info("[Sebrae] qtd={} reportedTotal={} added={} totalSoFar={} hasNext={} url={}",
                    Optional.of(pageQtd), Optional.of(reportedTotal), Optional.of(added), Optional.of(hrefToTitle.size()) , Optional.of(next), url);

            if (added == 0) {
                emptyStreak++;
                if (emptyStreak >= NULL_DOC_STREAK_CAP) {
                    stopReason = "NO_NEW_CARDS_STREAK";
                    break;
                }
            } else {
                emptyStreak = 0;
            }

            hasNext = next && (reportedTotal < 0 || hrefToTitle.size() < reportedTotal);
            if (!hasNext) {
                stopReason = "HAS_NEXT_FALSE_OR_TOTAL_REACHED";
                break;
            }

            if (hrefToTitle.size() >= maxItems) {
                stopReason = "ITEM_CAP";
                log.warn("[Sebrae] atingiu itemCap={} — interrompendo.", Optional.of(maxItems));
                break;
            }

            qtd += STEP;

            try { Thread.sleep(SLEEP_BETWEEN); }
            catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                stopReason = "INTERRUPTED";
                log.warn("[Sebrae] loop interrompido por Thread.interrupt().");
                break;
            }
        }

        final List<Course> out = new ArrayList<>(hrefToTitle.size());
        for (Map.Entry<String,String> e : hrefToTitle.entrySet()) {
            final String abs = e.getKey();
            final String title = e.getValue();

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

        final long tookMs = (System.nanoTime() - t0) / 1_000_000;
        log.info("[Sebrae] total extracted={} (reported total={}) stopReason={} tookMs={}ms",
                Optional.of(out.size()), Optional.of(reportedTotal), Optional.of(stopReason), Optional.of(tookMs));

        return out;
    }

    private String normalizeCacheKey(String url) {
        int idx = url.indexOf("&_cb=");
        return (idx > 0) ? url.substring(0, idx) : url;
    }

    private static int parseInt(Element hidden, int fb) {
        if (hidden == null) return fb;

        try {
            return Integer.parseInt(hidden.attr("value").trim());
        } catch (Exception ignored) {
            return fb;
        }
    }

    private static boolean parseBool(Element hidden) {
        if (hidden == null) return true;
        String v = hidden.attr("value").trim().toLowerCase(Locale.ROOT);
        return "true".equals(v) || "1".equals(v) || "yes".equals(v);
    }

    private static String normalizeBase(String b) {
        String base = (b != null && !b.isBlank()) ? b.trim() : DEFAULT_BASE;
        if (base.equalsIgnoreCase("https://sebrae.com.br")) return "https://sebrae.com.br";
        if (base.equalsIgnoreCase("https://www.sebrae.com.br")) return "https://www.sebrae.com.br";
        return base;
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
