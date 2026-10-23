# Closing Count System

Pending-export core: [bounded capture and frozen file output](docs/PENDING_EXPORT_CORE_R1.md). Local draft and admin intentions can be captured and written through the internal controller; 36 Android cases cover the format/capture/export/sync boundary. Backup/import UI, server duplicate checks and recovery inbox are still open.

Pending-transfer foundation: [versioned draft format and read-only Room capture](docs/PENDING_TRANSFER_FOUNDATION_R1.md), [remaining recovery plan](docs/ONLINE_PENDING_TRANSFER_PLAN_R1.md). Codec/capture/sync tests pass; online backup/import UI, recovery inbox and fresh server review are still being implemented.

Newest online transfer: [locked-report Excel download](docs/ONLINE_REPORT_EXPORT_R1.md), [native and workbook review](docs/report-export-review-r1.html). The selected frozen generation supplies both workbook tabs; fresh account/outlet/report checks run after the system save dialog and before report bytes are written. Verification details and limits accompany the source checkpoint. Online import safety and complete demo packaging remain open.

Newest local transfer: [validated review and native document flow](docs/LOCAL_TRANSFER_UI_R1.md), [light/large-text dark gallery](docs/local-transfer-review-r1.html). Choose and inspect a local file before replacement; cancellation and stale reviews preserve data, Activity recreation retains the reviewed identity, and provider changes do not replace its payload. Two real system-picker/save journeys, controller tests and legacy regressions pass. Online transfer and complete demo packaging remain open.

Newest transfer checkpoint: [legacy backup validation](docs/LEGACY_BACKUP_R1.md). Version1 encoding remains unchanged; strict decoding rejects silent coercion/corruption before restore. Ten Android codec/Room tests cover historical compatibility, transactional rollback and online queue isolation. Native file-provider flow and online transfer remain open.

Newest verification: [native responsive/dark/large-text layouts](docs/NATIVE_LAYOUT_R1.md), [42 screenshots across six configurations](docs/layout-review-r1.html). Compact navigation, long-name and quantity layouts preserve the approved R2 direction. Settings-review and unsaved-message dialog bodies can scroll. Six selected layout scenarios, a detector control and three native regressions passed on the same APK pair.

Previously verified: [actual native recovery after six force-stops](docs/NATIVE_PROCESS_RECOVERY_R1.md), [six recovery screenshots](docs/native-recovery-review-r1.html). Five committed-operation replies are deliberately interrupted; the native screens recover their original intents with no duplicate receipts. A saved catalog draft also returns through the editor.

Current connected checkpoint: [native catalog and recipe editor](docs/NATIVE_CATALOG_R1.md), following approved R2. Members read the catalog; administrators manage categories, ingredients, menus, active status, ordering and recipe selections. Valid row saves persist an editable local draft; publication freezes its identity and payload. The [typed catalog client foundation](docs/CATALOG_CLIENT_R1.md) supplies validation and receipt recovery.

Already integrated: [native team administration](docs/NATIVE_TEAM_R1.md) ([eight screenshots](docs/team-review-r1.html)), [outlet settings](docs/NATIVE_SETTINGS_R1.md), [history/audit/immutable reports](docs/NATIVE_HISTORY_R1.md), [supervisor lifecycle](docs/NATIVE_LIFECYCLE_R1.md) and [draft/conflict UI](docs/NATIVE_DRAFT_R1.md), following [approved R2](docs/VISUAL_REFERENCE_R2.md). [Repository process recovery](docs/PROCESS_RECOVERY_R1.md) is separate evidence from full native restoration or physical-phone acceptance.

The eight-project program remains in progress. Transfer, broader accessibility/multiwindow coverage, wider recovery permutations and complete demo packaging remain open. Android metadata stays0.1.1; the existing service stays0.2.0-dev.5. Older implementation descriptions below are historical checkpoints and scope context.

## Riwayat fondasi dan scope kolaborasi online

CCS-04 menambahkan [service QA0.2.0-dev.3](service/README.md): submit/return/review-lock/reopen dan laporan immutable per generasi, melanjutkan katalog/closing bersama serta operasi jumlah transaksional. Android masih offline0.1.1; Room outbox, integrasi UI, legacy/export dan demo utuh belum tersedia. [Referensi visual terbaru](docs/VISUAL_REFERENCE.md) mengikuti FINORA pilihan pengguna; preview biru awal kini arsip, belum disetujui.

Branch `portfolio/closing-online` menyiapkan rilis portofolio berdasarkan keputusan satu closing bersama per outlet/tanggal, akun individual, draft offline, konflik terlihat dan supervisor review/lock/reopen. [Kontrak implementasi](docs/ONLINE_CONTRACT.md) merinci invariant, izin, outbox, idempotency, migrasi dan acceptance. [Usulan visual interaktif](docs/visual-review.html) merupakan preview desain yang perlu direview sebelum redesign Compose.

Checkpoint spesifikasi `ccs-online-plan-r1` belum mengubah aplikasi Android 0.1.1. Kontrak/preview bukan bukti implementasi backend, sinkronisasi atau UAT. Perilaku baseline offline di bawah tetap berlaku sampai implementasi berikutnya terverifikasi. Provider/budget/deployment dan uji HP fisik tetap tahap program bersama.

Closing Count System adalah aplikasi Android offline untuk menghitung penggunaan bahan **Terjual/Out** dari jumlah menu yang terjual setiap hari.

## Status

Versi stabil: `0.1.1` (blue theme and application identity)

Roadmap lengkap tersedia di [ROADMAP.md](ROADMAP.md).

Hasil pengujian perangkat tersedia di [TESTING.md](TESTING.md).

Panduan penggunaan dan pembaruan tersedia di [USER_GUIDE.md](USER_GUIDE.md). Proses build release untuk pengembang tersedia di [RELEASE.md](RELEASE.md).

## Teknologi

- Kotlin
- Jetpack Compose
- Material 3
- Room/SQLite
- Gradle

## Build lokal

Project memerlukan Android SDK dan JDK yang kompatibel. Dari root project, jalankan:

```powershell
.\gradlew.bat assembleDebug
```

Debug APK akan dibuat di:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Build APK release memerlukan signing key. Ikuti [RELEASE.md](RELEASE.md); jangan pernah memasukkan keystore atau password ke Git.

## Privasi data

Seluruh data aplikasi disimpan secara lokal pada perangkat. Aplikasi tidak memerlukan akun, koneksi internet, atau layanan cloud.
