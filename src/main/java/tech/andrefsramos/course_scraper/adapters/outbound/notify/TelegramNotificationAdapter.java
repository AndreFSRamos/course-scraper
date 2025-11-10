package tech.andrefsramos.course_scraper.adapters.outbound.notify;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import tech.andrefsramos.course_scraper.core.domain.Course;
import tech.andrefsramos.course_scraper.core.ports.NotificationPort;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "app.notify.telegram", name = "enabled", havingValue = "true")
public class TelegramNotificationAdapter implements NotificationPort {

    @Value("${app.notify.telegram.botToken}")
    private String botToken;

    @Value("${app.notify.telegram.chatId}")
    private String chatId;

    @Override
    public void notifyNewCourses(String platformName, List<Course> newCourses) {
        if (newCourses == null || newCourses.isEmpty()) return;

        StringBuilder sb = new StringBuilder("🎓 *Novos cursos* — ").append(platformName.toUpperCase()).append("\n");
        for (Course c : newCourses) {
            sb.append("• ").append(escape(c.title()))
                    .append(" — ").append(c.area() != null ? c.area() : "Sem área")
                    .append("\n").append(c.url()).append("\n");
        }
        sendMarkdown(sb.toString());
    }

    @Override
    public void notifySummary(String platformName, int totalNew, String apiLink) {
        String msg = "📊 " + platformName.toUpperCase() + ": +" + totalNew + " novos cursos. "
                + (apiLink != null && !apiLink.isBlank() ? apiLink : "");
        sendMarkdown(msg);
    }

    private void sendMarkdown(String text) {
        try {
            String endpoint = "https://api.telegram.org/bot" + botToken + "/sendMessage";
            var data = java.net.URLEncoder.encode(text, java.nio.charset.StandardCharsets.UTF_8);
            String body = "chat_id=" + chatId + "&text=" + data + "&parse_mode=Markdown";
            var con = (java.net.HttpURLConnection) new java.net.URL(endpoint).openConnection();
            con.setDoOutput(true); con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (var os = con.getOutputStream()) { os.write(body.getBytes()); }
            con.getInputStream().close();
        } catch (Exception ignored) { }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("*","\\*").replace("_","\\_").replace("`","\\`");
    }
}

