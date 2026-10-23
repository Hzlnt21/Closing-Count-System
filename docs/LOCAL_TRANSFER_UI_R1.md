# Local backup review and native document flow R1

23 October 2026. The local home now explains the boundary between on-device history and shared server closings. A chosen version1 file is read with a20MiB cap, decoded and fingerprinted on Dispatchers.IO before a review appears. The review shows its filename, menu/ingredient/history counts, date range and replacement scope. Cancelling changes no local data.

The controller privately retains the reviewed snapshot behind a one-use identity and mutex. Restore uses that snapshot even if the source provider changes later. Stale identities, cancellation and duplicate submissions cannot apply another payload. Activity recreation retains the review through the ViewModel; it is not placed in the Activity saved-state bundle, persisted for automatic replay or uploaded. A full process restart requires choosing the file again. Real process-kill behavior remains separate from the Activity recreation test.

Backup creation, provider reads, validation and restore run away from the UI thread. The UI displays processing and explicit success/error state. Large text or short windows use a labelled destination selector; all five local destinations remain available. Material container roles now use the existing blue palette instead of falling back to purple defaults.

## Verified on the same final APK pair

- Four Android controller tests use actual MediaStore content URIs and isolated in-memory Room databases: reviewed content survives a provider rewrite; concurrent duplicate restore succeeds only once; cancellation/stale reviews and invalid replacement inspections preserve data; written backups round-trip.
- Ten legacy codec/SQLite regression tests pass, including coercion guards, historical compatibility, transaction rollback and online outbox isolation.
- Two complete native journeys pass on the owned phone emulator at360x800dp: light100% and dark200%. Each shows all five destinations, navigates History/Home, opens and cancels the real Android DocumentsUI picker, selects a corrupt file, cancels a valid review, recreates the Activity, modifies the source provider after review, restores the reviewed content, and creates a new backup through the real system save dialog.
- Both exported JSON artifacts are independently checked against the full expected dummy snapshot, not just file existence. Original app-local data is restored after each test; four online-table digests remain unchanged. Emulator size/density/font/night settings are restored.
- Four selected static-text scene checks (home/review in both configurations) have positive layout counts and no unexpected painted-line clipping. Explicit title/filename ellipses and legitimate scroll viewports are distinguished. The review explanation can be scrolled to its end. The detector and its deliberate clipping controls were established in layoutR1.
-19JVM tests/build pass. Lint remains0errors/17warnings, including the previously recorded online window-size recommendation. Service, schemas and dependencies remain unchanged.

[Review gallery](local-transfer-review-r1.html), [runtime proof, logs, two JSON outputs,12screenshots and host harness](local-transfer-evidence-r1/). Both final contact sheets were inspected; images are not all inspected individually at full resolution. System-picker images capture its opening frame, before the file list finishes loading.

The first APK passed the controller/legacy/light journey. The dark journey initially targeted DocumentsUI's preview affordance in its grid view; the test now clicks the filename. Its screenshot also exposed genuinely clipped old navigation labels at200%, fixed with compact navigation. Superseded/failed evidence is retained separately and does not count as final passing evidence.

## Remaining scope

This verifies the emulator's local DocumentsUI/MediaStore provider. Physical/cloud-provider behavior, process death during transfer, all legacy screens/accessibility permutations and public distribution remain separate. No physical phone, operational data or server transfer was used. Android stays0.1.1/versionCode9, Room8 and service0.2.0-dev.5.

Online frozen report export and authorized import safety remain open under ONLINE_CONTRACT. CCS-07 is still in progress, then CCS-08 complete dummy demo packaging and the other projects, joint phone UAT, reviewed infrastructure, public demos and final portfolio/CV review.
