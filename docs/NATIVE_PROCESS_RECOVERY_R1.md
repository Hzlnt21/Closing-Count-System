# Native process recovery R1

23 October 2026. Real native screens now have acceptance evidence across six deliberate Android force-stops. The owned host script confirms a live PID, stops that process, verifies it is gone and starts a new native instrumentation phase. The application uses its actual application-owned Room database, encrypted session and AtomicFile stores throughout.

## Verified boundaries

| Interrupted workflow | Recovery through native UI | Independent result |
| --- | --- | --- |
| Quantity saved, successful committed reply held | Encrypted employee session restores; input shows 5 and synced state | One quantity receipt, entry revision1 |
| Submit committed, reply held | Review restores submitted state and clears lifecycle intent | One submit receipt, closing revision2 |
| Outlet settings committed, reply held | Administrator session restores; settings retry confirms the existing operation | One settings receipt/audit, name Recovery Outlet and cutoff03:00 |
| Team account creation committed, reply held | Team retry finds the existing receipt without typing a password again | One account and one creation audit/receipt |
| Valid catalog draft saved locally | Reopened catalog shows Recovery Menu and the same EDITING operation ID | No catalog publication yet at this boundary |
| That catalog publication committed, reply held | Frozen same-ID retry confirms version2 and clears the intent | One publication receipt and one new immutable version |

The response-hold mechanism is opt-in inside the dedicated QA HTTP fixture. It waits until a successful response would be sent, after the real PostgreSQL transaction committed. The host then force-stops the client. This combines a deliberately controlled transport boundary with a real process termination; it is not a physical radio outage.

Eleven phases were verified: six instrumentation runs deliberately terminated by the host and five successful continuation runs. The deliberately terminated logs contain `Process crashed`; they are not counted as normal passing JUnit cases. PID/proof/held-response records distinguish them from unexpected crashes. [Six actual recovery screenshots](native-recovery-review-r1.html), [phase logs, APK hashes and database proof](native-recovery-evidence-r1/).

PostgreSQL independently confirms exactly five distinct operation receipts with their original actor and operation IDs, two closing audit changes, one settings audit, one team creation audit and two catalog versions including the fixture seed. The old submitted closing retains cutoff00:00 and its original catalog; a separately created April1 closing captures cutoff03:00 and Recovery Menu.

## Harness finalization and provenance

All eleven runtime phases completed before a host summary bookkeeping error. The initial APK fingerprint pipeline also needed explicit LiteralPath handling. The host's finalizer was corrected, including JSON-array enumeration and valid installed APK paths. Read-only FinalizeOnly verified all continuation logs and stop records, then independently matched SHA256 of both installed package APKs against the build files. Database proof was generated separately. Runtime phases were retained, not replayed or relabelled after the bookkeeping correction.

Android production source, JVM tests, dependencies, SQL and service implementation remain unchanged from native catalog R1. The fixture adds hold selectors for settings/team/catalog only under its existing explicit recovery gate. Source compilation, 19 JVM tests and debug/test APK build passed. Final lint passed with 0 errors and 16 warnings. The installed product APK is byte-identical to the native catalog checkpoint. Existing catalog/service results remain inherited for that preserved implementation; they are not claimed as new runs.

## Remaining scope

This verifies the six named saved-work boundaries on one owned phone emulator. Unsaved form text, OS reboot/power loss, two native devices, physical-phone UAT, all conflict/revocation permutations, tablet/dark/font-scale and responsive checks remain separate from this evidence. The earlier two-package repository recovery remains a distinct checkpoint. Transfer/legacy safety and complete local demo packaging remain open, followed by the full eight-project program's joint phone UAT, reviewed infrastructure, public demos and final portfolio/CV review. Room stays schema8, Android0.1.1/versionCode9 and service0.2.0-dev.5.
