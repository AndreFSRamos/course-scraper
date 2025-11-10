package tech.andrefsramos.course_scraper.core.domain;

import java.time.Instant;
import java.time.LocalDate;

public record Course(
        Long id,
        Long platformId,
        String externalIdHash,
        String title,
        String url,
        String provider,
        String area,
        boolean freeFlag,
        LocalDate startDate,
        LocalDate endDate,
        String statusText,
        String priceText,
        Instant createdAt,
        Instant updatedAt
) {}