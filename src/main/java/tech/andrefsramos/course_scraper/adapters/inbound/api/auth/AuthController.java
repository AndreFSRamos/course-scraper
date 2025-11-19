package tech.andrefsramos.course_scraper.adapters.inbound.api.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import tech.andrefsramos.course_scraper.adapters.inbound.api.auth.dto.AuthRequest;
import tech.andrefsramos.course_scraper.adapters.inbound.api.auth.dto.AuthResponse;
import tech.andrefsramos.course_scraper.adapters.inbound.api.auth.dto.PasswordUpdateRequest;
import tech.andrefsramos.course_scraper.config.security.JwtService;
import tech.andrefsramos.course_scraper.config.security.JpaUserDetailsService;
import tech.andrefsramos.course_scraper.core.ports.UserRepository;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "01", description = """
## AUTH
---
Endpoints responsáveis pelo fluxo de autenticação do sistema.

Este módulo garante:
- Login via credenciais (`username`, `password`);
- Emissão de tokens JWT válidos para chamadas autenticadas;
- Troca de senha do usuário autenticado;
- Segurança da console administrativa e do coletor de cursos.

⚠️ Os usuários padrão criados no primeiro run são:
- admin (ADMIN)
- admin.collector (COLLECTOR)

É obrigatório trocar essas senhas no primeiro acesso.
""")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final JpaUserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    @Operation(
            summary = "Realiza login e retorna um token JWT",
            description = """
                Autentica o usuário com username e password e retorna um token JWT
                necessário para acessar endpoints protegidos (ex.: /admin/**).
    
                Cada token contém:
                - Identidade do usuário (sub)
                - Tempo de expiração configurado em application.yml
                - Assinatura HMAC-SHA256 segura
    
                ‍💻 Fluxo típico
                1. Enviar o JSON com usuário e senha
                2. Receber o token JWT
                3. Usar o token no header:
                   `Authorization: Bearer <token>`
    
                🔐 Perfis existentes
                - ADMIN: acesso total ao módulo administrativo
                - COLLECTOR: acesso aos endpoints internos de coleta autenticada
                - Cursos públicos (/api/v1/courses) não exigem token
            """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Credenciais do usuário",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "Exemplo de login",
                                    value = """
                    {
                      "username": "admin",
                      "password": "admin"
                    }
                    """
                            )
                    )
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Autenticação bem-sucedida. Token JWT retornado.",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = AuthResponse.class),
                                    examples = @ExampleObject(
                                            value = """
                        {
                          "token": "Bearer eyJhbGciOiJIUzI1NiJ9..."
                        }
                        """
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "401",
                            description = "Credenciais inválidas (usuário ou senha incorretos)"
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Erro interno ao gerar o token"
                    )
            }
    )
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {

        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(
                        request.username(),
                        request.password()
                );

        authenticationManager.authenticate(authToken);

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.username());
        String jwt = jwtService.generateToken(userDetails);

        return ResponseEntity.ok(new AuthResponse("Bearer " + jwt));
    }

    @PutMapping("/password")
    @Operation(
            summary = "Altera a senha do usuário autenticado",
            description = """
            Permite que o próprio usuário altere sua senha.
            É obrigatório trocar as senhas padrões do sistema no primeiro acesso.

            🔐 Regras:
            - Requer token JWT válido
            - Usuário deve enviar a senha atual correta
            - Se a senha atual estiver errada → HTTP 401
            - Atualiza no banco com hash BCrypt

            🧰 Exemplo de uso
            Header:
            Authorization: Bearer <token>

            {
              "currentPassword": "admin",
              "newPassword": "SenhaNovaMuitoForte123"
            }
        """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Objeto com senha atual e nova senha",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PasswordUpdateRequest.class),
                            examples = @ExampleObject(
                                    name = "Exemplo troca de senha",
                                    value = """
                    {
                      "currentPassword": "admin",
                      "newPassword": "NovaSenhaSuperSegura@2025"
                    }
                    """
                            )
                    )
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Senha alterada com sucesso"
                    ),
                    @ApiResponse(
                            responseCode = "401",
                            description = "Senha atual incorreta"
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Usuário autenticado não foi encontrado"
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Erro interno ao atualizar a senha"
                    )
            }
    )
    public ResponseEntity<?> updatePassword(@RequestBody PasswordUpdateRequest request) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        var userEntity = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        if (!passwordEncoder.matches(request.currentPassword(), userEntity.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Incorrect password.");
        }

        userEntity.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(userEntity);

        return ResponseEntity.ok("Senha alterada com sucesso.");
    }
}