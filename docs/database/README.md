# Database

The ZCare schema is PostgreSQL 16 (16 or later accepted), owned entirely by Liquibase. The
changelog lives in `src/main/resources/db/changelog/`:

```
changelog-master.xml          <include> elements only, in file-sequence order (DB-02)
migrations/
  20260929_01_database_functions.xml
  ...
  20260929_20_security_posture_checks.xml
```

What each file creates: [migration-plan.md](migration-plan.md). Why the changelog carries SQL
rather than Liquibase change types: [ADR-0003](../adr/0003-liquibase-xml-changesets-carrying-postgresql-sql.md).

## Naming in force

The names of the agreed design are kept exactly (DB-13, legacy-mirror case; ADR-0003).

| Object | Convention | Example |
|---|---|---|
| Table | `zc_<singular_snake_case>` | `zc_care_plan` |
| Primary key column | `id bigint GENERATED ALWAYS AS IDENTITY` | |
| Foreign-key column | `<referenced_entity>_id` | `enrolment_id` |
| Reference to an external system | `<name>_ref`, text, never a foreign key | `plan_ref` |
| Primary key / foreign key | `pk_<table>` / `fk_<table>_<purpose>` | `fk_zc_goal_care_plan` |
| Unique / index | `uq_<table>_<purpose>` / `ix_<table>_<purpose>` | `uq_zc_care_gap_dedup` |
| Check / trigger | `ck_<table>_<rule>` / `trg_<table>_<rule>` | `trg_zc_enrolment_pins` |

**Constraint names are part of the API contract.** The API maps a violated constraint to an
error code (04D section 9.3), so renaming one is an API change.

Every mutable table carries `created_at`, `created_by`, `updated_at`, `updated_by` and
`row_version`. The shared `zc_touch_row()` trigger maintains the update columns and refuses changes
to `id`, `tenant_id` and the `created_*` columns. Append-only tables carry only `created_at` and
`created_by`.

## Tenant isolation and the two connections

- Every tenant table carries `tenant_id bigint NOT NULL`. It has a foreign key to `zc_tenant`,
  and every index on the table leads with it (DB-15).
- Row-level security is enabled and forced on every tenant table. Its policy admits only rows
  whose `tenant_id` equals the `zcare.tenant_id` setting of the current transaction. With the
  setting unset, every tenant table reads empty.
- **Migrations** run as the schema owner (`DB_MIGRATION_USERNAME`). That role may create roles
  but is not a superuser.
- **The service** connects as `zc_app`. That role owns nothing, holds no `DELETE` anywhere and has
  no `UPDATE` on append-only tables. Superusers bypass row-level security, so nothing may connect
  as one.

Four tables have no row-level security by design:
- `zc_tenant` and `zc_tenant_whatsapp`, registries read before a tenant is known;
- `zc_user`, because login happens before the tenant is known;
- `zc_security_event`, which records events before a tenant is resolved.

`ApplicationRolePostureIT` fails if that list changes.

## Precondition policy

This is a greenfield schema, so every changeset declares its preconditions with `onFail="HALT"`
(DB-08). A precondition that fails stops the update: drift is never marked as run.

## Adding a migration

1. Create `migrations/YYYYMMDD_NN_<concern>.xml`, dated the day it is written, with the next
   project-wide `NN` (DB-03). Start it with the header comment: file name, one-line summary,
   owning domain package and author (DB-06).
2. Give changesets the ids `YYYYMMDD-NN-SS` (DB-04). Order them: tables → foreign keys →
   triggers → row-level security → grants (DB-07).
3. Give every changeset:
   - `onFail="HALT"` preconditions;
   - a `<comment>`;
   - its SQL in `<sql splitStatements="false">`, with `dbms="postgresql"`;
   - a `<rollback>` inside it (DB-10).
4. Name every constraint, index and trigger explicitly (DB-12).
5. For a new tenant table:
   - add the tenant key, row-level security and grants to `zc_app`;
   - grant no `DELETE`;
   - for an append-only table, grant no `UPDATE` and add the append-only triggers.
6. If the table is exempt from row-level security or append-only, extend the lists in
   `20260929_20_security_posture_checks.xml` and `ApplicationRolePostureIT`.
7. Add the file's `<include>` to `changelog-master.xml`, and make the same schema change to the
   agreed-design baseline (`src/test/resources/fixtures/db/zcare-agreed-design.sql`).
8. Run `./mvnw verify`.

Once a changeset has run on any shared database it is never edited, renamed or moved (DB-05).
Correct it with a new changeset. The one exception is the read-only posture check, which is
`runOnChange`.

Data changesets (reference data, synthetic test data) use the `seed` and `test-data` contexts
(DB-16). Every context used must be listed in `spring.liquibase.contexts` for the environments
that need it.

## The agreed-design baseline

`AgreedDesignParityIT` builds two databases: one from the changelog, one from
`src/test/resources/fixtures/db/zcare-agreed-design.sql`. It then requires their catalogues to be
identical: tables, columns, constraints, indexes, triggers, functions, policies, privileges,
sequences and comments. The baseline is the roadmap's `db.sql` with its V003 corrected
([ADR-0004](../adr/0004-schema-baseline.md)).

How the changelog differs from `db.sql` in form, with the same resulting schema:

- V003, V004 and V005 are folded into the concern files of the tables they change.
- Foreign keys are added in their own changesets, after the tables they reference (DB-07).
- Grants sit with each table, with one explicit sequence grant per table instead of
  `GRANT USAGE ON ALL SEQUENCES`. The V002 `REVOKE ALL ON ALL TABLES` statements are dropped,
  because they do nothing on fresh tables.
- The V002 sanity checks become the posture changeset. It also exempts `zc_user` and
  `zc_tenant_whatsapp`, which now exist when it runs.
