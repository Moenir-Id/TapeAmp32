package com.projectzero.tapeamp32.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectzero.tapeamp32.R
import com.projectzero.tapeamp32.data.Playlist
import com.projectzero.tapeamp32.data.Song
import com.projectzero.tapeamp32.ui.theme.*

@Composable
internal fun NamePromptDialog(
    title: String,
    label: String,
    initialValue: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelBlack,
        shape = RoundedCornerShape(7.dp),
        modifier = Modifier.border(
            width = 1.dp,
            color = StrokeGold,
            shape = RoundedCornerShape(7.dp)
        ),
        title = {
            Text(
                text = title,
                fontFamily = MonoFont,
                fontWeight = FontWeight.Bold,
                color = GoldBright
            )
        },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = {
                    Text(text = label, fontFamily = MonoFont, color = TextMuted)
                },
                shape = RoundedCornerShape(4.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextLight,
                    unfocusedTextColor = TextLight,
                    focusedBorderColor = GoldBright,
                    unfocusedBorderColor = StrokeGold,
                    cursorColor = GoldBright,
                    focusedContainerColor = PanelBlackAlt,
                    unfocusedContainerColor = PanelBlackAlt,
                    focusedLabelColor = GoldBright,
                    unfocusedLabelColor = TextMuted
                ),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = MonoFont,
                    fontSize = 12.sp
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { if (value.isNotBlank()) onConfirm(value) }) {
                Text(text = confirmLabel, fontFamily = MonoFont, fontWeight = FontWeight.Bold, color = GoldBright)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.library_cancel), fontFamily = MonoFont, color = TextMuted)
            }
        }
    )
}

@Composable
internal fun AddToPlaylistDialog(
    song: Song,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPick: (Playlist) -> Unit,
    onCreateNewAndPick: (String) -> Unit
) {
    var showNewNameField by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelBlack,
        shape = RoundedCornerShape(7.dp),
        modifier = Modifier.border(
            width = 1.dp,
            color = StrokeGold,
            shape = RoundedCornerShape(7.dp)
        ),
        title = {
            Text(
                text = stringResource(R.string.library_add_to_playlist_title),
                fontFamily = MonoFont,
                fontWeight = FontWeight.Bold,
                color = GoldBright
            )
        },
        text = {
            Column {
                Text(
                    text = song.title.ifBlank { stringResource(R.string.library_unknown_title) },
                    color = TextMuted,
                    fontFamily = MonoFont,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (!showNewNameField) {
                    if (playlists.isEmpty()) {
                        Text(
                            text = stringResource(R.string.library_no_playlists_inline),
                            color = TextMuted,
                            fontFamily = MonoFont,
                            fontSize = 9.sp
                        )
                    } else {
                        playlists.forEach { playlist ->
                            Text(
                                text = playlist.name,
                                color = TextLight,
                                fontFamily = DisplayFont,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(playlist) }
                                    .padding(vertical = 8.dp)
                            )
                            HorizontalDivider(color = StrokeGold.copy(alpha = 0.3f), thickness = 0.5.dp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.library_create_new_playlist_inline),
                        color = GoldBright,
                        fontFamily = MonoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showNewNameField = true }
                            .padding(vertical = 8.dp)
                    )
                } else {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        label = { Text(text = stringResource(R.string.library_playlist_name_label), fontFamily = MonoFont, color = TextMuted) },
                        shape = RoundedCornerShape(4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight,
                            focusedBorderColor = GoldBright,
                            unfocusedBorderColor = StrokeGold,
                            cursorColor = GoldBright,
                            focusedContainerColor = PanelBlackAlt,
                            unfocusedContainerColor = PanelBlackAlt,
                            focusedLabelColor = GoldBright,
                            unfocusedLabelColor = TextMuted
                        )
                    )
                }
            }
        },
        confirmButton = {
            if (showNewNameField) {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        onCreateNewAndPick(newName)
                    }
                }) {
                    Text(text = stringResource(R.string.library_create_label), fontFamily = MonoFont, fontWeight = FontWeight.Bold, color = GoldBright)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.library_close), fontFamily = MonoFont, color = TextMuted)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.library_cancel), fontFamily = MonoFont, color = TextMuted)
            }
        }
    )
}
