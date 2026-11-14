package com.projectzero.tapeamp32.desktop.audio

import com.projectzero.tapeamp32.audio.ParametricEqAudioProcessor
import com.projectzero.tapeamp32.data.EqPreset
import com.projectzero.tapeamp32.desktop.shim.AudioProcessor
import com.projectzero.tapeamp32.desktop.shim.C
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicLong
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber

/**
 * Mesin audio desktop: FFmpeg (decode) -> ParametricEqAudioProcessor milik app (DSP yang SAMA
 * dengan di Android) -> javax.sound (output, PCM 24-bit kalau didukung, kalau tidak 16-bit).
 *
 * Mesin ini hanya tahu SATU sumber (file/URL) pada satu waktu. Antrean, repeat, dan "lagu
 * berikutnya" ada di PlayerManager (desktop) di atasnya.
 *
 * Satu sumber = satu thread worker ([Session]). Perintah dari UI (pause/seek/stop) dikirim
 * lewat flag @Volatile/Atomic, diproses di loop worker.
 */
class DesktopPlayer {

    /** DSP yang sama dengan di Android. */
    val eq = ParametricEqAudioProcessor()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _vuLevels = MutableStateFlow(0f to 0f)
    val vuLevels: StateFlow<Pair<Float, Float>> = _vuLevels.asStateFlow()

    private val _dspEngineOn = MutableStateFlow(true)
    val dspEngineOn: StateFlow<Boolean> = _dspEngineOn.asStateFlow()

    private val _peakActive = MutableStateFlow(false)
    val peakActive: StateFlow<Boolean> = _peakActive.asStateFlow()

    private val _sampleRate = MutableStateFlow(0)
    val sampleRate: StateFlow<Int> = _sampleRate.asStateFlow()

    private val _bitDepth = MutableStateFlow(16)
    val bitDepth: StateFlow<Int> = _bitDepth.asStateFlow()

    private val _isHiRes = MutableStateFlow(false)
    val isHiRes: StateFlow<Boolean> = _isHiRes.asStateFlow()

    private val _bitrateKbps = MutableStateFlow(0)
    val bitrateKbps: StateFlow<Int> = _bitrateKbps.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /** Dipanggil (di thread audio) saat sumber selesai alami -- bukan saat stop()/ganti sumber. */
    @Volatile var onTrackEnded: (() -> Unit)? = null

    /** 0f..1f, dipakai setelah DSP (tidak mengubah sample DSP / ReplayGain). */
    @Volatile var volume: Float = 1f

    @Volatile private var session: Session? = null

    /** True selama ada sumber yang termuat dan worker-nya masih hidup (belum selesai/di-stop). */
    val hasSession: Boolean get() = session?.isAlive == true

    init {
        // FFmpeg cerewet di stderr; cukup tampilkan error sungguhan.
        runCatching { avutil.av_log_set_level(avutil.AV_LOG_ERROR) }

        eq.vuCallback = { l, r ->
            _vuLevels.value = if (_isPlaying.value) l to r else 0f to 0f
        }
        eq.dspStateCallback = { active, peak ->
            _dspEngineOn.value = active
            _peakActive.value = _isPlaying.value && peak
        }
    }

    // ---------------------------------------------------------------- kontrol publik

    /**
     * Muat [source] (path file lokal atau URL http/https/HLS).
     * [startPaused] = true: siapkan di posisi [startPositionMs] tapi belum bersuara.
     * [knownDurationMs] > 0: durasi dari metadata library, ditampilkan sebelum decoder siap.
     */
    fun play(
        source: String,
        startPositionMs: Long = 0L,
        startPaused: Boolean = false,
        knownDurationMs: Long = 0L
    ) {
        stop()
        _lastError.value = null
        _positionMs.value = startPositionMs.coerceAtLeast(0L)
        _durationMs.value = knownDurationMs.coerceAtLeast(0L)
        _isPlaying.value = !startPaused
        val s = Session(source, startPositionMs, startPaused)
        session = s
        s.start()
    }

    fun playPause() {
        val s = session ?: return
        s.paused = !s.paused
        _isPlaying.value = !s.paused
        if (s.paused) _vuLevels.value = 0f to 0f
    }

    fun pause() {
        val s = session ?: return
        s.paused = true
        _isPlaying.value = false
        _vuLevels.value = 0f to 0f
    }

    fun resume() {
        val s = session ?: return
        s.paused = false
        _isPlaying.value = true
    }

    fun seekTo(ms: Long) {
        val s = session ?: return
        s.seekRequestUs.set(ms.coerceAtLeast(0L) * 1000L)
        _positionMs.value = ms.coerceAtLeast(0L)
    }

    /** Seek relatif berdasarkan fraksi durasi (-1f..1f), dipakai gesture roda kaset (onSeekDelta). */
    fun seekByFraction(delta: Float) {
        val s = session ?: return
        val dur = _durationMs.value
        if (dur <= 0L) return
        // Basis = target seek yang MASIH antre (kalau ada), supaya delta kecil beruntun dari
        // gesture roda menumpuk -- bukan dihitung ulang dari posisi lama yang belum ter-update.
        val pendingUs = s.seekRequestUs.get()
        val baseMs = if (pendingUs >= 0L) pendingUs / 1000L else _positionMs.value
        val target = (baseMs + (delta * dur).toLong()).coerceIn(0L, dur)
        s.seekRequestUs.set(target * 1000L)
        _positionMs.value = target // umpan balik instan di UI
    }

    fun stop() {
        val s = session ?: return
        session = null
        s.stopRequested = true
        s.interrupt()
        runCatching { s.join(2000) }
        _isPlaying.value = false
        _positionMs.value = 0L
        _vuLevels.value = 0f to 0f
        _peakActive.value = false
        _isHiRes.value = false
        _sampleRate.value = 0
        _bitDepth.value = 16
        _bitrateKbps.value = 0
    }

    fun clearError() { _lastError.value = null }

    fun setEqPreset(preset: EqPreset) = eq.setPreset(preset)

    fun release() {
        stop()
        eq.reset()
    }

    // ---------------------------------------------------------------- worker

    private inner class Session(
        private val source: String,
        private val startPositionMs: Long,
        startPaused: Boolean
    ) : Thread("TapeAmp32-audio") {

        @Volatile var paused = startPaused
        @Volatile var stopRequested = false
        val seekRequestUs = AtomicLong(-1L)

        init { isDaemon = true }

        override fun run() {
            var grabber: FFmpegFrameGrabber? = null
            var line: SourceDataLine? = null
            var endedNaturally = false
            try {
                val g = FFmpegFrameGrabber(source)
                grabber = g
                // Float 32-bit interleaved + stereo: format kerja DSP.
                g.sampleFormat = avutil.AV_SAMPLE_FMT_FLT
                g.sampleMode = FrameGrabber.SampleMode.FLOAT
                g.audioChannels = 2
                g.start()

                val sr = g.sampleRate
                require(sr > 0) { "Sample rate tidak terbaca dari: $source" }
                _sampleRate.value = sr
                val durMs = (g.lengthInTime / 1000L).coerceAtLeast(0L)
                if (durMs > 0L) _durationMs.value = durMs
                _bitrateKbps.value = (g.audioBitrate / 1000).coerceAtLeast(0)

                eq.configure(AudioProcessor.AudioFormat(sr, 2, C.ENCODING_PCM_FLOAT))
                eq.flush() // wajib setelah configure(): meng-commit format input ke prosesor

                val (ln, bits) = openLine(sr)
                line = ln
                // Kedalaman bit yang DITAMPILKAN: 24 hanya untuk sumber lossless (FFmpeg tidak
                // memberi tahu bit sumber lewat javacv); sumber lossy tampil 16.
                val shownBits = if (bits >= 24 && isLosslessSource(source)) 24 else 16
                _bitDepth.value = shownBits
                _isHiRes.value = sr > 48_000 || shownBits > 16
                val frameBytes = 2 * (bits / 8)

                var baseMs = 0L
                var writtenFrames = 0L // frame yang sudah ditulis sejak titik seek terakhir

                if (startPositionMs > 0L) {
                    runCatching { g.timestamp = startPositionMs * 1000L }
                    baseMs = startPositionMs
                }

                if (!paused) ln.start()

                var inBuf = ByteBuffer.allocateDirect(8192 * 2 * 4).order(ByteOrder.LITTLE_ENDIAN)
                var outBytes = ByteArray(8192 * frameBytes)

                while (!stopRequested) {
                    // --- seek
                    val seekUs = seekRequestUs.getAndSet(-1L)
                    if (seekUs >= 0L) {
                        runCatching { g.timestamp = seekUs }
                        eq.flush()
                        ln.flush()
                        baseMs = seekUs / 1000L
                        writtenFrames = 0L
                        _positionMs.value = baseMs
                    }

                    // --- pause
                    if (paused) {
                        ln.stop()
                        while (paused && !stopRequested && seekRequestUs.get() < 0L) {
                            try { sleep(20) } catch (e: InterruptedException) { /* cek flag di loop */ }
                        }
                        if (stopRequested) break
                        if (!paused) ln.start()
                        continue
                    }

                    // --- decode satu paket sampel
                    val frame = g.grabSamples()
                    if (frame == null) { endedNaturally = true; break }
                    val samples = frame.samples?.firstOrNull() as? FloatBuffer ?: continue
                    val src = samples.duplicate()
                    val floatCount = src.remaining()
                    if (floatCount <= 0) continue

                    if (inBuf.capacity() < floatCount * 4) {
                        inBuf = ByteBuffer.allocateDirect(floatCount * 4).order(ByteOrder.LITTLE_ENDIAN)
                    }
                    inBuf.clear()
                    inBuf.order(ByteOrder.LITTLE_ENDIAN)
                    inBuf.asFloatBuffer().put(src)
                    inBuf.limit(floatCount * 4)
                    inBuf.position(0)

                    // --- DSP (sama dengan di Android)
                    eq.queueInput(inBuf)
                    val out = eq.getOutput()
                    val outFloats = out.remaining() / 4
                    if (outFloats <= 0) continue
                    out.order(ByteOrder.LITTLE_ENDIAN)

                    val needed = outFloats * (bits / 8)
                    if (outBytes.size < needed) outBytes = ByteArray(needed)
                    val vol = volume
                    var o = 0
                    if (bits == 24) {
                        repeat(outFloats) {
                            val f = (out.float * vol).coerceIn(-1f, 1f)
                            val v = (f * 8388607f).roundToInt()
                            outBytes[o++] = v.toByte()
                            outBytes[o++] = (v shr 8).toByte()
                            outBytes[o++] = (v shr 16).toByte()
                        }
                    } else {
                        repeat(outFloats) {
                            val f = (out.float * vol).coerceIn(-1f, 1f)
                            val v = (f * 32767f).roundToInt()
                            outBytes[o++] = v.toByte()
                            outBytes[o++] = (v shr 8).toByte()
                        }
                    }

                    // write() memblokir saat buffer penuh -> itu yang mengatur tempo pemutaran.
                    ln.write(outBytes, 0, o)
                    writtenFrames += (outFloats / 2).toLong()

                    val queuedFrames = (ln.bufferSize - ln.available()) / frameBytes
                    val playedFrames = (writtenFrames - queuedFrames).coerceAtLeast(0L)
                    _positionMs.value = baseMs + playedFrames * 1000L / sr
                }

                if (endedNaturally && !stopRequested) {
                    ln.drain()
                }
            } catch (t: Throwable) {
                if (!stopRequested) _lastError.value = t.message ?: t.javaClass.simpleName
            } finally {
                runCatching { line?.stop() }
                runCatching { line?.close() }
                runCatching { grabber?.stop() }
                runCatching { grabber?.release() }
                if (session === this) {
                    _isPlaying.value = false
                    _vuLevels.value = 0f to 0f
                }
            }
            if (endedNaturally && !stopRequested) {
                _positionMs.value = _durationMs.value
                onTrackEnded?.invoke()
            }
        }
    }

    private fun isLosslessSource(source: String): Boolean {
        val ext = source.substringAfterLast('.', "").substringBefore('?').lowercase()
        return ext in setOf("flac", "wav", "aiff", "aif", "ape", "wv", "alac", "dsf", "dff")
    }

    /** Buka jalur output: 24-bit kalau mixer mendukung, kalau tidak 16-bit. Return (line, bits). */
    private fun openLine(sampleRate: Int): Pair<SourceDataLine, Int> {
        for (bits in intArrayOf(24, 16)) {
            val fmt = AudioFormat(sampleRate.toFloat(), bits, 2, true, false)
            val info = DataLine.Info(SourceDataLine::class.java, fmt)
            if (AudioSystem.isLineSupported(info)) {
                val line = AudioSystem.getLine(info) as SourceDataLine
                val bufferBytes = (sampleRate * 2 * (bits / 8) * 0.25).toInt() // ~250 ms
                line.open(fmt, bufferBytes)
                return line to bits
            }
        }
        throw IllegalStateException("Perangkat audio tidak mendukung PCM 16/24-bit @ $sampleRate Hz")
    }
}
