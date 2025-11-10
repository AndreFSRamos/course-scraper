package tech.andrefsramos.course_scraper.core.ports;

import java.util.Optional;

public interface PlatformRepository {
    Optional<Long> findIdByNameIgnoreCase(String name);
}
