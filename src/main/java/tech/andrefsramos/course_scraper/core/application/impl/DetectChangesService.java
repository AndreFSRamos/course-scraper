package tech.andrefsramos.course_scraper.core.application.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.andrefsramos.course_scraper.core.application.DetectChangesUseCase;
import tech.andrefsramos.course_scraper.core.domain.Course;
import tech.andrefsramos.course_scraper.core.domain.CourseChangePolicy;
import tech.andrefsramos.course_scraper.core.ports.CourseRepository;
import tech.andrefsramos.course_scraper.core.ports.SnapshotRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/*
 * Finalidade

 * Percorre um lote normalizado de cursos coletados e compara com o estado persistido:
 *  1) Busca o existente por "externalIdHash".
 *  2) Se não existir: faz upsert (insert) e cria snapshot -> curso é considerado NOVO (retornado para notificação).
 *  3) Se existir: aplica {@link CourseChangePolicy#isRelevantUpdate(Course, Course)}:
 *     - true  -> upsert (update) + snapshot (mudança relevante).
 *     - false -> apenas snapshot leve para histórico (sem notificação).
 */

public class DetectChangesService implements DetectChangesUseCase {

    private static final Logger log = LoggerFactory.getLogger(DetectChangesService.class);

    private final CourseRepository courseRepository;
    private final SnapshotRepository snapshotRepository;

    public DetectChangesService(
            CourseRepository courseRepository,
            SnapshotRepository snapshotRepository
    ) {
        this.courseRepository = courseRepository;
        this.snapshotRepository = snapshotRepository;
    }

    @Override
    public List<Course> computeNewOrUpdated(List<Course> batch) {
        final long t0 = System.nanoTime();

        if (batch == null || batch.isEmpty()) {
            log.info("[Detect] Lote vazio — nada a comparar.");
            return List.of();
        }

        final List<Course> newOnes = new ArrayList<>();
        int processed = 0;
        int created = 0;
        int updatedRelevant = 0;
        int unchanged = 0;
        int skipped = 0;
        int failed = 0;

        for (Course incoming : batch) {
            processed++;

            if (incoming == null) {
                skipped++;
                log.warn("[Detect] Item nulo na posição {} — ignorado.", processed - 1);
                continue;
            }

            if (incoming.externalIdHash() == null || incoming.externalIdHash().isBlank()) {
                skipped++;
                log.warn("[Detect] externalIdHash ausente para título='{}' — item ignorado.", incoming.title());
                continue;
            }

            if (incoming.platformId() == null) {
                skipped++;
                log.warn("[Detect] platformId ausente para hash={} título='{}' — upsert exige platformId, item ignorado.",
                        incoming.externalIdHash(), incoming.title());
                continue;
            }

            try {
                final Optional<Course> existingOpt = courseRepository.findByHash(incoming.externalIdHash());

                if (existingOpt.isEmpty()) {
                    final Course saved = safeUpsert(incoming);
                    if (saved != null) {
                        safeSnapshot(saved, /* raw_json */  saved.statusText(), saved.priceText());
                        newOnes.add(saved);
                        created++;
                        if (log.isDebugEnabled()) {
                            log.debug("[Detect] NEW hash={} título='{}'", incoming.externalIdHash(), incoming.title());
                        }
                    } else {
                        failed++;
                    }
                    continue;
                }

                final Course existing = existingOpt.get();
                final boolean relevant = CourseChangePolicy.isRelevantUpdate(existing, incoming);

                if (relevant) {
                    final Course saved = safeUpsert(incoming);
                    if (saved != null) {
                        safeSnapshot(saved, saved.statusText(), saved.priceText());
                        updatedRelevant++;
                        if (log.isDebugEnabled()) {
                            log.debug("[Detect] UPDATE(relevant) hash={} título='{}'", incoming.externalIdHash(), incoming.title());
                        }
                    } else {
                        failed++;
                    }
                } else {
                    safeSnapshot(existing, incoming.statusText(), incoming.priceText());
                    unchanged++;
                    if (log.isDebugEnabled()) {
                        log.debug("[Detect] NO-CHANGE hash={} título='{}'", incoming.externalIdHash(), incoming.title());
                    }
                }

            } catch (Exception e) {
                failed++;
                log.error("[Detect] Exceção ao processar hash={} título='{}': {}",
                        incoming.externalIdHash(), incoming.title(), e.getMessage(), e);
            }
        }

        final long t1 = System.nanoTime();
        log.info("[Detect] Concluído: processed={}, new={}, updatedRelevant={}, unchanged={}, skipped={}, failed={}, duração={} ms",
                processed, created, updatedRelevant, unchanged, skipped, failed, durMs(t0, t1));

        return newOnes;
    }

    private Course safeUpsert(Course c) {
        try {
            return courseRepository.upsert(c);
        } catch (Exception e) {
            log.error("[Detect] Falha no upsert hash={} título='{}': {}", c.externalIdHash(), c.title(), e.getMessage(), e);
            return null;
        }
    }

    private void safeSnapshot(Course course, String statusText, String priceText) {
        try {
            snapshotRepository.saveSnapshot(course, null, statusText, priceText);
        } catch (Exception e) {
            log.error("[Detect] Falha ao salvar snapshot courseId={} hash={} título='{}': {}",
                    course.id(), course.externalIdHash(), course.title(), e.getMessage(), e);
        }
    }

    private static long durMs(long tStart, long tEnd) {
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0, tEnd - tStart));
    }
}
