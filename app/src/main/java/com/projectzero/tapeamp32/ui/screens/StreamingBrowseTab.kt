package com.projectzero.tapeamp32.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.projectzero.tapeamp32.data.RadioBrowserStation
import com.projectzero.tapeamp32.ui.theme.*

private val QUICK_GENRE_TAGS = listOf(
    "pop", "rock", "dangdut", "top 40", "jazz", "news", "classical", "chill"
)

private val QUICK_COUNTRIES: List<Pair<String, String?>> = listOf(
    "Semua" to null,
    "Indonesia" to "ID",
    "Malaysia" to "MY",
    "Singapura" to "SG",
    "Jepang" to "JP",
    "Korea" to "KR",
    "Amerika" to "US",
    "Inggris" to "GB",
    "Australia" to "AU"
)

@Composable
internal fun StreamingBrowseTab(
    query: String,
    results: List<RadioBrowserStation>,
    isLoading: Boolean,
    errorMessage: String?,
    selectedCountry: String?,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onTagSelect: (String) -> Unit,
    onCountrySelect: (String?) -> Unit,
    onLoadInitial: () -> Unit,
    onPlayStation: (RadioBrowserStation) -> Unit,
    onAddToFavorites: (RadioBrowserStation) -> Unit,
    modifier: Modifier = Modifier
) {

    LaunchedEffect(Unit) {
        onLoadInitial()
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {

        OutlinedTextField(
            value = query,
            onValueChange = {
                onQueryChange(it)
                onSearch(it)
            },
            placeholder = {
                Text(
                    text = "Cari nama radio...",
                    fontFamily = MonoFont,
                    fontSize = 9.sp,
                    color = TextMuted
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(14.dp)
                )
            },
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = MonoFont,
                fontSize = 10.sp,
                color = TextLight
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Gold,
                unfocusedBorderColor = StrokeGold.copy(alpha = 0.4f),
                cursorColor = GoldBright
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(6.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            items(QUICK_GENRE_TAGS) { tag ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1A1D1D))
                        .border(
                            width = 1.dp,
                            color = StrokeGold.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { onTagSelect(tag) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = tag.uppercase(),
                        color = GoldBright,
                        fontFamily = MonoFont,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            items(QUICK_COUNTRIES) { (label, code) ->
                val isSelected = selectedCountry == code
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) Gold else Color(0xFF1A1D1D))
                        .border(
                            width = 1.dp,
                            color = if (isSelected) Gold else StrokeGold.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { onCountrySelect(code) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = label.uppercase(),
                        color = if (isSelected) Color.Black else GoldBright,
                        fontFamily = MonoFont,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(modifier = Modifier.weight(1f)) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        color = Gold,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(22.dp)
                    )
                }

                errorMessage != null -> {
                    Text(
                        text = errorMessage,
                        color = TextMuted,
                        fontFamily = MonoFont,
                        fontSize = 9.sp,
                        lineHeight = 13.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                results.isEmpty() -> {
                    Text(
                        text = "Belum ada hasil. Coba cari nama radio atau pilih genre di atas.",
                        color = TextMuted,
                        fontFamily = MonoFont,
                        fontSize = 9.sp,
                        lineHeight = 13.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(
                            items = results,
                            key = { it.stationUuid.ifBlank { it.streamUrl } }
                        ) { station ->
                            BrowseStationItem(
                                station = station,
                                onClick = { onPlayStation(station) },
                                onAdd = { onAddToFavorites(station) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseStationItem(
    station: RadioBrowserStation,
    onClick: () -> Unit,
    onAdd: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .clickable { onClick() }
            .padding(vertical = 5.dp, horizontal = 4.dp)
    ) {

        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0xFF1A1D1D)),
            contentAlignment = Alignment.Center
        ) {
            if (station.favicon.isNotBlank()) {
                AsyncImage(
                    model = station.favicon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Radio,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(7.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = station.name,
                color = TextLight,
                fontFamily = MonoFont,
                fontSize = 9.5.sp,
                maxLines = 1
            )
            val subtitle = listOfNotNull(
                station.tags.split(",").firstOrNull { it.isNotBlank() }?.trim(),
                station.countryCode.ifBlank { null }
            ).joinToString(" \u2022 ")
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontFamily = MonoFont,
                    fontSize = 7.5.sp,
                    maxLines = 1
                )
            }
        }

        IconButton(
            onClick = onAdd,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Tambah ke Favorit",
                tint = Gold,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}
