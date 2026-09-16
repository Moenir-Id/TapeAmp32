package com.projectzero.tapeamp32.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject

class MusicRepository(private val context: Context) {

    companion object {

        private val KNOWN_AUDIO_EXTENSIONS = listOf(
            ".mp3", ".wav", ".wave", ".flac", ".aac", ".m4a", ".m4b",
            ".ogg", ".oga", ".opus", ".dsf", ".dff", ".wma", ".ape",
            ".aiff", ".aif", ".alac", ".mka", ".amr", ".3gp", ".mid", ".midi"
        )
    }

    fun scanLibrary(): List<Song> {
        val songs = mutableListOf<Song>()
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown Title"
                val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                val album = cursor.getString(albumColumn) ?: "Unknown Album"
                val duration = cursor.getLong(durationColumn)
                val path = cursor.getString(dataColumn) ?: ""

                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                val format = path.substringAfterLast('.', "FLAC").uppercase()

                songs.add(
                    Song(
                        id = id,
                        title = title,
                        artist = if (artist == "<unknown>") "Unknown Artist" else artist,
                        album = if (album == "<unknown>") "Unknown Album" else album,
                        durationMs = duration,
                        uri = contentUri,
                        path = path,
                        format = format
                    )
                )
            }
        }
        return songs
    }

    suspend fun scanSelectedFolder(folderUri: Uri): List<Song> = coroutineScope {
        val fileEntries = mutableListOf<AudioFileEntry>()
        collectAudioFiles(folderUri, folderUri, fileEntries)

        val semaphore = Semaphore(6)
        fileEntries.map { entry ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val metadata = fetchAudioMetadata(entry.fileUri, entry.fileName)
                    val format = entry.fileName.substringAfterLast('.', "FLAC").uppercase()
                    Song(
                        id = entry.docId.hashCode().toLong(),
                        title = metadata.title,
                        artist = metadata.artist,
                        album = metadata.album,
                        durationMs = metadata.durationMs,
                        uri = entry.fileUri,
                        path = entry.fileUri.toString(),
                        format = format
                    )
                }
            }
        }.awaitAll()
    }

    private data class AudioFileEntry(val fileUri: Uri, val fileName: String, val docId: String)

    private fun collectAudioFiles(parentTreeUri: Uri, currentDirUri: Uri, out: MutableList<AudioFileEntry>) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            parentTreeUri,
            DocumentsContract.getTreeDocumentId(currentDirUri)
        )

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            OpenableColumns.DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val docId = cursor.getString(idIndex) ?: continue
                val fileName = cursor.getString(nameIndex) ?: continue
                val mimeType = cursor.getString(mimeIndex) ?: ""

                val fileUri = DocumentsContract.buildDocumentUriUsingTree(parentTreeUri, docId)

                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    collectAudioFiles(parentTreeUri, fileUri, out)
                    continue
                }

                val lowerName = fileName.lowercase()

                val isAudioByMime = mimeType.startsWith("audio/")
                val isAudioByExtension = KNOWN_AUDIO_EXTENSIONS.any { lowerName.endsWith(it) }

                if (isAudioByMime || isAudioByExtension) {
                    out.add(AudioFileEntry(fileUri, fileName, docId))
                }
            }
        }
    }

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
            obj.put("bitDepthOrRate", song.bitDepthOrRate)
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
                    bitDepthOrRate = obj.optString("bitDepthOrRate", "24-BIT / 96kHz")
                )
            )
        }
        return songs
    }

    fun songFromExternalUri(uri: Uri): Song {
        val displayName = queryDisplayName(uri) ?: uri.lastPathSegment ?: "Unknown"
        val meta = fetchAudioMetadata(uri, displayName)
        return Song(
            id = -(uri.toString().hashCode().toLong() and 0x7FFFFFFFL),
            title = meta.title,
            artist = meta.artist,
            album = meta.album,
            durationMs = meta.durationMs,
            uri = uri,
            path = uri.toString(),
            format = displayName.substringAfterLast('.', "").uppercase().ifBlank { "AUDIO" }
        )
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        }.getOrNull()
    }

    private data class ParsedMetadata(
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long
    )

    private fun fetchAudioMetadata(fileUri: Uri, defaultFileName: String): ParsedMetadata {
        val retriever = MediaMetadataRetriever()
        var title = defaultFileName.substringBeforeLast(".")
        var artist = "Unknown Artist"
        var album = "Unknown Album"
        var durationMs = 0L

        try {
            retriever.setDataSource(context, fileUri)

            val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val metaAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val metaDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

            if (!metaTitle.isNullOrBlank()) title = metaTitle
            if (!metaArtist.isNullOrBlank()) artist = metaArtist
            if (!metaAlbum.isNullOrBlank()) album = metaAlbum
            if (!metaDuration.isNullOrEmpty()) durationMs = metaDuration.toLongOrNull() ?: 0L
        } catch (_: Exception) {

        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        return ParsedMetadata(title, artist, album, durationMs)
    }
}
