package tech.andrefsramos.course_scraper.core.application.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tech.andrefsramos.course_scraper.adapters.outbound.persistence.entity.CourseCacheEntity;
import tech.andrefsramos.course_scraper.core.ports.CourseCacheRepository;

@Service
public class CourseCacheService {
    private static final Logger log = LoggerFactory.getLogger(CourseCacheService.class);

    private final CourseCacheRepository repository;

    public CourseCacheService(CourseCacheRepository repository) {
        this.repository = repository;
    }

    public boolean isFirstTimeSeen(String hash) {
        return !repository.existsByCourseHash(hash);
    }

    public void registerHash(String hash, String source) {
        if (!repository.existsByCourseHash(hash)) {
            CourseCacheEntity e = new CourseCacheEntity();
            e.setCourseHash(hash);
            e.setSource(source);
            try {
                repository.save(e);
            } catch (Exception err) {
                log.error("[CourseCacheService][registerHash]: {}", err.getMessage());
            }
        }
    }
}

