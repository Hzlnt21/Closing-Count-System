# Closing Count collaborative release contract

22 October 2026 | Implementation specification r1 under D29/D35. Baseline Android 0.1.1 remains unchanged at this checkpoint. This document defines the implementation target; it is not evidence of working cloud sync. Visual direction is separately reviewable in visual-review.html.

## Product and invariant

One shared closing per outlet and business date. Quantities are the outlet's total menu sales, **not per-employee contributions to be added together**. Each employee has an individual account; every accepted edit records the actor, previous/new quantity, server time and operation ID. Preserve the existing one-ingredient-use per menu sold formula and frozen menu/recipe names/categories. No grams, millilitres, HPP, POS integration or new stock accounting.

Keep the Kotlin/Compose Android application and Room offline storage. Add an authenticated PostgreSQL-backed service and explicit synchronization boundary. Browser visual preview is a design artifact, not a replacement Android app. Public provider, billing, external integrations and physical-phone UAT remain PF-03/04/05 gates.

## Roles and lifecycle

| Capability | Employee | Supervisor | Outlet administrator |
| --- | --- | --- | --- |
| View assigned outlet/history, edit DRAFT, submit synced revision | Yes | Yes | Yes |
| Return submitted closing to DRAFT with reason | No | Yes | Yes |
| Review totals/audit then lock exact SUBMITTED revision | No | Yes | Yes |
| Reopen LOCKED with reason | No | Yes | Yes |
| Manage outlet memberships, catalog, business settings | No | No | Yes |

Membership and active-account checks are performed on every request, including retries; a valid session alone does not grant outlet access. Never accept actor, permission, calculated total or reviewer identity from the client. No mutation of another outlet by guessing IDs.

Team receipt clarification (native team R1): an authenticated actor whose own recorded membership update removed their administration/access may read that single actor/outlet/operation-bound receipt to reconcile the result. This returns only their own recorded update, no roster or other member data. Unknown receipts, other team reads and every mutation still require current administrator access. Account authentication remains mandatory.

States: DRAFT -> SUBMITTED -> LOCKED. Returning SUBMITTED or reopening LOCKED increments an edit generation and requires a nonblank reason. SUBMITTED/LOCKED refuse quantity edits. Review+lock is one server transaction bound to the exact submitted revision; server calculates results from the closing's frozen catalog. Lock records reviewer/time and preserves an immutable report snapshot; reopen retains that snapshot and its audit before creating a new editable generation. Catalog changes cannot rewrite historical reports.

## Identity, date and versions

Use server UUIDs for outlet, account, closing, catalog versions and menu identity. Local installation UUID is diagnostic, never authorization. Unique server constraint (outlet_id, business_date). Creation races return the one existing closing. Resolve identity on the server; do not depend on device integer Room IDs.

Outlet stores IANA zone and business-day cutoff. Proposed demo default: Asia/Jakarta, 00:00; configurable by administrator for future closings. This is a demo default, not a confirmed real-outlet policy. Date is an explicit ISO date selected by user; server returns current business date and validates membership/date format. Offline clock does not determine authoritative edit times or reorder updates. Closing creation snapshots timezone/cutoff/catalog version; later settings changes do not relabel saved dates. Boundary tests cover before/at cutoff and month/year rollover.

Closing revision increases on every accepted mutation; each menu entry has a separate revision. Edit generation changes on return/reopen. This permits merging edits to different menu items while detecting same-item changes, including an ABA change back to the original value. Use integers 0..9999 per menu (existing input boundary); reject negative, fractional, null, text and unknown menu IDs on server rather than clamping. Calculate ingredient totals in checked 64-bit arithmetic and validate representable exports. Sending an unchanged accepted quantity is an acknowledged no-op; it must still check generation/state/membership.

## Sync protocol and atomicity

Pending-backup provenance can use the [PT-03 operation-status read query](PENDING_OPERATION_STATUS_R1.md): POST up to100 distinct original IDs, response booleans only, current actor/outlet/member scope and fresh authorization inside a read-only consistent snapshot. An absent result may precede an older sender's commit; it never bypasses normal current-state/revision/generation checks or authorizes replay from a backup. No receipt payload or other actor's status is disclosed.

1. Persist local quantity + immutable outbox operation in the **same Room transaction** before showing Saved on this device. Each op has UUID, closing ID, menu ID, generation, expected entry revision and new quantity. Edits made during an in-flight request queue a later operation dependent on its acknowledgement; do not silently alter an in-flight payload or base revision.
2. Process one operation at a time per closing/device. Server transaction locks closing then entry, rechecks state/generation/authorization, checks idempotency and expected revision, applies quantity/audit and stores response atomically. Receipt key is bound to actor/outlet/operation ID and a canonical payload fingerprint. Same ID with different payload is rejected; a retry with identical payload returns the stored response while still checking current account/membership. Unique receipt constraints and row locks resolve concurrent duplicate deliveries.
3. Persist acknowledgement, server revisions and removal/advancement of the exact operation atomically. Process termination after server commit but before local ack retries the same ID. Newer local edits remain visible/pending; an old acknowledgement never replaces them. Refresh/pull must merge around dirty/outbox rows, not overwrite them.
4. Different-menu updates can succeed despite a changed closing revision. Same-menu expected revision mismatch returns 409 with latest quantity/revision/actor/time; local draft remains intact and CONFLICT is visible. User chooses Use server value or deliberately Reapply my quantity against the newest revision as a new operation. Another concurrent change may conflict again; never force overwrite automatically.
5. Lifecycle mutations require exact closing revision and no pending/conflict **on the submitting device**. The UI cannot know other offline devices have unsent drafts, so it must not claim global synchronization. Submission freezes accepted server state; a later offline edit receives state/generation conflict, preserving the local draft for explicit reconciliation after return/reopen.
6. 401 pauses queue and requests login to the same account; 403/account revoked suspends operations, never uploads under a different account. Network/5xx retries with bounded backoff and same operation ID. 409/validation errors require action, not an infinite automatic retry. Switching account/outlet separates all local partitions and warns about unsent drafts; no cross-account replay.

Status vocabulary: LOCAL_SAVED, PENDING, SYNCING, SYNCED, CONFLICT, FAILED, AUTH_REQUIRED. Do not show Synced for an HTTP timeout or merely because the internet is available. Offline drafts survive closing/restarting Android. Foreground resume/manual sync supported first; background work can improve delivery but is not required for correctness. No misleading promise of background immediacy.

## Endpoint boundaries (proposed /api/v1)

| Route | Purpose |
| --- | --- |
| POST /auth/login; POST /auth/logout; GET /me | Session and active memberships; password hashes, bounded sessions and throttled login |
| GET /outlets/:id/catalog; GET /outlets/:id/business-date | Authorized catalog/settings/current date |
| POST /outlets/:id/closings {businessDate} | Get/create single shared closing and frozen catalog |
| GET /closings/:id | State, generation, revisions, entries, totals and permitted actions |
| POST /closings/:id/entries/:menuId/operations | Idempotent quantity mutation with expectedEntryRevision/generation/operationId |
| POST /closings/:id/submit; /return; /lock; /reopen | Exact-revision lifecycle operations, operation IDs and required reasons where applicable |
| GET /outlets/:id/closings; GET /closings/:id/audit; /exports | Scoped history, attribution and historical snapshots |

Use explicit error codes OUTLET_FORBIDDEN, SESSION_REQUIRED, ACCOUNT_DISABLED, ENTRY_CONFLICT, CLOSING_STATE_CONFLICT, GENERATION_CONFLICT, REVISION_CONFLICT, OPERATION_ID_REUSED and VALIDATION_ERROR. Error response must not expose unrelated outlet records, SQL or credentials. Detailed schema/auth transport will be pinned in the service source; no external provider is implied by this contract.

## Legacy migration and backup

Keep existing Room v6 tables and historical snapshots; no destructive migration or automatic upload. Add separate online namespace, sync metadata/outbox/conflict tables with explicit migrations and Room schema tests. Legacy history remains visible/exportable as On this device. Backups v1 remain readable with format validation and transactional restore; importing legacy history does not overwrite a shared closing.

Optional import is an explicit, authorized operation with preview, stable source fingerprint, source device/date and duplicate reporting. Existing shared date produces conflict requiring review; no replace-by-date. Original backup/local history remain available. Online cache restore cannot reactivate sessions, replay acknowledged operations or relabel historical authorship. Pending local data backup/versioning must be specified and tested before replacing the baseline restore flow. Identity mappings never infer server UUIDs from Room integers.

## Implementation checklist and required evidence

- CCS-01: source baseline, isolated worktree, this contract and visual proposal; review wider visual direction before applying Compose redesign.
- CCS-02: service schema/migrations/auth/memberships; real isolated DB tests for unique date, wrong outlet, disabled account and authorization.
- CCS-03: transactional entries/receipts/audit plus same/different-menu races, duplicate/reused operation ID, lost acknowledgement and ABA conflict.
- CCS-04: lifecycle/state locks/reopen generations and frozen recipe/report snapshots; races between submit/edit/lock and catalog changes.
- CCS-05: Room migrations, durable outbox/ack/pull reconciliation and account partitions; process death/restart, queue dependency, auth loss, offline/reconnect and validation recovery.
- CCS-06: native UI integration and reviewed visual direction, quick input, unsynced status/conflict resolution, supervisor review/history/settings and keyboard/responsive states.
- CCS-07: legacy backup/restore/export and explicit import safety, date boundaries and numeric limits.
- CCS-08: deterministic isolated demo with two employees + supervisor, start/stop/reset, restrictions, guide/video and GitHub checkpoint. Emulator/service tests first; physical HP batch remains PF-03.

Do not mark LOC complete from an in-memory model or single-client browser prototype. Required end-to-end evidence includes Android Room + service + real PostgreSQL with two isolated app installations/accounts. Emulator proof and later physical HP acceptance are reported separately.
