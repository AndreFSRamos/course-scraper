package tech.andrefsramos.course_scraper.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.andrefsramos.course_scraper.adapters.outbound.notify.CompositeNotificationPort;
import tech.andrefsramos.course_scraper.core.application.*;
import tech.andrefsramos.course_scraper.core.application.impl.*;
import tech.andrefsramos.course_scraper.core.ports.*;

import java.util.List;

@Configuration
public class AppConfig {

    // --- DetectChangesUseCase
    @Bean
    DetectChangesUseCase detectChangesUseCase(CourseRepository courseRepository, SnapshotRepository snapshotRepository) {
        return new DetectChangesService(courseRepository, snapshotRepository);
    }

    @Bean
    public NotificationPort notificationPort(List<NotificationPort> ports) {
        // 'ports' conterá apenas os adapters concretos (Telegram, Discord), já que o Composite não é @Component
        return new CompositeNotificationPort(ports == null ? List.of() : ports);
    }

    @Bean
    PendingNotifierUseCase pendingNotifierUseCase(
            CourseRepository courseRepository,
            NotificationPort notificationPort,
            @Value("${app.notify.perMessage:8}") int perMessage,
            @Value("${app.notify.maxNewCoursesPerRunPerPlatform:40}") int maxPerRun
    ) {
        return new PendingNotifierService(courseRepository, notificationPort, perMessage, maxPerRun);
    }

    // --- NotifyNewCoursesUseCase
    @Bean
    NotifyNewCoursesUseCase notifyNewCoursesUseCase(
            NotificationPort notificationPort,
            @Value("${app.notify.maxNewCoursesPerRunPerPlatform:40}") int maxPerRun,
            @Value("${app.notify.perMessage:8}") int perMessage,
            @Value("${app.api.baseUrl:}") String apiBaseUrl
    ) {
        return new NotifyNewCoursesService(notificationPort, maxPerRun, perMessage, apiBaseUrl);
    }

    // --- CollectCoursesUseCase
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
        var enabled = new java.util.ArrayList<String>();
        if (evgEnabled) enabled.add("evg");
        if (fgvEnabled) enabled.add("fgv");
        if (sebraeEnabled) enabled.add("sebrae");

        int maxPages = Math.max(evgMax, Math.max(fgvMax, sebraeMax));

        return new CollectCoursesService(
                scrapers,
                detectChangesUseCase,
                notifyNewCoursesUseCase,
                enabled,
                maxPages,
                platformRepository
        );
    }

    // Também crie @Bean para QueryLatestCoursesUseCase (já fizemos anteriormente)
    @Bean
    QueryLatestCoursesUseCase queryLatestCoursesUseCase(CourseRepository courseRepository) {
        return new QueryLatestCoursesService(courseRepository);
    }
}
