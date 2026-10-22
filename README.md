# Closing Count System

## Pengembangan kolaborasi online

CCS-02A menambahkan [fondasi service QA](service/README.md): akun individual, login/sesi, membership outlet, pengelolaan anggota oleh admin dan tanggal bisnis server. Backend `0.2.0-dev.1` terpisah dari versi Android. Aplikasi Android tetap offline0.1.1; catalog/closing bersama, transaksi sync, Room outbox dan integrasi UI belum tersedia.

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
