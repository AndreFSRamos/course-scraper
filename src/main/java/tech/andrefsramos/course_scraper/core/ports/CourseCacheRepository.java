package tech.andrefsramos.course_scraper.core.ports;

import org.springframework.data.jpa.repository.JpaRepository;
import tech.andrefsramos.course_scraper.adapters.outbound.persistence.entity.CourseCacheEntity;

public interface CourseCacheRepository extends JpaRepository<CourseCacheEntity, Long> {
    boolean existsByCourseHash(String courseHash);
}

