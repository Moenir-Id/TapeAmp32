package com.projectzero.tapeamp32.desktop.shim

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.projectzero.tapeamp32.ui.components.CassetteDeck
import com.projectzero.tapeamp32.ui.theme.CassetteSkin

/**
 * CassetteDeck asli dirancang untuk tinggi tetap 235dp (HP landscape). Di jendela desktop yang
 * tinggi, badan kaset ikut melar tapi isinya (label, jendela pita) tidak, jadi banyak ruang kosong.
 * Pembungkus ini memperbesar kaset secara proporsional (lewat LocalDensity) sampai mengisi
 * tinggi yang tersedia, lalu menaruhnya di tengah. Dipasang lewat tabel `rewrites` pada
 * PlayerScreen.kt; sumber di app/ tidak diubah.
 */
@Composable
fun ScaledCassetteDeck(
    skin: CassetteSkin,
    title: String,
    artist: String,
    isPlaying: Boolean,
    progressFraction: Float,
    modifier: Modifier = Modifier,
    reelSpeed: Float = 1f,
    tapeType: String = "TYPE II\nCHROME\nBIAS",
    nrOn: Boolean = true,
    serial: String = "D90",
    side: String = "A",
    onSeekDelta: (Float) -> Unit = {},
    onScrubSpeedChange: (Float) -> Unit = {},
    seekFractionPerFullRotation: Float = 0.10f,
    reelAnimationEnabled: Boolean = true,
    onDoubleTap: () -> Unit = {},
    onLongPress: () -> Unit = {}
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val base = LocalDensity.current
        // Skala: isi tinggi yang ada, tapi lebar (dalam dp terskala) tidak kurang dari 520dp
        // supaya proporsi mirip di HP. Tidak pernah diperkecil di bawah 1x.
        val scaleByHeight = maxHeight.value / 235f
        val scaleByWidth = maxWidth.value / 520f
        val scale = minOf(scaleByHeight, scaleByWidth).coerceIn(1f, 3f)

        CompositionLocalProvider(
            LocalDensity provides Density(base.density * scale, base.fontScale)
        ) {
            CassetteDeck(
                skin = skin,
                title = title,
                artist = artist,
                isPlaying = isPlaying,
                progressFraction = progressFraction,
                modifier = Modifier.fillMaxWidth().height(235.dp),
                reelSpeed = reelSpeed,
                tapeType = tapeType,
                nrOn = nrOn,
                serial = serial,
                side = side,
                onSeekDelta = onSeekDelta,
                onScrubSpeedChange = onScrubSpeedChange,
                seekFractionPerFullRotation = seekFractionPerFullRotation,
                reelAnimationEnabled = reelAnimationEnabled,
                onDoubleTap = onDoubleTap,
                onLongPress = onLongPress
            )
        }
    }
}
