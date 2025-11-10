package tech.andrefsramos.course_scraper.adapters.outbound.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tech.andrefsramos.course_scraper.adapters.outbound.persistence.entity.CourseEntity;
import tech.andrefsramos.course_scraper.adapters.outbound.persistence.entity.CourseSnapshotEntity;
import tech.andrefsramos.course_scraper.core.domain.Course;
import tech.andrefsramos.course_scraper.core.ports.SnapshotRepository;

import java.nio.charset.StandardCharsets;

/*
Finalidade

fias" (snapshots) de cursos no momento da coleta, guardando:
 *  - Referência ao curso (FK);
 *  - statusText e priceText efetivamente interpretados no parsing;
 *  - rawJson (payload bruto do scraper para auditoria e reprocessamento).

 * Como funciona

 * - saveSnapshot(course, rawJson, statusText, priceText):
 *   1) Valida entrada (curso e ID do curso são obrigatórios);
 *   2) Resolve referência leve do CourseEntity (getReference) para evitar SELECT extra;
 *   3) Cria CourseSnapshotEntity, preenche campos e persiste;
 *   4) Registra logs e métricas (tempo, tamanho do payload);
 *   5) Trata exceções lançando log com contexto e sem interromper o job chamador.

 */

@Repository
public class JpaSnapshotRepositoryImpl implements SnapshotRepository {
    private static final Logger log = LoggerFactory.getLogger(JpaSnapshotRepositoryImpl.class);

    private static final int RAWJSON_WARN_CHARS = 500_000;
    private static final int RAWJSON_WARN_BYTES = 2_000_000;

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional
    public void saveSnapshot(Course course, String rawJson, String statusText, String priceText) {
        if (course == null) {
            log.warn("[Snapshot] saveSnapshot chamado com 'course' nulo; operação ignorada.");
            return;
        }
        if (course.id() == null) {
            log.warn("[Snapshot] Curso sem ID (provavelmente ainda não persistido). Ignorando snapshot. title='{}'", course.title());
            return;
        }

        final long t0 = System.nanoTime();

        final String status = statusText != null ? statusText : "";
        final String price  = priceText  != null ? priceText  : "";
        final String raw    = rawJson    != null ? rawJson    : "";

        final int charLen = raw.length();
        final int byteLen = raw.getBytes(StandardCharsets.UTF_8).length;

        if (rawJson == null || rawJson.isBlank()) {
            log.debug("[Snapshot] rawJson vazio/nulo para courseId={}. Isto é aceitável se a origem não forneceu payload bruto.", course.id());
        }
        if (charLen > RAWJSON_WARN_CHARS || byteLen > RAWJSON_WARN_BYTES) {
            log.warn("[Snapshot] rawJson muito grande para courseId={} (chars={}, bytes={}). Verifique definição da coluna (TEXT/CLOB).",
                    course.id(), charLen, byteLen);
        }
        if (statusText == null) {
            log.debug("[Snapshot] statusText nulo normalizado para vazio. courseId={}", course.id());
        }
        if (priceText == null) {
            log.debug("[Snapshot] priceText nulo normalizado para vazio. courseId={}", course.id());
        }

        try {
            CourseEntity ref = em.getReference(CourseEntity.class, course.id());

            CourseSnapshotEntity snap = new CourseSnapshotEntity();
            snap.setCourse(ref);
            snap.setStatusText(status);
            snap.setPriceText(price);
            snap.setRawJson(raw);

            em.persist(snap);
            em.flush();

            final long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.info("[Snapshot] Criado com sucesso. courseId={} snapshotId={} tookMs={} charLen={} byteLen={}",
                    course.id(),
                    snap.getId(),
                    tookMs,
                    charLen,
                    byteLen);

        } catch (Exception e) {
            final long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.error("[Snapshot] Falha ao persistir snapshot. courseId={} tookMs={} charLen={} byteLen={} cause={}",
                    course.id(), tookMs, charLen, byteLen, e.getMessage(), e);
        }
    }
}
