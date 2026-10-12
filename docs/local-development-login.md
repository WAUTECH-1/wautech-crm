# Local development login

Run this only on your own development machine. Start PostgreSQL with the repository's Docker Compose configuration, the backend at `http://localhost:8080`, and your frontend at `http://localhost:5173`. Keep database credentials in the ignored `.env` file or environment variables as described in the root README. Java 25 and the Maven Wrapper are required.

Use an existing active local development organization. To find its ID without changing data:

```powershell
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT id, name FROM crm.organization WHERE archived = false;"'
```

From the repository root, run one command (replace the organization ID with your own):

```powershell
.\scripts\Setup-LocalAccount.ps1 -OrganizationId d383d3b6-b00d-4457-ad0d-a17d88946459
```

For another local port or database, add `-DatabaseUrl 'jdbc:postgresql://127.0.0.1:5433/your_development_database'`. Only literal loopback hosts are accepted; remote hosts, JDBC options, and inherited non-setup Spring profiles are refused. You must confirm the complete database URL by typing `LOCAL DEVELOPMENT <database URL>`. This explicit confirmation identifies the target as development; never confirm a production database, including a forwarded production connection. The command is a separate non-web process, is never started by the backend, disables Flyway migrations, and validates the existing schema. It does not create organizations or business data.

Enter and repeat your password at the masked PowerShell prompts. Use 12–72 UTF-8 bytes. The password stays in process memory and a private stdin pipe; it is never placed in arguments, environment variables, files, or logs. Temporary unmanaged buffers are cleared; managed runtime strings cannot be reliably overwritten. Do not enter the password in chat.

The command creates `admin@wautech.local` with the display name `Local Administrator`, provisions its initial BCrypt password through the existing credential service, and creates an ACTIVE ADMIN membership in the specified organization. All account changes share one transaction and the organization is locked to serialize reruns. Existing passwords and matching memberships are preserved. Disabled users or conflicting memberships require manual review; the command never silently changes those states.

The command then verifies login, `/api/auth/me`, organization-authorized `/api/companies` with `X-Organization-ID`, and logout/session invalidation through the running backend's existing API, using normal CSRF tokens. A verification failure after provisioning does not delete the account or reset the password. Rerun using the original password after resolving the reported problem.

Open [the local frontend](http://localhost:5173), enter `admin@wautech.local` and your chosen password, and select your development organization if the UI asks. The frontend must send `X-Organization-ID` for tenant-scoped calls. No frontend repository changes are made by this setup.
