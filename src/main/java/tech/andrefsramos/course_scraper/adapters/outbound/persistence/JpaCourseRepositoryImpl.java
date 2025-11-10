package tech.andrefsramos.course_scraper.adapters.outbound.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tech.andrefsramos.course_scraper.adapters.outbound.persistence.entity.CourseEntity;
import tech.andrefsramos.course_scraper.adapters.outbound.persistence.entity.PlatformEntity;
import tech.andrefsramos.course_scraper.core.domain.Course;
import tech.andrefsramos.course_scraper.core.ports.CourseRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class JpaCourseRepositoryImpl implements CourseRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Optional<Course> findByHash(String externalIdHash) {
        TypedQuery<CourseEntity> q = em.createQuery(
                "SELECT c FROM CourseEntity c WHERE c.externalIdHash = :h", CourseEntity.class);
        q.setParameter("h", externalIdHash);
        List<CourseEntity> list = q.getResultList();
        return list.isEmpty() ? Optional.empty() : Optional.of(toDomain(list.get(0)));
    }

    @Override
    @Transactional
    public Course upsert(Course c) {
        // >>> EXIJA platformId preenchido (evita hardcode “evg”)
        if (c.platformId() == null) {
            throw new IllegalArgumentException("Course.platformId is required for upsert()");
        }

        // procura por hash
        TypedQuery<CourseEntity> q = em.createQuery(
                "SELECT c FROM CourseEntity c WHERE c.externalIdHash = :h", CourseEntity.class);
        q.setParameter("h", c.externalIdHash());
        List<CourseEntity> found = q.getResultList();

        CourseEntity entity = found.isEmpty() ? new CourseEntity() : found.get(0);

        // associa a plataforma por ID (referência leve, sem SELECT completo)
        PlatformEntity pRef = em.getReference(PlatformEntity.class, c.platformId());
        entity.setPlatform(pRef);

        // copia campos
        entity.setExternalIdHash(c.externalIdHash());
        entity.setTitle(c.title());
        entity.setUrl(c.url());
        entity.setProvider(c.provider());
        entity.setArea(c.area());
        entity.setFreeFlag(c.freeFlag());
        entity.setStartDate(c.startDate());
        entity.setEndDate(c.endDate());
        entity.setStatusText(c.statusText());
        entity.setPriceText(c.priceText());

        if (entity.getId() == null) {
            em.persist(entity);
        } else {
            entity = em.merge(entity);
        }
        em.flush();
        return toDomain(entity);
    }

    @Override
    public List<Course> findLatest(String platformName, String area, boolean onlyFree,
                                   Instant since, int page, int size) {
        StringBuilder jpql = new StringBuilder("SELECT c FROM CourseEntity c WHERE 1=1 ");

        if (platformName != null && !platformName.isBlank()) {
            jpql.append(" AND LOWER(c.platform.name) = LOWER(:pname) ");
        }
        if (area != null && !area.isBlank()) {
            jpql.append(" AND c.area = :area ");
        }
        if (onlyFree) {
            jpql.append(" AND c.freeFlag = true ");
        }
        if (since != null) {
            jpql.append(" AND c.updatedAt >= :since ");
        }
        jpql.append(" ORDER BY c.updatedAt DESC ");

        TypedQuery<CourseEntity> query = em.createQuery(jpql.toString(), CourseEntity.class);
        if (platformName != null && !platformName.isBlank()) query.setParameter("pname", platformName);
        if (area != null && !area.isBlank()) query.setParameter("area", area);
        if (since != null) query.setParameter("since", java.sql.Timestamp.from(since));

        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 1), 100);
        query.setFirstResult(p * s);
        query.setMaxResults(s);

        return query.getResultList().stream().map(this::toDomain).collect(Collectors.toList());
    }

    private Course toDomain(CourseEntity e) {
        Long platformId = (e.getPlatform() != null ? e.getPlatform().getId() : null);
        return new Course(
                e.getId(),
                platformId,
                e.getExternalIdHash(),
                e.getTitle(),
                e.getUrl(),
                e.getProvider(),
                e.getArea(),
                Boolean.TRUE.equals(e.getFreeFlag()),
                e.getStartDate(),
                e.getEndDate(),
                e.getStatusText(),
                e.getPriceText(),
                e.getCreatedAt() != null ? e.getCreatedAt().toInstant() : null,
                e.getUpdatedAt() != null ? e.getUpdatedAt().toInstant() : null
        );
    }

    @Override
    public List<Course> findPendingToNotify(String platformName, int limit) {
        var jpql = """
          SELECT c FROM CourseEntity c
          WHERE (:pname IS NULL OR LOWER(c.platform.name) = LOWER(:pname))
            AND c.notifiedAt IS NULL
          ORDER BY c.createdAt ASC
        """;
        var q = em.createQuery(jpql, CourseEntity.class);
        q.setParameter("pname", platformName);
        q.setMaxResults(Math.min(Math.max(limit,1), 500));
        return q.getResultList().stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public void markNotified(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        var now = new java.sql.Timestamp(System.currentTimeMillis());
        em.createQuery("UPDATE CourseEntity c SET c.notifiedAt = :now WHERE c.id IN :ids")
                .setParameter("now", now)
                .setParameter("ids", ids)
                .executeUpdate();
    }

    private Long resolvePlatformIdByName(String name) {
        var q = em.createQuery("SELECT p.id FROM PlatformEntity p WHERE LOWER(p.name)=LOWER(:n)", Long.class);
        q.setParameter("n", name);
        var ids = q.getResultList();
        return ids.isEmpty() ? null : ids.get(0);
    }

    private PlatformEntity resolvePlatformByName(String name) {
        var q = em.createQuery(
                "SELECT p FROM PlatformEntity p WHERE LOWER(p.name)=LOWER(:n)", PlatformEntity.class);
        q.setParameter("n", name);
        return q.getResultList().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Platform not found: " + name));
    }
}
