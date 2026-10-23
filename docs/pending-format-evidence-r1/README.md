# Pending format foundation evidence R1

27 Android cases passed:8 codec,4 transactional capture and15 existing Room/sync regressions. 25 JVM tests and app/test builds passed; lint0errors17warnings. The final Unicode fixture adds BMP/non-BMP round-trip coverage. APK input hashes and exact passed test names are recorded. Device settings were unchanged and the owned emulator stopped.

This verifies internal format validation and read-only Room snapshot capture. No online backup/import control, recovery inbox, administrative store capture, network recovery or physical-phone acceptance is claimed. Existing database reopen tests run in one test process; they do not prove actual app force-stop recovery. The complete remaining plan is ../ONLINE_PENDING_TRANSFER_PLAN_R1.md.

The owner-machine harness uses explicit D:/Code tool/AVD paths and isolated in-memory or randomly named test databases. It does not access an operational database or a physical device. Private QA configuration and APKs are excluded. Source preflight is added after explicit Git staging; it checks eight canonical checkouts and unchanged service, schemas, dependencies, existing UI/transport and legacy backup paths.
