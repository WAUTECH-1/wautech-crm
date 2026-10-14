# ADR-004 — bounded read-only CRM Intelligence

Status: implemented under the user's Phase 4 mission; live activation awaits configured model/credentials and explicit provider-data retention acknowledgment.

## Decision

Keep AI inside the Spring Boot modular monolith and apps/web host. Use an AiProvider interface and an initial server-only OpenAI Responses adapter using existing Java HTTP/Jackson dependencies. Default disabled; no automatic model selection, provider endpoint override, credentials in browser code, simulated production inference or autonomous writes.

Use deterministic server-selected read tools and typed own-tenant SQL evidence, with existing persisted membership policy and CSRF. Treat question/CRM labels as untrusted data. Display SQL facts/rules separately from fallible AI interpretation. Restrict recommendations to retrieved references, and never execute provider-selected functions or URLs.

Store bounded conversations only in the current in-memory HTTP session; fixed 20-minute visibility lifetime, minute cleanup sweep, ten turns and four organization conversations. Do not transmit prior turns or create provider conversation objects. New V23 persists only rate/token counters; reserve conservatively before inference, without automatic retry/refund. Budgets survive process restarts and concurrent sessions.

## Consequences

No new infrastructure/framework/SDK. PostgreSQL locks serialize global budget reservations but network inference occurs outside database transactions. Token ceilings are conservative, not dollar billing; provider account spend controls remain required. History needs the existing single-instance session deployment or a new retention-approved session design. Live model factuality/injection behavior is not verified by mocked tests. Enabling provider data sharing requires operator approval of data scope and actual provider contract; store=false does not guarantee zero retention. Future write tools, per-tenant consent, persistence, streaming and broader text retrieval are separate reviewed extensions.

See backend/docs/phase4-delivery.md for exact tools, API/configuration, privacy and validation evidence.

