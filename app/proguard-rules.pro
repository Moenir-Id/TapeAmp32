# =====================================================================
# TapeAmp32 -- aturan R8/ProGuard untuk build release (v2.7)
#
# Dipakai karena `isMinifyEnabled = true` di app/build.gradle.kts.
# Tujuannya: memperkecil APK TANPA merusak hal-hal yang dipanggil lewat
# nama/refleksi (bukan lewat kode Kotlin langsung), yang tidak bisa
# dilacak R8 sendiri.
# =====================================================================

# --- Baris nomor untuk stacktrace crash -------------------------------
# Tanpa ini, laporan crash dari HP jadi tidak terbaca sama sekali.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Komponen yang dipanggil sistem lewat nama di AndroidManifest -----
# MainActivity, PlaybackService, dan PlayerWidgetProvider di-instantiate
# Android lewat string nama kelas -> tidak boleh di-rename.
-keep class com.projectzero.tapeamp32.MainActivity { *; }
-keep class com.projectzero.tapeamp32.audio.PlaybackService { *; }
-keep class com.projectzero.tapeamp32.widget.PlayerWidgetProvider { *; }

# --- Media3 / ExoPlayer ------------------------------------------------
# Media3 memuat sebagian extractor, renderer, dan AudioProcessor secara
# dinamis (Class.forName) tergantung format file yang diputar. Kalau
# di-strip, gejalanya: format tertentu (FLAC/HLS) mendadak gagal diputar
# HANYA di build release, sementara debug baik-baik saja.
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# AudioProcessor custom milik app ini dipasang ke pipeline Media3.
-keep class com.projectzero.tapeamp32.audio.ParametricEqAudioProcessor { *; }

# --- OkHttp / Okio (dipakai datasource stream & Radio Browser API) ----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- org.json (parser preset Poweramp & file backup) ------------------
-dontwarn org.json.**

# --- Coil (logo stasiun radio) ----------------------------------------
-dontwarn coil.**

# --- Kotlin coroutines -------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# --- Model data yang diserialisasi ke JSON (backup & preset EQ) -------
# Nama field-nya jadi KEY di file backup/preset. Kalau R8 me-rename
# field, file backup lama tidak bisa di-restore lagi setelah update.
-keep class com.projectzero.tapeamp32.data.EqPreset { *; }
-keep class com.projectzero.tapeamp32.data.Song { *; }
-keep class com.projectzero.tapeamp32.data.Playlist { *; }

# --- Compose ----------------------------------------------------------
# Compose punya rules bawaan yang cukup; ini cuma meredam warning
# dari tooling preview yang tidak ikut ke release.
-dontwarn androidx.compose.ui.tooling.**
