package tech.andrefsramos.course_scraper.adapters.outbound.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import tech.andrefsramos.course_scraper.adapters.outbound.persistence.entity.PlatformEntity;
import tech.andrefsramos.course_scraper.core.ports.PlatformRepository;

import java.util.Optional;

@Repository
public class JpaPlatformRepositoryImpl implements PlatformRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Optional<Long> findIdByNameIgnoreCase(String name) {
        var q = em.createQuery(
                "SELECT p.id FROM PlatformEntity p WHERE LOWER(p.name) = LOWER(:n)", Long.class);
        q.setParameter("n", name);
        var list = q.getResultList();
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}
