# Closing Count — panduan reviewer demo lokal

Data seluruhnya fiktif. Panduan ini memakai service DEMO terisolasi, bukan
database outlet operasional. Bukti perjalanan native tersedia pada
[baseline sembilan fase](DEMO_NATIVE_R1.md). [Panduan APK/paket lokal](DEMO_DISTRIBUTION_R1.md)
dan [pemetaan acceptance lokal](DEMO_LOCAL_ACCEPTANCE_R1.md) menjelaskan kandidat
serta batas perangkat/publik yang masih terbuka.

## Sebelum mencoba

Operator mengikuti [panduan menjalankan demo](DEMO_LOCAL.md), menggunakan private
state directory khusus demo dan menyiapkan dua instalasi Android uji terpisah.
Gunakan APK **Closing Count Demo** dengan paket `com.closingcount.app.demo`
pada emulator uji kosong. Endpoint emulator lokal adalah `http://10.0.2.2:4372`;
ini bukan URL publik atau endpoint HP melalui Wi-Fi.

Operator memperoleh akun melalui perintah `access` secara privat. Jangan
memasukkan password, session token, private state directory atau layar kredensial
ke rekaman, Git atau paket yang dibagikan. Rani dan Dimas adalah karyawan, Arif
supervisor, Dewi administrator. Akun pribadi membuat pencatat perubahan jelas.

Periksa tanggal bisnis yang ditampilkan. Fixture memakai Asia/Jakarta dengan
cutoff 04:00; anchor tanggal ditetapkan saat generasi demo dibuat. Gunakan anchor
tersebut untuk skenario ini, bukan mengasumsikan tanggal kalender saat menonton.

## Walkthrough utama

1. Masuk sebagai Rani pada instalasi A. Buka closing pada **Ringkasan**, lalu
   **Input**. Fixture baru berisi Cappuccino 3 dan Latte 5, total 8 porsi.
   Angka adalah total penjualan outlet per menu, bukan tambahan per karyawan.
2. Ubah Cappuccino menjadi 4, tekan **Simpan draft**, lalu tunggu konfirmasi
   sinkronisasi. Hasil server adalah 4/5. Mengetik saja belum menyimpan draft.
3. Pada instalasi B, masuk sebagai Dimas dan buka closing yang sama. Ubah Latte
   menjadi 6, simpan dan tunggu konfirmasi. Muat kembali di A: hasil 4/6,
   perubahan menu berbeda tergabung dan masing-masing pencatat tetap terlihat.
4. Buka **Review** untuk melihat jumlah server dan pemakaian bahan. Saat semua
   bahan resep terkait, satu porsi menghitung satu penggunaan setiap bahan yang
   terdaftar. Ini bukan perhitungan gram/ml, stok, HPP atau pembukuan.
5. Selesaikan draft dan konflik, lalu ajukan closing untuk diperiksa. Masuk
   sebagai Arif, periksa angka dan pencatat, lalu kunci. Karyawan tidak dapat
   mengedit closing yang sudah diajukan/dikunci melalui input biasa.
6. Pada **Riwayat**, buka laporan generasi yang dikunci. Ekspor Excel melalui
   pemilih file Android dan periksa angka yang sama. Fixture juga menyediakan
   laporan hari sebelumnya 6/4, total 10 porsi, sebagai contoh historis.
7. Untuk koreksi, supervisor membuka kembali closing dengan alasan. Edit baru
   menghasilkan generasi kerja berikutnya; laporan generasi terkunci sebelumnya
   tetap menggunakan angka dan resep historisnya.

Jangan menjumlahkan baseline dengan total yang baru diketik. Jika memakai fixture
yang sudah pernah diedit, hasil awal bisa berbeda; minta operator memeriksa status
atau membuat generasi baru melalui `reset`, bukan menghapus database.

## Skenario offline dan konflik

Siapkan kedua instalasi pada closing DRAFT yang sama dan telah dimuat. Biarkan A
tidak aktif tanpa memuat ulang, lalu B menyimpan angka baru pada menu yang akan
diuji. Setelah B terkonfirmasi, operator menghentikan service DEMO dengan
perintah `stop`. A dibuka saat service masih berhenti, mengubah menu tadi dan
menyimpan draft; tampilkan status belum terkirim. Tutup/buka kembali aplikasi
untuk memeriksa draft tetap ada. Ini gangguan service lokal, bukan bukti kondisi
radio/jaringan HP fisik.

Setelah `start`, sinkronkan A. Karena revisi lokal A mendahului perubahan B,
A harus menampilkan angka server dan draft
beserta pilihan penyelesaian; tidak menimpa diam-diam. Pilih angka secara sengaja,
simpan, lalu muat kembali dari B. Periksa hasil dan pencatat di kedua instalasi.
Simpan pekerjaan sebelum navigasi dan tunggu statusnya; draft lokal dan angka
server dapat berbeda selama ada pekerjaan pending.

## Reset dan pemulihan

Reset adalah tindakan operator lokal. Perintah `reset` membuat akun/outlet dan
generasi fixture baru, mengarsipkan generasi lama serta mencabut akses/sesi lama.
Perintah itu tidak menghapus volume, laporan, audit atau draft Android.

Jika perangkat lama masih menyimpan pekerjaan pending, tinjau/cadangkan dalam
konteks akun asal sebelum beralih. Jangan menganggap draft lama otomatis terkirim
sebagai pekerjaan akun baru. Gunakan akun generasi baru untuk baseline baru.
Backup histori lokal lama, backup pekerjaan online pending dan laporan Excel
memiliki tujuan berbeda; restore histori lokal tidak menimpa closing server.

## Selesai dan batas bukti

Keluar akun pada instalasi uji, lalu operator menjalankan `stop` dengan private
state directory yang sama. Data demo dipertahankan untuk pemeriksaan berikutnya.
Jika port dipakai proses lain atau ownership tidak cocok, ikuti pesan penolakan;
jangan mematikan proses atau menghapus volume secara acak.

Baseline native membuktikan alur dua instalasi, offline/konflik, supervisor,
Excel, perubahan katalog dan reset dengan cadangan draft. Pengujian HP fisik
PF-03, pemilihan layanan/budget PF-04 dan deployment/HTTPS/APK publik PF-05 belum
ditutup oleh bukti emulator. Kontribusi/bantuan AI serta maturity dijelaskan
pada studi kasus akhir; klaim Production tidak dibuat dari demo lokal ini.
