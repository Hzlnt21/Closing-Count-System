# Physical file validation and picker process recovery R1

18 November 2026, Asia/Jakarta. Run3fe72e2e23 follows
[physical DocumentsUI files R1](DEMO_PHYSICAL_FILES_R1.md) on the same authorized
M2101K7BNY / Android13 SDK33 / ARM64 / primary user0. Tested source base
`7103fa689dc87e259b234fb998a043a1470c36fb`; installed0.1.2-demo-local/code10.
No app, schema, dependency, build or install change. Source1465 tracked files plus
two pre-existing untracked October JVM logs remain raw-hash exact before docs.

Same USB debug APK20,681,213bytes/SHA256
`8aa46e927491c5424756a73dc45e5fd9a5748bb90d2145e47d6ff4f0d9f6e8fe`.
Its app-code/artifact base remains38a259a. Earlier10 native/13 independent,
21 mixed file-provider checks and27 JVM/four build guards keep their original
scope. They are not new executions or a total of unique native test cases.

## Observed result

All19 scoped groups pass:16 actual native UI/provider/process-behavior checks
plus3 independent preservation, backup and cleanup groups. Setup/login and cleanup
are included; this is not19 new instrumentation tests or full UAT.

Six NEW, uniquely named synthetic JSON files were copied into owned Downloads
paths and hash checked. They derive from a separate copy of the prior backup;
original bytes, operation references, saved JSON/XLSX and inbox were not edited.
Synthetic credential text is a dummy invalid field, not an actual access token.
Raw fixture JSON and identity values remain private.

| Actual local DocumentsUI flow | Result |
| --- | --- |
| OpenDocument cancel | Truthful cancellation, no preview or mutation; all19 Room tables and original inbox exact. |
| Truncated JSON | Rejected before preview/accept/inbox. Actual generic message is File atau layanan belum dapat diakses, followed by connection/location guidance; a dedicated malformed-file message is not claimed. |
| Duplicate schemaVersion key | Rejected before preview/accept/inbox; Room and original inbox exact. |
| Extra credential field | Strict root schema rejects the synthetic extra field before preview/accept/inbox; Room and original inbox exact. |
| Unsupported schema version | Rejected before preview/accept/inbox; Room and original inbox exact. |
| Foreign account | Structurally valid preview is inert. Explicit Save to inbox stores exact new bytes. Foreign-context warning is visible and Compare with server has a disabled native ancestor; no Compare/Apply/Save is attempted. |
| Foreign service origin | Same inert preview/quarantine and effective disabled Compare control. No foreign-service login or direct request is made by the driver. |
| Own-copy cleanup | Only each NEW foreign quarantine copy is removed using actual app confirmation after its exact fingerprint/card/control is visually scoped. The original inbox and all19 Room tables return/remain exact; all synthetic source Downloads files remain. |

The two foreign files deliberately test quarantine versus execution. Importing an
inert copy does not create authentication, a runnable queue or a new business key.
No foreign account is created, no original operation is replayed and no recovery
Apply is used. Original account/outlet/session context remains separate from file
metadata. Four malformed-format variants do not prove all possible invalid-file,
size, encoding or I/O failure modes.

## Actual process loss while picker is open

With OpenDocument foreground, Android am kill --user0 terminates only this QA app
process; its exact package/process identity is verified first. DocumentsUI stays
foreground. Selecting only the original owned backup recreates the app process
with a different PID and returns to the authenticated summary. No old import
preview or inbox acceptance survives; all19 Room tables and original inbox remain
exact. An expired-picker message is NOT observed and is not claimed.

Explicitly reopening the transfer panel and choosing that original file again
produces a valid INERT preview. It is closed without accepting another inbox copy,
applying a draft or submitting a business operation. Same valid account session
survives; no second login. This is one real QA-process-loss/return scope, not phone
power loss, OS reboot, fresh-device restore or every Activity/provider lifecycle.

Four product screens were visually reviewed: malformed rejection, foreign-account
disabled comparison, post-death summary and fresh preview. Two additional private
screens identify only the new own-copy removal targets. Raw PNGs/trees are private;
no broad accessibility/font/rotation or cloud-provider conclusion is inferred.

## Data, backup and terminal state

Same marked instance, volume, generation and retained runtime. Normal existing-
instance database/migration checks execute, with no new applied migration, fresh
initialization/reset/reseed. All15 prior final row sets match before API start and
all15 match across normal startup. At final,14 non-session tables remain exact,
including all13 receipts,15 audits, closings/current6/6 DRAFT generation2, both
frozen reports unchanged (17November generation1 remains4/6), migrations, catalogs,
identities and settings.
All prior sessions exact; only one new original Employee session is added and then
revoked by actual Logout. No new business/audit/receipt or recovery key.

All19 Room tables match post-login baseline;12 legacy Room tables also match the
initial baseline. Quantity/lifecycle/recovery queues and claims are zero. Original
933-byte JSON/SHA229b7b43,4,587-byte XLSX/SHA6a2e0169 and inbox bytes exact; all six
owned synthetic Downloads files retained. Same UID/code10/data, no clear/uninstall.

Private custom PostgreSQL dump51,682bytes starts PGDMP; pg_restore --list is readable
with15 TABLE DATA entries. All15 row sets stay exact across backup. Actual restore
was not performed. Raw dump, credentials, JSON/workbook, Room and SQL snapshots
remain outside the repository. API4372/PostgreSQL55437 stop normally, ports idle;
only own reverse4372 removed, other mappings exact, account logged out/phone Home.
Canonical91 raw files/HEAD/index/status exact; two old JVM logs remain untracked,
unstaged and byte-exact. No personal files, global setting, other-app stop,
operational DB, source/history deletion or public deployment.

## Observer corrections and remaining gates

The retained app already opened online login, so a fresh-mode entry locator was
unnecessary; the observed Username/Password page was adopted before authentication.
The phase guard blocked a premature login helper. An own-copy removal guard first
stopped before deleting because the button was outside the observed tree region.
After a small scroll, exact fingerprint/control bounds and actual card screenshots
were reviewed before confirmation. Previously passed selections/imports were not
replayed; no original file was deleted. These are QA driver corrections, not app
changes. Startup proof explicitly distinguishes normal existing-instance checks
from fresh initialization.

Remaining physical gates include positive fresh-device transfer/restore, other/
cloud file providers, actual recipient-app opening, broader font/rotation/a11y,
LAN/power/reboot and multi-device concurrency. Native navigation zero UiAutomator
bounds despite actual touch working remains an unresolved accessibility observation.
Full public/Production/program completion, asset/attribution/profile/CV/final user
review are separate gates. Existing emulator ZIP remains emulator-scoped.

[19 scoped groups](demo-file-guards-r1/phone-checks.json),
[case proof directory](demo-file-guards-r1/),
[process recovery](demo-file-guards-r1/picker-process-recovery.json),
[preservation](demo-file-guards-r1/preservation.json),
[backup metadata](demo-file-guards-r1/backup.json),
[cleanup](demo-file-guards-r1/cleanup.json).
