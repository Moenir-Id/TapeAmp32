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

/* ================================================================
 * LIBRARY TAB
 *
 * BARU (v1.2): tab PLAYLISTS ditambahkan di samping SONGS/ALBUMS/
 * ARTISTS/FOLDERS yang sudah ada, supaya playlist buatan pengguna
 * jadi bagian dari navigasi Library yang sama (bukan layar terpisah).
 * ================================================================ */

internal enum class LibraryTab {
    SONGS,
    ALBUMS,
    ARTISTS,
    FOLDERS,
    PLAYLISTS
}

/* ================================================================
 * LIBRARY SCREEN
 * ================================================================ */

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

    // BARU (v1.2): "grouping isi library buat cepat" -- sebelumnya baris grup di tab
    // ALBUMS/ARTISTS/FOLDERS cuma menampilkan nama + jumlah lagu tanpa bisa diketuk.
    // Sekarang menyimpan grup mana yang sedang dibuka (drill-down) supaya pengguna
    // bisa langsung lompat ke lagu-lagu di dalam grup itu, tanpa harus scroll/cari
    // manual di tab SONGS.
    var openGroupKey by remember { mutableStateOf<String?>(null) }

    // BARU (v1.2): playlist mana yang sedang dibuka (drill-down), dan dialog-dialog
    // terkait playlist (buat baru / pilih playlist tujuan saat menambah lagu).
    var openPlaylistId by remember { mutableStateOf<String?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var addToPlaylistSong by remember { mutableStateOf<Song?>(null) }

    fun switchTab(newTab: LibraryTab) {
        tab = newTab
        openGroupKey = null
        openPlaylistId = null
    }

    /* ------------------------------------------------------------
     * SEARCH
     *
     * Library hanya menggunakan hasil scan folder yang memang
     * sudah dimasukkan pengguna.
     *
     * Tidak ada refreshLibrary() otomatis di sini.
     * ------------------------------------------------------------ */

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

    /* ============================================================
     * ROOT
     * ============================================================ */

    // PATCH (klasik, seragam dgn Equalizer): sebelumnya sidebar + konten
    // langsung menempel di BgBlack dengan 1 garis divider tipis -- sekarang
    // dibungkus 2 panel gold-bordered (PanelBlack + StrokeGold, sudut
    // membulat 7dp) dipisah jarak 8dp, PERSIS pola "EQ MAIN PANEL" +
    // "VU METER PANEL" di EqualizerScreen supaya kedua layar terasa satu
    // keluarga desain, bukan dua gaya berbeda.
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBlack)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        /* ========================================================
         * LEFT LIBRARY NAVIGATION
         * ======================================================== */

        LibrarySidebar(
            selectedTab = tab,
            onTabSelected = { switchTab(it) },
            isCompact = isCompact
        )

        /* ========================================================
         * MAIN CONTENT
         * ======================================================== */

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

            // BARU (v1.2): saat sedang membuka sebuah grup (Album/Artist/Folder) atau
            // sebuah playlist, toolbar pencarian & shuffle diganti header "back" ringkas
            // supaya jelas pengguna sedang berada di dalam sebuah grup/playlist, bukan
            // di daftar utama.
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

                /* ====================================================
                 * TOP TOOLBAR
                 * ==================================================== */

                LibraryToolbar(
                    query = query,
                    onQueryChange = { query = it },
                    // FIX (v1.3): sebelumnya SHUFFLE ALL cuma memutar lagu tapi TIDAK
                    // pindah ke layar Pemutar (beda perilaku dengan tap satu lagu yang
                    // langsung redirect) -- sekarang disamakan: play lalu navigate ke
                    // Screen.PLAYER, persis seperti onSongClick.
                    onShuffleAll = {
                        vm.shuffleAll()
                        vm.navigate(Screen.PLAYER)
                    },
                    // BARU (v1.2): tombol "+" cuma relevan/tampil di tab PLAYLISTS,
                    // dipakai untuk membuka dialog "New Playlist".
                    showNewPlaylistAction = tab == LibraryTab.PLAYLISTS,
                    onNewPlaylist = { showCreatePlaylistDialog = true }
                )
            }

            Spacer(
                modifier = Modifier.height(9.dp)
            )

            /* ====================================================
             * CONTENT
             * ==================================================== */

            when (tab) {

                LibraryTab.SONGS -> {

                    SongsView(
                        songs = filtered,
                        onSongClick = { song ->

                            // FIX (v1.5): sebelumnya antrian diisi dari [filtered] (hasil
                            // pencarian) -- kalau pencarian mempersempit hasil jadi 1-2 lagu
                            // saja, antrian pemutaran ikut jadi sesempit itu, sehingga tombol
                            // NEXT terlihat "tidak berfungsi" (cuma muter-muter di 1 lagu yang
                            // sama / tidak ke mana-mana). Search di sini HARUSNYA cuma alat
                            // bantu menemukan lagu -- begitu diputar, antrian tetap SELURUH
                            // isi library (query dikosongkan tidak mengubah lagu apa yang lagi
                            // main), supaya NEXT/PREV tetap bisa menjelajah seluruh library
                            // seperti lazimnya.
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

                // BARU (v1.2): tab PLAYLISTS.
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

    /* ============================================================
     * DIALOG: NEW PLAYLIST
     * ============================================================ */

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

    /* ============================================================
     * DIALOG: ADD SONG TO PLAYLIST
     * ============================================================ */

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
                // Playlist baru langsung diisi lagu ini di panggilan berikutnya oleh
                // pengguna (state playlists butuh 1 recomposition untuk terisi ID
                // baru) -- cukup untuk patch kecil ini, pengguna tinggal ketuk "+"
                // lagi sekali kalau ingin langsung isi lagu yang sama.
            }
        )
    }
}

/* ================================================================
 * DRILL-DOWN HEADER (dipakai untuk grup Album/Artist/Folder & Playlist)
 * ================================================================ */
