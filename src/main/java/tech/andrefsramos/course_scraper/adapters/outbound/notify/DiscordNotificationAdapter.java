package tech.andrefsramos.course_scraper.adapters.outbound.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tech.andrefsramos.course_scraper.core.domain.Course;
import tech.andrefsramos.course_scraper.core.ports.NotificationPort;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * DiscordNotificationAdapter

 * Finalidade

 * Adapter de saída responsável por enviar notificações ao Discord via webhook.
 * Constrói embeds conforme os limites da API do Discord e envia em lotes para
 * respeitar rate-limit, tamanho total por mensagem e quantidade de embeds.

 * Funcionamento dos métodos

 * - notifyNewCourses(platformName, newCourses):
 *     1) Valida precondições (lista vazia, webhook não configurado).
 *     2) Constrói embeds por curso (título, url, campos principais).
 *     3) Agrupa em lotes respeitando:
 *        - MAX_EMBEDS_PER_MESSAGE (até 10 por mensagem)
 *        - MAX_EMBED_TOTAL_CHARS (cota aproximada por mensagem)
 *     4) Faz flush de cada lote com postEmbeds().
 *     5) Registra métricas e eventos no log (DEBUG/INFO/WARN/ERROR).

 * - notifySummary(platformName, totalNew, apiLink):
 *     1) Constrói um único embed de resumo da execução (total e link).
 *     2) Envia via postEmbeds() e registra logs.

 * - postEmbeds(embeds):
 *     1) Serializa body JSON manualmente.
 *     2) Executa POST no webhook com timeouts configuráveis.
 *     3) Implementa retentativa (até 3) em caso de 429 (Retry-After).
 *     4) Loga status HTTP, bytes enviados e tamanho do lote.
 */
@Component
@ConditionalOnProperty(prefix = "app.notify.discord", name = "enabled", havingValue = "true")
public class DiscordNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(DiscordNotificationAdapter.class);

    @Value("${app.notify.discord.webhookUrl:}")
    private String webhookUrl;

    @Value("${app.notify.discord.maxEmbedsPerMessage:10}")
    private int maxEmbedsPerMessage;

    @Value("${app.notify.discord.maxEmbedTotalChars:6000}")
    private int maxEmbedTotalChars;

    @Value("${app.notify.discord.connectTimeoutMs:10000}")
    private int connectTimeoutMs;

    @Value("${app.notify.discord.readTimeoutMs:20000}")
    private int readTimeoutMs;

    @Value("${app.notify.discord.batchDelayMs:350}")
    private long batchDelayMs;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    public void notifyNewCourses(String platformName, List<Course> newCourses) {
        if (newCourses == null || newCourses.isEmpty()) {
            log.debug("[Discord] Sem novos cursos para notificar. platform={}", platformName);
            return;
        }
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("[Discord] Webhook URL não configurada. Ignorando notificação. platform={}", platformName);
            return;
        }

        log.info("[Discord] Iniciando envio de novos cursos. platform={} totalCursos={} maxEmbeds={} maxCharsMsg={}",
                platformName, newCourses.size(), maxEmbedsPerMessage, maxEmbedTotalChars);

        List<Map<String, Object>> currentEmbeds = new ArrayList<>();
        int currentChars = 0;
        int totalEmbedsSent = 0;
        int totalMessages = 0;

        for (Course c : newCourses) {
            Map<String, Object> embed = buildEmbed(platformName, c);
            int embedChars = estimateEmbedChars(embed);

            boolean exceedEmbeds = currentEmbeds.size() >= maxEmbedsPerMessage;
            boolean exceedChars  = (currentChars + embedChars) > maxEmbedTotalChars;
            boolean mustFlush    = (exceedEmbeds || exceedChars) && !currentEmbeds.isEmpty();

            if (mustFlush) {
                log.debug("[Discord] Flush do lote: embeds={} charsAprox={} (exceedEmbeds={}, exceedChars={})",
                        currentEmbeds.size(), currentChars, exceedEmbeds, exceedChars);
                postEmbeds(currentEmbeds);
                totalMessages++;
                totalEmbedsSent += currentEmbeds.size();
                sleep(batchDelayMs);
                currentEmbeds = new ArrayList<>();
                currentChars = 0;
            }

            currentEmbeds.add(embed);
            currentChars += embedChars;
        }

        if (!currentEmbeds.isEmpty()) {
            postEmbeds(currentEmbeds);
            totalMessages++;
            totalEmbedsSent += currentEmbeds.size();
        }

        log.info("[Discord] Envio concluído. platform={} messages={} embedsTotal={}",
                platformName, totalMessages, totalEmbedsSent);
    }

    @Override
    public void notifySummary(String platformName, int totalNew, String apiLink) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("[Discord] Webhook URL não configurada. Ignorando resumo. platform={}", platformName);
            return;
        }

        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("title", "Resumo — " + platformName.toUpperCase(Locale.ROOT));
        embed.put("description", "Foram encontrados **" + totalNew + "** novos cursos."
                + (apiLink != null && !apiLink.isBlank() ? "\n" + apiLink : ""));
        postEmbeds(Collections.singletonList(embed));
    }

    private Map<String, Object> buildEmbed(String platformName, Course c) {
        Map<String, Object> embed = new LinkedHashMap<>();
        String title = truncate(safe(c.title()), 240);
        String url   = safe(c.url());

        embed.put("title", title);
        if (!url.isBlank()) embed.put("url", url);

        StringBuilder desc = new StringBuilder();

        if (c.provider() != null && !c.provider().isBlank()) {
            desc.append("**Fornecedor:** ").append(escape(c.provider())).append("\n");
        }

        if (c.area() != null && !c.area().isBlank()) {
            desc.append("**Área:** ").append(escape(c.area())).append("\n");
        }
        embed.put("description", desc.toString());

        List<Map<String, Object>> fields = new ArrayList<>();

        String formato = (c.statusText() != null && !c.statusText().isBlank())
                ? c.statusText() : "Online (EAD)";
        fields.add(field("Formato", formato, true));

        fields.add(field("Gratuito", "Sim", true));

        if (c.startDate() != null) fields.add(field("Início", DATE_FMT.format(c.startDate()), true));

        if (c.endDate()   != null) fields.add(field("Fim",    DATE_FMT.format(c.endDate()),   true));

        if (c.priceText() != null && !c.priceText().isBlank()) {
            fields.add(field("Preço", c.priceText(), true));
        }

        embed.put("fields", fields);

        Map<String, Object> footer = new LinkedHashMap<>();
        footer.put("text", platformName.toUpperCase(Locale.ROOT));
        embed.put("footer", footer);

        return embed;
    }

    private static Map<String, Object> field(String name, String value, boolean inline) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("name", name);
        f.put("value", (value == null || value.isBlank()) ? "—" : value);
        f.put("inline", inline);
        return f;
    }

    private static int estimateEmbedChars(Map<String, Object> embed) {
        int n = 0;
        for (var e : embed.entrySet()) {
            if (e.getValue() instanceof String s) n += s.length();
            if (e.getValue() instanceof List<?> l) {
                for (Object o : l) {
                    if (o instanceof Map<?,?> m) {
                        n += m.toString().length();
                    } else if (o != null) {
                        n += o.toString().length();
                    }
                }
            }
        }
        return n;
    }

    private void postEmbeds(List<Map<String, Object>> embeds) {
        HttpURLConnection con = null;
        byte[] payload = null;

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("embeds", embeds);

            String json = toJson(body);
            payload = json.getBytes(StandardCharsets.UTF_8);

            URL url = new URL(webhookUrl);
            con = (HttpURLConnection) url.openConnection();
            con.setDoOutput(true);
            con.setRequestMethod("POST");
            con.setConnectTimeout(connectTimeoutMs);
            con.setReadTimeout(readTimeoutMs);
            con.setRequestProperty("Content-Type", "application/json; charset=utf-8");

            try (var os = con.getOutputStream()) {
                os.write(payload);
            }

            int code = con.getResponseCode();

            int attempts = 0;
            while (code == 429 && attempts < 3) {
                attempts++;
                long retryMs = parseRetryAfterMs(con);
                log.warn("[Discord] 429 Too Many Requests (tentativa {}). Aguardando {} ms.", attempts, retryMs);
                sleep(retryMs > 0 ? retryMs : 1000);

                con.disconnect();
                con = (HttpURLConnection) url.openConnection();
                con.setDoOutput(true);
                con.setRequestMethod("POST");
                con.setConnectTimeout(connectTimeoutMs);
                con.setReadTimeout(readTimeoutMs);
                con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (var os = con.getOutputStream()) { os.write(payload); }
                code = con.getResponseCode();
            }

            if (code < 200 || code >= 300) {
                String err = readAll(con.getErrorStream());
                log.error("[Discord] HTTP {} ao enviar embeds. embeds={} bytes={} bodyErr='{}'",
                        code, embeds.size(), (payload != null ? payload.length : -1), err);
                return;
            }

            // drena resposta
            String resp = readAll(con.getInputStream());
            log.info("[Discord] Envio OK. embeds={} bytes={} respLen={}", embeds.size(),
                    (payload != null ? payload.length : -1), (resp != null ? resp.length() : 0));

        } catch (Exception e) {
            int code = -1;
            try { if (con != null) code = con.getResponseCode(); } catch (Exception ignored) {}
            log.error("[Discord] Falha no envio. status={} bytes={} cause={}",
                    code, (payload != null ? payload.length : -1), e.getMessage(), e);
        } finally {
            if (con != null) {
                try { if (con.getErrorStream() != null) con.getErrorStream().close(); } catch (Exception ignored) {}
                try { con.disconnect(); } catch (Exception ignored) {}
            }
        }
    }

    private static String toJson(Object o) {
        if (o == null) return "null";
        if (o instanceof String s) return "\"" + escape(s) + "\"";
        if (o instanceof Number || o instanceof Boolean) return o.toString();
        if (o instanceof Map<?,?> m) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (var e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append(toJson(e.getKey().toString())).append(':').append(toJson(e.getValue()));
            }
            return sb.append('}').toString();
        }
        if (o instanceof Collection<?> c) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (var it : c) {
                if (!first) sb.append(',');
                first = false;
                sb.append(toJson(it));
            }
            return sb.append(']').toString();
        }
        return "\"" + escape(o.toString()) + "\"";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\","\\\\")
                .replace("\"","\\\"")
                .replace("\n","\\n")
                .replace("\r","");
    }

    private static String readAll(java.io.InputStream is) {
        if (is == null) return "";
        try (is) {
            var sb = new StringBuilder();
            byte[] b = new byte[1024];
            int r;
            while ((r = is.read(b)) != -1) sb.append(new String(b, 0, r, StandardCharsets.UTF_8));
            return sb.toString();
        } catch (Exception e) { return ""; }
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

    private static String safe(String s) { return s == null ? "" : s; }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, Math.max(0, n - 1)) + "…";
    }
}
