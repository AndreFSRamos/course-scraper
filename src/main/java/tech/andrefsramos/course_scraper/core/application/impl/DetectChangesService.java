package tech.andrefsramos.course_scraper.core.application.impl;

import tech.andrefsramos.course_scraper.core.application.DetectChangesUseCase;
import tech.andrefsramos.course_scraper.core.domain.Course;
import tech.andrefsramos.course_scraper.core.domain.CourseChangePolicy;
import tech.andrefsramos.course_scraper.core.ports.CourseRepository;
import tech.andrefsramos.course_scraper.core.ports.SnapshotRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Percorre o lote normalizado de cursos, compara com o estado atual no repositório,
 * realiza upsert + snapshot e retorna a lista de NOVOS cursos (para notificação).
 * Se desejar, pode alterar para retornar também os "UPDATED".
 */
public class DetectChangesService implements DetectChangesUseCase {

    private final CourseRepository courseRepository;
    private final SnapshotRepository snapshotRepository;

    public DetectChangesService(CourseRepository courseRepository,
                                SnapshotRepository snapshotRepository) {
        this.courseRepository = courseRepository;
        this.snapshotRepository = snapshotRepository;
    }

    @Override
    public List<Course> computeNewOrUpdated(List<Course> batch) {
        List<Course> newOnes = new ArrayList<>();

        for (Course incoming : batch) {
            Optional<Course> existingOpt = courseRepository.findByHash(incoming.externalIdHash());

            if (existingOpt.isEmpty()) {
                // Novo curso
                Course saved = courseRepository.upsert(incoming);
                snapshotRepository.saveSnapshot(saved, /* raw_json */ null, saved.statusText(), saved.priceText());
                newOnes.add(saved);
            } else {
                Course existing = existingOpt.get();
                boolean relevant = CourseChangePolicy.isRelevantUpdate(existing, incoming);
                if (relevant) {
                    Course saved = courseRepository.upsert(incoming);
                    snapshotRepository.saveSnapshot(saved, /* raw_json */ null, saved.statusText(), saved.priceText());
                    // Se desejar notificar updates também, pode adicioná-los a uma outra lista.
                } else {
                    // Mesmo sem mudança relevante, ainda podemos registrar snapshot leve
                    snapshotRepository.saveSnapshot(existing, /* raw_json */ null, incoming.statusText(), incoming.priceText());
                }
            }
        }
        return newOnes;
    }
}
