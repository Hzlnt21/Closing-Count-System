# CCS-05 HTTP/session foundation R1

22 October 2026. Source checkpoint only: the existing Android screens still expose the offline workflow. App metadata remains0.1.1, service0.2.0-dev.3. This is not the finished connected Android candidate.

## Implemented

- Strict canonical service origins, HTTPS by default, no credential forwarding on redirects. Explicit debug opt-in permits only localhost/127.0.0.1/10.0.2.2 HTTP; debug network config allows these hosts only. Network permission added. No global cleartext permission or release endpoint introduced.
- Login/me/logout, create/fetch closing and immutable quantity-operation transport against the existing service. Responses are bounded and identity/outlet/closing/menu/revision/quantity fields validated before Room writes. Tokens/passwords are not logged.
- Android Keystore AES-GCM session storage with redacted session representation; no password persistence. Session preferences excluded from cloud/device backup. Database automatic device transfer is now excluded too, preventing queue replay on another installation; existing manual legacy JSON backup/restore remains available. Online pending-draft export/restore remains CCS-07 work, so do not treat automatic transfer as a supported migration path.
- Repository prepares login, requires confirmation before switching away from pending work/logout, retains old scoped drafts, and resumes authenticated queues only for the same account. No network call starts at app startup. UI initialization/login/sync integration is still pending.
- One sync engine per origin serializes manual/foreground callers, preserves SENT operations on network/5xx ambiguity, advances exact acknowledgements, pauses401/403, blocks409/validation, refreshes before deliberate conflict resolution and checks account/token changes around requests. Retry returns bounded2–60second backoff to its caller; scheduling/reconnect UI is not implemented yet. No automatic infinite retry of conflicts.

## Verified

12 JVM tests (including3 endpoint-boundary cases), debug/test APK builds,18 Room/legacy instrumentation regressions and6 HTTP instrumentation scenarios passed. HTTP tests used the production service handlers and real isolated PostgreSQL, two accounts and separate Room databases **inside one QA app installation**. They are not two-installation, UI, physical-phone or public-deployment proof.

Final lint/build passed with0errors/16warnings:14previous warnings and2KTX-style suggestions for intentional checked synchronous preference commits. Initial lint caught missing explicit `includeSubdomains` attributes; these were set tofalse, then lint and APK builds repeated successfully. The HTTP/Room APK hashes identify the native test inputs before that XML clarification; this checkpoint does not distribute a final demo APK. No suppression or dependency upgrade was used.

The six scenarios cover real same-menu409 followed by deliberate reapply/attribution, injected lost response after a real commit followed by database reopen/exact retry, revoked session/relogin, account switch during response, Keystore store reopen without plaintext token/password and repository pending-account/logout confirmation. The lost response is injected after the HTTP operation succeeds, not a physical network outage or Android process kill.

`http-evidence-r1/database-after.json` independently checks5closing dates:6accepted mutations =6receipts =6audit changes. The lost-response case remains one mutation; the colleague owns the accepted conflict resolution; pending18 never replaces accepted17 after logout. Fixtures are retained, not reset. No operational database or physical phone used.

First host-renderer boot was stopped when available virtual memory dropped too far. The actual test run used software renderer1536MB after Gradle stopped. It passed native database/network tests; this is not UI performance acceptance. Emulator and fixture listener were stopped after tests. The owned database container remains available for later QA.

## Reproduce with owned QA resources

Build `testDebugUnitTest assembleDebug assembleDebugAndroidTest`. `service/test/android-http-fixture.mjs` requires the existing explicit QA environment/ownership marker and absolute `CCS_HTTP_EVIDENCE_DIR` / `CCS_HTTP_FIXTURE_FILE` output paths. It creates fictional accounts/catalog on loopback4371 and writes only dummy credentials to the fixture file. The injected advancing login-limiter clock is for test isolation, not rate-limit acceptance; production limiter behavior is unchanged. `--smoke` checks bootstrap/shutdown; writing `<fixture-file>.stop` shuts down a running fixture. No production control routes are added.

Run `OnlineHttpInstrumentedTest` with `ccsHttpQa=true`, `ccsQaOrigin=http://10.0.2.2:4371`, outlet/employee/colleague/password from the generated dummy fixture. Never point instrumentation at operational resources. Without explicit opt-in these tests are assumed/skipped, not claimed as passed HTTP acceptance. Existing18 regressions can run with `notClass=...OnlineHttpInstrumentedTest`. Evidence and owned-device driver live in the program workspace; summarized logs/DB proof are tracked here.

## Still open

Actual process-kill/in-flight recovery, two separate Android installations/accounts, offline/reconnect scheduling and user-visible status, lifecycle operation durability/guards, native R2 login/outlet/input/conflict/review/history/settings screens, legacy/online backup/import/export and demo packaging. Physical UAT/public checks remain later gates. A SYNCED result only describes this local queue at that point, never knowledge of another offline device's drafts.
