package tech.andrefsramos.course_scraper.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Scraper - API para consulta de cursos onlines gratuitos",
                version = "v1",
                description = """
                                ## 🎯 Visão Geral da API Course Scraper

                                Bem-vindo à documentação oficial da API **Scraper**, um serviço voltado à **coleta, consolidação e exposição de cursos gratuitos e online** a partir de fontes públicas.

                                Atualmente, a API agrega cursos de:
                                - **EVG** — Escola Virtual de Governo;
                                - **FGV Educação Executiva** — cursos gratuitos online;
                                - **Sebrae** — cursos online gratuitos.

                                A proposta é oferecer um **catálogo unificado**, simples de consultar e fácil de integrar em outros sistemas, dashboards ou rotinas de estudo.

                                > ⚠️ **Aviso importante**  
                                > O Course Scraper **não é um serviço oficial** de nenhuma das instituições listadas e **não possui vínculo** com elas.  
                                > Todo o conteúdo é coletado por _web scraping_ em páginas públicas.

                                ---

                                ## ✅ O que você encontra nesta documentação

                                Aqui você terá acesso a:
                                - Endpoints para:
                                  - **Disparar coletas manuais** por plataforma (`/admin/collect/{platform}`);
                                  - **Listar cursos mais recentes** com filtros (`/api/v1/courses`);
                                - Exemplos de uso com query parameters;
                                - Convenções de campos e formatos (especialmente datas);
                                - Códigos de resposta HTTP mais comuns.

                                Use a busca da própria UI do Swagger para localizar endpoints e parâmetros rapidamente.

                                ---

                                ## 💡 O que a API faz

                                A API Course Scraper:

                                - **Coleta periodicamente** cursos gratuitos e online nas plataformas suportadas;
                                - **Normaliza e persiste** os dados em um banco relacional;
                                - **Evita duplicidades** através de um hash de identificação externa;
                                - **Disponibiliza consulta HTTP** aos cursos mais recentes, com filtros por:
                                  - `platform` — nome da plataforma (`evg`, `fgv`, `sebrae`);
                                  - `area` — área temática (ex.: `Tecnologia`, `Dados & IA`, etc.);
                                  - `free` — flag de cursos gratuitos (`true`/`false`);
                                  - `since` — apenas cursos atualizados/criados a partir de uma determinada data/hora;
                                  - `page` e `size` — paginação.

                                Opcionalmente, o backend pode enviar **notificações de novos cursos** para canais de:
                                - **Telegram** (via bot);
                                - **Discord** (via webhook).

                                ---

                                ## 🚫 O que a API *não* faz

                                - Não realiza cadastro nem autenticação de usuários finais;
                                - Não paga, matricula ou inscreve o usuário em nenhum curso;
                                - Não garante disponibilidade, atualização ou permanência dos cursos nas plataformas de origem;
                                - Não efetua qualquer tipo de integração oficial com os sistemas das instituições.

                                Ela atua apenas como um **catálogo agregador de cursos gratuitos e públicos**.

                                ---

                                ## 🔌 Formato e convenções

                                - Protocolo: **HTTP/HTTPS** (recomendado usar HTTPS em produção);
                                - Formato de dados: **JSON** (`Content-Type: application/json`);
                                - Charset: **UTF-8**.

                                ### Datas e horários

                                Alguns parâmetros e campos utilizam formato **ISO8601**.

                                | Tipo de campo | Formato | Exemplo |
                                |---------------|---------|---------|
                                | `since` (query param) | `Instant` em ISO8601 | `2025-01-01T00:00:00Z` |

                                Se o parâmetro `since` não for enviado, a API retornará os cursos mais recentes conforme ordenação interna, sem corte temporal explícito.

                                ---

                                ## 📡 Endpoints principais

                                ### 1) Coleta manual por plataforma

                                `POST /admin/collect/{platform}`

                                - Dispara a coleta **manual** para uma plataforma específica;
                                - Valores esperados em `{platform}`:
                                  - `evg`
                                  - `fgv`
                                  - `sebrae`
                                - Uso típico: operações internas, jobs manuais ou testes pontuais.

                                ### 2) Consulta de cursos

                                `GET /api/v1/courses`

                                Lista cursos mais recentes com filtros opcionais:

                                - `platform` *(opcional)* — filtra por plataforma (`evg`, `fgv`, `sebrae`);
                                - `area` *(opcional)* — filtra por área temática (ex.: `Tecnologia`);
                                - `free` *(opcional, padrão = `true`)* — se `true`, retorna apenas cursos gratuitos;
                                - `since` *(opcional)* — filtra cursos a partir de um `Instant` ISO8601;
                                - `page` *(opcional, padrão = `0`)* — página de resultados;
                                - `size` *(opcional, padrão = `20`)* — quantidade de itens por página.

                                **Exemplo de requisição:**

                                ```http
                                GET /api/v1/courses?platform=fgv&area=Tecnologia&free=true&page=0&size=20
                                ```

                                **Resposta (exemplo simplificado):**

                                ```json
                                [
                                  {
                                    "id": 123,
                                    "platformId": 2,
                                    "externalIdHash": "....",
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
                                ```

                                ---

                                ## 🧪 Como usar na prática

                                1. Faça o deploy do serviço (container ou execução local do Spring Boot);
                                2. Acesse o Swagger UI (ex.: `/swagger-ui.html` ou `/swagger-ui/index.html`);
                                3. Navegue até:
                                   - **`/api/v1/courses`** para listar cursos;
                                   - **`/admin/collect/{platform}`** para disparar coletas manuais;
                                4. Ajuste parâmetros de consulta conforme sua necessidade (por exemplo, filtrar por área ou plataforma);
                                5. Utilize a API em scripts, jobs ou integrações para alimentar:
                                   - dashboards;
                                   - bots de recomendação;
                                   - notificadores personalizados.

                                ---

                                ## 🔒 Segurança e boas práticas

                                - Em produção, recomenda-se expor a API **apenas via HTTPS**;
                                - O endpoint `/admin/collect/{platform}` é voltado a uso interno:
                                  - proteja-o via firewall, autenticação ou VPN;
                                  - evite deixá-lo aberto em ambientes públicos;
                                - Respeite as políticas de uso das plataformas de origem (EVG, FGV, Sebrae), incluindo:
                                  - limites razoáveis de requisição;
                                  - horários de coleta;
                                  - atualização de robôs/scrapers em caso de mudanças estruturais.

                                ---

                                ## 🛑 Tratamento de erros (visão geral)

                                A API utiliza códigos HTTP padrões. Alguns exemplos relevantes:

                                | Código | Significado                                                |
                                |--------|------------------------------------------------------------|
                                | 200    | Requisição bem-sucedida (lista de cursos retornada).       |
                                | 204    | Sem conteúdo (nenhum curso para os filtros informados).    |
                                | 400    | Parâmetros inválidos ou formato incorreto em `since`.      |
                                | 404    | Endpoint inexistente.                                      |
                                | 429    | Limite de requisições excedido (se houver rate limit).     |
                                | 500    | Erro interno ao executar coleta ou consulta de dados.      |

                                Caso encontre um erro recorrente, recomenda-se registrar:
                                - endpoint acessado;
                                - parâmetros enviados;
                                - horário aproximado (com timezone);
                                - payload de resposta (quando houver).

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

                                ---

                                🧠 *Use esta API como base para construir experiências melhores de descoberta de cursos gratuitos — dashboards, notificadores e ferramentas de apoio ao aprendizado.*
                                """
        )
)
public class OpenApiConfig {
    
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
