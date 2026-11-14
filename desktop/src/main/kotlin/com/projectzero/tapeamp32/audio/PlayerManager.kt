package com.projectzero.tapeamp32.audio

import com.projectzero.tapeamp32.data.EqPreset
import com.projectzero.tapeamp32.data.Song
import com.projectzero.tapeamp32.desktop.audio.DesktopPlayer
import com.projectzero.tapeamp32.desktop.shim.Context
import com.projectzero.tapeamp32.desktop.shim.toSourceString
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PlayerManager VERSI DESKTOP. Nama kelas, package, dan API publiknya SAMA dengan
 * app/.../audio/PlayerManager.kt (Android, berbasis ExoPlayer) -- file Android itu tidak
 * dikompilasi di desktop (lihat `androidOnly` di build.gradle.kts), sehingga PlayerViewModel
 * dan semua layar bisa dipakai TANPA diubah.
 *
 * Perbedaan perilaku yang disengaja:
 * - Antrean disimpan di sini, tapi lagu berikutnya TIDAK maju otomatis: saat sebuah lagu
 *   selesai, [onSongEnded] SELALU dipanggil dan PlayerViewModel.handleTrackEnded() yang
 *   memutuskan (repeat ONE/ALL/OFF, sleep timer akhir-lagu) lalu memanggil [seekToQueueItem].
 *   (Di Android sebagian kasus ditangani native oleh ExoPlayer; hasil akhirnya sama.)
 * - Crossfade, tape-scrub speed, offload/bit-perfect hardware: belum ada (fungsinya no-op
 *   atau hanya menyimpan nilai). Bit-perfect hanya mengaktifkan flag DSP yang sama.
 */
class PlayerManager(@Suppress("unused") private val context: Context) {

    companion object {
        @Volatile private var instance: PlayerManager? = null

        fun getInstance(context: Context): PlayerManager =
            instance ?: synchronized(this) {
                instance ?: PlayerManager(context.applicationContext).also { instance = it }
            }
    }

    private val engine = DesktopPlayer()

    val eqProcessor: ParametricEqAudioProcessor get() = engine.eq

    val usbDacObserver = UsbDacObserver(context)

    // ---------------------------------------------------------------- state yang di-forward dari mesin

    val vuLevels: StateFlow<Pair<Float, Float>> = engine.vuLevels
    val dspEngineOn: StateFlow<Boolean> = engine.dspEngineOn
    val peakActive: StateFlow<Boolean> = engine.peakActive
    val isHiRes: StateFlow<Boolean> = engine.isHiRes
    val sampleRate: StateFlow<Int> = engine.sampleRate
    val bitDepth: StateFlow<Int> = engine.bitDepth
    val streamBitrateKbps: StateFlow<Int> = engine.bitrateKbps
    val isPlaying: StateFlow<Boolean> = engine.isPlaying
    val positionMs: StateFlow<Long> = engine.positionMs
    val durationMs: StateFlow<Long> = engine.durationMs
    val lastError: StateFlow<String?> = engine.lastError

    private val _offloadActive = MutableStateFlow(false)
    val offloadActive: StateFlow<Boolean> = _offloadActive.asStateFlow()

    // ---------------------------------------------------------------- shuffle (hanya relay ke VM)

    private val _shuffleOn = MutableStateFlow(false)
    val shuffleOn: StateFlow<Boolean> = _shuffleOn.asStateFlow()

    fun setShuffleOnState(value: Boolean) {
        _shuffleOn.value = value
    }

    private val _shuffleToggleRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val shuffleToggleRequests: SharedFlow<Unit> = _shuffleToggleRequests

    fun requestToggleShuffle() {
        _shuffleToggleRequests.tryEmit(Unit)
    }

    // ---------------------------------------------------------------- lagu & antrean

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _currentStreamTitle = MutableStateFlow<String?>(null)
    val currentStreamTitle: StateFlow<String?> = _currentStreamTitle.asStateFlow()

    private val _currentQueueIndex = MutableStateFlow(0)
    val currentQueueIndex: StateFlow<Int> = _currentQueueIndex.asStateFlow()

    /** Dipanggil (thread audio) saat lagu selesai SENDIRI; bukan karena stop() manual. */
    @Volatile var onSongEnded: (() -> Unit)? = null

    private var queue: List<Song> = emptyList()
    private var songsById: Map<Long, Song> = emptyMap()
    @Volatile private var repeatMode: Int = 0

    init {
        engine.onTrackEnded = { onSongEnded?.invoke() }
    }

    fun clearError() = engine.clearError()

    fun stopAndRelease() {
        engine.stop()
    }

    private fun sourceOf(song: Song): String =
        song.path.takeIf { it.isNotBlank() && java.io.File(it).exists() } ?: song.uri.toSourceString()

    private fun load(song: Song, startPositionMs: Long, playWhenReady: Boolean) {
        _currentSong.value = song
        _currentStreamTitle.value = null
        engine.play(
            source = sourceOf(song),
            startPositionMs = startPositionMs,
            startPaused = !playWhenReady,
            knownDurationMs = song.durationMs
        )
    }

    fun restoreSong(song: Song, positionMs: Long) {
        load(song, positionMs.coerceAtLeast(0L), playWhenReady = false)
    }

    fun playSong(song: Song) {
        load(song, 0L, playWhenReady = true)
    }

    fun setQueue(songs: List<Song>, startIndex: Int, startPositionMs: Long = 0L, playWhenReady: Boolean = true) {
        if (songs.isEmpty()) return
        queue = songs
        songsById = songs.associateBy { it.id }
        val safeIndex = startIndex.coerceIn(0, songs.size - 1)
        _currentQueueIndex.value = safeIndex
        load(songs[safeIndex], startPositionMs.coerceAtLeast(0L), playWhenReady)
    }

    /** Ubah URUTAN antrean (jumlah sama) tanpa menyentuh lagu yang sedang diputar. */
    fun reorderQueue(newSongs: List<Song>) {
        if (newSongs.isEmpty()) return
        val currentId = _currentSong.value?.id
        queue = newSongs
        songsById = newSongs.associateBy { it.id }
        val idx = if (currentId != null) newSongs.indexOfFirst { it.id == currentId } else -1
        if (idx >= 0) _currentQueueIndex.value = idx
    }

    fun queueSongs(): List<Song> = queue

    fun registerQueueSongs(songs: List<Song>) {
        songsById = songsById + songs.associateBy { it.id }
    }

    fun seekToQueueItem(index: Int) {
        if (index !in queue.indices) return
        _currentQueueIndex.value = index
        load(queue[index], 0L, playWhenReady = true)
    }

    /** Menyimpan saja; pergantian lagu mengikuti keputusan VM lewat [onSongEnded]. */
    fun setRepeatMode(mode: Int) {
        repeatMode = mode
    }

    fun playStreamUrl(url: String, title: String = "Live Stream") {
        _currentSong.value = null
        _currentStreamTitle.value = title
        engine.play(source = url, startPositionMs = 0L, startPaused = false, knownDurationMs = 0L)
    }

    // ---------------------------------------------------------------- transport

    fun playPause() {
        if (!engine.hasSession) {
            // Idle / sudah selesai (setara STATE_IDLE/ENDED di Android): mulai ulang lagu sekarang.
            _currentSong.value?.let { load(it, 0L, playWhenReady = true) }
            return
        }
        engine.playPause()
    }

    fun pause() {
        if (engine.isPlaying.value) engine.pause()
    }

    fun stop() {
        engine.stop()
    }

    fun seekTo(ms: Long) {
        if (engine.hasSession) engine.seekTo(ms)
    }

    /** Seek relatif berdasar fraksi durasi -- dipakai roda kaset desktop. */
    fun seekByFraction(delta: Float) = engine.seekByFraction(delta)

    // Efek kaset FF/RW (suara scrub): belum ada di desktop.
    fun setTapeScrubSpeed(speedMultiplier: Float) {}
    fun resetTapeScrubSpeed() {}

    // ---------------------------------------------------------------- DSP (identik dengan Android)

    fun setEqPreset(preset: EqPreset) {
        eqProcessor.setPreset(preset)
    }

    fun setEqBypass(bypass: Boolean) {
        eqProcessor.bypass = bypass
    }

    fun setBitPerfectMode(enabled: Boolean) {
        eqProcessor.bitPerfectMode = enabled
        _offloadActive.value = false // offload hardware tidak ada di desktop
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
        engine.volume = volume.coerceIn(0f, 1f)
    }

    // Crossfade belum diimplementasikan di desktop (butuh dua sesi decode paralel).
    fun setCrossfadeEnabled(enabled: Boolean) {}
    fun setCrossfadeSeconds(seconds: Float) {}

    /** Posisi di-update langsung oleh mesin; tidak perlu polling seperti ExoPlayer. */
    fun pollPosition() {}

    fun release() {
        usbDacObserver.unregister()
        engine.release()
    }
}
