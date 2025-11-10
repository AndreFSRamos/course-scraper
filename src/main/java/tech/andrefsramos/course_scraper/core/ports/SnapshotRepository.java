package tech.andrefsramos.course_scraper.core.ports;

import tech.andrefsramos.course_scraper.core.domain.Course;

public interface SnapshotRepository {
    void saveSnapshot(Course course, String rawJson, String statusText, String priceText);
}
