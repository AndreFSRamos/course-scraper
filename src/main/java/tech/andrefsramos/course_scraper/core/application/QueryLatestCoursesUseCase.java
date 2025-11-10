package tech.andrefsramos.course_scraper.core.application;

import tech.andrefsramos.course_scraper.core.domain.Course;
import java.time.Instant;
import java.util.List;

public interface QueryLatestCoursesUseCase {
    List<Course> list(String platform, String area, boolean freeOnly, Instant since, int page, int size);
}
