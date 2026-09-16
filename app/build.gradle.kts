plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.projectzero.tapeamp32"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.projectzero.tapeamp32"
        minSdk = 26
        targetSdk = 34
        // v1.9 (patch): REPLAY GAIN beneran. Dulu ada toggle "ReplayGain" di kategori
        // AUDIO & EFFECTS (Settings) tapi ternyata tidak pernah benar-benar mengubah
        // suara -- dihapus di v1.4.1 bersama toggle palsu lainnya. Sekarang pipeline
        // audio bit-perfect sudah cukup matang (lihat v1.6/v1.8) untuk diukur & dipakai
        // secara jujur: toggle baru "REPLAY GAIN" di tab BATAS (Equalizer) mengukur RMS
        // sample PCM ASLI yang mengalir lewat ParametricEqAudioProcessor saat sebuah
        // lagu pertama kali diputar, menyimpan gain (dB) yang disarankan per lagu, lalu
        // menerapkannya otomatis di pemutaran berikutnya supaya level antar lagu lebih
        // rata. Nonaktif otomatis saat BIT-PERFECT MODE menyala (bit-perfect tidak boleh
        // mengubah sample sama sekali). Lihat changelog di dalam app (Settings > System
        // > Changelog) untuk detail & batasan jujur fitur ini.
        //
        // v1.8.1 (patch): fix akar masalah "toggle BATAS gak seragam" yang sebenarnya
        // -- submenu BATAS TIDAK bisa discroll, jadi di layar/jendela pendek kartu
        // paling bawah (AUTO EQ PER LAGU) ke-CLIP oleh batas panel (deskripsi hilang,
        // rocker switch kepotong) sehingga TERLIHAT beda ukuran padahal komponennya
        // sudah identik. Sekarang submenu BATAS bisa digulir penuh. Lihat changelog
        // di dalam app (Settings > System > Changelog) untuk detail lengkap.
        //
        // v2.0 (patch): Backup & Restore SEMUA pengaturan sekaligus (Settings >
        // System > Backup & Restore) -- bukan cuma preset EQ yang sudah bisa
        // di-export/import sejak v1.2. Sekalian fix "App Version" di Settings yang
        // hardcode salah ke "1.5" sejak v1.5, dan fix entri changelog v1.9 yang
        // sebelumnya cuma ada di TapeAmp32_changelog.html tapi tidak pernah
        // ditambahkan ke Changelog di dalam app -- lihat changelog di dalam app
        // (Settings > System > Changelog) untuk detail lengkap.
        //
        // v2.2 (patch "Crossfade"): CROSSFADE beneran di tab BATAS (Equalizer) --
        // key FADE_SECONDS sudah ada sejak lama tapi yatim, sekarang tersambung
        // penuh ke DSP lewat jalur playback kedua untuk overlap fade-out/fade-in.
        // Kontrol tombol headset/Bluetooth (AVRCP) diverifikasi ulang, sudah
        // berfungsi lewat MediaSession yang ada -- lihat changelog di dalam app
        // untuk detail lengkap.
        // v2.3 (patch "Widget Home Screen"): kontrol player minimal (prev/play-
        // pause/next + judul & artis lagu) langsung dari home screen tanpa buka
        // app. Pakai AppWidgetProvider + RemoteViews klasik (tidak nambah
        // dependency baru), tombolnya memicu broadcast ACTION_MEDIA_BUTTON yang
        // sudah ditangkap MediaButtonReceiver -- jalur yang sama dengan kontrol
        // statusbar/lockscreen yang sudah ada. Lihat changelog di dalam app untuk
        // detail lengkap & batasan yang jujur diakui.
        // v2.4 (patch "cassette side A/B"): double tap bodi kaset untuk pindah
        // Side A (Library) <-> Side B (Stream), stream terakhir di-resume otomatis,
        // label A/B ikut string resource ~10 bahasa. Lihat changelog di dalam app
        // untuk detail lengkap.
        versionCode = 17
        versionName = "2.6"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // BARU (v1.2): SplashScreen API resmi AndroidX -- dipakai supaya layar pembuka
    // konsisten memakai background/tema gelap TapeAmp32 di SEMUA versi Android
    // (termasuk 12+ yang sebelumnya menimpa dengan splash sistem polos putih
    // sebelum window activity yang sudah gelap sempat tampil).
    implementation("androidx.core:core-splashscreen:1.0.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Media3 / ExoPlayer - handles MP3, AAC, M4A, OGG, Opus, FLAC(24bit/hi-res), WAV natively.
    implementation("androidx.media3:media3-exoplayer:1.4.0")
    // Wajib untuk memutar stream .m3u8 (HLS) di StreamingScreen - tanpa ini, memilih
    // stasiun radio akan force close (IllegalStateException di main thread).
    implementation("androidx.media3:media3-exoplayer-hls:1.4.0")
    implementation("androidx.media3:media3-session:1.4.0")
    implementation("androidx.media3:media3-common:1.4.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.4.0")
    implementation("androidx.media3:media3-ui:1.4.0")
    // FIX (bug "bitrate selalu 0"): dideklarasikan eksplisit supaya IcyHeaders
    // (dipakai PlayerManager utk baca "icy-br" dari server radio Shoutcast/Icecast)
    // pasti ada di classpath -- sebelumnya cuma numpang transitif lewat media3-exoplayer.
    implementation("androidx.media3:media3-extractor:1.4.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Settings persistence
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // JSON parsing for Poweramp presets
    implementation("org.json:json:20240303")

    // BARU (fitur "Jelajahi Radio"): buat nampilin logo/favicon stasiun dari
    // Radio Browser API (URL gambar remote) di list hasil pencarian --
    // project ini sebelumnya tidak punya library image-loading sama sekali
    // (album art lokal dibaca langsung dari file lewat MediaMetadataRetriever,
    // bukan lewat URL remote). Coil dipilih karena ringan & standar buat
    // Compose (AsyncImage), bukan Glide (lebih berat, ditujukan buat View
    // system lama).
    implementation("io.coil-kt:coil-compose:2.6.0")

    // FIX (error "Unresolved reference 'junit'" di app/src/test/...): file test
    // (LyricsParserTest.kt, ShuffleQueueTest.kt, StringResourceParityTest.kt)
    // sudah ada di project, tapi dependency JUnit-nya sendiri belum pernah
    // ditambahkan ke sini -- makanya import org.junit.* di file test itu tidak
    // pernah bisa di-resolve. testImplementation (bukan implementation biasa)
    // artinya library ini CUMA tersedia buat kode di app/src/test/, tidak ikut
    // ke APK rilis sama sekali.
    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
