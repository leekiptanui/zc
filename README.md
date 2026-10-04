# zimasa-zcare-service

## Overview

ZCare is Zimasa's care and disease management service. It coordinates care between clinic
visits: identifying a cohort, inviting members, recording their consent, assessment, care plans
that a clinician approves, care work, referrals, medication and observation coordination, care
gaps, WhatsApp outreach and aggregate programme proof. It never diagnoses, prescribes,
adjudicates claims or holds the official medical or payer record.

This repository is the server. The staff web app is a separate project. It is one Spring Boot
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

The Spring Boot application itself arrives with milestone M2. Today you can build and test the
service and create a local database with the full schema:

```sh
cp .env.example .env                                   # then set the three passwords
docker compose --env-file .env -f deploy/compose.yaml up -d --wait   # PostgreSQL 16 on port 5433
scripts/local-db-setup.sh                              # or scripts\local-db-setup.ps1
cp liquibase.properties.example liquibase.properties   # then set the owner password
./mvnw liquibase:update
```

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
| `DB_ADMIN_USERNAME` | setup script only | PostgreSQL superuser that creates the database and roles |
| `DB_ADMIN_PASSWORD` | setup script only | Password of that superuser |

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

There is no HTTP API yet. It arrives with milestone M2, and its Swagger UI address will be listed here.

## Tests

| Command | Runs | Needs |
|---|---|---|
| `./mvnw test` | Unit tests (`*Test`), including the changelog-convention checks | JDK only |
| `./mvnw verify` | Unit tests, then integration tests (`*IT`) against PostgreSQL 16 | Docker running |

The integration tests prove that the changelog builds exactly the agreed design, that one tenant
can never read or write another's rows, that the application role cannot bypass those limits, and
that re-running the migrations changes nothing.

## Further docs

- [docs/architecture/overview.md](docs/architecture/overview.md): project layout, domain packages and dependency rules
- [docs/database/README.md](docs/database/README.md): schema rules and how to change the schema
- [docs/database/migration-plan.md](docs/database/migration-plan.md): what each migration file creates
- [docs/guides/local-setup.md](docs/guides/local-setup.md): local database and test setup
- [docs/adr/](docs/adr/): architecture decision records

The product and engineering canon (the ZCare PRD, the 04A–04F specifications and the executive
migration roadmap) is held in the ZCare workspace, outside this repository.
