# Pending backup and recovery native UI foundation R1

Implements the approved R2 visual language in Account > Cadangan & pemulihan. Export captures this outlet's pending work into a frozen private ticket before the real Android CreateDocument picker. Import freezes and validates selected bytes, previews source/account/outlet/date/counts, and accepts only an inert inbox copy. Per-target server comparison is separate from import and application.

The inbox shows valid, unreadable and incomplete copies separately. Explicit one-use removal deletes only the selected app-private AtomicFile and its artifacts. It does not remove the source provider document, Room drafts, provenance claims or server data. Removal's change guard compares filename/length/modified metadata, not cryptographic content equality. Corrupt payloads are never silently repaired.

Quantity review shows the file's prior server number, current server number and desired total, with generation/status warnings. Administrative review shows current role/base/state and readable values; catalog details include categories, ingredients, menus and recipes. Recovered account creation still asks for a password only in the normal team workflow. No password or session is accepted from a file.

Applying a proposal makes an ordinary local draft with new IDs and atomic provenance. The normal closing ViewModel now uses a separate automatic-sync entry point: outstanding recovered operation/lifecycle claims hold automatic foreground/bind sync. Only an explicit normal sync command sends them. Administrative operations remain in their existing explicit review/sync or edit/publish pages.

Private tickets and the active preview survive Activity recreation in an AndroidViewModel. Process death intentionally drops private authority: accepted inbox files persist and require fresh comparison. Tests must distinguish Activity recreation from actual process restart. A stale/unsolicited picker callback cannot write to an unselected URI, and local account changes are rechecked before opening output streams.

## Acceptance status

PT-07 and PT-08 remain OPEN until native runtime, configuration and restart evidence is recorded. New tests cover per-file inventory/removal, frozen provider bytes, cancellation/duplicate callbacks, account switch, and an owned production-MainActivity/DocumentsUI journey. Existing quantity and lifecycle HTTP tests additionally assert the automatic-sync hold and explicit sync success. Compiling these tests alone is not runtime acceptance.

No physical device, external document/cloud provider or public deployment is claimed. Physical UAT remains the final PF-03 batch. No new library, schema change or service change is introduced by this UI work; Room stays10, service0.2.0-dev.6, app0.1.1/code9. Safe pushed source remains b13635d/ccs-intent-recovery-r1 until a new tested checkpoint is committed and remote verified.

## Verified initial checkpoint

Final application/test build succeeded with25 JVM tests and lint0errors/17warnings.95 Android cases passed:88 PT01-06 regressions (now including automatic-sync hold),4 inbox management and3 isolated ViewModel cases. Three cross-installation invocations passed on primary/secondary Android users of one emulator. A separate production MainActivity/real DocumentsUI light journey passed on July2: cancel/open, export quantity4, reject invalid input, retain frozen preview across Activity recreation/provider rewrite, accept original bytes, compare0/4/8, create a draft, hold4 across Activity recreation and explicitly sync8. Existing online/legacy data and device users/settings were restored. Independently queried PostgreSQL audit confirms4then8 by the owned actor; no internal server errors. The same APK's admin regressions retain14receipts and expected settings/membership/catalog/lifecycle audits.

The first native attempt failed and its failure screenshot handler masked the original exception; ADB also became transiently offline during cleanup. Its proof confirms database restoration. The handler now retries a missing screenshot and preserves the original failure; scrolling to the sync control is explicit. Only that journey was repeated with a separate date on the same QA fixture. Native test APK changed; application APK and95-case production implementation remained identical. Raw failed logs are retained in global evidence, not counted as success. Build and final native evidence are supplied separately.

Visually inspected light screenshots: inbox, export review and quantity review. Readable hierarchy and contrast observed. Secondary actions currently sit immediately under primary buttons; add breathing space during the remaining native visual pass. Dark/large-text, native administrative continuations, actual process restart and complete PT07/PT08 acceptance remain OPEN. Accepted inbox core process persistence from earlier tests does not replace native process-restart acceptance.
