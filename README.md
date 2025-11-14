# 🧠 Course Scraper

**Course Scraper** é uma aplicação desenvolvida em **Java 17 com Spring Boot**, cujo objetivo é **coletar, armazenar e notificar automaticamente cursos gratuitos e online** disponibilizados por plataformas públicas de ensino.  
O projeto adota uma arquitetura **Hexagonal (Ports and Adapters)**, garantindo separação clara entre regras de negócio, infraestrutura e interfaces externas, facilitando manutenção, escalabilidade e testes.

## 🎯 Propósito do Projeto

O sistema automatiza a **busca, análise e divulgação de novos cursos** em sites institucionais, atualmente com suporte a:

- **EV.G** (Escola Virtual de Governo)  
- **FGV Educação Executiva**  
- **Sebrae Cursos Online**

O **Course Scraper** realiza:
1. **Coleta periódica** de cursos via _web scraping_ (com uso de `Jsoup`);
2. **Identificação de novos cursos** ou atualizações (comparando hashes);
3. **Persistência dos dados** em banco relacional via JPA/Hibernate;
4. **Notificações automáticas** via **Discord** e **Telegram**.

## 🧩 Arquitetura e Estrutura de Pastas

O projeto segue o padrão **Clean Architecture**, isolando a lógica de negócio do código de infraestrutura.

```text
course-scraper/
│
├── src/main/java/tech/andrefsramos/course_scraper/
│   ├── adapters/
│   │   ├── inbound/                       # Interfaces de entrada (API REST, Jobs, agendadores)
│   │   │   ├── api/                       # Controllers REST (AdminController, CoursesController)
│   │   │   ├── jobs/                      # Tarefas agendadas (PendingNotifierJob)
│   │   │   └── scheduler/                 # Agendadores (CollectScheduler)
│   │   │
│   │   ├── outbound/                      # Interfaces de saída (infraestrutura)
│   │   │   ├── http/                      # Conexões HTTP e sessões (HttpFetch, HttpSession)
│   │   │   ├── notify/                    # Envio de notificações (Discord, Telegram)
│   │   │   ├── persistence/               # Repositórios JPA (entities e impls)
│   │   │   └── scrapers/                  # Scrapers de cada plataforma (EVG, FGV, Sebrae)
│   │   │
│   │   └── config/                        # Configurações Spring (Beans e Scheduling)
│   │
│   └── core/                              # Camada de domínio (regras de negócio e use cases)
│       ├── application/                   # Casos de uso (interfaces + implementações)
│       ├── domain/                        # Entidades e políticas de negócio
│       └── ports/                         # Contratos de entrada/saída (interfaces dos adapters)
│
├── src/main/resources/
│   ├── application.yml                    # Configurações de ambiente
│   └── db.migration/                      # Scripts Flyway para inicialização do banco
│
└── pom.xml                                # Configuração Maven (dependências e plugins)
```

## ⚙️ Tecnologias Utilizadas

| Categoria | Tecnologias |
|------------|-------------|
| Linguagem | **Java 17** |
| Framework | **Spring Boot 3.x** |
| ORM / Banco | **JPA / Hibernate**, com **Flyway** para versionamento |
| Scraping | **Jsoup** |
| Logging | **SLF4J + Logback** |
| Build | **Maven Wrapper (mvnw)** |
| Notificações | **Discord Webhook**, **Telegram Bot API** |
| Agendamentos | **Spring Scheduler (@Scheduled)** |

## 🧠 Como o Sistema Funciona

### 1️⃣ Coleta de Cursos (`CollectCoursesUseCase`)
- Cada scraper (`EvGScraperAdapter`, `FgvScraperAdapter`, `SebraeScraperAdapter`) acessa a respectiva plataforma e extrai os cursos.
- Os dados são normalizados e salvos no banco via `CourseRepository`.

### 2️⃣ Detecção de Novos Cursos (`DetectChangesUseCase`)
- Cada curso é identificado por um **hash SHA-256** (baseado no título e URL).  
- O sistema compara o hash com os registros existentes, detectando **novos cursos ou alterações relevantes**.

### 3️⃣ Notificações (`NotifyNewCoursesUseCase`)
- Novos cursos são agrupados em lotes e enviados para:
  - Discord (via `DiscordNotificationAdapter`);
  - Telegram (via `TelegramNotificationAdapter`);
- Caso a notificação exceda o limite de mensagens, um resumo é enviado.

### 4️⃣ Pendências (`PendingNotifierJob`)
- Caso alguma notificação falhe, o sistema mantém o registro como "pendente";
- Jobs periódicos (`@Scheduled`) verificam e reenviam notificações automaticamente.

## 🧩 Descrição das Camadas

| Camada | Função |
|--------|--------|
| **core.domain** | Define as **entidades centrais** (Course, Platform) e **regras de negócio** (CourseChangePolicy). |
| **core.application** | Contém os **casos de uso** (use cases) e suas implementações (services). Essa camada orquestra o fluxo entre domínio e infraestrutura. |
| **core.ports** | Define **interfaces de comunicação** entre o domínio e os adapters externos (repositórios, scrapers, notificadores). |
| **adapters.inbound** | Entradas do sistema — controladores REST, agendadores e jobs que disparam os casos de uso. |
| **adapters.outbound** | Saídas do sistema — implementações concretas dos ports (repositórios JPA, scrapers, notificadores, etc). |
| **config** | Configurações Spring Boot (injeção de beans, scheduling e dependências). |

## 🚀 Como Executar o Projeto Localmente
### 🔧 Pré-requisitos

Certifique-se de ter as seguintes ferramentas instaladas antes de executar o projeto:

- [Java 17+](https://www.oracle.com/java/technologies/javase-jdk17-downloads.html) 
- [Maven 3.8+](https://maven.apache.org/) 
- [Docker](https://www.docker.com/) (opcional, se quiser usar container para banco) - Banco de dados PostgreSQL (padrão) ou compatível configurado
- [Git](https://git-scm.com/install/windows) para clonar o repositório

### 🐘 Banco de Dados (opcional via Docker)

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

🔹 O banco PostgreSQL ficará disponível em localhost:5432

🔹 O Adminer poderá ser acessado em http://localhost:8081

### 🧰 Passo a Passo de Execução

1️⃣ Clonar o repositório:

git clone https://github.com/AndreFSRamos/course-scraper.git


2️⃣ Acessar o diretório do projeto:

cd course-scraper


3️⃣ Abrir o projeto em sua IDE preferida
Recomenda-se o uso do IntelliJ IDEA ou VS Code com o plugin Spring Boot Extension Pack.

4️⃣ Criar o arquivo .env na raiz do projeto
Adicione as seguintes variáveis de ambiente:

```text
DB_URL=jdbc:postgresql://localhost:5432/courses?sslmode=disable
DB_USER=app_user
DB_PASS=app_pass
TELEGRAM_BOT_TOKEN=CHAVE_AQUI
TELEGRAM_CHAT_ID=-100CANAL
PLATFORM_EVG_ENABLED=true
PLATFORM_FGV_ENABLED=true
PLATFORM_SEBRAE_ENABLED=true
DISCORD_WEBHOOK_URL=https://discord.com/api/webhooks/XXX/ZZZ
```

#### ⚠️ Substitua CHAVE_AQUI, DISCORD_WEBHOOK_URL e -100CANAL pelos valores reais do seu bot e canal no Telegram.

5️⃣ Compilar e empacotar o projeto:
```bash
./mvnw clean package
```
6️⃣ Executar o JAR gerado:
```bash
java -jar target/course-scraper-0.0.1-SNAPSHOT.jar
```
A aplicação iniciará em http://localhost:8080
.
As migrações do banco de dados serão aplicadas automaticamente via Flyway.

## 📡 Endpoints Principais
| **Método** | **Endpoint** | **Descrição** |
|--------|----------|-----------|
| **POST** | /admin/collect/{platform} | Inicia a coleta manual para uma plataforma específica (evg, fgv, sebrae).|
| **GET**	| /api/v1/courses| Lista os cursos mais recentes, com filtros opcionais: platform, area, free, since, page, size.|

### 🧪 Exemplo de uso
#### Coletar cursos manualmente da FGV
```bash
curl -X POST http://localhost:8080/admin/collect/fgv
```
#### Listar cursos gratuitos da EVG
```bash
curl "http://localhost:8080/api/v1/courses?platform=evg&free=true&page=0&size=20"
```
#### Filtrar por área de Tecnologia e data
```bash
curl "http://localhost:8080/api/v1/courses?area=Tecnologia&since=2025-11-01T00:00:00Z"
```
## 🧭 Tarefas Automáticas
Tarefa	Frequência	Classe
Coleta de cursos	A cada 12h	CollectScheduler
Notificação de pendentes	A cada 60s	PendingNotifierJob
## 🧱 Banco de Dados

A estrutura é versionada via Flyway (/resources/db.migration):

| **Versão** | **Arquivo** |	**Descrição** |
|------------|-------------|----------------|
| **V1** | V1__init.sql | Criação das tabelas principais |
| **V2** | V2__seed_platforms.sql |	Inserção das plataformas padrão (EVG, FGV, Sebrae)|
| **V3** | V3__add_notified_at.sql | Adição do campo de controle de notificação|

## 🧩 Fluxo Geral do Sistema
    A[CollectScheduler / AdminController] --> B[CollectCoursesService]
    
    B --> C[ScraperPort (EVG, FGV, Sebrae)]
    
    C --> D[DetectChangesService]
    
    D --> E[CourseRepository + SnapshotRepository]
    
    D --> F[NotifyNewCoursesService]
    
    F --> G[NotificationPort (Discord / Telegram)]
    
    G -->|sucesso| H[Marcar como notificado]
    
    G -->|falha| I[PendingNotifierJob → reenviar]

## 🤝 Contribuições
Contribuições são bem-vindas!
Para colaborar:

Faça um fork do projeto;

Crie uma branch (feature/nome-da-feature);

Envie um pull request.

## 📄 Licença
Este projeto é distribuído sob a licença MIT.
Consulte o arquivo LICENSE para mais detalhes.

## 👨‍💻 Autor
André Felipe da Silva Ramos
💼 Desenvolvedor Full Stack
📧 https://andrefsramos.tech/

