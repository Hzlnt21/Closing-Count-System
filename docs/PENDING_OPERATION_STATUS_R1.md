# Pending operation status R1

23 October 2026. PT-03 adds a bounded server read for pending-backup provenance. Service 0.2.0-dev.6; Android remains 0.1.1/code9 and Room8. No migration or dependency changes. [Recovery plan](ONLINE_PENDING_TRANSFER_PLAN_R1.md).

`POST /api/v1/outlets/:outletId/operation-status` accepts exactly `{"operationIds":["UUID", "UUID"]}` with 1..100 distinct UUIDs and an 8KiB body limit. UUIDs are normalized to lowercase; duplicate canonical IDs, extra fields and query parameters are rejected. Response data is `{"outletId":"UUID","items":[{"operationId":"UUID","recorded":true}]}` in requested order. HTTP POST carries a read query, not a command. Responses use `Cache-Control: no-store`.

Inside a repeatable-read, read-only PostgreSQL transaction, the service freshly checks session/account and active outlet membership. It selects only operation IDs matching the authenticated actor and requested outlet. Missing IDs and IDs recorded by another actor/outlet both return false. Administrators gain no visibility into another actor's receipts. Receipt payloads, fingerprints, historical authors and secrets are never returned. This endpoint does not grant the separate former-admin team receipt exception.

`recorded:false` means absent from this consistent read snapshot. An older sender may still commit afterward. The result is not permission to resend, replay an old operation, or overwrite a newer server value. Recovery must still fetch current target state and use fresh revision/generation/permission checks through the normal guarded draft workflows. Original IDs remain provenance. A revocation committed before this transaction's first authorization read is honored; already established snapshots do not promise instantaneous cancellation of later revocations.

## Verification

22 unit tests and 80 HTTP/PostgreSQL Node tests (including six parent tests) pass on the dedicated owned QA database. The new integration group contains seven scenarios plus its parent:

- Real committed quantity/settings operations, actor/outlet isolation, administrator isolation and opaque missing results.
- Canonical batch order at 100 IDs; invalid/coerced/duplicate/oversized input, anonymous and denied outlet access.
- Business-row digests unchanged across successful/denied read queries; driver verifies `transaction_read_only=on`.
- Membership/account/outlet changes and revoked/expired sessions committed after outer authentication but before the inner snapshot deny access.
- An uncommitted operation returns false without waiting, then true after commit. A commit after the read snapshot is established remains absent in that response and appears on the next request.

Tests use real HTTP and PostgreSQL, controlled driver barriers and synthetic receipt fixtures. Synthetic sensitive markers prove receipt-body exclusion; they are not evidence that application receipts store passwords. Limiter time is advanced in the fixture to avoid unrelated login throttling. Existing mutation/lifecycle/settings/team regression tests pass. [Test logs and source proof](operation-status-evidence-r1/).

No Android changes or fresh Android test run in this checkpoint. The previous 36 Android/25 JVM checks remain inherited evidence. Client transport, recovery inbox, restoration, native picker flows, complete demo packaging and physical/provider UAT remain later gates.
