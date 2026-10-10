# Admin and operational support (Feature #18)

## Health and readiness

The public application probe `GET /api/health` (also `/api/health/liveness`) reports only `{"status":"UP"}` while the process can serve requests. It does not query PostgreSQL, so a temporary database outage does not fail liveness. `GET /api/health/readiness` checks a JDBC connection and returns only `UP` or `DOWN`; a database failure returns HTTP 503 without exception details. Actuator liveness/readiness groups are available at `/actuator/health/liveness` and `/actuator/health/readiness`. The readiness group includes database health; liveness excludes external dependencies. Component details and health discovery are disabled.

## Metrics and operational access

`GET /actuator/prometheus` is reserved for a dedicated machine principal. When `MONITORING_AUTH_ENABLED=true` and issuer, JWK set URI, and audience are all configured, a separate stateless Spring Security OAuth2 Resource Server chain validates signed JWTs for this exact GET route. Tokens must be signed by a key published by the configured JWK set, have the configured `iss`, be within their validity period, contain the configured audience, and carry the `metrics.read` scope. Spring Security's standard JWT scope conversion supplies the required `SCOPE_metrics.read` authority. Missing or incomplete configuration leaves the route denied by the CRM chain and does not prevent application startup. CRM session authentication cannot access Prometheus, and the monitoring bearer token cannot access CRM APIs or other Actuator endpoints.

Supply configuration through deployment environment or a protected configuration system; source control contains no issuer, audience, key, or working credential:

| Setting | Purpose |
| --- | --- |
| `MONITORING_AUTH_ENABLED` | Explicit opt-in; defaults to `false`. |
| `MONITORING_ISSUER_URI` | Exact trusted JWT `iss` value from the approved identity provider. |
| `MONITORING_JWK_SET_URI` | HTTPS/private-network JWK set URL used for signature verification without startup-time issuer discovery. |
| `MONITORING_AUDIENCE` | Dedicated audience for this CRM metrics resource, e.g. an environment-specific identifier configured by operations. |

The intended future flow is OAuth2 client credentials from an approved identity provider, issuing short-lived JWTs with the metrics audience and `metrics.read` scope. No IdP or token-acquisition integration is included, so live machine authentication is not operational yet. Keep scraping on a private network and require TLS end-to-end (or an explicitly trusted private TLS-terminating proxy). Permit only the scraper workload to reach the endpoint at the network layer. Rotate the scraper credential at the IdP, deploy the replacement, verify scraping, then revoke the old credential; short token lifetimes bound residual access. Revoke a workload/client immediately on compromise. JWKS key rotation follows the IdP's overlap procedure; emergency key revocation may be delayed by verifier/JWKS caches, so account for cache and token lifetime when planning incident response.

Prometheus export is restricted to these meter families: HTTP server request count/timing (`http.server.requests`), JVM memory used/max, GC pause, live threads, process/system CPU usage, and Hikari connection total/active/idle/pending/max/min. Tags are retained only from bounded operational keys (`uri`, `method`, `status`, `outcome`, `exception`, JVM `area`/`id`, and pool name); all other tags are removed. URI values are Spring route patterns. Organization IDs, user IDs, customer data, email addresses, tokens, and request content are not approved. Intentionally omitted families include filesystem/file-descriptor/process details, JVM buffer pools/classes/extra thread counters, logging metrics, database query metrics, and Hikari acquisition/usage timing. Detailed Actuator metric browsing endpoints and unlisted meter families are excluded from export.

Actuator endpoints other than public liveness/readiness and Prometheus require an authenticated CRM user, a valid active `X-Organization-ID`, and OWNER or ADMIN membership in that organization. The `/api/ops/health` diagnostic applies the same organization-scoped permission and reports only overall/database status. It does not offer cross-tenant support access. Health/metrics reads do not create audit records. Existing security denial auditing applies to rejected CRM requests.

### Monitoring verification and deployment dependencies

The integration security tests use locally generated RSA keys and a real Spring Nimbus JWT encoder/decoder to exercise signature, issuer, timestamp, audience, and scope validation; they do not require an external IdP. Local verification: `cd backend; cmd /c mvnw.cmd clean verify` (or use an approved local Maven repository if the global cache is read-only). A successful test run proves the application security configuration and cryptographic checks against the test key, not connectivity or identity-provider interoperability.

Production requires an approved IdP client-credentials policy, metrics-only client identity, short token lifetime and rotation/revocation process, secure environment/secret delivery, private routing, TLS, and a monitored scraper. AWS deployment additionally needs selected private networking/security-group rules and a supported token delivery/integration for the chosen CloudWatch Agent, Prometheus scraper, or OpenTelemetry Collector; this repository provisions none of them. Production JWKS availability, network policy, TLS termination, key-cache behavior, scrape operation, and IdP token issuance remain deployment checks.

## Correlation and logging

Every request receives an `X-Correlation-ID` response header. A supplied ID is accepted only if it is a canonical UUID; malformed values are replaced with a random UUID. The ID is placed in SLF4J MDC for synchronous request processing and included in the log-level pattern, then removed when request processing ends. It is diagnostic metadata only and is never used to identify a user, choose an organization, or authorize a request. Async work must explicitly propagate MDC if added in a future feature. Application code must not log passwords, session cookies, tokens, raw imports, or unnecessary CRM fields. Security and exception handlers continue to return safe problem details without stack traces.

## CI and local checks

`.github/workflows/backend-ci.yml` runs `./mvnw --batch-mode clean verify` on pull requests and pushes to `main` that change backend/workflow files. A disposable PostgreSQL 17 service uses CI-only credentials; the workflow then starts the packaged application and polls the public readiness endpoint. This applies Flyway migrations and exercises JPA schema validation against PostgreSQL. No CI credential is needed from GitHub Secrets.

Local verification from `backend/`:

```powershell
cmd /c mvnw.cmd clean verify
```

For a PostgreSQL-backed local startup, use the repository's Docker Compose service with a development-only `.env`, then run the packaged application or Spring Boot Maven goal. Do not reuse the CI password outside an isolated disposable database.

## PostgreSQL backup and recovery runbook

1. **Choose a consistent backup point.** Use `pg_dump --format=custom --no-owner --no-acl --dbname="$DB_URL" --file="$BACKUP_FILE"` with database credentials supplied through a protected `PGPASSFILE` or managed secret, not command history. For large production databases, evaluate managed PostgreSQL snapshots or continuous WAL archiving with the selected AWS database service.
2. **Protect backups.** Encrypt in transit and at rest. Use a private storage account/bucket, least-privilege backup and restore identities, separate backup access from application access, enable storage versioning/immutability where appropriate, and record backup metadata without customer content. On AWS, evaluate RDS automated backups/snapshots, S3 lifecycle controls, and KMS encryption; this repository deploys none of those services.
3. **Set retention deliberately.** Select retention only after the team approves data classification, legal obligations, storage cost, and deletion policy. Define daily/weekly/monthly copies as needed and monitor backup age and failures. No retention duration is approved in this repository.
4. **Restore only to an isolated empty database for validation.** Decrypt into a controlled temporary location, create a disposable database, and use `pg_restore --exit-on-error --no-owner --no-acl --dbname="$RESTORE_DB_URL" "$BACKUP_FILE"`. Do not use destructive restore flags against shared environments. Restrict access to the restored copy and remove it under the approved data-handling process after verification.
5. **Verify the restore.** Check `flyway_schema_history` for successful versions, start the matching application release with `ddl-auto=validate`, confirm readiness, and run agreed tenant-isolation and representative read/write checks. A backup file existing is not evidence of a successful restore.
6. **Match releases and migrations.** Restore a database backup with a compatible application release and PostgreSQL version. Flyway migrations are forward-only in normal operation; test newer migrations on a restored copy before deployment. Do not edit historical migration files or treat a restore as a migration rollback.

No backup or restore is executed by this feature. The team must approve RPO/RTO targets, backup frequency, retention periods, encryption/key ownership, restore-test cadence, and production recovery responsibilities before external pilots. Restoration remains unverified until a PostgreSQL-backed restore exercise is completed and recorded.

## AWS monitoring and remaining work

For AWS, evaluate CloudWatch Agent/Prometheus scraping or an OpenTelemetry Collector in a private network for metrics, and CloudWatch Logs for structured application logs. Expose operational endpoints only through a private security group or private service network; do not publish them on an internet-facing listener. Choose alarms for readiness failures, HTTP error rate/latency, JVM pressure, pool exhaustion, and database health after pilot baselines are measured. This feature does not add CloudWatch resources, OpenTelemetry, alerting rules, log shipping, distributed tracing, backup automation, user impersonation, or a privileged Platform Administrator identity.

Production still needs a reviewed platform-operations identity and audited support-access policy, environment-specific actuator network controls, alert thresholds, log retention/redaction review, backup automation and demonstrated recovery, and approved RPO/RTO targets. CI is configured but its first hosted run must complete successfully before treating remote PostgreSQL migration verification as confirmed.
