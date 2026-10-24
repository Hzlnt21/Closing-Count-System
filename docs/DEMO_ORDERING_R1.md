# Catalog presentation ordering R1

24 October 2026. Android 0.1.2/code10, Room10, service0.2.0-dev.8.
[Actual screens](demo-ordering-review-r1.html),
[native results](demo-ordering-evidence-r1/verification.json),
[build inputs](demo-ordering-evidence-r1/build-verification.json).

## Change and preservation

Input, review and frozen reports now display menus/ingredients by category
sortOrder, item sortOrder and stable ID ties, consistently with the Excel
exporter. Snapshot arrays remain UUID-canonical; no stored catalog, quantity,
recipe, actor or historical report is rewritten. Inactive historical rows remain
visible. Room schema, service, SQL and dependencies are unchanged.

## Verified evidence

The preserved R5 fixture generation2 contains a deterministic counterexample:
Latte's UUID precedes Cappuccino's, while configured display order puts
Cappuccino first. A normal native test against the verified 0.1.1 APK fails
exactly at the expected menu-order assertion. The same test against 0.1.2 passes
input, review and frozen-report checks, including actual UI positions and values.
This is one passing positive test and one expected negative control, not two
passing application tests.

The current closing stays3/5. The preceding locked report stays6/4 with ingredient
totals10/10/6. Independent host-side PostgreSQL business snapshots before/after
match, including catalogs, entries, reports, receipts and audits. Legacy and owner
Room hashes, device settings/users and current-user baseline also match. The two
owned temporary users are removed, runtime/emulator stopped and history/volumes
retained. Four positive screenshots were visually inspected; content below a
scroll viewport is not treated as lost data.

The final endpoint4372 build passed27 JVM tests and lint with0 errors/17 warnings.
Two ordering tests include independent copied inputs to detect accidental
mutation. APK hashes match the actual native invocation. The nine-phase domain
journey remains explicitly inherited from the
[0.1.1 native baseline](DEMO_NATIVE_R1.md); it was not relabelled as a 0.1.2 rerun.

## Failed attempts and scope

Ordering attemptR1 reproduced the old bug, then timed out during the fixed APK's
login/IME helper. Its failure screenshot shows an Android ANR dialog; the cause
is not established. The test helper was changed to hide only the current
Activity's IME instead of reading global dumpsys state/sending shell Back.
AttemptR2 then completed normally, including login/logout and preservation.
No production keyboard behavior was changed to make the test pass.

An earlier build in this session used an incorrect property name and therefore
defaulted to QA4371. It was superseded before native verification by the explicit
`-PclosingApiOrigin=http://10.0.2.2:4372` build recorded here. Private runtime,
credentials, diagnostics and Room databases are excluded from this archive.
Archived drivers use the developer's Windows workspace, not portable operator
entrypoints.

CCS08 media, complete APK/distribution and final local acceptance remain open.
Physical HP PF03, budget/provider PF04, public deployment PF05 and final
portfolio/CV are separate. This checkpoint makes no Production/public claim.
