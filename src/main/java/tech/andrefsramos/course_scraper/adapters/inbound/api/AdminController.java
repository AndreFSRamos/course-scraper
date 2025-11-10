package tech.andrefsramos.course_scraper.adapters.inbound.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.andrefsramos.course_scraper.core.application.CollectCoursesUseCase;

/**
 * AdminController
 *
 * Descrição geral:
 * - Controlador responsável por expor endpoints administrativos da aplicação,
 *   permitindo acionar manualmente a coleta de cursos para uma plataforma específica.
 *
 * Responsabilidades:
 * - Receber requisições HTTP de administração (rota /admin).
 * - Invocar o caso de uso {@link CollectCoursesUseCase} para disparar o scraper
 *   correspondente à plataforma informada.
 * - Retornar status de sucesso ou erro conforme o resultado da operação.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);
    private final CollectCoursesUseCase collect;

    public AdminController(CollectCoursesUseCase collect) {this.collect = collect;}

    @PostMapping("/collect/{platform}")
    public ResponseEntity<String> collect(@PathVariable String platform) {
        log.info("AdminController: solicitação de coleta recebida para platform='{}'", platform);

        if (platform == null || platform.isBlank()) {
            log.warn("AdminController: parâmetro 'platform' inválido ou vazio");
            return ResponseEntity.badRequest().body("Platform parameter cannot be empty.");
        }

        try {
            collect.collectForPlatform(platform);
            log.info("AdminController: coleta concluída com sucesso para platform='{}'", platform);
            return ResponseEntity.ok("Collect executed successfully for platform: " + platform);
        } catch (Exception ex) {
            log.error("AdminController: erro ao executar coleta para platform='{}'", platform, ex);
            return ResponseEntity.internalServerError()
                    .body("Error during collect for platform '" + platform + "': " + ex.getMessage());
        }
    }
}
