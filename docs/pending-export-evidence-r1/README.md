# Pending export core evidence R1

36 Android cases passed: 8 codec, 4 capture, 9 export and 15 sync/Room regressions. The recorded final APK pair matches current build outputs. 25 JVM tests and full app/test/lint run passed with 0 errors and 17 warnings; a final app/test rebuild fixed only the empty-target fixture and two source comments.

pending-export-qa.json is the actual frozen packet written through MediaStore and read back by Android. packet-verification.json independently checks its hash, fixture values, all three administrative kinds and absent credential/historical-actor fields. Its1707 exact bytes are checked in the Git index by source preflight. Twelve concurrent local ACK/capture iterations and a500-menu projection are separate test cases; neither proves network recovery or a heap benchmark.

This is an internal capture/export controller, not a completed backup/import UI. Fresh server status, recovery inbox, restore reviews, DocumentsUI and process/native acceptance remain future gates. All test data is dummy; local in-memory/random test databases and an unreachable loopback origin avoid operational data and HTTP. The owned emulator settings were unchanged and it was stopped.

The initial run failed two fixture assertions: a MediaStore row lacked a physical empty file. The fixture now creates the empty target before testing no-write/cancel behavior. That failed log is retained under superseded/ and is not counted as acceptance. The owner-machine harness uses existing D:/Code SDK/AVD paths, and does not access physical devices. APKs, credential fixtures and old crash logs are excluded.
