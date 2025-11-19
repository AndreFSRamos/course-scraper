package tech.andrefsramos.course_scraper.adapters.inbound.api.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.andrefsramos.course_scraper.core.application.CollectCoursesUseCase;

/**
 * AdminController

 * Exposição de funcionalidades administrativas da aplicação.

 * Esta controller é responsável exclusivamente por operações restritas,
 * exigindo credenciais com perfil ADMIN para execução.
 */
@RestController
@RequestMapping("/admin")
@Tag(
        name = "02",
        description = """
## ADMIN
---
Módulo responsável por operações administrativas sensíveis da aplicação.

### ⚙️ Funcionalidades disponíveis
- Acionar manualmente a coleta de cursos por plataforma
- Permitir que um administrador force o scraper a executar imediatamente

### 🔐 Segurança
- Acesso **restrito** a usuários com papel **ADMIN**
- Requer envio do token JWT no header:
  `Authorization: Bearer <token>`

### 📌 Plataformas suportadas
- **evg**
- **fgv**
- **sebrae**

O administrador pode forçar a coleta individualmente para qualquer uma delas.
"""
)
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);
    private final CollectCoursesUseCase collect;

    public AdminController(CollectCoursesUseCase collect) {
        this.collect = collect;
    }

    @PostMapping("/collect/{platform}")
    @Operation(
            summary = "Força manualmente a coleta de cursos para a plataforma informada",
            description = """
                Executa o processo de scraping imediatamente para uma plataforma específica (evg, fgv ou sebrae), ignorando agendamentos.
    
                🧩 Quando usar?
                    - Validar se o scraper está funcionando corretamente
                    - Forçar sincronização imediata após ajustes no scraper
                    - Testar notificações ou integrações
    
                ⚠️ Regras:
                    - Acesso restrito a administradores (ROLE_ADMIN)
                    - A plataforma deve existir e ser reconhecida pelo sistema
                    - Em caso de erro no scraper, uma resposta 500 será retornada
    
                🔧 Exemplo de chamada:
    
                POST /admin/collect/fgv
    
                Retorno esperado:
                Collect executed successfully for platform: fgv
            """,

            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Coleta executada com sucesso",
                            content = @Content(
                                    mediaType = "text/plain",
                                    examples = @ExampleObject(
                                            value = "Collect executed successfully for platform: fgv"
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Parâmetro de plataforma vazio ou inválido",
                            content = @Content(
                                    mediaType = "text/plain",
                                    examples = @ExampleObject(
                                            value = "Platform parameter cannot be empty."
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "401",
                            description = "Usuário não autenticado ou token inválido"
                    ),
                    @ApiResponse(
                            responseCode = "403",
                            description = "Acesso negado — usuário não possui papel ADMIN"
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Erro inesperado durante a execução do scraper",
                            content = @Content(
                                    mediaType = "text/plain",
                                    examples = @ExampleObject(
                                            value = "Error during collect for platform 'fgv': <mensagem do erro>"
                                    )
                            )
                    )
            },
            parameters = {
                    @Parameter(
                            name = "platform",
                            description = """
                    Nome da plataforma que será coletada manualmente.

                    Valores aceitos
                    - evg
                    - fgv
                    - sebrae

                    Qualquer valor inválido resultará em `400 Bad Request`.
                """,
                            example = "fgv"
                    )
            }
    )
    public ResponseEntity<String> collect(
            @PathVariable String platform
    ) {

        log.info("AdminController: solicitação de coleta recebida para platform='{}'", platform);

        if (platform == null || platform.isBlank()) {
            log.warn("AdminController: parâmetro 'platform' inválido ou vazio");
            return ResponseEntity
                    .badRequest()
                    .body("Platform parameter cannot be empty.");
        }

        try {
            collect.collectForPlatform(platform);
            log.info("AdminController: coleta concluída com sucesso para platform='{}'", platform);
            return ResponseEntity.ok("Collect executed successfully for platform: " + platform);

        } catch (Exception ex) {
            log.error("AdminController: erro ao executar coleta para platform='{}'", platform, ex);
            return ResponseEntity
                    .internalServerError()
                    .body("Error during collect for platform '" + platform + "': " + ex.getMessage());
        }
    }
}
