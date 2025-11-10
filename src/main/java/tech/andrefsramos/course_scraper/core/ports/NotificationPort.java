package tech.andrefsramos.course_scraper.core.ports;

import tech.andrefsramos.course_scraper.core.domain.Course;
import java.util.List;

public interface NotificationPort {
    void notifyNewCourses(String platformName, List<Course> newCourses);
    void notifySummary(String platformName, int totalNew, String apiLink);
}
