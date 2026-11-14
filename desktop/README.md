# TapeAmp 32 — Versi Windows (Desktop)

Versi PC dari TapeAmp 32. **Proyek Gradle terpisah** (`desktop/`, sejajar dengan `app/`).
Isi `app/` TIDAK diubah sama sekali.

Semua layar ada: Player, Equalizer, Library, Import, Lyrics, Settings, Streaming (radio).
UI, PlayerViewModel, SettingsRepository, dan DSP EQ memakai file ASLI dari `app/`
(disalin otomatis saat build, import Android diganti ke "shim" desktop). Jadi kalau kamu
ubah UI di `app/`, build ulang desktop dan perubahannya ikut.

## Cara menjalankan
1. Folder `desktop/` sudah berada di root repo `TapeAmp32/` (sejajar `app/`); jangan dipindah, karena build membaca `../app`.
2. Buka folder `desktop/` (BUKAN root repo) di IntelliJ IDEA, JDK 17+.
3. Wrapper Gradle 8.14.3 sudah termasuk (gradlew.bat). Pakai JDK 17-21 (JDK 25 belum didukung Gradle 8.14).
4. Jalankan: `gradlew.bat run`
5. EXE langsung (tanpa instal apa-apa): `gradlew.bat createDistributable` -> `build/compose/binaries/main/app/TapeAmp32/TapeAmp32.exe`.
   Installer EXE: instal WiX Toolset 3.14 dulu, lalu `gradlew.bat packageExe` (hasil di `build/compose/binaries`).

Build pertama mengunduh ±100–200 MB (Compose + FFmpeg native Windows).
"Open with" Windows: argumen file audio langsung diputar.

## Pintasan keyboard
Pintasan hanya aktif saat jendela TapeAmp 32 yang sedang fokus.

**Pemutaran**

| Tombol | Fungsi |
|---|---|
| Spasi | Play / Pause |
| F1 / F2 | Lagu sebelumnya / berikutnya |
| Panah Atas / Bawah | Volume +5% / -5% (slider volume di panel kiri ikut naik-turun) |
| Panah Kiri / Kanan | Mundur / maju 5 detik |
| Enter | Buka Streaming (radio) |
| F11 | Fullscreen |
| Shift (ditekan lalu dilepas sendirian) | Tukar sisi kaset: Library <-> Radio/Stream |

**Pindah menu: Alt + huruf**

| Tombol | Tujuan |
|---|---|
| Alt+D | Player (**D**eck) |
| Alt+E | **E**qualizer |
| Alt+I | **I**mport |
| Alt+Y | L**y**rics |
| Alt+S | **S**ettings |
| Alt+R | **R**adio / Streaming |
| Alt+L | **L**ibrary, tab Songs |
| Alt+A | Library, tab **A**lbums |
| Alt+T | Library, tab Ar**t**ists |
| Alt+F | Library, tab **F**olders |
| Alt+P | Library, tab **P**laylists |

**Cari lagu**
- Di layar Player atau Library, langsung ketik huruf apa saja: pindah ke Library dan huruf masuk ke kolom pencarian.
- Esc atau Enter keluar dari kolom pencarian (Spasi kembali jadi Play/Pause).
- Karena huruf polos dipakai untuk mencari, pindah menu memakai Alt.

## Struktur
- `build.gradle.kts` — task `syncShared` (salin + tulis ulang import), `generateR` (R.string dari strings.xml), `generateBuildConfig` (versi dari app/build.gradle.kts).
- `src/main/kotlin/.../desktop/shim` — pengganti API Android (Context, Uri, Intent, Build, Log, stringResource, AsyncImage, Media3 AudioProcessor, DataStore).
- `.../desktop/audio` — mesin audio: FFmpeg (JavaCV) → ParametricEq (DSP yang sama dengan Android) → javax.sound.
- `.../audio`, `.../data`, `.../util` — versi desktop dari PlayerManager, MusicRepository, WaveformExtractor, LocaleManager, LyricsUtils, ShareCardRenderer.
- `.../desktop/Main.kt` — jendela, dialog file, navigasi.
- Data pengguna: `%APPDATA%\TapeAmp32`.

## Cara termudah (Windows)
Klik dua kali **`build-exe.bat`** (membuat installer EXE) atau **`run.bat`** (menjalankan langsung).
Skrip ini otomatis memilih JDK 24/21 yang terpasang, jadi tidak kena error "25.0.x" dari JDK 25.
Hasil installer: `build\compose\binaries\main\exe`.

## Kalau build gagal
- Pesan `25.0.4` atau "Incompatible Gradle JVM version": Gradle 8.14 tidak bisa jalan di JDK 25. Pakai `.bat` di atas, atau pilih JDK 21/24 di Settings > Build Tools > Gradle.
- "non-Gradle Java modules and Android-Gradle modules": buka folder `desktop/` sebagai proyek **terpisah** di IDE (File > Open > pilih `desktop` > New Window), jangan dari jendela root `TapeAmp32` yang berisi modul Android.
- Folder `build/`, `.gradle/`, `.kotlin/` boleh dihapus kapan saja (ukurannya ratusan MB) dan tidak ikut ke GitHub. Installer `.exe` jangan di-commit; pakai GitHub Releases.

## Catatan
Kode desktop dikembangkan dengan bantuan AI dan diuji manual di Windows 10. Kalau setelah update
build menampilkan error kecil (versi dependency, import, tipe), laporkan lewat Issues.

## Belum ada / berbeda dari Android
Crossfade, efek scrub kaset, USB DAC, notifikasi/tray/SMTC Windows, widget, keep-screen-on,
dan share via Intent (diabaikan). Minimize ke tray dan pintasan global (saat jendela tidak fokus) sengaja tidak ada. Pilih folder musik memakai dialog folder biasa.
