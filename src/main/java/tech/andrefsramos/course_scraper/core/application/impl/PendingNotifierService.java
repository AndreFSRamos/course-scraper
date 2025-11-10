package tech.andrefsramos.course_scraper.core.application.impl;

import tech.andrefsramos.course_scraper.core.application.PendingNotifierUseCase;
import tech.andrefsramos.course_scraper.core.domain.Course;
import tech.andrefsramos.course_scraper.core.ports.CourseRepository;
import tech.andrefsramos.course_scraper.core.ports.NotificationPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PendingNotifierService implements PendingNotifierUseCase {

    private final CourseRepository courseRepo;
    private final NotificationPort notificationPort;
    private final int perMessage;
    private final int maxPerRun;

    public PendingNotifierService(
            CourseRepository courseRepo,
            NotificationPort notificationPort,
            int perMessage,
            int maxPerRun
    ) {
        this.courseRepo = courseRepo;
        this.notificationPort = notificationPort;
        this.perMessage = Math.max(perMessage, 1);
        this.maxPerRun  = Math.max(maxPerRun, 1);
    }

    @Override
    public void flushPlatform(String platformName) {
        var pending = courseRepo.findPendingToNotify(platformName, maxPerRun);
        if (pending.isEmpty()) return;

        var sentIds = new ArrayList<Long>();
        var buffer  = new ArrayList<Course>();

        for (var c : pending) {
            buffer.add(c);
            if (buffer.size() == perMessage) {
                notificationPort.notifyNewCourses(platformName, buffer);
                sentIds.addAll(buffer.stream().map(Course::id).filter(Objects::nonNull).toList());
                buffer.clear();
                sleep(250);
            }
        }
        if (!buffer.isEmpty()) {
            notificationPort.notifyNewCourses(platformName, buffer);
            sentIds.addAll(buffer.stream().map(Course::id).filter(Objects::nonNull).toList());
        }

        courseRepo.markNotified(sentIds);
    }

    @Override
    public void flushAll(List<String> platformNames) {
        if (platformNames == null || platformNames.isEmpty()) return;
        for (String p : platformNames) {
            flushPlatform(p);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
}
