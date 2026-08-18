# Closing Count System

Closing Count System adalah aplikasi Android offline untuk menghitung penggunaan bahan **Terjual/Out** dari jumlah menu yang terjual setiap hari.

## Status

Versi aktif: `0.0.7` (hardening and device testing)

Roadmap lengkap tersedia di [ROADMAP.md](ROADMAP.md).

Hasil pengujian perangkat tersedia di [TESTING.md](TESTING.md).

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

## Privasi data

Seluruh data aplikasi disimpan secara lokal pada perangkat. Aplikasi tidak memerlukan akun, koneksi internet, atau layanan cloud.
