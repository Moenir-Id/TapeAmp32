package com.projectzero.tapeamp32.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectzero.tapeamp32.R
import com.projectzero.tapeamp32.data.Playlist
import com.projectzero.tapeamp32.data.Song
import com.projectzero.tapeamp32.ui.theme.*

@Composable
internal fun GroupOrDrillDown(
    groupedData: Map<String, List<Song>>,
    openGroupKey: String?,
    onGroupClick: (String) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    val songsInOpenGroup = openGroupKey?.let { groupedData[it] }

    if (openGroupKey != null && songsInOpenGroup != null) {
        SongsView(
            songs = songsInOpenGroup,
            onSongClick = { song -> onSongClick(song, songsInOpenGroup) },
            onAddToPlaylist = onAddToPlaylist
        )
    } else {
        GroupedLibraryView(
            groupedData = groupedData,
            onGroupClick = onGroupClick
        )
    }
}

@Composable
internal fun GroupedLibraryView(
    groupedData: Map<String, List<Song>>,
    onGroupClick: (String) -> Unit
) {

    if (groupedData.isEmpty()) {

        EmptyLibraryView()

        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {

        items(
            items = groupedData.entries.toList(),
            key = {
                it.key
            }
        ) { entry ->

            LibraryGroupRow(
                title = entry.key,
                count = entry.value.size,

                onClick = { onGroupClick(entry.key) }
            )
        }
    }
}

@Composable
internal fun LibraryGroupRow(
    title: String,
    count: Int,
    onClick: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(
                vertical = 5.dp
            )
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween,
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(
                text = title.ifBlank {
                    stringResource(R.string.library_unknown)
                },
                color = TextLight,
                fontFamily = DisplayFont,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = stringResource(R.string.library_track_count, count),
                color = TextMuted,
                fontFamily = MonoFont,
                fontSize = 8.sp
            )
        }

        HorizontalDivider(
            color = StrokeGold.copy(alpha = 0.45f),
            thickness = 0.6.dp,
            modifier = Modifier.padding(
                top = 5.dp
            )
        )
    }
}

@Composable
internal fun PlaylistsListView(
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    onNewPlaylist: () -> Unit
) {
    if (playlists.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.PlaylistPlay,
                    contentDescription = null,
                    tint = Gold.copy(alpha = 0.75f),
                    modifier = Modifier.size(30.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.library_no_playlists_yet),
                    color = TextLight,
                    fontFamily = MonoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.library_tap_new_playlist_hint),
                    color = TextMuted,
                    fontFamily = MonoFont,
                    fontSize = 9.sp
                )
            }
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = playlists, key = { it.id }) { playlist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clickable { onPlaylistClick(playlist) }
                    .padding(horizontal = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.PlaylistPlay,
                    contentDescription = null,
                    tint = GoldBright,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = playlist.name,
                    color = TextLight,
                    fontFamily = DisplayFont,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.library_track_count, playlist.songPaths.size),
                    color = TextMuted,
                    fontFamily = MonoFont,
                    fontSize = 8.sp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.library_delete_playlist_desc),
                    tint = TextMuted,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onDeletePlaylist(playlist) }
                )
            }
            HorizontalDivider(
                color = StrokeGold.copy(alpha = 0.4f),
                thickness = 0.5.dp
            )
        }
    }
}
