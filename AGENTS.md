# WAUTECH CRM — Agent Instructions

## Project purpose

WAU TECH CRM is a production-oriented, multi-tenant CRM platform.

It will first be used internally by WAU TECH and later validated as a commercial SaaS product.

The engineering team currently consists of:

- Wajahat — technical/project lead
- Asad — developer
- Umar — developer

The project is being developed with AI assistance, but all architectural and security-sensitive decisions require human approval.

---

## Approved backend stack

- Java 25 LTS
- Spring Boot
- Maven with Maven Wrapper
- PostgreSQL
- Flyway for database migrations
- Docker / Docker Compose
- JUnit 5
- Spring Boot Test

---

## Architecture

Use a modular monolith.

Do not introduce microservices unless an Architecture Decision Record explicitly approves them.

The backend must have clear domain/module boundaries.

Expected future modules include:

- identity
- organization
- company
- contact
- lead
- opportunity
- pipeline
- activity
- task
- notification
- audit

---

## Multi-tenancy

Multi-tenancy is a core architectural requirement.

Do not implement cross-tenant data access.

Do not create shortcuts that bypass tenant boundaries.

Any future tenant-sensitive persistence or API code must enforce tenant isolation server-side.

---

## Security rules

Never:

- commit passwords
- commit API keys
- commit AWS credentials
- commit database production credentials
- log secrets
- disable authorization checks to make tests pass
- expose internal stack traces through APIs

Use environment-based configuration for secrets.

---

## Development rules

Before modifying code:

1. Read the task carefully.
2. Inspect existing architecture and code.
3. Explain the intended changes briefly.
4. Make the smallest coherent change.
5. Add or update automated tests.
6. Run relevant tests.
7. Report what changed and any assumptions.

Do not introduce a new framework, library, database, infrastructure product, or major architecture pattern without explicit approval.

---

## Code quality

Prefer:

- readable code
- explicit naming
- constructor injection
- small cohesive classes
- DTO validation
- service boundaries
- centralized error handling
- database migrations
- automated tests

Avoid:

- giant controllers
- business logic in controllers
- static global state
- magic strings
- duplicated logic
- premature abstractions

---

## Git workflow

Do not commit directly to main.

Use feature/setup branches and pull requests.

Examples:

- setup/backend-foundation
- feature/company-contact
- feature/opportunity
- fix/login-validation

Every meaningful change should be reviewed before merge.

---

## Current milestone

Milestone 0.1 — Backend Foundation

Acceptance criteria:

- Spring Boot application created
- Java 25 configured
- Maven Wrapper included
- PostgreSQL available via Docker Compose
- Flyway configured
- application can connect to PostgreSQL
- /api/health endpoint returns HTTP 200
- automated health endpoint test passes
- application starts successfully
- no CRM business entities implemented yet
