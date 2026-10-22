# Team R1: 0.2.0-dev.5 (2026-10-23)

Migration005, revision-checked team operations, transactional receipts/audit and narrow own-access-loss receipt recovery. Service20unit/72Node integration passed. See ../docs/NATIVE_TEAM_R1.md.

# Service changelog

## 0.2.0-dev.3 — CCS-04

- Additive003 submitted/reviewer metadata and immutable per-generation report storage;001/002 preserved.
- Real submit/return/review-and-lock/reopen endpoints with exact revision/generation, role checks, required correction reason and declared local sync state.
- Server-generated locked reports preserve recipe, quantities/results, contributor and reviewer/time snapshots after reopen or master/account label changes.
- Shared receipt/lock policy prevents duplicate lifecycle effects; queued actions recheck membership/session. Report/audit/receipt failure injection proves transaction rollback before retry.
-20unit/55HTTP-PG scenarios pass (58Node tests including three parents). Room/native, legacy/export and demo/UAT remain pending.
- Owner selected FINORA project257247565 as the visual reference. Initial blue HTML proposal is archived/unapproved; source images currently return403 and have not been inspected.

## 0.2.0-dev.2 — CCS-02B/03

- Additive002 schema: immutable catalog, closing snapshots, scoped receipts, entry revisions and attributed audit. Migration001 checksum is preserved.
- Publish catalog with optimistic revision/idempotency; get/create one outlet/date closing; frozen recipe/settings snapshots and original ingredient formula.
- Transactional quantity operations with per-item conflicts, ABA protection, no-op handling and original receipt replay. Permissions are checked under ordered locks before receipt lookup.
- Repeatable-read detail view, scoped history and numeric bigint audit cursor pagination. A reproduced text-alias sort truncation was corrected; synthetic large IDs cover ordering/precision.
- Thirteen unit tests and35real HTTP/PostgreSQL scenarios (37Node tests including two parents). Includes controlled concurrent edits/retries, failed receipt-write rollback and a real commit during a held read snapshot.
- Android0.1.1 remains offline. Lifecycle, Room/outbox, native UI, legacy import/export and demo/UAT remain next steps.

## 0.2.0-dev.1 — CCS-02A

- First owned PostgreSQL schema/migration for accounts, outlets, memberships, sessions and administrator audit.
- Real HTTP login/logout/current identity, outlet membership gates, administrator account/membership management and server business date.
- Bounded hashed sessions, login throttling, strict payloads, database ownership refusal and last-admin transaction locking.
- Seven unit tests and fifteen real HTTP/PostgreSQL scenarios (Node reports sixteen including the parent suite); baseline Android source is unchanged. Detailed final evidence is kept in the program checkpoint.
- This is local service foundation. Collaborative closing/catalog/entry schema, sync/lifecycle, Room migrations/outbox, native integration, UAT and public demo are not implemented by this checkpoint.
