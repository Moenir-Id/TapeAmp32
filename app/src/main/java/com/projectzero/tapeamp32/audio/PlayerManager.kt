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

        @Volatile private var instance: PlayerManager? = null

        fun getInstance(context: Context): PlayerManager =
            instance ?: synchronized(this) {
                instance ?: PlayerManager(context.applicationContext).also { instance = it }
            }
    }

    val eqProcessor = ParametricEqAudioProcessor()

    private var audioSinkRef: DefaultAudioSink? = null

    val usbDacObserver = UsbDacObserver(context)

    private var hasRetriedAfterError = false

    private val _vuLevels = MutableStateFlow(0f to 0f)
    val vuLevels: StateFlow<Pair<Float, Float>> = _vuLevels

    private val _dspEngineOn = MutableStateFlow(true)
    val dspEngineOn: StateFlow<Boolean> = _dspEngineOn

    private val _peakActive = MutableStateFlow(false)
    val peakActive: StateFlow<Boolean> = _peakActive

    private val _offloadActive = MutableStateFlow(false)
    val offloadActive: StateFlow<Boolean> = _offloadActive

    private val _isHiRes = MutableStateFlow(false)
    val isHiRes: StateFlow<Boolean> = _isHiRes

    private val _sampleRate = MutableStateFlow(0)
    val sampleRate: StateFlow<Int> = _sampleRate

    private val _bitDepth = MutableStateFlow(16)
    val bitDepth: StateFlow<Int> = _bitDepth

    private val _streamBitrateKbps = MutableStateFlow(0)
    val streamBitrateKbps: StateFlow<Int> = _streamBitrateKbps

    private var currentAudioMimeType: String? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong

    private val _currentStreamTitle = MutableStateFlow<String?>(null)
    val currentStreamTitle: StateFlow<String?> = _currentStreamTitle

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    var onSongEnded: (() -> Unit)? = null

    private var songsById: Map<String, Song> = emptyMap()

    private var crossfadePlayerRef: ExoPlayer? = null

    private var crossfadeEnabled = false
    private var crossfadeSeconds = 4f

    private var crossfadeTargetMediaId: String? = null

    private var userVolume = 1f

    private val _currentQueueIndex = MutableStateFlow(0)

    val currentQueueIndex: StateFlow<Int> = _currentQueueIndex

    fun clearError() {
        _lastError.value = null
    }

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

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                 15_000,
                 30_000,
                 500,
                 1_000
            )
            .build()

        val exo = ExoPlayer.Builder(context.applicationContext, renderersFactory)
            .setLoadControl(loadControl)
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)

            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context.applicationContext)
                    .setDataSourceFactory(streamDataSourceFactory)
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                 true
            )
            .setHandleAudioBecomingNoisy(true)

            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        exo.repeatMode = Player.REPEAT_MODE_OFF

        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (!isPlaying) {
                    _vuLevels.value = 0f to 0f
                }

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

                    onSongEnded?.invoke()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = exo.currentMediaItemIndex
                _currentQueueIndex.value = index
                songsById[mediaItem?.mediaId]?.let { song ->
                    _currentSong.value = song
                    _durationMs.value = song.durationMs
                }

                if (mediaItem?.mediaId != null && mediaItem.mediaId == crossfadeTargetMediaId) {
                    stopCrossfadePreview()
                    exo.volume = userVolume
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _isPlaying.value = false
                _vuLevels.value = 0f to 0f

                _lastError.value = error.errorCodeName + ": " + (error.message ?: "Playback gagal")

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

        exo.addAnalyticsListener(object : AnalyticsListener {

            override fun onAudioInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?
            ) {
                currentAudioMimeType = format.sampleMimeType

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

                _offloadActive.value = audioTrackConfig.offload

                val isLossless = isLosslessMimeType(currentAudioMimeType)
                _isHiRes.value = isLossless &&
                    (_sampleRate.value >= 48_000 || _bitDepth.value > 16)

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

    private fun bitDepthFromEncoding(encoding: Int): Int = when (encoding) {
        AudioFormat.ENCODING_PCM_8BIT -> 8
        AudioFormat.ENCODING_PCM_16BIT -> 16
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
        AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 32
        else -> 16
    }

    private fun isLosslessMimeType(mime: String?): Boolean = when (mime) {
        MimeTypes.AUDIO_FLAC,
        MimeTypes.AUDIO_ALAC,
        MimeTypes.AUDIO_RAW,

        "audio/wav",
        "audio/x-wav",
        "audio/dsd" -> true
        else -> false
    }

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

    fun setQueue(songs: List<Song>, startIndex: Int, startPositionMs: Long = 0L, playWhenReady: Boolean = true) {
        if (songs.isEmpty()) return

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

    fun queueSongs(): List<Song> = songsById.values.toList()

    fun registerQueueSongs(songs: List<Song>) {
        songsById = songs.associateBy { it.id.toString() }
    }

    fun buildPlayableMediaItem(song: Song): MediaItem = buildMediaItem(song)

    fun seekToQueueItem(index: Int) {
        if (index !in 0 until player.mediaItemCount) return
        stopCrossfadePreview(restoreVolume = true)
        player.seekTo(index, 0L)
        if (!player.isPlaying) player.playWhenReady = true
    }

    fun setRepeatMode(mode: Int) {
        player.repeatMode = mode
    }

    fun playStreamUrl(url: String, title: String = "Live Stream") {

        _lastError.value = null
        _currentSong.value = null

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

    fun pause() {
        if (player.isPlaying) player.pause()

        stopCrossfadePreview(restoreVolume = true)
    }

    fun stop() {
        stopCrossfadePreview(restoreVolume = true)
        player.stop()

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

        stopCrossfadePreview(restoreVolume = true)
        player.seekTo(ms)
        _positionMs.value = ms
    }

    fun setTapeScrubSpeed(speedMultiplier: Float) {
        val clamped = speedMultiplier.coerceIn(0.25f, 3f)
        player.playbackParameters = androidx.media3.common.PlaybackParameters(
            clamped,
            clamped
        )
    }

    fun resetTapeScrubSpeed() {
        player.playbackParameters = androidx.media3.common.PlaybackParameters(1f, 1f)
    }

    fun setEqPreset(preset: EqPreset) {
        eqProcessor.setPreset(preset)
    }

    fun setEqBypass(bypass: Boolean) {
        eqProcessor.bypass = bypass
    }

    fun setBitPerfectMode(enabled: Boolean) {
        eqProcessor.bitPerfectMode = enabled
        applyOffloadPreference(enabled)
    }

    private fun applyOffloadPreference(enabled: Boolean) {

        _offloadActive.value = false

        val offloadPreferences = if (enabled) {
            TrackSelectionParameters.AudioOffloadPreferences.Builder()
                .setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
                .setIsGaplessSupportRequired(true)

                .setIsSpeedChangeSupportRequired(true)
                .build()
        } else {
            TrackSelectionParameters.AudioOffloadPreferences.DEFAULT
        }

        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setAudioOffloadPreferences(offloadPreferences)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            audioSinkRef?.setOffloadMode(
                if (enabled) {
                    DefaultAudioSink.OFFLOAD_MODE_ENABLED_GAPLESS_NOT_REQUIRED
                } else {
                    DefaultAudioSink.OFFLOAD_MODE_DISABLED
                }
            )
        }

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

    fun setHeadroomSafetyRatio(value: Double) {
        eqProcessor.setHeadroomSafetyRatio(value)
    }

    fun setVolume(volume: Float) {
        userVolume = volume.coerceIn(0f, 1f)

        if (crossfadeTargetMediaId == null) {
            player.volume = userVolume
        }
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        crossfadeEnabled = enabled
        if (!enabled) stopCrossfadePreview(restoreVolume = true)
    }

    fun setCrossfadeSeconds(seconds: Float) {
        crossfadeSeconds = seconds.coerceIn(1f, 8f)
    }

    private fun startCrossfadePreview(nextItem: MediaItem) {
        val cf = crossfadePlayerRef ?: ExoPlayer.Builder(context.applicationContext)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                 false
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
