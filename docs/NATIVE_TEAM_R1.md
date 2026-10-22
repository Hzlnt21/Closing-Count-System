# Native team management R1

23 October 2026. The account screen opens an administrator-only team panel following the approved R2 visual direction. Administrators can create individual accounts, page through the roster, review role/access changes and refresh their own access after a self-demotion. Employees do not see team administration. [Eight actual emulator screenshots](team-review-r1.html).

## Persistence and service boundary

Service 0.2.0-dev.5 adds migration 005, membership revisions, immutable membership audit and actor/outlet/operation-bound team receipts. Account creation and membership changes commit with their audit and receipt in one transaction. Updates require the exact reviewed membership revision; legacy membership updates also advance that revision. The last active administrator cannot be removed. Roster pagination uses the same explicit C collation for ordering and cursor comparison.

Android saves immutable non-secret command metadata in backup-excluded AtomicFile storage before transmission. Passwords remain transient and masked; neither passwords nor password verifiers are written to this store. A creation retry first checks the original receipt. If no result exists, the user must enter the original password to continue the same command. Server fingerprints use a scoped scrypt derivation, not a fast password digest. Uncertain/auth-paused commands cannot be silently discarded or moved to another account. Definitive conflicts require a fresh review.

Normal team reads and all mutations require current administrator access. One narrow exception lets an authenticated actor read only their own already-recorded UPDATE_MEMBERSHIP receipt that removed their own administration or outlet access. It exposes no roster or other member, and grants no mutation rights. This permits acknowledgement recovery after self-demotion, including after logging back in with no assigned outlet. Unknown receipts and POST replay remain denied to that former administrator.

## Verification

- Service: 20 unit tests and 67 HTTP/PostgreSQL scenarios passed (72 Node tests including five parent scopes). New cases cover pagination punctuation, concurrent duplicate creation, changed-password rejection, stale role changes, legacy revision invalidation, rollback, last-admin protection and the narrow personal receipt boundary.
- Android: 12 JVM tests and debug/test APK builds passed. Three team-store tests verify non-secret persistence, immutable uncertain commands and backup recovery. One real HTTP scenario reconstructs the repository after a real commit while deliberately skipping local acknowledgement; it then recovers without a password, detects stale membership and acknowledges a self-access removal after relogin.
- Native emulator: employee restriction, last-admin rejection, short-password validation, account creation, roster pagination beyond 30 members, reviewed role/deactivation and own-role refresh passed in one scenario. Eight screenshots were inspected for readable contrast, alignment and unobstructed controls.
- Independent database proof: HTTP fixture has six members and five receipts/audits; native fixture has 37 members and four receipts/audits. Each retains exactly one active administrator. Fixtures contain fictional identities only.

Regression: seven existing HTTP/session scenarios, the original native draft/conflict scenario, one settings HTTP scenario and the settings native Activity-recreation scenario passed on identical app/test APKs. The first settings native run reused an already-modified fixture and timed out waiting for version 0; it passed with a fresh isolated fixture without code changes. Failed evidence remains in central evidence.

Final lint passed with 0 errors and 16 warnings. [Logs, APK hashes and database proof](team-evidence-r1/).

Repository reconstruction and AtomicFile recovery are not actual OS-kill acceptance. This slice does not claim physical-phone, tablet, dark-mode, font-scale, public deployment or complete product acceptance. Room stays schema 8; Android metadata stays 0.1.1/versionCode 9. Catalog/recipe editing, transfer, full native process recovery and complete demo packaging remain open.
