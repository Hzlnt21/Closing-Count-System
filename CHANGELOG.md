# Changelog

## 0.1.2 - catalog presentation ordering, 2026-10-24

- Display shared-closing menus and ingredients by category/item sortOrder with stable ID ties, consistently across input, supervisor review and frozen reports.
- Preserve canonical snapshot JSON, recipe links, quantities and historical authorship. The previous native demo baseline exposed UUID order changing the visible menu sequence after reset.
- Verified27 JVM tests, lint0errors/17warnings and a normal positive native journey across input/review/frozen report. The old0.1.1 APK is an explicit failing ordering control; business PostgreSQL snapshots and owner/device baseline remain unchanged. [Evidence and scope](docs/DEMO_ORDERING_R1.md).
- The nine-phase0.1.1 domain baseline remains separately recorded at `ccs-demo-native-r1`. Media and complete demo distribution remain open.

## Unreleased - local transfer UI R1, 2026-10-23

- Clarified on-device versus shared-server data. Added validated filename/count/date review, one-use frozen restore identity, cancellation, off-main provider IO, busy state and success/error feedback.
- Fixed clipped large-text local navigation with an accessible destination selector; aligned Material container roles with the blue palette.
- Four controller, ten legacy and two real DocumentsUI/native journeys passed on the same final APK pair; both exported JSON files match the expected dummy snapshot.19JVM/build passed; lint0errors/17warnings unchanged. [Evidence, gallery and remaining scope](docs/LOCAL_TRANSFER_UI_R1.md).

## Unreleased - legacy backup validation R1, 2026-10-23

- Reject silent JSON coercion/truncation, overflow, invalid dates/IDs, duplicate rows, orphaned recipe entries, corrupt UTF-8 and oversized local backups before restore.
- Preserve version1 encoding and migrated historical category0/deleted-master history. Ten Android codec/Room tests prove round trips, transaction rollback and online outbox isolation;19JVM/build passed, lint0errors/17warnings unchanged. [Evidence and remaining transfer scope](docs/LEGACY_BACKUP_R1.md).

## Unreleased - native layout R1, 2026-10-23

- Preserved approved R2 styling while adapting compact navigation, long outlet/date labels, summary totals and quantity input for large text and short viewports. Narrow large-text menu input stacks the full name above quantity; catalog section chips align at equal widths.
- Fixed clipped settings-review and unsaved-change explanations with scrollable bodies.
- Six phone/tablet/light/dark/font-scale configurations, 60 selected scene checks, detector controls and three native workflow regressions passed on the same final APK pair. 19 JVM tests/build passed; lint 0 errors/17 warnings with the new window-size recommendation recorded. [Evidence and limits](docs/NATIVE_LAYOUT_R1.md), [gallery](docs/layout-review-r1.html).

## Unreleased - native process recovery R1, 2026-10-23

- Added native acceptance across six actual force-stops: quantity, submit, settings, team creation, editable catalog draft and committed catalog publication.
- Dedicated QA fixture can hold successful settings/team/catalog replies after commit. Independent PID, installed APK fingerprints and PostgreSQL evidence verify same-ID recovery without duplicate mutation.
- Eleven phases, five normal continuation runs and six inspected recovery screenshots. [Evidence, harness finalization and limits](docs/NATIVE_PROCESS_RECOVERY_R1.md).

## Unreleased - native catalog R1, 2026-10-23

- Added role-based native category/ingredient/menu/recipe editing, search, bounded ingredient selection and reference/deactivation guards in the approved R2 direction.
- Valid row saves persist EDITING workspace records atomically. Review freezes the same ID/payload; frozen work cannot return to an editable state. Stale drafts require explicit resolution.
- Form Activity recreation, local draft restoration and publication preserve old closing snapshots. [Evidence and limits](docs/NATIVE_CATALOG_R1.md).

## Unreleased - catalog client foundation R1, 2026-10-23

- Added typed whole-catalog validation, strict JSON decoding, backup-excluded immutable publication intents and catalog account-switch/logout guards.
- Publication retry validates the original acknowledgement and reads the latest version; stale review requires explicit resolution and same-account reauthentication resumes paused work.
- 19 JVM tests, three emulator storage cases and real HTTP/PostgreSQL publication recovery passed. Native catalog editor remains open. [Evidence and limits](docs/CATALOG_CLIENT_R1.md).

## Unreleased - native team R1, 2026-10-23

- Added paged team administration, account creation, reviewed role/access changes and own-role refresh in the approved R2 direction.
- Service 0.2.0-dev.5 adds migration 005, exact membership revisions, immutable audit and transactional team receipts; backup-excluded Android intents contain no password/verifier.
- Service20unit/72Node, Android12JVM/3team-store/1teamHTTP/1teamUI passed with independent database proof and eight inspected screenshots. [Evidence and limits](docs/NATIVE_TEAM_R1.md).

## Unreleased - outlet settings R1, 2026-10-23

- Added role-based native outlet settings, review/validation/leave guards and unsaved form retention through Activity recreation.
- Service 0.2.0-dev.4 adds migration 004 and exact-revision settings/audit/receipt transactions. Android persists backup-excluded intents; account switch/logout includes pending settings.
- Service20unit/64Node integration, Android12JVM/3storage/1settingsHTTP/1settings UI and7HTTP/1UI regression passed. Five screenshots inspected. [Evidence and limits](docs/NATIVE_SETTINGS_R1.md).

## Unreleased - native history and reports R1, 2026-10-22

- Added paginated server history, attributed audit/reopen reasons and immutable report generations in native UI, following the approved visual direction.
- Added strict client decoding, scoped request invalidation and refreshing detail on reentry, including inline reauthentication after expiry.
- 12 JVM, one history HTTP scenario, one native history/auth scenario and original draft/conflict native regression passed. Seven screenshots inspected; PostgreSQL proves historical 4/current 6 and no read-generated mutations. [Evidence and remaining gates](docs/NATIVE_HISTORY_R1.md).

## Unreleased ? Android process recovery R1, 2026-10-22

- Added opt-in QA peer debug identity and multi-process Room/repository/session instrumentation. Default/release app identity is preserved.
- Verified five real Android force-stops, HTTP outage, retries after committed quantity/submit replies are held, and conflict reconciliation between separately installed package UIDs. Independent PostgreSQL checks confirm no duplicate accepted mutations.
- Fixture-only fault boundaries do not change production service handlers. This is integration proof, not native restoration UI or physical-device acceptance; see [scope and evidence](docs/PROCESS_RECOVERY_R1.md).

## Unreleased ? native supervisor lifecycle R1, 2026-10-22

- Connected Review tab, role-scoped submit/return/lock/reopen and reason confirmations following approved R2.
- Added additive schema 8 durable lifecycle operations, immutable retries, exact reviewed revisions and atomic receipt/snapshot application.
- 12 JVM, 26 storage, 10 real-service HTTP and two native UI scenarios passed; six screenshots inspected; lint 0 errors/16 warnings. See [checkpoint](docs/NATIVE_LIFECYCLE_R1.md) for limits.

## Unreleased — CCS-06 native draft slice R1, 2026-10-22

- Applied approved R2 direction/logo in connected Compose login, outlet/date, summary, draft, conflict, cached-history and account screens, accessible from legacy Home.
- Added atomic multi-menu saving, unsaved navigation confirmation, scoped foreground retry/status and online-screen disposal cancellation. Reapply now requires review again if the server base changed after the dialog was reviewed.
-12JVM+19Room+7HTTP+1real native UI scenario pass; PostgreSQL attribution/receipts checked and five actual screenshots reviewed. Added batch rollback and second-change-after-review tests. Supervisor/full-history/admin/process-kill/two-installation/physical/public acceptance remain open; APK metadata stays0.1.1.

## Unreleased — CCS-05 HTTP/session foundation, 2026-10-22

- Added canonical HTTPS transport, debug-only loopback HTTP, bounded JSON and closing/receipt identity checks against the existing service.
- Added Keystore AES-GCM session persistence, pending-account/logout confirmation and scoped queue sync/retry/error handling. Session preferences and databases are excluded from automatic transfer to prevent token/queue replay; manual legacy backup remains available, online migration/export remains open.
-12JVM,18Room/legacy and6real-service HTTP scenarios pass. Independent PostgreSQL proof confirms receipt/audit attribution and duplicate retry behavior. No native connected UI, two-installation/process-kill or physical/public acceptance claimed; APK metadata remains0.1.1.

## Unreleased — CCS-05 Room foundation, 2026-10-22

- Added non-destructive schema6→7 online namespace and scoped durable operations with transactional edit/ack/pull/conflict/auth recovery.
- Added14 real Room instrumentation tests;18 including legacy backup/menu/closing regressions pass on the owned emulator. Fixture context/index parsing issues were corrected before acceptance.
- Preserved Android version metadata0.1.1 and existing offline UI. This is a source foundation checkpoint, not connected online feature availability, a release or phone UAT.
- Recorded approved FINORA-inspired visual R2 separately; Compose application remains next.

## 0.1.1 - 2026-08-20

Visual identity update without changes to the closing workflow or stored data.

### Changed

- Replaced the coffee-brown color scheme with an EXVE-inspired blue palette for light and dark themes.
- Added an About dialog accessible from the information icon in the top app bar.
- Added automatic app version information, Android APK/offline mode, and developer attribution.
- Added the EXVE Labs color logo and copyright attribution.
- Finalized the visible application name as Closing Count System.

## 0.1.0 - 2026-08-18

First stable MVP release.

### Included

- Offline ingredient categories and ingredient management.
- Menu categories, menus, and ingredient recipes.
- One editable daily closing per date.
- Automatic Terjual/Out calculation grouped by ingredient category.
- Closing history with stable menu and recipe snapshots.
- Excel export with ingredient summary and menu sales sheets.
- Full JSON backup and transactional restore.
- Validation, empty states, confirmations, and unsaved-closing protection.
- Dedicated release signing and verified in-place Android update path.

### Platform

- Android 8.0 or newer (`minSdk 26`).
- Portrait-first Android interface.
- Fully offline; no account or internet connection required.
