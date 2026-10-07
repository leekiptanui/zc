# API

Every path is under `/api/v1` ([ADR-0005](../adr/0005-api-envelope-tenant-binding-and-base-path.md)).
No tenant identifier appears in any URL.
The live OpenAPI document is served at `/v3/api-docs`, with Swagger UI at `/swagger-ui.html`.
The full reference, with each endpoint's rules, request fields, responses and exceptions, is
[ZCare-API-Reference.docx](ZCare-API-Reference.docx).

## Calling the API

- **Authentication.** `Authorization: Bearer <token>`. In deployed environments the token comes
  from Keycloak; locally, mint one with `scripts/dev-token.ps1` or `scripts/dev-token.sh`.
  The token carries:
  - `sub`, the actor;
  - roles, in `realm_access.roles`, `resource_access.zimasa-zcare-service.roles`, `roles` or
    `role`;
  - `tenant`, the tenant code;
  - `org`, a provider user's `zc_organisation.id`.
- **Tenant.** Taken only from the token's `tenant` claim. Clients never name it. An
  `X-Tenant-Id` header naming a different tenant gets 403 and is logged as a
  `tenant_violation_attempt`.
- **Browsers.** A browser never holds the token. The web app calls the web server in
  [web-server/](../../web-server/README.md), which keeps the session server-side, gives the browser
  only an opaque session cookie and forwards `/api/v1/...` with the token
  ([ADR-0007](../adr/0007-browser-sessions-through-a-backend-for-frontend.md)).
- **Caching.** Every response carries `Cache-Control: no-store` and `Referrer-Policy: no-referrer`.
- **Idempotency.** Every POST needs an `Idempotency-Key` header holding a UUID.
  - Repeating a call with the same key and the same body returns the first envelope unchanged,
    with `Idempotency-Replay: true`.
  - Reusing a key with a different body returns `IDEMPOTENCY_KEY_CONFLICT`.
- **Correlation.** Send `X-Correlation-Id` to trace a call through audit rows and events. The
  service generates one if it is missing and returns it either way.

## Responses

Every response is the ENG-STD-SB-001 envelope:

```json
{
  "requestId": "…", "timestamp": "…", "status": "success | exception | error | pending",
  "data": { }, "exceptions": [ ], "nextActions": [ ], "sessionContext": { }, "auditTrail": { }
}
```

| Outcome | HTTP | `status` |
|---|---|---|
| Done | 200 | `success` |
| Business rule or validation refused it; one `exceptions` entry per failed field | 200 | `exception` |
| Done, with a warning such as "held by the frequency limit" | 200 | `exception`, with `data` set |
| Missing, invalid or expired token | 401 | outside the envelope |
| Role not allowed, tenant mismatch, or unregistered route | 403 | outside the envelope |
| Infrastructure failure | 500 | `error` |

Branch on `exceptions[].code`, from the registry in `ZCareExceptionCode`, and act only on
`nextActions`. A HARD_STOP has no next actions. Lists use 1-based `page` and `pageSize`
(at most 200) and return `{items, count, page, pageSize, totalItems, totalPages, hasNext,
hasPrevious}`.

## Endpoints

| Method and path | Roles | Notes |
|---|---|---|
| **Administration** | | |
| `GET /organisations` | programme admin, platform admin | |
| `POST /organisations` | platform admin | |
| `GET /config`, `POST /config` | platform admin | Append-only, effective-dated settings |
| **Programmes** (04B §10) | | |
| `GET /programmes`, `GET /programmes/{id}` | programme admin, clinician | |
| `POST /programmes` | programme admin | Creates draft version 1 |
| `POST /programmes/{id}/versions` | programme admin | New draft copying the latest version |
| `POST /programmes/{id}/observation-types`, `/thresholds`, `/goal-types`, `/gap-rules`, `/task-templates`, `/outcome-measures` | programme admin | Draft only; clinical changes void approval |
| `POST /programmes/{id}:record-clinical-approval` | clinician | |
| `POST /programmes/{id}/publish` | programme admin | `ZCARE_CLINICAL_APPROVAL_REQUIRED` without approval |
| `POST /programmes/{id}:retire` | programme admin | |
| `GET /referral-types`, `POST /referral-types` | read: care team; write: programme admin | |
| `GET /assessment-templates`, `POST /assessment-templates`, `POST /assessment-templates/{id}/publish` | read: care team; write: programme admin | |
| **Cohorts** | | |
| `GET /cohorts`, `GET /cohorts/{id}/members` | programme admin, care manager | |
| `POST /cohorts` | programme admin | Needs a published version |
| `POST /cohorts/{id}/members` | programme admin, care manager | By `memberId` or source identity |
| `POST /cohorts/{id}:identify` | programme admin | Through the payer port; none is connected yet |
| `POST /cohorts/{id}:release` | programme admin | Gate before invitations |
| **Enrolment and consent** | | |
| `GET /enrolments`, `GET /enrolments/{id}` | care team, programme admin | Filters: `filter[status]`, `filter[programmeId]`, `filter[memberId]`, `filter[responsibleCm]` |
| `POST /enrolments:invite` | care manager | |
| `POST /enrolments/{id}/consent:capture` | care manager | Assisted capture validates only after Privacy approval (config `privacy/assisted_consent_privacy_approved`) |
| `POST /enrolments/{id}:activate` | care manager | Consent gate |
| `POST /enrolments/{id}:suspend`, `:resume`, `:withdraw` | care manager | Withdrawal closes open work |
| `GET /members/{id}/care-context` | care manager, clinician | Health context only with consent |
| **Assessment** | | |
| `POST /enrolments/{id}/assessments` | care manager, clinician | |
| `POST /assessments/{id}:complete` | care manager, clinician | Deterministic score and priority tier |
| **Care plans** | | |
| `GET /care-plans/{id}` | care manager, clinician | |
| `POST /care-plans`, `/{id}:submit`, `/{id}:revise` | care manager | |
| `POST /care-plans/{id}:activate`, `:request-changes`, `:reject` | clinician only, never the author | |
| **Care work** | | |
| `GET /work-queues/{role}` | the role's holders, platform admin | |
| `POST /tasks` | care manager, programme admin | |
| `POST /tasks/{id}:complete` | care manager, clinician | `ZCARE_TASK_RESTRICTED` for the wrong role |
| **Referrals and providers** | | |
| `GET /referrals` | care team, provider coordinator | Provider users see only their organisation's referrals |
| `POST /referrals` | care manager, clinician | |
| `POST /referrals/{id}:record-outcome` | care team, provider coordinator | Completion only by a provider or clinician |
| `POST /provider-participations` | programme admin | |
| `POST /provider-actions` | provider coordinator, clinician | |
| **Medication** | | |
| `POST /refill-requests` | care manager | |
| `POST /refill-requests/{id}:record-fulfilment` | care manager, provider coordinator | "Confirmed" only by a provider |
| **Observations** | | |
| `POST /observations` | care team, provider coordinator | A threshold breach raises a clinician review |
| `POST /observations/{id}:invalidate` | care manager, clinician | |
| `GET /enrolments/{id}/observations` | care manager, clinician | Trend; filter by `type` |
| **Care gaps** | | |
| `GET /care-gaps`, `POST /care-gaps` | care team, programme admin | Detection deduplicates |
| `POST /care-gaps/{id}:suppress`, `:close` | care manager, programme admin | Others get `ZCARE_GAP_ACTION_RESTRICTED` |
| **Outreach** | | |
| `POST /outreach-requests` | care manager | Held, not dropped, by the frequency limit |
| **Outcomes and proof** | | |
| `POST /outcomes:record` | programme admin | |
| `GET /programmes/{id}/proof?audience=` | programme admin, payer manager, employer sponsor | Employer figures withheld below the threshold |
| **Audit** | | |
| `GET /audit` | platform admin, programme admin | Metadata only; each query is a security event |

Events written to the outbox use the 04B section 12 names (`common.outbox.DomainEventType`).
