# Local setup

How to build zimasa-zcare-service, run its tests and create a local database with the full schema.

## 1. Tools

| Tool | Version | Check |
|---|---|---|
| JDK | 21 | `java -version` |
| Docker Desktop | running | `docker version` shows a Server section |
| `psql` client | 16 or later, on the `PATH` | `psql --version` |

The local database runs in Docker; `psql` is only the client the setup script uses.

Point `JAVA_HOME` at the JDK. The Maven wrapper and Maven both use it:

```sh
export JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1"  # Git Bash
```

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12.1'  # PowerShell
```

The first `./mvnw` run downloads Maven 3.9.16 into `~/.m2/wrapper`.

## 2. Build and test

```sh
./mvnw test      # unit tests: no database needed
./mvnw verify    # unit and integration tests: Docker must be running
```

The integration tests start a throwaway `postgres:16` container and never touch your local
database. If Docker is not running they fail with "Could not find a valid Docker environment".
Start Docker Desktop and run them again.

## 3. Create the local database

1. Copy `.env.example` to `.env` and set the three passwords. `.env` is git-ignored.
2. Start PostgreSQL 16 from `deploy/compose.yaml`. It listens on host port 5433, so a native
   install on 5432 is unaffected, and its superuser is `DB_ADMIN_USERNAME` / `DB_ADMIN_PASSWORD`
   from `.env`:

   ```sh
   docker compose --env-file .env -f deploy/compose.yaml up -d --wait
   ```

   Data persists in the `postgres16-data` volume across restarts. `... down` stops the container
   and keeps the data; `... down -v` deletes the data too.

   To use a native PostgreSQL 16 instead, point `DB_URL` and `liquibase.properties` at its port
   and put its superuser in `DB_ADMIN_USERNAME` / `DB_ADMIN_PASSWORD`.
3. Create the database and its two login roles, the schema owner and `zc_app`:

   ```sh
   scripts/local-db-setup.sh
   ```

   ```powershell
   .\scripts\local-db-setup.ps1
   ```

   The script is safe to re-run. Existing roles get their passwords reset, and an existing
   database is left alone.
4. Copy `liquibase.properties.example` to `liquibase.properties`, which is git-ignored. Set the
   schema owner's password, matching `DB_MIGRATION_PASSWORD`.
5. Apply the changelog:

   ```sh
   ./mvnw liquibase:update
   ```

   A first run reports `Run: 222`. Later runs report `Run: 1`: only the read-only posture check
   runs again.

## 4. Look around as the application

Connect as the service would, set a tenant and query:

```sh
psql -h localhost -p 5433 -U zc_app -d zimasa_zcare_service
```

```sql
BEGIN;
SELECT set_config('zcare.tenant_id', '1', true);   -- a tenant id from zc_tenant
SELECT count(*) FROM zc_organisation;              -- only tenant 1's rows are visible
ROLLBACK;
```

Without the `set_config` call every tenant table reads empty. That is row-level security working,
not missing data.

## 5. Run the service

1. In `.env`, set `ZCARE_JWT_HMAC_SECRET` to a random string of at least 32 characters. It signs
   local tokens in place of Keycloak.
2. Start the service with the variables from `.env`. At startup it applies any pending
   changesets as the schema owner, then connects as `zc_app`.

   ```sh
   set -a; . ./.env; set +a
   ./mvnw spring-boot:run
   ```

   ```powershell
   Get-Content .env | Where-Object { $_ -match '^\w+=' } | ForEach-Object { $k, $v = $_ -split '=', 2; Set-Item "env:$k" $v }
   .\mvnw.cmd spring-boot:run
   ```

3. Register a tenant as the schema owner. `zc_app` can only read `zc_tenant` (OD-14).

   ```sql
   INSERT INTO zc_tenant (code, name, created_by) VALUES ('acme-health', 'Acme Health', 'local-setup');
   ```

4. Mint a token and call the API:

   ```sh
   TOKEN=$(scripts/dev-token.sh pa-001 programme_admin acme-health)
   curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/programmes
   ```

   Every POST also needs `Idempotency-Key: <uuid>`. Swagger UI is at
   http://localhost:8080/swagger-ui.html. Conventions and endpoints: [../api/README.md](../api/README.md).

Assisted consent validates only once Privacy has approved the script. Record that as a tenant
setting, with a platform-admin token:

```sh
curl -X POST -H "Authorization: Bearer $PLATFORM_TOKEN" -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" http://localhost:8080/api/v1/config \
  -d '{"scope":"privacy","key":"assisted_consent_privacy_approved","value":"true","changeReason":"Privacy approval recorded"}'
```

## 6. Use it through the web server

Browsers never call the API directly. The web server holds the session and the token
([../../web-server/README.md](../../web-server/README.md)).

1. In `.env`, set `ZCARE_WEB_SIGN_IN=local` and `ZCARE_WEB_LOCAL_PASSWORD` (at least 12
   characters). Keycloak sign-in is a placeholder for now.
2. With the service running, start the web server in a second terminal:

   ```powershell
   Get-Content .env | Where-Object { $_ -match '^\w+=' } | ForEach-Object { $k, $v = $_ -split '=', 2; Set-Item "env:$k" $v }
   .\mvnw.cmd -f web-server\pom.xml spring-boot:run
   ```

3. Open http://localhost:8081 and sign in as `pa-001`, `cm-001`, `cl-001` or `plat-001` with
   that password. Keep the browser's dev tools open: the network tab shows a session cookie and
   same-origin `/api/v1` calls, never a token or the tenant.