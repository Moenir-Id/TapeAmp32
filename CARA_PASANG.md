# CARA PASANG — Patch Multi-Bahasa (ID/EN) + Sub-Menu System

## Isi patch ini

```
TapeAmp 32/
├── app/src/main/java/com/projectzero/tapeamp32/
│   ├── MainActivity.kt                          (DIUBAH)
│   ├── audio/PlaybackService.kt                 (DIUBAH)
│   ├── data/LocaleManager.kt                    (BARU)
│   ├── ui/components/NavRail.kt                  (DIUBAH)
│   ├── ui/components/TransportControls.kt        (DIUBAH)
│   ├── ui/components/VuMeter.kt                  (DIUBAH)
│   ├── ui/components/VfdStatusPanel.kt           (DIUBAH)
│   ├── ui/components/CassetteDeck.kt             (DIUBAH)
│   ├── ui/screens/SettingsScreen.kt              (DIUBAH — besar)
│   ├── ui/screens/LyricsScreen.kt                (DIUBAH)
│   ├── ui/screens/ImportScreen.kt                (DIUBAH)
│   ├── ui/screens/PlayerScreen.kt                (DIUBAH)
│   ├── ui/screens/StreamingScreen.kt             (DIUBAH)
│   ├── ui/screens/LibraryScreen.kt               (DIUBAH — besar)
│   ├── ui/screens/EqualizerScreen.kt             (DIUBAH — besar)
│   └── widget/PlayerWidgetProvider.kt           (DIUBAH)
└── app/src/main/res/
    ├── values/strings.xml                       (DIUBAH — jadi default/English)
    └── values-in/strings.xml                    (BARU — Indonesia)
```

Timpa (overwrite) 18 file di atas ke lokasi yang sama persis di project kamu.
Tidak ada file lain yang perlu disentuh — tidak ada perubahan `build.gradle.kts`
atau `AndroidManifest.xml`.

**Patch ini KUMULATIF dan MENUNTASKAN SELURUH SCOPE MULTI-BAHASA** — semua 8
layar (Settings, Lyrics, Import, Player + semua komponennya, Streaming,
Library, Equalizer) + NavRail + infrastruktur bahasa sudah 100% ID/EN. Kalau
sudah pasang patch nomor sebelumnya, cukup timpa lagi dengan isi patch ini.

Label seperti REW/PLAY/PAUSE/LEFT/RIGHT/istilah VFD dan label cetak kaset
tetap dibiarkan sama di kedua bahasa (istilah universal hardware audio fisik),
begitu juga isi historis dialog Changelog dan nama pilihan Theme Accent Color.

## Apa yang berubah

1. **Bahasa aplikasi bisa diganti dari dalam app** (Settings → System → Language),
   terpisah dari bahasa sistem HP: Ikuti Sistem / Bahasa Indonesia / English.
   Ganti bahasa akan me-restart aplikasi otomatis supaya semua layar langsung
   ikut berubah.
2. **Settings → System sekarang berupa sub-menu**, bukan satu halaman panjang:
   - Playback & Screen (Keep Screen Awake)
   - Sleep Timer
   - Notifications (Statusbar & Lock Screen)
   - Language *(baru)*
   - About (Versi, Changelog)

   Tap salah satu masuk ke sub-halamannya sendiri; ada tombol back di atas.
3. Notifikasi media & widget home-screen (teks default/fallback-nya) juga ikut
   bahasa yang dipilih, bukan cuma bahasa sistem HP.

## Yang BELUM tercakup di patch ini

Tidak ada lagi layar yang tersisa dari scope aslinya — semua 8 layar sudah
selesai. Yang masih layak dicatat:

- Isi historis dialog Changelog (versi lama) sengaja Indonesia-only — nilai
  terjemahannya rendah dibanding effort-nya
- Nama pilihan Theme Accent Color ("Gold Retro"/"Neon 80s"/"Silver Hi-Fi")
  sengaja tidak diterjemahkan — dipakai sebagai key pencocokan warna tema di
  kode lain, menerjemahkannya akan merusak fitur ganti warna
- Berbagai istilah universal hardware audio (REW/PLAY/PAUSE/F.FWD, LEFT/RIGHT,
  label VFD, label cetak kaset "TYPE II CHROME BIAS"/"D90"/"NR.") sengaja
  dibiarkan sama di kedua bahasa, meniru perangkat fisik aslinya — tapi tetap
  disalurkan lewat `strings.xml`, bukan hardcode, jadi bisa diedit kapan saja
  kalau suatu saat mau diterjemahkan juga
- `contentDescription` di layout widget (`widget_player.xml`) masih ikut
  bahasa sistem HP seperti biasa (dampaknya kecil, cuma dibaca screen reader)

## Saran uji coba

Karena saya tidak punya akses Gradle/Android SDK untuk compile sungguhan
(sudah dijelaskan di catatan build sebelumnya, tetap berlaku), sebelum rilis
sebaiknya build & jalankan dulu di Android Studio, cek terutama:
- Ganti bahasa di Settings → System → Language ketiga pilihan (Ikuti Sistem/
  Indonesia/English) benar-benar mengubah SEMUA layar setelah restart
- Buka tiap tab (Player, Equalizer — termasuk sub-tab EQU/NADA/VOCAL/STEREO/
  BATAS, Library — termasuk semua dialog, Import, Streaming, Lyrics, Settings)
  di kedua bahasa, pastikan tidak ada teks yang kepotong/overflow karena teks
  Inggris vs Indonesia panjangnya beda
- Isi teks di dalam dialog Changelog (riwayat versi lama) — sengaja dibiarkan
  Indonesia-only, nilai terjemahannya rendah dibanding effort-nya
- `contentDescription` di layout widget (`widget_player.xml`) — ikut bahasa
  sistem HP seperti biasa (dampaknya kecil, cuma dibaca screen reader)
- Nama pilihan Theme Accent Color ("Gold Retro"/"Neon 80s"/"Silver Hi-Fi")
  SENGAJA tidak diterjemahkan karena dipakai sebagai key pencocokan warna tema
  di kode lain, bukan cuma label tampilan — menerjemahkannya akan merusak
  fitur ganti warna tema.

## Kenapa bukan pakai `AppCompatDelegate.setApplicationLocales()` bawaan AndroidX

Dokumentasi resmi Android mewajibkan `AppCompatActivity` kalau dipakai bareng
Compose — yang berarti juga wajib ganti tema dari
`android:Theme.Material.NoActionBar` (dipakai project ini) ke turunan
`Theme.AppCompat`, kalau tidak app akan crash saat start. Karena TapeAmp 32
100% Jetpack Compose (tidak ada View/AppCompat widget lain), perubahan tema
sebesar itu terlalu berisiko untuk fitur ini. Sebagai gantinya dipakai pola
klasik `attachBaseContext()` (dijelaskan lengkap di komentar
`LocaleManager.kt`) — jalan dari minSdk 26, tanpa dependency baru, tanpa ubah
tema.

## Catatan build

Saya tidak punya akses Gradle/Android SDK di lingkungan kerja ini untuk
compile sungguhan, jadi patch ini sudah saya cek manual sedetail mungkin
(validasi XML, kecocokan setiap `R.string.xxx` yang dipakai vs yang
didefinisikan di kedua `strings.xml`, kesetimbangan kurung). Tetap disarankan
build & coba jalan dulu di Android Studio sebelum rilis, terutama untuk
memastikan:
- Layar Settings tampil normal di kedua bahasa
- Ganti bahasa di System → Language benar-benar me-restart & mengubah semua
  teks di Settings, notifikasi, dan widget
- Sub-menu System bisa masuk/keluar dengan tombol back
