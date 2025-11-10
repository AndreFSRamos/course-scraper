package tech.andrefsramos.course_scraper.adapters.outbound.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tech.andrefsramos.course_scraper.adapters.outbound.persistence.entity.CourseEntity;
import tech.andrefsramos.course_scraper.adapters.outbound.persistence.entity.PlatformEntity;
import tech.andrefsramos.course_scraper.core.domain.Course;
import tech.andrefsramos.course_scraper.core.ports.CourseRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/*
 * JpaCourseRepositoryImpl

 * Finalidade

 * Implementação JPA do porto de repositório de cursos. Centraliza operações de
 * leitura e escrita em banco (MySQL) para a entidade Course, mapeada por CourseEntity.

 * Modelo de Dados (resumo)

 * - CourseEntity (tabela `courses` ou equivalente): contém campos básicos do curso,
 *   inclusive externalIdHash (chave lógica única por plataforma), price/status,
 *   controle de datas (createdAt/updatedAt) e notifiedAt (marca envio de notificação).
 * - PlatformEntity: entidade associada por FK (muitas Courses para uma Platform).

 */

@Repository
public class JpaCourseRepositoryImpl implements CourseRepository {

    private static final Logger log = LoggerFactory.getLogger(JpaCourseRepositoryImpl.class);

    @PersistenceContext
    private EntityManager em;

    @Override
    public Optional<Course> findByHash(String externalIdHash) {
        long t0 = System.nanoTime();
        TypedQuery<CourseEntity> q = em.createQuery(
                "SELECT c FROM CourseEntity c WHERE c.externalIdHash = :h", CourseEntity.class);
        q.setParameter("h", externalIdHash);

        List<CourseEntity> list = q.getResultList();
        long tookMs = (System.nanoTime() - t0) / 1_000_000;

        if (list.size() > 1) {
            log.warn("[JPA] findByHash() retornou múltiplos registros para hash={} (count={}). Verificar unicidade.",
                    externalIdHash, list.size());
        }

        log.debug("[JPA] findByHash hash={} resultados={} tookMs={}", externalIdHash, list.size(), tookMs);
        return list.isEmpty() ? Optional.empty() : Optional.of(toDomain(list.get(0)));
    }

    @Override
    @Transactional
    public Course upsert(Course c) {
        long t0 = System.nanoTime();

        if (c == null) {
            throw new IllegalArgumentException("Course não pode ser nulo em upsert()");
        }

        if (c.platformId() == null) {
            throw new IllegalArgumentException("Course.platformId é obrigatório em upsert()");
        }

        if (c.externalIdHash() == null || c.externalIdHash().isBlank()) {
            throw new IllegalArgumentException("Course.externalIdHash é obrigatório em upsert()");
        }

        TypedQuery<CourseEntity> q = em.createQuery(
                "SELECT c FROM CourseEntity c WHERE c.externalIdHash = :h", CourseEntity.class);
        q.setParameter("h", c.externalIdHash());
        List<CourseEntity> found = q.getResultList();

        if (found.size() > 1) {
            log.warn("[JPA] upsert() encontrou múltiplos registros para hash={} (count={}). Será atualizado o primeiro.",
                    c.externalIdHash(), found.size());
        }

        CourseEntity entity = found.isEmpty() ? new CourseEntity() : found.get(0);

        PlatformEntity pRef = em.getReference(PlatformEntity.class, c.platformId());
        entity.setPlatform(pRef);
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

        boolean insert = (entity.getId() == null);
        if (insert) {
            em.persist(entity);
            log.debug("[JPA] upsert() persist hash={} platformId={}", c.externalIdHash(), c.platformId());
        } else {
            entity = em.merge(entity);
            log.debug("[JPA] upsert() merge id={} hash={} platformId={}", entity.getId(), c.externalIdHash(), c.platformId());
        }

        em.flush();
        long tookMs = (System.nanoTime() - t0) / 1_000_000;
        log.info("[JPA] upsert concluído. insert={} id={} hash={} tookMs={}",
                insert, entity.getId(), c.externalIdHash(), tookMs);

        return toDomain(entity);
    }

    @Override
    public List<Course> findLatest(String platformName, String area, boolean onlyFree,
                                   Instant since, int page, int size) {
        long t0 = System.nanoTime();

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

        List<CourseEntity> rows = query.getResultList();
        List<Course> out = rows.stream().map(this::toDomain).collect(Collectors.toList());

        long tookMs = (System.nanoTime() - t0) / 1_000_000;
        log.debug("[JPA] findLatest filtros: platformName='{}', area='{}', onlyFree={}, since={}, page={}, size={}",
                platformName, area, onlyFree, since, p, s);
        log.info("[JPA] findLatest resultados={} tookMs={}", out.size(), tookMs);

        return out;
    }

    @Override
    public List<Course> findPendingToNotify(String platformName, int limit) {
        long t0 = System.nanoTime();
        int lim = Math.min(Math.max(limit, 1), 500);

        String jpql = """
          SELECT c FROM CourseEntity c
          WHERE (:pname IS NULL OR LOWER(c.platform.name) = LOWER(:pname))
            AND c.notifiedAt IS NULL
          ORDER BY c.createdAt ASC
        """;
        TypedQuery<CourseEntity> q = em.createQuery(jpql, CourseEntity.class);
        q.setParameter("pname", platformName);
        q.setMaxResults(lim);

        List<CourseEntity> rows = q.getResultList();
        List<Course> out = rows.stream().map(this::toDomain).toList();

        long tookMs = (System.nanoTime() - t0) / 1_000_000;
        log.info("[JPA] findPendingToNotify platform='{}' limit={} resultados={} tookMs={}",
                platformName, lim, out.size(), tookMs);
        return out;
    }

    @Override
    @Transactional
    public void markNotified(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            log.debug("[JPA] markNotified() chamado com lista vazia. Nada a atualizar.");
            return;
        }
        long t0 = System.nanoTime();
        var now = new java.sql.Timestamp(System.currentTimeMillis());
        try {
            int updated = em.createQuery("UPDATE CourseEntity c SET c.notifiedAt = :now WHERE c.id IN :ids")
                    .setParameter("now", now)
                    .setParameter("ids", ids)
                    .executeUpdate();
            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.info("[JPA] markNotified atualizado={} de {} ids. tookMs={}", updated, ids.size(), tookMs);
            if (updated < ids.size()) {
                log.warn("[JPA] markNotified: nem todos os IDs foram atualizados (updated={}, solicitados={}).",
                        updated, ids.size());
            }
        } catch (Exception e) {
            log.error("[JPA] markNotified falhou para {} ids. cause={}", ids.size(), e.getMessage(), e);
            throw e;
        }
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

    @SuppressWarnings("unused")
    private Long resolvePlatformIdByName(String name) {
        var q = em.createQuery("SELECT p.id FROM PlatformEntity p WHERE LOWER(p.name)=LOWER(:n)", Long.class);
        q.setParameter("n", name);
        var ids = q.getResultList();
        return ids.isEmpty() ? null : ids.get(0);
    }

    @SuppressWarnings("unused")
    private PlatformEntity resolvePlatformByName(String name) {
        var q = em.createQuery(
                "SELECT p FROM PlatformEntity p WHERE LOWER(p.name)=LOWER(:n)", PlatformEntity.class);
        q.setParameter("n", name);
        return q.getResultList().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Platform not found: " + name));
    }
}