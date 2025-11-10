package tech.andrefsramos.course_scraper.adapters.outbound.notify;

import tech.andrefsramos.course_scraper.core.domain.Course;
import tech.andrefsramos.course_scraper.core.ports.NotificationPort;

import java.util.List;

public class CompositeNotificationPort implements NotificationPort {

    private final List<NotificationPort> delegates;

    public CompositeNotificationPort(List<NotificationPort> delegates) {
        this.delegates = delegates != null ? delegates : List.of();
    }

    @Override
    public void notifyNewCourses(String platformName, List<Course> newCourses) {
        for (NotificationPort d : delegates) {
            try { d.notifyNewCourses(platformName, newCourses); } catch (Exception ignored) {}
        }
    }

    @Override
    public void notifySummary(String platformName, int totalNew, String apiLink) {
        for (NotificationPort d : delegates) {
            try { d.notifySummary(platformName, totalNew, apiLink); } catch (Exception ignored) {}
        }
    }
}

