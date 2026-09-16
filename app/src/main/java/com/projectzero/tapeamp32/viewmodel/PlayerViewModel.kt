package com.projectzero.tapeamp32.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.projectzero.tapeamp32.audio.PlayerManager
import com.projectzero.tapeamp32.data.BuiltInPresets
import com.projectzero.tapeamp32.data.EqPreset
import com.projectzero.tapeamp32.data.MusicRepository
import com.projectzero.tapeamp32.data.Playlist
import com.projectzero.tapeamp32.data.PlaylistUtils
import com.projectzero.tapeamp32.data.PowerampPresetParser
import com.projectzero.tapeamp32.data.RadioBrowserRepository
import com.projectzero.tapeamp32.data.RadioBrowserStation
import com.projectzero.tapeamp32.data.RadioStation
import com.projectzero.tapeamp32.data.SampleFavoriteStations
import com.projectzero.tapeamp32.data.SettingsKeys
import com.projectzero.tapeamp32.data.SettingsRepository
import com.projectzero.tapeamp32.data.ShuffleQueue
import com.projectzero.tapeamp32.data.Song
import com.projectzero.tapeamp32.data.WaveformCache
import com.projectzero.tapeamp32.data.WaveformExtractor
import com.projectzero.tapeamp32.data.deserializeRadioStations
import com.projectzero.tapeamp32.data.flatTenBandPreset
import com.projectzero.tapeamp32.ui.theme.CassetteSkin
import com.projectzero.tapeamp32.ui.theme.CassetteSkins
import com.projectzero.tapeamp32.ui.theme.ThemeAccent
import com.projectzero.tapeamp32.ui.theme.ThemeAccentState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class RepeatMode { OFF, ONE, ALL }
enum class Screen { PLAYER, EQUALIZER, LIBRARY, IMPORT, LYRICS, SETTINGS, STREAMING }

enum class SleepTimerMode { OFF, MINUTES, END_OF_TRACK }

@UnstableApi
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val musicRepository = MusicRepository(application)

    private val radioBrowserRepository = RadioBrowserRepository()
    val settingsRepository = SettingsRepository(application)
    val playerManager = PlayerManager.getInstance(application)

    var currentScreen = mutableStateOf(Screen.PLAYER)
        private set

    private val _library = MutableStateFlow<List<Song>>(emptyList())
    val library: StateFlow<List<Song>> = _library.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private var queueIndex = 0

    private val _shuffleOn = MutableStateFlow(false)
    val shuffleOn: StateFlow<Boolean> = _shuffleOn.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _powerOn = MutableStateFlow(true)
    val powerOn: StateFlow<Boolean> = _powerOn.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow(0f)
    val scanProgress: StateFlow<Float> = _scanProgress.asStateFlow()

    private val _customPresets = MutableStateFlow<List<EqPreset>>(emptyList())

    val customPresets: StateFlow<List<EqPreset>> = _customPresets.asStateFlow()
    private val _presets = MutableStateFlow(BuiltInPresets)
    val presets: StateFlow<List<EqPreset>> = _presets.asStateFlow()

    private val _activePreset = MutableStateFlow(flatTenBandPreset())
    val activePreset: StateFlow<EqPreset> = _activePreset.asStateFlow()

    private val _autoEqPerSong = MutableStateFlow(false)
    val autoEqPerSong: StateFlow<Boolean> = _autoEqPerSong.asStateFlow()

    private val _songEqMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val songEqMap: StateFlow<Map<String, String>> = _songEqMap.asStateFlow()

    private val _replayGainOn = MutableStateFlow(false)
    val replayGainOn: StateFlow<Boolean> = _replayGainOn.asStateFlow()

    private val _replayGainMap = MutableStateFlow<Map<String, Double>>(emptyMap())

    private val _waveform = MutableStateFlow<FloatArray?>(null)
    val waveform: StateFlow<FloatArray?> = _waveform.asStateFlow()

    private var waveformJob: kotlinx.coroutines.Job? = null

    private var measuringReplayGainKey: String? = null

    val currentSongReplayGainDb: StateFlow<Double?> = combine(
        playerManager.currentSong,
        _replayGainMap
    ) { song, map ->
        song?.let { map[songKeyFor(it)] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentSongSavedPresetName: StateFlow<String?> = combine(
        playerManager.currentSong,
        _songEqMap
    ) { song, map ->
        song?.let { map[songKeyFor(it)] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _limiterOn = MutableStateFlow(true)
    val limiterOn: StateFlow<Boolean> = _limiterOn.asStateFlow()

    private val _eqBypassOn = MutableStateFlow(false)
    val eqBypassOn: StateFlow<Boolean> = _eqBypassOn.asStateFlow()

    private val _bitPerfectOn = MutableStateFlow(false)
    val bitPerfectOn: StateFlow<Boolean> = _bitPerfectOn.asStateFlow()

    private val _crossfadeOn = MutableStateFlow(false)
    val crossfadeOn: StateFlow<Boolean> = _crossfadeOn.asStateFlow()

    private val _crossfadeSeconds = MutableStateFlow(4f)
    val crossfadeSeconds: StateFlow<Float> = _crossfadeSeconds.asStateFlow()

    private val _vocalOn = MutableStateFlow(false)
    val vocalOn: StateFlow<Boolean> = _vocalOn.asStateFlow()

    private val _vocalBassDb = MutableStateFlow(0.0)
    val vocalBassDb: StateFlow<Double> = _vocalBassDb.asStateFlow()

    private val _vocalTrebleDb = MutableStateFlow(0.0)
    val vocalTrebleDb: StateFlow<Double> = _vocalTrebleDb.asStateFlow()

    private val _stereoBalance = MutableStateFlow(0.0)
    val stereoBalance: StateFlow<Double> = _stereoBalance.asStateFlow()

    private val _stereoExpansion = MutableStateFlow(1.0)
    val stereoExpansion: StateFlow<Double> = _stereoExpansion.asStateFlow()

    private val _monoStereoOn = MutableStateFlow(false)
    val monoStereoOn: StateFlow<Boolean> = _monoStereoOn.asStateFlow()

    private val _headroomSafetyRatio = MutableStateFlow(0.3)
    val headroomSafetyRatio: StateFlow<Double> = _headroomSafetyRatio.asStateFlow()

    private val _currentSkin = MutableStateFlow(CassetteSkins.first())
    val currentSkin: StateFlow<CassetteSkin> = _currentSkin.asStateFlow()

    private val _volume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _sleepTimerMode = MutableStateFlow(SleepTimerMode.OFF)
    val sleepTimerMode: StateFlow<SleepTimerMode> = _sleepTimerMode.asStateFlow()

    private val _sleepTimerRemainingMs = MutableStateFlow(0L)
    val sleepTimerRemainingMs: StateFlow<Long> = _sleepTimerRemainingMs.asStateFlow()

    private var sleepTimerJob: kotlinx.coroutines.Job? = null

    fun adjustVolume(delta: Float) {
        val newVolume = (_volume.value + delta).coerceIn(0f, 1f)
        _volume.value = newVolume
        playerManager.setVolume(newVolume)
    }

    val isPlaying get() = playerManager.isPlaying
    val positionMs get() = playerManager.positionMs
    val durationMs get() = playerManager.durationMs
    val vuLevels get() = playerManager.vuLevels
    val currentSong get() = playerManager.currentSong

    val currentStreamTitle get() = playerManager.currentStreamTitle
    val lastError get() = playerManager.lastError
    fun clearError() = playerManager.clearError()

    val cassetteSide: StateFlow<String> =
        playerManager.currentStreamTitle
            .map { if (it != null) "B" else "A" }
            .stateIn(viewModelScope, SharingStarted.Eagerly, "A")

    private var songBeforeStream: Song? = null
    private var positionBeforeStreamMs: Long = 0L

    private fun captureLibraryStateBeforeStream() {
        if (cassetteSide.value == "A") {
            songBeforeStream = currentSong.value
            positionBeforeStreamMs = positionMs.value
        }
    }

    fun toggleCassetteSide() {
        if (cassetteSide.value == "A") {

            captureLibraryStateBeforeStream()
            viewModelScope.launch {
                val url = runCatching { settingsRepository.lastStreamUrl.first() }.getOrDefault("")
                val title = runCatching { settingsRepository.lastStreamTitle.first() }.getOrDefault("")
                if (url.isNotBlank()) {
                    val matchedStation = _streamStations.value.firstOrNull { it.streamUrl == url }
                    _currentStationId.value = matchedStation?.id
                    matchedStation?.let { pickSkinForStation(it) }
                    playerManager.playStreamUrl(url, title.ifBlank { "Live Stream" })
                } else {
                    navigate(Screen.STREAMING)
                }
            }
        } else {

            val song = songBeforeStream
            if (song != null) {
                pickSkinFor(song)
                val q = _queue.value
                val idx = q.indexOfFirst { it.id == song.id }
                if (idx != -1) {
                    queueIndex = idx
                    playerManager.setQueue(
                        songs = q,
                        startIndex = idx,
                        startPositionMs = positionBeforeStreamMs,
                        playWhenReady = false
                    )
                } else {

                    playerManager.restoreSong(song, positionBeforeStreamMs)
                }
            } else {
                playerManager.stop()
            }
            songBeforeStream = null
        }
    }

    val isUsbDacConnected get() = playerManager.usbDacObserver.isUsbDacConnected
    val connectedDacName get() = playerManager.usbDacObserver.connectedDacName
    val dspEngineOn get() = playerManager.dspEngineOn
    val peakActive get() = playerManager.peakActive

    val offloadActive get() = playerManager.offloadActive

    val isHiRes get() = playerManager.isHiRes
    val sampleRate get() = playerManager.sampleRate
    val bitDepth get() = playerManager.bitDepth

    val streamBitrateKbps get() = playerManager.streamBitrateKbps

    init {

        viewModelScope.launch {
            var tick = 0
            while (true) {
                playerManager.pollPosition()
                tick++
                if (tick % 45 == 0 && playerManager.isPlaying.value) {
                    persistPlaybackState(playerManager.currentSong.value, playerManager.positionMs.value)
                }
                delay(66)
            }
        }

        viewModelScope.launch {
            playerManager.isPlaying.collect { playing ->
                if (!playing) {
                    playerManager.currentSong.value?.let {
                        persistPlaybackState(it, playerManager.positionMs.value)
                    }
                }
            }
        }

        playerManager.onSongEnded = {
            viewModelScope.launch { handleTrackEnded() }
        }

        viewModelScope.launch {
            playerManager.currentQueueIndex.collect { idx ->
                if (idx in _queue.value.indices) queueIndex = idx
            }
        }

        viewModelScope.launch {
            val lastSongJson = runCatching { settingsRepository.lastSongJson.first() }.getOrDefault("")
            val lastPositionMs = runCatching { settingsRepository.lastPositionMs.first() }.getOrDefault(0L)
            songFromJson(lastSongJson)?.let { song ->
                pickSkinFor(song)
                playerManager.restoreSong(song, lastPositionMs)

                val existingIdx = _queue.value.indexOfFirst { it.id == song.id }
                if (existingIdx >= 0) {
                    queueIndex = existingIdx
                } else {
                    _queue.value = listOf(song)
                    queueIndex = 0
                }
            }
        }

        viewModelScope.launch {
            val savedJson = runCatching { settingsRepository.customStationsJson.first() }.getOrDefault("")
            val restoredStations = deserializeRadioStations(savedJson)
            if (_streamStations.value.isEmpty() && restoredStations.isNotEmpty()) {
                _streamStations.value = SampleFavoriteStations + restoredStations
            }
        }

        viewModelScope.launch {
            val restoredCustom = runCatching {
                val json = settingsRepository.customEqPresetsJson.first()
                if (json.isBlank()) emptyList() else PowerampPresetParser.parse(json)
            }.getOrDefault(emptyList())

            _customPresets.value = restoredCustom
            _presets.value = BuiltInPresets + restoredCustom

            val lastActiveName = settingsRepository.activeEqPresetName.first()
            val restoredActive = _presets.value.firstOrNull { it.name == lastActiveName } ?: flatTenBandPreset()
            _activePreset.value = restoredActive
            playerManager.setEqPreset(restoredActive)
        }

        viewModelScope.launch {
            val restoredVocalOn = runCatching { settingsRepository.vocalEnabled.first() }.getOrDefault(false)
            val restoredVocalBass = runCatching { settingsRepository.vocalBassDb.first() }.getOrDefault(0f).toDouble()
            val restoredVocalTreble = runCatching { settingsRepository.vocalTrebleDb.first() }.getOrDefault(0f).toDouble()
            val restoredBalance = runCatching { settingsRepository.stereoBalance.first() }.getOrDefault(0f).toDouble()
            val restoredExpansion = runCatching { settingsRepository.stereoExpansion.first() }.getOrDefault(1f).toDouble()
            val restoredMono = runCatching { settingsRepository.monoStereoOn.first() }.getOrDefault(false)

            val restoredShuffle = runCatching { settingsRepository.shuffleOn.first() }.getOrDefault(false)

            val restoredHeadroomSafetyRatio = runCatching { settingsRepository.headroomSafetyRatio.first() }.getOrDefault(0.3f).toDouble()

            _vocalOn.value = restoredVocalOn
            _vocalBassDb.value = restoredVocalBass
            _vocalTrebleDb.value = restoredVocalTreble
            _stereoBalance.value = restoredBalance
            _stereoExpansion.value = restoredExpansion
            _monoStereoOn.value = restoredMono

            _shuffleOn.value = restoredShuffle
            _headroomSafetyRatio.value = restoredHeadroomSafetyRatio

            playerManager.setVocalEnabled(restoredVocalOn)
            playerManager.setVocalBass(restoredVocalBass)
            playerManager.setVocalTreble(restoredVocalTreble)
            playerManager.setBalance(restoredBalance)
            playerManager.setStereoExpansion(restoredExpansion)
            playerManager.setMonoStereo(restoredMono)
            playerManager.setHeadroomSafetyRatio(restoredHeadroomSafetyRatio)
        }

        viewModelScope.launch {
            val restoredLimiter = runCatching { settingsRepository.peakLimiter.first() }.getOrDefault(true)
            _limiterOn.value = restoredLimiter
            playerManager.setLimiterEnabled(restoredLimiter)
        }

        viewModelScope.launch {
            val restoredBitPerfect = runCatching { settingsRepository.bitPerfectMode.first() }.getOrDefault(false)
            _bitPerfectOn.value = restoredBitPerfect
            playerManager.setBitPerfectMode(restoredBitPerfect)
        }

        viewModelScope.launch {
            val restoredOn = runCatching { settingsRepository.crossfadeEnabled.first() }.getOrDefault(false)
            val restoredSeconds = runCatching { settingsRepository.fadeSeconds.first() }.getOrDefault(4f)
            _crossfadeOn.value = restoredOn
            _crossfadeSeconds.value = restoredSeconds
            playerManager.setCrossfadeSeconds(restoredSeconds)
            playerManager.setCrossfadeEnabled(restoredOn)
        }

        viewModelScope.launch {
            val savedAccentLabel = runCatching { settingsRepository.themeAccent.first() }.getOrDefault("Gold Retro")
            ThemeAccentState.current = ThemeAccent.fromLabel(savedAccentLabel)
        }

        viewModelScope.launch {
            val restoredPlaylists = runCatching {
                val json = settingsRepository.playlistsJson.first()
                if (json.isBlank()) emptyList() else PlaylistUtils.deserializeList(json)
            }.getOrDefault(emptyList())
            _playlists.value = restoredPlaylists
        }

        viewModelScope.launch {
            val cachedJson = runCatching { settingsRepository.libraryCacheJson.first() }.getOrDefault("")
            val cachedSongs = if (cachedJson.isBlank()) {
                emptyList()
            } else {
                runCatching { musicRepository.deserializeSongs(cachedJson) }.getOrDefault(emptyList())
            }
            if (cachedSongs.isNotEmpty()) {

                syncQueueWithLibrary(cachedSongs, updateCache = false)
            }

            val autoScan = runCatching { settingsRepository.autoScanOnStartup.first() }.getOrDefault(true)
            val savedFolder = runCatching { settingsRepository.musicFolderUri.first() }.getOrDefault("")
            if (autoScan && savedFolder.isNotBlank()) {
                runCatching { Uri.parse(savedFolder) }.getOrNull()?.let { uri ->
                    scanFolder(uri, persist = false, showProgress = cachedSongs.isEmpty())
                }
            }
        }

        viewModelScope.launch {
            val restoredAutoEq = runCatching { settingsRepository.autoEqPerSong.first() }.getOrDefault(false)
            val restoredMapJson = runCatching { settingsRepository.songEqPresetMapJson.first() }.getOrDefault("")
            _autoEqPerSong.value = restoredAutoEq
            _songEqMap.value = parseSongEqMap(restoredMapJson)
        }

        viewModelScope.launch {
            playerManager.currentSong.collect { song ->
                if (song == null || !_autoEqPerSong.value) return@collect
                val savedName = _songEqMap.value[songKeyFor(song)] ?: return@collect
                if (savedName == _activePreset.value.name) return@collect
                val matchedPreset = _presets.value.firstOrNull { it.name == savedName } ?: return@collect
                _activePreset.value = matchedPreset
                playerManager.setEqPreset(matchedPreset)
                settingsRepository.setString(SettingsKeys.ACTIVE_EQ_PRESET_NAME, matchedPreset.name)
            }
        }

        viewModelScope.launch {
            val restoredOn = runCatching { settingsRepository.replayGain.first() }.getOrDefault(false)
            val restoredMapJson = runCatching { settingsRepository.replayGainMapJson.first() }.getOrDefault("")
            _replayGainOn.value = restoredOn
            _replayGainMap.value = parseReplayGainMap(restoredMapJson)
            playerManager.setReplayGainEnabled(restoredOn)

            applyReplayGainForSong(playerManager.currentSong.value)
        }

        viewModelScope.launch {
            playerManager.currentSong.collect { song ->
                measuringReplayGainKey?.let { prevKey ->
                    playerManager.finishReplayGainMeasurement()?.let { measuredDb ->
                        _replayGainMap.value = _replayGainMap.value + (prevKey to measuredDb)
                        persistReplayGainMap()
                    }
                }
                measuringReplayGainKey = null
                applyReplayGainForSong(song)
            }
        }

        viewModelScope.launch {
            playerManager.currentSong.collect { song ->
                waveformJob?.cancel()
                _waveform.value = null

                if (song == null) return@collect

                waveformJob = viewModelScope.launch(Dispatchers.IO) {
                    val cached = WaveformCache.get(getApplication(), song.id)
                    if (cached != null) {
                        withContext(Dispatchers.Main) {
                            if (playerManager.currentSong.value?.id == song.id) {
                                _waveform.value = cached
                            }
                        }
                        return@launch
                    }

                    val data = WaveformExtractor.safeExtract(getApplication(), song.uri)
                    if (data != null) {
                        WaveformCache.put(getApplication(), song.id, data)
                    }
                    withContext(Dispatchers.Main) {

                        if (playerManager.currentSong.value?.id == song.id) {
                            _waveform.value = data
                        }
                    }
                }
            }
        }
    }

    private fun applyReplayGainForSong(song: Song?) {
        if (song == null || !_replayGainOn.value) {
            playerManager.setReplayGainDb(0.0)
            return
        }
        val key = songKeyFor(song)
        val cached = _replayGainMap.value[key]
        if (cached != null) {
            playerManager.setReplayGainDb(cached)
        } else {
            playerManager.setReplayGainDb(0.0)
            playerManager.beginReplayGainMeasurement()
            measuringReplayGainKey = key
        }
    }

    private fun parseReplayGainMap(json: String): Map<String, Double> {
        if (json.isBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(json)
            val map = mutableMapOf<String, Double>()
            obj.keys().forEach { key -> map[key] = obj.getDouble(key) }
            map
        }.getOrDefault(emptyMap())
    }

    private fun persistReplayGainMap() {
        viewModelScope.launch {
            val obj = JSONObject()
            _replayGainMap.value.forEach { (key, value) -> obj.put(key, value) }
            settingsRepository.setString(SettingsKeys.REPLAY_GAIN_MAP, obj.toString())
        }
    }

    fun setReplayGainOn(enabled: Boolean) {
        _replayGainOn.value = enabled
        playerManager.setReplayGainEnabled(enabled)
        if (enabled) {
            applyReplayGainForSong(playerManager.currentSong.value)
        } else {
            playerManager.cancelReplayGainMeasurement()
            measuringReplayGainKey = null
            playerManager.setReplayGainDb(0.0)
        }
        viewModelScope.launch {
            settingsRepository.setBool(SettingsKeys.REPLAY_GAIN, enabled)
        }
    }

    fun forgetReplayGainForCurrentSong() {
        val song = playerManager.currentSong.value ?: return
        val key = songKeyFor(song)
        if (!_replayGainMap.value.containsKey(key)) return
        _replayGainMap.value = _replayGainMap.value - key
        persistReplayGainMap()
    }

    private fun songKeyFor(song: Song): String =
        song.path.ifBlank { "${song.title}|${song.artist}" }

    private fun parseSongEqMap(json: String): Map<String, String> {
        if (json.isBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(json)
            val map = mutableMapOf<String, String>()
            obj.keys().forEach { key -> map[key] = obj.getString(key) }
            map
        }.getOrDefault(emptyMap())
    }

    private fun persistSongEqMap() {
        viewModelScope.launch {
            val obj = JSONObject()
            _songEqMap.value.forEach { (key, value) -> obj.put(key, value) }
            settingsRepository.setString(SettingsKeys.SONG_EQ_PRESET_MAP, obj.toString())
        }
    }

    fun setAutoEqPerSong(enabled: Boolean) {
        _autoEqPerSong.value = enabled
        viewModelScope.launch {
            settingsRepository.setBool(SettingsKeys.AUTO_EQ_PER_SONG, enabled)
        }
    }

    fun forgetEqForCurrentSong() {
        val song = playerManager.currentSong.value ?: return
        if (!_songEqMap.value.containsKey(songKeyFor(song))) return
        _songEqMap.value = _songEqMap.value - songKeyFor(song)
        persistSongEqMap()
    }

    fun navigate(screen: Screen) {
        currentScreen.value = screen
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            _isScanning.value = true
            _scanProgress.value = 0.3f

            val songs = applyIgnoreShortTracksFilter(musicRepository.scanLibrary())
            _scanProgress.value = 0.8f
            delay(200)

            syncQueueWithLibrary(songs)

            _scanProgress.value = 1f
            _isScanning.value = false
        }
    }

    private suspend fun applyIgnoreShortTracksFilter(songs: List<Song>): List<Song> {
        val ignoreShort = runCatching { settingsRepository.ignoreShortTracks.first() }.getOrDefault(true)
        if (!ignoreShort) return songs
        return songs.filter { it.durationMs <= 0L || it.durationMs >= 30_000L }
    }

    private suspend fun syncQueueWithLibrary(songs: List<Song>, updateCache: Boolean = true) {
        _library.value = songs
        if (songs.isEmpty()) return

        val current = playerManager.currentSong.value
        val idx = current?.let { c -> songs.indexOfFirst { it.id == c.id } }?.takeIf { it >= 0 } ?: 0

        val queueToUse = if (_shuffleOn.value) ShuffleQueue.shuffled(songs) else songs
        val finalIdx = if (_shuffleOn.value) {
            current?.let { c -> queueToUse.indexOfFirst { it.id == c.id } }?.takeIf { it >= 0 } ?: 0
        } else idx

        _queue.value = queueToUse
        queueIndex = finalIdx

        val resumePositionMs = if (current != null) playerManager.positionMs.value else 0L

        playerManager.setQueue(queueToUse, finalIdx, resumePositionMs, playWhenReady = playerManager.isPlaying.value)

        if (updateCache) {
            runCatching {
                settingsRepository.setString(
                    SettingsKeys.LIBRARY_CACHE_JSON,
                    musicRepository.serializeSongs(songs)
                )
            }
        }
    }

    fun scanFolder(folderUri: Uri, persist: Boolean = true, showProgress: Boolean = true) {
        viewModelScope.launch {
            if (showProgress) {
                _isScanning.value = true
                _scanProgress.value = 0.3f
            }

            val rawSongs = runCatching {
                withContext(Dispatchers.IO) { musicRepository.scanSelectedFolder(folderUri) }
            }.getOrElse {
                if (showProgress) {
                    _isScanning.value = false
                    _scanProgress.value = 0f
                }
                return@launch
            }
            val songs = applyIgnoreShortTracksFilter(rawSongs)
            if (showProgress) {
                _scanProgress.value = 0.8f
                delay(200)
            }

            syncQueueWithLibrary(songs)

            if (showProgress) {
                _scanProgress.value = 1f
                _isScanning.value = false
            }

            if (persist) {
                settingsRepository.setString(
                    com.projectzero.tapeamp32.data.SettingsKeys.MUSIC_FOLDER_URI,
                    folderUri.toString()
                )
            }
        }
    }

    fun playExternalUri(uri: android.net.Uri) {
        viewModelScope.launch {
            val song = withContext(Dispatchers.IO) { musicRepository.songFromExternalUri(uri) }
            val combined = listOf(song) + _library.value.filter { it.id != song.id }
            playSong(song, combined)
        }
    }

    fun playExternalUris(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val externalSongs = withContext(Dispatchers.IO) { uris.map { musicRepository.songFromExternalUri(it) } }
            if (externalSongs.isEmpty()) return@launch
            val externalIds = externalSongs.map { it.id }.toSet()
            val combined = externalSongs + _library.value.filter { it.id !in externalIds }
            playSong(externalSongs.first(), combined)
        }
    }

    fun playSong(song: Song, fromList: List<Song> = _library.value) {
        val queueToUse = if (_shuffleOn.value) {
            listOf(song) + ShuffleQueue.shuffled(fromList.filter { it.id != song.id })
        } else {
            fromList
        }
        _queue.value = queueToUse
        queueIndex = queueToUse.indexOf(song).coerceAtLeast(0)
        pickSkinFor(song)

        playerManager.setQueue(queueToUse, queueIndex, 0L, playWhenReady = true)
        persistPlaybackState(song, 0L)
    }

    fun shuffleAll() {
        val shuffled = ShuffleQueue.shuffled(_library.value)
        _queue.value = shuffled
        _shuffleOn.value = true

        persistShuffleOn(true)
        queueIndex = 0
        if (shuffled.isNotEmpty()) {
            pickSkinFor(shuffled[0])
            playerManager.setQueue(shuffled, 0, 0L, playWhenReady = true)
            persistPlaybackState(shuffled[0], 0L)
        }
    }

    private var preShuffleQueue: List<Song> = emptyList()

    fun toggleShuffle() {

        if (cassetteSide.value == "B") {
            shuffleToRandomStation()
            return
        }

        val turningOn = !_shuffleOn.value
        _shuffleOn.value = turningOn

        persistShuffleOn(turningOn)

        val newQueue = if (turningOn) {
            preShuffleQueue = _queue.value
            ShuffleQueue.shuffled(_queue.value)
        } else {
            preShuffleQueue.ifEmpty { _queue.value }
        }
        _queue.value = newQueue
        if (newQueue.isEmpty()) return

        val current = playerManager.currentSong.value
        val idx = current?.let { c -> newQueue.indexOfFirst { it.id == c.id } }?.takeIf { it >= 0 } ?: 0

        queueIndex = idx
        playerManager.reorderQueue(newQueue)
    }

    private fun shuffleToRandomStation() {
        val stations = _streamStations.value
        if (stations.size < 2) return

        val candidates = stations.filterNot { it.id == _currentStationId.value }
        val pool = candidates.ifEmpty { stations }
        val station = pool.random()

        _currentStationId.value = station.id
        pickSkinForStation(station)
        playerManager.playStreamUrl(station.streamUrl, station.name)
        persistLastStream(station.streamUrl, station.name)
    }

    private val _browseQuery = MutableStateFlow("")
    val browseQuery: StateFlow<String> = _browseQuery.asStateFlow()

    private val _browseResults = MutableStateFlow<List<RadioBrowserStation>>(emptyList())
    val browseResults: StateFlow<List<RadioBrowserStation>> = _browseResults.asStateFlow()

    private val _browseLoading = MutableStateFlow(false)
    val browseLoading: StateFlow<Boolean> = _browseLoading.asStateFlow()

    private val _browseError = MutableStateFlow<String?>(null)
    val browseError: StateFlow<String?> = _browseError.asStateFlow()

    private val _browseCountry = MutableStateFlow<String?>(null)
    val browseCountry: StateFlow<String?> = _browseCountry.asStateFlow()

    private var browseSearchJob: kotlinx.coroutines.Job? = null

    fun updateBrowseQuery(query: String) {
        _browseQuery.value = query
    }

    fun loadPopularStationsIfEmpty() {
        if (_browseResults.value.isNotEmpty() || _browseLoading.value) return
        browseSearchJob?.cancel()
        browseSearchJob = viewModelScope.launch {
            _browseLoading.value = true
            _browseError.value = null
            val result = runCatching {
                withContext(Dispatchers.IO) { radioBrowserRepository.getPopularStations() }
            }
            _browseLoading.value = false
            result.onSuccess { stations ->
                _browseResults.value = stations
                if (stations.isEmpty()) {
                    _browseError.value = "Tidak bisa memuat daftar radio. Cek koneksi internet."
                }
            }.onFailure {
                _browseError.value = "Tidak bisa memuat daftar radio. Cek koneksi internet."
            }
        }
    }

    fun searchBrowseStations(query: String) {
        browseSearchJob?.cancel()
        if (query.isBlank()) {
            loadPopularStationsIfEmpty()
            return
        }
        browseSearchJob = viewModelScope.launch {

            delay(400)
            _browseLoading.value = true
            _browseError.value = null
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    radioBrowserRepository.searchStations(query, countryCode = _browseCountry.value)
                }
            }
            _browseLoading.value = false
            result.onSuccess { stations ->
                _browseResults.value = stations
                if (stations.isEmpty()) {
                    _browseError.value = "Tidak ada hasil untuk \"$query\"."
                }
            }.onFailure {
                _browseError.value = "Tidak bisa mencari radio. Cek koneksi internet."
            }
        }
    }

    fun filterBrowseByTag(tag: String) {
        browseSearchJob?.cancel()
        browseSearchJob = viewModelScope.launch {
            _browseLoading.value = true
            _browseError.value = null
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    radioBrowserRepository.getStationsByTag(tag, countryCode = _browseCountry.value)
                }
            }
            _browseLoading.value = false
            result.onSuccess { stations ->
                _browseResults.value = stations
                if (stations.isEmpty()) {
                    _browseError.value = "Tidak ada hasil untuk tag \"$tag\"."
                }
            }.onFailure {
                _browseError.value = "Tidak bisa memuat radio. Cek koneksi internet."
            }
        }
    }

    fun filterBrowseByCountry(countryCode: String?) {
        _browseCountry.value = countryCode
        browseSearchJob?.cancel()
        val currentQuery = _browseQuery.value
        browseSearchJob = viewModelScope.launch {
            _browseLoading.value = true
            _browseError.value = null
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    if (currentQuery.isNotBlank()) {
                        radioBrowserRepository.searchStations(currentQuery, countryCode = countryCode)
                    } else if (countryCode.isNullOrBlank()) {
                        radioBrowserRepository.getPopularStations()
                    } else {
                        radioBrowserRepository.getStationsByCountry(countryCode)
                    }
                }
            }
            _browseLoading.value = false
            result.onSuccess { stations ->
                _browseResults.value = stations
                if (stations.isEmpty()) {
                    _browseError.value = "Tidak ada hasil untuk negara ini."
                }
            }.onFailure {
                _browseError.value = "Tidak bisa memuat radio. Cek koneksi internet."
            }
        }
    }

    fun playFoundStation(station: RadioBrowserStation): RadioStation {
        val asRadioStation = RadioStation(
            id = "browse_${station.stationUuid.ifBlank { station.streamUrl.hashCode().toString() }}",
            name = station.name,
            streamUrl = station.streamUrl,
            bitrateKbps = station.bitrateKbps,
            codec = "custom"
        )
        _currentStationId.value = asRadioStation.id
        pickSkinForStation(asRadioStation)
        playerManager.playStreamUrl(asRadioStation.streamUrl, asRadioStation.name)
        persistLastStream(asRadioStation.streamUrl, asRadioStation.name)
        return asRadioStation
    }

    fun cycleRepeat() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }

        playerManager.setRepeatMode(
            when (_repeatMode.value) {
                RepeatMode.OFF -> androidx.media3.common.Player.REPEAT_MODE_OFF
                RepeatMode.ALL -> androidx.media3.common.Player.REPEAT_MODE_ALL
                RepeatMode.ONE -> androidx.media3.common.Player.REPEAT_MODE_ONE
            }
        )
    }

    fun togglePower() {
        _powerOn.value = !_powerOn.value
        if (!_powerOn.value) playerManager.stop()
    }

    fun playPause() = playerManager.playPause()

    fun startSleepTimer(minutes: Float) {
        cancelSleepTimer()
        if (minutes <= 0f) return
        _sleepTimerMode.value = SleepTimerMode.MINUTES
        viewModelScope.launch {
            runCatching { settingsRepository.setFloat(SettingsKeys.SLEEP_TIMER_MIN, minutes) }
        }
        val totalMs = (minutes * 60_000f).toLong()
        _sleepTimerRemainingMs.value = totalMs
        sleepTimerJob = viewModelScope.launch {
            var remaining = totalMs
            while (remaining > 0) {
                delay(1000)
                remaining -= 1000
                _sleepTimerRemainingMs.value = remaining.coerceAtLeast(0L)
            }
            playerManager.pause()
            _sleepTimerMode.value = SleepTimerMode.OFF
            _sleepTimerRemainingMs.value = 0L
        }
    }

    fun startSleepTimerEndOfTrack() {
        cancelSleepTimer()
        _sleepTimerMode.value = SleepTimerMode.END_OF_TRACK
        viewModelScope.launch {
            runCatching { settingsRepository.setFloat(SettingsKeys.SLEEP_TIMER_MIN, 0f) }
        }

        playerManager.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_OFF)
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        if (_sleepTimerMode.value == SleepTimerMode.END_OF_TRACK) {
            restoreRepeatModeToPlayer()
        }
        _sleepTimerMode.value = SleepTimerMode.OFF
        _sleepTimerRemainingMs.value = 0L
    }

    private fun restoreRepeatModeToPlayer() {
        playerManager.setRepeatMode(
            when (_repeatMode.value) {
                RepeatMode.OFF -> androidx.media3.common.Player.REPEAT_MODE_OFF
                RepeatMode.ALL -> androidx.media3.common.Player.REPEAT_MODE_ALL
                RepeatMode.ONE -> androidx.media3.common.Player.REPEAT_MODE_ONE
            }
        )
    }

    private fun tryAdvanceStreamStation(step: Int): Boolean {
        val stations = _streamStations.value

        if (_currentStationId.value == null || playerManager.currentSong.value != null || stations.isEmpty()) {
            return false
        }

        val curIdx = stations.indexOfFirst { it.id == _currentStationId.value }
            .let { if (it == -1) 0 else it }
        val newIdx = (curIdx + step + stations.size) % stations.size
        val station = stations[newIdx]

        _currentStationId.value = station.id
        pickSkinForStation(station)
        playerManager.playStreamUrl(station.streamUrl, station.name)
        return true
    }

    fun next() {
        if (tryAdvanceStreamStation(step = 1)) return

        val q = _queue.value
        if (q.isEmpty()) return
        queueIndex = (queueIndex + 1) % q.size
        val song = q[queueIndex]
        pickSkinFor(song)

        playerManager.seekToQueueItem(queueIndex)
        persistPlaybackState(song, 0L)
    }

    fun previous() {
        if (tryAdvanceStreamStation(step = -1)) return

        val q = _queue.value
        if (q.isEmpty()) return
        queueIndex = (queueIndex - 1 + q.size) % q.size
        val song = q[queueIndex]
        pickSkinFor(song)
        playerManager.seekToQueueItem(queueIndex)
        persistPlaybackState(song, 0L)
    }

    fun seekTo(ms: Long) = playerManager.seekTo(ms)

    fun setTapeScrubSpeed(speedMultiplier: Float) = playerManager.setTapeScrubSpeed(speedMultiplier)
    fun resetTapeScrubSpeed() = playerManager.resetTapeScrubSpeed()

    private fun handleTrackEnded() {

        if (_sleepTimerMode.value == SleepTimerMode.END_OF_TRACK) {
            playerManager.pause()
            restoreRepeatModeToPlayer()
            _sleepTimerMode.value = SleepTimerMode.OFF
            return
        }
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                playerManager.currentSong.value?.let { song ->

                    playerManager.seekToQueueItem(queueIndex)
                    persistPlaybackState(song, 0L)
                }
            }
            RepeatMode.ALL -> {
                next()
            }
            RepeatMode.OFF -> {
                val q = _queue.value
                if (q.isEmpty()) return
                if (queueIndex < q.size - 1) {
                    next()
                } else {

                    playerManager.stop()
                }
            }
        }
    }

    fun applyPreset(preset: EqPreset) {
        _activePreset.value = preset
        playerManager.setEqPreset(preset)
        viewModelScope.launch {
            settingsRepository.setString(com.projectzero.tapeamp32.data.SettingsKeys.ACTIVE_EQ_PRESET_NAME, preset.name)
        }

        if (_autoEqPerSong.value) {
            val song = playerManager.currentSong.value
            if (song != null) {
                _songEqMap.value = _songEqMap.value + (songKeyFor(song) to preset.name)
                persistSongEqMap()
            }
        }
    }

    fun updateBandGain(index: Int, gainDb: Double) {
        val preset = _activePreset.value
        val bands = preset.bands.toMutableList()
        if (index !in bands.indices) return
        bands[index] = bands[index].copy(gain = gainDb)
        val updated = preset.copy(name = "Custom", bands = bands)
        _activePreset.value = updated
        playerManager.setEqPreset(updated)
    }

    fun saveCustomPreset(name: String) {
        val presetName = name.trim().ifBlank { "Custom" }
        val presetToSave = _activePreset.value.copy(name = presetName)

        val updatedCustom = _customPresets.value.filterNot { it.name == presetName } + presetToSave
        _customPresets.value = updatedCustom
        _presets.value = BuiltInPresets + updatedCustom
        _activePreset.value = presetToSave
        playerManager.setEqPreset(presetToSave)

        viewModelScope.launch {
            settingsRepository.setString(
                com.projectzero.tapeamp32.data.SettingsKeys.CUSTOM_EQ_PRESETS,
                PowerampPresetParser.serializeList(updatedCustom)
            )
            settingsRepository.setString(
                com.projectzero.tapeamp32.data.SettingsKeys.ACTIVE_EQ_PRESET_NAME,
                presetName
            )
        }
    }

    fun deleteCustomPreset(name: String) {
        val stillExists = _customPresets.value.any { it.name == name }
        if (!stillExists) return

        val updatedCustom = _customPresets.value.filterNot { it.name == name }
        _customPresets.value = updatedCustom
        _presets.value = BuiltInPresets + updatedCustom

        if (_activePreset.value.name == name) {
            applyPreset(flatTenBandPreset())
        }

        viewModelScope.launch {
            settingsRepository.setString(
                com.projectzero.tapeamp32.data.SettingsKeys.CUSTOM_EQ_PRESETS,
                PowerampPresetParser.serializeList(updatedCustom)
            )
        }
    }

    fun importPresetJson(json: String): Result<EqPreset> = try {
        val parsed = PowerampPresetParser.parse(json)
        val preset = parsed.first()
        _customPresets.value = _customPresets.value + preset
        _presets.value = BuiltInPresets + _customPresets.value
        applyPreset(preset)
        viewModelScope.launch {
            settingsRepository.setString(
                com.projectzero.tapeamp32.data.SettingsKeys.CUSTOM_EQ_PRESETS,
                PowerampPresetParser.serializeList(_customPresets.value)
            )
        }
        Result.success(preset)
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun exportActivePresetJson(): String = PowerampPresetParser.serialize(_activePreset.value)

    suspend fun exportAllSettingsJson(): String = settingsRepository.exportAllSettingsJson()

    suspend fun importAllSettingsJson(json: String): Result<Unit> =
        settingsRepository.importAllSettingsJson(json)

    private fun persistPlaylists(updated: List<Playlist>) {
        _playlists.value = updated
        viewModelScope.launch {
            settingsRepository.setString(
                SettingsKeys.PLAYLISTS_JSON,
                PlaylistUtils.serializeList(updated)
            )
        }
    }

    fun createPlaylist(name: String) {
        val playlistName = name.trim().ifBlank { "Playlist" }
        val newPlaylist = Playlist(
            id = java.util.UUID.randomUUID().toString(),
            name = playlistName,
            songPaths = emptyList()
        )
        persistPlaylists(_playlists.value + newPlaylist)
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        val trimmed = newName.trim().ifBlank { return }
        persistPlaylists(
            _playlists.value.map { if (it.id == playlistId) it.copy(name = trimmed) else it }
        )
    }

    fun deletePlaylist(playlistId: String) {
        persistPlaylists(_playlists.value.filterNot { it.id == playlistId })
    }

    fun addSongToPlaylist(playlistId: String, song: Song) {
        persistPlaylists(
            _playlists.value.map { playlist ->
                if (playlist.id == playlistId && playlist.songPaths.none { it == song.path }) {
                    playlist.copy(songPaths = playlist.songPaths + song.path)
                } else playlist
            }
        )
    }

    fun removeSongFromPlaylist(playlistId: String, song: Song) {
        persistPlaylists(
            _playlists.value.map { playlist ->
                if (playlist.id == playlistId) {
                    playlist.copy(songPaths = playlist.songPaths.filterNot { it == song.path })
                } else playlist
            }
        )
    }

    fun songsForPlaylist(playlist: Playlist): List<Song> {
        val byPath = _library.value.associateBy { it.path }
        return playlist.songPaths.mapNotNull { byPath[it] }
    }

    fun playPlaylist(playlist: Playlist) {
        val songs = songsForPlaylist(playlist)
        if (songs.isEmpty()) return
        playSong(songs.first(), songs)
    }

    fun rescanLibrary() {
        viewModelScope.launch {
            val savedFolder = runCatching { settingsRepository.musicFolderUri.first() }.getOrDefault("")
            if (savedFolder.isNotBlank()) {
                runCatching { Uri.parse(savedFolder) }.getOrNull()?.let { uri ->
                    scanFolder(uri, persist = false)
                    return@launch
                }
            }

            refreshLibrary()
        }
    }

    fun updatePreampGain(db: Double) {
        val preset = _activePreset.value
        val clamped = db.coerceIn(-12.0, 12.0)
        val updated = preset.copy(name = "Custom", preamp = clamped)
        _activePreset.value = updated
        playerManager.setEqPreset(updated)
    }

    fun setLimiterEnabled(enabled: Boolean) {
        _limiterOn.value = enabled
        playerManager.setLimiterEnabled(enabled)

        viewModelScope.launch {
            settingsRepository.setBool(SettingsKeys.PEAK_LIMITER, enabled)
        }
    }

    fun setEqBypass(bypass: Boolean) {
        _eqBypassOn.value = bypass
        playerManager.setEqBypass(bypass)
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        _crossfadeOn.value = enabled
        playerManager.setCrossfadeEnabled(enabled)
        viewModelScope.launch {
            settingsRepository.setBool(SettingsKeys.CROSSFADE_ENABLED, enabled)
        }
    }

    fun setCrossfadeSeconds(seconds: Float) {
        val clamped = seconds.coerceIn(1f, 8f)
        _crossfadeSeconds.value = clamped
        playerManager.setCrossfadeSeconds(clamped)
        viewModelScope.launch {
            settingsRepository.setFloat(SettingsKeys.FADE_SECONDS, clamped)
        }
    }

    fun setBitPerfectMode(enabled: Boolean) {
        _bitPerfectOn.value = enabled
        playerManager.setBitPerfectMode(enabled)
        viewModelScope.launch {
            settingsRepository.setBool(SettingsKeys.BIT_PERFECT_MODE, enabled)
        }
    }

    fun setThemeAccent(label: String) {
        ThemeAccentState.current = ThemeAccent.fromLabel(label)
        viewModelScope.launch {
            settingsRepository.setString(SettingsKeys.THEME_ACCENT, label)
        }
    }

    fun setVocalEnabled(enabled: Boolean) {
        _vocalOn.value = enabled
        playerManager.setVocalEnabled(enabled)
        viewModelScope.launch { settingsRepository.setBool(SettingsKeys.VOCAL_ENABLED, enabled) }
    }

    fun updateVocalBass(db: Double) {
        val clamped = db.coerceIn(-12.0, 12.0)
        _vocalBassDb.value = clamped
        playerManager.setVocalBass(clamped)
        viewModelScope.launch { settingsRepository.setFloat(SettingsKeys.VOCAL_BASS_DB, clamped.toFloat()) }
    }

    fun updateVocalTreble(db: Double) {
        val clamped = db.coerceIn(-12.0, 12.0)
        _vocalTrebleDb.value = clamped
        playerManager.setVocalTreble(clamped)
        viewModelScope.launch { settingsRepository.setFloat(SettingsKeys.VOCAL_TREBLE_DB, clamped.toFloat()) }
    }

    fun updateStereoBalance(value: Double) {
        val clamped = value.coerceIn(-1.0, 1.0)
        _stereoBalance.value = clamped
        playerManager.setBalance(clamped)
        viewModelScope.launch { settingsRepository.setFloat(SettingsKeys.STEREO_BALANCE, clamped.toFloat()) }
    }

    fun updateStereoExpansion(value: Double) {
        val clamped = value.coerceIn(0.0, 2.0)
        _stereoExpansion.value = clamped
        playerManager.setStereoExpansion(clamped)
        viewModelScope.launch { settingsRepository.setFloat(SettingsKeys.STEREO_EXPANSION, clamped.toFloat()) }
    }

    fun setMonoStereo(monoOn: Boolean) {
        _monoStereoOn.value = monoOn
        playerManager.setMonoStereo(monoOn)
        viewModelScope.launch { settingsRepository.setBool(SettingsKeys.MONO_STEREO_ON, monoOn) }
    }

    private fun persistShuffleOn(value: Boolean) {
        viewModelScope.launch { settingsRepository.setBool(SettingsKeys.SHUFFLE_ON, value) }
    }

    fun updateHeadroomSafetyRatio(value: Double) {
        val clamped = value.coerceIn(0.0, 1.0)
        _headroomSafetyRatio.value = clamped
        playerManager.setHeadroomSafetyRatio(clamped)
        viewModelScope.launch { settingsRepository.setFloat(SettingsKeys.HEADROOM_SAFETY_RATIO, clamped.toFloat()) }
    }

    fun playStream(url: String, title: String = "Live Stream") {
        playerManager.playStreamUrl(url, title)
        persistLastStream(url, title)
    }

    private fun persistLastStream(url: String, title: String) {
        viewModelScope.launch {
            settingsRepository.setString(SettingsKeys.LAST_STREAM_URL, url)
            settingsRepository.setString(SettingsKeys.LAST_STREAM_TITLE, title)
        }
    }

    private val _streamStations = MutableStateFlow<List<RadioStation>>(emptyList())
    val streamStations: StateFlow<List<RadioStation>> = _streamStations

    private val _currentStationId = MutableStateFlow<String?>(null)
    val currentStationId: StateFlow<String?> = _currentStationId

    fun setStreamStations(stations: List<RadioStation>) {
        _streamStations.value = stations
    }

    fun playStreamStation(station: RadioStation) {

        captureLibraryStateBeforeStream()
        _currentStationId.value = station.id
        pickSkinForStation(station)
        playerManager.playStreamUrl(station.streamUrl, station.name)
        persistLastStream(station.streamUrl, station.name)
    }

    private fun pickSkinFor(song: Song) {
        val hash = (song.id.toInt() and 0x7fffffff)
        _currentSkin.value = CassetteSkins[hash % CassetteSkins.size]
    }

    private fun pickSkinForStation(station: RadioStation) {
        val hash = (station.id.hashCode() and 0x7fffffff)
        _currentSkin.value = CassetteSkins[hash % CassetteSkins.size]
    }

    private fun persistPlaybackState(song: Song?, positionMs: Long) {
        if (song == null) return
        viewModelScope.launch {
            runCatching {
                settingsRepository.setString(
                    com.projectzero.tapeamp32.data.SettingsKeys.LAST_SONG_JSON,
                    songToJson(song)
                )
                settingsRepository.setLong(
                    com.projectzero.tapeamp32.data.SettingsKeys.LAST_POSITION_MS,
                    positionMs
                )
            }
        }
    }

    private fun songToJson(song: Song): String {
        val obj = org.json.JSONObject()
        obj.put("id", song.id)
        obj.put("title", song.title)
        obj.put("artist", song.artist)
        obj.put("album", song.album)
        obj.put("durationMs", song.durationMs)
        obj.put("uri", song.uri.toString())
        obj.put("path", song.path)
        obj.put("format", song.format)
        obj.put("bitDepthOrRate", song.bitDepthOrRate)
        return obj.toString()
    }

    private fun songFromJson(json: String): Song? {
        if (json.isBlank()) return null
        return runCatching {
            val obj = org.json.JSONObject(json)
            Song(
                id = obj.optLong("id"),
                title = obj.optString("title", "Unknown Title"),
                artist = obj.optString("artist", "Unknown Artist"),
                album = obj.optString("album", "Unknown Album"),
                durationMs = obj.optLong("durationMs"),
                uri = Uri.parse(obj.optString("uri")),
                path = obj.optString("path", ""),
                format = obj.optString("format", "FLAC"),
                bitDepthOrRate = obj.optString("bitDepthOrRate", "24-BIT / 96kHz")
            )
        }.getOrNull()
    }

    override fun onCleared() {
        playerManager.currentSong.value?.let {

            @Suppress("OPT_IN_USAGE")
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                runCatching {
                    settingsRepository.setString(
                        com.projectzero.tapeamp32.data.SettingsKeys.LAST_SONG_JSON,
                        songToJson(it)
                    )
                    settingsRepository.setLong(
                        com.projectzero.tapeamp32.data.SettingsKeys.LAST_POSITION_MS,
                        playerManager.positionMs.value
                    )
                }
            }
        }
        super.onCleared()
    }
}
