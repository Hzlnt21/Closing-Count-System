# Closing Count System

Newest source slice: [native draft R1](docs/NATIVE_DRAFT_R1.md), [actual Android screenshots](docs/native-review-r1.html). Home → Closing bersama opens real login/outlet/date/summary/input/conflict/cached-history/account flows.12JVM+19Room+7HTTP+1native UI scenario pass. Supervisor lifecycle UI, process-kill/two-installation/reconnect acceptance and legacy/online transfer remain open. Historical descriptions below are checkpoints, not a claim that the connected app is complete.

Latest source foundation: [CCS-05 HTTP/session R1](docs/ONLINE_HTTP_R1.md) connects the Room outbox to existing service handlers, adds encrypted sessions and account-switch guards.12JVM+18Room/legacy+6HTTP emulator tests pass; actual PostgreSQL receipts/audit were checked. Two accounts use separate databases inside one QA installation; UI/two-installation/process-kill/reconnect acceptance remain open. R2 native screens are next, not already delivered. Offline app metadata stays0.1.1.

Latest portfolio checkpoint: [CCS-05 Room foundation R1](docs/ONLINE_ROOM_R1.md) adds additive schema7 and durable scoped outbox/ack/pull primitives;18 emulator instrumentation tests pass. No HTTP/native online integration yet. [Visual R2](docs/VISUAL_REFERENCE_R2.md) is now explicitly approved for application after inspecting the local FINORA screenshot. Old403/review-pending paragraphs below remain checkpoint history; they do not describe current reference availability. App metadata stays0.1.1 until the connected candidate is assembled and verified.

## Pengembangan kolaborasi online

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
