package com.projectzero.tapeamp32.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

data class RadioBrowserStation(
    val stationUuid: String,
    val name: String,
    val streamUrl: String,
    val favicon: String,
    val countryCode: String,
    val tags: String,
    val bitrateKbps: Int,
    val votes: Int
)

class RadioBrowserRepository {

    companion object {
        private const val TAG = "RadioBrowserRepository"

        private val MIRRORS = listOf(
            "https://de1.api.radio-browser.info",
            "https://de2.api.radio-browser.info",
            "https://at1.api.radio-browser.info"
        )
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    private fun buildRequest(url: String): Request =
        Request.Builder()
            .url(url)
            .header("User-Agent", "TapeAmp32/1.0 (Android)")
            .build()

    private suspend fun fetchFromAnyMirror(path: String): String? =
        withContext(Dispatchers.IO) {
            for (base in MIRRORS) {
                try {
                    client.newCall(buildRequest(base + path)).execute().use { response ->
                        if (response.isSuccessful) {
                            return@withContext response.body?.string()
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Mirror gagal: $base -- ${e.message}")
                }
            }
            null
        }

    private fun parseStations(json: String?): List<RadioBrowserStation> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val streamUrl = obj.optString("url_resolved").ifBlank { obj.optString("url") }
                if (streamUrl.isBlank()) return@mapNotNull null
                RadioBrowserStation(
                    stationUuid = obj.optString("stationuuid"),
                    name = obj.optString("name").ifBlank { "Unknown Station" },
                    streamUrl = streamUrl,
                    favicon = obj.optString("favicon"),
                    countryCode = obj.optString("countrycode"),
                    tags = obj.optString("tags"),
                    bitrateKbps = obj.optInt("bitrate", 0),
                    votes = obj.optInt("votes", 0)
                )
            }
        }.onFailure {
            Log.w(TAG, "Gagal parse response Radio Browser: ${it.message}")
        }.getOrDefault(emptyList())
    }

    suspend fun getPopularStations(countryCode: String = "ID", limit: Int = 40): List<RadioBrowserStation> {
        val path = "/json/stations/bycountrycodeexact/$countryCode" +
            "?order=clickcount&reverse=true&limit=$limit&hidebroken=true"
        return parseStations(fetchFromAnyMirror(path))
    }

    suspend fun getStationsByCountry(countryCode: String, limit: Int = 40): List<RadioBrowserStation> {
        val path = "/json/stations/bycountrycodeexact/$countryCode" +
            "?order=clickcount&reverse=true&limit=$limit&hidebroken=true"
        return parseStations(fetchFromAnyMirror(path))
    }

    suspend fun searchStations(query: String, countryCode: String? = null, limit: Int = 40): List<RadioBrowserStation> {
        if (query.isBlank()) return emptyList()
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        var path = "/json/stations/search?name=$encoded&limit=$limit&hidebroken=true" +
            "&order=clickcount&reverse=true"
        if (!countryCode.isNullOrBlank()) {
            path += "&countrycode=${java.net.URLEncoder.encode(countryCode, "UTF-8")}"
        }
        return parseStations(fetchFromAnyMirror(path))
    }

    suspend fun getStationsByTag(tag: String, countryCode: String? = null, limit: Int = 40): List<RadioBrowserStation> {
        val encoded = java.net.URLEncoder.encode(tag, "UTF-8")
        var path = "/json/stations/bytag/$encoded?limit=$limit&hidebroken=true" +
            "&order=clickcount&reverse=true"
        if (!countryCode.isNullOrBlank()) {
            path += "&countrycode=${java.net.URLEncoder.encode(countryCode, "UTF-8")}"
        }
        return parseStations(fetchFromAnyMirror(path))
    }
}
