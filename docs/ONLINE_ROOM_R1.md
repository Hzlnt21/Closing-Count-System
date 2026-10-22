# CCS-05 Room foundation R1

22 October 2026. This checkpoint adds storage primitives, not a connected online Android release. App version metadata remains0.1.1; service stays0.2.0-dev.3. Use the source tag `ccs-room-r1` to identify this database foundation separately from APK feature availability.

## Implemented boundary

- Additive schema6→7 migration creates online closing headers, per-menu server/local quantities and ordered immutable operations. Legacy tables/IDs remain separate, without cross-namespace foreign keys or automatic historical upload.
- Scope partitions by canonical service origin, account UUID and outlet UUID; no session token is stored in the namespace. Callers must authenticate and construct the correct scope; a DAO string is not authorization.
- Local edits and queue writes are transactional. A later edit waits for its predecessor's acknowledgement before receiving an expected revision. SENT retry preserves the same operation ID/payload/base across database close/reopen.
- Pulls preserve dirty quantities and detect changed remote bases/generations. Stale pull/receipt cannot regress newer server state; explicit conflict resolution discards or reapplies with a new operation ID.
- Acknowledgement removes its operation, advances acknowledged base and preserves later local edits in one transaction. Duplicate callbacks do not delete successors. SQL failures roll back the queue and quantities together.
- Auth failures pause scoped operations until login to that same scope. Validation failures require explicit recovery. Amount boundary remains integer0–9999 per menu.

## Evidence

`room-evidence-r1/verification.json` and `instrumentation.log`:18 emulator instrumentation tests passed (14 new queue/migration cases +4 existing backup/menu/closing tests). Real schema6 was created from the tracked fixture, legacy metadata/closing/menu rows inserted, and Room migrated it to7 without importing history. Room validated the schema. Database close/reopen demonstrates persistence but is not an Android process-kill/network test.

Nine JVM unit tests passed; debug APK and AndroidTest APK built. Test fixture setup initially read the wrong context and assumed an index array for every table; both fixture issues were corrected, failures retained in the program workspace, and full instrumentation rerun passed. See program evidence `closing-count-visual-r2` (build logs) and `closing-count-room-r1/r2/r3` (test attempts).

`lintDebug` passed with0errors and14existing warnings (dependency/API/locale/vector/obsolete SDK suggestions). No warning suppression or bulk dependency upgrade was used. The tracked lint text provides the actual warning inventory.

Reproduce with the existing Android SDK/JDK and a disposable emulator: `./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest`, then install app/test APKs and run `am instrument -w com.closingcount.app.test/androidx.test.runner.AndroidJUnitRunner`. Use only owned dummy devices; this checkpoint was run against `PortfolioQA_Phone_API36`, serial `emulator-5580`.

## Remaining acceptance

HTTP client, session storage, sync scheduling/reconnect, lifecycle submit/review guards against durable queue, process-kill while requests are in flight, actual Android+PostgreSQL two-account flows, native R2 screens, online backup/import, public demo and physical UAT remain open. Restore/import behavior for online queues must be designed before exposing it through legacy backup flows. No claim that local cache entries or simulated receipts constitute server sync. The emulator was shut down after testing; no physical phone was accessed.
