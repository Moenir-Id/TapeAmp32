package com.projectzero.tapeamp32.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.projectzero.tapeamp32.R
import com.projectzero.tapeamp32.data.RadioStation
import com.projectzero.tapeamp32.data.SampleFavoriteStations
import com.projectzero.tapeamp32.data.SettingsKeys
import com.projectzero.tapeamp32.data.deserializeRadioStations
import com.projectzero.tapeamp32.data.serializeRadioStations
import com.projectzero.tapeamp32.ui.theme.*
import com.projectzero.tapeamp32.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/* ================================================================
 * HELPER: nama stasiun dari URL custom
 * ================================================================ */

// FIX (patch "nama stasiun tidak jelas"): sebelumnya nama diambil mentah-mentah
// dari `url.substringAfterLast('/')`, termasuk QUERY STRING dan token acak
// (misal ".../live.mp3?auth=xxx&sid=1" -> "live.mp3?auth=xxx&sid=1", atau
// ".../;stream" dari server Shoutcast lama -> ";stream", atau ".../8f2a91cd"
// dari layanan seperti Zeno -> "8f2a91cd") -- semuanya tidak menjelaskan
// stasiun radio yang mana. Sekarang query/fragment dibuang dulu, lalu kalau
// sisa nama-nya masih generik/acak, pakai HOST-nya (lebih jelas menunjukkan
// sumber stream-nya) sebelum jatuh ke label default.
internal fun deriveStationNameFromUrl(url: String, fallback: String): String {

    val uri = runCatching { java.net.URI(url) }.getOrNull()

    val pathName = (uri?.path ?: url.substringBefore('?').substringBefore('#'))
        .trimEnd('/')
        .substringAfterLast('/')
        .trim(';', ' ')

    val looksGeneric =
        pathName.isBlank() ||
            pathName.length <= 2 ||
            pathName.matches(Regex("^[0-9a-fA-F]{6,}$")) ||
            pathName.lowercase() in setOf(
                "stream", "live", "listen", "radio", "audio", "play", ";stream"
            )

    val host = uri?.host

    return when {
        !looksGeneric -> pathName
        !host.isNullOrBlank() -> host
        else -> fallback
    }
}

/* ================================================================
 * STREAMING SCREEN
 * ================================================================ */

@Composable
fun StreamingScreen(
    vm: PlayerViewModel
) {

    var selected by remember {
        mutableStateOf(
            SampleFavoriteStations.firstOrNull()
        )
    }

    // FIX (patch "streaming persist"): sebelumnya cuma `remember` -- daftar stasiun
    // custom hilang lagi begitu proses app mati (bukan cuma pindah layar), sehingga
    // stream yang sudah ditambahkan terasa "tidak bisa disimpan". Sekarang dipulihkan
    // dari SettingsRepository di bawah & ditulis ulang setiap kali ada stasiun baru.
    var customStations by remember {
        mutableStateOf(listOf<RadioStation>())
    }

    val scope = rememberCoroutineScope()

    // Muat stasiun custom yang sudah pernah disimpan, sekali saat layar ini pertama
    // kali masuk komposisi.
    LaunchedEffect(Unit) {
        val savedJson = vm.settingsRepository.customStationsJson.first()
        val restored = deserializeRadioStations(savedJson)
        customStations = restored
        if (selected == null) {
            selected = restored.firstOrNull()
        }
    }

    val allStations by remember(customStations) {
        mutableStateOf(SampleFavoriteStations + customStations)
    }

    // FIX (patch "next/prev tidak jalan di mode stream"): daftar gabungan ini sebelumnya
    // cuma hidup di sini (state lokal Composable) -- didaftarkan ke ViewModel supaya
    // tombol next/prev di tape deck (PlayerScreen) juga bisa tahu & pindah-pindah antar
    // stream URL tersimpan ini, bukan cuma antar lagu Library.
    LaunchedEffect(allStations) {
        vm.setStreamStations(allStations)
    }

    // Kalau stasiun aktif berpindah dari LUAR layar ini (mis. lewat tombol next/prev di
    // tape deck), sinkronkan highlight "selected" di sini juga supaya tetap sesuai.
    val currentStationId by vm.currentStationId.collectAsStateWithLifecycle()
    LaunchedEffect(currentStationId, allStations) {
        val matched = allStations.find { it.id == currentStationId }
        if (matched != null) selected = matched
    }

    val isPlaying by
        vm.isPlaying.collectAsStateWithLifecycle()

    val currentSong by
        vm.currentSong.collectAsStateWithLifecycle()

    // FIX (patch "spektrum nyala terus padahal stream off"): vm.isPlaying itu
    // status GLOBAL player (lagu Library ATAU stream, mana pun yang sedang
    // aktif) -- kalau user sedang muter lagu dari Library lalu pindah ke tab
    // Streaming ini, isPlaying tetap TRUE walau tidak ada stream radio yang
    // benar-benar berjalan, sehingga spektrum & badge LIVE ikut menyala terus.
    // PlayerManager set currentSong = null setiap kali yang diputar adalah
    // stream (bukan lagu Library), jadi itu dipakai sebagai syarat tambahan
    // supaya spektrum & badge LIVE cuma nyala kalau STREAM-nya yang aktif.
    val isStreamPlaying = isPlaying && currentSong == null

    // FIX (bug "bitrate selalu 0"): sebelumnya panel Stream Information di bawah
    // menampilkan station?.bitrateKbps, field statis yang di-hardcode ke 0 begitu
    // stasiun ditambahkan (lihat onAddCustomUrl) dan tidak pernah diisi ulang --
    // makanya SEMUA stasiun (tidak ada preset bawaan, semuanya custom) selalu
    // tampil "0 kbps". Sekarang dipakai bitrate SUNGGUHAN yang dibaca live dari
    // stream yang sedang didecode (icy-br server radio / container), lihat
    // PlayerManager.streamBitrateKbps.
    val liveStreamBitrateKbps by
        vm.streamBitrateKbps.collectAsStateWithLifecycle()

    val bufferSeconds by
        vm.settingsRepository
            .streamBufferSeconds
            .collectAsStateWithLifecycle(
                initialValue = 3.2f
            )

    // BARU (fitur "Jelajahi Radio")
    val browseQuery by vm.browseQuery.collectAsStateWithLifecycle()
    val browseResults by vm.browseResults.collectAsStateWithLifecycle()
    val browseLoading by vm.browseLoading.collectAsStateWithLifecycle()
    val browseError by vm.browseError.collectAsStateWithLifecycle()
    val browseCountry by vm.browseCountry.collectAsStateWithLifecycle()

    // Diresolve di sini (bukan di dalam lambda onAddCustomUrl di bawah) karena
    // stringResource() cuma bisa dipanggil dari konteks @Composable, sedangkan
    // onAddCustomUrl adalah lambda biasa (String) -> Unit.
    val customStreamDefaultLabel =
        stringResource(R.string.streaming_custom_stream_default)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF090B0B),
                        BgBlack,
                        Color(0xFF0B0D0D)
                    )
                )
            )
            .padding(10.dp)
    ) {

        /*
         * ============================================================
         * LEFT : FAVORITES
         * ============================================================
         */

        StreamingFavoritesPanel(
            stations = allStations,
            selected = selected,
            onSelect = { station ->

                selected = station

                vm.playStreamStation(station)
            },
            onAddCustomUrl = { url ->

                val custom = RadioStation(
                    id = "custom_${System.currentTimeMillis()}",
                    name = deriveStationNameFromUrl(url, customStreamDefaultLabel),
                    streamUrl = url,
                    bitrateKbps = 0,
                    codec = "custom"
                )

                val updatedStations = customStations + custom
                customStations = updatedStations
                selected = custom

                vm.playStreamStation(custom)

                // FIX (patch "streaming persist"): tulis ke DataStore supaya stasiun
                // ini tetap ada lain kali app dibuka, bukan cuma hidup di state layar.
                scope.launch {
                    vm.settingsRepository.setString(
                        SettingsKeys.CUSTOM_STATIONS_JSON,
                        serializeRadioStations(updatedStations)
                    )
                }
            },
            // FIX (patch "hapus stream tersimpan"): sebelumnya stasiun custom yang
            // sudah disimpan tidak bisa dihapus lagi dari sini -- daftar cuma bisa
            // bertambah terus. Sekarang setiap item custom punya tombol hapus yang
            // membuang stasiunnya dari state DAN dari DataStore (supaya tidak
            // muncul lagi lain kali app dibuka).
            onDeleteCustomUrl = { station ->

                val updatedStations =
                    customStations.filterNot { it.id == station.id }

                customStations = updatedStations

                if (selected?.id == station.id) {
                    selected = updatedStations.firstOrNull()
                }

                scope.launch {
                    vm.settingsRepository.setString(
                        SettingsKeys.CUSTOM_STATIONS_JSON,
                        serializeRadioStations(updatedStations)
                    )
                }
            },
            // BARU (fitur "edit stream"): stasiun custom sekarang bisa diganti
            // nama DAN URL stream-nya dari sini (sebelumnya cuma nama). Perubahan
            // disimpan ke state DAN DataStore, sama seperti alur tambah/hapus di
            // atas, supaya perubahan tetap ada lain kali app dibuka. Kalau stasiun
            // yang diedit sedang aktif diputar, `selected` juga ikut diperbarui
            // supaya panel player di kanan langsung menampilkan data baru tanpa
            // perlu pindah pilihan -- dan kalau URL-nya berubah, stream disambung
            // ulang ke URL baru supaya tidak tetap memutar alamat lama.
            onEditCustomStation = { station, newName, newUrl ->

                val trimmedName = newName.trim().ifEmpty { station.name }
                val trimmedUrl = newUrl.trim().ifEmpty { station.streamUrl }

                if (trimmedName != station.name || trimmedUrl != station.streamUrl) {

                    val updatedStation =
                        station.copy(name = trimmedName, streamUrl = trimmedUrl)

                    val updatedStations =
                        customStations.map {
                            if (it.id == station.id)
                                updatedStation
                            else
                                it
                        }

                    customStations = updatedStations

                    if (selected?.id == station.id) {
                        selected = updatedStation

                        if (trimmedUrl != station.streamUrl) {
                            vm.playStreamStation(updatedStation)
                        }
                    }

                    scope.launch {
                        vm.settingsRepository.setString(
                            SettingsKeys.CUSTOM_STATIONS_JSON,
                            serializeRadioStations(updatedStations)
                        )
                    }
                }
            },
            modifier = Modifier
                .width(205.dp)
                .fillMaxHeight(),
            // BARU (fitur "Jelajahi Radio")
            browseQuery = browseQuery,
            browseResults = browseResults,
            browseLoading = browseLoading,
            browseError = browseError,
            browseCountry = browseCountry,
            onBrowseQueryChange = { vm.updateBrowseQuery(it) },
            onBrowseSearch = { vm.searchBrowseStations(it) },
            onBrowseTagSelect = { vm.filterBrowseByTag(it) },
            onBrowseCountrySelect = { vm.filterBrowseByCountry(it) },
            onBrowseLoadInitial = { vm.loadPopularStationsIfEmpty() },
            onPlayFoundStation = { found ->
                // FIX (bug "panel detail selalu Prambors"): set `selected`
                // langsung dari nilai balik playFoundStation(), bukan
                // menunggu LaunchedEffect(currentStationId, allStations) yang
                // cuma cocok kalau stasiunnya ada di Favorit -- lihat catatan
                // panjang di PlayerViewModel.playFoundStation().
                selected = vm.playFoundStation(found)
            },
            // BARU: "+" di hasil Jelajahi -- pakai jalur persist yang SAMA
            // persis dengan onAddCustomUrl di atas (customStations +
            // CUSTOM_STATIONS_JSON), supaya stasiun hasil pencarian yang
            // disimpan berperilaku identik dengan stasiun yang ditambah
            // manual lewat URL (bisa dihapus/diganti nama dari tab Favorit,
            // tetap ada lain kali app dibuka).
            onAddFoundStationToFavorites = { found ->
                val custom = RadioStation(
                    id = "custom_${System.currentTimeMillis()}",
                    name = found.name,
                    streamUrl = found.streamUrl,
                    bitrateKbps = found.bitrateKbps,
                    codec = "custom"
                )
                val updatedStations = customStations + custom
                customStations = updatedStations
                scope.launch {
                    vm.settingsRepository.setString(
                        SettingsKeys.CUSTOM_STATIONS_JSON,
                        serializeRadioStations(updatedStations)
                    )
                }
            }
        )

        Spacer(
            modifier = Modifier.width(9.dp)
        )

        /*
         * ============================================================
         * RIGHT : STREAM PLAYER
         * ============================================================
         */

        StreamingPlayerPanel(
            station = selected,
            isPlaying = isStreamPlaying,
            bufferSeconds = bufferSeconds,
            isStreamPlaying = isStreamPlaying,
            liveStreamBitrateKbps = liveStreamBitrateKbps,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}

/* ================================================================
 * FAVORITES PANEL
 * ================================================================ */
