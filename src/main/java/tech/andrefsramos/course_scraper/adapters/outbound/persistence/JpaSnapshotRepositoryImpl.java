package tech.andrefsramos.course_scraper.adapters.outbound.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tech.andrefsramos.course_scraper.adapters.outbound.persistence.entity.CourseEntity;
import tech.andrefsramos.course_scraper.adapters.outbound.persistence.entity.CourseSnapshotEntity;
import tech.andrefsramos.course_scraper.core.domain.Course;
import tech.andrefsramos.course_scraper.core.ports.SnapshotRepository;

@Repository
public class JpaSnapshotRepositoryImpl implements SnapshotRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional
    public void saveSnapshot(Course course, String rawJson, String statusText, String priceText) {
        if (course == null || course.id() == null) return;

        CourseEntity ref = em.getReference(CourseEntity.class, course.id());
        CourseSnapshotEntity snap = new CourseSnapshotEntity();
        snap.setCourse(ref);
        snap.setStatusText(statusText);
        snap.setPriceText(priceText);
        snap.setRawJson(rawJson);
        em.persist(snap);
    }
}