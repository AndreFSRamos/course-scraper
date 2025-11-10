package tech.andrefsramos.course_scraper.core.application.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.andrefsramos.course_scraper.core.application.NotifyNewCoursesUseCase;
import tech.andrefsramos.course_scraper.core.domain.Course;
import tech.andrefsramos.course_scraper.core.ports.NotificationPort;
import java.util.List;
import java.util.concurrent.TimeUnit;

/*
 * Finalidade

 * Responsável por notificar novos cursos coletados às implementações do {@link NotificationPort}.
 * Divide a lista de cursos recém-descobertos em lotes menores e envia as mensagens de forma controlada.

 * Fluxo Principal

 * 1. Recebe lista de novos cursos para determinada plataforma.
 * 2. Aplica limite máximo por execução (maxNewPerRun).
 * 3. Quebra a lista em lotes de tamanho definido (perMessage).
 * 4. Para cada lote:
 *    - Chama {@link NotificationPort#notifyNewCourses(String, List)}.
 *    - Aguarda brevemente entre mensagens para respeitar limites de webhook.
 * 5. Se houver cursos restantes além do limite máximo, envia resumo via {@link NotificationPort#notifySummary(String, int, String)}.
 */

public class NotifyNewCoursesService implements NotifyNewCoursesUseCase {

    private static final Logger log = LoggerFactory.getLogger(NotifyNewCoursesService.class);

    private final NotificationPort notificationPort;
    private final int maxNewPerRun;
    private final int perMessage;
    private final String apiBaseUrl;

    public NotifyNewCoursesService(
            NotificationPort notificationPort,
            int maxNewPerRun,
            int perMessage,
            String apiBaseUrl
    ) {
        this.notificationPort = notificationPort;
        this.maxNewPerRun = Math.max(0, maxNewPerRun);
        this.perMessage = Math.max(1, perMessage);
        this.apiBaseUrl = apiBaseUrl != null ? apiBaseUrl : "";
    }

    @Override
    public void notifyNew(String platformName, List<Course> newOnes) {
        final long t0 = System.nanoTime();

        if (platformName == null || platformName.isBlank()) {
            log.warn("[Notify] Nome de plataforma nulo/vazio — ignorando notificação.");
            return;
        }

        if (newOnes == null || newOnes.isEmpty()) {
            log.info("[Notify] Nenhum novo curso a notificar para platform={}.", platformName);
            return;
        }

        int cap = (maxNewPerRun > 0) ? Math.min(maxNewPerRun, newOnes.size()) : newOnes.size();
        List<Course> head = newOnes.subList(0, cap);

        log.info("[Notify] Iniciando notificação platform={} total={} (maxPorExecução={} perMessage={})",
                platformName, newOnes.size(), maxNewPerRun, perMessage);

        int sent = 0;
        int failed = 0;

        for (int i = 0; i < head.size(); i += perMessage) {
            List<Course> slice = head.subList(i, Math.min(i + perMessage, head.size()));
            try {
                if (log.isDebugEnabled()) {
                    log.debug("[Notify] Enviando lote {}–{} ({} cursos) para platform={}",
                            i + 1, i + slice.size(), slice.size(), platformName);
                }

                notificationPort.notifyNewCourses(platformName, slice);
                sent += slice.size();

                Thread.sleep(300);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("[Notify] Interrompido durante espera entre mensagens platform={}", platformName);
                break;
            } catch (Exception e) {
                failed += slice.size();
                log.error("[Notify] Falha ao enviar lote ({} cursos) para platform={}: {}",
                        slice.size(), platformName, e.getMessage(), e);
            }
        }

        int remaining = newOnes.size() - cap;
        if (remaining > 0) {
            try {
                String apiLink = apiBaseUrl.isBlank()
                        ? ""
                        : apiBaseUrl + "?platform=" + platformName + "&sort=updated_at,desc";
                notificationPort.notifySummary(platformName, remaining, apiLink);
                log.info("[Notify] Enviou resumo de {} cursos restantes para platform={}", remaining, platformName);
            } catch (Exception e) {
                log.error("[Notify] Falha ao enviar resumo platform={}: {}", platformName, e.getMessage(), e);
            }
        }

        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        log.info("[Notify] FIM platform={} enviados={} falhos={} restantes={} duração={} ms",
                platformName, sent, failed, Math.max(remaining, 0), elapsed);
    }
}
