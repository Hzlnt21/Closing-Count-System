# Pending transfer visual and large-text verification R1

The approved R2 pending-transfer pages now leave10dp above outlined secondary actions, so paired buttons do not touch. Malformed JSON has a file-specific message instead of suggesting a network problem. Other transport/provider failures retain their existing message.

Final build passed25 JVM tests and lint0errors/17warnings. Seven inbox/ViewModel tests were rerun, including malformed-file copy, frozen provider bytes, duplicate/canceled callbacks, local account switch and explicit copy removal. Four production MainActivity/real DocumentsUI quantity journeys passed: light/dark at fontScale1 and light/dark at fontScale2. Each covers cancel/export/invalid-file rejection/frozen preview/provider rewrite/Activity recreation/accept/compare/apply/continue/explicit sync.32 native scenes had static text layouts measured with no significant internal text overflow. Large text uses ordinary scrolling; it is not required to fit one viewport.

The test harness now scrolls the LazyColumn to compose an offscreen target before finding/clicking it. The first large-text attempt timed out because the test waited for a child that was not composed yet. It did not prove a product failure or a passing journey. Initial evidence remains in global evidence/closing-count-pending-visual-r1; final evidence is closing-count-pending-visual-r2 and the curated source folder pending-visual-evidence-r1.

Independent PostgreSQL checks confirm two receipts and4then8 audit changes for each July1-4 closing, all by the owned actor; no internal server errors. Each journey restores old Room/legacy data. Device size/density/font/night/users match baseline; emulator and fixture were stopped. Screenshots were visually inspected for normal dark review and both large-text review variants. The large-text screenshots show a scrolled viewport, not truncated text layout.

Inherited, not rerun:88 PT01-06 Android cases and3 cross-install invocations from c145c07, plus22unit/80HTTP-PG service checks from earlier checkpoint. Production changes here are restricted to spacing and malformed-file copy; service, schemas, migrations, transport, sync/recovery logic and dependencies are unchanged. App0.1.1/code9, Room10, service0.2.0-dev.6.

PT07 remains OPEN for native administrative continuations and actual native process stop/restart. Activity recreation does not prove process restart. PT08/fullCCS07, CCS08 dummy demo packaging, physical PF03 and public acceptance remain open. No phone, operational database, provider, budget or deployment is involved.
