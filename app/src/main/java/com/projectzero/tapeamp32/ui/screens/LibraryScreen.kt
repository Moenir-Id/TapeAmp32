package com.projectzero.tapeamp32.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.projectzero.tapeamp32.R
import com.projectzero.tapeamp32.data.Playlist
import com.projectzero.tapeamp32.data.Song
import com.projectzero.tapeamp32.ui.theme.*
import com.projectzero.tapeamp32.viewmodel.PlayerViewModel
import com.projectzero.tapeamp32.viewmodel.Screen

internal enum class LibraryTab {
    SONGS,
    ALBUMS,
    ARTISTS,
    FOLDERS,
    PLAYLISTS
}

@Composable
fun LibraryScreen(
    vm: PlayerViewModel,
    isCompact: Boolean = false
) {

    val library by vm.library.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()

    var tab by remember {
        mutableStateOf(LibraryTab.SONGS)
    }

    var query by remember {
        mutableStateOf("")
    }

    var openGroupKey by remember { mutableStateOf<String?>(null) }

    var openPlaylistId by remember { mutableStateOf<String?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var addToPlaylistSong by remember { mutableStateOf<Song?>(null) }

    fun switchTab(newTab: LibraryTab) {
        tab = newTab
        openGroupKey = null
        openPlaylistId = null
    }

    val filtered = remember(
        library,
        query
    ) {

        if (query.isBlank()) {
            library
        } else {

            library.filter { song ->

                song.title.contains(
                    query,
                    ignoreCase = true
                ) ||

                song.artist.contains(
                    query,
                    ignoreCase = true
                ) ||

                song.album.contains(
                    query,
                    ignoreCase = true
                )
            }
        }
    }

    val openPlaylist = remember(playlists, openPlaylistId) {
        playlists.firstOrNull { it.id == openPlaylistId }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBlack)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        LibrarySidebar(
            selectedTab = tab,
            onTabSelected = { switchTab(it) },
            isCompact = isCompact
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(7.dp))
                .background(PanelBlack)
                .border(
                    width = 1.dp,
                    color = StrokeGold,
                    shape = RoundedCornerShape(7.dp)
                )
                .padding(
                    horizontal = 9.dp,
                    vertical = 8.dp
                )
        ) {

            val drillDownTitle = when {
                tab == LibraryTab.PLAYLISTS && openPlaylist != null -> openPlaylist.name
                tab != LibraryTab.SONGS && tab != LibraryTab.PLAYLISTS && openGroupKey != null ->
                    openGroupKey!!.ifBlank { stringResource(R.string.library_unknown) }
                else -> null
            }

            if (drillDownTitle != null) {

                DrillDownHeader(
                    title = drillDownTitle,
                    onBack = {
                        if (tab == LibraryTab.PLAYLISTS) openPlaylistId = null
                        else openGroupKey = null
                    },
                    trailing = {
                        if (tab == LibraryTab.PLAYLISTS && openPlaylist != null) {
                            val songsInPlaylist = vm.songsForPlaylist(openPlaylist)
                            PlayAllChip(
                                enabled = songsInPlaylist.isNotEmpty(),
                                onClick = { vm.playPlaylist(openPlaylist); vm.navigate(Screen.PLAYER) }
                            )
                        }
                    }
                )

            } else {

                LibraryToolbar(
                    query = query,
                    onQueryChange = { query = it },

                    onShuffleAll = {
                        vm.shuffleAll()
                        vm.navigate(Screen.PLAYER)
                    },

                    showNewPlaylistAction = tab == LibraryTab.PLAYLISTS,
                    onNewPlaylist = { showCreatePlaylistDialog = true }
                )
            }

            Spacer(
                modifier = Modifier.height(9.dp)
            )

            when (tab) {

                LibraryTab.SONGS -> {

                    SongsView(
                        songs = filtered,
                        onSongClick = { song ->

                            vm.playSong(
                                song,
                                library
                            )

                            vm.navigate(
                                Screen.PLAYER
                            )
                        },
                        onAddToPlaylist = { song -> addToPlaylistSong = song }
                    )
                }

                LibraryTab.ALBUMS -> {

                    GroupOrDrillDown(
                        groupedData = filtered.groupBy { it.album.ifBlank { stringResource(R.string.library_unknown_album) } },
                        openGroupKey = openGroupKey,
                        onGroupClick = { openGroupKey = it },
                        onSongClick = { song, songsInGroup ->
                            vm.playSong(song, songsInGroup)
                            vm.navigate(Screen.PLAYER)
                        },
                        onAddToPlaylist = { song -> addToPlaylistSong = song }
                    )
                }

                LibraryTab.ARTISTS -> {

                    GroupOrDrillDown(
                        groupedData = filtered.groupBy { it.artist.ifBlank { stringResource(R.string.library_unknown_artist) } },
                        openGroupKey = openGroupKey,
                        onGroupClick = { openGroupKey = it },
                        onSongClick = { song, songsInGroup ->
                            vm.playSong(song, songsInGroup)
                            vm.navigate(Screen.PLAYER)
                        },
                        onAddToPlaylist = { song -> addToPlaylistSong = song }
                    )
                }

                LibraryTab.FOLDERS -> {

                    GroupOrDrillDown(
                        groupedData = filtered.groupBy {
                            it.path.substringBeforeLast("/").ifBlank { stringResource(R.string.library_unknown_folder) }
                        },
                        openGroupKey = openGroupKey,
                        onGroupClick = { openGroupKey = it },
                        onSongClick = { song, songsInGroup ->
                            vm.playSong(song, songsInGroup)
                            vm.navigate(Screen.PLAYER)
                        },
                        onAddToPlaylist = { song -> addToPlaylistSong = song }
                    )
                }

                LibraryTab.PLAYLISTS -> {

                    if (openPlaylist != null) {

                        val songsInPlaylist = remember(openPlaylist, library) {
                            vm.songsForPlaylist(openPlaylist)
                        }

                        if (songsInPlaylist.isEmpty()) {
                            EmptyLibraryView(
                                title = stringResource(R.string.library_playlist_empty_title),
                                subtitle = stringResource(R.string.library_playlist_empty_hint)
                            )
                        } else {
                            SongsView(
                                songs = songsInPlaylist,
                                onSongClick = { song ->
                                    vm.playSong(song, songsInPlaylist)
                                    vm.navigate(Screen.PLAYER)
                                },
                                onAddToPlaylist = null,
                                onRemoveFromCurrentPlaylist = { song ->
                                    vm.removeSongFromPlaylist(openPlaylist.id, song)
                                }
                            )
                        }

                    } else {

                        PlaylistsListView(
                            playlists = playlists,
                            onPlaylistClick = { openPlaylistId = it.id },
                            onDeletePlaylist = { vm.deletePlaylist(it.id) },
                            onNewPlaylist = { showCreatePlaylistDialog = true }
                        )
                    }
                }
            }
        }
    }

    if (showCreatePlaylistDialog) {
        NamePromptDialog(
            title = stringResource(R.string.library_new_playlist_title),
            label = stringResource(R.string.library_playlist_name_label),
            initialValue = "",
            confirmLabel = stringResource(R.string.library_create_label),
            onDismiss = { showCreatePlaylistDialog = false },
            onConfirm = { name ->
                vm.createPlaylist(name)
                showCreatePlaylistDialog = false
            }
        )
    }

    val songPendingPlaylist = addToPlaylistSong
    if (songPendingPlaylist != null) {
        AddToPlaylistDialog(
            song = songPendingPlaylist,
            playlists = playlists,
            onDismiss = { addToPlaylistSong = null },
            onPick = { playlist ->
                vm.addSongToPlaylist(playlist.id, songPendingPlaylist)
                addToPlaylistSong = null
            },
            onCreateNewAndPick = { name ->
                vm.createPlaylist(name)
                addToPlaylistSong = null

            }
        )
    }
}
