# Physical Android USB demo acceptance R1

Latest follow-up: [physical DocumentsUI files R1](DEMO_PHYSICAL_FILES_R1.md), 17 November2026:21 scoped mixed UI/oracle checks on the same code10. Current retained draft is6/6; frozen generation1 remains4/6. Earlier results below keep their original date/scope and are not fresh reruns. Remaining provider/restore and hardware gates are listed in the follow-up.


17 November 2026. Android 0.1.2/code10 DEMO, service 0.2.0-dev.8, one authorized
M2101K7BNY phone on Android13/SDK33, primary user0. This adds an explicit USB
transport for the debug demo and a separate physical test driver. Business code,
Room schema, API, dependencies and default emulator transport are unchanged.
The earlier emulator journeys retain their original scope.

## USB candidate

Build from the source checkpoint with Android SDK/JDK configured:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest "-PclosingLocalDemo=true" "-PclosingLocalDemoTransport=usb"
```

The opt-in USB build uses `http://127.0.0.1:4372`, application ID
`com.closingcount.app.demo`, label Closing Count Demo, version0.1.2-demo-local.
The default remains `emulator`/10.0.2.2. Unknown transports, USB without DEMO,
DEMO+QApeer and DEMO release tasks are refused; all four refusal checks passed.
Only the existing debug loopback cleartext policy is used. Release settings are
unchanged. This is a debug QA artifact, not a signed public release.

Run the normal DEMO runtime with a separately owned private state directory and
keep its credentials outside the repository. Use an explicitly authorized device:

```text
adb -s <authorized-device> reverse tcp:4372 tcp:4372
adb -s <authorized-device> install <USB-demo-apk>
```

Do not replace or clear an existing installation merely to repeat this journey.
The test requires a fresh demo installation and a fresh marked runtime at its
first phase; subsequent phases require its exact saved run marker. Host credentials
and device serial are private instrumentation arguments, never public logs.
The host stops/starts the owned runtime between phases without reset/reseed.
Business mutations use actual MainActivity Compose controls; API/Room/SQL reads
are verification oracles. The driver refuses emulator endpoint, wrong model,
package/user and absent authorization reference.

## Observed result

All ten native checks passed in three invocations on the same app and fixture:

| Phase | Verified behavior |
| --- | --- |
| Prime, 2 checks | Real phone displays seeded3/5; native save changes Cappuccino to4 on the actual server. |
| Offline, 2 checks | With API/database stopped, native save retains Latte6 pending; Activity recreation retains the unsent quantity. |
| Resume, 6 checks | New app process/server restart syncs the pending original; employee submits without lock authority; supervisor locks; explicit reopen keeps generation1 report exact; employee edits Cappuccino5 while frozen4/6 stays exact; logout leaves no pending work. |

Independent database comparison passed13 checks: baseline identities, catalogs,
previous-day closing/entries, all prior reports/audits/receipts retained exactly;
six unique new native operations produced exactly six receipts and six audits.
Final current closing is DRAFT/generation2 with5/6; the new frozen generation1
report is4/6 and matches the actual API proof. Legacy local Room snapshot hash is
unchanged through all three phases. Four physical screenshots were visually
reviewed: saved draft, offline pending, locked review and reopened draft.

The USB app APK SHA256 is
`8aa46e927491c5424756a73dc45e5fd9a5748bb90d2145e47d6ff4f0d9f6e8fe`
(20,681,213bytes).27 JVM tests passed, four build guards passed, actual manifest
identity/debug status and loopback endpoint in DEX checked. Instrumentation APK
compiled successfully. No new lint claim or server-suite rerun for this change.
The original qualified emulator ZIP remains20,623,722bytes/SHA256
`c1c53335be575401afb08a81b531b2e8cf701d0770e444fc57afc7ae91da6fac`.
It was not rebuilt, replaced or relabelled physical-qualified.

[Curated machine proof](demo-physical-usb-r1/independent-oracle.json),
[native resume](demo-physical-usb-r1/resume-proof.json),
[build checks](demo-physical-usb-r1/build-validation.json).
Full run evidence is in the program's
`evidence/closing-phone-uat-20261117/045323` directory, with source hashes,
original failures, actual screenshots and source-bound host drivers.

The first instrumentation build failed because PowerShell split an unquoted
Gradle property; quoted retry passed. Initial Android install required its normal
MIUI confirmation; retry installed successfully. First prime collection misread
column-formatted `ls`; corrected to `ls -1` and recollected the original passing
proof. The two successful prime business actions were not repeated.

## Preserved state and remaining acceptance

Owned runtime stopped and only newly created reverse4372 removed. The app/test
installation, QA Room data, runtime volume, fixture, historical IDs and six new
operations remain. Phone returned Home; other QA apps were preserved. No personal
data, operational DB, external recipient, Android user creation or device reset.

This covers one physical phone over USB and real service loss/restart. Physical
Wi-Fi/LAN interruption, two-device concurrent conflicts, power/reboot scenarios,
DocumentsUI Excel/JSON exports and restore/pending transfer, demo reset with old
drafts on this phone, broad font/rotation/accessibility and preserving upgrades
remain pending. Prior emulator acceptance is separate evidence, not a substitute.
Public hosting/HTTPS/distribution, final portfolio/CV and user review remain open.
