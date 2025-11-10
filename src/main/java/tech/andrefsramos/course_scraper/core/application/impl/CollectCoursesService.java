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
import java.util.concurrent.TimeUnit;

/*
 * Finalidade

 * Orquestra o fluxo de coleta e publicação de cursos por plataforma:
 *  1) Resolve o {@link ScraperPort} compatível com a {@link Platform}.
 *  2) Executa a coleta paginada (batch normalizado) via {@code fetchBatch}.
 *  3) Enriquecer os cursos com o ID da plataforma (chave estrangeira).
 *  4) Delegar a detecção de novos/atualizados a {@link DetectChangesUseCase}.
 *  5) Delegar a notificação incremental a {@link NotifyNewCoursesUseCase}.

 */
public class CollectCoursesService implements CollectCoursesUseCase {
    private static final Logger log = LoggerFactory.getLogger(CollectCoursesService.class);

    private final List<ScraperPort> scrapers;
    private final DetectChangesUseCase detectChanges;
    private final NotifyNewCoursesUseCase notifyNew;
    private final List<String> enabledPlatformNames;
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
        this.maxPagesPerRun = Math.max(maxPagesPerRun, 1);
        this.platformRepository = platformRepository;
    }

    @Override
    public void collectForPlatform(String platformName) {
        final long t0 = System.nanoTime();

        if (platformName == null || platformName.isBlank()) {
            log.warn("[Collect] Nome de plataforma vazio/nulo. Ignorando execução.");
            return;
        }

        final String pname = platformName.trim();
        log.info("[Collect] Iniciando coleta para platform={} (maxPagesPerRun={})", pname, maxPagesPerRun);

        final long tResolve0 = System.nanoTime();
        Platform platform = new Platform(null, pname, "", true);
        ScraperPort scraper = resolveScraper(platform);
        final long tResolve1 = System.nanoTime();

        if (scraper == null) {
            log.warn("[Collect] Nenhum ScraperPort compatível encontrado para platform={}. Scrapers registrados={}",
                    pname, scrapers.size());
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("[Collect] Scraper resolvido: {} para platform={} ({} ms)",
                    scraper.getClass().getSimpleName(), pname, durMs(tResolve0, tResolve1));
        }

        final long tFetch0 = System.nanoTime();
        List<Course> batch = safeFetch(scraper, platform, maxPagesPerRun);
        final long tFetch1 = System.nanoTime();
        log.info("[Collect] Resultado scraper platform={}, items={} ({} ms)",
                pname, batch.size(), durMs(tFetch0, tFetch1));

        if (batch.isEmpty()) {
            log.warn("[Collect] Batch vazio para platform={} — nada a processar.", pname);
            return;
        }

        final long tPid0 = System.nanoTime();
        final Long pid;
        try {
            pid = platformRepository.findIdByNameIgnoreCase(pname)
                    .orElseThrow(() -> new IllegalStateException("Platform not found: " + pname));
        } catch (Exception e) {
            log.error("[Collect] Falha ao resolver Platform.id para platform={}. Causa={}", pname, e.getMessage(), e);
            return;
        }
        final long tPid1 = System.nanoTime();
        if (log.isDebugEnabled()) {
            log.debug("[Collect] Platform.id={} resolvido para platform={} ({} ms)", pid, pname, durMs(tPid0, tPid1));
        }

        final long tEnrich0 = System.nanoTime();
        List<Course> enriched = batch.stream().filter(Objects::nonNull).map(c -> withPlatformId(c, pid)).toList();
        final long tEnrich1 = System.nanoTime();
        if (log.isDebugEnabled()) {
            log.debug("[Collect] Enriquecimento concluído platform={}, count={} ({} ms)",
                    pname, enriched.size(), durMs(tEnrich0, tEnrich1));
        }

        final long tDiff0 = System.nanoTime();
        List<Course> newOrUpdated;
        try {
            newOrUpdated = detectChanges.computeNewOrUpdated(enriched);
        } catch (Exception e) {
            log.error("[Collect] Erro no DetectChanges para platform={}. Causa={}", pname, e.getMessage(), e);
            return;
        }

        final long tDiff1 = System.nanoTime();
        log.info("[Collect] DetectChanges finalizado platform={}, novos/atualizados={} ({} ms)",
                pname, newOrUpdated.size(), durMs(tDiff0, tDiff1));

        if (!newOrUpdated.isEmpty()) {
            final long tNotify0 = System.nanoTime();
            try {
                notifyNew.notifyNew(pname, newOrUpdated);
            } catch (Exception e) {
                log.error("[Collect] Erro no NotifyNew para platform={}. cursos={} Causa={}",
                        pname, newOrUpdated.size(), e.getMessage(), e);
            } finally {
                final long tNotify1 = System.nanoTime();
                log.info("[Collect] Notificação concluída platform={} ({} ms)", pname, durMs(tNotify0, tNotify1));
            }
        } else {
            log.info("[Collect] Nenhuma novidade para notificar em platform={}.", pname);
        }

        log.info("[Collect] FIM platform={} ({} ms totais)", pname, durMs(t0, System.nanoTime()));
    }

    @Override
    public void collectAllEnabled() {
        final long t0 = System.nanoTime();
        if (enabledPlatformNames.isEmpty()) {
            log.warn("[CollectAll] Nenhuma plataforma habilitada. Verifique app.features.platforms.*");
            return;
        }

        log.info("[CollectAll] Iniciando coleta para {} plataformas: {}", enabledPlatformNames.size(), enabledPlatformNames);

        int ok = 0, fail = 0;
        List<String> failures = new ArrayList<>();

        for (String name : enabledPlatformNames) {
            final long ti = System.nanoTime();
            try {
                collectForPlatform(name);
                ok++;
            } catch (Exception e) {
                fail++;
                failures.add(name + " (" + e.getClass().getSimpleName() + ")");
                log.error("[CollectAll] Falha ao coletar platform={}. Causa={}", name, e.getMessage(), e);
            } finally {
                log.info("[CollectAll] Plataforma processada={} em {} ms", name, durMs(ti, System.nanoTime()));
            }
        }

        log.info("[CollectAll] FIM: ok={}, fail={}, totalPlataformas={}, falhas={}, duração={} ms",
                ok, fail, enabledPlatformNames.size(), failures, durMs(t0, System.nanoTime()));
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
        if (scrapers.isEmpty()) {
            log.warn("[Collect] Lista de scrapers vazia — nenhum adaptador registrado.");
            return null;
        }
        for (ScraperPort s : scrapers) {
            try {
                if (s.supports(platform)) return s;
            } catch (Exception e) {
                log.warn("[Collect] Erro ao testar supports() em {} para platform={}: {}",
                        s.getClass().getSimpleName(), platform.name(), e.toString());
            }
        }
        return null;
    }

    private List<Course> safeFetch(ScraperPort scraper, Platform p, int maxPages) {
        try {
            final long t0 = System.nanoTime();
            List<Course> list = scraper.fetchBatch(p, maxPages);
            final long t1 = System.nanoTime();
            int count = (list == null) ? 0 : list.size();
            log.debug("[Collect] fetchBatch concluído por {} para platform={} -> {} itens ({} ms)",
                    scraper.getClass().getSimpleName(), p.name(), count, durMs(t0, t1));
            return list != null ? list.stream().filter(Objects::nonNull).toList() : List.of();
        } catch (Exception e) {
            log.error("[Collect] Exceção em fetchBatch para platform={}: {}", p.name(), e.getMessage(), e);
            return List.of();
        }
    }

    private static long durMs(long tStart, long tEnd) {
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0, tEnd - tStart));
    }
}
