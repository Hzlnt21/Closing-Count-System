# Native history, audit and frozen reports R1

22 October 2026. The History tab now loads authorized server dates, including dates never opened on this installation. Server pages and device-cached closings have separate labels. Users can open the closing, inspect attributed changes and reasons, or view a locked generation with its original menu/recipe totals and reviewer. Returning to a detail view refreshes it; account/outlet changes clear remote state and invalidate old requests.

History, audit and report lists use the real service cursors. The client validates page order, cursor progression, numeric bounds, identifiers and immutable report identity/snapshot consistency. A historical report never uses current editable quantities. Business timezone is used for audit/report timestamps. Existing Room schema 8 and legacy data remain unchanged.

## Verified

- Debug/test APK builds and 12 JVM tests passed.
- One real-service HTTP scenario checks history/audit/report pagination, two locked generations, quantity 4 remaining in the old report after quantity 6 is locked, actor/reopen reason and revoked-session rejection.
- One native UI scenario traverses more than 20 server dates, audit, frozen report 4, current input 6, session expiry and successful reauthentication. Seven actual light-theme phone screenshots inspected; [gallery](history-review-r1.html).
- The existing native draft/validation/unsaved-navigation/save/conflict/use-server/logout scenario passed on the same final APK, using a separate fixture.
- Independent owned PostgreSQL queries confirm 24 seeded closings, 12 accepted receipts, 36 audit events (24 CREATE + 12 mutations), three frozen reports with quantities 4/6/4 and current native quantity 6. Read-only history/report screens added no mutations. Regression fixture shutdown queries separately confirm final quantity 12 attributed to the colleague and exactly two accepted entry mutations.

The negative native session test caught a real issue: reentering a cached detail loaded the list in the background, hiding that list's authentication error. Detail revalidation and inline reauthentication now pass. Initial visual QA also caught damaged separators; the final captures use correct separators/arrows and the closing timezone. Initial captures and the failed session run remain in central evidence; they are not the current gallery.

Final lint passed with 0 errors and 16 warnings; the report is in history-evidence-r1/lint.txt. Evidence/logs/APK hashes are in [history-evidence-r1](history-evidence-r1/). No production service handlers or operational data were changed.

## Remaining

This is a development source slice, not a complete demo candidate. Native admin/settings, online backup/import/export and legacy transfer safety, full demo packaging, and native-screen verification of process recovery remain open. Persisted offline access currently covers the device closing cache; server audit/report pages are not promised as a durable offline archive. Physical-phone UAT, tablet/dark/font scaling, public hosting and final APK metadata remain separate gates. Android metadata stays 0.1.1/versionCode 9, service 0.2.0-dev.3. No physical phone, hosting purchase or deployment occurred.
