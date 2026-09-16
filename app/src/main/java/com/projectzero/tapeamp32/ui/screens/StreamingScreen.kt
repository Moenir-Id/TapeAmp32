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

@Composable
fun StreamingScreen(
    vm: PlayerViewModel
) {

    var selected by remember {
        mutableStateOf(
            SampleFavoriteStations.firstOrNull()
        )
    }

    var customStations by remember {
        mutableStateOf(listOf<RadioStation>())
    }

    val scope = rememberCoroutineScope()

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

    LaunchedEffect(allStations) {
        vm.setStreamStations(allStations)
    }

    val currentStationId by vm.currentStationId.collectAsStateWithLifecycle()
    LaunchedEffect(currentStationId, allStations) {
        val matched = allStations.find { it.id == currentStationId }
        if (matched != null) selected = matched
    }

    val isPlaying by
        vm.isPlaying.collectAsStateWithLifecycle()

    val currentSong by
        vm.currentSong.collectAsStateWithLifecycle()

    val isStreamPlaying = isPlaying && currentSong == null

    val liveStreamBitrateKbps by
        vm.streamBitrateKbps.collectAsStateWithLifecycle()

    val bufferSeconds by
        vm.settingsRepository
            .streamBufferSeconds
            .collectAsStateWithLifecycle(
                initialValue = 3.2f
            )

    val browseQuery by vm.browseQuery.collectAsStateWithLifecycle()
    val browseResults by vm.browseResults.collectAsStateWithLifecycle()
    val browseLoading by vm.browseLoading.collectAsStateWithLifecycle()
    val browseError by vm.browseError.collectAsStateWithLifecycle()
    val browseCountry by vm.browseCountry.collectAsStateWithLifecycle()

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

                scope.launch {
                    vm.settingsRepository.setString(
                        SettingsKeys.CUSTOM_STATIONS_JSON,
                        serializeRadioStations(updatedStations)
                    )
                }
            },

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

                selected = vm.playFoundStation(found)
            },

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
