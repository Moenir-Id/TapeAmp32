package com.projectzero.tapeamp32.data

import com.projectzero.tapeamp32.desktop.shim.Context
import com.projectzero.tapeamp32.desktop.shim.Uri
import com.projectzero.tapeamp32.desktop.shim.toSourceString
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.max
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber

/**
 * Versi desktop (API sama dengan Android): amplitudo puncak per bucket untuk WaveformSeekBar.
 * Decode lewat FFmpeg, di-downmix ke mono 8 kHz supaya cepat; hasil dinormalisasi 0..1.
 */
object WaveformExtractor {

    private const val ANALYSIS_RATE = 8000

    fun safeExtract(context: Context, uri: Uri, bucketCount: Int = 180): FloatArray? =
        runCatching { extract(context, uri, bucketCount) }.getOrNull()

    @Suppress("UNUSED_PARAMETER")
    fun extract(context: Context, uri: Uri, bucketCount: Int = 180): FloatArray {
        val g = FFmpegFrameGrabber(uri.toSourceString())
        try {
            g.sampleFormat = avutil.AV_SAMPLE_FMT_FLT
            g.sampleMode = FrameGrabber.SampleMode.FLOAT
            g.audioChannels = 1
            g.sampleRate = ANALYSIS_RATE
            g.start()

            val durationUs = g.lengthInTime
            require(durationUs > 0) { "Durasi tidak diketahui" }
            val totalFrames = max(1L, durationUs * ANALYSIS_RATE / 1_000_000L)

            val peaks = FloatArray(bucketCount)
            var frameIndex = 0L
            while (true) {
                val frame = g.grabSamples() ?: break
                val fb = frame.samples?.firstOrNull() as? FloatBuffer ?: continue
                val buf = fb.duplicate()
                while (buf.hasRemaining()) {
                    val v = abs(buf.get())
                    val bucket = (frameIndex * bucketCount / totalFrames).toInt().coerceIn(0, bucketCount - 1)
                    if (v > peaks[bucket]) peaks[bucket] = v
                    frameIndex++
                }
            }

            val top = peaks.max()
            if (top > 0f) for (i in peaks.indices) peaks[i] = peaks[i] / top
            return peaks
        } finally {
            runCatching { g.stop() }
            runCatching { g.release() }
        }
    }
}

/** Cache waveform: memori (LRU 40) + disk (%APPDATA%\TapeAmp32\cache\waveform_cache). */
object WaveformCache {

    private const val MAX_MEM_ENTRIES = 40
    private const val CACHE_DIR_NAME = "waveform_cache"

    private val memCache = object : LinkedHashMap<Long, FloatArray>(MAX_MEM_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, FloatArray>?): Boolean =
            size > MAX_MEM_ENTRIES
    }

    private fun diskFile(context: Context, songId: Long): File {
        val dir = File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
        return File(dir, "$songId.wf")
    }

    fun get(context: Context, songId: Long): FloatArray? {
        synchronized(memCache) { memCache[songId] }?.let { return it }
        val file = diskFile(context, songId)
        if (!file.exists()) return null
        return runCatching {
            val bytes = file.readBytes()
            if (bytes.isEmpty() || bytes.size % 4 != 0) return null
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val data = FloatArray(bytes.size / 4) { buffer.getFloat(it * 4) }
            synchronized(memCache) { memCache[songId] = data }
            data
        }.getOrNull()
    }

    fun put(context: Context, songId: Long, data: FloatArray) {
        synchronized(memCache) { memCache[songId] = data }
        runCatching {
            val buffer = ByteBuffer.allocate(data.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            data.forEach { buffer.putFloat(it) }
            diskFile(context, songId).writeBytes(buffer.array())
        }
    }
}
