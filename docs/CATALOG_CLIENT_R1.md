# Catalog Android client foundation R1

23 October 2026. This checkpoint connects typed Android category, ingredient, menu and recipe models to the existing versioned catalog API. It is the persistence/validation foundation for the native editor; there is no new catalog editing screen in this checkpoint.

## Behavior

The full catalog is reviewed and published together, with stable server UUIDs and an exact base revision. Validation preserves the one-use-per-ingredient recipe formula: 1Ã¢â‚¬â€œ100 distinct registered ingredients per menu; active menus require active ingredients. Categories, names, integer ordering, unique identities and collection limits are checked before transmission. JSON types and allowed catalog fields are decoded strictly. Deleting a referenced category or ingredient cannot produce a valid replacement catalog.

Before sending, Android writes an immutable operation ID, reviewed revision and catalog payload to AtomicFile in noBackupFilesDir. The actual UTF-8 publication body is capped at the server's 524288-byte limit. Account-switch/logout guards include these commands. Retry uses the same ID and payload; uncertain or authentication-paused operations cannot be discarded silently. A definitive stale revision needs dismissal and a fresh review, not rebasing to a newer revision automatically.

The publication response must match both the proposed catalog and base revision plus one. After acknowledgement, Android reads the latest catalog before clearing the intent. If another administrator published a later version, recovery returns that newer version without overwriting it. Same-account login resumes authentication-paused work. No background delivery or cross-device intent transfer is promised.

## Evidence

- Nineteen JVM tests passed, including seven catalog cases for stable identity/canonical order, independent historical values, reference/deactivation guards, recipe validity and name/order/collection boundaries.
- Three emulator storage/decoder cases passed: exact JSON types/unknown fields, immutable uncertain/auth-paused intents, account partitioning and AtomicFile backup recovery.
- One Android-to-real-HTTP/PostgreSQL scenario passed: employee publication denial, pending logout/account-switch guards, real commit with deliberately omitted local acknowledgement, later publication before recovery, stale queued review, reauthentication and frozen closing catalogs.
- Independent read-only database proof confirms six catalog versions and exactly five distinct publication receipts, all attributed to the fixture administrator. June 1 retains version 1; June 2 captures version 6 and its updated recipe/name snapshot. The initial fixture version has no publication receipt because it was seeded directly.

Regression: seven existing Android HTTP/session scenarios, one native draft/conflict scenario, one settings HTTP scenario and one team HTTP scenario passed. All six runtime groups used identical final app/test APKs. [Logs, hashes and database proof](catalog-client-evidence-r1/).

Final lint passed with 0 errors and 16 warnings.

Service implementation, SQL migrations and dependency resolution remain unchanged from native team R1 (0.2.0-dev.5). Existing service20unit/72Node results are inherited, not rerun or claimed as new coverage. Room stays schema8 and Android metadata stays0.1.1/versionCode9.

Repository/store reconstruction after an actual commit is not an OS-kill test. There are no new native-editor screenshots or physical-phone/public acceptance claims. Native editor/review UI, retained unsubmitted editor drafts, responsive validation, transfer, full native process recovery and complete demo packaging remain open. Goal acceptance still includes all eight projects and later joint phone UAT, reviewed infrastructure, public demos, portfolio and CV.
