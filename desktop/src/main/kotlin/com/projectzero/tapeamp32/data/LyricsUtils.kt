package com.projectzero.tapeamp32.data

import com.projectzero.tapeamp32.desktop.audio.AudioMetadata
import com.projectzero.tapeamp32.desktop.shim.Context
import com.projectzero.tapeamp32.desktop.shim.Uri
import com.projectzero.tapeamp32.desktop.shim.toSourceString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// LyricsUtils VERSI DESKTOP. LyricLine & parseLrc disalin APA ADANYA dari versi Android
// (murni Kotlin); extractEmbeddedLyrics memakai tag FFmpeg, bukan Media3 MetadataRetriever.
// Kalau parseLrc di app Android diubah, ubah juga di sini.

data class LyricLine(val timeMs: Long, val text: String)

private val LRC_TIMESTAMP_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

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

/** Lirik tersemat (tag LYRICS/UNSYNCEDLYRICS/SYNCEDLYRICS, USLT di MP3) lewat FFmpeg. */
@Suppress("UNUSED_PARAMETER")
suspend fun extractEmbeddedLyrics(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        val raw = AudioMetadata.read(uri.toSourceString()).raw
        raw.entries.firstOrNull { (k, v) ->
            v.isNotBlank() && (k == "lyrics" || k.startsWith("lyrics-") || k == "unsyncedlyrics" ||
                k == "unsynced lyrics" || k == "syncedlyrics" || k == "synced lyrics")
        }?.value
    }.getOrNull()
}
