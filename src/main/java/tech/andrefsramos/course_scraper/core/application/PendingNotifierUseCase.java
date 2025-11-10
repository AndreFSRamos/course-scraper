package tech.andrefsramos.course_scraper.core.application;

import java.util.List;

public interface PendingNotifierUseCase {
    void flushPlatform(String platformName);
    void flushAll(List<String> platformNames);
}
