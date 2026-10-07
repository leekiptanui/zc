# 0007. Browser sessions through a backend-for-frontend

**Status:** Proposed · **Date:** 2026-10-07

## Context

ZCare handles personal health information. Nothing that identifies a tenant, an actor or a role
should be readable from a browser's address bar, console or network tab. [ADR-0005](0005-api-envelope-tenant-binding-and-base-path.md)
has removed the tenant from URLs. Two exposures remain if the web app calls this API directly:

- **The access token is visible.** A single-page app that holds the access token sends it in an
  `Authorization` header on every call. The token is signed, not encrypted, so anyone with the
  network tab can decode it and read the tenant, user id, roles and organisation.
- **A script can steal the token.** A token held in browser memory or storage can be taken by
  injected script (XSS) or a malicious extension, and replayed until it expires.

The current guidance for browser-based OAuth apps (IETF *OAuth 2.0 for Browser-Based
Applications*) is a backend-for-frontend (BFF). The browser never receives a token.

## Decision

1. **The browser never holds a token.**
   - A small server-side component, the BFF, runs the Keycloak sign-in (authorisation code with
     PKCE) and keeps the access and refresh tokens on the server.
   - The browser holds only a session cookie: `HttpOnly`, `Secure`, `SameSite=Strict`, an opaque
     random value. Scripts cannot read it, and it means nothing outside the BFF.
2. **The BFF forwards API calls.**
   - The web app calls the BFF on its own origin: `/api/v1/...` with the cookie.
   - The BFF adds `Authorization: Bearer <token>` and forwards the call to this service.
   - This service is unchanged: it remains a stateless resource server that verifies the token.
3. **The network tab shows only:**
   - same-origin `/api/v1/...` URLs carrying numeric record ids and no tenant;
   - an opaque cookie;
   - response data the signed-in user is authorised to see.
4. **The web app must also:**
   - never log health data to the console;
   - never put names, member numbers or other health data in URLs or query strings;
   - never keep health data in `localStorage`, `sessionStorage` or IndexedDB;
   - ship without source maps;
   - set a strict Content-Security-Policy.

## What cannot be hidden

A browser has to receive the data it shows. A signed-in care manager viewing a member can see
that member's data in the network tab, exactly as on screen. No design prevents this. The
protection is that only authorised staff receive any data:

- role checks on every endpoint;
- row-level security in the database;
- consent gating of all health data;
- audit of every read of a member's care context.

Responses are marked `Cache-Control: no-store`, so nothing stays in the browser cache after the
session.

## Where the BFF runs

Chosen on 2026-10-07 from three options (a gateway in front of this service, a server deployed with
the web app, or the platform's shared gateway): **a server deployed with the web app.** It serves
the web app's files and holds the sessions. It is built as [`web-server/`](../../web-server/README.md),
a separate Spring Boot application.

- Sessions are held in server memory, so tokens never touch disk. A restart signs everyone out, and
  more than one instance needs sticky sessions or a shared session store.
- **Keycloak sign-in is a placeholder** until the realm and clients are agreed (OD-11). The server
  refuses to start in that mode.
- Until then, **local sign-in** (development only, `local` profile) checks configured users and
  signs API tokens on the server with the API's local HS256 secret. The browser contract is the
  same as it will be with Keycloak.

## Consequences

- The web app needs no token handling, and an XSS flaw cannot exfiltrate a token.
- The BFF introduces server-side sessions, which need storage and expiry: a short idle timeout,
  and sign-out that ends both the BFF session and the Keycloak session.
- Cross-site request forgery is blocked by `SameSite=Strict` cookies plus an anti-CSRF header on
  mutating calls, which the BFF checks.
- `SameSite=Strict` needs Keycloak on the same site as the web app (the same registrable domain),
  or the session cookie is not sent back after the Keycloak redirect. Otherwise the cookie must be
  `Lax`.
- The API must be reachable only from the BFF, never from browsers.
