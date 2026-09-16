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

    val CROSSFADE_ENABLED = booleanPreferencesKey("crossfade_enabled")
    val AUTO_PAUSE = booleanPreferencesKey("auto_pause")
    val PEAK_LIMITER = booleanPreferencesKey("peak_limiter")
    val DSD_MODE = stringPreferencesKey("dsd_mode")
    val CASSETTE_SKIN_MODE = stringPreferencesKey("cassette_skin_mode")
    val REEL_SPEED = floatPreferencesKey("reel_speed")
    val STREAM_BUFFER_SECONDS = floatPreferencesKey("stream_buffer_seconds")
    val SUBSONIC_URL = stringPreferencesKey("subsonic_url")
    val SUBSONIC_USER = stringPreferencesKey("subsonic_user")
    val SLEEP_TIMER_MIN = floatPreferencesKey("sleep_timer_min")
    val CUSTOM_EQ_PRESETS = stringPreferencesKey("custom_eq_presets_json")
    val ACTIVE_EQ_PRESET_NAME = stringPreferencesKey("active_eq_preset_name")
    val MUSIC_FOLDER_URI = stringPreferencesKey("music_folder_uri")
    val AUTO_SCAN_ON_STARTUP = booleanPreferencesKey("auto_scan_on_startup")

    val LIBRARY_CACHE_JSON = stringPreferencesKey("library_cache_json")

    val LAST_SONG_JSON = stringPreferencesKey("last_song_json")
    val LAST_POSITION_MS = longPreferencesKey("last_position_ms")

    val LAST_STREAM_URL = stringPreferencesKey("last_stream_url")
    val LAST_STREAM_TITLE = stringPreferencesKey("last_stream_title")

    val VOCAL_ENABLED = booleanPreferencesKey("vocal_enabled")
    val VOCAL_BASS_DB = floatPreferencesKey("vocal_bass_db")
    val VOCAL_TREBLE_DB = floatPreferencesKey("vocal_treble_db")
    val STEREO_BALANCE = floatPreferencesKey("stereo_balance")
    val STEREO_EXPANSION = floatPreferencesKey("stereo_expansion")
    val MONO_STEREO_ON = booleanPreferencesKey("mono_stereo_on")

    val SHUFFLE_ON = booleanPreferencesKey("shuffle_on")

    val HEADROOM_SAFETY_RATIO = floatPreferencesKey("headroom_safety_ratio")

    val PLAYLISTS_JSON = stringPreferencesKey("playlists_json")

    val THEME_ACCENT = stringPreferencesKey("theme_accent")
    val COMPACT_VU = booleanPreferencesKey("compact_vu")
    val REEL_ANIMATION = booleanPreferencesKey("reel_animation")
    val GAPLESS_PLAYBACK = booleanPreferencesKey("gapless_playback")
    val REPLAY_GAIN = booleanPreferencesKey("replay_gain")
    val AUDIO_ENGINE = stringPreferencesKey("audio_engine")
    val IGNORE_SHORT_TRACKS = booleanPreferencesKey("ignore_short_tracks")
    val KEEP_SCREEN_AWAKE = booleanPreferencesKey("keep_screen_awake")
    val HIGH_PERFORMANCE_DSP = booleanPreferencesKey("high_performance_dsp")

    val BIT_PERFECT_MODE = booleanPreferencesKey("bit_perfect_mode")

    val AUTO_EQ_PER_SONG = booleanPreferencesKey("auto_eq_per_song")
    val SONG_EQ_PRESET_MAP = stringPreferencesKey("song_eq_preset_map_json")

    val REPLAY_GAIN_MAP = stringPreferencesKey("replay_gain_map_json")

    val CUSTOM_STATIONS_JSON = stringPreferencesKey("custom_stations_json")
}

class SettingsRepository(private val context: Context) {

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

    val sleepTimerMin: Flow<Float> = safeDataStore.map { it[SettingsKeys.SLEEP_TIMER_MIN] ?: 0f }
    val customEqPresetsJson: Flow<String> = safeDataStore.map { it[SettingsKeys.CUSTOM_EQ_PRESETS] ?: "" }
    val activeEqPresetName: Flow<String> = safeDataStore.map { it[SettingsKeys.ACTIVE_EQ_PRESET_NAME] ?: "Flat" }

    val musicFolderUri: Flow<String> = safeDataStore.map { it[SettingsKeys.MUSIC_FOLDER_URI] ?: "" }

    val autoScanOnStartup: Flow<Boolean> = safeDataStore.map { it[SettingsKeys.AUTO_SCAN_ON_STARTUP] ?: true }

    val libraryCacheJson: Flow<String> = safeDataStore.map { it[SettingsKeys.LIBRARY_CACHE_JSON] ?: "" }
    val lastSongJson: Flow<String> = safeDataStore.map { it[SettingsKeys.LAST_SONG_JSON] ?: "" }
    val lastPositionMs: Flow<Long> = safeDataStore.map { it[SettingsKeys.LAST_POSITION_MS] ?: 0L }

    val lastStreamUrl: Flow<String> = safeDataStore.map { it[SettingsKeys.LAST_STREAM_URL] ?: "" }
    val lastStreamTitle: Flow<String> = safeDataStore.map { it[SettingsKeys.LAST_STREAM_TITLE] ?: "" }

    val vocalEnabled: Flow<Boolean> = safeDataStore.map { it[SettingsKeys.VOCAL_ENABLED] ?: false }
    val vocalBassDb: Flow<Float> = safeDataStore.map { it[SettingsKeys.VOCAL_BASS_DB] ?: 0f }
    val vocalTrebleDb: Flow<Float> = safeDataStore.map { it[SettingsKeys.VOCAL_TREBLE_DB] ?: 0f }
    val stereoBalance: Flow<Float> = safeDataStore.map { it[SettingsKeys.STEREO_BALANCE] ?: 0f }
    val stereoExpansion: Flow<Float> = safeDataStore.map { it[SettingsKeys.STEREO_EXPANSION] ?: 1f }
    val monoStereoOn: Flow<Boolean> = safeDataStore.map { it[SettingsKeys.MONO_STEREO_ON] ?: false }

    val shuffleOn: Flow<Boolean> = safeDataStore.map { it[SettingsKeys.SHUFFLE_ON] ?: false }

    val headroomSafetyRatio: Flow<Float> = safeDataStore.map { it[SettingsKeys.HEADROOM_SAFETY_RATIO] ?: 0.3f }

    val playlistsJson: Flow<String> = safeDataStore.map { it[SettingsKeys.PLAYLISTS_JSON] ?: "" }

    val themeAccent: Flow<String> = safeDataStore.map { it[SettingsKeys.THEME_ACCENT] ?: "Gold Retro" }
    val compactVu: Flow<Boolean> = safeDataStore.map { it[SettingsKeys.COMPACT_VU] ?: false }
    val reelAnimation: Flow<Boolean> = safeDataStore.map { it[SettingsKeys.REEL_ANIMATION] ?: true }
    val gaplessPlayback: Flow<Boolean> = safeDataStore.map { it[SettingsKeys.GAPLESS_PLAYBACK] ?: true }

    val replayGain: Flow<Boolean> = safeDataStore.map { it[SettingsKeys.REPLAY_GAIN] ?: false }
    val audioEngine: Flow<String> = safeDataStore.map { it[SettingsKeys.AUDIO_ENGINE] ?: "AAudio (Low Latency)" }
    val ignoreShortTracks: Flow<Boolean> = safeDataStore.map { it[SettingsKeys.IGNORE_SHORT_TRACKS] ?: true }
    val keepScreenAwake: Flow<Boolean> = safeDataStore.map { it[SettingsKeys.KEEP_SCREEN_AWAKE] ?: true }
    val highPerformanceDsp: Flow<Boolean> = safeDataStore.map { it[SettingsKeys.HIGH_PERFORMANCE_DSP] ?: true }

    val bitPerfectMode: Flow<Boolean> = safeDataStore.map { it[SettingsKeys.BIT_PERFECT_MODE] ?: false }

    val autoEqPerSong: Flow<Boolean> = safeDataStore.map { it[SettingsKeys.AUTO_EQ_PER_SONG] ?: false }
    val songEqPresetMapJson: Flow<String> = safeDataStore.map { it[SettingsKeys.SONG_EQ_PRESET_MAP] ?: "" }

    val replayGainMapJson: Flow<String> = safeDataStore.map { it[SettingsKeys.REPLAY_GAIN_MAP] ?: "" }

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
