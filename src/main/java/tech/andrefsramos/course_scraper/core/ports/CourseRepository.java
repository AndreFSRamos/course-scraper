package tech.andrefsramos.course_scraper.core.ports;

import tech.andrefsramos.course_scraper.core.domain.Course;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CourseRepository {
    Optional<Course> findByHash(String externalIdHash);
    Course upsert(Course course);
    List<Course> findLatest(String platformName, String area, boolean onlyFree, Instant since, int page, int size);
    List<Course> findPendingToNotify(String platformName, int limit);
    void markNotified(List<Long> ids);
}