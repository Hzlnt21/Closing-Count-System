# Physical DocumentsUI files R1

17 November 2026. Scoped follow-up to [physical USB R1](DEMO_PHYSICAL_USB_R1.md)
on the same authorized M2101K7BNY / Android13 SDK33 / ARM64 primary user0.
Source base `38a259aa2ae498677ac579c854cd50ef92aa4212`; installed app
0.1.2-demo-local/code10, service0.2.0-dev.8. No application code, schema,
dependency, build or install change. The earlier27 JVM tests/four build guards
are inherited evidence, not a new execution. This checkpoint adds documentation
and sanitized proof only. Earlier10 native/13 independent checks remain historical.

## Observed scope

All21 scoped checks pass: a mixture of actual native UI/file-provider behavior
and independent file, Room and full-database oracles, not21 native-only test cases.
The same retained marked runtime/volume and original accounts were used.

| Actual flow | Result and independent verification |
| --- | --- |
| Frozen report, cancel and save | Actual local DocumentsUI CreateDocument cancel returns without a false success; saving a new owned Downloads XLSX succeeds. ZIP CRC, two worksheets, numeric Cappuccino4/Latte6, total10/formula, ingredients/date/generation1/revision6 match the frozen snapshot. All15 DB tables and12 legacy Room tables stay exact. |
| One new ordinary offline intent | Normal API/PostgreSQL stop; Employee saves Cappuccino6 once from retained5/6, DRAFT/generation2. One QUEUED original UUID retained; this is additional work, not replay of the earlier six commands. |
| Pending JSON cancel and save | Real CreateDocument cancel/save, one closing/item, original quantity6/base5/generation2 and all identity/revision fields exact. Strict JSON/no duplicate keys; no credentials/session/lifecycle/admin operation. All19 Room tables exact across cancel/save. |
| OpenDocument and inbox | Actual local Downloads selection of that owned file, explicit Save to inbox; exact933-byte copy, same original still QUEUED, Room unchanged. No draft applied by import. |
| Restart and fresh compare | Normal API/PostgreSQL restart preserves all15 tables exactly before sync. Compare refuses RECOVERY_LOCAL_PENDING while original remains local. |
| Normal foreground sync | Closing transfer pauses allows ordinary automatic sync of that SAME original once: one new receipt/ENTRY_UPDATED audit, current DRAFT/generation2 quantities6/6. No second Save, cloned operation or recovery Apply. Prior12 receipts/14 audits and both frozen reports retained. |
| Recorded file review | Fresh-server compare says all references handled and no figures will be queued again; Room and all15 DB tables unchanged by this review. |
| Cleanup | Two authorized sessions both logged out/revoked, prior sessions exact; native quantity/lifecycle/recovery queues/claims empty. Same UID/app data, owned files/inbox retained, legacy12 Room tables and their sequences exact. Owned API4372/PostgreSQL55437 stopped, only own reverse removed, other mappings exact, phone Home. |

Selected actual native screens visually confirm saved generation1/revision6 and
the recorded-reference no-new-queue state. Raw screens, files, Room/SQL snapshots,
credentials and private fixture remain outside this repository.

## Artifact identity

| Artifact | Bytes | SHA256 |
| --- | ---: | --- |
| Same installed USB debug APK | 20,681,213 | `8aa46e927491c5424756a73dc45e5fd9a5748bb90d2145e47d6ff4f0d9f6e8fe` |
| Actual saved XLSX | 4,587 | `6a2e01692c09e81be6f2fe63ea61d79efff5f8ec241729d233bbf779d66bdbe9` |
| Actual saved pending JSON | 933 | `229b7b430f8749690436d4993854ea8ce4c9d15551706f6099b965606e5a8b85` |

Original new-operation ID SHA256:
`0b5504516a0300b0c73245f0964a9cfbe51c9b4705056cb950eb8c723a8cdce5`.
Full private custom PostgreSQL backup is51,592bytes, PGDMP format; pg_restore
--list is readable with15 TABLE DATA entries. Actual restore was not performed.
The earlier emulator ZIP20,623,722bytes/c1c53335 remains emulator-scoped.

[21-check record](demo-physical-files-r1/phone-checks.json),
[XLSX oracle](demo-physical-files-r1/xlsx-verification.json),
[JSON oracle](demo-physical-files-r1/pending-json-verification.json),
[same-original receipt](demo-physical-files-r1/same-original-receipt.json),
[final preservation](demo-physical-files-r1/final-preservation.json),
[cleanup](demo-physical-files-r1/cleanup.json).
Program evidence run: closing-file-provider-20261117/31e5ad2610.

## Limits and observer corrections

Not fresh-device restore/transfer acceptance. Physical malformed/foreign-context
files, picker Activity recreation, other/cloud providers, actual recipient-app
opening, broader font/rotation/accessibility, LAN/power/reboot/multi-device and
public behavior remain open. Native navigation rendered and worked by actual
touch, but UiAutomator reported zero bounds: cause and TalkBack impact remain
unestablished; full accessibility is not claimed.

Observer-only corrections were reconciled without repeating Save/login/import:
initial marker-only snapshot was replaced before UI by full15-schema-table
baseline and8 legacy business-group comparison; historical timestamp precision,
numeric IDs and proven label-encoding differences were normalized for comparison
only, not repaired in DB. Actual pending status is QUEUED and valid JSON933bytes;
wrong observer assumptions were corrected by rereading the same original.
Android exec-out does not reliably propagate remote test exit status: the
not-yet-accepted inbox guard was corrected to shell-v2 before one explicit accept.
QA helpers compiled after correction. Full-table stop/restart proof is separate
from the initial marker-only observation.

Two pre-existing October2026 untracked JVM memory-crash logs remain byte-exact,
unstaged. Application inputs1452 tracked files were exact before these docs;
canonical repository91 raw files/HEAD/index/status were preserved.
No reset/clear/uninstall/reseed, old operation replay, operational DB, personal
file deletion, external recipient or deployment. This is scoped demo evidence,
not overall project/program completion or Production readiness.
