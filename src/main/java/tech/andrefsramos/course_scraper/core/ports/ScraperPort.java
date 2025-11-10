package tech.andrefsramos.course_scraper.core.ports;

import tech.andrefsramos.course_scraper.core.domain.Course;
import tech.andrefsramos.course_scraper.core.domain.Platform;
import java.util.List;

public interface ScraperPort {
    boolean supports(Platform platform);
    List<Course> fetchBatch(Platform platform, int maxPages);
}
