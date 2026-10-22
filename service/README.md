# Closing Count service: CCS-02A

Version 0.2.0-dev.1 is an isolated QA checkpoint, not a public demo or an Android release. The existing Kotlin application remains offline 0.1.1. [The collaborative contract](../docs/ONLINE_CONTRACT.md) remains the full implementation target.

This checkpoint implements accounts, outlet memberships, administrator provisioning, login/logout, bounded opaque sessions and server-derived business date. Closing/catalog schema, entries, collaborative sync, lifecycle, Room outbox and native integration are next checkpoints. The visual prototype does not call this service.

## Run and verify

Use Node 24. Install locked dependencies with `npm ci`. Set **only dedicated QA values** for `CCS_ENVIRONMENT=QA`, `CCS_INSTANCE` (the owned instance marker) and `CCS_DATABASE_URL`. The first target profile requires the restricted `ccs_portfolio_app` role, `closing_count_portfolio_qa` database and loopback PostgreSQL port55436. Host API defaults to127.0.0.1:4370. An owner must provision the separate database and protected `public.ccs_instance` marker before migration; there is no fallback to DATABASE_URL, developer defaults or operational settings.

```text
npm run migrate
npm test
npm run test:integration
npm start
```

Integration tests refuse an unowned database, create a unique fictional namespace per run and retain those fixtures. They do not reset or clear data. They start ephemeral HTTP listeners on127.0.0.1 and close them after the run. Test rate-limit windows are explicitly advanced via an injected limiter clock; database expiry fixtures use real PostgreSQL timestamps shifted by the test. These are not claims of physical outages or eight-hour real-time tests.

The development workspace's separate PortfolioQA wrapper provisions a labelled, dedicated postgres:16 container/volume, loopback55436 and private credentials outside the source repository. That automation is not a portable recruiter package. No provider, cloud account, external messaging or physical phone is used here.

## Auth and access boundary

- Passwords use salted scrypt with fixed bounded parameters. No plaintext password in tables, responses or request logs.
- Tokens are 32 random bytes; only SHA256 token digests are stored. Sessions expire after8hours. A locked account transaction retains at most5 live sessions; logout revokes only the selected session. There is no refresh-token endpoint in this checkpoint.
- Every authenticated request checks active account/session. Outlet queries require active outlet membership. Roles/actor headers and client-supplied identities cannot grant permissions.
- Only an outlet ADMIN can list members, create a new individual account or change existing memberships. SUPERVISOR is not an account administrator. A transaction locks the outlet, rechecks access and preserves at least one active administrator. Attribution/audit is stored in the same transaction.
- Rate limiting is per process: ten login requests/IP/minute, five/normalized username/minute, bounded5000 active buckets. Forwarded-for is not trusted. Public shared-sandbox limits/proxy configuration belong to CCS-08/PF-05.
- Body limit16KiB, strict JSON and exact allowed fields, parameterized SQL, no-store responses, no wildcard CORS and redacted unexpected errors. Startup/migration checks database identity/role/instance before writes and records migration checksums. The QA application role owns its dedicated database for migrations; runtime/migrator privilege separation for deployment is not claimed.

## HTTP contract implemented now

All API responses are `{data, requestId}` or `{error:{code,message}, requestId}`. Send `Authorization: Bearer <opaque token>` for authenticated routes. Mutations require `Content-Type: application/json`.

| Method/path | Body / permission | Result |
| --- | --- | --- |
| GET /api/health | Public, local QA only | Database round-trip; service/environment label |
| POST /api/v1/auth/login | username/password | accessToken, expiresAt, tokenType |
| POST /api/v1/auth/logout | `{}`; authenticated | loggedOut |
| GET /api/v1/me | Authenticated | id, username, displayName, active outlets/roles |
| GET /api/v1/outlets/:id | Active membership | Outlet name/role/timezone/cutoffMinutes |
| GET /api/v1/outlets/:id/business-date | Active membership | businessDate/serverTime/timezone/cutoffMinutes |
| GET /api/v1/outlets/:id/memberships | ADMIN | Scoped public account/membership fields |
| POST /api/v1/outlets/:id/accounts | ADMIN; username/displayName/password/role | New individual account+membership; HTTP201 |
| PUT /api/v1/outlets/:id/memberships/:accountId | ADMIN; role/active | Updated membership and audit, HTTP200 |

Unknown/invalid/disabled login accounts share INVALID_CREDENTIALS. Existing disabled-account sessions receive ACCOUNT_DISABLED. Expired/revoked/missing sessions receive SESSION_REQUIRED. Outlet access denied is OUTLET_FORBIDDEN without unrelated records. Removing membership does not destroy local drafts or automatically erase the global account; Android partition/recovery behavior remains CCS-05.

Roles are EMPLOYEE/SUPERVISOR/ADMIN. Username is normalized lowercase ASCII3..64characters. Password creation requires12characters and at most256UTF8bytes. No self-registration, password-reset provider, global admin, external email, account deletion or destructive database reset endpoint.
