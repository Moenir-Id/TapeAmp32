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

data class LyricLine(val timeMs: Long, val text: String)

private val LRC_TIMESTAMP_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

private val LYRICS_TAG_KEYS = setOf(
    "LYRICS",
    "UNSYNCEDLYRICS",
    "UNSYNCED LYRICS",
    "SYNCEDLYRICS",
    "SYNCED LYRICS"
)

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
