# WAUTECH CRM — Architecture

## Architecture style

The initial system is a modular monolith.

The application will have:

- a React/TypeScript frontend
- a Java/Spring Boot backend
- PostgreSQL as the primary relational database
- Docker Compose for local infrastructure
- AWS for cloud deployment

## Backend responsibilities

The backend will own:

- authentication and authorization
- tenant isolation
- CRM business rules
- persistence
- auditability
- API validation
- integrations
- background processing

## Initial backend modules

Planned modules:

1. identity
2. organization
3. company
4. contact
5. lead
6. opportunity
7. pipeline
8. activity
9. task
10. notification
11. audit

## Deployment direction

Local:
- Docker Compose
- PostgreSQL

Cloud:
- AWS

Exact AWS services will be chosen through future ADRs.

## Current milestone

Milestone 0.1 establishes only the backend platform foundation.

No CRM domain model should be implemented during this milestone.
