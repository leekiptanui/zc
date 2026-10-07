# zimasa-zcare-service

## Overview

ZCare is Zimasa's care and disease management service. It coordinates care between clinic
visits: identifying a cohort, inviting members, recording their consent, assessment, care plans
that a clinician approves, care work, referrals, medication and observation coordination, care
gaps, WhatsApp outreach and aggregate programme proof. It never diagnoses, prescribes,
adjudicates claims or holds the official medical or payer record.

This repository is the server. The staff web app is a separate project; it reaches the API only
through the web server in [web-server/](web-server/README.md), which holds each browser session so
the browser never sees a token or a tenant. The service is one Spring Boot
project in the standard Maven layout, with one package per business domain under
`src/main/java/com/zimasahealth/zcare/domains/`; see [docs/architecture/overview.md](docs/architecture/overview.md).

## Tech stack

| Concern | Choice |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.2.2 |
| Build | Maven 3.9, through the committed wrapper |
| Database | PostgreSQL 16; schema owned by Liquibase 4 |
| Sign-in | Keycloak |
| Tests | JUnit 5, AssertJ, Testcontainers 1 |

Decisions and their trade-offs are recorded in [docs/adr/](docs/adr/).

## Prerequisites

- JDK 21, with `JAVA_HOME` pointing at it.
- Docker Desktop, running: the local database and the integration tests both run PostgreSQL 16
  in containers.
- A `psql` client (16 or later) on the `PATH`, used by the local database setup script.

Maven does not need installing: use `./mvnw` (or `mvnw.cmd` on Windows).

## Run locally

```sh
cp .env.example .env                                   # set the passwords and ZCARE_JWT_HMAC_SECRET
docker compose --env-file .env -f deploy/compose.yaml up -d --wait   # PostgreSQL 16 on port 5433
scripts/local-db-setup.sh                              # or scripts\local-db-setup.ps1
set -a; . ./.env; set +a
./mvnw spring-boot:run                                 # migrates as the owner, then serves on :8080
```

The service applies the Liquibase changelog at startup as the schema owner, then connects as
`zc_app`. Tenants are registered by the owner, because `zc_app` can only read `zc_tenant`
(OD-14):

```sql
INSERT INTO zc_tenant (code, name, created_by) VALUES ('acme-health', 'Acme Health', 'local-setup');
```

Then call the API with a local token:

```sh
TOKEN=$(scripts/dev-token.sh pa-001 programme_admin acme-health)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/programmes
```

To use it as a browser would, start the web server too and open <http://localhost:8081>
([web-server/README.md](web-server/README.md)).

Step by step: [docs/guides/local-setup.md](docs/guides/local-setup.md).

## Configuration

Every variable is listed, with a dummy value, in [.env.example](.env.example). Never commit real values.

| Variable | Used by | Purpose |
|---|---|---|
| `DB_URL` | service, setup script | JDBC URL of the ZCare database |
| `DB_USERNAME` | service | Always `zc_app`, the role the schema grants application privileges to |
| `DB_PASSWORD` | service, setup script | Password of `zc_app` |
| `DB_MIGRATION_USERNAME` | migrations, setup script | Schema-owner role that applies Liquibase migrations |
| `DB_MIGRATION_PASSWORD` | migrations, setup script | Password of the schema owner |
| `DB_MIGRATE_ON_STARTUP` | service | `true` (default) applies the changelog at startup as the schema owner |
| `DB_ADMIN_USERNAME` | setup script only | PostgreSQL superuser that creates the database and roles |
| `DB_ADMIN_PASSWORD` | setup script only | Password of that superuser |
| `KEYCLOAK_ISSUER_URI` | service | Keycloak realm issuer; tokens are verified against it |
| `KEYCLOAK_JWK_SET_URI` | service | Signing keys, when not discovered from the issuer |
| `KEYCLOAK_AUDIENCE` | service | Required `aud`; default `zimasa-zcare-service` |
| `ZCARE_TENANT_CLAIM`, `ZCARE_ORGANISATION_CLAIM` | service | Claim names for the tenant code and a provider user's organisation |
| `ZCARE_JWT_HMAC_SECRET` | service, local only | Shared HS256 secret for local tokens; never set where Keycloak is used |
| `SPRING_PROFILES_ACTIVE` | service | `local` for development |

The Liquibase Maven plugin reads its connection from `liquibase.properties` (git-ignored); copy
[liquibase.properties.example](liquibase.properties.example).

## Database & migrations

Liquibase owns the schema; nothing else changes it. The master changelog is
`src/main/resources/db/changelog/changelog-master.xml`, and it includes one migration file per
concern, each owned by one domain package. The schema is the agreed ZCare design:
60 tables with row-level security keeping each tenant's data apart. Migrations run as a schema
owner; the service connects as `zc_app`, which owns nothing and can delete nothing.

How to add a migration, the naming in force and the precondition policy:
[docs/database/README.md](docs/database/README.md). File-by-file contents:
[docs/database/migration-plan.md](docs/database/migration-plan.md).

## API docs

The API is the 04B founding slice under `/api/v1`, with the ENG-STD-SB-001 envelope
on every response. Swagger UI is at `/swagger-ui.html` and the OpenAPI document at
`/v3/api-docs`. Conventions and the endpoint catalogue: [docs/api/README.md](docs/api/README.md).

## Tests

| Command | Runs | Needs |
|---|---|---|
| `./mvnw test` | Unit tests (`*Test`), including the changelog-convention checks | JDK only |
| `./mvnw verify` | Unit tests, then integration tests (`*IT`) against PostgreSQL 16 | Docker running |

The integration tests prove that the changelog builds exactly the agreed design, that one tenant
can never read or write another's rows, that the application role cannot bypass those limits, and
that re-running the migrations changes nothing. `ApiJourneyIT` drives the API end to end over HTTP,
with the service connected as `zc_app`.

`ArchitectureTest` enforces the package rules of ADR-0002: controllers never touch repositories,
no domain reaches into another's persistence, and every endpoint declares its roles and audit
operation.

`ApiJourneyIT` can run without Docker against an existing PostgreSQL 16 superuser: set
`ZCARE_TEST_PG_HOST`, `ZCARE_TEST_PG_PORT`, `ZCARE_TEST_PG_USER` and `ZCARE_TEST_PG_PASSWORD`.

## Further docs

- [docs/api/README.md](docs/api/README.md): API conventions and the endpoint catalogue
- [web-server/README.md](web-server/README.md): the browser-facing web server and the web app's contract
- [docs/architecture/overview.md](docs/architecture/overview.md): project layout, domain packages and dependency rules
- [docs/database/README.md](docs/database/README.md): schema rules and how to change the schema
- [docs/database/migration-plan.md](docs/database/migration-plan.md): what each migration file creates
- [docs/guides/local-setup.md](docs/guides/local-setup.md): local database and test setup
- [docs/adr/](docs/adr/): architecture decision records

The product and engineering canon (the ZCare PRD, the 04A–04F specifications and the executive
migration roadmap) is held in the ZCare workspace, outside this repository.
