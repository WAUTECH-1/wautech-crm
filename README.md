# WAU TECH CRM

Milestone 0.1: backend foundation only. Java 25, Spring Boot 3.5.16, Maven Wrapper, PostgreSQL 17, and Flyway. No CRM entities, authentication, or frontend yet.

## Prerequisites

- JDK 25 (`java -version`); set `JAVA_HOME` to the JDK directory.
- Docker with Docker Compose, running with Linux containers.
- Internet access for the first Maven Wrapper run and Docker image pull. Standalone Maven is unnecessary.

## Local startup (PowerShell)

Create a root `.env` file using plain KEY=value entries (no shell commands). Set DB_NAME=wautech_crm, DB_USER=wautech, and DB_PASSWORD to your local password. This file is ignored by Git. Both Compose and the backend load it when started as shown below. Environment variables override the file.

```powershell
docker compose up -d --wait postgres
cd backend
.\mvnw.cmd spring-boot:run
```

From another terminal:

```powershell
Invoke-RestMethod http://localhost:8080/api/health
```

Expected HTTP 200 with `{"status":"UP"}`. This endpoint reports application liveness, not database readiness. Startup requires a successful PostgreSQL connection and Flyway migration.

For macOS/Linux, from the repository root:

```sh
docker compose up -d --wait postgres
cd backend
sh ./mvnw spring-boot:run
```

## Tests and packaging

From `backend/`:

```powershell
.\mvnw.cmd clean verify
```

On macOS/Linux use `sh ./mvnw clean verify`. The JUnit 5 MVC test verifies the health HTTP response without requiring PostgreSQL. To validate persistence and migrations, run the local startup steps and inspect from the repository root:

```powershell
docker compose exec postgres psql -U wautech -d wautech_crm -c 'SELECT version, description, success FROM flyway_schema_history;'
```

The first migration creates an empty `crm` schema. Flyway owns migration history; Hibernate validates mappings and does not create tables.

## Configuration

The backend reads environment variables and the optional root `.env` file:

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/wautech_crm` | JDBC connection |
| `DB_USERNAME` / `DB_USER` | `wautech` | Database user (`DB_USERNAME` takes precedence) |
| `DB_PASSWORD` | empty in backend; required by Compose | Set locally before startup |
| `SERVER_PORT` | `8080` | HTTP port |
| `DB_NAME` | `wautech_crm` | Compose database name |
| `DB_PORT` | `5432` | Compose host port, bound to loopback |

The default JDBC URL follows `DB_NAME` and `DB_PORT`; `DB_URL` can override it. Start the backend from `backend/` or the repository root so it can find the root `.env`. Use plain, unquoted values in `.env` for compatibility with both loaders; Spring reads it as a properties file. If using backslashes in a value, supply that value through an environment variable instead. Never commit credentials. These defaults are for local development; production configuration must be supplied externally.

Stop the app with Ctrl+C. From the root, `docker compose down` stops PostgreSQL and preserves data. PostgreSQL initialization settings apply only to an empty volume; changing the password requires updating the existing database or deliberately recreating local data.

Architecture decisions: [architecture](docs/architecture/ARCHITECTURE.md) and [ADR-001](docs/adr/ADR-001-backend-foundation.md).
