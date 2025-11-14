package tech.andrefsramos.course_scraper.adapters.inbound.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.andrefsramos.course_scraper.core.application.QueryLatestCoursesUseCase;
import tech.andrefsramos.course_scraper.core.domain.Course;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * CoursesController
 *
 * Descrição geral:
 * - Controlador REST (versão v1) para consulta paginada de cursos armazenados pelo sistema.
 * - Expõe o endpoint GET /api/v1/courses com filtros por plataforma, área, gratuidade (free),
 *   e data mínima de atualização/criação (since).
 *
 * Responsabilidades:
 * - Validar e normalizar parâmetros de consulta recebidos via query string.
 * - Invocar o caso de uso {@link QueryLatestCoursesUseCase} para obter os cursos.
 * - Retornar respostas HTTP com códigos adequados e payload consistente.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name="02 - Cursos")
public class CoursesController {

    private static final Logger log = LoggerFactory.getLogger(CoursesController.class);
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final QueryLatestCoursesUseCase query;

    public CoursesController(QueryLatestCoursesUseCase query) {this.query = query;}

    @GetMapping("/courses")
    public ResponseEntity<List<Course>> list(
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String area,
            @RequestParam(required = false, defaultValue = "true") boolean free,
            @RequestParam(required = false) String since,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_SIZE) int size
    ) {
        long t0 = System.nanoTime();
        log.info("CoursesController: consulta recebida platform='{}', area='{}', free={}, since='{}', page={}, size={}",
                platform, area, free, since, page, size);

        platform = (platform != null && platform.isBlank()) ? null : platform;
        area = (area != null && area.isBlank()) ? null : area;

        if (page < 0) {
            log.warn("CoursesController: parâmetro 'page' inválido: {}", page);
            return ResponseEntity.badRequest().build();
        }
        if (size <= 0) {
            log.warn("CoursesController: parâmetro 'size' inválido (<= 0): {}. Forçando size={} (default).", size, DEFAULT_SIZE);
            size = DEFAULT_SIZE;
        }
        if (size > MAX_PAGE_SIZE) {
            log.warn("CoursesController: parâmetro 'size' acima do limite ({}). Ajustando de {} para {}.", MAX_PAGE_SIZE, size, MAX_PAGE_SIZE);
            size = MAX_PAGE_SIZE;
        }

        Instant sinceInstant = null;
        if (since != null) {
            try {
                sinceInstant = Instant.parse(since);
            } catch (DateTimeParseException dtpe) {
                log.warn("CoursesController: parâmetro 'since' inválido: '{}'", since, dtpe);
                return ResponseEntity.badRequest().build();
            }
        }

        try {
            List<Course> result = query.list(platform, area, free, sinceInstant, page, size);
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
            log.info("CoursesController: consulta concluída (itens={}, elapsedMs={}) platform='{}', area='{}', free={}, since='{}', page={}, size={}",
                    (result != null ? result.size() : 0), elapsedMs, platform, area, free, sinceInstant, page, size);
            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
            log.error("CoursesController: erro ao executar consulta (elapsedMs={}) platform='{}', area='{}', free={}, since='{}', page={}, size={}",
                    elapsedMs, platform, area, free, sinceInstant, page, size, ex);
            return ResponseEntity.internalServerError().build();
        }
    }
}
