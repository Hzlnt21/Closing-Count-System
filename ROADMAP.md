# Closing Count System Roadmap

## Product Goal

Closing Count System adalah aplikasi Android offline untuk menghitung jumlah penggunaan bahan pada kolom **Terjual/Out** berdasarkan jumlah menu yang terjual pada suatu tanggal.

Setiap bahan yang terdaftar pada sebuah menu bernilai satu penggunaan untuk setiap satu menu yang terjual.

Contoh:

- Cappuccino menggunakan Biji Kopi, Fresh Milk, dan Powder Cokelat.
- Latte menggunakan Biji Kopi dan Fresh Milk.
- Jika terjual 2 Cappuccino dan 1 Latte, hasilnya adalah Biji Kopi 3, Fresh Milk 3, dan Powder Cokelat 2.

## Agreed Scope

- Aplikasi Android native dalam bentuk APK.
- Berjalan sepenuhnya secara offline.
- Digunakan oleh satu pengguna tanpa akun atau login.
- Satu closing untuk setiap tanggal.
- Closing pada tanggal yang sama dapat diedit.
- Semua menu ditampilkan dalam satu halaman input dan dikelompokkan berdasarkan kategori menu.
- Setiap menu memiliki daftar bahan tanpa kuantitas gram, mililiter, atau satuan lainnya.
- Hasil Terjual/Out dikelompokkan berdasarkan kategori bahan.
- Kategori hasil dapat di-expand dan di-collapse.
- Menu dan bahan dapat dinonaktifkan tanpa merusak riwayat.
- Riwayat closing tetap konsisten walaupun resep menu kemudian berubah.
- Hasil closing dapat diekspor ke Excel.
- Seluruh data dapat di-backup dan di-restore.

## Out of Scope

Versi awal tidak menangani:

- Stok awal.
- Permintaan bar.
- Spoiled atau waste.
- Perhitungan sisa stok.
- Harga bahan, HPP, pendapatan, atau profit.
- Sinkronisasi cloud.
- Multi-user dan sistem login.
- Integrasi POS.

## Initial Ingredient Categories

Urutan kategori dan bahan mengikuti form operasional coffee shop.

### Bahan Baku

1. Fresh Milk
2. Evaporasi
3. Soda
4. Yakult
5. CHI
6. SKM
7. Sunquick Mangga
8. Jungle Orange
9. Hydrococo
10. Blend 08
11. Whipecream

### Powder

1. Matcha
2. Coklat
3. Alpukat
4. Max Creamer
5. Teh Dandang
6. Teh Gopek
7. Teh Tongtji
8. Milo

### Syrup

1. Salted Caramel
2. Lychee
3. Lemon
4. Strawberry
5. Pandan
6. Pappermint
7. Sakura
8. Vanilla
9. Raspberry
10. Gula Aren

### Buah

1. Strawberry
2. Leci Kaleng
3. Lemon

### Lain-lain

1. Chocolatos
2. Regal Biscuit
3. Ice Cream Vanilla
4. Air Mineral
5. Simple Syrup
6. Butter
7. Selai Blueberry
8. Selai Strawberry

Nama bahan di atas mengikuti rekap awal dan dapat dikoreksi melalui master data jika terdapat perbedaan ejaan pada form operasional.

## Technical Direction

- Kotlin sebagai bahasa utama.
- Jetpack Compose untuk antarmuka Android.
- Room/SQLite untuk penyimpanan data lokal.
- Portrait sebagai orientasi utama.
- Bahasa antarmuka Indonesia.
- Tema terang sebagai tema awal.
- Minimum target awal Android 8.
- Signed release APK untuk distribusi dan pembaruan aplikasi.
- Package ID awal: `com.closingcount.app`.

## Version Roadmap

### v0.0.1 - Project Foundation

Target:

- Membuat project Kotlin dan Jetpack Compose.
- Menyiapkan struktur modul dan package.
- Menyiapkan tema dan navigasi dasar.
- Menyiapkan Room database.
- Membuat kerangka layar utama.
- Menghasilkan debug APK pertama.

Definition of done:

- Project berhasil dikompilasi tanpa error.
- Aplikasi dapat dibuka pada emulator atau perangkat Android.
- Navigasi dasar dapat digunakan.
- Database dapat dibuat saat aplikasi pertama kali dijalankan.

### v0.0.2 - Ingredient Management

Target:

- Membuat data kategori bahan.
- Mengisi kategori dan bahan awal dari form coffee shop.
- Menampilkan bahan berdasarkan kategori dan urutan operasional.
- Menambah, mengedit, dan menonaktifkan bahan.
- Menambah dan mengedit kategori bahan.

Definition of done:

- Semua kategori dan bahan awal tampil dengan urutan yang benar.
- Perubahan master bahan tersimpan setelah aplikasi ditutup dan dibuka kembali.
- Bahan nonaktif tidak ditawarkan untuk penggunaan baru.

### v0.0.3 - Menu and Recipe Management

Target:

- Membuat kategori menu.
- Menambah, mengedit, dan menonaktifkan menu.
- Memilih beberapa bahan untuk sebuah menu.
- Mencegah satu bahan dipilih lebih dari sekali pada menu yang sama.

Definition of done:

- Menu dapat disimpan dengan kategori dan daftar bahannya.
- Resep dapat diedit.
- Menu nonaktif tidak muncul pada closing baru.

### v0.0.4 - Daily Closing Calculation

Target:

- Membuat atau membuka closing berdasarkan tanggal.
- Menampilkan semua menu aktif dalam satu halaman berdasarkan kategori.
- Menyediakan input jumlah terjual dengan nilai awal nol.
- Menghitung Terjual/Out setiap bahan secara otomatis.
- Menampilkan hasil berdasarkan kategori bahan yang dapat di-expand/collapse.

Rumus:

```text
Total bahan = jumlah seluruh menu terjual yang menggunakan bahan tersebut
```

Definition of done:

- Hanya ada satu closing untuk setiap tanggal.
- Menu dengan jumlah nol tidak menambah hasil bahan.
- Hasil perhitungan sesuai dengan resep dan jumlah menu terjual.
- Closing dapat disimpan dan dibuka kembali.

### v0.0.5 - History and Editing

Target:

- Menampilkan daftar riwayat closing berdasarkan tanggal.
- Menampilkan detail penjualan menu dan hasil bahan.
- Mengedit closing lama.
- Menyimpan snapshot nama menu, resep, dan hasil pada closing.

Definition of done:

- Riwayat lama tidak berubah ketika resep menu saat ini diedit.
- Perubahan closing lama menghitung ulang hasil menggunakan data closing yang sesuai.
- Detail closing dapat dibuka tanpa koneksi internet.

### v0.0.6 - Export, Backup, and Restore

Target:

- Mengekspor closing menjadi file Excel `.xlsx`.
- Menyediakan sheet ringkasan bahan dan penjualan menu.
- Membuat backup seluruh master data dan riwayat.
- Memulihkan seluruh data dari file backup.
- Memvalidasi file sebelum proses restore.

Definition of done:

- File Excel dapat dibuka oleh aplikasi spreadsheet umum.
- Backup mencakup kategori, bahan, menu, resep, dan closing.
- Restore yang berhasil menghasilkan data yang sama dengan sumber backup.
- File rusak atau tidak sesuai ditolak tanpa merusak data aktif.

### v0.0.7 - Hardening and Device Testing

Target:

- Menguji alur dari pembuatan master data hingga hasil closing.
- Menambahkan empty state, validasi, pesan error, dan konfirmasi aksi penting.
- Menguji tampilan pada ukuran layar Android yang relevan.
- Menguji instalasi dan penggunaan pada HP fisik.
- Memperbaiki masalah performa dan usability yang ditemukan.

Definition of done:

- Tidak ada bug kritis pada alur utama.
- Data tetap tersedia setelah aplikasi ditutup dan perangkat dimulai ulang.
- Pengguna dapat menyelesaikan closing tanpa petunjuk teknis tambahan.

### v0.1.0 - First Stable MVP

Status: Completed on 18 August 2026.

Target:

- Menetapkan identitas final aplikasi.
- Membuat signing key dan menyimpannya dengan aman.
- Menghasilkan signed release APK.
- Menulis dokumentasi instalasi, backup, restore, dan update.
- Membuat GitHub Release pertama.

Definition of done:

- Signed APK dapat dipasang pada HP target.
- Update dengan APK baru tidak menghapus data lokal.
- Fitur dalam agreed scope telah selesai dan lolos pengujian.
- Source code, tag, release notes, dan APK release tersedia di GitHub.

## Git and Release Workflow

- Repository GitHub bersifat public.
- Nama repository yang direncanakan: `closing-count-system`.

Setiap checkpoint mengikuti proses berikut:

1. Implementasikan fitur untuk versi aktif.
2. Jalankan build dan pengujian yang relevan.
3. Perbaiki seluruh error yang menghalangi penggunaan fitur.
4. Perbarui `versionName` dan `versionCode`.
5. Perbarui roadmap atau release notes jika terdapat keputusan baru.
6. Commit perubahan dengan pesan yang menjelaskan hasilnya.
7. Buat annotated Git tag, misalnya `v0.0.2`.
8. Push branch utama dan tag ke GitHub.

Rencana pemetaan versi Android:

| Checkpoint | versionName | versionCode |
| --- | --- | ---: |
| Foundation | 0.0.1 | 1 |
| Ingredients | 0.0.2 | 2 |
| Menus and recipes | 0.0.3 | 3 |
| Closing calculation | 0.0.4 | 4 |
| History | 0.0.5 | 5 |
| Export and backup | 0.0.6 | 6 |
| Hardening | 0.0.7 | 7 |
| Stable MVP | 0.1.0 | 8 |

APK hasil build tidak dimasukkan ke commit source code. APK checkpoint atau release disimpan sebagai artifact atau lampiran GitHub Release agar ukuran repository tetap terkontrol.

## Change Control

- Perubahan scope harus ditulis dalam roadmap sebelum atau bersamaan dengan implementasinya.
- Fitur baru yang tidak diperlukan untuk menyelesaikan MVP dicatat sebagai rencana lanjutan.
- Satu checkpoint hanya ditandai selesai setelah definition of done terpenuhi.
- Riwayat data pengguna tidak boleh rusak oleh perubahan master data atau pembaruan aplikasi.
- Signing key release tidak boleh dimasukkan ke repository Git.

## Future Ideas

Fitur berikut dapat dipertimbangkan setelah `v0.1.0`:

- Pencarian menu dan bahan.
- Pengaturan urutan kategori, menu, dan bahan secara manual.
- Dark mode.
- Filter dan rentang tanggal untuk export.
- Ringkasan tren penggunaan bahan.
- Import data menu dan resep dari Excel.
- Sinkronisasi atau backup cloud opsional.
