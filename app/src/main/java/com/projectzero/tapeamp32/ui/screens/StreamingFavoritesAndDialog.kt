package com.projectzero.tapeamp32.ui.screens

import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectzero.tapeamp32.R
import com.projectzero.tapeamp32.data.RadioStation
import com.projectzero.tapeamp32.ui.theme.*

// Favorites panel, edit-station dialog, and station list item composables for
// the Streaming screen. Split out of StreamingScreen.kt.

@Composable
internal fun StreamingFavoritesPanel(
    stations: List<RadioStation>,
    selected: RadioStation?,
    onSelect: (RadioStation) -> Unit,
    onAddCustomUrl: (String) -> Unit,
    onDeleteCustomUrl: (RadioStation) -> Unit,
    onEditCustomStation: (RadioStation, String, String) -> Unit,
    // BARU (fitur "Jelajahi Radio"): state & callback tab JELAJAHI, semuanya
    // diteruskan dari StreamingScreen.kt (yang mengambilnya dari
    // PlayerViewModel) -- panel ini sendiri tetap murni presentational,
    // konsisten dengan pola callback eksplisit yang sudah dipakai untuk
    // parameter-parameter di atas (bukan menerima `vm: PlayerViewModel`
    // langsung).
    browseQuery: String,
    browseResults: List<com.projectzero.tapeamp32.data.RadioBrowserStation>,
    browseLoading: Boolean,
    browseError: String?,
    onBrowseQueryChange: (String) -> Unit,
    onBrowseSearch: (String) -> Unit,
    onBrowseTagSelect: (String) -> Unit,
    onBrowseLoadInitial: () -> Unit,
    onPlayFoundStation: (com.projectzero.tapeamp32.data.RadioBrowserStation) -> Unit,
    onAddFoundStationToFavorites: (com.projectzero.tapeamp32.data.RadioBrowserStation) -> Unit,
    modifier: Modifier = Modifier
) {

    // BARU (fitur "Jelajahi Radio"): 0 = tab FAVORIT (perilaku lama, tidak
    // diubah), 1 = tab JELAJAHI (baru). State tab ini lokal ke panel (tidak
    // perlu dipersist -- wajar balik ke FAVORIT tiap kali layar Streaming
    // dibuka ulang).
    var selectedTabIndex by remember { mutableStateOf(0) }

    // BARU (fitur "edit stream"): stasiun yang sedang diedit (nama + URL) lewat
    // dialog. null berarti dialog sedang tidak ditampilkan.
    var stationBeingEdited by remember {
        mutableStateOf<RadioStation?>(null)
    }

    Column(
        modifier = modifier
            .clip(
                RoundedCornerShape(6.dp)
            )
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF151818),
                        Color(0xFF0B0D0D),
                        Color(0xFF111313)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = StrokeGold.copy(
                    alpha = 0.65f
                ),
                shape =
                    RoundedCornerShape(6.dp)
            )
            .padding(8.dp)
    ) {

        /*
         * BARU (fitur "Jelajahi Radio"): TAB SWITCHER FAVORIT / JELAJAHI
         */

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StreamingTabButton(
                label = stringResource(R.string.streaming_favorites_header),
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                modifier = Modifier.weight(1f)
            )
            StreamingTabButton(
                label = "JELAJAHI",
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(7.dp))

        if (selectedTabIndex == 1) {

            StreamingBrowseTab(
                query = browseQuery,
                results = browseResults,
                isLoading = browseLoading,
                errorMessage = browseError,
                onQueryChange = onBrowseQueryChange,
                onSearch = onBrowseSearch,
                onTagSelect = onBrowseTagSelect,
                onLoadInitial = onBrowseLoadInitial,
                onPlayStation = onPlayFoundStation,
                onAddToFavorites = onAddFoundStationToFavorites,
                modifier = Modifier.weight(1f)
            )

        } else {

        /*
         * HEADER
         */

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    StrokeGold.copy(
                        alpha = 0.30f
                    )
                )
        )

        Spacer(
            modifier = Modifier.height(5.dp)
        )

        /*
         * STATION LIST
         */

        if (stations.isEmpty()) {

            Text(
                text = stringResource(R.string.streaming_no_stations),
                color = TextMuted,
                fontFamily = MonoFont,
                fontSize = 9.sp,
                lineHeight = 13.sp,
                modifier = Modifier.weight(1f)
            )

        } else {

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement =
                Arrangement.spacedBy(2.dp)
        ) {

            items(
                items = stations,
                key = {
                    it.id
                }
            ) { station ->

                val isSelected =
                    selected?.id == station.id

                StreamingStationItem(
                    station = station,
                    selected = isSelected,
                    onClick = {
                        onSelect(station)
                    },
                    // Cuma stasiun custom (ditambahkan lewat "ADD STREAM URL") yang
                    // boleh dihapus/diganti nama -- stasiun bawaan/daftar favorit tetap.
                    onDelete =
                        if (station.id.startsWith("custom_"))
                            { { onDeleteCustomUrl(station) } }
                        else
                            null,
                    onRename =
                        if (station.id.startsWith("custom_"))
                            { { stationBeingEdited = station } }
                        else
                            null
                )
            }
        }

        }

        /*
         * ============================================================
         * ADD CUSTOM STREAM URL
         * ============================================================
         */

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    StrokeGold.copy(
                        alpha = 0.30f
                    )
                )
        )

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        Text(
            text = stringResource(R.string.streaming_add_url_header),
            color = GoldBright,
            fontFamily = MonoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            letterSpacing = 0.6.sp
        )

        Spacer(
            modifier = Modifier.height(5.dp)
        )

        var customUrlText by remember {
            mutableStateOf("")
        }

        OutlinedTextField(
            value = customUrlText,
            onValueChange = {
                customUrlText = it
            },
            placeholder = {
                Text(
                    text = "https://stream.url/live.mp3",
                    fontFamily = MonoFont,
                    fontSize = 9.sp,
                    color = TextMuted
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
                unfocusedBorderColor =
                    StrokeGold.copy(alpha = 0.4f),
                cursorColor = GoldBright
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        Button(
            onClick = {
                val url = customUrlText.trim()
                if (url.isNotEmpty()) {
                    onAddCustomUrl(url)
                    customUrlText = ""
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Gold
            ),
            modifier = Modifier.fillMaxWidth()
        ) {

            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(15.dp)
            )

            Spacer(
                modifier = Modifier.width(4.dp)
            )

            Text(
                text = stringResource(R.string.streaming_play_url),
                color = Color.Black,
                fontFamily = MonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
            )
        }

        } // end else (tab FAVORIT)

        /*
         * ============================================================
         * EDIT DIALOG (custom stations only) -- nama + URL stream
         * ============================================================
         */

        val stationToEdit = stationBeingEdited

        if (stationToEdit != null) {

            EditStationDialog(
                currentName = stationToEdit.name,
                currentUrl = stationToEdit.streamUrl,
                onConfirm = { newName, newUrl ->
                    onEditCustomStation(stationToEdit, newName, newUrl)
                    stationBeingEdited = null
                },
                onDismiss = {
                    stationBeingEdited = null
                }
            )
        }
    }
}

/* ================================================================
 * EDIT STATION DIALOG (name + stream URL)
 * ================================================================ */

// BARU (fitur "edit stream"): dialog berisi dua text field untuk mengganti
// nama DAN URL stream stasiun custom (sebelumnya cuma nama lewat
// RenameStationDialog). Nilai awal kedua field diisi dari data saat ini
// supaya user tinggal edit, bukan ketik ulang dari kosong.
@Composable
internal fun EditStationDialog(
    currentName: String,
    currentUrl: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {

    var nameText by remember(currentName) {
        mutableStateOf(currentName)
    }

    var urlText by remember(currentUrl) {
        mutableStateOf(currentUrl)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF151818),
        titleContentColor = GoldBright,
        textContentColor = TextLight,
        title = {
            Text(
                text = stringResource(R.string.streaming_edit_dialog_title),
                fontFamily = MonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        },
        text = {
            Column {

                OutlinedTextField(
                    value = nameText,
                    onValueChange = {
                        nameText = it
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.streaming_edit_dialog_name_label),
                            fontFamily = MonoFont,
                            fontSize = 10.sp
                        )
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = MonoFont,
                        fontSize = 12.sp,
                        color = TextLight
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold,
                        unfocusedBorderColor =
                            StrokeGold.copy(alpha = 0.4f),
                        cursorColor = GoldBright
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(
                    modifier = Modifier.height(10.dp)
                )

                OutlinedTextField(
                    value = urlText,
                    onValueChange = {
                        urlText = it
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.streaming_edit_dialog_url_label),
                            fontFamily = MonoFont,
                            fontSize = 10.sp
                        )
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = MonoFont,
                        fontSize = 12.sp,
                        color = TextLight
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold,
                        unfocusedBorderColor =
                            StrokeGold.copy(alpha = 0.4f),
                        cursorColor = GoldBright
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (nameText.isNotBlank() && urlText.isNotBlank()) {
                        onConfirm(nameText, urlText)
                    }
                }
            ) {
                Text(
                    text = stringResource(R.string.streaming_edit_save),
                    color = GoldBright,
                    fontFamily = MonoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(
                    text = stringResource(R.string.streaming_edit_cancel),
                    color = TextMuted,
                    fontFamily = MonoFont,
                    fontSize = 10.sp
                )
            }
        }
    )
}

/* ================================================================
 * SINGLE STATION
 * ================================================================ */

@Composable
internal fun StreamingStationItem(
    station: RadioStation,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(47.dp)
            .clip(
                RoundedCornerShape(4.dp)
            )
            .background(
                if (selected)
                    Color(0xFF292311)
                else
                    Color.Transparent
            )
            .border(
                width =
                    if (selected)
                        1.dp
                    else
                        0.5.dp,

                color =
                    if (selected)
                        Gold
                    else
                        Color.Transparent,

                shape =
                    RoundedCornerShape(4.dp)
            )
            .clickable {
                onClick()
            }
            .padding(
                horizontal = 7.dp,
                vertical = 5.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        /*
         * RADIO ICON
         */

        Box(
            modifier = Modifier
                .size(25.dp)
                .clip(
                    RoundedCornerShape(3.dp)
                )
                .background(
                    if (selected)
                        Gold.copy(alpha = 0.12f)
                    else
                        Color(0xFF111313)
                ),
            contentAlignment =
                Alignment.Center
        ) {

            Icon(
                imageVector =
                    Icons.Filled.Radio,
                contentDescription = null,
                tint =
                    if (selected)
                        GoldBright
                    else
                        TextMuted,
                modifier =
                    Modifier.size(15.dp)
            )
        }

        Spacer(
            modifier = Modifier.width(7.dp)
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text = station.name,
                color =
                    if (selected)
                        GoldBright
                    else
                        TextLight,

                fontFamily = DisplayFont,
                fontWeight =
                    if (selected)
                        FontWeight.Bold
                    else
                        FontWeight.Normal,

                fontSize = 11.sp,
                maxLines = 1
            )

            Text(
                text =
                    // FIX (bug "bitrate selalu 0"): bitrate stasiun cuma diketahui
                    // SETELAH stream-nya benar-benar tersambung (lihat
                    // PlayerManager.streamBitrateKbps di panel Stream Information),
                    // bukan dari station.bitrateKbps yang statis & selalu 0 untuk
                    // stasiun custom -- daripada tampil "0 kbps" yang menyesatkan
                    // di badge kecil ini, tampilkan "--" selama belum diketahui.
                    if (station.bitrateKbps > 0)
                        "${station.bitrateKbps} kbps"
                    else
                        "--",
                color = TextMuted,
                fontFamily = MonoFont,
                fontSize = 8.sp,
                maxLines = 1
            )
        }

        /*
         * RENAME (custom stations only)
         */

        if (onRename != null) {

            IconButton(
                onClick = onRename,
                modifier = Modifier.size(22.dp)
            ) {

                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription =
                        stringResource(R.string.streaming_edit_station),
                    tint = TextMuted,
                    modifier = Modifier.size(13.dp)
                )
            }

            Spacer(
                modifier = Modifier.width(1.dp)
            )
        }

        /*
         * DELETE (custom stations only)
         */

        if (onDelete != null) {

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(22.dp)
            ) {

                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription =
                        stringResource(R.string.streaming_delete_station),
                    tint = TextMuted,
                    modifier = Modifier.size(14.dp)
                )
            }

            Spacer(
                modifier = Modifier.width(2.dp)
            )
        }

        /*
         * ACTIVE INDICATOR
         */

        if (selected) {

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(25.dp)
                    .clip(
                        RoundedCornerShape(2.dp)
                    )
                    .background(
                        GoldBright
                    )
            )
        }
    }
}

/* ================================================================
 * STREAM PLAYER PANEL
 * ================================================================ */

/* ================================================================
 * BARU (fitur "Jelajahi Radio"): tombol tab kecil buat switcher
 * FAVORIT / JELAJAHI di atas StreamingFavoritesPanel.
 * ================================================================ */

@Composable
private fun StreamingTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(
                if (selected) Gold else Color(0xFF1A1D1D)
            )
            .border(
                width = 1.dp,
                color = if (selected) Gold else StrokeGold.copy(alpha = 0.35f),
                shape = RoundedCornerShape(5.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else TextMuted,
            fontFamily = MonoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 8.5.sp,
            letterSpacing = 0.5.sp
        )
    }
}
