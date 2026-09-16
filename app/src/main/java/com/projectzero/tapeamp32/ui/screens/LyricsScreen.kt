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

        val sidecar = path?.let { findSidecarLyrics(it) }
        if (sidecar != null) {
            lyricsText = sidecar
            return@LaunchedEffect
        }

        isLoadingEmbedded = true
        val embedded = runCatching { extractEmbeddedLyrics(context, currentSong.uri) }.getOrNull()

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

                syncedLines != null -> SyncedLyricsView(
                    lines = syncedLines,
                    positionMs = positionMs,
                    onLineTap = { line -> vm.seekTo(line.timeMs) }
                )

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

@Composable
private fun SyncedLyricsView(
    lines: List<LyricLine>,
    positionMs: Long,
    onLineTap: (LyricLine) -> Unit
) {

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

                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onLineTap(line) }
                    .padding(vertical = 4.dp)
            )
        }
    }
}
