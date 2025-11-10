package tech.andrefsramos.course_scraper.core.application.impl;

import tech.andrefsramos.course_scraper.core.application.NotifyNewCoursesUseCase;
import tech.andrefsramos.course_scraper.core.domain.Course;
import tech.andrefsramos.course_scraper.core.ports.NotificationPort;

import java.util.List;

public class NotifyNewCoursesService implements NotifyNewCoursesUseCase {

    private final NotificationPort notificationPort;
    private final int maxNewPerRun;  // ex.: 40
    private final int perMessage;    // ex.: 8
    private final String apiBaseUrl;

    public NotifyNewCoursesService(
            NotificationPort notificationPort,
            int maxNewPerRun,
            int perMessage,
            String apiBaseUrl
    ) {
        this.notificationPort = notificationPort;
        this.maxNewPerRun = Math.max(0, maxNewPerRun);
        this.perMessage = Math.max(1, perMessage);
        this.apiBaseUrl = apiBaseUrl != null ? apiBaseUrl : "";
    }

    @Override
    public void notifyNew(String platformName, List<Course> newOnes) {
        if (newOnes == null || newOnes.isEmpty()) return;

        int cap = (maxNewPerRun > 0) ? Math.min(maxNewPerRun, newOnes.size()) : newOnes.size();
        List<Course> head = newOnes.subList(0, cap);

        // envia em lotes (N cursos por mensagem)
        for (int i = 0; i < head.size(); i += perMessage) {
            List<Course> slice = head.subList(i, Math.min(i + perMessage, head.size()));
            notificationPort.notifyNewCourses(platformName, slice);
            // (Opcional) dormir um pouco para respeitar rate-limit de webhooks
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        }

        int remaining = newOnes.size() - cap;
        if (remaining > 0) {
            String apiLink = apiBaseUrl.isBlank()
                    ? ""
                    : apiBaseUrl + "?platform=" + platformName + "&sort=updated_at,desc";
            notificationPort.notifySummary(platformName, remaining, apiLink);
        }
    }
}

