# 0006. Cross-cutting writers and in-process domain events

**Status:** Proposed · **Date:** 2026-10-07

## Context

M02 must provide writers for the audit trail, security events, idempotency and the
transactional outbox. Their tables belong to the M17 and M18 domains (`domains.audit`,
`domains.integration`). OD-08 asks where these writers live. ADR-0002 forbids infrastructure
depending on a domain, and forbids a domain using another domain's entities.

Some business rules also span domains. Ending an enrolment must close that member's open tasks,
gaps, outreach, refills and referrals. Approving a care plan must turn its interventions into
tasks. Calling those domains directly from `enrolment` or `careplan` would create dependency
cycles.

## Decision

1. **The writers are infrastructure and use plain JDBC (OD-08).**
   - `common.audit.AuditWriter`, `common.outbox.OutboxWriter` and
     `common.idempotency.IdempotencyStore` live in `common`.
   - `security.SecurityEventWriter` lives in `security`.
   - None of them maps an entity, so `domains.audit` and `domains.integration` remain the only
     mappers of their tables. `domains.audit` maps `zc_domain_audit` read-only for the audit
     query.
   - The audit and outbox writers require an existing transaction (`MANDATORY`), so a change,
     its audit row and its event commit or roll back together.
   - Security events commit in their own transaction (`REQUIRES_NEW`), so a denial is recorded
     even when the request rolls back.
2. **Cross-domain effects inside one request travel as in-process Spring events.** The
   publishing domain defines the event record in its `dto` package, such as
   `EnrolmentExitedEvent` and `CarePlanActivatedEvent`. Listening domains handle it
   synchronously, inside the publishing transaction. The publisher has no compile-time link to
   its listeners.
3. **Two extension points live in `common`** so that composing domains need no link to the
   domains they compose:
   - `ConsentGate`, implemented by `domains.enrolment`;
   - `CareContextContributor`, implemented by every domain that adds to a member's care
     context.
4. **Effects between services travel through the outbox.** Every 04B section 12 event is
   written to `zc_integration_outbox`. Publishing it is the M18 drain's job.

## Consequences

- No open work outlives an enrolment, with no cycle between `enrolment` and the domains holding
  that work.
- A listener failure rolls back the whole request, which is the intended all-or-nothing
  behaviour.
- `ArchitectureTest` enforces the rest: infrastructure never imports a domain, and no domain
  touches another's repositories or entities.
- OD-08 can move to Decided once this ADR is accepted.
