package tech.andrefsramos.course_scraper.core.application;

public interface CollectCoursesUseCase {
    void collectForPlatform(String platformName);
    void collectAllEnabled();
}
