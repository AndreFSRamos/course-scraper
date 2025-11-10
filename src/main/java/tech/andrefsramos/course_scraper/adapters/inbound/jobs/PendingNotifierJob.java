package tech.andrefsramos.course_scraper.adapters.inbound.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tech.andrefsramos.course_scraper.core.application.PendingNotifierUseCase;

import java.util.List;

/**
 * PendingNotifierJob
 *
 * Descrição geral:
 * - Tarefa agendada responsável por enviar notificações pendentes das plataformas
 *   de cursos (FGV, EVG, SEBRAE, etc.) para os canais configurados (por exemplo, Discord).
 * - Atua como um despachante periódico, garantindo que todas as notificações acumuladas
 *   sejam processadas e enviadas em intervalos regulares.
 *
 * Responsabilidades:
 * - Recuperar as plataformas habilitadas via configuração de ambiente (propriedade app.platforms.enabled).
 * - Invocar o caso de uso {@link PendingNotifierUseCase} para processar as notificações pendentes.
 * - Garantir logs de execução, sucesso e falha a cada ciclo de execução.
 *
 * Fluxo resumido:
 * 1. O método {@code flushPending()} é executado periodicamente conforme o cron ou delay configurado.
 * 2. Invoca {@link PendingNotifierUseCase#flushAll(List)} passando a lista de plataformas habilitadas.
 * 3. Registra logs de início, fim, duração e eventuais falhas.
 */
@Component
public class PendingNotifierJob {

    private static final Logger log = LoggerFactory.getLogger(PendingNotifierJob.class);

    private final PendingNotifierUseCase useCase;
    private final List<String> platforms;

    public PendingNotifierJob(
            PendingNotifierUseCase useCase,
            @Value("#{'${app.platforms.enabled:evg,fgv,sebrae}'.split(',')}") List<String> platforms
    ) {
        this.useCase = useCase;
        this.platforms = platforms.stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        log.info("PendingNotifierJob: plataformas habilitadas={}", this.platforms);
    }

    @Scheduled(fixedDelayString = "${app.notify.pending.fixedDelayMs:60000}")
    public void flushPending() {
        long start = System.nanoTime();
        log.info("PendingNotifierJob: início da execução (platforms={})", platforms);

        if (platforms.isEmpty()) {
            log.warn("PendingNotifierJob: nenhuma plataforma configurada — execução ignorada");
            return;
        }

        try {
            useCase.flushAll(platforms);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.info("PendingNotifierJob: execução concluída com sucesso (elapsedMs={} ms, platforms={})",
                    elapsedMs, platforms);
        } catch (Exception ex) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.error("PendingNotifierJob: erro ao executar flush (elapsedMs={} ms, platforms={})",
                    elapsedMs, platforms, ex);
        }
    }
}
