package tech.andrefsramos.course_scraper.core.application.impl;

import tech.andrefsramos.course_scraper.core.application.QueryLatestCoursesUseCase;
import tech.andrefsramos.course_scraper.core.domain.Course;
import tech.andrefsramos.course_scraper.core.ports.CourseRepository;

import java.time.Instant;
import java.util.List;

public class QueryLatestCoursesService implements QueryLatestCoursesUseCase {
    private final CourseRepository courseRepository;

    public QueryLatestCoursesService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Override
    public List<Course> list(String platform, String area, boolean freeOnly,
                             Instant since, int page, int size) {
        return courseRepository.findLatest(platform, area, freeOnly, since, page, size);
    }
}
