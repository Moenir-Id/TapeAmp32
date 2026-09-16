package com.projectzero.tapeamp32.data

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object WaveformExtractor {

    private const val TIMEOUT_US = 10_000L

    fun safeExtract(context: Context, uri: Uri, bucketCount: Int = 180): FloatArray? {
        return try {
            extract(context, uri, bucketCount)
        } catch (e: Exception) {
            null
        }
    }

    fun extract(context: Context, uri: Uri, bucketCount: Int = 180): FloatArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            extractor.setDataSource(context, uri, null)

            var trackIndex = -1
            var format: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = f
                    break
                }
            }

            if (trackIndex < 0 || format == null) {
                throw IllegalStateException("Tidak ada track audio di $uri")
            }

            extractor.selectTrack(trackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val durationUs =
                if (format.containsKey(MediaFormat.KEY_DURATION))
                    format.getLong(MediaFormat.KEY_DURATION)
                else 0L

            val estTotalFrames =
                max(1L, (durationUs / 1_000_000.0 * sampleRate).toLong())

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val bucketPeak = FloatArray(bucketCount)
            var framesDecoded = 0L
            var sawInputEos = false
            var sawOutputEos = false

            val bufferInfo = MediaCodec.BufferInfo()

            while (!sawOutputEos) {

                if (!sawInputEos) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(
                                inputIndex, 0, sampleSize,
                                extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outputIndex >= 0) {

                    if (bufferInfo.size > 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                        val shortBuffer = outputBuffer.asShortBuffer()
                        val totalSamples = shortBuffer.remaining()
                        val frameCount =
                            if (channelCount > 0) totalSamples / channelCount else 0

                        var frame = 0
                        while (frame < frameCount) {

                            var peakInFrame = 0
                            var ch = 0
                            while (ch < channelCount) {
                                val sample = shortBuffer.get(frame * channelCount + ch)
                                val a = abs(sample.toInt())
                                if (a > peakInFrame) peakInFrame = a
                                ch++
                            }

                            val globalFrame = framesDecoded + frame
                            val bucketIndex =
                                ((globalFrame * bucketCount) / estTotalFrames)
                                    .toInt()
                                    .coerceIn(0, bucketCount - 1)

                            val normalized = peakInFrame / 32768f
                            if (normalized > bucketPeak[bucketIndex]) {
                                bucketPeak[bucketIndex] = normalized
                            }

                            frame++
                        }

                        framesDecoded += frameCount
                    }

                    val isEos =
                        (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (isEos) sawOutputEos = true

                } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (sawInputEos) {

                        break
                    }
                }
            }

            var maxPeak = 0f
            for (v in bucketPeak) maxPeak = max(maxPeak, v)

            if (maxPeak > 0.0001f) {
                for (i in bucketPeak.indices) {
                    bucketPeak[i] = min(1f, bucketPeak[i] / maxPeak)
                }
            }

            return bucketPeak

        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            try {
                codec?.release()
            } catch (_: Exception) {
            }
            extractor.release()
        }
    }
}

object WaveformCache {

    private const val MAX_MEM_ENTRIES = 40
    private const val CACHE_DIR_NAME = "waveform_cache"

    private val memCache = object : LinkedHashMap<Long, FloatArray>(
        MAX_MEM_ENTRIES, 0.75f, true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Long, FloatArray>?
        ): Boolean = size > MAX_MEM_ENTRIES
    }

    private fun diskFile(context: Context, songId: Long): File {
        val dir = File(context.cacheDir, CACHE_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$songId.wf")
    }

    fun get(context: Context, songId: Long): FloatArray? {
        memCacheGet(songId)?.let { return it }

        val file = diskFile(context, songId)
        if (!file.exists()) return null

        return try {
            val bytes = file.readBytes()
            if (bytes.isEmpty() || bytes.size % 4 != 0) return null
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val data = FloatArray(bytes.size / 4) { buffer.getFloat(it * 4) }
            memCachePut(songId, data)
            data
        } catch (_: Exception) {

            null
        }
    }

    fun put(context: Context, songId: Long, data: FloatArray) {
        memCachePut(songId, data)
        try {
            val buffer = ByteBuffer.allocate(data.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            data.forEach { buffer.putFloat(it) }
            diskFile(context, songId).writeBytes(buffer.array())
        } catch (_: Exception) {

        }
    }

    @Synchronized
    private fun memCacheGet(songId: Long): FloatArray? = memCache[songId]

    @Synchronized
    private fun memCachePut(songId: Long, data: FloatArray) {
        memCache[songId] = data
    }
}
