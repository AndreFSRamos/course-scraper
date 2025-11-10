package tech.andrefsramos.course_scraper.adapters.outbound.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tech.andrefsramos.course_scraper.core.domain.Course;
import tech.andrefsramos.course_scraper.core.ports.NotificationPort;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * TelegramNotificationAdapter

 * Finalidade

 * Envia notificações para um chat do Telegram (canal/grupo/DM) utilizando o método
 * sendMessage da Bot API. É um adapter de saída (NotificationPort).

 * Funcionamento

 * - notifyNewCourses(platformName, newCourses):
 *   1) Valida pré-condições (lista vazia, token/chat configurados).
 *   2) Monta uma mensagem de cabeçalho e lista cada curso (título, área e URL).
 *   3) Segmenta o texto em "chunks" <= maxMessageChars para respeitar o limite do Telegram.
 *   4) Envia cada chunk com sendMarkdownMessage(), respeitando rate limit e timeouts.

 * - notifySummary(platformName, totalNew, apiLink):
 *   1) Monta resumo simples do processamento.
 *   2) Envia com sendMarkdownMessage().
 */
@Component
@ConditionalOnProperty(prefix = "app.notify.telegram", name = "enabled", havingValue = "true")
public class TelegramNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationAdapter.class);

    @Value("${app.notify.telegram.botToken:}")
    private String botToken;

    @Value("${app.notify.telegram.chatId:}")
    private String chatId;

    @Value("${app.notify.telegram.connectTimeoutMs:10000}")
    private int connectTimeoutMs;

    @Value("${app.notify.telegram.readTimeoutMs:20000}")
    private int readTimeoutMs;

    @Value("${app.notify.telegram.maxMessageChars:3800}")
    private int maxMessageChars;

    @Value("${app.notify.telegram.batchDelayMs:300}")
    private long batchDelayMs;

    @Value("${app.notify.telegram.disableWebPreview:true}")
    private boolean disableWebPreview;

    @Override
    public void notifyNewCourses(String platformName, List<Course> newCourses) {
        if (newCourses == null || newCourses.isEmpty()) {
            log.debug("[Telegram] Sem novos cursos para notificar. platform={}", platformName);
            return;
        }
        if (isConfigMissing()) return;

        String header = "🎓 *Novos cursos* — " + safe(platformName).toUpperCase() + "\n";
        StringBuilder sb = new StringBuilder(header);

        for (Course c : newCourses) {
            sb.append("• ").append(escapeMd(safe(c.title())))
                    .append(" — ")
                    .append(escapeMd(safeOrDefault(c.area())))
                    .append("\n")
                    .append(safe(c.url()))
                    .append("\n");
        }

        List<String> chunks = splitInChunks(sb.toString(), maxMessageChars);
        log.info("[Telegram] Enviando novos cursos. platform={} cursos={} chunks={}",
                platformName, newCourses.size(), chunks.size());

        int success = 0;
        for (String chunk : chunks) {
            boolean ok = sendMarkdownMessage(chunk);
            if (ok) success++;
            sleep(batchDelayMs);
        }
        log.info("[Telegram] Envio concluído. platform={} chunksOk={}/{}", platformName, success, chunks.size());
    }

    @Override
    public void notifySummary(String platformName, int totalNew, String apiLink) {
        if (isConfigMissing()) return;

        StringBuilder sb = new StringBuilder();
        sb.append("📊 ").append(safe(platformName).toUpperCase())
                .append(": +").append(totalNew).append(" novos cursos.");
        if (apiLink != null && !apiLink.isBlank()) {
            sb.append(" ").append(apiLink.trim());
        }

        String text = sb.toString();
        List<String> chunks = splitInChunks(text, maxMessageChars);
        log.info("[Telegram] Enviando resumo. platform={} chunks={}", platformName, chunks.size());

        int success = 0;
        for (String chunk : chunks) {
            if (sendMarkdownMessage(chunk)) success++;
            sleep(batchDelayMs);
        }
        log.info("[Telegram] Resumo concluído. platform={} chunksOk={}/{}", platformName, success, chunks.size());
    }

    private boolean sendMarkdownMessage(String text) {
        HttpURLConnection con = null;
        byte[] bodyBytes = null;
        String endpoint = "https://api.telegram.org/bot" + botToken + "/sendMessage";

        try {
            String form = "chat_id=" + urlEnc(chatId)
                    + "&text=" + urlEnc(text)
                    + "&parse_mode=Markdown"
                    + (disableWebPreview ? "&disable_web_page_preview=true" : "");

            bodyBytes = form.getBytes(StandardCharsets.UTF_8);

            URL url = new URL(endpoint);
            con = (HttpURLConnection) url.openConnection();
            con.setDoOutput(true);
            con.setRequestMethod("POST");
            con.setConnectTimeout(connectTimeoutMs);
            con.setReadTimeout(readTimeoutMs);
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

            try (var os = con.getOutputStream()) { os.write(bodyBytes); }

            int code = con.getResponseCode();

            int attempts = 0;
            while (code == 429 && attempts < 3) {
                attempts++;
                long retryMs = parseRetryAfterMs(con);
                log.warn("[Telegram] 429 Too Many Requests (tentativa {}). Aguardando {} ms.", attempts, retryMs);
                sleep(retryMs > 0 ? retryMs : 1000);

                closeQuietly(con);
                con = (HttpURLConnection) url.openConnection();
                con.setDoOutput(true);
                con.setRequestMethod("POST");
                con.setConnectTimeout(connectTimeoutMs);
                con.setReadTimeout(readTimeoutMs);
                con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                try (var os = con.getOutputStream()) { os.write(bodyBytes); }
                code = con.getResponseCode();
            }

            if (code < 200 || code >= 300) {
                String err = readAll(con.getErrorStream());
                log.error("[Telegram] HTTP {} ao enviar mensagem. bytes={} err='{}'",
                        code, bodyBytes.length, truncate(err));
                return false;
            }

            String resp = readAll(con.getInputStream());
            log.debug("[Telegram] Envio OK. bytes={} respLen={}", bodyBytes.length, resp.length());
            return true;

        } catch (Exception e) {
            int status = -1;
            try { if (con != null) status = con.getResponseCode(); } catch (Exception ignored) {}
            log.error("[Telegram] Falha no envio. status={} bytes={} cause={}",
                    status, (bodyBytes != null ? bodyBytes.length : -1), e.getMessage(), e);
            return false;
        } finally {
            closeQuietly(con);
        }
    }

    private boolean isConfigMissing() {
        if (botToken == null || botToken.isBlank()) {
            log.warn("[Telegram] Bot token não configurado. Ignorando notificação.");
            return true;
        }
        if (chatId == null || chatId.isBlank()) {
            log.warn("[Telegram] Chat ID não configurado. Ignorando notificação.");
            return true;
        }
        return false;
    }

    private static List<String> splitInChunks(String text, int maxLen) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        String s = text;
        while (s.length() > maxLen) {
            int cut = findSplitPoint(s, maxLen);
            out.add(s.substring(0, cut));
            s = s.substring(cut);
        }
        if (!s.isEmpty()) out.add(s);
        return out;
    }

    private static int findSplitPoint(String s, int maxLen) {
        int cut = Math.min(maxLen, s.length());
        int nl = s.lastIndexOf('\n', cut - 1);
        if (nl >= 0 && nl > cut - 600) return nl + 1;
        return cut;
    }

    private static String escapeMd(String s) {
        if (s == null) return "";
        return s.replace("*", "\\*")
                .replace("_", "\\_")
                .replace("`", "\\`")
                .replace("[", "\\[");
    }

    private static String urlEnc(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8);
        }
        catch (Exception e) {
            return "";
        }
    }

    private static String safe(String s) {
        return (s == null) ? "" : s;
    }

    private static String safeOrDefault(String s) {
        return (s == null || s.isBlank()) ? "Sem área" : s;
    }

    private static String readAll(InputStream is) {
        if (is == null) return "";
        try (is) {
            var sb = new StringBuilder();
            byte[] buf = new byte[1024];
            int r;
            while ((r = is.read(buf)) != -1) sb.append(new String(buf, 0, r, StandardCharsets.UTF_8));
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    private static void closeQuietly(HttpURLConnection con) {
        if (con == null) return;
        try { if (con.getErrorStream() != null) con.getErrorStream().close(); } catch (Exception ignored) {}
        try { if (con.getInputStream() != null) con.getInputStream().close(); } catch (Exception ignored) {}
        try { con.disconnect(); } catch (Exception ignored) {}
    }

    private static long parseRetryAfterMs(HttpURLConnection con) {
        try {
            String ra = con.getHeaderField("Retry-After");
            if (ra != null) {
                double secs = Double.parseDouble(ra.trim());
                return (long) (secs * 1000);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= 500 ? s : s.substring(0, Math.max(0, 500 - 1)) + "…";
    }
}