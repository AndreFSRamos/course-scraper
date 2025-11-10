package tech.andrefsramos.course_scraper.adapters.outbound.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.andrefsramos.course_scraper.core.domain.Course;
import tech.andrefsramos.course_scraper.core.ports.NotificationPort;

import java.util.List;

/*
 * CompositeNotificationPort

 * Finalidade

 * Implementação do padrão **Composite** para o envio de notificações de novos cursos.
 * Permite que múltiplos adaptadores de notificação (ex: Discord, Slack, E-mail, etc.)
 * sejam acionados simultaneamente de forma transparente.

 * Cada notificação recebida é repassada a todos os objetos {@link NotificationPort}
 * configurados no construtor.

 * Fluxo resumido:

 * 1. Recebe a notificação de novos cursos ou resumo de plataforma.
 * 2. Itera sobre todos os adaptadores registrados em `delegates`.
 * 3. Envia a notificação individualmente, capturando falhas para evitar impacto nas demais.
 */

public class CompositeNotificationPort implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(CompositeNotificationPort.class);
    private final List<NotificationPort> delegates;

    public CompositeNotificationPort(List<NotificationPort> delegates) {
        this.delegates = delegates != null ? delegates : List.of();
        log.info("CompositeNotificationPort inicializado com {} adaptadores de notificação.", this.delegates.size());
    }

    @Override
    public void notifyNewCourses(String platformName, List<Course> newCourses) {
        if (newCourses == null || newCourses.isEmpty()) {
            log.debug("notifyNewCourses: sem cursos novos para enviar. platform={}", platformName);
            return;
        }

        log.info("notifyNewCourses: iniciando envio de {} cursos novos para {} adaptadores. platform={}",
                newCourses.size(), delegates.size(), platformName);

        for (NotificationPort delegate : delegates) {
            String adapterName = delegate.getClass().getSimpleName();
            try {
                delegate.notifyNewCourses(platformName, newCourses);
                log.debug("notifyNewCourses: envio bem-sucedido para adapter={} platform={}", adapterName, platformName);
            } catch (Exception ex) {
                log.warn("notifyNewCourses: falha ao enviar para adapter={} platform={} erro={}",
                        adapterName, platformName, ex.getMessage());
            }
        }

        log.info("notifyNewCourses: envio concluído para platform={} totalAdapters={}", platformName, delegates.size());
    }

    @Override
    public void notifySummary(String platformName, int totalNew, String apiLink) {
        log.info("notifySummary: enviando resumo para {} adaptadores. platform={} totalNew={} link={}",
                delegates.size(), platformName, totalNew, apiLink);

        for (NotificationPort delegate : delegates) {
            String adapterName = delegate.getClass().getSimpleName();
            try {
                delegate.notifySummary(platformName, totalNew, apiLink);
                log.debug("notifySummary: resumo enviado com sucesso para adapter={} platform={}", adapterName, platformName);
            } catch (Exception ex) {
                log.warn("notifySummary: falha ao enviar resumo para adapter={} platform={} erro={}",
                        adapterName, platformName, ex.getMessage());
            }
        }

        log.info("notifySummary: envio de resumo concluído. platform={} totalAdapters={}", platformName, delegates.size());
    }
}
