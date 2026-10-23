# Closing Count service: operation status R1

Version 0.2.0-dev.6 adds a read-only, actor/outlet-scoped batch query for original pending-operation references. It rechecks current authorization within a consistent database read and exposes no receipt payload. [Contract, evidence and snapshot limitations](../docs/PENDING_OPERATION_STATUS_R1.md). No migration or dependency changes in this release. Migration 005 and team management remain the preceding foundation; see [native team evidence and recovery boundary](../docs/NATIVE_TEAM_R1.md).

GET /api/v1/outlets/:id/team supports limit and after pagination; GET team/:accountId returns a reviewed member. POST team-operations accepts CREATE_ACCOUNT or UPDATE_MEMBERSHIP; GET team-operations/:operationId retrieves a bound receipt. Normal reads/mutations require current ADMIN access. A former administrator may read only their own recorded access-loss update receipt; no roster, foreign result or mutation access follows. Password fingerprints use scoped scrypt and Android persists only non-secret metadata.

Android metadata remains 0.1.1. The native catalog/recipe editor and durable local workspace are integrated; transfer and complete demo acceptance remain open. [Collaborative contract](../docs/ONLINE_CONTRACT.md).

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
- Body limit16KiB (catalog publication512KiB), strict JSON and exact allowed fields, parameterized SQL, no-store responses, no wildcard CORS and redacted unexpected errors. Startup/migration checks database identity/role/instance before writes and records migration checksums. The QA application role owns its dedicated database for migrations; runtime/migrator privilege separation for deployment is not claimed.

## HTTP contract implemented now

All API responses are `{data, requestId}` or `{error:{code,message}, requestId}`. Send `Authorization: Bearer <opaque token>` for authenticated routes. Mutations require `Content-Type: application/json`.

| Method/path | Body / permission | Result |
| --- | --- | --- |
| GET /api/health | Public, local QA only | Database round-trip; service/environment label |
| POST /api/v1/auth/login | username/password | accessToken, expiresAt, tokenType |
| POST /api/v1/auth/logout | `{}`; authenticated | loggedOut |
| GET /api/v1/me | Authenticated | id, username, displayName, active outlets/roles |
| GET /api/v1/outlets/:id | Active membership | Outlet name/role/timezone/cutoffMinutes/settingsRevision |
| POST /api/v1/outlets/:id/operation-status | Active membership; operationIds: 1..100 unique UUIDs; 8KiB | Ordered operationId/recorded pairs scoped to current actor/outlet; read-only query, no receipt body |
| POST /api/v1/outlets/:id/settings | ADMIN; operationId/expectedRevision/name/timezone/cutoffMinutes | Durable receipt, next settingsRevision; SETTINGS_CONFLICT for stale review; no historical closing rewrite |
| GET /api/v1/outlets/:id/business-date | Active membership | businessDate/serverTime/timezone/cutoffMinutes |
| GET /api/v1/outlets/:id/memberships | ADMIN | Scoped public account/membership fields |
| POST /api/v1/outlets/:id/accounts | ADMIN; username/displayName/password/role | New individual account+membership; HTTP201 |
| PUT /api/v1/outlets/:id/memberships/:accountId | ADMIN; role/active | Updated membership and audit, HTTP200 |

Unknown/invalid/disabled login accounts share INVALID_CREDENTIALS. Existing disabled-account sessions receive ACCOUNT_DISABLED. Expired/revoked/missing sessions receive SESSION_REQUIRED. Outlet access denied is OUTLET_FORBIDDEN without unrelated records. Removing membership does not destroy local drafts or automatically erase the global account; Android partition/recovery behavior remains CCS-05.

Roles are EMPLOYEE/SUPERVISOR/ADMIN. Username is normalized lowercase ASCII3..64characters. Password creation requires12characters and at most256UTF8bytes. No self-registration, password-reset provider, global admin, external email, account deletion or destructive database reset endpoint.

## Catalog and collaborative closing endpoints

| Method/path | Input / result |
| --- | --- |
| GET /api/v1/outlets/:id/catalog | Active member; latest versionId/revision/catalog or revision0 empty catalog |
| POST /api/v1/outlets/:id/catalog | ADMIN; operationId, expectedRevision, catalog. Publishes an immutable new version; stale version returns CATALOG_CONFLICT |
| POST /api/v1/outlets/:id/closings | Active member; businessDate ISO date. HTTP201 first creation,200 existing; same-date races obtain one closing |
| GET /api/v1/closings/:id | Consistent repeatable-read snapshot: header, catalog, entries and ingredientTotals |
| POST /api/v1/closings/:id/entries/:menuId/operations | operationId, generation, expectedEntryRevision, quantity; quantity integer0..9999 |
| GET /api/v1/outlets/:id/closings?limit=20&before=YYYY-MM-DD | Scoped date-descending cursor pages, items/nextBefore |
| GET /api/v1/closings/:id/audit?limit=50&after=0 | Scoped numeric-ID-ascending cursor pages, items/nextAfter. IDs remain strings, including values above JavaScript's safe integer range |

Catalog contains ingredientCategories/menuCategories (`id,name,sortOrder`), ingredients (`id,categoryId,name,sortOrder,active`) and menus (`id,categoryId,name,sortOrder,active,ingredientIds`). IDs are UUIDs. Caps:100categories of each kind,1000ingredients,500menus,100distinct ingredient references per recipe, plus the request byte limit. References/duplicates/boolean/integer types are validated; active menus require active ingredients. Disabling a menu means future closings omit it, while old frozen snapshots retain it. Native Room integer IDs will require an explicit mapping at migration/import, not implicit conversion.

A closing freezes active menus/recipes, names/categories, catalog version, timezone and cutoff. Ingredient use follows the existing one-use-per-listed-ingredient/menu formula, excludes zero totals, uses checked BigInt calculation and returns safe numeric totals. New catalog/settings versions cannot rewrite that history. Database triggers protect catalog versions, closing snapshots, audit and receipts from update/delete; there is no rewrite endpoint.

Receipt keys are bound to outlet+actor+operationId; a normalized request fingerprint includes resource/kind/expected revisions/payload. Lock order is operation key -> outlet -> actor -> closing -> entry. Receipt lookup occurs only after current auth/role checks under resource locks. Membership administration shares the outlet lock ordering. Accepted mutation/audit/receipt commit in one transaction; an identical retry returns the original acknowledgement. Reusing an ID for a different payload/kind/resource returns OPERATION_ID_REUSED. A replayed acknowledgement describes the original accepted event, not today's closing state; clients must reconcile/pull current state without overwriting newer local drafts.

Different-menu edits can merge without a global expected revision. Same-menu changes, including change-away-and-back (ABA), return ENTRY_CONFLICT with current quantity, entryRevision, editor/time and closingRevision. New operations require current generation and DRAFT; no-op same-quantity operations still check both plus expected revision and membership, then receive a receipt without an extra revision/audit. Entry response is `{closingId,generation,closingRevision,entry:{menuId,quantity,entryRevision,updatedBy,updatedAt}}`.

Core regression tests still include explicit state/generation fixtures, synthetic bigint audit rows and injected driver/read barriers. The separate CCS-04 suite now exercises actual lifecycle HTTP endpoints and real PostgreSQL, including concurrent actions and rollback after report/audit writes. These remain server tests, not a physical network failure or native offline-sync acceptance.

## Lifecycle and immutable reports

| Method/path | Permission / behavior |
| --- | --- |
| POST /api/v1/closings/:id/submit | Any active member; DRAFT -> SUBMITTED |
| POST /api/v1/closings/:id/return | SUPERVISOR/ADMIN; SUBMITTED -> DRAFT, required reason, generation+1 |
| POST /api/v1/closings/:id/lock | SUPERVISOR/ADMIN reviews exact SUBMITTED revision and locks it in one transaction; creates report |
| POST /api/v1/closings/:id/reopen | SUPERVISOR/ADMIN; LOCKED -> DRAFT, required reason, generation+1 |
| GET /api/v1/closings/:id/reports?limit=20&beforeGeneration=2 | Active member; immutable report metadata pages in generation-descending order |
| GET /api/v1/closings/:id/reports/:generation | Active member; complete frozen report; absent generation404 |

Every lifecycle body has `operationId,generation,expectedRevision,deviceSyncState`; return/reopen additionally require `reason` (trimmed1..500characters). Only `deviceSyncState: "SYNCED"` is accepted. This is a declaration about the submitting device; the server cannot know another device's unsent drafts or verify a client's local Room queue. The native client must enforce its real queue/conflict state at CCS-05/06. Known pending/conflict/auth states return LOCAL_CHANGES_PENDING; an old server revision/generation is rejected independently of the declaration.

All lifecycle mutations use the same scoped operation receipts and ordered locks as entry edits. Permissions and sessions are rechecked after waiting for locks and before receipt replay. Each transition increments closingRevision. Return/reopen clear current submitted/locked metadata and start a new edit generation; entry revisions and the closing's frozen recipe stay intact. Old drafts cannot bypass the generation boundary, including unchanged-quantity operations.

Lock computes all results on the server and atomically stores state, captured actor/time, a report snapshot, audit and receipt. Report schemaVersion1 includes format, report ID, closing/outlet/generation/revision, reviewer/time and the full locked closing/catalog/entries/ingredient totals. Reopen preserves previous reports; a later lock creates a separate report for its generation. A returned-but-never-locked generation has no report. Database checks enforce state/actor metadata; triggers reject report update/delete. Existing rows are not destructively migrated; migration003 is additive and keeps001/002 checksums.

Original operation replay returns the original acknowledgement even if today's state/generation is newer. It never repeats a reopen, restores old quantities or creates another report. Native clients must reconcile monotonically and pull current state. The service does not expose report rewrite/delete, client-supplied totals or reviewer impersonation.

Verified service regression: 20 unit tests and 60 real HTTP/PostgreSQL scenarios (64 Node tests including four parent suites). Five new settings scenarios cover authorization/validation, concurrent administrators, frozen dates, transactional rollback and immutable/revoked retries. Earlier lifecycle checkpoint below remains historical. Driver failures after actual report/audit statements, queued membership/session revocation and concurrent lifecycle races are controlled tests. Android/Room, physical HP, budget/provider, public demo and final portfolio acceptance remain separate program phases.
