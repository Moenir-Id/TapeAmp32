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

/**
 * BARU (v2.4, "Waveform Seekbar"): ekstrak amplitude riil dari file audio,
 * dipakai [com.projectzero.tapeamp32.ui.components.WaveformSeekBar] untuk gambar
 * bar waveform presisi -- BUKAN bar acak/dekoratif, tapi benar-benar
 * hasil decode PCM dari lagu yang bersangkutan.
 *
 * Cara kerja singkat: decode seluruh track audio lewat MediaExtractor +
 * MediaCodec (jalur decode standar Android, bukan lewat ExoPlayer supaya
 * tidak mengganggu player yang sedang aktif), lalu bagi timeline lagu jadi
 * [bucketCount] bucket dan simpan PEAK amplitude (nilai absolut sample
 * tertinggi) per bucket. Hasil akhir dinormalisasi 0f..1f terhadap bucket
 * tertinggi supaya waveform selalu "mengisi" tinggi seekbar, sama seperti
 * gaya modern.
 *
 * (patch "cache instan"): WaveformCache di bawah sekarang dua lapis --
 * memori (instan, LRU) DAN disk (persist di cacheDir, bertahan lintas buka-tutup
 * app), jadi lagu yang sama tidak perlu di-decode ulang lagi begitu pernah
 * dibuka sekali, termasuk setelah app di-restart total.
 *
 * Catatan sisa:
 * - Untuk lagu panjang/bitrate tinggi yang BELUM pernah di-cache, decode-nya
 *   tetap beberapa ratus ms sampai ~1-2 detik sekali saja, makanya dipanggil
 *   dari coroutine IO dan UI menampilkan waveform datar dulu selagi loading
 *   (lihat WaveformSeekBar).
 * - Asumsi output decoder PCM 16-bit (ENCODING_PCM_16BIT), yang merupakan
 *   default MediaCodec audio decoder bawaan Android -- sesuai juga dengan
 *   asumsi pipeline DSP lain di app ini (ParametricEqAudioProcessor dkk).
 * - Kalau format/track tidak didukung atau proses decode gagal di tengah
 *   jalan, fungsi ini melempar exception; PEMANGGIL (WaveformExtractor.safeExtract
 *   atau collector di PlayerViewModel) yang bertanggung jawab menangkapnya
 *   dan fallback ke null (WaveformSeekBar otomatis balik ke garis progres
 *   polos kalau waveform null, tidak crash).
 */
object WaveformExtractor {

    private const val TIMEOUT_US = 10_000L

    /**
     * Versi aman: tidak pernah melempar exception, mengembalikan null kalau
     * decode gagal (format tidak didukung, file tidak terbaca, dll).
     */
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

            // Estimasi total sample FRAME (per channel) sepanjang lagu, dipakai untuk
            // memetakan posisi decode saat ini ke index bucket 0..bucketCount-1. Ini
            // perkiraan (bisa meleset sedikit dari padding encoder), makanya index
            // bucket di-clamp di bawah -- tidak perlu presisi sample-exact.
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

                            // Ambil amplitude tertinggi ANTAR channel per frame (mis.
                            // stereo: max(|L|,|R|)) supaya waveform tetap merepresentasikan
                            // channel yang lebih "keras" pada momen itu.
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
                        // Sudah tidak ada input baru & belum ada output baru -- kemungkinan
                        // decoder sudah benar benar habis walau EOS flag belum kebaca,
                        // hindari infinite loop.
                        break
                    }
                }
            }

            // Normalisasi akhir terhadap bucket tertinggi supaya waveform selalu
            // "mengisi" tinggi kontainer UI (gaya modern), bukan cuma 0..1 mentah
            // relatif terhadap Short.MAX_VALUE (lagu yang di-mastering pelan akan
            // terlihat rata/flat kalau tidak dinormalisasi ulang di sini).
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

/**
 * Cache DUA LAPIS, kunci = Song.id:
 * 1. Memori (LinkedHashMap access-order = LRU sederhana, maks [MAX_MEM_ENTRIES])
 *    -- paling cepat, tapi cuma hidup selama proses app berjalan.
 * 2. Disk (satu file kecil biner per lagu di cacheDir/waveform_cache/) -- bertahan
 *    lintas buka-tutup app, jadi lagu yang sudah pernah diputar SEBELUM app
 *    ditutup total pun tidak perlu decode ulang lagi pas dibuka lagi ("sekali
 *    buka library cepat"). Baca file kecil ini (cuma ~180 float =
 *    720 byte per lagu) masih JAUH lebih cepat daripada decode MediaCodec ulang.
 *
 * File cache HANYA di cacheDir (bukan file storage biasa) supaya otomatis boleh
 * dibersihkan OS kalau perangkat kehabisan ruang, dan otomatis ikut hilang kalau
 * app di-uninstall -- tidak menyampah storage pengguna.
 */
object WaveformCache {

    private const val MAX_MEM_ENTRIES = 40
    private const val CACHE_DIR_NAME = "waveform_cache"

    // LinkedHashMap access-order = otomatis jadi LRU sederhana; entry paling
    // lama tidak dipakai kebuang duluan begitu MAX_MEM_ENTRIES terlampaui.
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

    /**
     * Cek memori dulu (instan). Kalau cache proses ini kosong (mis. app baru
     * saja di-restart), coba baca dari disk -- masih instan (baca file <1KB),
     * cuma sedikit lebih lambat dari cache memori murni. Baru null kalau
     * memang belum pernah di-cache sama sekali (lagu ini belum pernah dibuka).
     */
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
            // File cache korup/setengah tertulis (mis. app di-kill di tengah
            // penulisan) -- anggap saja cache miss, WaveformExtractor akan
            // decode ulang & menimpa file ini lewat put() di bawah.
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
            // Gagal tulis ke disk (mis. storage penuh) bukan fatal -- waveform
            // tetap tampil dari cache memori untuk sesi ini, cuma tidak persist.
        }
    }

    @Synchronized
    private fun memCacheGet(songId: Long): FloatArray? = memCache[songId]

    @Synchronized
    private fun memCachePut(songId: Long, data: FloatArray) {
        memCache[songId] = data
    }
}
