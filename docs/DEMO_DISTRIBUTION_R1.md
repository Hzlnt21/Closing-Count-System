# Closing Count — kandidat distribusi demo lokal

Android **0.1.2-demo-local**, versionCode10, paket
`com.closingcount.app.demo`, nama launcher **Closing Count Demo**. APK debug
terpisah dari aplikasi utama; tidak menawarkan endpoint publik atau koneksi HP
Wi-Fi. Signature dan checksum diperiksa pada APK yang benar-benar diuji native.
Jangan menggantinya dengan build default QA atau APK aplikasi operasional.

## Jalankan paket hasil ekstraksi

Paket lokal berisi `operator/`, `android/`, `android-source/`, `guides/`, `media/`,
`package-info.json` dan `SHA256SUMS`. Periksa checksum sebelum digunakan. Prasyarat:
Windows, Node24, Docker Desktop dengan Linux engine dan image postgres:16, serta
Android SDK/platform-tools dan emulator uji yang tersedia. Download dependency,
image dan tooling memerlukan internet pada setup awal. Ini bukan paket offline
yang membawa seluruh SDK, Docker atau dependency terpasang.

Dari folder `operator/`:

```text
npm ci --prefix service
docker pull postgres:16
node demo/runtime.mjs init
node demo/runtime.mjs start
node demo/runtime.mjs status
```

API hanya loopback127.0.0.1:4372; PostgreSQL demo hanya127.0.0.1:55437.
Private state default berada di luar paket. Untuk lokasi lain, tambahkan
`--state-dir ABSOLUTE_PRIVATE_DIRECTORY` yang sama pada setiap perintah runtime.
Jangan menyalin private state ke paket/Git atau memakai database operasional.

Pada terminal privat, `node demo/runtime.mjs access` menampilkan akun dummy.
Jangan menjalankannya saat merekam atau membagikan layar terminal tersebut.

Pasang `android/Closing-Count-0.1.2-demo-local.apk` hanya pada emulator uji yang
dipilih eksplisit, misalnya `adb -s EMULATOR_SERIAL install -r PATH_TO_DEMO_APK`.
Pastikan serial adalah emulator yang dimaksud. APK memakai10.0.2.2:4372 untuk
menghubungi laptop host. Buka **Closing Count Demo**, masuk dengan akun dummy,
lalu ikuti [skenario reviewer](DEMO_SCENARIOS.md). Tidak perlu menghapus aplikasi
utama atau data operasional. UAT HP fisik tetap dijadwalkan pada PF03.

Untuk dua instalasi, gunakan dua lingkungan Android uji yang datanya terpisah;
akun Rani/Dimas saja pada satu instalasi tidak membuktikan sinkronisasi dua
instalasi. Bukti lokal sembilan fase sudah mencakup dua profil Android terisolasi
yang dijalankan bergantian pada emulator milik QA.

Setelah selesai, dari `operator/` jalankan `node demo/runtime.mjs stop`.
Data/volume tetap tersimpan. `start` mempertahankan fixture; `reset` membuat
generasi baru dan mencabut akses lama tanpa menghapus histori atau draft Android.
Tinjau/cadangkan pekerjaan lama sebelum beralih akun. Jika port/ownership tidak
cocok, ikuti penolakan runtime dan [panduan operator](DEMO_LOCAL.md); jangan
menghapus volume atau menghentikan proses yang tidak dikenal.

## Media dan cakupan bukti

`media/presentation.mp4` adalah23detik awal rekaman UI native nyata: input,
review, menu laporan dan pemakaian bahan historis. Pengisian kredensial tidak
direkam. Durasi aksi tidak dipercepat; frame rate dinormalisasi30fps untuk
pemutaran browser dan bagian logout/launcher dipotong. Video ini walkthrough
baca-saja, bukan rekaman seluruh sembilan fase domain atau bukti performa HP.
Screenshot empat layar berasal dari APK demo yang sama.

APK demo lulus native login/input/review/report/logout dengan pencocokan
snapshot bisnis PostgreSQL dan owner/legacy Room/baseline perangkat.27JVM serta
lint0errors/18warnings tercatat; warning tambahan `UnusedResources app_name`
berasal dari label launcher literal khusus varian demo. Sembilan fase domain
offline/konflik/supervisor/Excel/katalog/reset tetap bukti baseline0.1.1; perbaikan
urutan0.1.2 memiliki kontrol negatif/positif terpisah. Jangan menyatakan semuanya
diulang pada APK demo ini.

## Build dan provenance

Dari `android-source/`, setelah JDK/SDK dikonfigurasi:

```text
.\gradlew.bat assembleDebug -PclosingLocalDemo=true
```

Flag tersebut membatasi build ke debug, identitas `.demo` dan origin lokal4372.
Kombinasi dengan QA peer serta build release ditolak. Endpoint final HTTPS,
signing/distribusi APK publik dan retest pascadeploy tetap PF05.

Pengembang membuat paket dari repo menggunakan
`node demo/build-reviewer-package.mjs --out ABSOLUTE_FRESH_DIRECTORY` setelah
APK/media terverifikasi tersedia. Builder menolak APK yang hash/identitasnya
tidak cocok dengan inventory media dan output-metadata build. Source Gradle dan
Android yang dibutuhkan ikut disalin; local.properties, keystore, credential,
DB/Room, .git, cache dan dependency terpasang tidak disertakan. Link bukti di
panduan paket diarahkan ke reference source GitHub dan memerlukan akses repo;
langkah operasional dasar dapat dibaca dari paket tanpa membuka bukti eksternal.

`sourceMatchesBaseCommit` dan hash payload membedakan reference Git dari byte
source kandidat yang sedang dikerjakan. Git dapat mengonversi line ending;
`SHA256SUMS` adalah acuan byte paket. Demo lokal tidak menutup UAT perangkat,
budget/provider, deployment publik atau finalisasi portofolio/CV.
