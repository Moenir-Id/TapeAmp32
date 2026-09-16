package com.projectzero.tapeamp32.data

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: Uri,
    val path: String = "",
    val format: String = "FLAC",
    val bitDepthOrRate: String = "24-BIT / 96kHz"
)

data class RadioStation(
    val id: String,
    val name: String,
    val streamUrl: String,
    val bitrateKbps: Int,
    val codec: String = "MP3"
)

val SampleFavoriteStations = listOf<RadioStation>()

fun serializeRadioStations(stations: List<RadioStation>): String {
    val arr = JSONArray()
    stations.forEach { station ->
        val obj = JSONObject()
        obj.put("id", station.id)
        obj.put("name", station.name)
        obj.put("streamUrl", station.streamUrl)
        obj.put("bitrateKbps", station.bitrateKbps)
        obj.put("codec", station.codec)
        arr.put(obj)
    }
    return arr.toString()
}

fun deserializeRadioStations(json: String): List<RadioStation> {
    if (json.isBlank()) return emptyList()
    val stations = mutableListOf<RadioStation>()
    val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
    for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        stations.add(
            RadioStation(
                id = obj.optString("id"),
                name = obj.optString("name", "Custom Stream"),
                streamUrl = obj.optString("streamUrl"),
                bitrateKbps = obj.optInt("bitrateKbps", 0),
                codec = obj.optString("codec", "custom")
            )
        )
    }
    return stations
}
