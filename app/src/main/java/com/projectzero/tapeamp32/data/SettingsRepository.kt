package com.projectzero.tapeamp32.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "tapeamp32_settings")

object SettingsKeys {
    val TAPE_HISS = booleanPreferencesKey("tape_hiss")
    val BUTTON_CLICK = booleanPreferencesKey("button_click")
    val WOW_FLUTTER = floatPreferencesKey("wow_flutter")
    val FADE_SECONDS = floatPreferencesKey("fade_seconds")

    // BARU (patch "Crossfade"): dulu FADE_SECONDS di atas cuma key yatim -- tidak
    // pernah dibaca/ditulis siapa pun (sisa dari kategori PLAYBACK lama yang
    // dihapus di v1.4.1 karena tidak pernah benar-benar menyambung ke DSP).
    // Sekarang beneran dipakai: CROSSFADE_ENABLED = toggle master di tab BATAS
    // (Equalizer), FADE_SECONDS = durasi overlap-nya (detik), dipersist sama
    // seperti BIT_PERFECT_MODE/PEAK_LIMITER di atas.
    val CROSSFADE_ENABLED = booleanPreferencesKey("crossfade_enabled")
    val AUTO_PAUSE = booleanPreferencesKey("auto_pause")
    val PEAK_LIMITER = booleanPreferencesKey("peak_limiter")
    val DSD_MODE = stringPreferencesKey("dsd_mode")
    val CASSETTE_SKIN_MODE = stringPreferencesKey("cassette_skin_mode") // "random" atau ID skin spesifik
    val REEL_SPEED = floatPreferencesKey("reel_speed")
    val STREAM_BUFFER_SECONDS = floatPreferencesKey("stream_buffer_seconds")
    val SUBSONIC_URL = stringPreferencesKey("subsonic_url")
    val SUBSONIC_USER = stringPreferencesKey("subsonic_user")
    val SLEEP_TIMER_MIN = floatPreferencesKey("sleep_timer_min")
    val CUSTOM_EQ_PRESETS = stringPreferencesKey("custom_eq_presets_json")
    val ACTIVE_EQ_PRESET_NAME = stringPreferencesKey("active_eq_preset_name")
    val MUSIC_FOLDER_URI = stringPreferencesKey("music_folder_uri")
    val AUTO_SCAN_ON_STARTUP = booleanPreferencesKey("auto_scan_on_startup")

    // BARU (patch "cache library cepat"): hasil scan folder/MediaStore TERAKHIR
    // (JSON array, format sama seperti MusicRepository.serializeSongs/deserializeSongs)
    // disimpan di sini supaya saat app dibuka lagi, daftar lagu bisa langsung ditampilkan
    // INSTAN dari cache ini dulu -- tanpa nunggu SAF traversal + MediaMetadataRetriever
    // per file selesai lagi. Scan folder yang sebenarnya tetap jalan di background
    // sesudahnya untuk menangkap lagu baru/terhapus, lalu diam-diam update cache ini
    // & tampilan kalau ada perubahan (lihat PlayerViewModel.init{} & syncQueueWithLibrary).
    val LIBRARY_CACHE_JSON = stringPreferencesKey("library_cache_json")
    // Lagu terakhir yang diputar (JSON satu objek Song) & posisi playback terakhir (ms).
    // Dipakai untuk memulihkan tampilan player + posisi kaset saat app dibuka ulang.
    val LAST_SONG_JSON = stringPreferencesKey("last_song_json")
    val LAST_POSITION_MS = longPreferencesKey("last_position_ms")

    // BARU (patch "cassette side A/B"): stream terakhir yang diputar (URL + judul/nama
    // stasiun), dipakai supaya double-tap kaset ke Side B tahu stream mana yang harus
    // di-resume, persis seperti LAST_SONG_JSON dipakai untuk memulihkan Side A.
    val LAST_STREAM_URL = stringPreferencesKey("last_stream_url")
    val LAST_STREAM_TITLE = stringPreferencesKey("last_stream_title")

    // BARU (patch "DSP control knobs"): Vocal enhancer + trio kontrol image stereo
    // (Balance, Stereo Expansion, Mono/Stereo), dipersist sama seperti limiterOn/
    // eqBypassOn supaya posisi knop tidak reset ke default tiap app dibuka ulang.
    val VOCAL_ENABLED = booleanPreferencesKey("vocal_enabled")
    val VOCAL_BASS_DB = floatPreferencesKey("vocal_bass_db")
    val VOCAL_TREBLE_DB = floatPreferencesKey("vocal_treble_db")
    val STEREO_BALANCE = floatPreferencesKey("stereo_balance")
    val STEREO_EXPANSION = floatPreferencesKey("stereo_expansion")
    val MONO_STEREO_ON = booleanPreferencesKey("mono_stereo_on")

    // BARU (patch "headroom slider"): rasio auto-preamp/headroom management di
    // ParametricEqAudioProcessor (dulu `private val HEADROOM_SAFETY_RATIO` HARDCODE
    // 0.5, tidak dipersist & tidak ada UI-nya sama sekali). Dipersist sama persis
    // seperti STEREO_EXPANSION/VOCAL_BASS_DB di atas: 0.0 = full loudness (andalkan
    // limiter sepenuhnya), 1.0 = paling konservatif/aman (perilaku lama sebelum
    // patch ini). Key & default (0.3) lihat headroomSafetyRatio flow di bawah.
    val HEADROOM_SAFETY_RATIO = floatPreferencesKey("headroom_safety_ratio")

    // BARU (v1.2): fitur playlist -- seluruh playlist pengguna disimpan sebagai satu
    // JSON array (lihat PlaylistUtils), sama seperti CUSTOM_EQ_PRESETS di atas.
    val PLAYLISTS_JSON = stringPreferencesKey("playlists_json")

    // BARU (v1.4): sebelumnya SEMUA key di bawah ini tidak ada -- toggle/dropdown
    // terkait di SettingsScreen cuma disimpan ke `var ... by remember { ... }` lokal
    // layar, jadi kembali ke default diam-diam setiap kali user pindah layar atau
    // menutup app. Sekarang dipersist sama seperti pengaturan Audio & Library lain.
    val THEME_ACCENT = stringPreferencesKey("theme_accent")
    val COMPACT_VU = booleanPreferencesKey("compact_vu")
    val REEL_ANIMATION = booleanPreferencesKey("reel_animation")
    val GAPLESS_PLAYBACK = booleanPreferencesKey("gapless_playback")
    val REPLAY_GAIN = booleanPreferencesKey("replay_gain")
    val AUDIO_ENGINE = stringPreferencesKey("audio_engine")
    val IGNORE_SHORT_TRACKS = booleanPreferencesKey("ignore_short_tracks")
    val KEEP_SCREEN_AWAKE = booleanPreferencesKey("keep_screen_awake")
    val HIGH_PERFORMANCE_DSP = booleanPreferencesKey("high_performance_dsp")

    // BARU (v1.6): toggle "BIT-PERFECT MODE" di tab BATAS (Equalizer) -- dipersist
    // sama seperti PEAK_LIMITER/MONO_STEREO_ON di atas.
    val BIT_PERFECT_MODE = booleanPreferencesKey("bit_perfect_mode")

    // BARU (v1.7): EQ per-lagu otomatis -- AUTO_EQ_PER_SONG adalah toggle master
    // (tab BATAS), SONG_EQ_PRESET_MAP adalah hasil "ingatan" preset EQ per lagu
    // (JSON object: key = path lagu / "judul|artis" kalau path kosong, value = nama
    // preset), disimpan sebagai satu string JSON persis seperti CUSTOM_EQ_PRESETS
    // dan PLAYLISTS_JSON di atas.
    val AUTO_EQ_PER_SONG = booleanPreferencesKey("auto_eq_per_song")
    val SONG_EQ_PRESET_MAP = stringPreferencesKey("song_eq_preset_map_json")

    // BARU (v1.9): REPLAY GAIN beneran. REPLAY_GAIN di atas (BARU v1.4, sudah ada
    // sejak lama sebagai toggle "hantu" yang tidak pernah dibaca/dipakai siapa pun
    // sejak dihapus dari Settings di v1.4.1) sekarang benar-benar dipakai lagi
    // sebagai toggle master di tab BATAS (Equalizer). REPLAY_GAIN_MAP adalah
    // "ingatan" gain (dB) hasil pengukuran per lagu -- JSON object (key = path lagu/
    // "judul|artis", sama seperti SONG_EQ_PRESET_MAP di atas; value = gain dB) --
    // supaya lagu yang sudah pernah diukur tidak perlu diukur ulang tiap diputar.
    val REPLAY_GAIN_MAP = stringPreferencesKey("replay_gain_map_json")

    // BARU (patch "streaming persist"): daftar RadioStation custom yang ditambahkan
    // pengguna lewat "ADD STREAM URL" di StreamingScreen -- JSON array, format sama
    // seperti LIBRARY_CACHE_JSON/PLAYLISTS_JSON di atas (lihat
    // Song.kt#serializeRadioStations/deserializeRadioStations). Sebelumnya daftar ini
    // cuma hidup di `remember` composable StreamingScreen, jadi hilang setiap app
    // ditutup total.
    val CUSTOM_STATIONS_JSON = stringPreferencesKey("custom_stations_json")
}

class SettingsRepository(private val context: Context) {

    // Safely map DataStore Preferences with IOException fallback
    private val safeDataStore: Flow<Preferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }

    val tapeHiss: Flow<Boolean> = safeDataStore.map { it[SettingsKeys.TAPE_HISS] ?: true }
    val buttonClick: Flow<Boolean> = safeDataStore.map { it[SettingsKeys.BUTTON_CLICK] ?: true }
    val wowFlutter: Flow<Float> = safeDataStore.map { it[SettingsKeys.WOW_FLUTTER] ?: 0.25f }
    val fadeSeconds: Flow<Float> = safeDataStore.map { it[SettingsKeys.FADE_SECONDS] ?: 4f }
    val crossfadeEnabled: Flow<Boolean> = safeDataStore.map { it[SettingsKeys.CROSSFADE_ENABLED] ?: false }
    val autoPause: Flow<Boolean> = safeDataStore.map { it[SettingsKeys.AUTO_PAUSE] ?: false }
    val peakLimiter: Flow<Boolean> = safeDataStore.map { it[SettingsKeys.PEAK_LIMITER] ?: true }
    val dsdMode: Flow<String> = safeDataStore.map { it[SettingsKeys.DSD_MODE] ?: "DSD Native (DoP)" }
    val cassetteSkinMode: Flow<String> = safeDataStore.map { it[SettingsKeys.CASSETTE_SKIN_MODE] ?: "random" }
    val reelSpeed: Flow<Float> = safeDataStore.map { it[SettingsKeys.REEL_SPEED] ?: 1f }
    val streamBufferSeconds: Flow<Float> = safeDataStore.map { it[SettingsKeys.STREAM_BUFFER_SECONDS] ?: 5f }
    // BARU (v2.1): SLEEP TIMER beneran. SLEEP_TIMER_MIN sudah ada key & flow-nya sejak
    // lama (pola "ghost setting" yang sama seperti REPLAY_GAIN sebelum v1.9 / BIT_PERFECT_MODE
    // sebelum v1.6) tapi tidak pernah dibaca/ditulis siapa pun -- tidak ada UI, tidak ada
    // logika apa pun yang menghentikan playback. Sekarang benar-benar dipakai: menyimpan
    // durasi (menit) terakhir yang dipilih pengguna di Settings > System > Sleep Timer
    // (0 = mati), supaya slider menampilkan pilihan terakhir lagi saat Settings dibuka
    // ulang. Status HIDUP/countdown aktualnya sendiri live di PlayerViewModel (lihat
    // sleepTimerMode/sleepTimerRemainingMs), TIDAK dipersist di sini -- kalau app
    // ditutup total, timer yang sedang berjalan ikut hilang (bukan resume otomatis).
    val sleepTimerMin: Flow<Float> = safeDataStore.map { it[SettingsKeys.SLEEP_TIMER_MIN] ?: 0f }
    val customEqPresetsJson: Flow<String> = safeDataStore.map { it[SettingsKeys.CUSTOM_EQ_PRESETS] ?: "" }
    val activeEqPresetName: Flow<String> = safeDataStore.map { it[SettingsKeys.ACTIVE_EQ_PRESET_NAME] ?: "Flat" }
    // URI folder musik yang secara eksplisit dipilih pengguna (via SAF). Kosong berarti
    // belum ada folder yang dipilih -> aplikasi TIDAK melakukan scan apa pun secara otomatis.
    val musicFolderUri: Flow<String> = safeDataStore.map { it[SettingsKeys.MUSIC_FOLDER_URI] ?: "" }
    // Kalau true, folder yang tersimpan di atas akan dipindai ulang otomatis saat app dibuka.
    // Kalau false / belum ada folder tersimpan, tidak ada scan otomatis sama sekali.
    val autoScanOnStartup: Flow<Boolean> = safeDataStore.map { it[SettingsKeys.AUTO_SCAN_ON_STARTUP] ?: true }
    // BARU (patch "cache library cepat")
    val libraryCacheJson: Flow<String> = safeDataStore.map { it[SettingsKeys.LIBRARY_CACHE_JSON] ?: "" }
    val lastSongJson: Flow<String> = safeDataStore.map { it[SettingsKeys.LAST_SONG_JSON] ?: "" }
    val lastPositionMs: Flow<Long> = safeDataStore.map { it[SettingsKeys.LAST_POSITION_MS] ?: 0L }

    // BARU (patch "cassette side A/B")
    val lastStreamUrl: Flow<String> = safeDataStore.map { it[SettingsKeys.LAST_STREAM_URL] ?: "" }
    val lastStreamTitle: Flow<String> = safeDataStore.map { it[SettingsKeys.LAST_STREAM_TITLE] ?: "" }

    // BARU (patch "DSP control knobs")
    val vocalEnabled: Flow<Boolean> = safeDataStore.map { it[SettingsKeys.VOCAL_ENABLED] ?: false }
    val vocalBassDb: Flow<Float> = safeDataStore.map { it[SettingsKeys.VOCAL_BASS_DB] ?: 0f }
    val vocalTrebleDb: Flow<Float> = safeDataStore.map { it[SettingsKeys.VOCAL_TREBLE_DB] ?: 0f }
    val stereoBalance: Flow<Float> = safeDataStore.map { it[SettingsKeys.STEREO_BALANCE] ?: 0f }
    val stereoExpansion: Flow<Float> = safeDataStore.map { it[SettingsKeys.STEREO_EXPANSION] ?: 1f }
    val monoStereoOn: Flow<Boolean> = safeDataStore.map { it[SettingsKeys.MONO_STEREO_ON] ?: false }

    // BARU (patch "headroom slider"): default 0.3 -- HARUS disamakan persis dengan
    // default `headroomSafetyRatio` di ParametricEqAudioProcessor, supaya app yang
    // baru dipasang (belum pernah menyentuh slider ini, belum ada key tersimpan di
    // DataStore) berperilaku identik dengan default DSP-nya, bukan diam-diam balik
    // ke rasio lama (0.5) hanya karena Settings belum pernah ditulis.
    val headroomSafetyRatio: Flow<Float> = safeDataStore.map { it[SettingsKeys.HEADROOM_SAFETY_RATIO] ?: 0.3f }

    // BARU (v1.2)
    val playlistsJson: Flow<String> = safeDataStore.map { it[SettingsKeys.PLAYLISTS_JSON] ?: "" }

    // BARU (v1.4) -- defaultnya SENGAJA disamakan persis dengan default lokal lama
    // di SettingsScreen supaya perilaku app tidak berubah untuk user yang belum
    // pernah menyentuh setting-setting ini sama sekali.
    val themeAccent: Flow<String> = safeDataStore.map { it[SettingsKeys.THEME_ACCENT] ?: "Gold Retro" }
    val compactVu: Flow<Boolean> = safeDataStore.map { it[SettingsKeys.COMPACT_VU] ?: false }
    val reelAnimation: Flow<Boolean> = safeDataStore.map { it[SettingsKeys.REEL_ANIMATION] ?: true }
    val gaplessPlayback: Flow<Boolean> = safeDataStore.map { it[SettingsKeys.GAPLESS_PLAYBACK] ?: true }
    // BARU (v1.9): sejak v1.4.1 sampai v1.8.1 flow ini tidak dibaca oleh kode manapun
    // (toggle-nya sudah dihapus dari Settings, tapi key & flow-nya sengaja dibiarkan
    // di sini). Sekarang benar-benar dipakai lagi -- lihat PlayerViewModel.replayGainOn
    // & tab BATAS di EqualizerScreen.
    val replayGain: Flow<Boolean> = safeDataStore.map { it[SettingsKeys.REPLAY_GAIN] ?: false }
    val audioEngine: Flow<String> = safeDataStore.map { it[SettingsKeys.AUDIO_ENGINE] ?: "AAudio (Low Latency)" }
    val ignoreShortTracks: Flow<Boolean> = safeDataStore.map { it[SettingsKeys.IGNORE_SHORT_TRACKS] ?: true }
    val keepScreenAwake: Flow<Boolean> = safeDataStore.map { it[SettingsKeys.KEEP_SCREEN_AWAKE] ?: true }
    val highPerformanceDsp: Flow<Boolean> = safeDataStore.map { it[SettingsKeys.HIGH_PERFORMANCE_DSP] ?: true }

    // BARU (v1.6)
    val bitPerfectMode: Flow<Boolean> = safeDataStore.map { it[SettingsKeys.BIT_PERFECT_MODE] ?: false }

    // BARU (v1.7): EQ per-lagu otomatis
    val autoEqPerSong: Flow<Boolean> = safeDataStore.map { it[SettingsKeys.AUTO_EQ_PER_SONG] ?: false }
    val songEqPresetMapJson: Flow<String> = safeDataStore.map { it[SettingsKeys.SONG_EQ_PRESET_MAP] ?: "" }

    // BARU (v1.9): REPLAY GAIN beneran. `replayGain` (Flow<Boolean>) sudah ada sejak
    // lama di atas (BARU v1.4) sebagai toggle "hantu" yang tidak pernah dibaca siapa
    // pun sejak dihapus dari Settings di v1.4.1 -- sekarang dipakai lagi sungguhan
    // sebagai toggle master di tab BATAS (Equalizer), defaultnya TETAP false (fitur
    // baru, tidak menyalakan diri sendiri di app yang sudah pernah dipasang).
    // replayGainMapJson adalah "ingatan" gain hasil pengukuran per lagu (lihat
    // SettingsKeys.REPLAY_GAIN_MAP).
    val replayGainMapJson: Flow<String> = safeDataStore.map { it[SettingsKeys.REPLAY_GAIN_MAP] ?: "" }

    // BARU (patch "streaming persist")
    val customStationsJson: Flow<String> = safeDataStore.map { it[SettingsKeys.CUSTOM_STATIONS_JSON] ?: "" }

    suspend fun setBool(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun setLong(key: Preferences.Key<Long>, value: Long) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun setFloat(key: Preferences.Key<Float>, value: Float) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun setString(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { it[key] = value }
    }

    /* ============================================================
     * BARU (v2.0): BACKUP & RESTORE SEMUA PENGATURAN SEKALIGUS
     *
     * Beda dari EXPORT preset EQ (PowerampPresetParser, ada sejak v1.2) yang cuma
     * menyimpan SATU preset aktif, dua fungsi di bawah ini membaca/menulis SELURUH
     * isi DataStore "tapeamp32_settings" apa adanya lewat Preferences.asMap() --
     * generik, bukan enumerasi key satu-satu -- jadi otomatis mencakup semua toggle
     * & dropdown, preset EQ custom (CUSTOM_EQ_PRESETS), playlist (PLAYLISTS_JSON),
     * peta EQ per-lagu (SONG_EQ_PRESET_MAP) & REPLAY GAIN per-lagu
     * (REPLAY_GAIN_MAP) di atas -- semuanya sudah berupa satu key String masing-
     * masing, jadi ikut terbawa otomatis tanpa perlu disentuh di sini. Key BARU
     * yang ditambahkan nanti juga otomatis ikut ter-backup tanpa perlu mengubah
     * fungsi ini lagi.
     *
     * FIX (patch "backup stream url"): daftar RadioStation custom yang disimpan
     * StreamingScreen di SettingsKeys.CUSTOM_STATIONS_JSON (lihat StreamingScreen
     * .onAddCustomUrl/.onDeleteCustomUrl/.onRenameCustomUrl) HANYALAH satu
     * stringPreferencesKey biasa di DataStore ini -- sudah otomatis ikut
     * ter-backup & ter-restore lewat loop generik di bawah, TANPA perlu key ini
     * disebut satu-satu di sini. Sebelumnya catatan di layar Settings
     * (settings_backup_restore_note) tidak menyebut ini secara eksplisit,
     * sehingga terkesan URL stream custom yang tersimpan tidak ikut ter-cover --
     * padahal sudah. String itu sudah diperbarui untuk menyebutkan "saved custom
     * stream URLs" secara eksplisit di semua bahasa.
     * ============================================================ */

    suspend fun exportAllSettingsJson(): String {
        val prefs = context.dataStore.data.first()
        val entries = JSONObject()
        for ((key, value) in prefs.asMap()) {
            val entry = JSONObject()
            when (value) {
                is Boolean -> {
                    entry.put("type", "bool")
                    entry.put("value", value)
                }
                is Int -> {
                    entry.put("type", "int")
                    entry.put("value", value)
                }
                is Long -> {
                    entry.put("type", "long")
                    entry.put("value", value)
                }
                is Float -> {
                    entry.put("type", "float")
                    entry.put("value", value.toDouble())
                }
                is Double -> {
                    entry.put("type", "double")
                    entry.put("value", value)
                }
                is String -> {
                    entry.put("type", "string")
                    entry.put("value", value)
                }
                is Set<*> -> {
                    entry.put("type", "stringset")
                    entry.put("value", JSONArray(value.map { it.toString() }))
                }
                else -> {
                    // Tipe tak dikenal (seharusnya tidak pernah terjadi di Preferences
                    // DataStore) -- fallback aman: simpan sebagai string apa adanya
                    // daripada menjatuhkan seluruh proses backup.
                    entry.put("type", "string")
                    entry.put("value", value.toString())
                }
            }
            entries.put(key.name, entry)
        }
        val root = JSONObject()
        root.put("app", "TapeAmp 32")
        root.put("backupFormatVersion", 1)
        root.put("settings", entries)
        return root.toString(2)
    }

    /**
     * Restore hasil exportAllSettingsJson() di atas. Seluruh isi DataStore SAAT INI
     * di-CLEAR dulu (bukan cuma ditimpa sebagian per key) supaya hasil restore
     * benar-benar persis sama dengan kondisi backup -- bukan campuran antara isi
     * backup lama dan pengaturan yang sempat diubah setelah backup itu dibuat.
     *
     * JUJUR diakui: menulis ke DataStore di sini SELALU benar & langsung permanen,
     * tapi sebagian besar pengaturan (preset EQ aktif, playlist, posisi knop
     * Vocal/Stereo, dll) di PlayerViewModel hanya dipulihkan ke layar SEKALI saat
     * app baru dibuka (lihat init{} di PlayerViewModel) -- bukan flow yang terus
     * dipantau live selama app berjalan. Jadi hasil Restore baru terlihat PENUH di
     * UI setelah app ditutup total & dibuka ulang; pemanggil (SettingsScreen)
     * bertanggung jawab memberi tahu pengguna soal ini.
     */
    suspend fun importAllSettingsJson(json: String): Result<Unit> = runCatching {
        val root = JSONObject(json)
        val entries = root.getJSONObject("settings")
        context.dataStore.edit { prefs ->
            prefs.clear()
            val keyNames = entries.keys()
            while (keyNames.hasNext()) {
                val keyName = keyNames.next()
                val entry = entries.getJSONObject(keyName)
                when (entry.getString("type")) {
                    "bool" -> prefs[booleanPreferencesKey(keyName)] = entry.getBoolean("value")
                    "int" -> prefs[intPreferencesKey(keyName)] = entry.getInt("value")
                    "long" -> prefs[longPreferencesKey(keyName)] = entry.getLong("value")
                    "float" -> prefs[floatPreferencesKey(keyName)] = entry.getDouble("value").toFloat()
                    "double" -> prefs[doublePreferencesKey(keyName)] = entry.getDouble("value")
                    "string" -> prefs[stringPreferencesKey(keyName)] = entry.getString("value")
                    "stringset" -> {
                        val arr = entry.getJSONArray("value")
                        val set = (0 until arr.length()).map { arr.getString(it) }.toSet()
                        prefs[stringSetPreferencesKey(keyName)] = set
                    }
                }
            }
        }
    }
}
