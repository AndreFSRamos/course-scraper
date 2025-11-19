# 🧠 Course Scraper — Sistema de Coleta, Cacheamento e Notificação de Cursos Gratuitos

**Course Scraper** é uma aplicação desenvolvida em **Java 17 + Spring Boot 3**, projetada para coletar, armazenar, cachear e notificar automaticamente cursos gratuitos e online disponibilizados por plataformas públicas de ensino.

A arquitetura segue o padrão **Hexagonal (Ports & Adapters)**, garantindo separação clara entre camadas, extensibilidade, facilidade de manutenção e testabilidade.

---

## 🎯 Propósito do Projeto

O sistema automatiza:

1. Coleta periódica ou manual de cursos via scraping
2. Normalização e padronização dos dados
3. Identificação de novos cursos por hash
4. Persistência em banco PostgreSQL
5. Cacheamento inteligente para evitar duplicidades
6. Notificação automática em:
  - Discord
  - Telegram
7. Exposição de API pública para consumo externo

Plataformas atualmente suportadas:

- EV.G – Escola Virtual de Governo
- FGV – Fundação Getúlio Vargas
- Sebrae – Cursos Online Gratuitos

---

## 🧩 Arquitetura Hexagonal

core/
├── domain/ # Entidades e regras de negócio
├── ports/ # Interfaces (contracts)
└── application/ # Casos de uso (orquestração)
adapters/
├── inbound/ # Controllers, Schedulers, Jobs
└── outbound/ # Scrapers, Repositórios, Notificações, Cache, HTTP

yaml
Copiar código

Benefícios:

- Domínio isolado
- Infraestrutura substituível
- Facilidade para adicionar novas plataformas
- Testes independentes

---

## 📦 Estrutura de Pastas
```text
course-scraper/
│
├── src/main/java/tech/andrefsramos/course_scraper/
│ ├── adapters/
│ │ ├── inbound/
│ │ │ ├── api/
│ │ │ ├── jobs/
│ │ │ └── scheduler/
│ │ ├── outbound/
│ │ │ ├── http/
│ │ │ ├── notify/
│ │ │ ├── persistence/
│ │ │ ├── scrapers/
│ │ │ └── cache/
│ │ └── config/
│ └── core/
│ ├── application/
│ ├── domain/
│ └── ports/
│
└── src/main/resources/db/migration/ # Scripts Flyway
```
---

## ⚙️ Tecnologias Utilizadas

| Categoria | Tecnologias |
|----------|-------------|
| Linguagem | Java 17 |
| Framework | Spring Boot 3.x |
| ORM | JPA + Hibernate |
| Banco | PostgreSQL |
| Migração | Flyway |
| Scraping | Jsoup |
| Notificações | Telegram Bot API, Discord Webhook |
| Build | Maven Wrapper |
| Logs | SLF4J + Logback |

---

## 🔐 Autenticação JWT

A aplicação implementa autenticação via **JWT**, utilizada para proteger rotas administrativas.

### Usuários criados automaticamente

| Usuário | Papel | Senha inicial |
|--------|--------|----------------|
| admin | ADMIN | admin |
| admin.collector | COLLECTOR | admin |

Após o primeiro login, é obrigatória a troca da senha:

```bash
  PUT /auth/password
```

### Fluxo de Login

#### Requisição
```bash
  POST /auth/login
  {
    "username": "admin",
    "password": "admin"
  }
```
Resposta
```json
{
  "token": "Bearer eyJhbGciOiJIUzI1NiJ9..."
}
```
### Autorização
Enviar nas rotas protegidas:

---
## 📦 Cacheamento de Cursos
O cacheamento evita reprocessar cursos já coletados recentemente.

### Funcionamento
Ao coletar um curso, gera-se um hash exclusivo

Verifica-se a existência do hash na tabela course_cache

Se existir → coleta ignorada

Se não existir → curso persistido e cache atualizado

### Benefícios
 - Redução de requisições HTTP desnecessárias

 - Menor carga nos scrapers

 - Maior velocidade de resposta

 - Evita duplicidade

---

## 🚀 Como Executar o Projeto
### 1️⃣ Pré-requisitos
- [Java 17+](https://www.oracle.com/java/technologies/javase-jdk17-downloads.html)
- [Maven 3.8+](https://maven.apache.org/)
- [Postgres](https://www.postgresql.org/)
- [Docker](https://www.docker.com/) (opcional, se quiser usar container para banco)
- [Git](https://git-scm.com/install/windows) para clonar o repositório

### 2️⃣ Clonar o repositório
```bash
  git clone https://github.com/AndreFSRamos/course-scraper.git
  cd course-scraper
```

### 3️⃣ Criar .env
```ini
  DB_URL=jdbc:postgresql://localhost:5432/courses?sslmode=disable
  DB_USER=app_user
  DB_PASS=app_pass
  TELEGRAM_BOT_TOKEN=TOKEN_AQUI
  TELEGRAM_CHAT_ID=-100ID_AQUI
  PLATFORM_EVG_ENABLED=true
  PLATFORM_FGV_ENABLED=true
  PLATFORM_SEBRAE_ENABLED=true
  DISCORD_WEBHOOK_URL=https://discord.com/api/webhooks/.../...
  SWAGGER_SERVER_URL=http://localhost:8080/scraper
  JWT_SECRET=<SUA SECRET KEY>
```

### 4️⃣ Subir banco via Docker (opcional)
Se desejar subir o banco de dados localmente usando Docker Compose, crie um arquivo chamado docker-compose.yml na raiz do projeto com o seguinte conteúdo:
```ymal
version: "3.9"
services:
  postgres:
    image: postgres:17
    container_name: pg-courses
    environment:
      POSTGRES_DB: courses
      POSTGRES_USER: app_user
      POSTGRES_PASSWORD: app_pass
    ports:
      - "5432:5432"
    command: ["postgres", "-c", "shared_buffers=256MB", "-c", "max_connections=100"]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U app_user -d courses"]
      interval: 10s
      timeout: 5s
      retries: 10

  adminer:
    image: adminer
    container_name: adminer
    environment:
      ADMINER_DEFAULT_SERVER: postgres
    ports:
      - "8081:8080"
    depends_on:
      postgres:
        condition: service_healthy
```

Para iniciar o banco e o painel de administração (Adminer):

```bash
  docker compose up -d
```

 - O banco PostgreSQL ficará disponível em http://localhost:5432

 - O Adminer poderá ser acessado em http://localhost:8081

### Aplicação disponível em:

  - http://localhost:8080

  - Swagger UI: http://localhost:8080/swagger-ui/index.html

 ---

## 📡 Endpoints da API
### Públicos
| **Método** | **Endpoint**      | **Descrição**                      |
|------------|-------------------|------------------------------------|
| **GET**    | `/api/v1/courses` | Lista cursos com filtros opcionais |

### Protegidos (JWT)

| **Método** | **Endpoint**                | **Papel**          |
|------------|-----------------------------|--------------------|
| **POST**   | `/admin/collect/{platform}` | ADMIN              |
| **PUT**    | 	`/auth/password`         |  admin / collector |
| **POST**   | `/auth/login`               | Público            |

 ---

## 🧪 Exemplos de Uso
Listar cursos gratuitos da EVG
```bash
     curl "http://localhost:8080/api/v1/courses?platform=evg&free=true"
```

Coletar cursos da FGV (ADMIN)
```bash
     curl -X POST http://localhost:8080/admin/collect/fgv \
     -H "Authorization: Bearer <token>"
```

Filtrar por área e data
```bash
     curl "http://localhost:8080/api/v1/courses?area=Tecnologia&since=2025-11-01T00:00:00Z"
```

---

### 🧭 Tarefas Automáticas
| **Tarefa**            | **Frequência** | **Classe**          |
|-----------------------|----------------|---------------------|
| Coleta periódica      | a cada 12h     | CollectScheduler    |
| Reenvio de pendências | 	a cada 60s    | 	PendingNotifierJob |

---

## 🗃️ Banco de Dados
Scripts Flyway localizados em:

````text
     src/main/resources/db/migration/
````
Versões disponíveis:

| **Versão** | **Descrição**                      |
|------------|------------------------------------|
| V1         | 	Estrutura inicial                 |
| V2         | 	Seed de plataformas               |
| V3         | 	Campo notified_at                 |
| V4         | 	Estrutura de cache (course_cache) |
| V5         | 	Seed de usuários admin            |

---

## 🧩 Fluxo Geral do Sistema
    A[CollectScheduler / AdminController] --> B[CollectCoursesService]
    
    B --> C[ScraperPort (EVG, FGV, Sebrae)]
    
    C --> D[DetectChangesService]
    
    D --> E[CourseRepository + SnapshotRepository]
    
    D --> F[NotifyNewCoursesService]
    
    F --> G[NotificationPort (Discord / Telegram)]
    
    G -->|sucesso| H[Marcar como notificado]
    
    G -->|falha| I[PendingNotifierJob → reenviar]

---

## 🤝 Contribuições
 - #### Faça um fork

 - #### Crie uma branch (feature/nome-da-feature)

 - #### Envie um Pull Request

---

## 📄 Licença
Este projeto é distribuído sob a licença MIT.

Consulte o arquivo LICENSE para mais detalhes.

---

## 👨‍💻 Autor
André Felipe da Silva Ramos

💼 Desenvolvedor Full Stack

📧 https://andrefsramos.tech/