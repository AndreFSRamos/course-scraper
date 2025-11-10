package tech.andrefsramos.course_scraper.core.application.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.andrefsramos.course_scraper.core.application.CollectCoursesUseCase;
import tech.andrefsramos.course_scraper.core.application.DetectChangesUseCase;
import tech.andrefsramos.course_scraper.core.application.NotifyNewCoursesUseCase;
import tech.andrefsramos.course_scraper.core.domain.Course;
import tech.andrefsramos.course_scraper.core.domain.Platform;
import tech.andrefsramos.course_scraper.core.ports.PlatformRepository;
import tech.andrefsramos.course_scraper.core.ports.ScraperPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Orquestra a coleta:
 *  - resolve o scraper compatível
 *  - busca lote normalizado
 *  - delega o diff/upsert/snapshot ao DetectChangesUseCase
 *  - delega a notificação ao NotifyNewCoursesUseCase
 *
 * Para "collectAllEnabled", recebe a lista de nomes de plataformas habilitadas via construtor.
 */
public class CollectCoursesService implements CollectCoursesUseCase {
    private static final Logger log = LoggerFactory.getLogger(CollectCoursesService.class);

    private final List<ScraperPort> scrapers;
    private final DetectChangesUseCase detectChanges;
    private final NotifyNewCoursesUseCase notifyNew;
    private final List<String> enabledPlatformNames; // ex.: ["evg","fgv","sebrae"]
    private final int maxPagesPerRun;
    private final PlatformRepository platformRepository;

    public CollectCoursesService(
            List<ScraperPort> scrapers,
            DetectChangesUseCase detectChanges,
            NotifyNewCoursesUseCase notifyNew,
            List<String> enabledPlatformNames,
            int maxPagesPerRun,
            PlatformRepository platformRepository
    ) {
        this.scrapers = scrapers != null ? scrapers : List.of();
        this.detectChanges = detectChanges;
        this.notifyNew = notifyNew;
        this.enabledPlatformNames = enabledPlatformNames != null ? enabledPlatformNames : List.of();
        this.maxPagesPerRun = maxPagesPerRun > 0 ? maxPagesPerRun : 100;
        this.platformRepository = platformRepository;
    }

    @Override
    public void collectForPlatform(String platformName) {
        if (platformName == null || platformName.isBlank()) return;

        Platform p = new Platform(null, platformName, "", true);
        ScraperPort scraper = resolveScraper(p);
        if (scraper == null) {
            log.warn("Nenhum scraper compatível para platform={}", platformName);
            return;
        }

        List<Course> batch = safeFetch(scraper, p, maxPagesPerRun);
        log.info("Scraper result platform={}, items={}", platformName, batch.size());
        if (batch.isEmpty()) return;

        Long pid = platformRepository.findIdByNameIgnoreCase(platformName)
                .orElseThrow(() -> new IllegalStateException("Platform not found: " + platformName));

        List<Course> enriched = batch.stream().map(c -> withPlatformId(c, pid)).toList();

        List<Course> newOnes = detectChanges.computeNewOrUpdated(enriched);
        log.info("DetectChanges platform={}, novos={}", platformName, newOnes.size());
        notifyNew.notifyNew(platformName, newOnes);
    }


    @Override
    public void collectAllEnabled() {
        for (String name : enabledPlatformNames) {
            collectForPlatform(name);
        }
    }

    private Course withPlatformId(Course c, Long pid) {
        return new Course(
                c.id(),
                pid,
                c.externalIdHash(),
                c.title(),
                c.url(),
                c.provider(),
                c.area(),
                c.freeFlag(),
                c.startDate(),
                c.endDate(),
                c.statusText(),
                c.priceText(),
                c.createdAt(),
                c.updatedAt()
        );
    }

    private ScraperPort resolveScraper(Platform platform) {
        for (ScraperPort s : scrapers) {
            try {
                if (s.supports(platform)) return s;
            } catch (Exception ignored) { }
        }
        return null;
    }

    private List<Course> safeFetch(ScraperPort scraper, Platform p, int maxPages) {
        try {
            List<Course> list = scraper.fetchBatch(p, maxPages);
            return list != null ? list.stream().filter(Objects::nonNull).toList() : List.of();
        } catch (Exception e) {
            log.error("Erro no scraper platform={}", p.name(), e);
            return List.of();
        }
    }
}
