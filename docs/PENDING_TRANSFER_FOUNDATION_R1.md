# Pending-data transfer foundation R1

23 October 2026. The new `closing-count-online-pending-backup` version1 model and strict codec preserve quantity drafts, original ordered operation references, lifecycle intentions and the non-secret settings/team/catalog payloads. This is an internal foundation; no online backup/import control is connected to the UI yet. The [complete transfer plan](ONLINE_PENDING_TRANSFER_PLAN_R1.md) remains the acceptance authority.

The file is separate from the existing local-history version1 format. Its account/outlet/origin/device labels are provenance, not grants of access. No decoding path creates a session, writes server data, restores a cache or inserts a runnable operation. Credential fields and unknown keys are rejected. Passwords and historical server authorship are absent from the transport model. Original operation references will support later server duplicate checks, not automatic replay.

The lexical reader rejects duplicate object members, malformed UTF-8/UTF-16, trailing JSON, numeric coercion/fractions/exponents, unsafe integers and oversized/deep/complex graphs. Known field sets, UUIDs, canonical origin/date/time, quantity bounds, unique operation identities, dependent queue shape, local tail quantities and non-secret administrative payloads are validated. Pending operations from an older generation can be retained as unresolved history for fresh review. The encoder passes its output back through the same external-file decoder and uses a reporting UTF-8 encoder.

Room adds a read-only transactional capture API. It returns pending headers/rows/quantity operations/lifecycle intentions for one exact scope. Synced-only closings are excluded. Source status and operation identities remain unchanged. Missing parents or a pending row/queue mismatch fail visibly instead of yielding an incomplete successful snapshot. This API is not wired to a complete bounded export pipeline; administrative store capture and header-memory projection are PT-02 work.

## Verification

- Eight Android codec cases: full packet round trip, BMP/non-BMP text, exact wide integers, team membership payload, credentials/unknown fields, lexical types, repeated IDs/keys, queue/date/lifecycle contradictions, malformed text/size/depth and stable raw-file fingerprints.
- Four isolated Room cases: account-scope isolation; SENT plus dependent queue preservation; lifecycle-only capture; inconsistent/orphan pending data rejection without queue mutation.
- Fifteen existing Room/sync regression cases pass, including storage reopen, acknowledgement/rollback, conflict/retry and migration behavior. Storage reopen within a test process is not an actual app process-kill acceptance.
- 25 JVM tests, app/test builds and lint pass with 0 errors and 17 existing warnings. The last test-only rebuild adds Unicode to the round-trip fixture. All 27 Android cases use the recorded final APK pair. Device settings remain unchanged and the owned emulator is stopped.

[Logs, APK hashes, verification and harness](pending-format-evidence-r1/). No service, dependency, Room schema, live queue mutation behavior, existing UI or local backup codec changes. No HTTP recovery/inbox/native backup UI or physical-phone acceptance is claimed. Next: PT-02 through PT-08, then CCS-08 complete demo packaging. The full eight-project portfolio/CV goal remains active.
