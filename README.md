# Ruang Putar

Pemutar musik Windows yang **ringan**: cari, putar, dan simpan lagu dari YouTube tanpa iklan dan tanpa akun.
Dibuat oleh **@SuS_X_x**. Saudara dari **Ruang Putar Musik** (Android) — mesinnya sama.

## Fitur
- Cari lagu (*Lagu* / *Semua video*), halaman ◀ ▶, filter durasi minimal
- Beranda: rak lagu + filter durasi, "Sering kamu putar", rak dari penyanyi favorit
- Pustaka: Favorit, Riwayat, Offline, Penyanyi yang diikuti
- Antrean (naik/turun/hapus), acak, ulang, timer tidur
- Unduh ke Offline → folder `Music\RuangPutar` (M4A/Opus, tanpa konversi)
- Cache lagu (putar ulang tanpa internet), batas kecepatan unduh (cocok sambil main game)
- Tema Batik / Gelap / Terang, ikon di tray, kontrol keyboard
- Pembaruan dari dalam aplikasi, termasuk **mesin YouTube** (tanpa pasang ulang)

### Keyboard
| Tombol | Fungsi |
|---|---|
| Spasi | Putar / jeda |
| ← → | Mundur / maju 5 detik |
| ↑ ↓ | Volume |
| Ctrl+F | Cari |

## Unduh & pasang
1. Buka **[Releases](../../releases/latest)** → unduh `RuangPutar-…-setup.exe`.
2. Jalankan → Berikutnya → Pasang. Tidak perlu hak admin dan tidak perlu memasang Java.
3. Kalau muncul "Windows protected your PC": **More info → Run anyway** (installer belum ditandatangani).

Sudah memakai **Ruang Putar versi lama** (mesin yt-dlp)? Cukup pasang di atasnya — versi lama otomatis
digantikan, dan favorit, riwayat, rak, serta penyanyi yang diikuti ikut dipindahkan.

## Masukan & lapor bug
Gabung server Discord **Bengkel SuS**: https://discord.gg/VRqw9EGUpb
- 💡 Ide fitur → `#masukan`
- 🐞 Lapor bug → `#lapor-bug` (sertakan versi dari **Pengaturan → Tentang** + screenshot)

## Catatan
Untuk pemakaian pribadi. Aplikasi ini tidak berafiliasi dengan YouTube atau Google.

## Lisensi pihak ketiga
[NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) (GPL-3.0),
[mpv / libmpv](https://mpv.io) (LGPL-2.1+), Compose Multiplatform & OkHttp (Apache-2.0), JNA (Apache-2.0).
