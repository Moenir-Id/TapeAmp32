package com.projectzero.tapeamp32.desktop.shim

import androidx.compose.runtime.mutableStateOf

/**
 * Permintaan pindah tab di Library (Songs/Albums/Artists/Folders/Playlists) dari pintasan keyboard
 * Alt+huruf di Main.kt. LibraryScreen mengambilnya lewat aturan rewrite di build.gradle.kts.
 * [index] = urutan LibraryTab: 0 SONGS, 1 ALBUMS, 2 ARTISTS, 3 FOLDERS, 4 PLAYLISTS.
 */
object LibraryTabRequest {
    class Request(val index: Int, val at: Long)

    val pending = mutableStateOf<Request?>(null)
}
