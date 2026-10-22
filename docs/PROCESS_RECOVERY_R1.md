# Android process recovery and independent installations R1

22 October 2026. This checkpoint verifies repository/Room/session integration in actual Android processes against the real local service and owned PostgreSQL. It does not add new user screens. The host explicitly targets PortfolioQA_Phone_API36 / emulator-5580; no physical phone or public service was used.

## Verified sequence

1. Employee Rani stores quantity 7 in a durable queue; the host captures its PID and force-stops Android before sending.
2. A new process loads the encrypted session and the same queued operation. An actual HTTP 503 outage leaves quantity 7 queued. This models service unavailability, not radio or OS network transitions.
3. The fixture holds the successful HTTP response after the real quantity transaction. The host force-stops the Android process. A new process finds SENT and retries the original operation ID; server revision stays 1.
4. A second installed debug package, with a different Android UID, session and Room files, logs in as Dimas, sees 7 and queues 9. Its process is force-stopped. The first installation writes 11. The second installation restarts, preserves local 9 and reports conflict against server 11; an explicit use-server resolution retains 11.
5. Rani queues submit and is force-stopped. A new process sends that original action while the fixture holds the committed reply; it too is force-stopped. The final process loads SENT and retries the same ID, leaving one SUBMITTED closing at revision 3 and quantity 11.

Default and peer debug/test APK builds and 12 JVM tests passed. The default application build was restored afterward; final lint passed with 0 errors and 16 warnings. Ten coordinated phases completed: five intentionally interrupted processes and five normally passing instrumentation phases. Each deliberate force-stop records a live PID before and no PID after. The test retains its owned QA state. [Evidence](process-evidence-r1/) includes logs, distinct package UIDs, APK SHA-256 values, host events and independent database proof. The database confirms exactly three accepted receipts/audits: quantity 7, quantity 11 and submit. No receipt was accepted for the conflicting 9; both lost-response retries have exactly one receipt.

The first host attempt misread a Windows process ExitCode despite a successful offline test. The runner now caches its process handle and waits for exit; it resumed only after revalidating the seed boundary. The original offline log remains in central evidence. The final integration and database proofs passed.

## QA boundaries

`-PclosingQaPeer=true` creates debug application ID `com.closingcount.app.qapeer`; default application ID stays `com.closingcount.app`. Release tasks reject that QA property and the release identity never changes. Namespace and production source are shared; this is two independent installations on one emulator, not two physical devices.

`service/test/android-http-fixture.mjs` has an opt-in `CCS_HTTP_RECOVERY_GATE=true` response hold and HTTP-outage boundary. These affect the fixture wrapper only; production handlers, SQL transactions and idempotency are unchanged. An advancing fixture login-limiter clock remains injected, so this is not a rate-limit test.

The test uses its own persistent Room database inside each application process and production repository/session components. It does not yet prove restored state through the native screens, automatic background work, arbitrary low-memory termination, radio handover or two physical devices. Keep those UI/device acceptance items separate. Full server history/report UI, admin/settings, transfer and complete local demo remain open. App metadata remains 0.1.1/versionCode 9; this is a development source checkpoint.
