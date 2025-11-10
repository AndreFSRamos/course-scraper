package tech.andrefsramos.course_scraper.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/*
 * Finalidade

 * Habilita o agendador de tarefas (Spring Scheduling) para execução periódica
 * de componentes anotados com {@code @Scheduled}.

 * Detalhes Técnicos

 * - O Spring gerencia o thread pool padrão para os jobs agendados.
 * - A anotação {@link EnableScheduling} ativa o processamento de métodos
 *   anotados com {@code @Scheduled} em toda a aplicação.
 * - As configurações de frequência/cron são definidas diretamente nos
 *   componentes ou via propriedades.

 * Boas Práticas
 *
 * - Centralizar apenas a habilitação do agendamento aqui.
 * - Evitar lógica ou beans adicionais nesta classe.
 * - Usar logs de inicialização para rastrear a ativação em ambientes complexos.
 */

@Configuration
@EnableScheduling
public class SchedulingConfig {

    private static final Logger log = LoggerFactory.getLogger(SchedulingConfig.class);

    public SchedulingConfig() {
        log.info("[Scheduling] Scheduler global ativado — métodos @Scheduled agora serão executados automaticamente.");
    }
}