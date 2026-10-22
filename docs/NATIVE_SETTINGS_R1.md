# Outlet settings and native configuration recovery R1

23 October 2026. Account opens a native outlet settings panel. Employees read the rules; administrators review and save outlet name, WIB/WITA/WIT timezone and business-day cutoff. Invalid time disables submission. Leaving unsaved fields requires confirmation. The form and selected account tab survive ActivityScenario.recreate with the original reviewed revision; a changed server base requires a fresh review. The account/header name refreshes after confirmation.

## Data and recovery

Service 0.2.0-dev.4 adds POST /api/v1/outlets/:id/settings with operationId, expectedRevision, name, timezone and cutoffMinutes. It rechecks authorization inside the transaction, serializes against closing creation, writes settings/audit/receipt atomically and rejects stale revisions. Duplicate IDs return the original receipt; altered payloads or revoked permission cannot replay. Additive migration 004 preserves earlier migration checksums. Old closing timezone/cutoff/catalog snapshots remain unchanged; new closings capture the latest rules.

Android persists non-secret intents in AtomicFile under noBackupFilesDir before sending. Payload/ID stay immutable across store reopen, uncertain results keep SENT, and removal follows a validated receipt plus fresh server settings. Definitive rejection can be dismissed; uncertain/auth-paused work cannot. Account switch/logout includes pending settings alongside Room draft/lifecycle queues. Room stays schema 8. Settings intents do not automatically transfer to another device or another account.

## Verified

- Service: 20 unit tests and 60 HTTP/PostgreSQL scenarios passed (64 Node tests including four parent scopes). Five new settings scenarios cover role/validation, administrator concurrency, frozen dates, receipt-write rollback and immutable/revoked retry.
- Android: 12 JVM and debug/test APK builds passed; three AtomicFile storage tests and one settings HTTP scenario passed. The latter deliberately skips local acknowledgement after real server commit, reconstructs the store/repository and verifies retry. This models a lost reply boundary, not an actual OS process kill.
- Native: employee read access, admin invalid-time validation, unsaved-leave guard, retained form after Activity recreation, confirmation, saved settings, refreshed account name and preserved account tab passed. Five actual phone/light screenshots inspected; [gallery](settings-review-r1.html).
- Regression: seven existing Android HTTP/session scenarios and the original draft/validation/conflict/use-server/logout native scenario passed. All five runtime runs used identical final app/test APK hashes.
- Independent PostgreSQL checks: HTTP fixture has three settings receipts/audits despite retry/conflict; native fixture has one. Both retain July 1 Asia/Jakarta/00:00 while July 2 captures the updated zone/cutoff.

The initial native helper tried to scroll a fixed header button and failed before settings mutations. The helper was corrected; failed-harness and initial visual evidence remain in central evidence. Read-only timezone contrast was improved before final captures. Final lint passed with 0 errors and 16 warnings; report: settings-evidence-r1/lint.txt. [Logs, hashes and database proof](settings-evidence-r1/).

## Remaining

Closing Count and the eight-project goal remain in progress. Native team/account and catalog/recipe editors remain, followed by online backup/import/export, legacy transfer safety and complete demo packaging. Actual settings process-kill/OS restart, radio/offline native UAT, tablet/dark/font scale and physical-phone acceptance remain separate checks. Activity recreation is not a physical rotation or OS-kill claim. Manual intent recovery is not immediate background delivery. Android metadata stays 0.1.1/versionCode 9; service is 0.2.0-dev.4. No operational DB, physical phone, purchase or public deployment was used.
