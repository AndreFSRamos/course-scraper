package tech.andrefsramos.course_scraper.core.ports;

import java.time.Instant;

public interface ClockPort {
    Instant now();
}
