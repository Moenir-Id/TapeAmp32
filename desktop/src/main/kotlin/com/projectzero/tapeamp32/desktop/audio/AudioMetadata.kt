package com.projectzero.tapeamp32.desktop.audio

import org.bytedeco.javacv.FFmpegFrameGrabber

/** Pembaca tag/durasi lewat FFmpeg (pengganti MediaMetadataRetriever di Android). */
object AudioMetadata {

    val audioExtensions = setOf(
        "mp3", "flac", "wav", "ogg", "oga", "opus", "m4a", "aac", "wma", "aiff", "aif",
        "ape", "wv", "mka", "mp2", "alac"
    )

    data class Tags(
        val title: String?,
        val artist: String?,
        val album: String?,
        val durationMs: Long,
        /** Seluruh tag mentah (key huruf kecil), dipakai mis. untuk lirik tersemat. */
        val raw: Map<String, String>
    )

    fun read(source: String): Tags = runCatching {
        val g = FFmpegFrameGrabber(source)
        try {
            g.start()
            val raw = HashMap<String, String>()
            (g.metadata ?: emptyMap<String, String>()).forEach { (k, v) -> raw[k.lowercase()] = v }
            // Tag audio bisa juga ada di metadata stream; javacv menggabungkannya ke getAudioMetadata().
            (g.audioMetadata ?: emptyMap<String, String>()).forEach { (k, v) -> raw.putIfAbsent(k.lowercase(), v) }
            Tags(
                title = raw["title"]?.takeIf { it.isNotBlank() },
                artist = (raw["artist"] ?: raw["album_artist"])?.takeIf { it.isNotBlank() },
                album = raw["album"]?.takeIf { it.isNotBlank() },
                durationMs = (g.lengthInTime / 1000L).coerceAtLeast(0L),
                raw = raw
            )
        } finally {
            runCatching { g.stop() }
            runCatching { g.release() }
        }
    }.getOrElse { Tags(null, null, null, 0L, emptyMap()) }
}
