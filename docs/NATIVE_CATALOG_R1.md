# Native catalog and recipe editor R1

23 October 2026. The account screen opens a native catalog panel in the approved blue/white R2 direction. Members have read access; administrators create and edit ingredient/menu categories, ingredients, menus, active status, display order and ingredient selections. Referenced categories/ingredients cannot be deleted; an active menu cannot retain an inactive ingredient. Recipes keep the existing one-use-per-ingredient formula.

## Draft and publication boundary

Starting a draft validates current administrator access and the reviewed server version. Each valid row save commits the whole edited catalog locally before closing its form. The editor uses one backup-excluded AtomicFile record per account/outlet for both EDITING drafts and immutable publication operations. Closing/reopening the panel restores that draft. Unsubmitted row fields use small saveable values; the whole catalog is not placed in Activity saved state. Leaving a changed row form requires confirmation.

Publishing reviews the catalog counts and base revision, then freezes the existing draft ID/payload to QUEUED before networking. A frozen operation cannot return to EDITING. Retry uses the original ID; uncertain/auth-paused operations cannot be discarded. A stale editable draft is retained without automatic rebasing. Users explicitly discard it to load the latest server catalog before starting a replacement. A confirmed publication removes the one record, avoiding a separate stale draft surviving acknowledgement.

New closings snapshot the newly published catalog. Existing closings and immutable reports retain their previous names/categories/recipes. No grams, millilitres, stock units, HPP, payments or POS integration are introduced. Service0.2.0-dev.5/SQL/dependencies remain unchanged; Room remains schema8 and Android metadata remains0.1.1/versionCode9.

## Verification scope

- Source compilation, 19 JVM tests and final debug/test APK build passed.
- Four emulator storage cases passed, including mutable EDITING data, frozen-state immutability, exact decoding, partitioning and backup recovery.
- One real HTTP draft scenario passed: draft persistence/reopen, logout guard, stale review without rebasing, explicit replacement, same-ID freeze and receipt recovery after a real commit.
- One native scenario passed: employee read-only access, adding both category types and an ingredient/menu/recipe, deleting an unused category, retained form through Activity recreation, edited ordering, restored draft after panel close/reopen, reference/deactivation guards, unsaved-row leave confirmation and reviewed publication.
- Independent PostgreSQL proof confirms three versions/two receipts for the draft fixture, and two versions/one receipt for the native fixture. The latter contains three menus/four ingredients; the unused category is absent. May 1 retains its original two-menu catalog while May 2 snapshots the three-menu publication.
- The existing catalog HTTP recovery scenario, seven HTTP/session cases, original native draft/conflict scenario, settings HTTP and team HTTP regression all passed. Eight runtime groups used identical final app/test APK hashes. The HTTP fixture independently retains six versions/five receipts and its frozen June closings.
- Seven actual phone/light screenshots were inspected; [gallery](catalog-review-r1.html), [logs, hashes and database proof](catalog-native-evidence-r1/). Cards were made white for consistency with approved R2, reference errors now name the dependent records, and the restored-form capture waits until loading completes.

Final lint passed with 0 errors and 16 warnings.

The first storage test found that an unchanged queued payload could return to EDITING and then become mutable. The store now refuses that transition; the failed run remains in central evidence. An initial Compose list brace and an accidental text-encoding conversion were corrected before the final QA build. These corrections do not constitute public or physical-device acceptance.

Activity recreation and repository/AtomicFile reconstruction are separate from OS process-kill, radio outage and physical-phone UAT. Tablet, dark-mode, font-scale, full native process recovery, transfer/legacy safety and complete demo packaging remain open. The eight-project goal continues through joint phone UAT, reviewed infrastructure, public demos and final portfolio/CV review.
