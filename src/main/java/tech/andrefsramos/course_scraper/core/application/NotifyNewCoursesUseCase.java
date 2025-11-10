package tech.andrefsramos.course_scraper.core.application;

import tech.andrefsramos.course_scraper.core.domain.Course;
import java.util.List;

public interface NotifyNewCoursesUseCase {
    void notifyNew(String platformName, List<Course> newOnes);
}
