# Admin and operational support (Feature #18)

## Health and readiness

The public application probe `GET /api/health` (also `/api/health/liveness`) reports only `{"status":"UP"}` while the process can serve requests. It does not query PostgreSQL, so a temporary database outage does not fail liveness. `GET /api/health/readiness` checks a JDBC connection and returns only `UP` or `DOWN`; a database failure returns HTTP 503 without exception details. Actuator liveness/readiness groups are available at `/actuator/health/liveness` and `/actuator/health/readiness`. The readiness group includes database health; liveness excludes external dependencies. Component details and health discovery are disabled.

## Metrics and operational access

Spring Boot Actuator and Micrometer expose `GET /actuator/metrics` and `GET /actuator/prometheus`, plus metric-specific read endpoints. This includes standard HTTP request counts/latency/outcomes, JVM runtime and memory metrics, and Hikari connection-pool metrics when the pool is configured. No organization, user, email, token, or request-content tags are added. Default URI tags use Spring route patterns.

All actuator endpoints other than the minimal liveness/readiness probes require an authenticated user, a valid active `X-Organization-ID`, and OWNER or ADMIN membership in that organization. The `/api/ops/health` diagnostic applies the same organization-scoped permission and reports only overall/database status. It does not offer cross-tenant support access. Health/metrics reads do not create audit records. Existing security denial auditing applies to rejected requests.

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
