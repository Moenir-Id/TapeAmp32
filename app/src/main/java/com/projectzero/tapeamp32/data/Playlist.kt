package com.projectzero.tapeamp32.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * BARU (v1.2): model playlist buatan pengguna.
 *
 * Playlist cuma menyimpan REFERENSI ke lagu (lewat [Song.path], yang unik per file),
 * bukan salinan objek Song penuh -- supaya kalau metadata lagu berubah setelah rescan
 * library (judul/artist/album ke-update lewat tag baru), playlist otomatis ikut
 * menunjuk ke versi terbaru tanpa perlu disinkronkan manual satu-satu.
 */
data class Playlist(
    val id: String,
    val name: String,
    val songPaths: List<String> = emptyList()
)

/**
 * Serializer/parser JSON untuk daftar playlist, dipakai untuk menyimpan seluruh
 * playlist ke DataStore (lihat SettingsKeys.PLAYLISTS_JSON) dan memulihkannya lagi
 * saat aplikasi dibuka ulang. Pola & gaya kode sama persis dengan
 * PowerampPresetParser/MusicRepository.serializeSongs supaya konsisten satu app.
 */
object PlaylistUtils {

    fun serializeList(playlists: List<Playlist>): String {
        val arr = JSONArray()
        playlists.forEach { playlist ->
            val obj = JSONObject()
            obj.put("id", playlist.id)
            obj.put("name", playlist.name)
            val pathsArr = JSONArray()
            playlist.songPaths.forEach { pathsArr.put(it) }
            obj.put("songPaths", pathsArr)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun deserializeList(json: String): List<Playlist> {
        if (json.isBlank()) return emptyList()
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        val result = mutableListOf<Playlist>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val pathsArr = obj.optJSONArray("songPaths") ?: JSONArray()
            val paths = mutableListOf<String>()
            for (p in 0 until pathsArr.length()) {
                pathsArr.optString(p)?.let { if (it.isNotBlank()) paths.add(it) }
            }
            result.add(
                Playlist(
                    id = obj.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                    name = obj.optString("name", "Playlist"),
                    songPaths = paths
                )
            )
        }
        return result
    }
}
