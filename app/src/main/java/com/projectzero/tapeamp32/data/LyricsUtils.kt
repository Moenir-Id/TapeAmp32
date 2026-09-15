package com.projectzero.tapeamp32.data

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.MetadataRetriever
import androidx.media3.extractor.metadata.flac.VorbisComment
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/* ================================================================
 * LYRICS UTILS -- PATCH: lirik dari metadata embedded + sinkron
 * ================================================================
 *
 * Sebelumnya lirik cuma dibaca dari file sidecar (.lrc/.txt) di folder
 * yang sama dengan lagu. Banyak file FLAC (dan OGG) sudah menyimpan
 * lirik langsung di metadatanya sendiri lewat Vorbis Comment dengan key
 * "LYRICS" / "UNSYNCEDLYRICS" / "SYNCEDLYRICS" -- tag umum yang dipakai
 * banyak tagger musik (mp3tag, Picard, dll). File ini menambahkan:
 *
 * 1. extractEmbeddedLyrics(): baca tag lirik itu langsung dari file
 *    lewat Media3 (jalan juga untuk lagu content:// dari SAF, bukan
 *    cuma path filesystem biasa).
 * 2. parseLrc(): ubah teks lirik (dari sidecar ATAU dari metadata) jadi
 *    daftar baris + timestamp kalau formatnya LRC standar
 *    ("[mm:ss.xx] teks"), supaya bisa disinkron ke posisi playback.
 */

/** Satu baris lirik dengan waktu mulainya (dalam ms sejak awal lagu). */
data class LyricLine(val timeMs: Long, val text: String)

private val LRC_TIMESTAMP_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

/** Vorbis Comment key yang umum dipakai tagger buat nyimpen lirik di FLAC/OGG. */
private val LYRICS_TAG_KEYS = setOf(
    "LYRICS",
    "UNSYNCEDLYRICS",
    "UNSYNCED LYRICS",
    "SYNCEDLYRICS",
    "SYNCED LYRICS"
)

/**
 * Parse teks jadi daftar baris lirik + timestamp, kalau memang ada
 * timestamp gaya LRC di dalamnya (mis. "[00:12.34]"). Kalau teksnya
 * lirik polos tanpa timestamp sama sekali, return null -- caller
 * tinggal tampilkan sebagai teks biasa (tidak bisa disinkron).
 */
fun parseLrc(raw: String): List<LyricLine>? {
    val lines = mutableListOf<LyricLine>()
    var hasTimestamp = false

    raw.lineSequence().forEach { rawLine ->
        val matches = LRC_TIMESTAMP_REGEX.findAll(rawLine).toList()
        if (matches.isEmpty()) return@forEach

        val text = LRC_TIMESTAMP_REGEX.replace(rawLine, "").trim()
        matches.forEach { match ->
            val minutes = match.groupValues[1].toLongOrNull() ?: return@forEach
            val seconds = match.groupValues[2].toLongOrNull() ?: return@forEach
            val fracRaw = match.groupValues[3]
            val fracMs = when (fracRaw.length) {
                0 -> 0L
                1 -> fracRaw.toLong() * 100L
                2 -> fracRaw.toLong() * 10L
                else -> fracRaw.take(3).toLong()
            }
            hasTimestamp = true
            lines.add(LyricLine(minutes * 60_000L + seconds * 1000L + fracMs, text))
        }
    }

    if (!hasTimestamp) return null
    return lines.sortedBy { it.timeMs }
}

/**
 * Baca lirik yang tersimpan langsung di metadata file audio (Vorbis
 * Comment di FLAC/OGG). Return null kalau tag lirik tidak ada, filenya
 * tidak bisa dibuka, atau formatnya tidak menyimpan Vorbis Comment
 * (mis. MP3 murni -- di luar cakupan patch kecil ini).
 */
@UnstableApi
suspend fun extractEmbeddedLyrics(context: Context, uri: Uri): String? {
    return try {
        val mediaItem = MediaItem.fromUri(uri)
        val future = MetadataRetriever.retrieveMetadata(context, mediaItem)
        suspendCancellableCoroutine { cont ->
            future.addListener(
                {
                    val lyrics = runCatching {
                        val trackGroups = future.get()
                        var found: String? = null
                        outer@ for (i in 0 until trackGroups.length) {
                            val group = trackGroups[i]
                            for (j in 0 until group.length) {
                                val metadata = group.getFormat(j).metadata ?: continue
                                for (k in 0 until metadata.length()) {
                                    val entry = metadata.get(k)
                                    if (entry is VorbisComment && entry.key.uppercase() in LYRICS_TAG_KEYS) {
                                        found = entry.value
                                        break@outer
                                    }
                                }
                            }
                        }
                        found
                    }.getOrNull()

                    if (cont.isActive) cont.resume(lyrics)
                },
                Executor { command -> command.run() }
            )
            cont.invokeOnCancellation { future.cancel(false) }
        }
    } catch (_: Exception) {
        null
    }
}
