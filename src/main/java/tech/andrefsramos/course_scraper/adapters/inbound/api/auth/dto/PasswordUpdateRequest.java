package tech.andrefsramos.course_scraper.adapters.inbound.api.auth.dto;

public record PasswordUpdateRequest(
        String currentPassword,
        String newPassword
) {}
