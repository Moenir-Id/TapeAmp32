package com.projectzero.tapeamp32.data

import com.projectzero.tapeamp32.desktop.audio.AudioMetadata
import com.projectzero.tapeamp32.desktop.shim.Context
import com.projectzero.tapeamp32.desktop.shim.Uri
import com.projectzero.tapeamp32.desktop.shim.toFileOrNull
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject

/**
 * MusicRepository VERSI DESKTOP (API sama dengan versi Android yang berbasis MediaStore/SAF).
 * "Folder" di desktop = folder biasa, direpresentasikan sebagai Uri file: (Uri.fromFile).
 * Metadata dibaca lewat FFmpeg; scan folder paralel (6 sekaligus) dan memakai cache lama
 * (id + lastModified sama -> file tidak dibuka lagi), sama seperti versi Android.
 */
class MusicRepository(@Suppress("unused") private val context: Context) {

    /** Android: seluruh library MediaStore. Desktop: isi folder Musik pengguna (kalau ada). */
    fun scanLibrary(): List<Song> {
        val music = File(System.getProperty("user.home"), "Music")
        if (!music.isDirectory) return emptyList()
        return collectAudioFiles(music).map { readSong(it) }.sortedBy { it.title.lowercase() }
    }

    suspend fun scanSelectedFolder(
        folderUri: Uri,
        oldCache: List<Song> = emptyList()
    ): List<Song> = coroutineScope {
        val folder = folderUri.toFileOrNull() ?: return@coroutineScope emptyList()
        if (!folder.isDirectory) return@coroutineScope emptyList()

        val files = collectAudioFiles(folder)
        val cacheById = oldCache.associateBy { it.id }
        val semaphore = Semaphore(6)

        files.map { file ->
            async(Dispatchers.IO) {
                val id = idFor(file)
                val cached = cacheById[id]
                if (cached != null && cached.lastModified == file.lastModified()) {
                    cached
                } else {
                    semaphore.withPermit { readSong(file) }
                }
            }
        }.awaitAll().sortedBy { it.title.lowercase() }
    }

    private fun collectAudioFiles(root: File): List<File> =
        root.walkTopDown()
            .onEnter { !it.name.startsWith(".") }
            .filter { it.isFile && it.extension.lowercase() in AudioMetadata.audioExtensions }
            .toList()

    private fun idFor(file: File): Long = file.absolutePath.hashCode().toLong()

    private fun readSong(file: File): Song {
        val tags = AudioMetadata.read(file.absolutePath)
        return Song(
            id = idFor(file),
            title = tags.title ?: file.nameWithoutExtension,
            artist = tags.artist ?: "Unknown Artist",
            album = tags.album ?: "Unknown Album",
            durationMs = tags.durationMs,
            uri = Uri.fromFile(file),
            path = file.absolutePath,
            format = file.extension.uppercase().ifBlank { "AUDIO" },
            lastModified = file.lastModified()
        )
    }

    fun songFromExternalUri(uri: Uri): Song {
        val file = uri.toFileOrNull()
        if (file == null || !file.isFile) {
            // URL jaringan / sumber tak dikenal: tampilkan apa adanya.
            val name = uri.lastPathSegment ?: "Unknown"
            return Song(
                id = -(uri.toString().hashCode().toLong() and 0x7FFFFFFFL),
                title = name.substringBeforeLast('.'),
                artist = "Unknown Artist",
                album = "Unknown Album",
                durationMs = 0L,
                uri = uri,
                path = uri.toString(),
                format = name.substringAfterLast('.', "").uppercase().ifBlank { "AUDIO" }
            )
        }
        val tags = AudioMetadata.read(file.absolutePath)
        return Song(
            id = -(uri.toString().hashCode().toLong() and 0x7FFFFFFFL),
            title = tags.title ?: file.nameWithoutExtension,
            artist = tags.artist ?: "Unknown Artist",
            album = tags.album ?: "Unknown Album",
            durationMs = tags.durationMs,
            uri = uri,
            path = file.absolutePath,
            format = file.extension.uppercase().ifBlank { "AUDIO" }
        )
    }

    // ---- serialisasi cache library (identik dengan versi Android)

    fun serializeSongs(songs: List<Song>): String {
        val arr = JSONArray()
        songs.forEach { song ->
            val obj = JSONObject()
            obj.put("id", song.id)
            obj.put("title", song.title)
            obj.put("artist", song.artist)
            obj.put("album", song.album)
            obj.put("durationMs", song.durationMs)
            obj.put("uri", song.uri.toString())
            obj.put("path", song.path)
            obj.put("format", song.format)
            obj.put("lastModified", song.lastModified)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun deserializeSongs(json: String): List<Song> {
        if (json.isBlank()) return emptyList()
        val songs = mutableListOf<Song>()
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            songs.add(
                Song(
                    id = obj.optLong("id"),
                    title = obj.optString("title", "Unknown Title"),
                    artist = obj.optString("artist", "Unknown Artist"),
                    album = obj.optString("album", "Unknown Album"),
                    durationMs = obj.optLong("durationMs"),
                    uri = Uri.parse(obj.optString("uri")),
                    path = obj.optString("path", ""),
                    format = obj.optString("format", "FLAC"),
                    lastModified = obj.optLong("lastModified", 0L)
                )
            )
        }
        return songs
    }
}
