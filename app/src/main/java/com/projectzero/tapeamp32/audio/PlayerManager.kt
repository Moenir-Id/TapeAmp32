package com.projectzero.tapeamp32.audio

import android.content.Context
import android.media.AudioFormat
import android.os.Build
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.extractor.metadata.icy.IcyHeaders
import androidx.media3.exoplayer.audio.DefaultAudioOffloadSupportProvider
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.projectzero.tapeamp32.data.EqPreset
import com.projectzero.tapeamp32.data.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@UnstableApi
class PlayerManager private constructor(private val context: Context) {

    companion object {
        // PlayerViewModel (UI) dan PlaybackService (notifikasi/lockscreen/MediaButtonReceiver)
        // sebelumnya masing-masing membuat PlayerManager -> ExoPlayer -> AudioTrack SENDIRI.
        // Begitu PlaybackService dibind oleh sistem (lewat MediaSessionService/MediaButtonReceiver
        // di manifest, bisa otomatis tanpa tombol fisik ditekan), DUA AudioTrack itu rebutan audio
        // focus & output di level OS -> playback gagal total untuk semua jenis file. Fix: satu
        // instance PlayerManager (dan satu ExoPlayer) untuk seluruh aplikasi.
        @Volatile private var instance: PlayerManager? = null

        fun getInstance(context: Context): PlayerManager =
            instance ?: synchronized(this) {
                instance ?: PlayerManager(context.applicationContext).also { instance = it }
            }
    }

    val eqProcessor = ParametricEqAudioProcessor()

    // BARU (v1.6): referensi ke DefaultAudioSink yang benar-benar dibangun, supaya
    // setBitPerfectMode() bisa menyalakan/mematikan OFFLOAD MODE di sink secara
    // dinamis (DefaultAudioSink.setOffloadMode() boleh dipanggil kapan saja setelah
    // sink dibuat, tidak cuma sekali di buildAudioSink()).
    private var audioSinkRef: DefaultAudioSink? = null

    // BARU: observer hardware USB DAC untuk Status Panel VFD. Di-expose sebagai
    // singleton bersama PlayerManager (bukan dibuat ulang tiap Composable recompose)
    // supaya state deteksinya konsisten sepanjang aplikasi hidup. Registrasi ke
    // AudioManager sendiri TIDAK dilakukan di sini -- itu ditempelkan ke LocalLifecycleOwner
    // dari UI (PlayerScreen) lewat DisposableEffect, supaya register/unregister-nya
    // benar-benar mengikuti siklus hidup layar, bukan siklus hidup singleton ini.
    val usbDacObserver = UsbDacObserver(context)

    // BARU (fix bug "kadang lagu tiba-tiba berhenti, harus tekan Play lagi"):
    // lihat onPlayerError()/onIsPlayingChanged() di buildPlayer() untuk detail
    // lengkap -- flag ini yang memastikan auto-retry cuma dicoba SEKALI per
    // rentang waktu sebelum sukses playing lagi, bukan infinite-retry-loop
    // kalau errornya memang permanen.
    private var hasRetriedAfterError = false

    private val _vuLevels = MutableStateFlow(0f to 0f)

    /* ================================================================
     * BARU (fitur "tombol Shuffle & Close di notifikasi", ala Poweramp):
     * status ON/OFF Shuffle yang SEBENARNYA hidup di PlayerViewModel
     * (_shuffleOn -- karena logic shuffle butuh akses ke _library, reorder
     * queue, dan persist ke DataStore, yang semuanya cuma ada di ViewModel).
     * PlaybackService (yang bikin notifikasi) TIDAK punya akses ke
     * PlayerViewModel sama sekali -- cuma ke PlayerManager (singleton ini).
     *
     * Jadi PlayerManager dipakai sebagai "jembatan" komunikasi 2 arah:
     * 1) shuffleOn di sini di-SET oleh ViewModel tiap kali _shuffleOn
     *    berubah (lihat setShuffleOnState()) -- PlaybackService baca ini
     *    buat nentuin ikon notifikasi (ic_notif_shuffle_on/off).
     * 2) requestToggleShuffle() dipanggil PlaybackService tiap tombol
     *    Shuffle di notifikasi ditekan -- ViewModel yang "dengerin" sinyal
     *    ini (lihat init block di PlayerViewModel) lalu jalanin
     *    toggleShuffle()-nya sendiri yang lengkap (reorder + persist).
     * ================================================================ */

    private val _shuffleOn = MutableStateFlow(false)
    val shuffleOn: StateFlow<Boolean> = _shuffleOn

    fun setShuffleOnState(value: Boolean) {
        _shuffleOn.value = value
    }

    private val _shuffleToggleRequests = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val shuffleToggleRequests: kotlinx.coroutines.flow.SharedFlow<Unit> = _shuffleToggleRequests

    fun requestToggleShuffle() {
        _shuffleToggleRequests.tryEmit(Unit)
    }

    // BARU (tombol Close/X di notifikasi, ala Poweramp): stop playback total
    // & minta service berhenti sepenuhnya (bukan cuma pause) -- beda dari
    // pause biasa, ini yang bikin notifikasi media hilang total dari status
    // bar, persis perilaku tombol X di Poweramp.
    fun stopAndRelease() {
        player.stop()
        player.playWhenReady = false
    }
    val vuLevels: StateFlow<Pair<Float, Float>> = _vuLevels

    // BARU: status DSP Audio Engine (EQ / Tape Saturation aktif atau bypass) dan
    // indikator PEAK (limiter/soft-clipper sedang menahan sinyal), untuk Status Panel VFD.
    private val _dspEngineOn = MutableStateFlow(true)
    val dspEngineOn: StateFlow<Boolean> = _dspEngineOn

    private val _peakActive = MutableStateFlow(false)
    val peakActive: StateFlow<Boolean> = _peakActive

    // BARU (v1.8): status OFFLOAD hardware AKTUAL per lagu, untuk Status Panel VFD
    // dan tab BATAS di Equalizer -- lihat catatan "JUJUR diakui" di changelog v1.6:
    // sebelumnya status ini cuma bisa dikonfirmasi manual lewat adb logcat karena
    // tidak pernah diteruskan ke UI. Diisi dari AudioSink.AudioTrackConfig.offload
    // AKTUAL yang dilaporkan ExoPlayer setiap kali AudioTrack (di)buat ulang (lihat
    // onAudioTrackInitialized di buildPlayer()) -- BUKAN diasumsikan dari nilai
    // toggle BIT-PERFECT MODE, supaya benar-benar mencerminkan apakah device/USB
    // DAC yang sedang nyambung SUNGGUH memakai jalur offload atau diam-diam
    // fallback ke bypass software (lihat applyOffloadPreference di bawah).
    private val _offloadActive = MutableStateFlow(false)
    val offloadActive: StateFlow<Boolean> = _offloadActive

    // BARU: Hi-Res Audio Badge Detector, untuk Status Panel VFD. sampleRate & bitDepth
    // diambil dari AudioTrack config AKTUAL yang dipakai AudioSink (lihat
    // AnalyticsListener.onAudioTrackInitialized di buildPlayer()) -- bukan dari metadata
    // file mentah -- supaya angka yang tampil benar-benar mencerminkan apa yang sedang
    // dikirim ke hardware output saat ini (setelah decode, sebelum resampling perangkat).
    private val _isHiRes = MutableStateFlow(false)
    val isHiRes: StateFlow<Boolean> = _isHiRes

    private val _sampleRate = MutableStateFlow(0)
    val sampleRate: StateFlow<Int> = _sampleRate

    private val _bitDepth = MutableStateFlow(16)
    val bitDepth: StateFlow<Int> = _bitDepth

    // FIX (bug "bitrate selalu 0" di halaman Streaming): RadioStation.bitrateKbps
    // cuma di-hardcode 0 saat stasiun ditambahkan (lihat StreamingScreen.onAddCustomUrl)
    // dan tidak pernah diisi dari sumber lain -- makanya panel Stream Information selalu
    // menampilkan 0 kbps untuk SEMUA stasiun, bukan cuma stasiun tertentu. Ini bitrate
    // SUNGGUHAN dari stream yang sedang didecode saat ini (diisi di onAudioInputFormatChanged
    // di bawah), dipakai UI sebagai sumber utama menggantikan field statis di RadioStation.
    private val _streamBitrateKbps = MutableStateFlow(0)
    val streamBitrateKbps: StateFlow<Int> = _streamBitrateKbps

    // Menyimpan MIME type codec ASLI (sebelum decode) dari track audio yang sedang
    // diputar -- dipakai untuk menentukan lossless/lossy di onAudioTrackInitialized
    // (yang cuma tahu PCM hasil decode, tanpa info codec sumbernya).
    private var currentAudioMimeType: String? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong

    // FIX (patch "streaming label"): _currentSong itu Song? -- tidak bisa mewakili
    // "sedang streaming radio" karena stream bukan Song dari library. playStreamUrl()
    // di bawah men-set _currentSong ke null, dan sebelum ini tidak ada apa pun yang
    // menggantikannya, jadi CassetteDeck/PlayerScreen jatuh ke fallback "NO SONG
    // LOADED" walaupun stream sedang aktif diputar. _currentStreamTitle menyimpan
    // nama stasiun/URL yang sedang di-stream supaya UI bisa menampilkannya sebagai
    // pengganti title/artist lagu selama _currentSong null karena streaming (BUKAN
    // karena benar-benar belum ada apa pun yang diputar).
    private val _currentStreamTitle = MutableStateFlow<String?>(null)
    val currentStreamTitle: StateFlow<String?> = _currentStreamTitle

    // Menampung pesan error playback terakhir (mis. format tidak didukung, URI folder
    // kehilangan izin akses, dll) supaya bisa ditampilkan ke user alih-alih ditelan diam-diam.
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    // Dipanggil saat lagu selesai SENDIRI (STATE_ENDED alami, bukan karena stop() manual).
    // PlayerViewModel pasang listener di sini untuk memutuskan lanjut ke lagu berikutnya
    // atau replay, sesuai RepeatMode -- sebelumnya tidak ada hook ini sama sekali sehingga
    // playback cuma berhenti begitu lagu habis.
    var onSongEnded: (() -> Unit)? = null

    // FIX (controlbar statusbar/lockscreen + tombol next/prev): dulu setiap playSong()/
    // restoreSong() memanggil player.setMediaItem() (TUNGGAL), jadi ExoPlayer -- dan
    // MediaSession yang mengikuti timeline ExoPlayer itu -- SELALU cuma punya 1 item di
    // playlist-nya. Akibatnya tombol Next/Prev di notifikasi, lockscreen, tombol headset/
    // Bluetooth, dan Android Auto tidak punya "lagu lain" untuk dituju -> Media3 otomatis
    // menyembunyikan/mendisable tombol itu (controlbar terlihat kosong / tidak lengkap),
    // dan seekToNext()/seekToPrevious() dari luar tidak melakukan apa-apa.
    //
    // Sekarang seluruh antrian (bukan cuma 1 lagu) dimasukkan ke ExoPlayer lewat setQueue(),
    // sehingga timeline ExoPlayer PERSIS sama dengan antrian aplikasi. Ini membuat tombol
    // next/prev di statusbar/lockscreen otomatis muncul & berfungsi, karena keduanya kini
    // memanggil operasi timeline ExoPlayer yang sama dengan tombol next/prev di dalam app.
    private var songsById: Map<String, Song> = emptyMap()

    // BARU (patch "Crossfade"): dijalankan lewat ExoPlayer KEDUA yang berdiri
    // sendiri (bukan lewat 1 AudioProcessor tambahan di sink utama) -- selama
    // jendela overlap, dua AudioTrack Android memang otomatis di-mix oleh HAL
    // audio device (perilaku standar OS, bukan trik khusus di app ini), jadi
    // player utama TIDAK PERNAH perlu "berpindah identitas" (MediaSession tetap
    // nempel ke `player` yang sama sepanjang waktu, timeline/queue-nya juga
    // tidak diutak-atik). Player kedua ini murni untuk PREVIEW singkat lagu
    // berikutnya sesaat sebelum lagu sekarang benar-benar habis; begitu lagu
    // berikutnya itu tercapai secara alami oleh player utama sendiri (lewat
    // queue yang sudah ada dari setQueue()), preview dihentikan & player utama
    // yang mengambil alih seterusnya seperti biasa.
    //
    // JUJUR diakui: preview di player kedua ini TIDAK melewati eqProcessor
    // (EQ/Vocal/Stereo/Limiter/Bit-Perfect) -- cuma decode+output polos. Untuk
    // jendela overlap 1-8 detik ini dampaknya kecil, tapi tetap dicatat di sini
    // supaya tidak ada yang berasumsi crossfade "transparan penuh" terhadap DSP.
    // Begitu player utama sendiri yang mengambil alih lagu berikutnya (di titik
    // itu eqProcessor otomatis berlaku lagi, sama seperti transisi lagu biasa).
    private var crossfadePlayerRef: ExoPlayer? = null

    private var crossfadeEnabled = false
    private var crossfadeSeconds = 4f

    // Media ID lagu berikutnya yang SEDANG di-preview lewat crossfadePlayerRef
    // (null = tidak ada crossfade berjalan). Dipakai supaya pollPosition() tidak
    // memulai preview berkali-kali untuk lagu berikutnya yang sama, dan supaya
    // listener onMediaItemTransition tahu kapan harus menyerahkan alih ke player
    // utama.
    private var crossfadeTargetMediaId: String? = null

    // Volume "asli" yang diminta user lewat setVolume() (swipe kaset) -- dipisah
    // dari player.volume mentah karena SELAMA crossfade berjalan, player.volume
    // dipakai untuk kurva fade-out, bukan level yang user pilih. userVolume-lah
    // yang jadi patokan/batas atas kurva itu, dan yang dikembalikan begitu
    // crossfade selesai.
    private var userVolume = 1f

    private val _currentQueueIndex = MutableStateFlow(0)
    // Dipakai PlayerViewModel untuk menyamakan queueIndex-nya sendiri setiap kali index
    // ExoPlayer berubah -- termasuk saat perubahan itu dipicu dari LUAR aplikasi (lockscreen,
    // tombol headset, Bluetooth, Android Auto), bukan cuma dari tombol next/prev di dalam app.
    val currentQueueIndex: StateFlow<Int> = _currentQueueIndex

    fun clearError() {
        _lastError.value = null
    }

    // FIX (patch "akses semua URL stream"): dependency media3-datasource-okhttp
    // & okhttp sudah ada di build.gradle.kts tapi belum pernah benar-benar
    // dipakai -- tanpa ini ExoPlayer jatuh ke DefaultHttpDataSource bawaan yang
    // (a) MENOLAK cross-protocol redirect (http -> https atau sebaliknya, sangat
    // umum di server Icecast/Shoutcast/CDN radio), dan (b) pakai timeout default
    // 8 detik yang kadang terlalu pendek untuk server stream yang lambat
    // handshake-nya -- dua-duanya bikin banyak URL stream gagal diputar sama
    // sekali walau URL-nya valid & bisa dibuka di browser.
    private val streamOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val streamDataSourceFactory: DataSource.Factory by lazy {
        val okHttpFactory = OkHttpDataSource.Factory(streamOkHttpClient)
            .setUserAgent("TapeAmp32/1.0 (Android; Icecast/Shoutcast compatible)")
        DefaultDataSource.Factory(context.applicationContext, okHttpFactory)
    }

    val player: ExoPlayer by lazy { buildPlayer() }

    private fun buildPlayer(): ExoPlayer {
        eqProcessor.vuCallback = { l, r ->
            if (_isPlaying.value) {
                _vuLevels.value = l to r
            } else {
                _vuLevels.value = 0f to 0f
            }
        }

        eqProcessor.dspStateCallback = { active, peak ->
            _dspEngineOn.value = active
            _peakActive.value = _isPlaying.value && peak
        }

        val renderersFactory = object : DefaultRenderersFactory(context.applicationContext) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                // BARU (v1.6): setAudioOffloadSupportProvider() dipasang dari awal (walau
                // offload baru DIMINTA aktif saat BIT-PERFECT MODE dinyalakan lewat
                // setBitPerfectMode()) -- tanpa provider ini, sink tidak pernah tahu cara
                // mengecek apakah device/USB DAC yang sedang nyambung mendukung offload
                // sama sekali.
                val sink = DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(eqProcessor))
                    .setAudioOffloadSupportProvider(DefaultAudioOffloadSupportProvider(context))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
                audioSinkRef = sink
                return sink
            }
        }.apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        }

        // FIX: sebelumnya ExoPlayer.Builder() tidak diberi LoadControl/SeekParameters sama
        // sekali, jadi memakai default milik ExoPlayer yang dirancang untuk STREAMING
        // (butuh ~2.5 detik buffer sebelum mulai memutar, dan ~5 detik buffer ULANG setiap
        // kali seek) -> inilah sebab "load lagu agak lama" dan "ada jeda tiap seek" yang
        // terasa jauh lebih lambat dibanding aplikasi pemutar musik lain. Untuk file lokal/SAF, buffer
        // sekecil ini sudah cukup dan jauh lebih responsif; SeekParameters.CLOSEST_SYNC
        // juga menghindari demux frame-exact yang memperlambat seek.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 30_000,
                /* bufferForPlaybackMs = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 1_000
            )
            .build()

        val exo = ExoPlayer.Builder(context.applicationContext, renderersFactory)
            .setLoadControl(loadControl)
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            // FIX (patch "akses semua URL stream"): pakai streamDataSourceFactory
            // (OkHttp, lihat catatan di atas) untuk SEMUA sumber -- termasuk file
            // lokal/SAF, karena DefaultDataSource.Factory otomatis fallback ke
            // FileDataSource/ContentDataSource untuk uri non-http, jadi lagu di
            // Library tetap jalan seperti biasa.
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context.applicationContext)
                    .setDataSourceFactory(streamDataSourceFactory)
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            // FIX (mati sendiri 1-2 menit setelah layar mati): sebelumnya wake mode ExoPlayer
            // tidak pernah di-set (default C.WAKE_MODE_NONE) -> begitu layar mati & app masuk
            // background, CPU boleh masuk Deep Sleep oleh sistem walau PlaybackService masih
            // foreground, sehingga decoding audio berhenti di tengah jalan. WAKE_MODE_LOCAL
            // membuat ExoPlayer otomatis pegang PARTIAL_WAKE_LOCK sendiri (butuh permission
            // WAKE_LOCK, sudah ada di manifest) selama isPlaying = true, dilepas otomatis saat
            // pause/stop -- jadi CPU tetap menyala HANYA saat benar-benar sedang memutar.
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        // CATATAN: repeatMode bawaan ExoPlayer sengaja DIBIARKAN REPEAT_MODE_OFF (default).
        // Kalau di-set REPEAT_MODE_ALL di sini, ExoPlayer akan otomatis lompat ke lagu
        // berikutnya sendiri begitu satu lagu habis TANPA PERNAH masuk STATE_ENDED -- padahal
        // onSongEnded (di-hook ke STATE_ENDED di bawah) adalah satu-satunya tempat pilihan
        // RepeatMode pengguna sendiri (OFF/ALL/ONE, lihat PlayerViewModel.handleTrackEnded)
        // diterapkan. Kalau STATE_ENDED tidak pernah tercapai, repeat-satu-lagu dan
        // stop-di-lagu-terakhir (repeat OFF) jadi tidak pernah berfungsi lagi. Wrap-around
        // untuk tombol next/prev MANUAL (di dalam app & swipe kaset) sudah ditangani sendiri
        // lewat modulo di PlayerViewModel.next()/previous(), jadi tidak bergantung ke sini.
        exo.repeatMode = Player.REPEAT_MODE_OFF

        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (!isPlaying) {
                    _vuLevels.value = 0f to 0f
                }
                // BARU (bug "kadang lagu tiba-tiba berhenti, harus tekan Play
                // lagi"): begitu playback beneran jalan lagi dengan sukses (baik
                // itu setelah auto-retry di onPlayerError() di bawah, atau
                // memang lagi normal muter lagu baru), reset jatah retry --
                // supaya kalau ada error LAGI nanti (lagu lain/waktu lain), tetap
                // dapat 1x kesempatan auto-retry juga, bukan cuma sekali seumur
                // hidup sesi app ini.
                if (isPlaying) {
                    hasRetriedAfterError = false
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _durationMs.value = if (exo.duration != C.TIME_UNSET) exo.duration else 0L
                } else if (playbackState == Player.STATE_ENDED) {
                    _isPlaying.value = false
                    _vuLevels.value = 0f to 0f
                    // Lagu selesai secara alami (bukan karena stop() manual, yang membawa
                    // player ke STATE_IDLE, bukan STATE_ENDED) -- beri tahu ViewModel supaya
                    // bisa lanjut ke lagu berikutnya / replay sesuai RepeatMode.
                    onSongEnded?.invoke()
                }
            }

            // FIX (controlbar & swipe kaset): dipanggil setiap kali item aktif di timeline
            // ExoPlayer berpindah -- baik karena tombol next/prev di dalam app, swipe kaset,
            // MAUPUN karena tombol next/prev di notifikasi/lockscreen/headset/Bluetooth/
            // Android Auto (yang langsung memanggil player.seekToNext()/seekToPrevious()
            // tanpa lewat kode Kotlin kita). Tanpa hook ini, judul lagu & posisi antrian di
            // UI app bisa "ketinggalan" / tidak sinkron begitu lagu diganti dari lockscreen.
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = exo.currentMediaItemIndex
                _currentQueueIndex.value = index
                songsById[mediaItem?.mediaId]?.let { song ->
                    _currentSong.value = song
                    _durationMs.value = song.durationMs
                }

                // BARU (patch "Crossfade"): player utama baru saja mencapai SENDIRI
                // lagu yang sedang di-preview lewat crossfadePlayerRef -- ini titik
                // serah-terima yang tepat (persis pas overlap fade selesai, karena
                // preview dimulai [crossfadeSeconds] sebelum titik ini tercapai).
                // Hentikan preview & kembalikan volume player utama ke level user
                // (bukan 1f mentah, supaya tidak override volume swipe kaset yang
                // sedang dipakai).
                if (mediaItem?.mediaId != null && mediaItem.mediaId == crossfadeTargetMediaId) {
                    stopCrossfadePreview()
                    exo.volume = userVolume
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _isPlaying.value = false
                _vuLevels.value = 0f to 0f
                // Sebelumnya error di sini ditelan begitu saja (exo.stop() tanpa jejak apa pun),
                // sehingga kalau lagu gagal diputar (format tidak didukung, izin URI hilang,
                // file terhapus/dipindah, dll), tombol PLAY terlihat "tidak berfungsi" tanpa
                // penjelasan apa pun ke user. Sekarang pesannya ditangkap supaya bisa
                // ditampilkan di UI (lihat PlayerViewModel.lastError / PlayerScreen).
                _lastError.value = error.errorCodeName + ": " + (error.message ?: "Playback gagal")

                // FIX (bug "kadang lagu tiba-tiba berhenti sendiri, harus tekan
                // Play lagi"): sebelumnya SELALU exo.stop() di sini tanpa
                // pengecualian -- ExoPlayer masuk STATE_IDLE dan diam permanen
                // di situ sampai user pencet Play manual (yang baru memicu
                // prepare() ulang lewat playPause() di atas). Banyak
                // PlaybackException itu TRANSIENT/sesaat (glitch decoder,
                // hiccup di audio processor custom seperti limiter/EQ saat
                // memproses konten tertentu, dll) -- bukan berarti file/stream-nya
                // benar-benar rusak permanen. Sekarang dicoba auto-retry SEKALI:
                // prepare() ulang dari item & posisi yang sama secara otomatis,
                // tanpa perlu user menekan apa pun. Kalau errornya terjadi LAGI
                // sebelum sempat playing sukses (lihat reset di
                // onIsPlayingChanged di atas) -- berarti bukan sekadar glitch
                // sesaat, baru dibiarkan diam di STATE_IDLE seperti perilaku
                // lama, supaya tidak infinite-retry-loop kalau file/stream-nya
                // memang benar-benar rusak/hilang/tidak didukung.
                if (!hasRetriedAfterError) {
                    hasRetriedAfterError = true
                    val retryIndex = exo.currentMediaItemIndex
                    val retryPositionMs = exo.currentPosition
                    exo.seekTo(retryIndex, retryPositionMs)
                    exo.prepare()
                    exo.playWhenReady = true
                } else {
                    exo.stop()
                }
            }
        })

        // BARU: Hi-Res Audio Badge Detector. onAudioTrackInitialized dipanggil setiap kali
        // AudioSink (mem)buat ulang android.media.AudioTrack -- yaitu setiap kali format
        // output PCM aktual berubah (lagu baru, USB DAC di-attach/dilepas, dst). Ini dipakai
        // (bukan Format dari track pemilih/onTracksChanged) karena mencerminkan sample rate
        // & bit depth yang BENAR-BENAR dikirim ke hardware sekarang, bukan sekadar metadata
        // file sumber.
        exo.addAnalyticsListener(object : AnalyticsListener {
            // Dipanggil setiap kali format audio yang MASUK ke decoder berubah (lagu
            // baru dimulai) -- ini format codec ASLI dari file (mis. "audio/flac",
            // "audio/mp4a-latm" untuk AAC, "audio/opus", dst), sebelum di-decode jadi
            // PCM. Disimpan supaya onAudioTrackInitialized di bawah tahu apakah sumber
            // aslinya lossless atau lossy.
            override fun onAudioInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?
            ) {
                currentAudioMimeType = format.sampleMimeType

                // FIX (bug "bitrate selalu 0"): utamakan header ICY "icy-br" yang dikirim
                // server Shoutcast/Icecast (dilampirkan Media3 di format.metadata) karena
                // itu angka bitrate yang benar-benar diiklankan stasiun radio -- baru
                // fallback ke Format.bitrate/peakBitrate dari container (mis. MP4/AAC
                // progresif) kalau server tidak kirim ICY sama sekali. Kalau dua-duanya
                // tidak diketahui, dibiarkan 0 (UI menampilkan "--" alih-alih angka palsu).
                val icyBitrateBps = format.metadata
                    ?.let { meta -> (0 until meta.length()).mapNotNull { meta.get(it) as? IcyHeaders } }
                    ?.firstOrNull()
                    ?.bitrate
                    ?.takeIf { it > 0 }

                val containerBitrateBps = format.bitrate
                    .takeIf { it != Format.NO_VALUE && it > 0 }
                    ?: format.peakBitrate.takeIf { it != Format.NO_VALUE && it > 0 }

                _streamBitrateKbps.value = (icyBitrateBps ?: containerBitrateBps ?: 0) / 1000
            }

            override fun onAudioTrackInitialized(
                eventTime: AnalyticsListener.EventTime,
                audioTrackConfig: AudioSink.AudioTrackConfig
            ) {
                _sampleRate.value = audioTrackConfig.sampleRate
                _bitDepth.value = bitDepthFromEncoding(audioTrackConfig.encoding)

                // BARU (v1.8): AudioTrackConfig.offload adalah status offload AKTUAL
                // yang benar-benar dipakai AudioTrack yang baru dibuat ini -- inilah
                // sumber kebenaran real-time untuk baris OFFLOAD di Status Panel VFD
                // dan status di bawah toggle BIT-PERFECT MODE (lihat EqualizerScreen).
                _offloadActive.value = audioTrackConfig.offload

                // Hi-Res HARUS lossless (FLAC/WAV/ALAC/DSD/raw PCM). Codec lossy seperti
                // AAC, MP3, Opus, atau OGG-Vorbis tidak pernah dianggap Hi-Res walau
                // sample rate output-nya kebetulan >= 48kHz -- karena detail audio yang
                // "hi-res" itu sudah dibuang saat encoding, jadi badge yang jujur wajib
                // menolaknya.
                val isLossless = isLosslessMimeType(currentAudioMimeType)
                _isHiRes.value = isLossless &&
                    (_sampleRate.value >= 48_000 || _bitDepth.value > 16)

                // LOG SEMENTARA untuk verifikasi lewat `adb logcat -s HiResBadge`.
                // Aman dibiarkan (cuma Log.d, tidak berdampak ke rilis), tapi boleh
                // dihapus kalau sudah tidak dibutuhkan.
                Log.d(
                    "HiResBadge",
                    "mime=$currentAudioMimeType lossless=$isLossless " +
                        "sampleRate=${_sampleRate.value}Hz bitDepth=${_bitDepth.value}bit " +
                        "isHiRes=${_isHiRes.value}"
                )
            }
        })

        return exo
    }

    /** Konversi konstanta android.media.AudioFormat.ENCODING_* -> lebar bit PCM aktual. */
    private fun bitDepthFromEncoding(encoding: Int): Int = when (encoding) {
        AudioFormat.ENCODING_PCM_8BIT -> 8
        AudioFormat.ENCODING_PCM_16BIT -> 16
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
        AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 32
        else -> 16
    }

    /**
     * Badge "HI-RES" secara audiophile SELALU mensyaratkan codec lossless -- ini daftar
     * MIME type yang dianggap lossless. Format lossy (AAC/MP3/Opus/Vorbis/AC3/dst)
     * SENGAJA tidak dimasukkan meskipun sample rate/bit depth-nya tinggi.
     */
    private fun isLosslessMimeType(mime: String?): Boolean = when (mime) {
        MimeTypes.AUDIO_FLAC,
        MimeTypes.AUDIO_ALAC,
        MimeTypes.AUDIO_RAW,
        // String literal (bukan konstanta MimeTypes) karena file WAV pada praktiknya
        // dilaporkan Media3 sebagai MimeTypes.AUDIO_RAW setelah di-extract -- dua
        // string ini dijaga sebagai fallback untuk extractor/provider lain yang
        // melaporkan MIME "audio/wav" apa adanya.
        "audio/wav",
        "audio/x-wav",
        "audio/dsd" -> true
        else -> false
    }

    /**
     * Memuat lagu terakhir + posisi terakhir TANPA langsung memutar (playWhenReady = false).
     * Dipanggil sekali saat app dibuka, supaya layar player langsung menunjukkan lagu &
     * posisi terakhir seperti sebelum app ditutup.
     *
     * Ini juga yang membuat tombol PLAY bisa langsung berfungsi begitu ditekan pertama kali:
     * sebelumnya, kalau belum ada lagu yang dimuat sama sekali, ExoPlayer berada di
     * STATE_IDLE tanpa MediaItem apa pun, sehingga player.play() di playPause() di bawah
     * tidak melakukan apa-apa (tombol terlihat "tidak berfungsi").
     */
    fun restoreSong(song: Song, positionMs: Long) {
        _currentSong.value = song
        _currentStreamTitle.value = null
        val mediaItem = MediaItem.Builder()
            .setUri(song.uri)
            .setMediaId(song.id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .build()
            )
            .build()

        player.setMediaItem(mediaItem, positionMs.coerceAtLeast(0L))
        player.prepare()
        player.playWhenReady = false
        _durationMs.value = song.durationMs
        _positionMs.value = positionMs
    }

    fun playSong(song: Song) {
        // FIX (patch "error stream nyangkut"): lihat catatan yang sama di playStreamUrl().
        _lastError.value = null
        _currentSong.value = song
        _currentStreamTitle.value = null
        val mediaItem = buildMediaItem(song)

        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
        _durationMs.value = song.durationMs
    }

    private fun buildMediaItem(song: Song): MediaItem =
        MediaItem.Builder()
            .setUri(song.uri)
            .setMediaId(song.id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .build()
            )
            .build()

    /**
     * FIX UTAMA controlbar statusbar/lockscreen + swipe kaset: memuat SELURUH antrian [songs]
     * (bukan cuma 1 lagu) ke timeline ExoPlayer, dengan [startIndex] sebagai lagu yang aktif
     * sekarang. Dipanggil oleh PlayerViewModel setiap kali antrian berubah (buka lagu dari
     * Library, folder baru selesai di-scan, shuffle, dst).
     *
     * Sebelumnya PlayerManager cuma pernah tahu 1 lagu dalam satu waktu (lewat playSong/
     * restoreSong -> player.setMediaItem tunggal), jadi ExoPlayer/MediaSession tidak pernah
     * "melihat" ada lagu lain di sekitarnya -> tombol Next/Prev di notifikasi & lockscreen
     * tidak muncul/tidak berfungsi walau di dalam app pengguna sebetulnya punya banyak lagu.
     */
    fun setQueue(songs: List<Song>, startIndex: Int, startPositionMs: Long = 0L, playWhenReady: Boolean = true) {
        if (songs.isEmpty()) return
        // FIX (patch "error stream nyangkut"): lihat catatan yang sama di playStreamUrl().
        _lastError.value = null
        stopCrossfadePreview(restoreVolume = true)
        val safeIndex = startIndex.coerceIn(0, songs.size - 1)

        songsById = songs.associateBy { it.id.toString() }
        val mediaItems = songs.map { buildMediaItem(it) }

        _currentSong.value = songs[safeIndex]
        _currentStreamTitle.value = null
        _currentQueueIndex.value = safeIndex
        _durationMs.value = songs[safeIndex].durationMs

        player.setMediaItems(mediaItems, safeIndex, startPositionMs.coerceAtLeast(0L))
        player.prepare()
        player.playWhenReady = playWhenReady
    }

    /**
     * BARU (fix bug "lagu kesendat sesaat tiap kali tombol Shuffle ditekan"):
     * sebelumnya toggleShuffle() di PlayerViewModel memanggil setQueue() di
     * atas buat menerapkan urutan baru -- setQueue() SELALU memanggil
     * player.setMediaItems() + player.prepare(), yang artinya item yang
     * SEDANG DIPUTAR pun ikut di-reprepare/rebuffer dari nol, walau isinya
     * cuma pindah urutan sekitar (bukan ganti lagu). Itu sebabnya kedengaran
     * "kesendat sesaat" tiap Shuffle ditekan.
     *
     * Fungsi ini menata ulang urutan lewat player.moveMediaItem() -- API
     * ExoPlayer yang MEMANG dirancang khusus buat mengubah posisi item di
     * timeline TANPA menyentuh state playback item mana pun (termasuk yang
     * sedang aktif), beda total dari setMediaItems() yang selalu menghitung
     * ulang seluruh timeline dari awal. Dicocokkan berdasarkan mediaId (id
     * lagu, dibuat di buildMediaItem() di atas) -- bukan index polos --
     * supaya pencocokan tetap benar walau urutan lama & baru sudah beda jauh.
     *
     * CATATAN: cuma dipakai untuk RE-ORDER (jumlah lagu sebelum & sesudah
     * harus SAMA PERSIS) -- bukan buat menambah/menghapus lagu dari antrian.
     * Kalau jumlahnya beda (harusnya tidak pernah terjadi dari toggleShuffle,
     * tapi dijaga untuk keamanan), fallback ke setQueue() biasa supaya tidak
     * menghasilkan timeline yang salah/korup.
     */
    fun reorderQueue(newSongs: List<Song>) {
        if (newSongs.isEmpty()) return

        if (newSongs.size != player.mediaItemCount) {
            val current = _currentSong.value
            val idx = current?.let { c -> newSongs.indexOfFirst { it.id == c.id } }?.takeIf { it >= 0 } ?: 0
            setQueue(newSongs, idx, player.currentPosition, playWhenReady = player.isPlaying)
            return
        }

        songsById = newSongs.associateBy { it.id.toString() }

        for (targetIndex in newSongs.indices) {
            val targetId = newSongs[targetIndex].id.toString()
            var foundIndex = -1
            for (i in targetIndex until player.mediaItemCount) {
                if (player.getMediaItemAt(i).mediaId == targetId) {
                    foundIndex = i
                    break
                }
            }
            if (foundIndex != -1 && foundIndex != targetIndex) {
                player.moveMediaItem(foundIndex, targetIndex)
            }
        }

        _currentQueueIndex.value = player.currentMediaItemIndex
    }

    /**
     * BARU (Android Auto): salinan urutan lagu dalam antrian yang sedang dimuat sekarang
     * (persis isi [songsById], urutan insersi = urutan antrian). Dipakai PlaybackService
     * untuk menyusun pohon Browse ("Semua Lagu") yang ditampilkan Android Auto, TANPA
     * PlaybackService perlu tahu struktur internal PlayerManager.
     */
    fun queueSongs(): List<Song> = songsById.values.toList()

    /**
     * BARU (Android Auto): daftarkan ulang isi [songsById] SAJA, tanpa memanggil
     * player.setMediaItems() di sini -- dipakai saat MediaLibrarySession.Callback
     * (PlaybackService) sudah/akan menyerahkan resolusi antrian baru ke framework Media3
     * sendiri (lihat onSetMediaItems), supaya tidak double-load timeline yang sama dua
     * kali. Cukup supaya listener onMediaItemTransition di atas tetap bisa mencocokkan
     * mediaId -> Song begitu framework benar-benar mengganti timeline ExoPlayer, sehingga
     * _currentSong/_currentQueueIndex (dibaca UI app) tetap sinkron walau antrian barusan
     * dipilih dari luar app (Android Auto), bukan dari Library di dalam app.
     */
    fun registerQueueSongs(songs: List<Song>) {
        songsById = songs.associateBy { it.id.toString() }
    }

    /**
     * BARU (Android Auto): akses publik ke [buildMediaItem] privat di atas, dipakai
     * PlaybackService untuk membangun ulang MediaItem yang benar-benar bisa diputar
     * (lengkap dengan URI asli) dari [Song] hasil Browse tree / playFromMediaId.
     */
    fun buildPlayableMediaItem(song: Song): MediaItem = buildMediaItem(song)

    /** Pindah ke lagu ke-[index] dalam antrian yang sedang dimuat lewat [setQueue]. */
    fun seekToQueueItem(index: Int) {
        if (index !in 0 until player.mediaItemCount) return
        stopCrossfadePreview(restoreVolume = true)
        player.seekTo(index, 0L)
        if (!player.isPlaying) player.playWhenReady = true
    }

    /**
     * Samakan repeatMode ExoPlayer sendiri dengan pilihan RepeatMode pengguna (OFF/ALL/ONE
     * di UI). PENTING sejak antrian penuh dimuat lewat [setQueue]: untuk ALL & ONE, ExoPlayer
     * sekarang yang mengurus lanjut/ulang lagu secara NATIVE lewat timeline-nya sendiri
     * (STATE_ENDED tidak akan pernah tercapai untuk kedua mode ini) -- konsisten juga dengan
     * apa yang ditampilkan/dilakukan tombol repeat di notifikasi/lockscreen kalau ada.
     * Terima nilai androidx.media3.common.Player.REPEAT_MODE_* langsung supaya PlayerManager
     * tidak perlu tahu soal enum RepeatMode milik PlayerViewModel.
     */
    fun setRepeatMode(mode: Int) {
        player.repeatMode = mode
    }

    fun playStreamUrl(url: String, title: String = "Live Stream") {
        // FIX (patch "error stream nyangkut"): pesan error dari URL/stream sebelumnya
        // (mis. HTTP 404/403/timeout) disimpan di _lastError dan baru dihapus lewat
        // clearError() -- tapi clearError() sebelumnya tidak pernah dipanggil otomatis,
        // jadi walau URL baru berhasil diputar, banner error lama tetap nyangkut di UI
        // (PlayerScreen) sampai user memanggil clearError() manual. Reset di sini supaya
        // setiap kali user coba stream URL baru, error lama otomatis hilang duluan.
        _lastError.value = null
        _currentSong.value = null
        // FIX (patch "streaming label"): dulu di sini cuma di-set null tanpa
        // pengganti apa pun -- lihat catatan di deklarasi _currentStreamTitle di atas.
        _currentStreamTitle.value = title
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist("Streaming")
                    .build()
            )
            .build()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }

    fun playPause() {
        // FIX: kalau belum pernah ada MediaItem yang di-prepare (STATE_IDLE), player.play()
        // di bawah tidak melakukan apa-apa sama sekali -> inilah sebab tombol PLAY terlihat
        // tidak berfungsi. Panggil prepare() dulu supaya ExoPlayer benar-benar bisa mulai.
        if (player.playbackState == Player.STATE_IDLE) {
            player.prepare()
        }

        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0)
            player.play()
        } else if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    // BARU (v2.1): pause() EKSPLISIT untuk Sleep Timer. Sengaja TIDAK memakai playPause()
    // yang ada di atas -- playPause() itu TOGGLE (kalau kebetulan sedang pause, dia akan
    // PLAY lagi), sedangkan Sleep Timer harus selalu MENGHENTIKAN playback saat waktunya
    // habis, tidak peduli pemanggil memanggilnya dua kali atau timing race apa pun.
    fun pause() {
        if (player.isPlaying) player.pause()
        // BARU (patch "Crossfade"): pause manual di tengah overlap akan
        // terdengar aneh (preview lagu berikutnya ikut kepause di volume
        // separuh) -- batalkan saja, lebih dari cukup untuk kasus ini.
        stopCrossfadePreview(restoreVolume = true)
    }

    fun stop() {
        stopCrossfadePreview(restoreVolume = true)
        player.stop()
        // FIX (patch "play diam setelah stop di mode stream"): player.stop() TIDAK
        // mereset posisi playback -- ExoPlayer tetap "ingat" posisi terakhir sebelum
        // stop, dan kalau nanti prepare()+play() dipanggil lagi (lihat playPause()),
        // ExoPlayer mencoba melanjutkan dari posisi itu. Untuk lagu biasa ini aman
        // (file selalu bisa di-seek ke posisi mana pun), tapi untuk STREAM radio live
        // (Icecast/Shoutcast) posisi lama itu tidak valid lagi karena sumbernya terus
        // berjalan & umumnya tidak mendukung seek -- akibatnya player nyangkut selamanya
        // di STATE_BUFFERING tanpa suara dan TANPA error apa pun yang terlihat user
        // (makanya kelihatan "diam saja"). seekTo(0) di sini memaksa ExoPlayer mulai
        // ulang dari awal koneksi/stream (bukan dari posisi basi) begitu di-play lagi --
        // aman juga untuk lagu biasa karena STOP secara semantik memang beda dari PAUSE
        // (pause menahan di tempat, stop mengembalikan ke awal, sama seperti kaset asli).
        player.seekTo(0)
        _isPlaying.value = false
        _vuLevels.value = 0f to 0f
        _peakActive.value = false
        _positionMs.value = 0L
        _isHiRes.value = false
        _sampleRate.value = 0
        _bitDepth.value = 16
    }

    fun seekTo(ms: Long) {
        // BARU (patch "Crossfade"): seek manual (drag progress bar) menjauhkan
        // posisi dari jendela fade yang sedang dihitung -- batalkan preview
        // supaya tidak nyangkut; kalau ternyata posisi baru MASIH di dalam
        // jendela fade, pollPosition() tick berikutnya akan memulainya lagi
        // secara wajar.
        stopCrossfadePreview(restoreVolume = true)
        player.seekTo(ms)
        _positionMs.value = ms
    }

    // BARU: efek suara "FF/RW" ala kaset asli -- dipanggil terus-menerus SELAMA
    // roda kaset diputar manual untuk seeking (lihat CassetteDeck.kt onScrubSpeedChange).
    // [speedMultiplier] > 1f = putar MAJU cepat (pitch naik, kayak fast-forward),
    // < 1f (tapi tetap positif) = arah MUNDUR/rewind (kita simulasikan dengan pitch
    // turun -- ExoPlayer sendiri tidak bisa memutar audio benar-benar mundur, jadi
    // arah rewind ditandai dari pitch yang turun, bukan dari audio yang benar-benar
    // berjalan terbalik). Speed & pitch DISAMAKAN nilainya supaya suaranya berubah
    // nada sekaligus tempo, persis seperti motor kaset fisik yang dipercepat/
    // diperlambat manual, bukan cuma time-stretch digital yang pitch-nya rata.
    fun setTapeScrubSpeed(speedMultiplier: Float) {
        val clamped = speedMultiplier.coerceIn(0.25f, 3f)
        player.playbackParameters = androidx.media3.common.PlaybackParameters(
            clamped,
            clamped
        )
    }

    // Kembalikan speed & pitch ke normal (1x) begitu jari diangkat dari roda kaset.
    fun resetTapeScrubSpeed() {
        player.playbackParameters = androidx.media3.common.PlaybackParameters(1f, 1f)
    }

    fun setEqPreset(preset: EqPreset) {
        eqProcessor.setPreset(preset)
    }

    fun setEqBypass(bypass: Boolean) {
        eqProcessor.bypass = bypass
    }

    // BARU (v1.6): passthrough master override BIT-PERFECT MODE ke
    // ParametricEqAudioProcessor, SEKALIGUS meminta jalur AUDIO OFFLOAD hardware
    // (direct-path ke DSP chip device/USB DAC, bypass AudioProcessor sepenuhnya
    // di level Android) lewat applyOffloadPreference() di bawah.
    fun setBitPerfectMode(enabled: Boolean) {
        eqProcessor.bitPerfectMode = enabled
        applyOffloadPreference(enabled)
    }

    /**
     * BARU (v1.6): minta ExoPlayer + AudioSink mencoba jalur AUDIO OFFLOAD hardware.
     * Ini PERMINTAAN/preference, BUKAN jaminan -- Android & ExoPlayer sendiri yang
     * memutuskan final apakah kombinasi device + USB DAC + format lagu yang sedang
     * diputar benar-benar mendukungnya. Kalau tidak didukung, ExoPlayer otomatis
     * fallback diam-diam ke jalur non-offload biasa (EQ/DSP software tetap jalan
     * seperti biasa lewat eqProcessor, TIDAK ada silent failure/lagu berhenti).
     *
     * Kenapa offload relevan untuk "bit-perfect": saat offload BENAR-BENAR aktif,
     * audio dikirim langsung ke chip DSP hardware TANPA lewat AudioProcessor app
     * (termasuk eqProcessor kita) sama sekali -- dan pada banyak device, jalur
     * offload inilah yang menghindari resampling paksa oleh mixer software Android,
     * karena tujuan offload memang mengirim stream sedekat mungkin ke bentuk
     * aslinya demi hemat daya. Untuk device/format yang TIDAK mendukung offload,
     * app tetap sepenuhnya berfungsi lewat BIT-PERFECT MODE software (bypass total
     * di eqProcessor) yang sudah ada -- toggle "BIT-PERFECT MODE" yang sama
     * mengaktifkan keduanya sekaligus, generik untuk device/DAC apa pun.
     */
    private fun applyOffloadPreference(enabled: Boolean) {
        // BARU (v1.8): reset dulu status AKTUAL ke false setiap kali preference
        // berubah (baik dinyalakan MAUPUN dimatikan) -- status sebelumnya jadi basi
        // begitu preference berubah, dan nilai yang benar-benar baru cuma bisa
        // dipastikan lewat onAudioTrackInitialized berikutnya (lihat buildPlayer()).
        // Tanpa reset ini, baris OFFLOAD di Status Panel VFD/Equalizer bisa sempat
        // menampilkan status lagu SEBELUMNYA sesaat setelah toggle ditekan.
        _offloadActive.value = false

        val offloadPreferences = if (enabled) {
            TrackSelectionParameters.AudioOffloadPreferences.Builder()
                .setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
                .setIsGaplessSupportRequired(true)
                // FIX konflik dengan fitur FF/RW kaset (lihat setTapeScrubSpeed di
                // bawah): tanpa ini, sebagian device diam-diam mengabaikan perubahan
                // speed/pitch saat sedang di jalur offload, atau pada beberapa device
                // (dilaporkan resmi di tracker Media3) bisa melempar exception. Dengan
                // ini, ExoPlayer TIDAK akan memilih jalur offload untuk device yang
                // tidak sanggup mendukung speed change juga -- lebih aman daripada
                // scrub kaset diam-diam berhenti berfungsi.
                .setIsSpeedChangeSupportRequired(true)
                .build()
        } else {
            TrackSelectionParameters.AudioOffloadPreferences.DEFAULT
        }

        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setAudioOffloadPreferences(offloadPreferences)
            .build()

        // setOffloadMode() cuma tersedia mulai API 29 (Android 10) -- di bawah itu,
        // toggle BIT-PERFECT MODE tetap berfungsi penuh lewat bypass software di
        // eqProcessor, cuma tanpa kemungkinan hardware offload tambahan.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            audioSinkRef?.setOffloadMode(
                if (enabled) {
                    DefaultAudioSink.OFFLOAD_MODE_ENABLED_GAPLESS_NOT_REQUIRED
                } else {
                    DefaultAudioSink.OFFLOAD_MODE_DISABLED
                }
            )
        }

        // CATATAN (v1.8): status offload AKTUAL sekarang juga di-expose lewat
        // offloadActive (StateFlow) -- lihat baris OFFLOAD di Status Panel VFD dan
        // status di bawah toggle BIT-PERFECT MODE di tab BATAS Equalizer. Log ini
        // sengaja dipertahankan untuk debugging lebih detail lewat adb logcat.
        Log.d(
            "BitPerfectMode",
            "requested=$enabled sdkInt=${Build.VERSION.SDK_INT} -- status offload " +
                "AKTUAL dikonfirmasi ulang di onAudioTrackInitialized (lihat " +
                "offloadActive)."
        )
    }

    fun setLimiterEnabled(enabled: Boolean) {
        eqProcessor.limiterEnabled = enabled
    }

    // BARU (v1.9): passthrough tipis ke ParametricEqAudioProcessor untuk REPLAY GAIN
    // -- pola sama persis dengan setLimiterEnabled/setEqBypass di atas. Lihat komentar
    // panjang di ParametricEqAudioProcessor.replayGainEnabled untuk detail & batasan
    // jujur fitur ini.
    fun setReplayGainEnabled(enabled: Boolean) {
        eqProcessor.replayGainEnabled = enabled
    }

    fun setReplayGainDb(db: Double) {
        eqProcessor.setReplayGainDb(db)
    }

    fun beginReplayGainMeasurement() {
        eqProcessor.beginReplayGainMeasurement()
    }

    fun finishReplayGainMeasurement(): Double? = eqProcessor.finishReplayGainMeasurement()

    fun cancelReplayGainMeasurement() {
        eqProcessor.cancelReplayGainMeasurement()
    }

    // BARU (patch "DSP control knobs"): passthrough tipis ke ParametricEqAudioProcessor
    // untuk Vocal enhancer (tombol vocal + knop bass/treble vokal) dan trio kontrol
    // image stereo (Balance, Stereo Expansion, Mono/Stereo) -- pola sama persis dengan
    // setEqBypass/setLimiterEnabled di atas.
    fun setVocalEnabled(enabled: Boolean) {
        eqProcessor.setVocalEnabled(enabled)
    }

    fun setVocalBass(db: Double) {
        eqProcessor.setVocalBass(db)
    }

    fun setVocalTreble(db: Double) {
        eqProcessor.setVocalTreble(db)
    }

    fun setBalance(value: Double) {
        eqProcessor.setBalance(value)
    }

    fun setStereoExpansion(value: Double) {
        eqProcessor.setStereoExpansion(value)
    }

    fun setMonoStereo(monoOn: Boolean) {
        eqProcessor.setMonoStereo(monoOn)
    }

    // BARU (patch "headroom slider"): passthrough tipis ke ParametricEqAudioProcessor
    // untuk rasio auto-preamp/headroom management -- pola sama persis dengan
    // setStereoExpansion/setVocalBass di atas. Lihat komentar panjang di
    // ParametricEqAudioProcessor.headroomSafetyRatio untuk detail kenapa nilai ini
    // sekarang bisa diubah runtime (dulu hardcode `private val`).
    fun setHeadroomSafetyRatio(value: Double) {
        eqProcessor.setHeadroomSafetyRatio(value)
    }

    // FITUR BARU: volume dikontrol lewat swipe vertikal di kaset (lihat CassetteDeck +
    // PlayerScreen). Ini terpisah dari preamp/EQ -- player.volume adalah gain akhir
    // ExoPlayer setelah EQ, jadi tidak mengubah kurva/headroom EQ sama sekali, cuma
    // menaik-turunkan level output secara linear seperti tombol volume biasa.
    fun setVolume(volume: Float) {
        userVolume = volume.coerceIn(0f, 1f)
        // Kalau sedang crossfade, JANGAN timpa langsung -- kurva fade di
        // pollPosition() di bawah yang mengatur player.volume tiap tick,
        // memakai userVolume sebagai batas atasnya. Di luar crossfade, berlaku
        // seperti sebelumnya (langsung diterapkan).
        if (crossfadeTargetMediaId == null) {
            player.volume = userVolume
        }
    }

    // BARU (patch "Crossfade"): dipanggil dari EqualizerScreen (tab BATAS) lewat
    // PlayerViewModel. Kalau dimatikan SELAGU crossfade sedang berjalan, preview
    // dibatalkan seketika supaya tidak ada 2 lagu nyaring bersamaan tanpa fade.
    fun setCrossfadeEnabled(enabled: Boolean) {
        crossfadeEnabled = enabled
        if (!enabled) stopCrossfadePreview(restoreVolume = true)
    }

    fun setCrossfadeSeconds(seconds: Float) {
        crossfadeSeconds = seconds.coerceIn(1f, 8f)
    }

    // Mulai preview lagu berikutnya di player kedua, volume 0 (langsung naik
    // lewat kurva di pollPosition()). handleAudioFocus = false & wake mode
    // default sengaja dipakai -- player ini cuma hidup singkat (durasi overlap),
    // fokus audio & wake lock tetap dipegang penuh oleh player utama.
    private fun startCrossfadePreview(nextItem: MediaItem) {
        val cf = crossfadePlayerRef ?: ExoPlayer.Builder(context.applicationContext)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ false
            )
            .build()
            .also { crossfadePlayerRef = it }

        cf.volume = 0f
        cf.setMediaItem(nextItem)
        cf.prepare()
        cf.playWhenReady = true
        crossfadeTargetMediaId = nextItem.mediaId
    }

    private fun stopCrossfadePreview(restoreVolume: Boolean = false) {
        if (crossfadeTargetMediaId == null && crossfadePlayerRef == null) return
        crossfadePlayerRef?.let {
            it.playWhenReady = false
            it.stop()
            it.clearMediaItems()
        }
        crossfadeTargetMediaId = null
        if (restoreVolume) player.volume = userVolume
    }

    fun pollPosition() {
        if (player.isPlaying) {
            _positionMs.value = player.currentPosition
            if (player.duration != C.TIME_UNSET) {
                _durationMs.value = player.duration
            }

            // BARU (patch "Crossfade"): jendela overlap dicek tiap tick polling
            // ini (~66ms dari PlayerViewModel), bukan job/coroutine terpisah --
            // konsisten dengan pola PlayerManager yang sudah semuanya digerakkan
            // dari 1 loop polling yang sama.
            if (crossfadeEnabled && player.duration != C.TIME_UNSET) {
                val remainingMs = player.duration - player.currentPosition
                val fadeMs = (crossfadeSeconds * 1000L).toLong().coerceAtLeast(200L)

                if (remainingMs in 1..fadeMs && player.hasNextMediaItem()) {
                    val nextItem = player.getMediaItemAt(player.nextMediaItemIndex)
                    if (crossfadeTargetMediaId != nextItem.mediaId) {
                        startCrossfadePreview(nextItem)
                    }
                    val fraction = (1f - remainingMs.toFloat() / fadeMs.toFloat()).coerceIn(0f, 1f)
                    player.volume = userVolume * (1f - fraction)
                    crossfadePlayerRef?.volume = userVolume * fraction
                } else if (crossfadeTargetMediaId != null && remainingMs > fadeMs) {
                    // User seek mundur menjauhi jendela fade (mis. scrub kaset) --
                    // batalkan preview yang sudah kadung dimulai, biar tidak
                    // nyangkut di volume separuh.
                    stopCrossfadePreview(restoreVolume = true)
                }
            }
        }
    }

    fun release() {
        _vuLevels.value = 0f to 0f
        _peakActive.value = false
        usbDacObserver.unregister()
        crossfadePlayerRef?.release()
        crossfadePlayerRef = null
        player.release()
    }
}
