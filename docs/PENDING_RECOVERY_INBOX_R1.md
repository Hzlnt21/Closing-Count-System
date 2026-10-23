# Quarantined recovery inbox R1

23 October 2026. PT-04 adds the internal inbox and picker-controller foundation; no backup/import buttons or recovery application are connected yet. [Remaining transfer gates](ONLINE_PENDING_TRANSFER_PLAN_R1.md).

The controller reads a bounded provider stream off the main thread, strictly validates the entire pending-backup format and keeps private frozen bytes. Preview returns only identity, origin/account/outlet/device labels, date range, counts and SHA-256. It does not create a session or accept a service URL. Cancel/stale/duplicate callbacks cannot accept a different ticket; acceptance consumes the current ticket once, even if storage fails.

Explicit acceptance stores the exact reviewed bytes in `noBackupFilesDir/online-recovery-inbox/<sha256>.json` through Android AtomicFile. The store has no repository, Room, session or HTTP dependency. It cannot insert runnable operations, rewrite server cache or authenticate. Reading a stored packet validates its full schema again and requires its content hash to match the filename. An identical fingerprint is returned as a duplicate; a corrupt existing record is never silently overwritten by another copy.

Listing reconstructs metadata from committed files rather than maintaining a second index that could diverge. Abandoned uncommitted `.new` files are not accepted recoveries. AtomicFile backup recovery remains available; corrupt committed data produces an error. No automatic repair, deletion or clearing of other recoveries occurs. A process-wide lock serializes multiple store/controller instances in this single-process application; cross-process writers are not supported.

Capacity is ten distinct recoveries and 100MiB total recognized AtomicFile storage; each packet remains bounded to 20MiB. The capacity check includes abandoned files and explicitly refuses a new packet when full. Identical accepted duplicates still open without consuming capacity. These are local storage safeguards, not a destructive rotation policy. A completed-target/archive management flow belongs to later recovery UI work.

## Verification boundary

45 Android cases pass on the final APK pair: nine inbox, eight codec, four capture, nine export and fifteen Room-sync cases. Build/25JVM pass; lint has zero errors and 17 existing warnings. The nine inbox cases cover preview/cancel/stale/duplicate callbacks, provider rewrites, fresh store instances, exact fixture payloads (quantity, lifecycle, settings, team creation and catalog; team-update format remains covered by the codec regressions), malformed/missing providers, corrupt accepted files, interrupted AtomicFile writes, eight simultaneous duplicate accepts and count/byte-capacity preservation. Sibling sentinel files demonstrate isolation; they are not a claim of operational database testing.

The separate process test stages a committed packet and an uncommitted partial file, then verifies full model/bytes and duplicate recognition in a second instrumentation process after an actual force-stop. Both process invocations pass with different PIDs. The 2034-byte output is independently compared to a complete expected packet on the host. Device settings are unchanged and the emulator is stopped afterward. [Evidence](pending-inbox-evidence-r1/runtime-proof.json). Force-stop/AtomicFile tests do not simulate power loss or filesystem hardware failure.

No network calls, restore application, Android picker UI, fresh account/outlet authorization or physical/cloud-provider UAT is accepted by these tests. Attaching a recovery still requires current same-origin/account/outlet authorization and fresh server comparisons at PT-05/PT-06; imported identities cannot grant access. Native picker/configuration/visual flows remain PT-07.
