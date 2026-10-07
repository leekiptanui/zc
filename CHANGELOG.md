# Changelog

All notable changes to zimasa-zcare-service are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow semantic versioning.

## [Unreleased]

### Added

- The service now runs. The founding API is under `/api/v1`, with every response in
  the ENG-STD-SB-001 envelope. Business refusals are HTTP 200 with typed exception codes.
- Sign-in by bearer token, verified against Keycloak. The tenant comes only from the token and
  never appears in a URL. Naming another tenant in a header is refused and logged.
- API responses are never cached or stored by the browser (`Cache-Control: no-store`) and never
  passed on as referrers.
- A web server (`web-server/`) for the staff web app. It holds each session on the server, so the
  browser keeps only an unreadable session cookie and never sees a token or the tenant. It checks
  an anti-CSRF header on every change, never serves source maps, and sends a strict
  Content-Security-Policy. Keycloak sign-in is still a placeholder; local sign-in covers
  development.
- Every change records an audit entry and writes its event to the outbox in the same
  transaction. Denials and audit exports are recorded as security events.
- Repeating a request with the same `Idempotency-Key` returns the first answer instead of acting
  twice.
- **Programmes:** drafting, clinical approval, publication and retirement. Publication is
  impossible without a clinician's approval, and a published version cannot be changed.
- **Cohorts:** manual inclusion, identification through the payer (none is connected yet) and
  release.
- **Enrolment and consent:** invitation, assisted consent, activation, suspension, resumption and
  withdrawal. Activation needs valid consent to the programme's current wording. Withdrawing
  closes all open work and raises the safe-exit follow-up.
- **Assessments:** deterministic scoring into a priority tier.
- **Care plans:** drafting, submission, clinician approval or return, and revision.
- **Care work:** tasks and role work queues.
- **Referrals and providers:** referrals, provider participation and provider actions. A
  referral completes only on provider or clinician confirmation.
- **Refills:** a refill is confirmed only by a provider.
- **Observations:** a reading beyond its threshold raises a clinician review.
- **Care gaps:** gaps deduplicate, and suppressing or closing one needs an authorised role and a
  reason.
- **Outreach:** health messages are held, not dropped, by the frequency limit, and a withdrawal
  stops all contact.
- **Outcomes and proof:** employer figures below the population threshold are withheld.
- **Queries:** a member's care context, consent-gated, and the audit query.
- Organisations and tenant settings for platform administrators.
- OpenAPI document at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`; health probes at
  `/actuator/health`.

### Configuration

- New variables: `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_JWK_SET_URI`, `KEYCLOAK_AUDIENCE`,
  `ZCARE_TENANT_CLAIM`, `ZCARE_ORGANISATION_CLAIM`, `DB_MIGRATE_ON_STARTUP`, and
  `ZCARE_JWT_HMAC_SECRET` (local only). See `.env.example`.
- Web server: `ZCARE_API_BASE_URL`, `ZCARE_WEB_SIGN_IN`, `ZCARE_WEB_LOCAL_PASSWORD` (local only)
  and optional `ZCARE_WEB_*` settings; see `web-server/README.md`.

### Database

- No schema change. The service applies the existing changelog at startup as the schema owner.
