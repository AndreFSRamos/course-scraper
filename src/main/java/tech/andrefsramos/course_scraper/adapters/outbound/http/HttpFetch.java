package tech.andrefsramos.course_scraper.adapters.outbound.http;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finalidade

 * Classe utilitária estática para execução de requisições HTTP simples (GET) com fallback e retry.
 * Diferente de {@link HttpSession}, não mantém cookies nem estado de sessão — cada chamada é isolada.

 * Principais características:
 * - Tenta duas User-Agents (bot e browser-like) para evitar bloqueios.
 * - Implementa retries configuráveis com backoff incremental.
 * - Utiliza cabeçalhos e referrer padrão para simular tráfego humano legítimo.
 */
public final class HttpFetch {

    private static final Logger log = LoggerFactory.getLogger(HttpFetch.class);

    private HttpFetch() {}

    private static final String UA_PRIMARY =
            "CourseScraperBot/1.0 (+https://plantview.io/coursescraper; contact: andre@plantview.io)";

    private static final String UA_FALLBACK =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private static final String REF = "https://www.google.com";
    private static final String ACCEPT =
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
    private static final String ACCEPT_LANG =
            "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7";

    public static Document get(String url, int timeoutMs, int maxRetries, long backoffMs) {
        long globalStart = System.nanoTime();
        log.info("HttpFetch: iniciando GET url={} timeoutMs={} retries={} backoffMs={}", url, timeoutMs, maxRetries, backoffMs);

        Document doc = tryOnce(url, UA_PRIMARY, timeoutMs);
        if (doc != null) {
            log.info("HttpFetch: sucesso com UA_PRIMARY url={}", url);
            return doc;
        }

        doc = tryOnce(url, UA_FALLBACK, timeoutMs);
        if (doc != null) {
            log.info("HttpFetch: sucesso com UA_FALLBACK url={}", url);
            return doc;
        }

        for (int i = 0; i < Math.max(0, maxRetries); i++) {
            sleep(backoffMs);
            String ua = (i % 2 == 0) ? UA_PRIMARY : UA_FALLBACK;
            int adjustedTimeout = timeoutMs + (i * 1000);

            log.debug("HttpFetch: retry={} url={} userAgent={} timeoutMs={}", i + 1, url, ua, adjustedTimeout);
            doc = tryOnce(url, ua, adjustedTimeout);
            if (doc != null) {
                log.info("HttpFetch: sucesso após retry={} url={}", i + 1, url);
                return doc;
            }
        }

        long elapsedMs = (System.nanoTime() - globalStart) / 1_000_000;
        log.warn("HttpFetch: falha após todas as tentativas url={} elapsedMs={}ms", url, elapsedMs);
        return null;
    }

    private static Document tryOnce(String url, String ua, int timeoutMs) {
        long start = System.nanoTime();
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(ua)
                    .referrer(REF)
                    .timeout(timeoutMs)
                    .followRedirects(true)
                    .header("Accept", ACCEPT)
                    .header("Accept-Language", ACCEPT_LANG)
                    .header("Cache-Control", "no-cache")
                    .header("Pragma", "no-cache")
                    .get();

            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.debug("HttpFetch.tryOnce: sucesso url={} ua={} elapsedMs={}ms", url, ua, elapsedMs);
            return doc;
        } catch (Exception ex) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.warn("HttpFetch.tryOnce: falha url={} ua={} elapsedMs={}ms msg={}", url, ua, elapsedMs, ex.getMessage());
            return null;
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public static String sanit(String s) {
        return s == null ? "" : s.trim();
    }

    public static String absUrl(String base, String href) {
        if (href == null || href.isBlank()) return "";
        if (href.startsWith("http")) return href;
        if (!href.startsWith("/")) href = "/" + href;
        return base + href;
    }
}
