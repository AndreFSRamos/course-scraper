package tech.andrefsramos.course_scraper.adapters.inbound.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
    @Operation(
            summary = "Lista cursos mais recentes",
            description = """
        Retorna uma lista paginada de cursos coletados pelo sistema, ordenados do mais recente para o mais antigo.
        
        Este endpoint permite aplicar filtros opcionais por plataforma, área temática, gratuidade
        e um corte temporal (`since`) baseado em `Instant` em formato ISO8601.

        ### 🧩 Casos de uso típicos
        - Construir dashboards de cursos filtrados por plataforma (EVG, FGV, Sebrae).
        - Exibir apenas cursos gratuitos (`free=true`).
        - Listar somente cursos atualizados/registrados após uma data e hora específica.
        - Paginar resultados para consumo eficiente em apps ou integrações externas.

        ### 🔎 Ordenação
        Os itens retornados são ordenados internamente pelo critério definido na persistência
        (normalmente `updatedAt DESC`).

        ### ⚠️ Cuidados
        - O parâmetro `since` deve estar em formato ISO8601 (`2025-01-01T00:00:00Z`).
        - O tamanho da página (`size`) é limitado a **100** itens.
        - Valores inválidos em `page`, `size` ou `since` resultam em `400 Bad Request`.
        """,
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Consulta executada com sucesso. Retorna uma lista (possivelmente vazia) de cursos.",
                            content = @Content(
                                    mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = Course.class)),
                                    examples = {
                                            @ExampleObject(
                                                    name = "Exemplo de curso",
                                                    value = """
                        [
                          {
                            "id": 123,
                            "platformId": 2,
                            "externalIdHash": "abc123xyz",
                            "title": "Introdução à Ciência de Dados",
                            "url": "https://...",
                            "provider": "FGV",
                            "area": "Tecnologia",
                            "freeFlag": true,
                            "startDate": null,
                            "endDate": null,
                            "statusText": "Online (EAD)",
                            "priceText": "",
                            "createdAt": "2025-01-10T12:00:00Z",
                            "updatedAt": "2025-01-10T12:00:00Z"
                          }
                        ]
                        """
                                            )
                                    }
                            )
                    ),
                    @ApiResponse(
                            responseCode = "204",
                            description = "Nenhum curso encontrado para os filtros informados."
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Algum parâmetro de entrada está inválido. Verifique `page`, `size` ou o formato de `since`."
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Erro inesperado ao consultar os cursos."
                    )
            }
    )
    public ResponseEntity<List<Course>> list(
            @Parameter(
                    description = """
                Plataforma de origem dos cursos.  
                Valores aceitos: `evg`, `fgv`, `sebrae`.  
                Quando omitido, não aplica filtro por plataforma.
            """,
                    example = "fgv"
            )
            @RequestParam(required = false) String platform,

            @Parameter(
                    description = """
                Área temática dos cursos (ex.: Tecnologia, Gestão, Finanças).  
                O filtro é aplicado exatamente conforme salvo na base.
            """,
                    example = ""
            )
            @RequestParam(required = false) String area,

            @Parameter(
                    description = """
                Indica se devem ser retornados apenas cursos gratuitos.  
                - `true` (padrão): retorna somente cursos com flag de gratuidade.  
                - `false`: retorna todos os cursos, independentemente de serem pagos/gratuitos.
            """,
                    example = "true"
            )
            @RequestParam(required = false, defaultValue = "true") boolean free,

            @Parameter(
                    description = """
                Retorna apenas cursos criados/atualizados **a partir deste horário**.  
                Formato: `Instant` ISO8601 (ex.: `2025-01-10T00:00:00Z`).  
                Caso enviado em formato inválido, retorna `400 Bad Request`.
            """,
                    example = "2025-01-10T00:00:00Z"
            )
            @RequestParam(required = false) String since,

            @Parameter(
                    description = """
                Número da página (base 0).  
                Deve ser >= 0. Valores negativos resultam em `400 Bad Request`.
            """,
                    example = "0"
            )
            @RequestParam(defaultValue = "0") int page,

            @Parameter(
                    description = """
                Quantidade de itens por página.  
                Valor padrão: 20.  
                Máximo permitido: 100.  
                Valores <= 0 são substituídos por 20; valores > 100 são limitados a 100.
            """,
                    example = "20"
            )
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
