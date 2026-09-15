package com.projectzero.tapeamp32.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.projectzero.tapeamp32.R
import com.projectzero.tapeamp32.data.LyricLine
import com.projectzero.tapeamp32.data.extractEmbeddedLyrics
import com.projectzero.tapeamp32.data.parseLrc
import com.projectzero.tapeamp32.ui.theme.*
import com.projectzero.tapeamp32.viewmodel.PlayerViewModel
import java.io.File

/* ================================================================
 * LYRICS SCREEN
 * ================================================================
 *
 * FITUR: tab lirik terpisah dari PLAYER.
 *
 * Sumber lirik, dicoba berurutan:
 *  1. File sidecar dengan nama sama seperti lagunya, ekstensi .lrc
 *     atau .txt, di folder yang sama (konvensi umum banyak music
 *     player). Skip kalau path lagu adalah content:// URI dari SAF.
 *  2. PATCH: kalau sidecar tidak ada (atau lagunya dari SAF), coba
 *     baca lirik yang sudah ada TERTANAM di metadata file itu sendiri
 *     -- Vorbis Comment "LYRICS"/"UNSYNCEDLYRICS"/"SYNCEDLYRICS" yang
 *     dipakai FLAC/OGG. Ini jalan untuk kedua jenis path (biasa maupun
 *     content://) karena dibaca lewat Media3, bukan java.io.File.
 *
 * PATCH: kalau teks lirik yang ditemukan (dari sumber manapun di atas)
 * berformat LRC dengan timestamp ("[00:12.34] ..."), lirik ditampilkan
 * baris-per-baris dan otomatis scroll + highlight mengikuti posisi
 * playback lagu yang sedang berjalan. Kalau tidak ada timestamp sama
 * sekali, tetap ditampilkan sebagai teks polos seperti sebelumnya.
 */

private fun stripLrcTimestamps(raw: String): String {
    val timestampRegex = Regex("""\[\d{1,3}:\d{2}(\.\d{1,3})?]""")
    return raw.lineSequence()
        .map { line -> timestampRegex.replace(line, "").trim() }
        .filter { it.isNotBlank() }
        .joinToString("\n")
}

private fun findSidecarLyrics(songPath: String): String? {
    if (songPath.isBlank() || songPath.startsWith("content://")) return null
    val songFile = File(songPath)
    val baseName = songFile.nameWithoutExtension
    val dir = songFile.parentFile ?: return null
    for (ext in listOf("lrc", "txt")) {
        val candidate = File(dir, "$baseName.$ext")
        if (candidate.exists() && candidate.canRead()) {
            return runCatching { candidate.readText() }.getOrNull()
        }
    }
    return null
}

@UnstableApi
@Composable
fun LyricsScreen(vm: PlayerViewModel) {

    val context = LocalContext.current
    val song by vm.currentSong.collectAsStateWithLifecycle()
    val positionMs by vm.positionMs.collectAsStateWithLifecycle()

    var lyricsText by remember { mutableStateOf<String?>(null) }
    var isLoadingEmbedded by remember { mutableStateOf(false) }
    var lookedUpForPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(song?.path) {
        val currentSong = song
        val path = currentSong?.path
        lookedUpForPath = path
        lyricsText = null
        isLoadingEmbedded = false

        if (currentSong == null) return@LaunchedEffect

        // 1) File sidecar (.lrc / .txt) di folder yang sama.
        val sidecar = path?.let { findSidecarLyrics(it) }
        if (sidecar != null) {
            lyricsText = sidecar
            return@LaunchedEffect
        }

        // 2) PATCH: fallback ke lirik yang sudah tertanam di metadata file
        // itu sendiri (Vorbis Comment "LYRICS" dkk di FLAC/OGG).
        isLoadingEmbedded = true
        val embedded = runCatching { extractEmbeddedLyrics(context, currentSong.uri) }.getOrNull()
        // Cegah race kalau lagu sudah ganti lagi sebelum lookup ini selesai.
        if (lookedUpForPath == path) {
            lyricsText = embedded
            isLoadingEmbedded = false
        }
    }

    val syncedLines = remember(lyricsText) { lyricsText?.let(::parseLrc) }
    val plainText = remember(lyricsText, syncedLines) {
        if (syncedLines == null) lyricsText?.let(::stripLrcTimestamps) else null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBlack)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {

        /* ============================================================
         * HEADER
         * ============================================================ */

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Filled.Lyrics,
                contentDescription = null,
                tint = GoldBright,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.lyrics_header),
                color = TextLight,
                fontFamily = MonoFont,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = song?.let { stringResource(R.string.lyrics_song_subtitle, it.title, it.artist) }
                ?: stringResource(R.string.lyrics_no_song_loaded),
            color = TextMuted,
            fontFamily = MonoFont,
            fontSize = 10.sp,
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(12.dp))

        /* ============================================================
         * LYRICS PANEL
         * ============================================================ */

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(PanelBlack)
                .padding(16.dp)
        ) {

            when {
                song == null -> Text(
                    text = stringResource(R.string.lyrics_empty_no_song),
                    color = TextMuted,
                    fontFamily = MonoFont,
                    fontSize = 12.sp
                )

                // Lirik bersinkron (ada timestamp LRC) -> auto-scroll + highlight.
                syncedLines != null -> SyncedLyricsView(
                    lines = syncedLines,
                    positionMs = positionMs,
                    onLineTap = { line -> vm.seekTo(line.timeMs) }
                )

                // Lirik polos (tanpa timestamp), dari sidecar atau metadata.
                plainText != null -> Text(
                    text = plainText,
                    color = TextLight,
                    fontFamily = MonoFont,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                )

                isLoadingEmbedded -> Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = GoldBright,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.lyrics_loading_embedded),
                        color = TextMuted,
                        fontFamily = MonoFont,
                        fontSize = 12.sp
                    )
                }

                else -> Text(
                    text = stringResource(R.string.lyrics_not_found, song?.title ?: ""),
                    color = TextMuted,
                    fontFamily = MonoFont,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

/**
 * PATCH: daftar baris lirik yang auto-scroll dan highlight baris aktif
 * sesuai [positionMs] (posisi playback lagu yang sedang berjalan).
 */
@Composable
private fun SyncedLyricsView(
    lines: List<LyricLine>,
    positionMs: Long,
    onLineTap: (LyricLine) -> Unit
) {
    // FIX: kalau parseLrc() balikin daftar kosong (edge case: ada tag "[...]"
    // tapi baris teksnya semua kebuang), jangan lanjut ke LazyColumn sama
    // sekali -- itu yang bikin animateScrollToItem() di bawah bisa nembak ke
    // index yang gak valid dan nge-crash SELURUH Compose tree (termasuk
    // NavRail di sebelahnya, karena exception di sini menjalar ke atas).
    if (lines.isEmpty()) {
        Text(
            text = stringResource(R.string.lyrics_found_but_empty),
            color = TextMuted,
            fontFamily = MonoFont,
            fontSize = 12.sp
        )
        return
    }

    val listState = rememberLazyListState()

    val activeIndex = remember(lines, positionMs) {
        lines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
    }

    LaunchedEffect(activeIndex, lines) {
        // FIX: index harus selalu di dalam rentang lines saat ini -- kalau
        // lagu ganti dan `lines` baru lebih pendek dari activeIndex lama,
        // target bisa >= lines.size dan animateScrollToItem() melempar
        // IndexOutOfBoundsException yang tidak ketangkep di mana pun ->
        // crash. Di-coerce dulu + dibungkus try/catch sebagai jaring
        // pengaman terakhir supaya kalaupun ada edge case lain, paling
        // buruk cuma auto-scroll yang gagal, bukan seluruh layar ngilang.
        val target = (activeIndex - 2).coerceIn(0, lines.lastIndex)
        runCatching { listState.animateScrollToItem(target) }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(lines) { index, line ->
            val isActive = index == activeIndex
            Text(
                text = line.text.ifBlank { "\u266A" },
                color = if (isActive) GoldBright else TextMuted,
                fontFamily = MonoFont,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                fontSize = if (isActive) 15.sp else 13.sp,
                lineHeight = 22.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    // FIX: tap-to-seek -- sebelumnya baris lirik cuma teks
                    // pasif tanpa handler apa pun, jadi tap/geser ke baris
                    // tertentu tidak pernah mengubah posisi playback. Area
                    // tap dibuat selebar baris (bukan cuma lebar teksnya)
                    // biar gampang di-tap, dan pakai interactionSource +
                    // indication = null supaya tidak ada ripple aneh di atas
                    // background gelap panel lirik.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onLineTap(line) }
                    .padding(vertical = 4.dp)
            )
        }
    }
}
