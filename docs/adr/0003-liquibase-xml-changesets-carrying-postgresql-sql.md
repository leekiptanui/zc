# 0003. Liquibase XML changesets carrying PostgreSQL SQL

**Status:** Accepted · **Date:** 2026-09-29

## Context

The agreed schema (`db.sql`, ADR-0004) is PostgreSQL DDL that relies on features Liquibase has no
change type for. These include:

- table-level and multi-column `CHECK` constraints;
- partial and expression indexes;
- an `EXCLUDE USING gist` constraint and `UNIQUE NULLS NOT DISTINCT`;
- `GENERATED ALWAYS AS IDENTITY`;
- PL/pgSQL trigger functions, row-level security policies, roles and grants.

The API maps constraint names to error codes (04D section 9.3), so the names in `db.sql` are part
of the contract. The layout standard prefers Liquibase change types (DB-11). It also asks for
`PK_`/`FK_`-style names (NAM) and, on greenfield projects, one naming convention (DB-13).

## Decision

1. **XML changelogs, SQL inside.** Files follow DB-02 to DB-10:
   - one master of `<include>`s;
   - `YYYYMMDD_NN_concern.xml` names;
   - `YYYYMMDD-NN-SS` ids;
   - `onFail="HALT"` preconditions;
   - a rollback in every changeset.

   Each changeset carries PostgreSQL SQL in a `<sql>` block tagged `dbms="postgresql"`. This
   deviates from DB-11 because most of the schema has no change type. Converting part of it would
   split each table across two notations.
2. **The design's names are kept exactly.** Tables are `zc_*`, and constraints and indexes are
   `pk_`, `fk_`, `uq_`, `ix_`, `ck_` and `trg_` in lowercase. This is DB-13's "mirroring a legacy
   DDL" case, and the NAM `PK_<Table>` form is not used. Renaming a constraint is an API change,
   not a refactor.
3. **One concern file per domain package (DB-06, PKG-06).** Each file creates its domain's tables in
   `db.sql` order, then:
   1. the tables, each with its keys, checks, indexes and comments;
   2. the foreign keys, in separate changesets (DB-07);
   3. the triggers;
   4. row-level security;
   5. the grants to `zc_app` and `zc_housekeeping`.

   Shared functions and the roles come first, in files 01 and 02.
4. **Greenfield policy is HALT (DB-08).** Drift fails loudly; no changeset is marked as run.
5. **One changeset runs on every update:** `20260929-20-01`, the read-only posture checks from
   the design's V002. It is `runAlways` and `runOnChange`, so it can be extended when a table is
   added, and it has an empty rollback because it changes nothing. It is the only exception to
   DB-05's immutability rule.
6. **The build proves both halves:**
   - `ChangelogConventionsTest` enforces the file rules without a database.
   - `AgreedDesignParityIT` builds the changelog and the agreed design side by side and requires
     identical catalogues.

## Consequences

- The changelog is not portable to other databases; ZCare is PostgreSQL-only by design (ADR-0004).
- Reviewers can compare any table's changeset with the agreed design line by line; only the
  foreign keys move, into their own changesets.
- A schema change after first deployment is a new migration file, never an edit (DB-05). The same
  change updates the agreed-design baseline, so the parity test keeps proving the two match.
