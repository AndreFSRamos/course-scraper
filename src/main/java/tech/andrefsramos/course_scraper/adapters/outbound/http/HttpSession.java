package tech.andrefsramos.course_scraper.adapters.outbound.http;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * HttpSession

 * Finalidade

 * Encapsula uma sessão HTTP baseada em Jsoup para realizar requisições GET com:
 *   - Persistência de cookies entre chamadas (simulando uma “sessão” de navegação).
 *   - Cabeçalhos padrão (User-Agent, Accept, Accept-Language, Referrer) para reduzir bloqueios por WAF/CDN.
 *   - Tentativas com retry e backoff exponencial simples (constante) em caso de falha.
 *   - “Aquecimento” opcional da sessão (warm-up) visitando uma URL raiz para receber cookies anti-bot.

 * Uso típico

 * 1) (Opcional) {@link #warmUp(String)}: visita uma URL base do domínio para coleta de cookies iniciais.
 * 2) {@link #get(String)}: faz GET resiliente com retries; retorna {@link Document} ou null.
 */

public class HttpSession {

    private static final Logger log = LoggerFactory.getLogger(HttpSession.class);

    private final Map<String, String> cookies = new HashMap<>();

    private static final String UA_PRIMARY =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final String REF = "https://www.google.com";
    private static final String ACCEPT =
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
    private static final String ACCEPT_LANG =
            "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7";
    private static final String CACHE_NO =
            "no-cache";

    private int timeoutMs = 25000;
    private int retries = 2;
    private long backoffMs = 700;

    public HttpSession() {}

    public HttpSession(int timeoutMs, int retries, long backoffMs) {
        this.timeoutMs = timeoutMs;
        this.retries = retries;
        this.backoffMs = backoffMs;
    }

    public Document get(String url) {
        long globalStart = System.nanoTime();
        if (log.isDebugEnabled()) {
            log.debug("HttpSession.get: iniciando GET url={} timeoutMs={} retries={} backoffMs={} cookiesAtuais={}",
                    url, timeoutMs, retries, backoffMs, cookies.size());
        }

        Document d = tryOnce(url, 0);
        if (d != null) {
            log.info("HttpSession.get: sucesso na primeira tentativa url={}", url);
            return d;
        }

        for (int i = 1; i <= Math.max(0, retries); i++) {
            sleep(backoffMs);
            d = tryOnce(url, i);
            if (d != null) {
                log.info("HttpSession.get: sucesso na tentativa={} url={}", i, url);
                return d;
            }
        }

        long elapsedMs = (System.nanoTime() - globalStart) / 1_000_000;
        log.warn("HttpSession.get: esgotadas as tentativas de GET url={} elapsedMs={} ms; retornando null.", url, elapsedMs);
        return null;
    }

    public void warmUp(String warmUrl) {
        log.info("HttpSession.warmUp: iniciando warm-up url={}", warmUrl);
        long start = System.nanoTime();
        try {
            Connection.Response r = Jsoup.connect(warmUrl)
                    .userAgent(UA_PRIMARY)
                    .referrer(REF)
                    .timeout(timeoutMs)
                    .followRedirects(true)
                    .header("Accept", ACCEPT)
                    .header("Accept-Language", ACCEPT_LANG)
                    .header("Cache-Control", CACHE_NO)
                    .header("Pragma", CACHE_NO)
                    .method(Connection.Method.GET)
                    .execute();

            cookies.putAll(r.cookies());
            int code = r.statusCode();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            if (code >= 200 && code < 300) {
                log.info("HttpSession.warmUp: concluído com sucesso url={} status={} elapsedMs={}ms cookiesAcumulados={}",
                        warmUrl, code, elapsedMs, cookies.size());
            } else {
                log.warn("HttpSession.warmUp: status não-2xx url={} status={} elapsedMs={}ms cookiesAcumulados={}",
                        warmUrl, code, elapsedMs, cookies.size());
            }
        } catch (Exception ex) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.error("HttpSession.warmUp: falha ao aquecer sessão url={} elapsedMs={}ms", warmUrl, elapsedMs, ex);
        }
    }

    private Document tryOnce(String url, int attempt) {
        long start = System.nanoTime();
        try {
            Connection conn = Jsoup.connect(url)
                    .userAgent(UA_PRIMARY)
                    .referrer(REF)
                    .timeout(timeoutMs)
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .header("Accept", ACCEPT)
                    .header("Accept-Language", ACCEPT_LANG)
                    .header("Cache-Control", CACHE_NO)
                    .header("Pragma", CACHE_NO);

            if (!cookies.isEmpty()) {
                conn.cookies(cookies);
            }

            Connection.Response r = conn.method(Connection.Method.GET).execute();
            cookies.putAll(r.cookies());
            int code = r.statusCode();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            if (log.isDebugEnabled()) {
                String contentType = r.contentType();
                log.debug("HttpSession.tryOnce: tentativa={} url={} status={} elapsedMs={}ms contentType={} cookiesAcumulados={}",
                        attempt, url, code, elapsedMs, contentType, cookies.size());
            }

            if (code >= 200 && code < 300) {
                return r.parse();
            }

            if (code == 301 || code == 302) {
                try {
                    Document doc = r.parse();
                    if (doc != null) {
                        log.warn("HttpSession.tryOnce: redirecionamento com HTML intermediário parseado. tentativa={} url={} status={}",
                                attempt, url, code);
                    }
                    return doc;
                } catch (Exception parseEx) {
                    log.warn("HttpSession.tryOnce: falha ao parsear HTML de redirecionamento. tentativa={} url={} status={}",
                            attempt, url, code);
                    return null;
                }
            }

            log.warn("HttpSession.tryOnce: status não suportado para parse. tentativa={} url={} status={}", attempt, url, code);
        } catch (Exception ex) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.error("HttpSession.tryOnce: exceção na tentativa={} url={} elapsedMs={}ms", attempt, url, elapsedMs, ex);
        }
        return null;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public static String sanit(String s) { return s == null ? "" : s.trim(); }

    public static String absUrl(String base, String href) {
        if (href == null || href.isBlank()) return "";
        if (href.startsWith("http")) return href;
        if (!href.startsWith("/")) href = "/" + href;
        return base + href;
    }

    public static boolean isTrue(String v, boolean fb) {
        if (v == null) return fb;
        String x = v.trim().toLowerCase(Locale.ROOT);
        return "true".equals(x) || "1".equals(x) || "yes".equals(x);
    }
}
