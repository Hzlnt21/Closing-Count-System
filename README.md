# Closing Count System

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
