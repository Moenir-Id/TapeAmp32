package com.projectzero.tapeamp32.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectzero.tapeamp32.R
import com.projectzero.tapeamp32.data.Song
import com.projectzero.tapeamp32.ui.theme.*

// Flat song-list view: table header, song row, and empty-state composables.
// Split out of LibraryScreen.kt.

@Composable
internal fun SongsView(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onAddToPlaylist: ((Song) -> Unit)? = null,
    onRemoveFromCurrentPlaylist: ((Song) -> Unit)? = null
) {

    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        if (songs.isEmpty()) {

            EmptyLibraryView()

        } else {

            /* ====================================================
             * TABLE HEADER
             * ==================================================== */

            LibraryTableHeader()

            HorizontalDivider(
                color = StrokeGold.copy(
                    alpha = 0.65f
                ),
                thickness = 0.7.dp
            )

            Spacer(
                modifier = Modifier.height(2.dp)
            )

            /* ====================================================
             * SONG LIST
             * ==================================================== */

            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {

                items(
                    items = songs,
                    key = {
                        it.path
                    }
                ) { song ->

                    SongRow(
                        song = song,
                        onClick = {
                            onSongClick(song)
                        },
                        onAddToPlaylist = onAddToPlaylist?.let { { it(song) } },
                        onRemoveFromCurrentPlaylist = onRemoveFromCurrentPlaylist?.let { { it(song) } }
                    )
                }
            }

            /* ====================================================
             * FOOTER
             * ==================================================== */

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = stringResource(R.string.library_songs_count, songs.size),
                color = TextMuted,
                fontFamily = MonoFont,
                fontSize = 9.sp
            )
        }
    }
}

/* ================================================================
 * TABLE HEADER
 * ================================================================ */

@Composable
internal fun LibraryTableHeader() {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(25.dp)
            .padding(
                horizontal = 5.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        LibraryHeaderText(
            text = stringResource(R.string.library_col_title),
            modifier = Modifier.weight(2f)
        )

        LibraryHeaderText(
            text = stringResource(R.string.library_col_artist),
            modifier = Modifier.weight(1.45f)
        )

        LibraryHeaderText(
            text = stringResource(R.string.library_col_album),
            modifier = Modifier.weight(1.45f)
        )

        LibraryHeaderText(
            text = stringResource(R.string.library_col_duration),
            modifier = Modifier.width(58.dp)
        )

        LibraryHeaderText(
            text = stringResource(R.string.library_col_format),
            modifier = Modifier.width(52.dp)
        )

        Spacer(modifier = Modifier.width(26.dp))
    }
}

@Composable
internal fun LibraryHeaderText(
    text: String,
    modifier: Modifier
) {

    Text(
        text = text,
        color = GoldBright,
        fontFamily = MonoFont,
        fontWeight = FontWeight.Bold,
        fontSize = 8.sp,
        maxLines = 1,
        modifier = modifier
    )
}

/* ================================================================
 * SONG ROW
 * ================================================================ */

@Composable
internal fun SongRow(
    song: Song,
    onClick: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    onRemoveFromCurrentPlaylist: (() -> Unit)? = null
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(
                RoundedCornerShape(2.dp)
            )
            .clickable {
                onClick()
            }
            .padding(
                horizontal = 5.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        /* ========================================================
         * TITLE
         * ======================================================== */

        Text(
            text = song.title.ifBlank {
                stringResource(R.string.library_unknown_title)
            },
            color = TextLight,
            fontFamily = DisplayFont,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(2f)
        )

        /* ========================================================
         * ARTIST
         * ======================================================== */

        Text(
            text = song.artist.ifBlank {
                stringResource(R.string.library_unknown_artist)
            },
            color = TextMuted,
            fontFamily = DisplayFont,
            fontSize = 8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.45f)
        )

        /* ========================================================
         * ALBUM
         * ======================================================== */

        Text(
            text = song.album.ifBlank {
                stringResource(R.string.library_unknown_album)
            },
            color = TextMuted,
            fontFamily = DisplayFont,
            fontSize = 8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.45f)
        )

        /* ========================================================
         * DURATION
         * ======================================================== */

        Text(
            text = formatMs(
                song.durationMs
            ),
            color = TextMuted,
            fontFamily = MonoFont,
            fontSize = 8.sp,
            maxLines = 1,
            modifier = Modifier.width(58.dp)
        )

        /* ========================================================
         * FORMAT
         * ======================================================== */

        Text(
            text = song.format.uppercase(),
            color = GoldBright,
            fontFamily = MonoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 8.sp,
            maxLines = 1,
            modifier = Modifier.width(52.dp)
        )

        /* ========================================================
         * BARU (v1.2): AKSI PLAYLIST
         * - Di tab SONGS/ALBUMS/ARTISTS/FOLDERS: ikon "+" -> tambah ke playlist.
         * - Di dalam sebuah playlist yang sedang dibuka: ikon tempat sampah ->
         *   keluarkan lagu ini dari playlist tsb (tidak menghapus filenya).
         * ======================================================== */

        Box(
            modifier = Modifier.width(26.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                onAddToPlaylist != null -> {
                    Icon(
                        imageVector = Icons.Filled.PlaylistAdd,
                        contentDescription = stringResource(R.string.library_add_to_playlist_desc),
                        tint = TextMuted,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onAddToPlaylist() }
                    )
                }
                onRemoveFromCurrentPlaylist != null -> {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.library_remove_from_playlist_desc),
                        tint = TextMuted,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onRemoveFromCurrentPlaylist() }
                    )
                }
            }
        }
    }

    // PATCH (klasik): divider baris lagu diganti StrokeGold tipis (sebelumnya
    // abu-abu #202323) supaya konsisten dengan divider header tabel di atasnya.
    HorizontalDivider(
        color = StrokeGold.copy(alpha = 0.4f),
        thickness = 0.5.dp
    )
}

/* ================================================================
 * EMPTY LIBRARY
 * ================================================================ */

@Composable
internal fun EmptyLibraryView(
    title: String = stringResource(R.string.library_no_songs_found),
    subtitle: String = stringResource(R.string.library_import_hint)
) {

    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment =
            Alignment.Center
    ) {

        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Icon(
                imageVector =
                    Icons.Filled.MusicNote,
                contentDescription = null,
                tint = Gold.copy(
                    alpha = 0.75f
                ),
                modifier = Modifier.size(30.dp)
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = title,
                color = TextLight,
                fontFamily = MonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = subtitle,
                color = TextMuted,
                fontFamily = MonoFont,
                fontSize = 9.sp
            )
        }
    }
}

/* ================================================================
 * GROUP + DRILL-DOWN (Album/Artist/Folder)
 *
 * BARU (v1.2): sebelumnya cuma GroupedLibraryView yang menampilkan daftar
 * grup TANPA bisa diketuk (mati/dekoratif). Sekarang mengetuk sebuah grup
 * membuka daftar lagunya (drill-down), memakai SongsView yang sama dengan
 * tab SONGS supaya perilakunya konsisten (search header diganti header
 * back di LibraryScreen di atas).
 * ================================================================ */
