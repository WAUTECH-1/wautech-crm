# Phase 4 — secure CRM Intelligence copilot

Date: 2026-10-14. Branch in both repositories: `codex/phase4-secure-copilot`. Based on merged Phase 3 main (frontend `593ba80`, backend `5c1bf2f`). This implementation follows the user's Phase 4 read-only AI mission. Existing Spring Boot, JDBC, session/membership security and apps/web host contracts are reused; no SDK, new framework or standalone AI application is introduced.

## Delivered integration

Production `/assistant` is tenant-gated and available from navigation, sidebar Intelligence assistant and Ask Intelligence. It supports suggested questions, context selection, specific record details, current-session history/reset, loading, safe errors/history refresh, source links, keyboard-native controls and existing themes. Non-streaming responses are deliberate: validation completes before model content is displayed. Disabled configuration shows an honest setup state and cannot submit questions. No production mock answers or business data were added.

Backend `intelligence/` contains a provider interface, OpenAI Responses adapter, fixed read-only CRM tools, durable rate/token budget service, bounded HTTP-session conversations and controller:

- GET `/api/intelligence/status`
- GET `/api/intelligence/conversation`
- POST `/api/intelligence/messages`
- POST `/api/intelligence/conversation/reset`

The server chooses tools from validated user context. The model never chooses executable tools, SQL, organization IDs, API URLs or writes. Model output has a strict recommendations/source-ID schema and is checked again at the common service boundary. All references must exist in the retrieved own-tenant source list; frontend references must map to allowed relative CRM paths. React renders plain text, without model HTML/Markdown execution.

## Questions, evidence and definitions

| Question/context                    | Authorized read tool                                                                      | Evidence and limits                                                                                                                                         |
| ----------------------------------- | ----------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Active leads / lead prioritization  | Current reporting aggregates plus NEW lead sample                                         | Active leads = non-archived NEW, CONTACTED or QUALIFIED; DISQUALIFIED excluded. NEW sample ordered oldest creation first, at most five; no validated score. |
| Which opportunities need attention? | Current opportunity aggregates; past expected-close sample                                | Excludes closed outcomes; date rule uses UTC today, at most five. This is not a probability/forecast or inactivity detector.                                |
| Deals in a stage                    | Allowlisted compatibility stage filter                                                    | Five ordered matching opportunity samples, complete current stage counts separately. Custom pipeline key filtering is not yet an assistant tool.            |
| Overdue tasks / today's priorities  | SQL aggregate pending overdue count and sample                                            | OPEN/IN_PROGRESS due at/before retrieval, at most five oldest due tasks.                                                                                    |
| Recent activities / meeting summary | Latest activity occurrence sample                                                         | At most five subjects, types and occurrence dates. No descriptions, meeting transcript or historical full collection is available.                          |
| Pipeline / business briefing        | Complete current counts/stage counts and up to 40 currency/stage groups plus four samples | Never mix currencies, call pipeline revenue, fabricate historical conversion or treat samples as complete.                                                  |
| Specific permitted record           | Fixed leads/opportunities/tasks/activities detail query                                   | UUID/type validation, same tenant and non-archived predicate, foreign/unavailable ID returns 404.                                                           |

Currency/stage context is capped at 40 groups with an explicit truncation flag and UI disclosure; incomplete groups are never full pipeline totals. Reports retain the full breakdown.

Each turn shows **factual CRM results**, **rules-based observations**, and **AI interpretation/suggested actions** separately. Current facts and amounts come from SQL, not the model. Model prose remains fallible interpretation; reference validation does not mathematically prove its claims. No statistically validated prediction or universal prompt-injection resistance is claimed. Recommendations do not execute mutations or send messages; future write tools require a separate preview/confirmation/service authorization flow.

Each question retrieves fresh evidence. Prior chat turns are retained for viewing but **are not sent back to the provider**; references such as “that deal” should use the specific-record context. No embeddings, vector store, external search, uploads or autonomous agents are configured.

## Authorization and privacy

Existing session-cookie authentication, persisted enabled user/active organization membership and `crmAuthorization.canView` apply. OWNER, ADMIN, SALES_USER and VIEWER can read/ask with the same CRM visibility. Every SQL query binds the authenticated organization; model input cannot override it. Both POSTs require fresh CSRF. Permissions are rechecked after inference; obsolete tenant/account responses are discarded by the frontend. Conversation IDs are scoped to authenticated user, HTTP session and organization, and cannot access another conversation.

Only selected record labels/names and typed statuses/dates/amounts plus aggregates are transmitted with the question. Email, phone, note body, activity/task descriptions, passwords and API credentials are excluded from retrieval. CRM labels and questions remain untrusted; instructions prohibit obeying embedded text, and there is no executable tool channel. Recognized credential patterns and the configured provider key are rejected in input/context/output. This guard is not a comprehensive secret/PII classifier; administrators must review data sensitivity before activation.

Conversations stay in server session memory, with a fixed 20-minute visibility lifetime, a maximum of 10 turns per organization and four active organization conversations per session. A minute sweeper erases expired session history (up to one minute cleanup grace); logout/session expiry/reset clears access/history. Browser expiry refreshes the history. No chat is stored in PostgreSQL, browser local/session storage or provider conversation objects. Deployment must keep HTTP session storage in memory as currently configured; distributed/persistent sessions need a new retention review.

Request logs contain generated request IDs, tool scope/source counts, timings, outcomes and token usage/reservations. They do not contain question text, evidence, responses, API keys or raw provider errors. V23 adds only `crm.ai_budget`: durable counter keys and usage numbers; no conversational content. Counter metadata is retained until an operator applies an approved pruning policy; current-period counters must never be removed to reset spending.

The adapter sets `store:false`, uses foreground non-streaming requests and creates no remote conversations. That does **not** guarantee zero provider retention. OpenAI documents default abuse-monitoring retention up to 30 days with exceptions, and separately approved retention controls; caching has its own conditions. Review the actual project contract before enabling. [Official data controls](https://developers.openai.com/api/docs/guides/your-data). Adapter schema follows [official structured outputs](https://developers.openai.com/api/docs/guides/structured-outputs).

## Server configuration and safeguards

Configure only on the backend host, never a `VITE_` variable or browser settings form:

| Environment variable             | Default / requirement                                                                             |
| -------------------------------- | ------------------------------------------------------------------------------------------------- |
| `AI_ENABLED`                     | false; explicit true activates only valid configuration                                           |
| `AI_PROVIDER`                    | openai; other providers fail closed until an adapter is installed                                 |
| `AI_MODEL`                       | empty; explicitly choose a project-authorized Responses model supporting strict structured output |
| `AI_API_KEY`                     | empty; server secret; do not paste in chat or commit to Git                                       |
| `AI_DATA_RETENTION_ACKNOWLEDGED` | false; operator must approve the selected CRM data/provider policy before setting true            |
| `AI_DAILY_TOKEN_LIMIT`           | 250,000 globally per UTC day                                                                      |
| `AI_MONTHLY_TOKEN_LIMIT`         | 2,000,000 globally per UTC calendar month                                                         |
| `AI_TENANT_DAILY_TOKEN_LIMIT`    | 100,000 per tenant per UTC day                                                                    |

Every potential inference reserves **27,000 tokens** durably before calling the provider. Reservations are not refunded after failures/ambiguous timeouts; retries are separate attempts. Input is bounded to 1,000 question characters, 11,000 evidence UTF-8 bytes and 24,000 total request UTF-8 bytes; output ceiling is 1,000 tokens/64 KiB transport body/six sourced recommendations. The reservation conservatively allows request overhead and output. Limits require at least one reservation. Default tenant daily budget permits three attempts. Database transactions and row locks enforce ceilings across sessions, process restarts and instances sharing this database. Monthly counters prevent restart-based unlimited spending.

Additional limits: six requests per user UTC minute and 30 per tenant UTC hour, one in-flight request per conversation. No automatic provider retries, redirects, arbitrary endpoints or hosted tools. Connect timeout five seconds, HTTP request 30 seconds, future deadline 35 seconds; browser transport deadline 45 seconds. API usage billing varies by configured model: these are conservative token ceilings, not a dollar accounting ledger. Configure provider project spend limits as an independent billing control. HTTP sessions remain single-instance unless an approved deployment/session design changes.

## Verification and live status

- New backend safeguard suite: 20 tests pass (adapter request/response, timeout/error/body limits, permitted references, credential rejection, tool failure, tenant/user conversation separation, post-inference permissions, history erasure, reset/turn cap and budget denial/rollback).
- Disposable real HTTP suite: 15 discovered, 14 passed, one interactive bridge skipped; two AI scenarios use a **mocked provider** with real Spring security, SQL, migrations and HTTP. They verify foreign-tenant exclusion, PII exclusion, injected CRM labels without writes, CSRF, VIEWER access, reset, malformed tools, unverified citations and durable denied spending.
- Production web: 198 tests pass in 15 files, including 11 copilot tests; build and type check pass. All four workspace type checks/builds pass; 280 tests across 28 files total (web 198, Asad 10, Wajahat 36, Umer 36). Root lint, formatting and development orchestration pass. Backend clean verify discovers 400 tests: 385 passed, 15 opt-in skipped, zero failures/errors; packaged JAR builds.
- Live provider smoke: **NOT RUN**. Credential/model presence checks found no configured AI key/model in the process or backend .env; secret values were never printed. Mock adapter tests are not live AI verification.
- Browser setup-state acceptance verifies the dashboard entry, honest disabled submission, suggested questions, keyboard reset, four themes, and mobile/tablet bounds (375/753px content and scroll widths equal). No provider call was made. VIEWER tenant switching opens a separate empty assistant history. Interactive browser bridge: one passed, zero failures/errors/skips. No model response was simulated or live-verified.

## Remaining work

Configure an approved provider model/key and retention acknowledgment, then explicitly authorize a bounded live smoke using disposable/sanitized evidence. Evaluate actual model factuality, abstention and malicious-title behavior before production rollout. Model recommendations are not independently validated facts. Per-tenant owner consent/settings, persistent cross-session chat, streaming, broader record descriptions/transcripts, historical BI, AI mutations, multimodal/voice/VR and autonomous operations remain separate work. Existing recovery/deployment/accessibility/load/security-scan release obligations remain open.
