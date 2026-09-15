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

// BARU (v2.1): SLEEP TIMER. OFF = mati. MINUTES = hitung mundur durasi tetap (lihat
// sleepTimerRemainingMs). END_OF_TRACK = tidak menghitung mundur waktu, tapi berhenti
// begitu lagu yang SEDANG diputar saat ini selesai -- lihat catatan di startSleepTimerEndOfTrack().
enum class SleepTimerMode { OFF, MINUTES, END_OF_TRACK }

@UnstableApi
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val musicRepository = MusicRepository(application)

    // BARU (fitur "Jelajahi Radio")
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
    // BARU (fitur "hapus preset"): diekspos supaya EqualizerScreen bisa membedakan
    // preset custom (boleh dihapus) dari preset bawaan (tidak boleh) di dropdown --
    // sebelumnya cuma dipakai internal (private) untuk membangun _presets.
    val customPresets: StateFlow<List<EqPreset>> = _customPresets.asStateFlow()
    private val _presets = MutableStateFlow(BuiltInPresets)
    val presets: StateFlow<List<EqPreset>> = _presets.asStateFlow()

    private val _activePreset = MutableStateFlow(flatTenBandPreset())
    val activePreset: StateFlow<EqPreset> = _activePreset.asStateFlow()

    // BARU (v1.7): EQ per-lagu otomatis. _autoEqPerSong = toggle master di tab BATAS.
    // _songEqMap = "ingatan" preset per lagu (key lihat songKeyFor()) -- diisi otomatis
    // setiap kali user memilih preset dari dropdown SELAGU toggle ini aktif (lihat
    // applyPreset()), lalu diterapkan lagi otomatis begitu lagu yang sama diputar ulang
    // (lihat collector currentSong di init{}).
    private val _autoEqPerSong = MutableStateFlow(false)
    val autoEqPerSong: StateFlow<Boolean> = _autoEqPerSong.asStateFlow()

    private val _songEqMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val songEqMap: StateFlow<Map<String, String>> = _songEqMap.asStateFlow()

    // BARU (v1.9): REPLAY GAIN beneran. _replayGainOn = toggle master di tab BATAS.
    // _replayGainMap = "ingatan" gain (dB) hasil pengukuran per lagu (key lihat
    // songKeyFor()) -- diisi otomatis saat sebuah lagu SELESAI diukur untuk pertama
    // kalinya (lihat collector currentSong di init{} & applyReplayGainForSong()),
    // lalu dipakai lagi otomatis begitu lagu yang sama diputar ulang tanpa perlu
    // diukur ulang.
    private val _replayGainOn = MutableStateFlow(false)
    val replayGainOn: StateFlow<Boolean> = _replayGainOn.asStateFlow()

    private val _replayGainMap = MutableStateFlow<Map<String, Double>>(emptyMap())

    // BARU (v2.4): WAVEFORM SEEKBAR. Amplitude asli hasil decode WaveformExtractor
    // untuk lagu yang SEDANG diputar -- null selagi masih loading/gagal decode
    // (lihat collector di init{} & WaveformSeekBar untuk fallback tampilannya).
    private val _waveform = MutableStateFlow<FloatArray?>(null)
    val waveform: StateFlow<FloatArray?> = _waveform.asStateFlow()

    // Job decode waveform yang SEDANG berjalan (null kalau tidak ada) -- dibatalkan
    // begitu lagu berganti lagi sebelum decode-nya selesai, supaya tidak menumpuk
    // decode yang hasilnya toh bakal dibuang (mis. user skip cepat berkali-kali).
    private var waveformJob: kotlinx.coroutines.Job? = null

    // Key lagu yang SEDANG diukur (null kalau tidak ada pengukuran berjalan) --
    // dipakai untuk menyimpan hasilnya ke _replayGainMap begitu lagu berpindah.
    private var measuringReplayGainKey: String? = null

    // Gain (dB) tersimpan untuk lagu yang SEDANG diputar (null kalau belum pernah
    // terukur) -- dipakai EqualizerScreen buat menampilkan status.
    val currentSongReplayGainDb: StateFlow<Double?> = combine(
        playerManager.currentSong,
        _replayGainMap
    ) { song, map ->
        song?.let { map[songKeyFor(it)] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Nama preset tersimpan untuk lagu yang SEDANG diputar (null kalau belum pernah
    // disimpan) -- dipakai EqualizerScreen buat menampilkan status "tersimpan"/belum.
    val currentSongSavedPresetName: StateFlow<String?> = combine(
        playerManager.currentSong,
        _songEqMap
    ) { song, map ->
        song?.let { map[songKeyFor(it)] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // BARU: state untuk sub-menu EQUALIZER (tab "Nada"/tone & "Batas"/limiter) supaya
    // UI bisa menampilkan status toggle yang sebenarnya (sebelumnya setLimiterEnabled/
    // setEqBypass cuma "write-only" ke PlayerManager, tidak ada StateFlow buat dibaca UI).
    private val _limiterOn = MutableStateFlow(true)
    val limiterOn: StateFlow<Boolean> = _limiterOn.asStateFlow()

    private val _eqBypassOn = MutableStateFlow(false)
    val eqBypassOn: StateFlow<Boolean> = _eqBypassOn.asStateFlow()

    // BARU (v1.6): state untuk toggle "BIT-PERFECT MODE" di tab BATAS -- pola sama
    // persis dengan limiterOn/eqBypassOn di atas.
    private val _bitPerfectOn = MutableStateFlow(false)
    val bitPerfectOn: StateFlow<Boolean> = _bitPerfectOn.asStateFlow()

    // BARU (patch "Crossfade"): state untuk toggle "CROSSFADE" + durasi overlap-nya
    // di tab BATAS -- pola sama persis dengan limiterOn/bitPerfectOn di atas.
    private val _crossfadeOn = MutableStateFlow(false)
    val crossfadeOn: StateFlow<Boolean> = _crossfadeOn.asStateFlow()

    private val _crossfadeSeconds = MutableStateFlow(4f)
    val crossfadeSeconds: StateFlow<Float> = _crossfadeSeconds.asStateFlow()

    // BARU (patch "DSP control knobs"): state untuk sub-menu VOCAL (tombol vocal + knop
    // bass/treble khusus vokal) dan STEREO (Balance, Stereo Expansion, Mono/Stereo) di
    // EqualizerScreen -- pola StateFlow-nya sama persis dengan limiterOn/eqBypassOn di
    // atas, cuma dipersist ke DataStore (lihat init{} & fungsi setter di bawah) supaya
    // posisi knop tidak selalu kembali ke tengah/default tiap app dibuka ulang.
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

    // BARU (patch "headroom slider"): state untuk slider "MAX LOUDNESS <-> SAFE
    // HEADROOM" di tab BATAS (EqualizerScreen) -- pola StateFlow-nya sama persis
    // dengan stereoExpansion di atas. Default 0.3 HARUS sama dengan default
    // headroomSafetyRatio di ParametricEqAudioProcessor & SettingsRepository, lihat
    // catatan di masing-masing deklarasinya.
    private val _headroomSafetyRatio = MutableStateFlow(0.3)
    val headroomSafetyRatio: StateFlow<Double> = _headroomSafetyRatio.asStateFlow()

    private val _currentSkin = MutableStateFlow(CassetteSkins.first())
    val currentSkin: StateFlow<CassetteSkin> = _currentSkin.asStateFlow()

    // FITUR BARU: volume lewat swipe vertikal di kaset (CassetteDeck / PlayerScreen).
    // Disimpan di sini (bukan cuma di Composable) supaya nilainya tetap konsisten kalau
    // pengguna pindah-pindah tab lalu balik lagi ke PLAYER. Belum dipersist ke
    // SettingsRepository -- reset ke 100% tiap app dibuka ulang, cukup untuk patch kecil ini.
    private val _volume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    // BARU (v1.2): fitur PLAYLIST -- daftar playlist buatan pengguna, dipulihkan dari
    // DataStore di init{} (lihat bawah) dan disimpan ulang tiap kali ada perubahan
    // (create/rename/delete/add-song/remove-song), pola sama dengan _customPresets.
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    // BARU (v2.1): SLEEP TIMER. _sleepTimerMode = status aktif sekarang (lihat
    // SleepTimerMode). _sleepTimerRemainingMs hanya relevan saat mode == MINUTES --
    // dihitung mundur oleh sleepTimerJob tiap 1 detik. Keduanya TIDAK dipersist (lihat
    // catatan SettingsRepository.sleepTimerMin) -- sengaja hilang kalau app ditutup total,
    // sama seperti timer alarm/dapur fisik, bukan sesuatu yang harus "resume" otomatis.
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

    // BARU (patch "streaming label"): lihat catatan di PlayerManager._currentStreamTitle.
    val currentStreamTitle get() = playerManager.currentStreamTitle
    val lastError get() = playerManager.lastError
    fun clearError() = playerManager.clearError()

    /* ============================================================
     * BARU (patch "cassette side A/B") -- CASSETTE SIDE
     * ------------------------------------------------------------
     * Side A = Library, Side B = Stream. SENGAJA di-DERIVE langsung dari
     * currentStreamTitle (bukan state terpisah yang harus di-set manual di
     * tiap pemanggil) -- supaya label sisi kaset SELALU sinkron dengan sumber
     * audio yang benar-benar aktif, siapa pun/apa pun yang memicunya: double
     * tap manual di sini, pilih stasiun dari StreamingScreen, next/prev di
     * tape deck, ATAU nanti kalau ada fitur auto-resume stream dari Settings
     * saat startup -- begitu playStreamUrl() dipanggil dari jalur mana pun,
     * currentStreamTitle otomatis terisi dan kaset otomatis pindah ke Side B
     * tanpa perlu masing-masing pemanggil mengurus toggle-nya sendiri.
     * ============================================================ */
    val cassetteSide: StateFlow<String> =
        playerManager.currentStreamTitle
            .map { if (it != null) "B" else "A" }
            .stateIn(viewModelScope, SharingStarted.Eagerly, "A")

    // Dipulihkan/di-cache di sini supaya kalau user double-tap balik ke Side A dari
    // tengah-tengah streaming, lagu (+posisi) yang sedang diputar SEBELUM pindah ke
    // stream bisa dilanjutkan lagi -- bukan cuma balik ke label doang tanpa audio.
    private var songBeforeStream: Song? = null
    private var positionBeforeStreamMs: Long = 0L

    // FIX (bug "double-tap balik ke Library gak jalan kalau stream dimulai dari
    // layar Streaming, bukan dari double-tap kaset"): songBeforeStream &
    // positionBeforeStreamMs sebelumnya CUMA diisi di dalam toggleCassetteSide()
    // (jalur A -> B lewat double-tap kaset). Kalau user memulai stream langsung
    // dari layar Settings > Streaming (tap salah satu URL di list, lewat
    // playStreamStation()), dua variabel itu TIDAK PERNAH terisi sama sekali --
    // begitu user balik ke tape deck & double-tap kaset (Side B -> A),
    // toggleCassetteSide() menemukan songBeforeStream == null lalu jatuh ke
    // playerManager.stop() -- BUKAN kembali ke lagu Library manapun, kelihatan
    // seperti double-tap "tidak menuju ke lagu Library" sama sekali/aneh.
    //
    // Fungsi ini sekarang dipanggil di SETIAP jalur yang bisa memulai/memindahkan
    // stream (tap list di StreamingScreen, next/prev antar stasiun, double-tap
    // kaset) -- tapi HANYA benar-benar menyimpan snapshot kalau saat ini MASIH di
    // Side A (Library). Guard ini penting: begitu sudah streaming (Side B) dan
    // user pindah-pindah stasiun lewat next/prev/tap list lain, currentSong sudah
    // null (bukan lagu Library lagi) -- tanpa guard ini, snapshot lagu Library yang
    // ASLI akan ketimpa null/salah setiap kali ganti stasiun.
    private fun captureLibraryStateBeforeStream() {
        if (cassetteSide.value == "A") {
            songBeforeStream = currentSong.value
            positionBeforeStreamMs = positionMs.value
        }
    }

    /**
     * Double tap di kaset (CassetteDeck) untuk pindah Side A (Library) <-> Side B
     * (Stream). Lihat catatan di [cassetteSide] soal kenapa label-nya sendiri tidak
     * di-set manual di sini -- fungsi ini HANYA mengurus efek sampingnya: mulai/
     * hentikan playback yang sesuai, currentStreamTitle yang berubah lewat playSong()/
     * playStreamUrl() di bawah ini yang bikin cassetteSide ikut berubah otomatis.
     */
    fun toggleCassetteSide() {
        if (cassetteSide.value == "A") {
            // A -> B: coba lanjutkan stream TERAKHIR yang tersimpan (lihat
            // LAST_STREAM_URL/LAST_STREAM_TITLE di SettingsRepository). Kalau belum
            // pernah streaming sama sekali, tidak ada yang bisa ditebak -- cukup buka
            // layar Streaming supaya user pilih sendiri.
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
            // B -> A: hentikan stream, kembalikan lagu library yang sedang diputar
            // sebelum pindah ke Side B (kalau ada), di posisi terakhirnya.
            //
            // FIX (bug "prev/next diam setelah balik dari Stream, cuma skin yang
            // ganti"): sebelumnya di sini dipanggil restoreSong(song, ...) yang
            // cuma memuat SATU MediaItem ke ExoPlayer -- itu menimpa timeline
            // ExoPlayer (player.mediaItemCount) jadi 1 item, padahal _queue &
            // queueIndex di ViewModel ini tetap utuh berisi seluruh antrian
            // Library. Akibatnya next()/previous() tetap menghitung queueIndex
            // & ganti skin dengan benar (pickSkinFor jalan), tapi
            // seekToQueueItem(queueIndex) yang dipanggil setelahnya selalu
            // gagal diam-diam karena index-nya di luar jangkauan
            // mediaItemCount == 1 milik ExoPlayer -- audio tidak pernah
            // benar-benar pindah lagu. Sekarang antrian PENUH dimuat ulang
            // lewat setQueue() persis di index & posisi lagu ini berada,
            // supaya timeline ExoPlayer sinkron lagi dengan _queue dan
            // tombol next/prev berfungsi normal seperti sebelum pindah ke
            // Stream.
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
                    // Fallback: lagu ini sudah tidak ada lagi di _queue (mis. antrian
                    // berubah selagi streaming) -- minimal pulihkan lagunya sendiri
                    // seperti perilaku lama, walau next/prev tidak akan berfungsi
                    // sampai antrian dimuat ulang dari Library.
                    playerManager.restoreSong(song, positionBeforeStreamMs)
                }
            } else {
                playerManager.stop()
            }
            songBeforeStream = null
        }
    }

    // BARU: untuk Status Panel VFD (di bawah VU Meter) -- status hardware USB DAC dan
    // status DSP Audio Engine, di-passthrough dari PlayerManager persis seperti vuLevels.
    val isUsbDacConnected get() = playerManager.usbDacObserver.isUsbDacConnected
    val connectedDacName get() = playerManager.usbDacObserver.connectedDacName
    val dspEngineOn get() = playerManager.dspEngineOn
    val peakActive get() = playerManager.peakActive

    // BARU (v1.8): status offload hardware AKTUAL, untuk Status Panel VFD dan tab
    // BATAS di Equalizer (lihat PlayerManager.offloadActive).
    val offloadActive get() = playerManager.offloadActive

    // BARU: Hi-Res Audio Badge Detector, untuk Status Panel VFD.
    val isHiRes get() = playerManager.isHiRes
    val sampleRate get() = playerManager.sampleRate
    val bitDepth get() = playerManager.bitDepth

    // FIX (bug "bitrate selalu 0" di halaman Streaming): bitrate SUNGGUHAN yang
    // sedang didecode saat ini (icy-br server / container), lihat PlayerManager.
    val streamBitrateKbps get() = playerManager.streamBitrateKbps

    init {
        // Polling posisi playback ~15fps untuk kehalusan animasi pita kaset & slider.
        // Sekalian jadi tempat menyimpan lagu + posisi terakhir secara berkala (tiap ~3 detik
        // saat sedang main), supaya kalau app ditutup paksa (bukan lewat tombol pause/back),
        // posisi terakhir yang tersimpan tetap mendekati posisi asli.
        viewModelScope.launch {
            var tick = 0
            while (true) {
                playerManager.pollPosition()
                tick++
                if (tick % 45 == 0 && playerManager.isPlaying.value) { // ~45 * 66ms ≈ 3 detik
                    persistPlaybackState(playerManager.currentSong.value, playerManager.positionMs.value)
                }
                delay(66)
            }
        }

        // Simpan juga setiap kali playback berhenti/pause (termasuk lagu selesai), supaya
        // posisi yang tersimpan akurat begitu pengguna menekan PAUSE lalu menutup app.
        viewModelScope.launch {
            playerManager.isPlaying.collect { playing ->
                if (!playing) {
                    playerManager.currentSong.value?.let {
                        persistPlaybackState(it, playerManager.positionMs.value)
                    }
                }
            }
        }

        // Lagu selesai sendiri (bukan di-pause/stop manual) -> lanjut ke lagu berikutnya
        // sesuai RepeatMode. Sebelumnya tidak ada apa pun yang terhubung ke sini, jadi
        // playback berhenti total begitu satu lagu habis.
        playerManager.onSongEnded = {
            viewModelScope.launch { handleTrackEnded() }
        }

        // FIX (statusbar/lockscreen controlbar): setiap kali lagu berpindah di dalam
        // ExoPlayer -- termasuk saat dipicu dari LUAR app (tombol next/prev di notifikasi,
        // lockscreen, headset, Bluetooth, Android Auto) -- samakan queueIndex ViewModel
        // dengan index ExoPlayer yang sesungguhnya. Tanpa ini, setelah user pindah lagu
        // dari lockscreen, tombol Next/Prev DI DALAM app akan salah hitung dari index lama.
        viewModelScope.launch {
            playerManager.currentQueueIndex.collect { idx ->
                if (idx in _queue.value.indices) queueIndex = idx
            }
        }

        // Pulihkan lagu terakhir + posisi terakhir, supaya saat app dibuka lagi, layar player
        // langsung menunjukkan lagu & posisi terakhir persis seperti sebelum app ditutup.
        // Lagu dimuat tapi TIDAK langsung diputar (menunggu pengguna menekan PLAY).
        viewModelScope.launch {
            val lastSongJson = runCatching { settingsRepository.lastSongJson.first() }.getOrDefault("")
            val lastPositionMs = runCatching { settingsRepository.lastPositionMs.first() }.getOrDefault(0L)
            songFromJson(lastSongJson)?.let { song ->
                pickSkinFor(song)
                playerManager.restoreSong(song, lastPositionMs)
                // FIX (bug "Next tidak ganti lagu, harus shuffle di Library dulu"): coroutine
                // ini jalan BERSAMAAN (race) dengan coroutine pemulihan cache library di bawah
                // (lihat syncQueueWithLibrary). Kalau cache library kebetulan selesai duluan
                // dan sudah mengisi _queue dengan SELURUH isi library, baris lama di sini
                // (`_queue.value = listOf(song)`) langsung MENIMPA-nya jadi queue isi 1 lagu
                // saja -- next()/previous() sesudahnya cuma "muter" di lagu itu-itu terus
                // (modulo 1), kelihatan seperti macet, sampai pengguna menekan Shuffle All di
                // Library (yang membangun queue baru dari _library.value, bukan dari _queue).
                //
                // Sekarang: kalau queue SUDAH berisi lagu ini (berarti cache library menang
                // race & sudah benar), cukup samakan queueIndex ke posisinya di queue itu --
                // JANGAN ganti isi queue. Queue isi 1 lagu hanya dipakai sebagai placeholder
                // sementara selama library/cache belum sempat dimuat sama sekali.
                val existingIdx = _queue.value.indexOfFirst { it.id == song.id }
                if (existingIdx >= 0) {
                    queueIndex = existingIdx
                } else {
                    _queue.value = listOf(song)
                    queueIndex = 0
                }
            }
        }

        // FIX (bug "next/prev diam untuk sebagian URL radio sampai dibuka dulu dari
        // Streaming list"): _streamStations sebelumnya HANYA diisi lewat
        // setStreamStations(), yang cuma dipanggil dari LaunchedEffect di
        // StreamingScreen -- artinya kalau user belum pernah membuka tab Streaming
        // sama sekali di sesi ini lalu langsung double-tap kaset ke Side B, kode di
        // toggleCassetteSide() mencoba mencocokkan URL stream terakhir ke
        // _streamStations.value yang MASIH KOSONG, sehingga _currentStationId tetap
        // null. Begitu _currentStationId null, tryAdvanceStreamStation() SELALU
        // gagal (guard stations.isEmpty()/id null) -- next/prev di tape deck jadi
        // diam terus untuk stream itu, sampai user membuka Streaming screen &
        // TAP LANGSUNG salah satu stasiun di list (yang memanggil playStreamStation()
        // dan barulah mengisi _currentStationId dengan benar). Sekarang daftar
        // stasiun custom yang tersimpan dimuat di sini juga, saat ViewModel pertama
        // kali dibuat -- persis seperti StreamingScreen memuatnya sendiri -- supaya
        // _streamStations sudah terisi lebih dulu, TANPA harus menunggu user membuka
        // tab Streaming sama sekali.
        viewModelScope.launch {
            val savedJson = runCatching { settingsRepository.customStationsJson.first() }.getOrDefault("")
            val restoredStations = deserializeRadioStations(savedJson)
            if (_streamStations.value.isEmpty() && restoredStations.isNotEmpty()) {
                _streamStations.value = SampleFavoriteStations + restoredStations
            }
        }

        // Pulihkan preset custom/hasil upload & preset aktif terakhir dari DataStore,
        // supaya tidak selalu kembali ke "Flat" setiap kali aplikasi dibuka ulang.
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

        // BARU (patch "DSP control knobs"): pulihkan posisi Vocal + Balance/Stereo
        // Expansion/Mono dari DataStore, sama seperti preset EQ custom di atas, lalu
        // teruskan ke ParametricEqAudioProcessor lewat PlayerManager supaya DSP-nya
        // langsung sesuai begitu lagu pertama diputar (tidak menunggu user membuka
        // tab VOCAL/STEREO dulu).
        viewModelScope.launch {
            val restoredVocalOn = runCatching { settingsRepository.vocalEnabled.first() }.getOrDefault(false)
            val restoredVocalBass = runCatching { settingsRepository.vocalBassDb.first() }.getOrDefault(0f).toDouble()
            val restoredVocalTreble = runCatching { settingsRepository.vocalTrebleDb.first() }.getOrDefault(0f).toDouble()
            val restoredBalance = runCatching { settingsRepository.stereoBalance.first() }.getOrDefault(0f).toDouble()
            val restoredExpansion = runCatching { settingsRepository.stereoExpansion.first() }.getOrDefault(1f).toDouble()
            val restoredMono = runCatching { settingsRepository.monoStereoOn.first() }.getOrDefault(false)
            // BARU (patch "headroom slider")
            val restoredHeadroomSafetyRatio = runCatching { settingsRepository.headroomSafetyRatio.first() }.getOrDefault(0.3f).toDouble()

            _vocalOn.value = restoredVocalOn
            _vocalBassDb.value = restoredVocalBass
            _vocalTrebleDb.value = restoredVocalTreble
            _stereoBalance.value = restoredBalance
            _stereoExpansion.value = restoredExpansion
            _monoStereoOn.value = restoredMono
            _headroomSafetyRatio.value = restoredHeadroomSafetyRatio

            playerManager.setVocalEnabled(restoredVocalOn)
            playerManager.setVocalBass(restoredVocalBass)
            playerManager.setVocalTreble(restoredVocalTreble)
            playerManager.setBalance(restoredBalance)
            playerManager.setStereoExpansion(restoredExpansion)
            playerManager.setMonoStereo(restoredMono)
            playerManager.setHeadroomSafetyRatio(restoredHeadroomSafetyRatio)
        }

        // FIX (v1.4.1): sebelumnya Peak Limiter HANYA dipulihkan dari DataStore lewat
        // LaunchedEffect(peakLimiter) di SettingsScreen -- kalau user tidak pernah
        // membuka layar Settings setelah app dibuka ulang, _limiterOn diam-diam balik
        // ke default `true` walau user sudah pernah mematikannya lewat tab BATAS di
        // Equalizer. Baris duplikat "Peak Limiter" di Settings sudah dihapus (kontrol
        // limiter sekarang HANYA di tab BATAS), jadi pemulihan dipindah ke sini --
        // pola sama seperti preset EQ / Vocal-Stereo di atas.
        viewModelScope.launch {
            val restoredLimiter = runCatching { settingsRepository.peakLimiter.first() }.getOrDefault(true)
            _limiterOn.value = restoredLimiter
            playerManager.setLimiterEnabled(restoredLimiter)
        }

        // BARU (v1.6): pulihkan BIT-PERFECT MODE, pola sama dengan Peak Limiter di atas.
        viewModelScope.launch {
            val restoredBitPerfect = runCatching { settingsRepository.bitPerfectMode.first() }.getOrDefault(false)
            _bitPerfectOn.value = restoredBitPerfect
            playerManager.setBitPerfectMode(restoredBitPerfect)
        }

        // BARU (patch "Crossfade"): pulihkan toggle + durasi, pola sama dengan
        // Peak Limiter/BIT-PERFECT MODE di atas.
        viewModelScope.launch {
            val restoredOn = runCatching { settingsRepository.crossfadeEnabled.first() }.getOrDefault(false)
            val restoredSeconds = runCatching { settingsRepository.fadeSeconds.first() }.getOrDefault(4f)
            _crossfadeOn.value = restoredOn
            _crossfadeSeconds.value = restoredSeconds
            playerManager.setCrossfadeSeconds(restoredSeconds)
            playerManager.setCrossfadeEnabled(restoredOn)
        }

        // BARU (v1.4): pulihkan Theme Accent Color tersimpan ke ThemeAccentState.current
        // supaya seluruh UI (border/ikon/teks emas di Settings, Equalizer, Player, dst)
        // langsung memakai accent terakhir yang dipilih user, bukan selalu balik ke
        // Gold Retro tiap app dibuka ulang.
        viewModelScope.launch {
            val savedAccentLabel = runCatching { settingsRepository.themeAccent.first() }.getOrDefault("Gold Retro")
            ThemeAccentState.current = ThemeAccent.fromLabel(savedAccentLabel)
        }

        // BARU (v1.2): pulihkan playlist tersimpan dari DataStore, sama seperti preset
        // EQ custom di atas.
        viewModelScope.launch {
            val restoredPlaylists = runCatching {
                val json = settingsRepository.playlistsJson.first()
                if (json.isBlank()) emptyList() else PlaylistUtils.deserializeList(json)
            }.getOrDefault(emptyList())
            _playlists.value = restoredPlaylists
        }

        // Library TIDAK lagi di-scan otomatis dari seluruh penyimpanan device.
        // Satu-satunya scan otomatis yang boleh terjadi adalah re-scan folder yang
        // SEBELUMNYA sudah dipilih sendiri oleh pengguna (via SAF), dan hanya jika
        // toggle "Auto-Scan Specified Folder on Startup" di Settings aktif.
        // Kalau belum pernah pilih folder / toggle dimatikan, library kosong sampai
        // pengguna memilih folder secara manual dari layar Import.
        //
        // BARU (patch "cache library cepat"): sebelumnya app SELALU menunggu
        // scanFolder() penuh (traversal SAF + metadata) selesai dulu baru pengguna
        // lihat lagu apa pun -- makanya kerasa "scan lagu lama banget" tiap buka app,
        // padahal isinya kemungkinan besar SAMA dengan terakhir kali dibuka. Sekarang:
        // 1) cache hasil scan TERAKHIR (JSON di DataStore) dimuat & ditampilkan dulu,
        //    INSTAN, tanpa nunggu SAF/metadata apa pun -- inilah yang bikin buka app
        //    langsung "walla" lihat daftar lagu.
        // 2) scan folder yang sebenarnya tetap jalan sesudahnya di background untuk
        //    menangkap lagu baru/terhapus. Kalau cache TADI sudah ada isinya, scan ini
        //    jalan DIAM-DIAM (showProgress=false, tidak ada spinner) karena pengguna
        //    sudah melihat sesuatu; hasilnya otomatis menimpa tampilan & cache begitu
        //    selesai lewat syncQueueWithLibrary. Kalau belum ada cache sama sekali
        //    (install baru / cache pernah dihapus), scan tetap tampilkan progress
        //    seperti biasa karena layar memang masih kosong.
        viewModelScope.launch {
            val cachedJson = runCatching { settingsRepository.libraryCacheJson.first() }.getOrDefault("")
            val cachedSongs = if (cachedJson.isBlank()) {
                emptyList()
            } else {
                runCatching { musicRepository.deserializeSongs(cachedJson) }.getOrDefault(emptyList())
            }
            if (cachedSongs.isNotEmpty()) {
                // updateCache=false: ini cuma memuat ulang apa yang SUDAH tersimpan,
                // tidak perlu ditulis balik ke key yang sama.
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

        // BARU (v1.7): pulihkan toggle "AUTO EQ PER LAGU" + peta preset per lagu dari
        // DataStore, pola sama seperti preset EQ custom / playlist di atas.
        viewModelScope.launch {
            val restoredAutoEq = runCatching { settingsRepository.autoEqPerSong.first() }.getOrDefault(false)
            val restoredMapJson = runCatching { settingsRepository.songEqPresetMapJson.first() }.getOrDefault("")
            _autoEqPerSong.value = restoredAutoEq
            _songEqMap.value = parseSongEqMap(restoredMapJson)
        }

        // BARU (v1.7): terapkan otomatis preset EQ tersimpan setiap kali lagu berpindah
        // -- baik dipicu dari dalam app, next/prev, maupun lockscreen/notifikasi (sama
        // seperti collector currentQueueIndex di atas). Kalau AUTO EQ PER LAGU mati,
        // atau lagu yang baru diputar belum pernah punya preset tersimpan, preset yang
        // sedang aktif TIDAK disentuh sama sekali.
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

        // BARU (v1.9): REPLAY GAIN beneran. Pulihkan toggle master + peta gain
        // tersimpan dari DataStore, pola sama dengan AUTO EQ PER LAGU di atas.
        viewModelScope.launch {
            val restoredOn = runCatching { settingsRepository.replayGain.first() }.getOrDefault(false)
            val restoredMapJson = runCatching { settingsRepository.replayGainMapJson.first() }.getOrDefault("")
            _replayGainOn.value = restoredOn
            _replayGainMap.value = parseReplayGainMap(restoredMapJson)
            playerManager.setReplayGainEnabled(restoredOn)
            // Terapkan/ukur langsung untuk lagu yang sudah dipulihkan (restoreSong di
            // atas berjalan lebih dulu), supaya tidak menunggu lagu berikutnya dulu.
            applyReplayGainForSong(playerManager.currentSong.value)
        }

        // BARU (v1.9): setiap kali lagu berpindah -- baik dari dalam app, next/prev,
        // maupun lockscreen/notifikasi -- selesaikan dulu pengukuran untuk lagu yang
        // TADI diputar (kalau ada), simpan gain-nya, baru siapkan gain/pengukuran
        // untuk lagu yang BARU. Pola alurnya sama dengan collector AUTO EQ PER LAGU
        // di atas, cuma di sini butuh urutan selesai-dulu-baru-mulai karena
        // pengukuran (bukan cuma pemasangan preset) yang sedang berjalan.
        //
        // CATATAN JUJUR: karena sample PCM diukur di dalam AudioProcessor (mengalir
        // lewat buffer decode ExoPlayer, bukan real-time 1:1 dengan posisi playback
        // yang terlihat di UI), pengukuran bisa sedikit meleset di detik-detik
        // terakhir kalau lagu di-skip sangat cepat -- cukup akurat untuk pemakaian
        // normal (lagu didengar sampai habis atau di-skip di tengah/akhir).
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

        // BARU (v2.4): WAVEFORM SEEKBAR. Setiap kali lagu berpindah, decode waveform
        // amplitude aslinya di background (Dispatchers.IO) lewat WaveformExtractor --
        // dicek dulu ke WaveformCache (memori DAN disk, lihat WaveformExtractor.kt)
        // supaya lagu yang sudah pernah diputar -- baik sesi ini (Previous, replay,
        // dst) MAUPUN sesi sebelumnya sebelum app ditutup total -- tidak decode ulang.
        // Selagi menunggu, _waveform di-set null dulu supaya WaveformSeekBar otomatis
        // balik ke tampilan fallback garis datar (tidak menampilkan waveform lagu
        // SEBELUMNYA yang salah sambil menunggu).
        viewModelScope.launch {
            playerManager.currentSong.collect { song ->
                waveformJob?.cancel()
                _waveform.value = null

                if (song == null) return@collect

                // Baca cache (memori/disk) di IO dispatcher -- baca file kecil di disk
                // tetap I/O, jangan blok main thread walau biasanya sangat cepat.
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
                        // Cek ulang lagu masih sama -- kalau sudah keburu ganti lagi
                        // (job ini seharusnya sudah di-cancel di atas, tapi dobel jaga
                        // supaya tidak ada race hasil decode lama nyasar ke lagu baru).
                        if (playerManager.currentSong.value?.id == song.id) {
                            _waveform.value = data
                        }
                    }
                }
            }
        }
    }

    // BARU (v1.9): pasang gain REPLAY GAIN untuk lagu yang sedang/akan diputar --
    // kalau sudah pernah terukur, pakai langsung; kalau belum, mulai ukur sambil
    // lagu ini diputar (hasilnya baru tersimpan setelah lagu ini selesai/berpindah,
    // lihat collector currentSong di init{}).
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

    // BARU (v1.9): nyala/matikan REPLAY GAIN dari tab BATAS -- pola sama dengan
    // setAutoEqPerSong. Mematikan toggle TIDAK menghapus peta gain yang sudah
    // tersimpan (cuma berhenti diterapkan/diukur), jadi kalau dinyalakan lagi
    // nanti, lagu yang sudah pernah terukur tidak perlu diukur ulang.
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

    // BARU (v1.9): lupakan gain tersimpan untuk lagu yang sedang diputar (tombol
    // "HAPUS" di status EqualizerScreen, sama seperti forgetEqForCurrentSong) --
    // lagu ini akan diukur ulang dari awal di pemutaran berikutnya.
    fun forgetReplayGainForCurrentSong() {
        val song = playerManager.currentSong.value ?: return
        val key = songKeyFor(song)
        if (!_replayGainMap.value.containsKey(key)) return
        _replayGainMap.value = _replayGainMap.value - key
        persistReplayGainMap()
    }

    // BARU (v1.7): kunci identitas lagu untuk peta EQ per-lagu -- pakai path berkas
    // (stabil selama file tidak dipindah/rescan ulang dari folder berbeda), fallback ke
    // "judul|artis" kalau path kosong (mis. sumber lagu yang tidak menyertakan path).
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

    // BARU (v1.7): nyala/matikan EQ per-lagu otomatis dari tab BATAS. Mematikan toggle
    // ini TIDAK menghapus peta preset yang sudah tersimpan -- cuma berhenti diterapkan
    // otomatis, supaya kalau dinyalakan lagi nanti, ingatan lama tetap ada.
    fun setAutoEqPerSong(enabled: Boolean) {
        _autoEqPerSong.value = enabled
        viewModelScope.launch {
            settingsRepository.setBool(SettingsKeys.AUTO_EQ_PER_SONG, enabled)
        }
    }

    // BARU (v1.7): lupakan preset tersimpan untuk lagu yang sedang diputar (tombol
    // "HAPUS" di status EqualizerScreen) -- preset yang SEDANG aktif tidak berubah,
    // cuma lagu ini tidak lagi auto-ganti preset di pemutaran berikutnya.
    fun forgetEqForCurrentSong() {
        val song = playerManager.currentSong.value ?: return
        if (!_songEqMap.value.containsKey(songKeyFor(song))) return
        _songEqMap.value = _songEqMap.value - songKeyFor(song)
        persistSongEqMap()
    }

    fun navigate(screen: Screen) {
        currentScreen.value = screen
    }

    /**
     * Pemindaian pustaka penuh (MediaStore, seluruh device). Dipertahankan sebagai fungsi
     * manual (opsional) saja — TIDAK dipanggil otomatis di mana pun lagi, karena itulah
     * penyebab "auto scan semua file audio" yang tidak diinginkan sebelumnya.
     */
    fun refreshLibrary() {
        viewModelScope.launch {
            _isScanning.value = true
            _scanProgress.value = 0.3f

            val songs = applyIgnoreShortTracksFilter(musicRepository.scanLibrary())
            _scanProgress.value = 0.8f
            delay(200) // Efek transisi pemindaian halus

            syncQueueWithLibrary(songs)

            _scanProgress.value = 1f
            _isScanning.value = false
        }
    }

    // FIX (v1.4.1): sebelumnya toggle "Ignore Short Audio Tracks (< 30s)" di Settings
    // tersimpan ke DataStore tapi TIDAK PERNAH dibaca di mana pun -- hasil scan selalu
    // memasukkan semua lagu apa pun durasinya. Sekarang benar-benar dipakai untuk
    // menyaring hasil scan di refreshLibrary() maupun scanFolder(). Lagu dengan durasi
    // TIDAK diketahui (durationMs <= 0, mis. metadata gagal dibaca) sengaja tetap
    // dipertahankan supaya lagu yang valid tidak salah kebuang gara-gara metadata rusak.
    private suspend fun applyIgnoreShortTracksFilter(songs: List<Song>): List<Song> {
        val ignoreShort = runCatching { settingsRepository.ignoreShortTracks.first() }.getOrDefault(true)
        if (!ignoreShort) return songs
        return songs.filter { it.durationMs <= 0L || it.durationMs >= 30_000L }
    }

    /**
     * FIX UTAMA "tombol Next cuma memutar lagu yang sama itu-itu saja": sebelumnya baris ini
     * cuma `if (_queue.value.isEmpty()) _queue.value = songs`. Begitu app dibuka, blok restore
     * di init{} SUDAH mengisi _queue dengan 1 lagu (lagu terakhir), jadi _queue TIDAK PERNAH
     * kosong lagi -> pemindaian folder yang selesai belakangan (auto-scan saat startup ATAU
     * scan manual) tidak pernah benar-benar mengisi antrian dengan seluruh lagu di folder.
     * Akibatnya next()/previous() cuma muter-muter di antrian isi 1 lagu itu (modulo 1).
     *
     * Sekarang antrian SELALU disamakan dengan hasil scan folder/library terbaru begitu
     * pemindaian selesai, dengan posisi lagu yang sedang diputar (kalau ada & ditemukan di
     * daftar) dipertahankan. Seluruh antrian ini juga dimuat ke ExoPlayer lewat
     * [PlayerManager.setQueue] supaya tombol next/prev di statusbar/lockscreen ikut berfungsi.
     */
    private suspend fun syncQueueWithLibrary(songs: List<Song>, updateCache: Boolean = true) {
        _library.value = songs
        if (songs.isEmpty()) return

        val current = playerManager.currentSong.value
        val idx = current?.let { c -> songs.indexOfFirst { it.id == c.id } }?.takeIf { it >= 0 } ?: 0

        // FIX (bug "shuffle kadang tidak benar-benar shuffle"): sama seperti di playSong(),
        // baris ini dulu SELALU menimpa _queue.value dengan [songs] urutan asli, tanpa cek
        // _shuffleOn -- jadi kalau ada rescan folder (auto-scan startup atau manual) SAAT
        // shuffle sedang menyala, antrian diam-diam balik berurutan padahal toggle Shuffle
        // di UI tetap ON. Sekarang ikut diacak (lagu yang sedang jalan dipertahankan di
        // posisinya yang baru, bukan dipindah ke depan, karena rescan bukan aksi "pilih
        // lagu baru" seperti di playSong()).
        val queueToUse = if (_shuffleOn.value) ShuffleQueue.shuffled(songs) else songs
        val finalIdx = if (_shuffleOn.value) {
            current?.let { c -> queueToUse.indexOfFirst { it.id == c.id } }?.takeIf { it >= 0 } ?: 0
        } else idx

        _queue.value = queueToUse
        queueIndex = finalIdx

        val resumePositionMs = if (current != null) playerManager.positionMs.value else 0L
        // playWhenReady mengikuti status main saat ini, supaya scan folder yang jalan di
        // background (mis. auto-scan saat startup) TIDAK diam-diam mulai memutar musik.
        playerManager.setQueue(queueToUse, finalIdx, resumePositionMs, playWhenReady = playerManager.isPlaying.value)

        // BARU (patch "cache library cepat"): simpan hasil scan TERBARU ini ke
        // DataStore supaya lain kali app dibuka, daftar lagu bisa langsung ditampilkan
        // dari cache ini dulu (instan) sebelum scan folder yang sebenarnya selesai --
        // lihat init{}. updateCache=false dipakai saat kita cuma MEMUAT cache lama itu
        // sendiri ke layar (tidak perlu ditulis balik ke tempat yang sama).
        if (updateCache) {
            runCatching {
                settingsRepository.setString(
                    SettingsKeys.LIBRARY_CACHE_JSON,
                    musicRepository.serializeSongs(songs)
                )
            }
        }
    }

    /**
     * Pemindaian HANYA folder yang dipilih pengguna sendiri lewat Storage Access Framework
     * (lihat pickFolderLauncher di MainActivity). Ini yang dipakai untuk "scan sesuai folder
     * yang kumau saja" — tidak menyentuh isi penyimpanan lain di luar folder tsb.
     *
     * @param persist simpan URI folder ini ke DataStore supaya bisa di-restore lagi saat
     * app dibuka ulang (tunduk pada toggle "Auto-Scan Specified Folder on Startup").
     * @param showProgress kalau false, scan jalan DIAM-DIAM di background -- tidak
     * menyalakan _isScanning/_scanProgress (jadi tidak ada spinner/progress bar blocking
     * di UI). Dipakai untuk refresh otomatis diam-diam di startup ketika layar SUDAH
     * menampilkan hasil dari cache disk (lihat init{}) -- pengguna sudah lihat daftar
     * lagu, tidak perlu tahu ada scan ulang jalan di belakang layar untuk menangkap
     * lagu baru/terhapus, KECUALI kalau ternyata hasilnya beda dari cache (baru diam-
     * diam update tampilan begitu selesai, lihat syncQueueWithLibrary).
     */
    fun scanFolder(folderUri: Uri, persist: Boolean = true, showProgress: Boolean = true) {
        viewModelScope.launch {
            if (showProgress) {
                _isScanning.value = true
                _scanProgress.value = 0.3f
            }

            // FIX: musicFolderUri bisa dipulihkan lewat Restore All Settings dari backup
            // yang dibuat di install/device lain (atau dari sebelum data app dihapus) --
            // izin SAF ke folder itu (takePersistableUriPermission) TIDAK ikut ter-restore
            // sama sekali, cuma string URI-nya saja. Sebelumnya scanSelectedFolder() yang
            // gagal (mis. SecurityException "No persisted permission") dibiarkan lempar
            // keluar dari coroutine ini -- tidak ada yang menangkapnya -> app crash setiap
            // kali dibuka ulang, karena auto-scan startup langsung memanggil scanFolder()
            // lagi dengan URI yang sama. Sekarang gagal scan cukup batal & reset status,
            // biar pengguna cuma perlu pilih ulang folder lewat Import.
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

    // FIX (bug "shuffle kadang tidak benar-benar shuffle, kadang masih berurutan"):
    // sebelumnya playSong() SELALU menimpa _queue.value dengan [fromList] apa adanya
    // (urutan asli/berurutan), tanpa pernah mengecek _shuffleOn sama sekali. Efeknya:
    // begitu Shuffle dinyalakan (lewat shuffleAll()), lalu pengguna balik ke Library
    // dan tap lagu MANAPUN (misal pindah artis/album) -- playSong() dipanggil, queue
    // diam-diam ditimpa balik ke urutan berurutan asli, TAPI toggle Shuffle di UI
    // tetap kelihatan ON (tidak pernah di-reset) karena tidak ada baris yang
    // menyentuh _shuffleOn di sini. Hasilnya: next()/previous() jalan berurutan
    // padahal indikator Shuffle masih menyala -- persis gejala "kadang shuffle,
    // kadang berurutan" tergantung terakhir kali masuk lewat shuffleAll() atau
    // playSong().
    //
    // Sekarang: kalau _shuffleOn sedang true saat playSong() dipanggil, [fromList]
    // ikut diacak juga (Fisher-Yates yang sama dengan ShuffleQueue di tempat lain),
    // dengan lagu yang ditekan tetap diputar duluan (di index 0) -- bukan predictable/
    // tetap di posisi aslinya -- supaya next() setelahnya beneran lanjut acak, bukan
    // diam-diam balik berurutan. Shuffle state (_shuffleOn) sendiri TIDAK disentuh di
    // sini (sengaja dibiarkan sesuai kondisi sebelumnya) karena tap lagu manual bukan
    // aksi "matikan shuffle" yang eksplisit dari pengguna.
    // BARU (bug "app tidak muncul di 'Buka dengan' saat tap file lagu di file
    // manager/app downloader lain"): dipanggil dari MainActivity.onCreate()/
    // onNewIntent() begitu ada Intent.ACTION_VIEW masuk dari luar app. File
    // eksternal ini dimainkan sebagai antrian isi SATU lagu saja (bukan
    // dicampur ke _queue/_library yang sedang aktif) -- supaya tidak diam-diam
    // mengacak-acak apa yang sedang didengarkan pengguna dari library-nya
    // sendiri kalau kebetulan lagi ada lagu lain yang jalan. Tetap lewat
    // playSong() yang sama (bukan panggil playerManager langsung) supaya bug
    // shuffle yang sudah diperbaiki sebelumnya (playSong menghormati
    // _shuffleOn) juga konsisten berlaku di sini.
    // REVISI (bug "next/prev pas play file eksternal cuma restart lagu yang sama"):
    // sebelumnya queue-nya CUMA berisi lagu eksternal itu sendiri (listOf(song)) --
    // karena cuma 1 item, wraparound modulo di next()/previous() balik ke lagu yang
    // sama, jadi Next/Prev kerasa "nggak ngapa-ngapain" (cuma restart dari awal).
    // Atas permintaan: begitu lagu eksternal ini habis/di-skip, LANJUT ke lagu-lagu
    // di library (BUKAN scan folder asal file eksternal itu). Caranya: lagu
    // eksternal ditaruh di depan (index 0, yang langsung diputar), diikuti seluruh
    // isi _library apa adanya -- playSong() sendiri yang nanti ngurus pengacakan
    // sisa library kalau _shuffleOn lagi aktif (perilaku sama persis kayak playSong
    // dipanggil dari Library biasa, cuma bedanya nyisipin 1 lagu eksternal duluan).
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
        // FIX: sebelumnya playerManager.playSong(song) memuat lagu ini SENDIRIAN ke ExoPlayer
        // (playlist isi 1 item), jadi ExoPlayer/MediaSession tidak pernah tahu ada lagu lain
        // di sekitarnya. Sekarang seluruh [queueToUse] dimuat sekaligus lewat setQueue(), supaya
        // next/prev -- baik di dalam app, swipe kaset, maupun di statusbar/lockscreen -- benar-
        // benar bisa pindah ke lagu lain di daftar ini.
        playerManager.setQueue(queueToUse, queueIndex, 0L, playWhenReady = true)
        persistPlaybackState(song, 0L)
    }

    fun shuffleAll() {
        val shuffled = ShuffleQueue.shuffled(_library.value)
        _queue.value = shuffled
        _shuffleOn.value = true
        queueIndex = 0
        if (shuffled.isNotEmpty()) {
            pickSkinFor(shuffled[0])
            playerManager.setQueue(shuffled, 0, 0L, playWhenReady = true)
            persistPlaybackState(shuffled[0], 0L)
        }
    }

    // FIX (v1.3): sebelumnya toggleShuffle() dimatikan (shuffle OFF) SELALU melompat
    // ke _library.value (seluruh isi library device) -- jadi kalau pengguna sedang
    // memutar sebuah ALBUM/PLAYLIST lalu nyalakan+matikan shuffle di kaset pemutar,
    // antrian malah berubah jadi seluruh library, bukan kembali ke urutan
    // album/playlist semula. Sekarang urutan SEBELUM diacak disimpan dulu di
    // [preShuffleQueue] dan dikembalikan persis saat shuffle dimatikan.
    private var preShuffleQueue: List<Song> = emptyList()

    fun toggleShuffle() {
        // BARU (fitur "Shuffle stasiun radio di mode Stream"): sebelumnya tombol
        // Shuffle di deck cuma jadi no-op (diabaikan) selama mode Stream (Side B) --
        // lihat FIX di bawah soal kenapa awalnya dulu malah "memaksa" pindah ke
        // Library. No-op memang aman, tapi terasa mati/tidak ada feedback sama
        // sekali begitu dipencet selagi streaming. Sekarang Shuffle di Side B
        // benar-benar berfungsi: lompat ke stasiun ACAK lain dari _streamStations
        // (bukan next/prev berurutan seperti tryAdvanceStreamStation), mirip
        // semangat Shuffle di Library tapi sumbernya daftar stasiun, bukan lagu.
        if (cassetteSide.value == "B") {
            shuffleToRandomStation()
            return
        }

        // FIX (bug "shuffle di deck malah pindah ke Library pas lagi Stream"):
        // Shuffle tidak relevan sama sekali untuk mode Stream (Side B) -- radio
        // cuma satu "track" hidup terus-menerus, tidak ada antrian untuk diacak
        // sama sekali. Sebelumnya fungsi ini SELALU jalan sampai bawah & memanggil
        // playerManager.setQueue() dengan _queue milik Library, tanpa peduli mode
        // aktif sekarang apa -- begitu tombol Shuffle di deck dipencet SAAT sedang
        // streaming, baris setQueue() itu menimpa MediaItem stream yang sedang
        // aktif di ExoPlayer dengan antrian Library, sehingga kelihatan seperti
        // tombol Shuffle "memaksa" pindah balik ke Library dengan sendirinya.
        val turningOn = !_shuffleOn.value
        _shuffleOn.value = turningOn

        val newQueue = if (turningOn) {
            preShuffleQueue = _queue.value
            ShuffleQueue.shuffled(_queue.value)
        } else {
            preShuffleQueue.ifEmpty { _queue.value }
        }
        _queue.value = newQueue
        if (newQueue.isEmpty()) return

        // Pertahankan lagu yang sedang diputar & posisinya saat acak dinyalakan/dimatikan --
        // cuma urutan antrian di sekitarnya yang berubah, bukan lagu yang sedang jalan.
        val current = playerManager.currentSong.value
        val idx = current?.let { c -> newQueue.indexOfFirst { it.id == c.id } }?.takeIf { it >= 0 } ?: 0
        queueIndex = idx
        playerManager.setQueue(newQueue, idx, playerManager.positionMs.value, playWhenReady = playerManager.isPlaying.value)
    }

    /**
     * BARU: lompat ke stasiun radio ACAK dari [_streamStations], dipanggil Shuffle
     * di deck selagi mode Stream (Side B). Sengaja TIDAK mengubah [_shuffleOn] --
     * itu flag khusus shuffle antrian Library, tidak relevan untuk stream.
     * Kalau stasiun cuma 0/1 di daftar, tidak ada apa pun yang bisa diacak --
     * dibiarkan diam (no-op), sama seperti sebelumnya untuk kasus ini.
     */
    private fun shuffleToRandomStation() {
        val stations = _streamStations.value
        if (stations.size < 2) return

        // Pilih stasiun ACAK selain yang sedang aktif sekarang, supaya Shuffle
        // selalu terasa berefek (tidak kadang-kadang "acak" tapi hasilnya sama
        // dengan stasiun yang barusan diputar).
        val candidates = stations.filterNot { it.id == _currentStationId.value }
        val pool = candidates.ifEmpty { stations }
        val station = pool.random()

        _currentStationId.value = station.id
        pickSkinForStation(station)
        playerManager.playStreamUrl(station.streamUrl, station.name)
        persistLastStream(station.streamUrl, station.name)
    }

    /* ================================================================
     * BARU (fitur "Jelajahi Radio"): state buat tab JELAJAHI di layar
     * Streaming. Hasil browse/search di sini SENGAJA tidak dipersist ke
     * SettingsRepository (beda dari _streamStations/customStations yang
     * memang harus tetap ada lain kali app dibuka) -- ini cuma hasil
     * pencarian sementara, wajar hilang kalau app ditutup. Yang di-PERSIST
     * cuma stasiun yang di-explicit user pindahkan ke Favorit lewat
     * addFoundStationToFavorites() (di StreamingScreen.kt, menulis ke jalur
     * CUSTOM_STATIONS_JSON yang SUDAH ADA -- bukan skema/key baru).
     * ================================================================ */

    private val _browseQuery = MutableStateFlow("")
    val browseQuery: StateFlow<String> = _browseQuery.asStateFlow()

    private val _browseResults = MutableStateFlow<List<RadioBrowserStation>>(emptyList())
    val browseResults: StateFlow<List<RadioBrowserStation>> = _browseResults.asStateFlow()

    private val _browseLoading = MutableStateFlow(false)
    val browseLoading: StateFlow<Boolean> = _browseLoading.asStateFlow()

    private val _browseError = MutableStateFlow<String?>(null)
    val browseError: StateFlow<String?> = _browseError.asStateFlow()

    private var browseSearchJob: kotlinx.coroutines.Job? = null

    fun updateBrowseQuery(query: String) {
        _browseQuery.value = query
    }

    // Dipanggil sekali saat tab JELAJAHI pertama kali dibuka (lihat
    // LaunchedEffect(Unit) di StreamingBrowseTab.kt) -- isi awal berupa
    // stasiun POPULER, supaya user yang tidak tahu nama radio apa pun tetap
    // punya sesuatu buat di-scroll/coba, bukan layar kosong menunggu ketikan.
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
            // BARU: debounce kecil supaya tidak nembak request tiap 1 huruf
            // diketik -- tunggu jeda 400ms tanpa ketikan baru sebelum benar-
            // benar search ke API.
            delay(400)
            _browseLoading.value = true
            _browseError.value = null
            val result = runCatching {
                withContext(Dispatchers.IO) { radioBrowserRepository.searchStations(query) }
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
                withContext(Dispatchers.IO) { radioBrowserRepository.getStationsByTag(tag) }
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

    // Tap item di tab Jelajahi langsung memutar -- TIDAK otomatis masuk
    // Favorit (harus tap tombol "+" terpisah di UI), supaya user bisa coba-
    // coba dengar dulu tanpa mengotori daftar Favorit dengan stasiun yang
    // ternyata tidak disukai. Konversi ke RadioStation di sini pakai prefix
    // id "browse_" (beda dari "custom_" yang dipakai stasiun tersimpan) --
    // StreamingFavoritesPanel sudah punya aturan "cuma id berawalan custom_
    // yang boleh dihapus/diganti nama", prefix beda ini sengaja dijaga
    // supaya hasil browse tidak keliru dianggap sebagai favorit tersimpan.
    // FIX (bug "panel detail stream selalu menampilkan nama stasiun favorit
    // terakhir, misal 'Prambors', walau yang sedang main stasiun hasil
    // Jelajahi"): sebelumnya fungsi ini `Unit` -- StreamingScreen.kt cuma
    // tahu "sudah diputar" tanpa tahu OBJEK RadioStation-nya, dan `selected`
    // di sana cuma di-update lewat LaunchedEffect(currentStationId,
    // allStations) yang MENCARI id ini di [allStations] (gabungan Favorit +
    // custom stations tersimpan). Stasiun hasil Jelajahi yang BELUM
    // ditambahkan ke Favorit tidak pernah ada di [allStations], jadi
    // pencarian itu selalu gagal (matched == null) dan `selected` diam di
    // tempat -- tetap menampilkan info stasiun lama. Sekarang fungsi ini
    // MENGEMBALIKAN RadioStation yang baru dibuat, supaya caller
    // (StreamingScreen.kt) bisa langsung set `selected` dari nilai balik ini
    // TANPA bergantung ke pencarian di [allStations] sama sekali.
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
        // FIX: sejak seluruh antrian dimuat ke ExoPlayer lewat PlayerManager.setQueue(), mode
        // ALL & ONE HARUS disamakan ke ExoPlayer sendiri (bukan cuma ditangani lewat callback
        // onSongEnded/STATE_ENDED seperti dulu saat playlist cuma isi 1 lagu) -- lihat catatan
        // di PlayerManager.setRepeatMode() & buildPlayer().
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

    /* ================================================================
     * SLEEP TIMER -- BARU (v2.1)
     *
     * Dua mode independen (memilih salah satu otomatis membatalkan yang lain, lihat
     * cancelSleepTimer() dipanggil di awal kedua fungsi start* di bawah):
     *
     * - startSleepTimer(minutes): hitung mundur waktu tetap. sleepTimerJob menghitung per
     *   detik (bukan cuma satu delay() panjang) supaya sleepTimerRemainingMs bisa ditampilkan
     *   live di UI (Settings & VFD Status Panel) selagi berjalan.
     *
     * - startSleepTimerEndOfTrack(): TIDAK ada hitung mundur waktu -- berhenti begitu lagu
     *   yang SEDANG diputar SAAT INI selesai. Masalahnya: kalau RepeatMode pengguna sedang
     *   ALL/ONE, ExoPlayer menangani lanjut/ulang lagu SENDIRI lewat repeatMode-nya sendiri
     *   dan tidak pernah mencapai STATE_ENDED (lihat catatan panjang di
     *   PlayerManager.buildPlayer() & handleTrackEnded() di bawah) -- artinya onSongEnded
     *   tidak akan pernah terpanggil untuk lagu ini. Makanya di sini repeatMode ExoPlayer
     *   dipaksa OFF sementara supaya lagu ini pasti mencapai STATE_ENDED secara alami,
     *   TANPA mengubah _repeatMode (pilihan repeat pengguna yang tampil di UI tombol repeat)
     *   -- begitu timer ini terpicu (di handleTrackEnded()) atau dibatalkan manual
     *   (cancelSleepTimer()), repeatMode ExoPlayer dikembalikan lagi sesuai _repeatMode.value
     *   lewat restoreRepeatModeToPlayer().
     * ================================================================ */

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
        // Paksa ExoPlayer benar-benar mencapai STATE_ENDED untuk lagu ini -- lihat
        // penjelasan panjang di komentar blok SLEEP TIMER di atas.
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

    // Mengembalikan repeatMode ExoPlayer supaya sesuai lagi dengan pilihan repeat pengguna
    // (_repeatMode) -- dipakai sesudah repeatMode sempat dipaksa OFF oleh
    // startSleepTimerEndOfTrack() di atas. Mapping-nya sama persis dengan cycleRepeat().
    private fun restoreRepeatModeToPlayer() {
        playerManager.setRepeatMode(
            when (_repeatMode.value) {
                RepeatMode.OFF -> androidx.media3.common.Player.REPEAT_MODE_OFF
                RepeatMode.ALL -> androidx.media3.common.Player.REPEAT_MODE_ALL
                RepeatMode.ONE -> androidx.media3.common.Player.REPEAT_MODE_ONE
            }
        )
    }

    // FIX (patch "next/prev tidak jalan di mode stream"): sebelumnya fungsi ini SELALU
    // baca dari _queue (antrian lagu Library) dan langsung return kalau kosong -- padahal
    // pas lagi streaming, _queue itu memang kosong (streaming tidak pernah mengisinya),
    // jadi tombol next/prev di tape deck diam saja tanpa efek apa pun. Sekarang dicek
    // dulu: kalau yang aktif sekarang stream (currentSong == null, sama seperti penanda
    // yang sudah dipakai StreamingScreen untuk isStreamPlaying) DAN ada daftar stasiun
    // terdaftar, pindah ke stasiun berikutnya/sebelumnya di daftar itu -- baru fallback
    // ke logic queue lagu yang lama kalau bukan lagi streaming.
    private fun tryAdvanceStreamStation(step: Int): Boolean {
        val stations = _streamStations.value
        // Syarat tambahan _currentStationId != null (bukan cuma currentSong == null) supaya
        // tidak salah kira "lagi streaming" kalau app baru dibuka & belum pernah ada stream
        // yang benar-benar dipilih user (currentSong juga masih null sebelum playback pertama).
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
        // FIX: seekToQueueItem() berpindah DI DALAM antrian yang sama yang sudah dimuat ke
        // ExoPlayer (lihat setQueue), bukan mengganti playlist jadi 1 item baru seperti
        // playSong() dulu -- ini juga fungsi yang sama dipakai swipe kaset & disinkronkan
        // dengan tombol next di statusbar/lockscreen.
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

    // BARU: efek suara FF/RW ala kaset asli selama roda kaset diputar manual
    // untuk seeking (lihat CassetteDeck.kt onScrubSpeedChange & PlayerScreen.kt).
    fun setTapeScrubSpeed(speedMultiplier: Float) = playerManager.setTapeScrubSpeed(speedMultiplier)
    fun resetTapeScrubSpeed() = playerManager.resetTapeScrubSpeed()

    /**
     * Dipanggil lewat [PlayerManager.onSongEnded] setiap kali lagu selesai diputar sampai
     * habis dengan sendirinya (STATE_ENDED). Perilakunya mengikuti [RepeatMode]:
     * - ONE   : ulangi lagu yang sama dari awal.
     * - ALL   : lanjut ke lagu berikutnya, dan tetap lanjut (wrap) walau sudah di lagu terakhir.
     * - OFF   : lanjut ke lagu berikutnya, TAPI berhenti (bukan wrap ke lagu pertama lagi)
     *           begitu lagu terakhir di antrian selesai.
     *
     * CATATAN sejak antrian penuh dimuat ke ExoPlayer (lihat cycleRepeat() yang menyamakan
     * player.repeatMode dengan pilihan ini): untuk ALL & ONE, ExoPlayer sendiri sudah
     * menangani lanjut/ulang lagu secara native lewat timeline-nya -- STATE_ENDED nyaris
     * tidak pernah tercapai untuk kedua mode itu lagi. Fungsi ini jadinya efektif hanya
     * benar-benar terpanggil untuk kasus OFF + lagu terakhir di antrian selesai (di situ
     * playerManager.stop() di bawah yang berperan), dan tetap dipertahankan utuh sebagai
     * fallback yang aman kalau repeatMode ExoPlayer & pilihan pengguna sempat tidak sinkron.
     */
    private fun handleTrackEnded() {
        // BARU (v2.1): SLEEP TIMER mode "berhenti setelah lagu ini" -- dicek PALING AWAL,
        // sebelum logika RepeatMode normal di bawah, supaya lagu TIDAK lanjut/berulang lagi
        // walau RepeatMode pengguna ALL/ONE. repeatMode ExoPlayer yang sempat dipaksa OFF di
        // startSleepTimerEndOfTrack() dikembalikan lagi di sini supaya RepeatMode pengguna
        // berfungsi normal lagi untuk lagu-lagu berikutnya setelah timer ini selesai bertugas.
        if (_sleepTimerMode.value == SleepTimerMode.END_OF_TRACK) {
            playerManager.pause()
            restoreRepeatModeToPlayer()
            _sleepTimerMode.value = SleepTimerMode.OFF
            return
        }
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                playerManager.currentSong.value?.let { song ->
                    // FIX: dulu pakai playerManager.playSong(song) yang mereset ExoPlayer jadi
                    // playlist 1 item -> merusak antrian yang sudah dimuat setQueue(), sehingga
                    // next/prev (termasuk di statusbar/lockscreen) berhenti berfungsi setelah
                    // repeat-satu-lagu aktif sekali saja. seekToQueueItem() ke index yang sama
                    // cukup mengulang lagu ini dari awal tanpa membongkar antrian.
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
                    // Lagu terakhir di antrian selesai dan repeat mati -> berhenti,
                    // jangan wrap balik ke lagu pertama.
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

        // BARU (v1.7): kalau AUTO EQ PER LAGU aktif, pemilihan preset manual dari
        // dropdown ini sekaligus "diingat" sebagai preset untuk lagu yang sedang
        // diputar -- begitu lagu ini diputar lagi nanti, preset ini otomatis kepasang
        // lagi tanpa perlu ganti manual (lihat collector currentSong di init{}).
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

    /**
     * Simpan preset EQ yang sedang aktif (hasil geser slider band) sebagai preset custom
     * bernama [name]. Sebelumnya tombol SAVE di EqualizerScreen tidak terhubung ke logika
     * apa pun (onSavePreset default-nya lambda kosong), sehingga preset custom pengguna
     * tidak pernah benar-benar tersimpan / dipersist ke DataStore.
     */
    fun saveCustomPreset(name: String) {
        val presetName = name.trim().ifBlank { "Custom" }
        val presetToSave = _activePreset.value.copy(name = presetName)

        // Timpa preset custom lama dengan nama yang sama (kalau ada), lalu tambahkan yang baru
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

    /**
     * BARU (fitur "hapus preset"): hapus preset custom (hasil SAVE atau UPLOAD)
     * berdasarkan nama. Preset bawaan (BuiltInPresets) TIDAK bisa dihapus lewat
     * fungsi ini -- kalau nama yang diberikan bukan preset custom, panggilan ini
     * diabaikan (no-op), sesuai comment di EqualizerScreen: ikon hapus memang cuma
     * dirender untuk preset custom, tapi guard ini jaga-jaga di sisi ViewModel juga.
     *
     * Kalau preset yang dihapus sedang aktif, otomatis pindah ke preset "Flat"
     * (lewat applyPreset yang sudah ada) supaya activePreset tidak pernah menunjuk
     * ke preset yang sudah tidak ada lagi di daftar.
     */
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

    // ================================================================
    // BARU (v2.0) -- BACKUP & RESTORE SEMUA PENGATURAN SEKALIGUS.
    // Beda dari exportActivePresetJson()/importPresetJson() di atas (cuma satu
    // preset EQ aktif), dua fungsi ini meneruskan langsung ke
    // SettingsRepository.exportAllSettingsJson()/importAllSettingsJson() yang
    // membaca/menulis SELURUH DataStore secara generik -- lihat komentar lengkap
    // di SettingsRepository.kt untuk detail & batasan jujur fitur ini (terutama
    // soal restore yang baru terlihat penuh setelah app dibuka ulang).
    // ================================================================

    suspend fun exportAllSettingsJson(): String = settingsRepository.exportAllSettingsJson()

    suspend fun importAllSettingsJson(json: String): Result<Unit> =
        settingsRepository.importAllSettingsJson(json)

    // ================================================================
    // BARU (v1.2) -- FITUR PLAYLIST.
    // Tiap fungsi: ubah _playlists (StateFlow, dibaca UI) -> persist ke DataStore,
    // pola yang sama dengan saveCustomPreset() di atas.
    // ================================================================

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

    /** Daftar Song penuh (dari library saat ini) yang jadi isi sebuah playlist. */
    fun songsForPlaylist(playlist: Playlist): List<Song> {
        val byPath = _library.value.associateBy { it.path }
        return playlist.songPaths.mapNotNull { byPath[it] }
    }

    /** Putar seluruh isi playlist mulai dari lagu pertama, sama seperti playSong(fromList). */
    fun playPlaylist(playlist: Playlist) {
        val songs = songsForPlaylist(playlist)
        if (songs.isEmpty()) return
        playSong(songs.first(), songs)
    }

    // ================================================================
    // BARU (v1.2) -- FITUR "SCAN LAGU LAMA" (rescan manual).
    // Sebelumnya cuma ada scan otomatis-diam-diam saat startup (init{}); tidak ada
    // tombol yang bisa dipicu manual oleh pengguna kapan pun untuk memindai ulang
    // lagu-lagu lama/baru di folder musik yang sudah tersimpan (mis. setelah pengguna
    // menambah file baru ke folder itu dari luar app). Dipanggil dari tombol
    // "RESCAN LIBRARY NOW" di SettingsScreen.
    // ================================================================
    fun rescanLibrary() {
        viewModelScope.launch {
            val savedFolder = runCatching { settingsRepository.musicFolderUri.first() }.getOrDefault("")
            if (savedFolder.isNotBlank()) {
                runCatching { Uri.parse(savedFolder) }.getOrNull()?.let { uri ->
                    scanFolder(uri, persist = false)
                    return@launch
                }
            }
            // Belum ada folder SAF tersimpan -> jatuhkan ke pemindaian MediaStore biasa.
            refreshLibrary()
        }
    }

    /**
     * BARU: kontrol Preamp terpisah dari band EQ, dipakai oleh sub-menu "Nada" di
     * EqualizerScreen. Menyimpan ke activePreset.preamp yang sudah didukung EqPreset,
     * cuma sebelumnya tidak ada fungsi publik untuk mengubahnya dari UI.
     */
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
        // FIX (v1.4.1): dipersist di sini sekarang -- sebelumnya dipersist lewat
        // baris "Peak Limiter" duplikat di Settings (LaunchedEffect(peakLimiter)),
        // yang sudah dihapus bersama kategori AUDIO & EFFECTS.
        viewModelScope.launch {
            settingsRepository.setBool(SettingsKeys.PEAK_LIMITER, enabled)
        }
    }

    fun setEqBypass(bypass: Boolean) {
        _eqBypassOn.value = bypass
        playerManager.setEqBypass(bypass)
    }

    // BARU (v1.6): "BIT-PERFECT MODE" -- pola sama persis dengan setEqBypass/
    // setLimiterEnabled di atas. Lihat komentar di ParametricEqAudioProcessor.bitPerfectMode
    // untuk cakupan jujur fitur ini (bit-perfect sisi software app, bukan hardware/offload).
    // BARU (patch "Crossfade"): pola sama persis dengan setLimiterEnabled/
    // setBitPerfectMode -- update state UI, teruskan ke PlayerManager, lalu
    // persist ke DataStore supaya tidak reset ke default tiap app dibuka ulang.
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

    // BARU (v1.4): dulu dropdown "Theme Accent Color" di Settings cuma mengubah
    // variabel lokal layar dan tidak berefek apa pun. Sekarang benar-benar mengubah
    // ThemeAccentState.current (dibaca reaktif oleh Gold/GoldBright/GoldDim/StrokeGold
    // di seluruh app) sekaligus dipersist supaya pilihan bertahan setelah app ditutup.
    fun setThemeAccent(label: String) {
        ThemeAccentState.current = ThemeAccent.fromLabel(label)
        viewModelScope.launch {
            settingsRepository.setString(SettingsKeys.THEME_ACCENT, label)
        }
    }

    // ================================================================
    // BARU (patch "DSP control knobs") -- VOCAL (tombol vocal + knop bass/
    // treble vokal) & STEREO (Balance, Stereo Expansion, Mono/Stereo).
    // Tiap setter: update StateFlow (UI) -> teruskan ke DSP lewat
    // PlayerManager -> persist ke DataStore, pola yang sama dengan
    // saveCustomPreset/updatePreampGain di atas.
    // ================================================================

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

    // BARU (patch "headroom slider"): setter untuk slider "MAX LOUDNESS <-> SAFE
    // HEADROOM" di tab BATAS -- pola sama persis dengan updateStereoExpansion di
    // atas. 0.0 = full loudness (andalkan limiter sepenuhnya), 1.0 = paling
    // konservatif/aman.
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

    // BARU (patch "cassette side A/B"): simpan stream terakhir yang diputar supaya
    // double-tap kaset ke Side B (toggleCassetteSide) tahu stream mana yang mau
    // di-resume, bahkan setelah app ditutup total lalu dibuka lagi.
    private fun persistLastStream(url: String, title: String) {
        viewModelScope.launch {
            settingsRepository.setString(SettingsKeys.LAST_STREAM_URL, url)
            settingsRepository.setString(SettingsKeys.LAST_STREAM_TITLE, title)
        }
    }

    // BARU (patch "next/prev tidak jalan di mode stream"): daftar stasiun (built-in +
    // custom) sebelumnya cuma hidup sebagai state lokal di StreamingScreen -- ViewModel
    // (dan tombol next/prev di tape deck/PlayerScreen) sama sekali tidak tahu stasiun apa
    // saja yang tersedia atau yang mana yang lagi aktif. Sekarang StreamingScreen mendaftarkan
    // daftar gabungannya ke sini lewat setStreamStations(), supaya next()/previous() di bawah
    // bisa pindah-pindah antar stream URL tersimpan juga, bukan cuma antar lagu di _queue.
    private val _streamStations = MutableStateFlow<List<RadioStation>>(emptyList())
    val streamStations: StateFlow<List<RadioStation>> = _streamStations

    // Stasiun mana yang lagi aktif diputar -- dipakai StreamingScreen untuk menyorot
    // pilihan yang benar walau pindahnya lewat next/prev di tape deck, bukan lewat tap
    // di layar Streaming itu sendiri.
    private val _currentStationId = MutableStateFlow<String?>(null)
    val currentStationId: StateFlow<String?> = _currentStationId

    fun setStreamStations(stations: List<RadioStation>) {
        _streamStations.value = stations
    }

    fun playStreamStation(station: RadioStation) {
        // FIX (bug "double-tap balik ke Library gak jalan kalau stream dimulai dari
        // layar Streaming"): lihat catatan lengkap di captureLibraryStateBeforeStream().
        // Kalau ini pertama kalinya pindah dari Library ke Stream (masih Side A),
        // simpan dulu lagu Library yang sedang aktif SEBELUM stream ini menimpanya --
        // baru setelah itu boleh mulai putar streamnya.
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

    // FIX (bug "skin kaset gak berubah pas streaming, cuma skin terakhir dari
    // Library"): pickSkinFor(song: Song) di atas cuma pernah dipanggil dari
    // jalur playback Library (playSong/next/previous/shuffle/restore) -- jalur
    // stream (playStreamStation dari tap list, tryAdvanceStreamStation dari
    // next/prev tape deck, toggleCassetteSide() saat pindah ke Side B) tidak
    // pernah menyentuh _currentSkin sama sekali, jadi skin yang tampil selama
    // streaming selalu skin terakhir yang di-pick waktu masih di Library.
    // Overload ini sama persis pola hash-nya, cuma sumber ID-nya String
    // (RadioStation.id) bukan Long (Song.id) -- dipakai supaya tiap stasiun
    // yang berbeda juga dapat skin kaset yang berbeda & konsisten (id yang
    // sama akan selalu menghasilkan skin yang sama, layaknya lagu Library).
    private fun pickSkinForStation(station: RadioStation) {
        val hash = (station.id.hashCode() and 0x7fffffff)
        _currentSkin.value = CassetteSkins[hash % CassetteSkins.size]
    }

    /** Simpan lagu + posisi saat ini ke DataStore supaya bisa dipulihkan saat app dibuka lagi. */
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
            // Best-effort: viewModelScope sudah/akan dibatalkan di onCleared, jadi tulis
            // langsung tanpa menunggu coroutine viewModelScope agar sempat tersimpan.
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
