package com.projectzero.tapeamp32.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/* ================================================================
 * BARU (fitur "Jelajahi Radio"): layer network buat Radio Browser API
 * (https://api.radio-browser.info) -- database stasiun radio gratis,
 * community-maintained, tanpa perlu API key. Dipakai OkHttp manual +
 * org.json (BUKAN Retrofit) supaya konsisten dengan gaya project ini:
 * MusicRepository.kt juga pakai org.json manual untuk parsing, dan
 * OkHttp sudah jadi dependency lewat fitur streaming radio custom
 * (lihat PlayerManager.streamOkHttpClient) -- tidak perlu nambah
 * library baru.
 * ================================================================ */

// Model 1 hasil pencarian stasiun dari Radio Browser API. Field diambil dari
// subset response JSON API-nya yang relevan buat app ini -- API aslinya
// punya puluhan field, yang tidak dipakai sengaja tidak dimasukkan di sini
// (favicon boleh string kosong -- banyak stasiun tidak punya logo terdaftar,
// UI di StreamingBrowseTab.kt yang tangani fallback-nya).
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

        // Radio Browser API dilayani lewat beberapa server mirror (bukan satu
        // domain tetap) -- daftar mirror resmi bisa berubah dari waktu ke
        // waktu (lihat dokumentasi resminya soal DNS SRV record kalau mau
        // resolve otomatis). Untuk kesederhanaan, di sini dipakai daftar
        // mirror yang dikenal stabil, dicoba berurutan (fallback manual) kalau
        // salah satu gagal/timeout -- daripada bergantung ke satu domain saja
        // yang kalau down bikin seluruh fitur Jelajahi mati total.
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

    // BARU: User-Agent WAJIB diisi (bukan default OkHttp) -- Radio Browser API
    // menolak/rate-limit request tanpa User-Agent yang jelas, sesuai etika
    // pemakaian API publik gratis yang tercantum di dokumentasi resminya.
    private fun buildRequest(url: String): Request =
        Request.Builder()
            .url(url)
            .header("User-Agent", "TapeAmp32/1.0 (Android)")
            .build()

    // Coba tiap mirror berurutan sampai ada yang berhasil (bukan cuma andalkan
    // mirror pertama) -- mengembalikan null kalau SEMUA mirror gagal, supaya
    // caller (ViewModel) bisa tampilkan pesan error yang jelas ke user alih-alih
    // exception mentah.
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

    // BARU: dipakai sebagai isi default tab Jelajahi SEBELUM user mengetik apa
    // pun -- lihat diskusi UX-nya: bukan semua orang tahu nama stasiun radio,
    // jadi begitu tab ini dibuka, langsung tampilkan stasiun POPULER (sort by
    // clickcount) di negara [countryCode], supaya tetap ada yang bisa
    // di-scroll/tap tanpa perlu ketik apa pun dulu.
    suspend fun getPopularStations(countryCode: String = "ID", limit: Int = 40): List<RadioBrowserStation> {
        val path = "/json/stations/bycountrycodeexact/$countryCode" +
            "?order=clickcount&reverse=true&limit=$limit&hidebroken=true"
        return parseStations(fetchFromAnyMirror(path))
    }

    // BARU (filter negara): dipanggil saat user pilih chip negara tanpa ada
    // kata kunci pencarian -- beda dari getPopularStations() yang defaultnya
    // selalu "ID", ini dipanggil eksplisit dengan kode negara APA PUN yang
    // dipilih user dari chip.
    suspend fun getStationsByCountry(countryCode: String, limit: Int = 40): List<RadioBrowserStation> {
        val path = "/json/stations/bycountrycodeexact/$countryCode" +
            "?order=clickcount&reverse=true&limit=$limit&hidebroken=true"
        return parseStations(fetchFromAnyMirror(path))
    }

    // BARU (filter negara): [countryCode] null/blank berarti cari di SEMUA
    // negara (perilaku lama, tidak berubah). Kalau diisi, hasil pencarian nama
    // ikut disaring ke negara itu saja -- Radio Browser API mendukung kombinasi
    // parameter `name` + `countrycode` sekaligus di endpoint /search yang sama.
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

    // BARU (filter negara): sama seperti searchStations(), [countryCode]
    // opsional -- null/blank berarti tag ini dicari di semua negara.
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
