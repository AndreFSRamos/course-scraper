package tech.andrefsramos.course_scraper.core.application.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.andrefsramos.course_scraper.core.application.PendingNotifierUseCase;
import tech.andrefsramos.course_scraper.core.domain.Course;
import tech.andrefsramos.course_scraper.core.ports.CourseRepository;
import tech.andrefsramos.course_scraper.core.ports.NotificationPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/*
 * Finalidade
 *
 * Esvazia a fila de cursos pendentes de notificação por plataforma (ou para todas as plataformas),
 * enviando mensagens em lotes ao {@link NotificationPort} e marcando os itens como notificados
 * apenas quando o envio do respectivo lote for bem-sucedido.

 * Fluxo de flushPlatform()

 * 1) Busca cursos pendentes da plataforma, respeitando um teto por execução (maxPerRun).
 * 2) Agrupa os cursos em lotes de tamanho perMessage.
 * 3) Para cada lote:
 *    - Tenta enviar via notificationPort.notifyNewCourses().
 *    - Em caso de sucesso, acumula os IDs enviados para posterior marcação.
 *    - Em caso de erro, registra log e segue para o próximo lote (não marca notificado).
 *    - Pausa breve entre lotes para respeitar rate-limit.
 * 4) Marca no repositório apenas os IDs efetivamente enviados (markNotified).
 */

public class PendingNotifierService implements PendingNotifierUseCase {

    private static final Logger log = LoggerFactory.getLogger(PendingNotifierService.class);

    private final CourseRepository courseRepo;
    private final NotificationPort notificationPort;
    private final int perMessage;
    private final int maxPerRun;

    public PendingNotifierService(
            CourseRepository courseRepo,
            NotificationPort notificationPort,
            int perMessage,
            int maxPerRun
    ) {
        this.courseRepo = courseRepo;
        this.notificationPort = notificationPort;
        this.perMessage = Math.max(perMessage, 1);
        this.maxPerRun  = Math.max(maxPerRun, 1);
    }

    @Override
    public void flushPlatform(String platformName) {
        final long t0 = System.nanoTime();

        if (platformName == null || platformName.isBlank()) {
            log.warn("[Pending] Nome de plataforma vazio — ignorando flush.");
            return;
        }

        List<Course> pending;
        try {
            pending = courseRepo.findPendingToNotify(platformName, maxPerRun);
        } catch (Exception e) {
            log.error("[Pending] Falha ao consultar pendentes platform={}: {}", platformName, e.getMessage(), e);
            return;
        }

        if (pending.isEmpty()) {
            log.info("[Pending] Nenhum pendente para platform={} (maxPerRun={})", platformName, maxPerRun);
            return;
        }

        log.info("[Pending] Iniciando flush platform={} pendentes={} perMessage={} maxPerRun={}",
                platformName, pending.size(), perMessage, maxPerRun);

        final var sentIds = new ArrayList<Long>();
        final var buffer  = new ArrayList<Course>();
        int batches = 0, failedBatches = 0;

        for (var c : pending) {
            buffer.add(c);
            if (buffer.size() == perMessage) {
                batches++;
                if (sendBatch(platformName, buffer, sentIds)) {
                    if (log.isDebugEnabled()) {
                        log.debug("[Pending] Lote OK platform={} size={} ids={}",
                                platformName, buffer.size(),
                                buffer.stream().map(Course::id).filter(Objects::nonNull).toList());
                    }
                } else {
                    failedBatches++;
                }
                buffer.clear();
                sleep(250);
            }
        }

        if (!buffer.isEmpty()) {
            batches++;
            if (sendBatch(platformName, buffer, sentIds)) {
                if (log.isDebugEnabled()) {
                    log.debug("[Pending] Lote final OK platform={} size={} ids={}",
                            platformName, buffer.size(),
                            buffer.stream().map(Course::id).filter(Objects::nonNull).toList());
                }
            } else {
                failedBatches++;
            }
        }

        try {
            if (!sentIds.isEmpty()) {
                courseRepo.markNotified(sentIds);
                log.info("[Pending] Marcados como notificados platform={} totalIds={}", platformName, sentIds.size());
            } else {
                log.warn("[Pending] Nenhum ID marcado como notificado platform={} (envios falharam ou não havia IDs).", platformName);
            }
        } catch (Exception e) {
            log.error("[Pending] Falha ao marcar notificados platform={} ids={} -> {}",
                    platformName, sentIds.size(), e.getMessage(), e);
        }

        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        log.info("[Pending] FIM platform={} pendentes={} enviados={} lotes={} falhos={} duração={} ms",
                platformName, pending.size(), sentIds.size(), batches, failedBatches, elapsedMs);
    }

    @Override
    public void flushAll(List<String> platformNames) {
        if (platformNames == null || platformNames.isEmpty()) {
            log.warn("[Pending] Lista de plataformas vazia — ignorando flushAll.");
            return;
        }
        log.info("[Pending] flushAll -> plataformas={}", platformNames);
        for (String p : platformNames) {
            try {
                flushPlatform(p);
            } catch (Exception e) {
                log.error("[Pending] Erro inesperado em flushPlatform platform={}: {}", p, e.getMessage(), e);
            }
        }
    }

    private boolean sendBatch(String platformName, List<Course> batch, List<Long> sentIds) {
        try {
            notificationPort.notifyNewCourses(platformName, batch);
            sentIds.addAll(batch.stream().map(Course::id).filter(Objects::nonNull).toList());
            return true;
        } catch (Exception e) {
            log.error("[Pending] Falha ao notificar lote platform={} size={} -> {}",
                    platformName, batch.size(), e.getMessage(), e);
            return false;
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
