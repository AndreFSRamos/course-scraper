package tech.andrefsramos.course_scraper.adapters.inbound.api.auth.dto;

public record AuthRequest(
        String username,
        String password
) {}