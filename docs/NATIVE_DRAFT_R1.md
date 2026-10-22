# CCS-06 native draft slice R1

22 October 2026. Enter from **Home → Closing bersama**. This checkpoint applies the approved R2 direction to real Compose screens and the existing HTTP/Room repository. It is a partial connected candidate, not completion of CCS-05/06 or a public/physical release. Android metadata remains0.1.1 and service0.2.0-dev.3; source tag identifies the checkpoint.

## Working boundary

- Native login/password visibility, outlet/business-date selection, blue summary cards, menu quantity input, per-device sync status, cached device history and account/logout controls. The approved CC logo is rendered as an Android gradient vector. Default debug endpoint is owned emulator loopback; `closingApiOrigin` configures the origin at build time. Release has no default endpoint and never inherits emulator HTTP.
- Frozen catalog and attributed quantities come from the real service; no mock values or role switching in these screens. History currently means closings cached on this device, clearly labelled; complete server history/reports/admin are not implemented here.
- Numeric0–9999 validation, explicit save and transactional multi-menu edit. Unsaved text is not a saved draft: navigation/date/outlet/account changes ask before discarding it. Saved drafts remain in Room; uncommitted typing is not promised across process termination.
- Manual sync and bounded foreground retry (up to4attempts per run) with cancellation on activity pause or leaving the online screen. SYNCED is shown only after an actual server check for this device; it does not assert knowledge of other devices' offline drafts. No promise of immediate background delivery.
- Explicit conflict choices, actor/current-server/local quantities, no initial selection, and disabled confirmation until chosen. Reapplying compares the reviewed entry revision/generation against a fresh response; another server change asks for review again instead of silently rebasing an unseen overwrite. Use-server deliberately discards the local draft.
- Returning to legacy device screens preserves the existing menu/ingredient/history/manual-backup workflow. Supervisor submit/return/lock/reopen and online import/export are still separate acceptance work.

## Verification

12JVM tests/builds pass.19Room/legacy tests pass (15queue/migration/batch +4legacy).7HTTP scenarios pass, including the new second-change-after-review case. One real native UI scenario passes login → invalid10000 rejected → unsaved navigation cancelled → save8 → server colleague12 → local9 conflict → explicit use-server12 → logout → return to legacy Home. Five actual screenshots are in `native-evidence-r1/`; these are not browser mockups.

Final lint passes with0errors/16existing warnings; no suppressions or dependency upgrades. The final APK/test APK hashes are tracked with the native screenshots and logs. UI/business Kotlin did not change after the final build and test runs.

PostgreSQL proof across7closing dates:11accepted mutations/receipts/audit records; native case has exactly8 and12, with no accepted9. The old lost-response, auth/partition and pending18-versus-server17 cases still hold. Two accounts are tested within one installation plus separate test Room databases; two independent Android installations/process-kill/physical UAT remain unproved.

Software renderer caused a System UI ANR and keyboard focus failure; that failed run is retained in program evidence. Host renderer1536MB passed. A screenshot captured Android's dialog transition before it became fully opaque; captures were repeated on fresh retained dummy fixtures with owned-emulator window animations disabled. This changes capture conditions, not the app's production animation settings. Final screenshots were visually reviewed; no ANR overlay remains. No phone or operational DB was used. Emulator and fixture listener are stopped after verification; owned PostgreSQL remains available.

## Remaining

Supervisor lifecycle operation durability/guards/UI, complete server history/reports/admin/settings, process-kill/in-flight and true offline/reconnect tests, two installations, tablet/landscape/dark/font-scale/large-catalog/accessibility acceptance, online backup/import/export, demo package/video and physical/public acceptance. Dark palette exists but light portrait is the screenshot-verified direction here. No task completion is inferred from simply having screens or a green build.
