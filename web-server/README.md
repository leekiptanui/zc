# ZCare web server

Serves the ZCare staff web app and holds each browser session on the server
([ADR-0007](../docs/adr/0007-browser-sessions-through-a-backend-for-frontend.md)). The browser
never receives a token or a tenant. It holds two cookies:

- an opaque session cookie (`HttpOnly`, `SameSite=Strict`, `Secure` outside local runs), which
  scripts cannot read;
- an `XSRF-TOKEN` cookie, a random anti-CSRF value.

The web app calls this server on its own origin. This server adds the bearer token and forwards
`/api/v1/...` to the ZCare API, which browsers must not be able to reach directly.

This is a separate Spring Boot application from the service in the repository root. It ships with
the web app, and can move into the web app's repository once that exists.

## Sign-in

| `ZCARE_WEB_SIGN_IN` | Status |
|---|---|
| `keycloak` (default) | **Placeholder.** Refuses to start until Keycloak sign-in is built (realm and clients still being agreed: OD-11). |
| `local` | Development only; also needs the `local` profile. Users are configured in `application-local.yml` and share `ZCARE_WEB_LOCAL_PASSWORD`. The server signs each API token itself with the API's `ZCARE_JWT_HMAC_SECRET`. |

## The web app's contract

| Call | Purpose |
|---|---|
| `GET /bff/user` | Always 200: `{"authenticated": false}`, or `{"authenticated": true, "name": "…", "roles": ["care_manager"]}`. Never the user id, tenant, organisation or a token. Call it on page load: it also sets the `XSRF-TOKEN` cookie. |
| `POST /bff/login` | Local sign-in: `{"username", "password"}`. 200 with the same body as `/bff/user`, or 401. Starts a new session. |
| `POST /bff/logout` | 204. Ends the session and clears both cookies. |
| `GET` and `POST /api/v1/...` | Forwarded to the API unchanged. 401 when signed out, never a redirect. |

On every `POST`, send `X-XSRF-TOKEN` with the value of the `XSRF-TOKEN` cookie; without it the call
gets 403. Send `Idempotency-Key` on API `POST`s, as before.

Forwarded to the API: `Accept`, `Accept-Language`, `Content-Type`, `Idempotency-Key`,
`X-Correlation-Id`. Everything else is dropped, including cookies, the anti-CSRF header and any
`X-Tenant-Id`. Returned to the browser: the API's status and body, plus `Content-Type`,
`Idempotency-Replay` and `X-Correlation-Id`.

The trial web app (`zimasa-zcare/web`) still sends `Authorization: Bearer` from the browser. To use
this server it must:

- drop the token;
- read `/bff/user` instead of keeping a session in the page;
- send `X-XSRF-TOKEN` on every POST.

`src/main/resources/static/check.js` shows the pattern in about 60 lines.

## What every response carries

- `Cache-Control: no-store`: nothing is kept in the browser cache.
- `Referrer-Policy: no-referrer`, `X-Frame-Options: DENY`.
- A strict `Content-Security-Policy`: scripts, styles and connections only from this origin
  (set `zcare.web.content-security-policy` to change it).
- Source maps (`*.map`) are never served, even if a build ships them.

The session id never appears in a URL, and an idle session expires after 15 minutes
(`ZCARE_WEB_SESSION_TIMEOUT`). Sessions are held in memory, so a restart signs everyone out and
more than one instance needs sticky sessions.

## Run locally

With the API running on `:8080` with `ZCARE_JWT_HMAC_SECRET` set:

```powershell
# .env must also hold ZCARE_WEB_SIGN_IN=local and ZCARE_WEB_LOCAL_PASSWORD (12+ characters)
Get-Content .env | Where-Object { $_ -match '^[A-Z_]+=' } | ForEach-Object { $k, $v = $_ -split '=', 2; Set-Item "env:$k" $v }
.\mvnw.cmd -f web-server\pom.xml spring-boot:run
```

Open <http://localhost:8081> and sign in as `pa-001`, `cm-001`, `cl-001` or `plat-001`. The page
is a session check: keep the browser's dev tools open to see what it holds. To serve the built
web app instead, set `ZCARE_WEB_STATIC_LOCATION=file:/path/to/web/dist/`.

| Variable | Default | Purpose |
|---|---|---|
| `ZCARE_API_BASE_URL` | `http://localhost:8080` | The ZCare API, reachable from this server only |
| `ZCARE_WEB_SIGN_IN` | `keycloak` | `local` for development |
| `ZCARE_WEB_LOCAL_PASSWORD` | none | Local sign-in password |
| `ZCARE_JWT_HMAC_SECRET` | none | The API's local signing secret |
| `ZCARE_WEB_PORT` | `8081` | |
| `ZCARE_WEB_SESSION_TIMEOUT` | `15m` | Idle timeout |
| `ZCARE_WEB_SESSION_COOKIE` | `__Host-zcare` (`zcare` locally) | Session cookie name |
| `ZCARE_WEB_SECURE_COOKIES` | `true` (`false` locally) | `Secure` on both cookies |
| `ZCARE_WEB_STATIC_LOCATION` | the bundled check page | Where the built web app is |

## Tests

`.\mvnw.cmd -f web-server\pom.xml test` runs against a stub API. The tests show that:

- the browser learns only the name and roles;
- the API receives the token and the tenant, and never the cookies;
- state-changing calls need the anti-CSRF header;
- sign-in replaces any earlier session, and sign-out ends it;
- only `/api/v1` is forwarded;
- Keycloak sign-in refuses to start, and local sign-in refuses to start outside the `local` profile.
