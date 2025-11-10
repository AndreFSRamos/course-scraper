package tech.andrefsramos.course_scraper.adapters.inbound.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tech.andrefsramos.course_scraper.core.application.CollectCoursesUseCase;

/**
 * CollectScheduler

 * Descrição geral:
 * - Responsável por executar automaticamente o processo de coleta de cursos
 *   de todas as plataformas habilitadas (por exemplo, EVG, FGV e SEBRAE) em intervalos regulares.

 * Responsabilidades:
 * - Acionar o caso de uso {@link CollectCoursesUseCase} em um agendamento periódico.
 * - Garantir que a coleta seja feita de forma confiável, com logs de auditoria e
 *   tratamento de exceções.

 * Agendamento:
 * - O método {@link #runAll()} é executado a cada 12 horas (PT12H = período ISO-8601).
 *   Isso significa que o próximo ciclo só inicia após o término completo do anterior.

 * Fluxo resumido:
 * 1. Scheduler dispara o método {@code runAll()} automaticamente.
 * 2. O método invoca {@link CollectCoursesUseCase#collectAllEnabled()} para
 *    realizar a coleta em todas as plataformas configuradas.
 * 3. Logs informam início, sucesso, tempo de execução e falhas, se houver.
 */

@Component
public class CollectScheduler {

    private static final Logger log = LoggerFactory.getLogger(CollectScheduler.class);
    private final CollectCoursesUseCase collect;

    public CollectScheduler(CollectCoursesUseCase collect) {this.collect = collect;}

    @Scheduled(fixedDelayString = "PT12H")
    public void runAll() {
        long start = System.nanoTime();
        log.info("CollectScheduler: início da execução automática de coleta (todas as plataformas habilitadas).");

        try {
            collect.collectAllEnabled();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.info("CollectScheduler: execução concluída com sucesso (elapsedMs={} ms).", elapsedMs);
        } catch (Exception ex) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.error("CollectScheduler: erro durante a execução automática (elapsedMs={} ms).", elapsedMs, ex);
        }
    }
}