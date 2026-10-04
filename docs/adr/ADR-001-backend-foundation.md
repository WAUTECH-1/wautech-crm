# ADR-001 — Backend Foundation

## Status

Accepted

## Context

WAU TECH CRM needs a production-capable backend that is understandable by a small engineering team and suitable for iterative SaaS development.

## Decision

Use:

- Java 25 LTS
- Spring Boot
- Maven Wrapper
- PostgreSQL
- Flyway
- Docker Compose
- JUnit 5

Use a modular monolith rather than microservices.

## Reasons

- strong Java ecosystem
- production maturity
- clear learning path for the team
- lower operational complexity than microservices
- good support for testing, persistence, validation, security, and observability

## Consequences

The system begins as one deployable backend application.

Internal modules must still maintain clean boundaries so parts may be extracted later if business requirements justify it.

## Not decided yet

The following remain open:

- authentication provider
- authorization model details
- ORM strategy details
- Redis usage
- AWS runtime
- infrastructure as code
- observability stack
