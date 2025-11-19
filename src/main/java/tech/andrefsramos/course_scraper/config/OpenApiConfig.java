package tech.andrefsramos.course_scraper.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Scraper - API para consulta de cursos onlines gratuitos",
                version = "v1",
                description = """
                                ---
                                
                                ## 🎯 Visão Geral da API Course Scraper
                                
                                Bem-vindo à documentação oficial da API **Scraper**, um serviço inteligente que coleta, organiza e disponibiliza cursos **gratuitos e online** de fontes públicas, mantendo tudo atualizado automaticamente.
                                
                                Atualmente, a API integra:
                                - **EVG** — Escola Virtual de Governo  
                                - **FGV Educação Executiva** — cursos gratuitos online  
                                - **Sebrae** — cursos online gratuitos  
                                
                                A proposta é reunir tudo em um **catálogo centralizado, fácil de consultar**, acessível via HTTP e pronto para integrações com aplicativos, dashboards e sistemas de aprendizado automatizados.
                                
                                > ⚠️ **Aviso importante**  
                                > Este serviço **não tem vínculo** com as instituições de origem.  
                                > Ele apenas organiza informações públicas, obtidas via scraping.
                                
                                ---
                                
                                ## ⚙️ Como funciona a API — visão simples e prática
                                                         
                                ### **Coleta inteligente com uso de cache**
                                O sistema coleta cursos periodicamente (via agendamentos internos) ou manualmente (via endpoint `/admin/collect/{platform}`).
                                
                                Para evitar sobrecargas nas plataformas de origem e garantir performance, existe um **mecanismo interno de cache**:
                                - Cada curso coletado recebe um hash único;
                                - Se uma plataforma ainda não publicou novos cursos, o sistema **ignora a coleta repetida**;
                                - Isso reduz custo computacional e elimina duplicidade de registros;
                                - O banco mantém apenas cursos válidos e atualizados.
                                
                                Resultado: **consultas mais rápidas, menos acessos desnecessários às páginas de origem e economia de recursos**.
                                
                                ---
                                
                                ## 🔐 Autenticação & Segurança
                                
                                A API agora possui um módulo completo de **autenticação JWT**, utilizado para proteger rotas administrativas.
                                
                                ### Endpoints públicos (não exigem login)
                                - `GET /api/v1/courses`  
                                  (listar cursos, aplicar filtros, consultas ilimitadas)
                                
                                ### Endpoints protegidos (ADMIN)
                                - `POST /admin/collect/{platform}`  
                                  (forçar coletas manuais)
                                
                                ---
                                
                                ## 🔑 Como funciona o login
                                
                                Quando o sistema inicia pela primeira vez, ele cria dois usuários padrão:
                                
                                | Usuário | Papel | Uso |
                                |--------|--------|------|
                                | `admin` | ADMIN | Acesso total aos endpoints administrativos |
                                | `admin.collector` | COLLECTOR | Acesso aos fluxos internos de coleta |
                                
                                Essas contas são criadas automaticamente na tabela `users`.
                                
                                ### 📌 **Passo 1 — Login inicial**
                                Envie:
                                
                                ```json
                                //POST /auth/login
                                {
                                  "username": "admin",
                                  "password": "admin"
                                }
                                ```
                                
                                A resposta será:
                                
                                ```json
                                {
                                  "token": "Bearer eyJhbGciOiJIUzI1NiJ9..."
                                }
                                ```
                                
                                Você deve usar este token nos endpoints protegidos:
                                
                                ### 🔁 Troca obrigatória da senha
                                
                                Por segurança, ao fazer login pela primeira vez com o usuário admin, você deve alterar a senha padrão:
                                
                                ```json
                                //PUT /auth/password
                                {
                                  "currentPassword": "admin",
                                  "newPassword": "NovaSenhaSuperSegura123"
                                }
                                ```
                                
                                A partir desse momento:
                                
                                o login passa a exigir a nova senha,
                                
                                e o token futuro será gerado com as credenciais atualizadas.
                                
                                ---
                                
                                ## 🗂️ O que você pode fazer com a API
                                ### 1) Consultar cursos
                                Use o endpoint:
                                
                                 - `GET /api/v1/courses`
                                
                                Com filtros opcionais:
                                
                                 - `platform` — evg, fgv, sebrae
                                
                                 - `area` — área temática
                                
                                 - `free` — cursos gratuitos
                                
                                 - `since` — retornar somente cursos recentes
                                
                                 - `page` & `size` — paginação
                                
                                ### 2) Forçar coleta manual
                                Apenas administradores podem usar:
                                
                                 - `POST /admin/collect/{platform}`
                                
                                Use para:
                                
                                 - Testes de desenvolvimento
                                 - Reprocessamento manual
                                 - Execução fora da rotina automática
                                
                                ---
                                
                                ## 🧪 Como começar — passo a passo
                                 - Inicie a aplicação
                                
                                 - Realize o login inicial com **admin/admin**
                                 - Troque a senha imediatamente
                                 - Consulte cursos usando `/api/v1/courses`
                                 - Use `/admin/collect/{platform}` para forçar coletar novamente
                                 - Utilize o catálogo em bots, dashboards ou sistemas externos
                                
                                ---
                                
                                ### 📌 Tratamento de erros resumido
                                | **Código** | **Significado** |
                                |--------|-------------|
                                | **200** |	Sucesso |
                                | **204** |	Sem resultados |
                                | **400** | Parâmetros inválidos |
                                | **401** | Token ausente ou credenciais incorretas |
                                | **403** | Usuário sem permissão |
                                | **500** | Erro interno ao coletar ou buscar cursos |

                                ---

                                ## 📢 Entre no nosso Discord / Telegram para receber notificações de novos cursos!

                                Basta escanear os QR Codes abaixo ou clicar nos links:

                                <img src="https://raw.githubusercontent.com/AndreFSRamos/GifCards/refs/heads/main/qrcode_gruoup_dc_scraper.svg" alt="Discord QR Code" width="180" height="180" />
                                <img src="https://raw.githubusercontent.com/AndreFSRamos/GifCards/refs/heads/main/qrcode_canal_telegram_scraper.svg" alt="Telegram QR Code" width="180" height="180" />
                                <br/>
                              
                                🔗 **Discord:** [https://discord.gg/SWyvdjsJ](https://discord.gg/SWyvdjsJ)      
                                🔗 **Telegram:** [https://t.me/cursos_gratuitos](https://t.me/cursos_gratuitos)  

                                ---

                                ## 👨‍💻 Autor & Contato

                                Este projeto foi desenvolvido por **André Felipe da Silva Ramos**.

                                - 🌐 **Portfólio / Site:** [https://andrefsramos.tech](https://andrefsramos.tech)  
                                - 💻 **Repositório GitHub:** [https://github.com/AndreFSRamos/course-scraper](https://github.com/AndreFSRamos/course-scraper)  
                                - ✉️ **E-mail:** [dev.andreramos@andrefsramos.tech](mailto:dev.andreramos@andrefsramos.tech)

                                🧠 *Use esta API como base para construir experiências melhores de descoberta de cursos gratuitos — dashboards, notificadores e ferramentas de apoio ao aprendizado.*
                                
                                ---
                                
                                ## 🧩Endpoints
                                
                                """
        )
)
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .name("bearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        )
                )
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
    
    @Bean
    public SwaggerIndexTransformer swaggerIndexTransformer(
            SwaggerUiConfigProperties swaggerUiConfig,
            SwaggerUiOAuthProperties swaggerUiOAuthProperties,
            SwaggerWelcomeCommon swaggerWelcomeCommon,
            ObjectMapperProvider objectMapperProvider
    ) {

        return new SwaggerCustomCssInjector(swaggerUiConfig, swaggerUiOAuthProperties, swaggerWelcomeCommon, objectMapperProvider);
    }
}
