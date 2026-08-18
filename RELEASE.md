# Release Guide

Dokumen ini ditujukan untuk maintainer. Release APK harus selalu ditandatangani dengan key yang sama agar Android dapat memperbarui aplikasi tanpa menghapus data.

## Rahasia signing

Build membaca empat environment variable berikut:

```text
CLOSING_COUNT_KEYSTORE_PATH
CLOSING_COUNT_KEYSTORE_PASSWORD
CLOSING_COUNT_KEY_ALIAS
CLOSING_COUNT_KEY_PASSWORD
```

Keystore release lokal menggunakan alias `closing-count`. File `*.jks`, `*.keystore`, dan konfigurasi kredensial sudah diabaikan oleh `.gitignore`.

Simpan sedikitnya dua salinan keystore dan password di lokasi aman yang berbeda. Kehilangan key atau password berarti APK berikutnya tidak dapat memperbarui instalasi yang sudah ada.

## Build dan verifikasi

Dari root project pada PowerShell:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug connectedDebugAndroidTest assembleRelease
```

APK dihasilkan di:

```text
app/build/outputs/apk/release/app-release.apk
```

Untuk pengujian update saja, versi build dapat dioverride tanpa mengubah source:

```powershell
.\gradlew.bat assembleRelease "-PversionCodeOverride=7" "-PversionNameOverride=0.0.7"
```

Jangan gunakan override untuk artifact yang akan dipublikasikan.

Verifikasi signature sebelum publikasi:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.1.0\apksigner.bat" verify --verbose --print-certs app\build\outputs\apk\release\app-release.apk
```

## Checklist publikasi

1. Pastikan working tree bersih dan seluruh pengujian lulus.
2. Pastikan `versionCode` naik dan `versionName` sesuai tag.
3. Uji instalasi atau update APK pada HP target.
4. Commit, buat annotated tag, lalu push branch dan tag.
5. Buat GitHub Release dari tag tersebut dan lampirkan `app-release.apk` beserta checksum SHA-256.

Keystore release tidak boleh diunggah sebagai source, artifact, atau lampiran release.
