package com.projectzero.tapeamp32.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectzero.tapeamp32.R
import com.projectzero.tapeamp32.ui.theme.*
import com.projectzero.tapeamp32.viewmodel.Screen

@Composable
internal fun DrillDownHeader(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Icon(
            imageVector = Icons.Filled.ArrowBackIosNew,
            contentDescription = stringResource(R.string.library_back_desc),
            tint = GoldBright,
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable { onBack() }
                .padding(6.dp)
        )

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = title.uppercase(),
            color = TextLight,
            fontFamily = MonoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        trailing()
    }
}

@Composable
internal fun PlayAllChip(enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(
                Brush.verticalGradient(listOf(Color(0xFF2A2A28), PanelBlackAlt, Color(0xFF161614)))
            )
            .border(width = 1.dp, color = StrokeGold, shape = RoundedCornerShape(5.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = if (enabled) GoldBright else TextMuted,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.library_play_all),
            color = if (enabled) GoldBright else TextMuted,
            fontFamily = MonoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 8.sp
        )
    }
}

@Composable
internal fun LibrarySidebar(
    selectedTab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit,
    isCompact: Boolean = false
) {

    Column(
        modifier = Modifier
            .width(if (isCompact) 60.dp else 102.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(7.dp))
            .background(
                PanelBlack
            )
            .border(
                width = 1.dp,
                color = StrokeGold,
                shape = RoundedCornerShape(7.dp)
            )
            .padding(
                horizontal = if (isCompact) 4.dp else 7.dp,
                vertical = 10.dp
            )
    ) {

        LibraryTabItem(
            label = stringResource(R.string.library_tab_songs),
            icon = Icons.Filled.MusicNote,
            selected =
                selectedTab == LibraryTab.SONGS,
            isCompact = isCompact
        ) {
            onTabSelected(
                LibraryTab.SONGS
            )
        }

        LibraryTabItem(
            label = stringResource(R.string.library_tab_albums),
            icon = Icons.Filled.Album,
            selected =
                selectedTab == LibraryTab.ALBUMS,
            isCompact = isCompact
        ) {
            onTabSelected(
                LibraryTab.ALBUMS
            )
        }

        LibraryTabItem(
            label = stringResource(R.string.library_tab_artists),
            icon = Icons.Filled.Person,
            selected =
                selectedTab == LibraryTab.ARTISTS,
            isCompact = isCompact
        ) {
            onTabSelected(
                LibraryTab.ARTISTS
            )
        }

        LibraryTabItem(
            label = stringResource(R.string.library_tab_folders),
            icon = Icons.Filled.Folder,
            selected =
                selectedTab == LibraryTab.FOLDERS,
            isCompact = isCompact
        ) {
            onTabSelected(
                LibraryTab.FOLDERS
            )
        }

        LibraryTabItem(
            label = stringResource(R.string.library_tab_playlists),
            icon = Icons.Filled.PlaylistPlay,
            selected =
                selectedTab == LibraryTab.PLAYLISTS,
            isCompact = isCompact
        ) {
            onTabSelected(
                LibraryTab.PLAYLISTS
            )
        }
    }
}

@Composable
internal fun LibraryTabItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    isCompact: Boolean = false,
    onClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(
                RoundedCornerShape(4.dp)
            )
            .background(
                if (selected) {
                    GoldBright
                } else {
                    PanelBlackAlt
                }
            )
            .border(
                width = 0.8.dp,
                color = StrokeGold,
                shape =
                    RoundedCornerShape(4.dp)
            )
            .clickable {
                onClick()
            }
            .padding(
                horizontal = if (isCompact) 4.dp else 8.dp
            ),
        horizontalArrangement =
            if (isCompact) Arrangement.Center else Arrangement.Start,
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Icon(
            imageVector = icon,
            contentDescription = if (isCompact) label else null,
            tint =
                if (selected) {
                    BgBlack
                } else {
                    TextMuted
                },
            modifier = Modifier.size(15.dp)
        )

        if (!isCompact) {

            Spacer(
                modifier = Modifier.width(7.dp)
            )

            Text(
                text = label,
                color =
                    if (selected) {
                        BgBlack
                    } else {
                        TextLight
                    },
                fontFamily = MonoFont,
                fontWeight =
                    if (selected) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Normal
                    },

                fontSize = 8.sp,
                letterSpacing = 0.35.sp,
                maxLines = 1
            )
        }
    }

    Spacer(
        modifier = Modifier.height(4.dp)
    )
}

@Composable
internal fun LibraryToolbar(
    query: String,
    onQueryChange: (String) -> Unit,
    onShuffleAll: () -> Unit,
    showNewPlaylistAction: Boolean = false,
    onNewPlaylist: () -> Unit = {}
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Row(
            modifier = Modifier
                .weight(1f)
                .height(32.dp)
                .clip(
                    RoundedCornerShape(4.dp)
                )

                .background(
                    PanelBlackAlt
                )
                .border(
                    width = 0.8.dp,
                    color = StrokeGold,
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(
                    horizontal = 8.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(14.dp)
            )

            Spacer(
                modifier = Modifier.width(7.dp)
            )

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = TextLight,
                    fontFamily = MonoFont,
                    fontSize = 10.sp
                ),
                cursorBrush = SolidColor(Gold),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->

                    if (query.isEmpty()) {

                        Text(
                            text = stringResource(R.string.library_search_placeholder),
                            color = TextMuted,
                            fontFamily = MonoFont,
                            fontSize = 10.sp
                        )
                    }

                    innerTextField()
                }
            )
        }

        Spacer(
            modifier = Modifier.width(8.dp)
        )

        if (showNewPlaylistAction) {

            Row(
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF2A2A28), PanelBlackAlt, Color(0xFF161614))
                        )
                    )
                    .border(width = 1.dp, color = StrokeGold, shape = RoundedCornerShape(5.dp))
                    .clickable { onNewPlaylist() }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = GoldBright,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = stringResource(R.string.library_new_playlist_title),
                    color = GoldBright,
                    fontFamily = MonoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.5.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
        }

        Row(
            modifier = Modifier
                .height(32.dp)
                .clip(
                    RoundedCornerShape(5.dp)
                )
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF2A2A28), PanelBlackAlt, Color(0xFF161614))
                    )
                )
                .border(
                    width = 1.dp,
                    color = StrokeGold,
                    shape = RoundedCornerShape(5.dp)
                )
                .clickable {
                    onShuffleAll()
                }
                .padding(
                    horizontal = 12.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Icon(
                imageVector = Icons.Filled.Shuffle,
                contentDescription = null,
                tint = GoldBright,
                modifier = Modifier.size(13.dp)
            )

            Spacer(
                modifier = Modifier.width(5.dp)
            )

            Text(
                text = stringResource(R.string.library_shuffle_all),
                color = GoldBright,
                fontFamily = MonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 8.5.sp
            )
        }
    }
}
