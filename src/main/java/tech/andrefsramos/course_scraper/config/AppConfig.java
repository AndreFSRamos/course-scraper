package tech.andrefsramos.course_scraper.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.andrefsramos.course_scraper.adapters.outbound.notify.CompositeNotificationPort;
import tech.andrefsramos.course_scraper.core.application.*;
import tech.andrefsramos.course_scraper.core.application.impl.*;
import tech.andrefsramos.course_scraper.core.ports.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/*
 * Finalidade

 * Orquestra a composição dos casos de uso (UseCases) e portas (Ports), criando beans Spring
 * com dependências explicitadas via construtor. Centraliza flags de recursos e parâmetros de
 * execução obtidos via propriedades (application.yml / env).

 * Visão Geral dos Beans

 * - DetectChangesUseCase: comuta CourseRepository + SnapshotRepository para calcular diffs e
 *   registrar snapshots.
 * - NotificationPort (Composite): agrega múltiplos adapters (Discord, Telegram, etc.) e
 *   distribui notificações.
 * - PendingNotifierUseCase: varre cursos pendentes (notifiedAt = null) e envia em lotes.
 * - NotifyNewCoursesUseCase: envia notificações de “novos cursos” por execução de coleta.
 * - CollectCoursesUseCase: coordena scrapers habilitados, detecta mudanças e notifica, respeitando
 *   limites de paginação por plataforma. Usa PlatformRepository para resolver IDs.
 * - QueryLatestCoursesUseCase: leitura paginada dos cursos mais recentes (filtros opcionais).
 */

@Configuration
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    /* ============================= DetectChangesUseCase ============================= */

    @Bean
    DetectChangesUseCase detectChangesUseCase(
            CourseRepository courseRepository,
            SnapshotRepository snapshotRepository
    ) {
        final long t0 = System.nanoTime();
        try {
            Objects.requireNonNull(courseRepository, "courseRepository is required");
            Objects.requireNonNull(snapshotRepository, "snapshotRepository is required");

            DetectChangesUseCase bean = new DetectChangesService(courseRepository, snapshotRepository);
            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.info("[AppConfig] DetectChangesUseCase inicializado (tookMs={}ms)", tookMs);
            return bean;
        } catch (RuntimeException e) {
            log.error("[AppConfig] Erro ao criar DetectChangesUseCase: {}", e.getMessage(), e);
            throw e;
        }
    }

    /* ============================= NotificationPort (Composite) ============================= */

    @Bean
    public NotificationPort notificationPort(List<NotificationPort> ports) {
        final long t0 = System.nanoTime();
        try {
            List<NotificationPort> delegates = (ports == null) ? List.of() : ports;
            delegates = delegates.stream()
                    .filter(p -> !(p instanceof CompositeNotificationPort))
                    .toList();

            if (delegates.isEmpty()) {
                log.warn("[AppConfig] Nenhum NotificationPort concreto encontrado. Notificações ficarão desabilitadas.");
            } else {
                log.info("[AppConfig] NotificationPorts concretos detectados: count={}", delegates.size());
            }

            NotificationPort bean = new CompositeNotificationPort(delegates);
            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.info("[AppConfig] NotificationPort (Composite) inicializado (adapters={}) tookMs={}ms",
                    delegates.size(), tookMs);
            return bean;
        } catch (RuntimeException e) {
            log.error("[AppConfig] Erro ao criar NotificationPort composite: {}", e.getMessage(), e);
            throw e;
        }
    }

    /* ============================= PendingNotifierUseCase ============================= */

    @Bean
    PendingNotifierUseCase pendingNotifierUseCase(
            CourseRepository courseRepository,
            NotificationPort notificationPort,
            @Value("${app.notify.perMessage:8}") int perMessage,
            @Value("${app.notify.maxNewCoursesPerRunPerPlatform:40}") int maxPerRun
    ) {
        final long t0 = System.nanoTime();
        try {
            if (perMessage < 1) {
                log.warn("[AppConfig] app.notify.perMessage={} inválido. Ajustando para 1.", perMessage);
                perMessage = 1;
            }
            if (maxPerRun < 1) {
                log.warn("[AppConfig] app.notify.maxNewCoursesPerRunPerPlatform={} inválido. Ajustando para 1.", maxPerRun);
                maxPerRun = 1;
            }

            PendingNotifierUseCase bean = new PendingNotifierService(
                    courseRepository, notificationPort, perMessage, maxPerRun);

            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.info("[AppConfig] PendingNotifierUseCase inicializado (perMessage={}, maxPerRun={}) tookMs={}ms",
                    perMessage, maxPerRun, tookMs);
            return bean;
        } catch (RuntimeException e) {
            log.error("[AppConfig] Erro ao criar PendingNotifierUseCase: {}", e.getMessage(), e);
            throw e;
        }
    }

    /* ============================= NotifyNewCoursesUseCase ============================= */

    @Bean
    NotifyNewCoursesUseCase notifyNewCoursesUseCase(
            NotificationPort notificationPort,
            @Value("${app.notify.maxNewCoursesPerRunPerPlatform:40}") int maxPerRun,
            @Value("${app.notify.perMessage:8}") int perMessage,
            @Value("${app.api.baseUrl:}") String apiBaseUrl
    ) {
        final long t0 = System.nanoTime();
        try {
            if (perMessage < 1) {
                log.warn("[AppConfig] app.notify.perMessage={} inválido. Ajustando para 1.", perMessage);
                perMessage = 1;
            }
            if (maxPerRun < 1) {
                log.warn("[AppConfig] app.notify.maxNewCoursesPerRunPerPlatform={} inválido. Ajustando para 1.", maxPerRun);
                maxPerRun = 1;
            }

            NotifyNewCoursesUseCase bean = new NotifyNewCoursesService(
                    notificationPort, maxPerRun, perMessage, apiBaseUrl);

            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.info("[AppConfig] NotifyNewCoursesUseCase inicializado (perMessage={}, maxPerRun={}, apiBaseUrl='{}') tookMs={}ms",
                    perMessage, maxPerRun, (apiBaseUrl == null ? "" : apiBaseUrl), tookMs);
            return bean;
        } catch (RuntimeException e) {
            log.error("[AppConfig] Erro ao criar NotifyNewCoursesUseCase: {}", e.getMessage(), e);
            throw e;
        }
    }

    /* ============================= CollectCoursesUseCase ============================= */

    @Bean
    CollectCoursesUseCase collectCoursesUseCase(
            List<ScraperPort> scrapers,
            DetectChangesUseCase detectChangesUseCase,
            NotifyNewCoursesUseCase notifyNewCoursesUseCase,
            @Value("${app.features.platforms.evg:true}") boolean evgEnabled,
            @Value("${app.features.platforms.fgv:true}") boolean fgvEnabled,
            @Value("${app.features.platforms.sebrae:true}") boolean sebraeEnabled,
            @Value("${app.scrape.connectors.evg.maxPagesPerRun:100}") int evgMax,
            @Value("${app.scrape.connectors.fgv.maxPagesPerRun:100}") int fgvMax,
            @Value("${app.scrape.connectors.sebrae.maxPagesPerRun:100}") int sebraeMax,
            PlatformRepository platformRepository
    ) {
        final long t0 = System.nanoTime();
        try {
            List<String> enabled = new ArrayList<>();
            if (evgEnabled) enabled.add("evg");
            if (fgvEnabled) enabled.add("fgv");
            if (sebraeEnabled) enabled.add("sebrae");

            if (enabled.isEmpty()) {
                log.warn("[AppConfig] Nenhuma plataforma habilitada (app.features.platforms.*). Coleta ficará inoperante.");
            }

            int maxPages = Math.max(evgMax, Math.max(fgvMax, sebraeMax));
            if (maxPages < 1) {
                log.warn("[AppConfig] maxPages calculado={} inválido. Ajustando para 1.", maxPages);
                maxPages = 1;
            }

            int scraperCount = (scrapers == null) ? 0 : scrapers.size();
            if (scraperCount == 0) {
                log.warn("[AppConfig] Nenhum ScraperPort detectado no contexto Spring.");
            }

            CollectCoursesUseCase bean = new CollectCoursesService(
                    scrapers,
                    detectChangesUseCase,
                    notifyNewCoursesUseCase,
                    enabled,
                    maxPages,
                    platformRepository
            );

            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.info("[AppConfig] CollectCoursesUseCase inicializado (enabled={}, evgMax={}, fgvMax={}, sebraeMax={}, maxPages={}, scrapers={}) tookMs={}ms",
                    enabled, evgMax, fgvMax, sebraeMax, maxPages, scraperCount, tookMs);
            return bean;
        } catch (RuntimeException e) {
            log.error("[AppConfig] Erro ao criar CollectCoursesUseCase: {}", e.getMessage(), e);
            throw e;
        }
    }

    /* ============================= QueryLatestCoursesUseCase ============================= */

    @Bean
    QueryLatestCoursesUseCase queryLatestCoursesUseCase(CourseRepository courseRepository) {
        final long t0 = System.nanoTime();
        try {
            QueryLatestCoursesUseCase bean = new QueryLatestCoursesService(courseRepository);
            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.info("[AppConfig] QueryLatestCoursesUseCase inicializado (tookMs={}ms)", tookMs);
            return bean;
        } catch (RuntimeException e) {
            log.error("[AppConfig] Erro ao criar QueryLatestCoursesUseCase: {}", e.getMessage(), e);
            throw e;
        }
    }
}