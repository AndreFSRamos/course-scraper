package tech.andrefsramos.course_scraper.core.application;

import tech.andrefsramos.course_scraper.core.domain.Course;
import java.util.List;

public interface DetectChangesUseCase {
    List<Course> computeNewOrUpdated(java.util.List<Course> batch);
}
