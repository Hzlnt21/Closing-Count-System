# Native supervisor lifecycle R1 ? 22 October 2026

The approved R2 direction now includes a connected Review tab: employees submit a closing; supervisors return it with a reason, lock it, or reopen it with a reason. Review displays the server snapshot, recipe totals and actors. Confirmation captures the reviewed generation and revision; a changed server base requires a fresh review.

Room schema 8 adds a durable, account/outlet/server-scoped lifecycle outbox without replacing legacy data or quantity operations. Actions are persisted before sending. A lost response retries the same operation ID; an uncertain sent action cannot be discarded. A validated receipt and fresh server snapshot are applied atomically before removing the operation. Pending lifecycle actions prevent quantity edits. Migration preserves existing pending quantity payloads.

## Verified

- 12 JVM tests and debug/test APK builds passed.
- 26 storage/legacy instrumentation tests passed, including migration, rollback, immutable retry, auth recovery and stale review rejection.
- 7 quantity HTTP and 3 lifecycle HTTP scenarios passed against the local service/PostgreSQL.
- Two native UI scenarios passed: original draft/conflict regression and employee/supervisor submit-return-lock-reopen cycle.
- Independent database checks confirm receipt uniqueness, actor attribution and immutable locked reports. The UI scenario changed quantity 8 to 9 after reopening while the locked generation retained 8.
- Six new screenshots inspected; [gallery](lifecycle-review-r1.html). Lint: 0 errors, 16 warnings.

Evidence is in [lifecycle-evidence-r1](lifecycle-evidence-r1/). Final regressions used the same final APK build. Database reopen and an injected lost reply after real server commit were tested; these do not establish actual Android process-kill or two-installation acceptance.

## Remaining

Actual OS process death/reconnect, independent installations, full server history/report UI, admin/settings and online backup/import/export remain open, followed by a complete local demo candidate. Physical-device UAT stays in the combined program phase. No hosting, payment or deployment occurred. Android metadata remains 0.1.1/versionCode 9 and service 0.2.0-dev.3; the Git checkpoint identifies this development slice, not a final distribution.
