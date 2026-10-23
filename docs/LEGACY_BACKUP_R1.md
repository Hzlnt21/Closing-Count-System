# Legacy backup validation R1

Follow-up: [local review and real system-document flow](LOCAL_TRANSFER_UI_R1.md) now has separate native evidence. The codec-only verification below remains its original checkpoint scope.

23 October 2026. The version1 local backup reader now rejects coerced strings/booleans, fractional integer fields, narrowing overflow, malformed UTF-8, files above20MiB, invalid canonical dates, negative IDs, duplicate composite rows/metadata and recipes without their historical menu entry. Validation happens before the existing transactional legacy replacement.

The version1 encoder and schema number are preserved. Historical menu/ingredient IDs do not need to exist in today's master catalog. Unknown historical category ID0 remains readable because migrations4->5 and5->6 deliberately produced that sentinel. Original historical names and totals are retained; this change does not recalculate or relabel them from current masters.

## Verified

Ten Android instrumentation tests passed using the platform JSON implementation and isolated in-memory Room8 databases:

- Valid version1 master/history round trips, deleted historical master IDs, migrated category0, leap-day2024-02-29 and signed64-bit ID boundary.
- Ten typed/coercion cases, invalid calendar formats/dates, negative IDs, five duplicate-key families, orphaned historical recipe, corrupt UTF-8 and the size cap.
- A real SQLite duplicate constraint after replacement starts rolls back all deleted legacy data.
- Restoring legacy history leaves online headers, entry state, SENT/queued quantity operations and a SENT lifecycle operation unchanged. Exported version1 data contains only the ten legacy collections.

19 JVM tests and app/test builds passed; lint remains0errors/17warnings with the previously recorded window-container sizing recommendation. Service, schemas, UI and dependency versions are unchanged. [Logs, APK hashes and local harness](legacy-backup-evidence-r1/).

## Remaining transfer work

These are codec and transaction tests, not native document-picker acceptance or a server backup/import/export implementation. In-memory Room tests do not prove real-device file-provider behavior, full app restart recovery or PostgreSQL transfer. No physical phone, operational data or hosting was accessed.

Next: explicit local-versus-server wording, reviewed restore/transfer flow, online report export and authorized import safety, followed by complete dummy demo packaging. No automatic local history upload or integer-to-server-UUID identity mapping is introduced. CCS-07 and the full eight-project goal remain open.
