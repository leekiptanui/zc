# 0004. Schema baseline

**Status:** Accepted · **Date:** 2026-09-29

## Context

The roadmap names `db.sql` (V001 to V005, 60 tables) as the agreed design for the new database.
Nothing is carried over from the trial system's data. Reading `db.sql` against the engineering
canon (04B, 04D, PRD addendum ADD-001) found four places where they disagree.

## Decision

`db.sql` is the baseline, with these rulings made on 2026-09-29:

| Topic | `db.sql` | Canon | Ruling |
|---|---|---|---|
| Record ids | `bigint` identity | UUIDs (04D section 7, 04B examples) | **Keep `bigint` identity ids.** Roadmap risk R1: the API description must be republished with numeric ids before M2. |
| V004 `zc_user` | Local users with password hashes | Keycloak sign-in; "ZCare keeps no passwords" | **Keep V004 as a placeholder.** Keycloak is the priority from M2; `zc_user` is then reduced to a local copy of role and organisation. |
| V005 `zc_tenant_whatsapp.access_token` | Token stored in the database | 04D section 14: secrets never in the database | **Keep V005 as agreed.** The conflict stays flagged in the column comment until the token becomes a secret-store reference. |
| V003 | "Reconstructed from its title only": `ambiguous_opt_out` plus resolution columns | ADD-001 Part B: `consent_opt_out_ambiguous`, `consent_decline` | **Replace V003 with the trial system's real V003.** An ambiguous opt-out waits for a human through a `CONFIRM_OPT_OUT` task, not resolution columns. |

The corrected V003 sets `zc_inbound_message.interpreted_as` to: `consent_opt_in`,
`consent_opt_out`, `consent_opt_out_ambiguous`, `consent_decline`, `assessment_answer`,
`refill_reply`, `task_reply`, `unrecognised`.

PostgreSQL 16 is the target version. The schema's own guard accepts 16 or later.

## Consequences

- `src/test/resources/fixtures/db/zcare-agreed-design.sql` is `db.sql` with
  only its V003 block replaced. The parity test builds it and the Liquibase changelog side by
  side, and requires them to be identical.
- Folding V003 to V005 into their concern files changes no schema object. Two wording changes
  follow from it:
  - The V002 posture check exempts `zc_user` and `zc_tenant_whatsapp` from row-level security
    explicitly, because those tables now exist before the check runs.
  - The V002 statements `REVOKE ALL ON ALL TABLES` are dropped, because they do nothing on fresh
    tables.
- Open items the schema still carries, in its TODO comments:
  - the approved list of care models (`zc_programme.care_model`);
  - the approved priority-tier names (`zc_risk_classification`);
  - the 26 approved audit operations (`zc_domain_audit.operation`).

  These stay free text until the approved lists arrive (roadmap risk R3).
