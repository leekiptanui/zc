# 0005. API envelope, tenant binding and base path

**Status:** Proposed · **Date:** 2026-10-07

## Context

M02 needs three answers before any endpoint is built. The Open Decisions sheet lists them as
open:

- **OD-01, response style.** 04B section 5 makes the ENG-STD-SB-001 envelope mandatory, with
  business exceptions on HTTP 200. The coding standard prefers RFC 9457 Problem Details
  (WEB-02, ERR-02) but allows an envelope when an ADR records it.
- **OD-02, tenant source.** 04B section 3 takes the tenant from an `X-Tenant-Id` header. The
  `tenant` package and the trial roadmap take it from the token.
- **OD-03, base path.** 04B section 4 says `/{tenant}/zcare/v1`; the standard says `/api/v1`
  (API-01).

## Decision

1. **The 04B envelope (OD-01).** Every endpoint returns the eight-field envelope:
   `requestId`, `timestamp`, `status`, `data`, `exceptions`, `nextActions`, `sessionContext`
   and `auditTrail`.
   - Business rules and validation are HTTP 200 with `status: exception` and typed entries
     from the `ZCareExceptionCodes` registry (`common.error.ZCareExceptionCode`). Validation
     yields one entry per field and never a 422.
   - Authentication is 401 and authorisation is 403. Both sit outside the envelope.
     Infrastructure failures are HTTP 500 with `status: error`.
   - `auditTrail.operation` comes from the endpoint's `@AuditOperation`. It equals the
     operation the service writes to `zc_domain_audit`, including for side-effect writes
     made within the same request.
2. **The token is the only tenant source (OD-02).** The tenant is the verified token's `tenant`
   claim; it is never read from the URL or the body.
   - `X-Tenant-Id`, if sent at all, must equal the claim. A mismatch is a 403 and a
     `tenant_violation_attempt` security event.
   - A token with no tenant, or an unknown, suspended or closed one, is a 403.
   - Actor identity and roles also come only from the token; `X-Actor-Id` and `X-Actor-Role`
     are ignored.
3. **Base path `/api/v1` (OD-03),** as the standard's API-01 sets it, defined once in
   `common.api.ApiPaths`. 04B section 4 proposed `/{tenant}/zcare/v1`. That was first built,
   then replaced on 2026-10-07: health data must not be traceable through tenant identifiers in
   addresses, browser history, proxy logs or referrers. Any path outside the base path, and any
   API route not registered, is a 403 (fail closed).
4. **Responses are never stored or passed on.** Every API response carries
   `Cache-Control: no-store`, `Referrer-Policy: no-referrer` and `X-Frame-Options: DENY`.

## Consequences

- Clients branch on `status` and `exceptions[].code`, not on HTTP status, for business
  outcomes. Spring's `ProblemDetail` support stays off.
- No tenant identifier appears in any URL. Row-level security follows the token's tenant, set per
  transaction by `tenant.TenantAwareTransactionManager`; the numeric tenant id never leaves the
  server.
- 04B section 4 and the trial OpenAPI's `servers` entry need updating to `/api/v1`.
- The token itself still names the tenant. Keeping the token out of the browser is
  [ADR-0007](0007-browser-sessions-through-a-backend-for-frontend.md).
- Registry codes are a versioned contract. Three codes have been added beyond the 04B founding
  21 and need adding to 04B (OD-17): `ZCARE_THRESHOLD_REVIEW_REQUIRED` (from ADD-001),
  `ZCARE_CONCURRENT_MODIFICATION` (optimistic-lock conflicts) and `ZCARE_INTERNAL_ERROR` (only on
  `status: error`).
- The Open Decisions rows OD-01, OD-02 and OD-03 can move to Decided once this ADR is accepted.
