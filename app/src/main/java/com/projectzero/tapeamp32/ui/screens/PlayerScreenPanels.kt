package com.projectzero.tapeamp32.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectzero.tapeamp32.R
import com.projectzero.tapeamp32.ui.components.PowerSwitch
import com.projectzero.tapeamp32.ui.components.ScreenToggleButton
import com.projectzero.tapeamp32.ui.components.VerticalVolumeSlider
import com.projectzero.tapeamp32.ui.components.WaveformSeekBar
import com.projectzero.tapeamp32.ui.theme.*

// Side info panel, scrolling track bar, and small status-badge composables
// for the Player screen, plus the formatMs() time-formatting helper. Split out
// of PlayerScreen.kt.

@Composable
internal fun PlayerSidePanel(
    isCompact: Boolean = false,
    powerOn: Boolean,
    onPowerToggle: () -> Unit,
    fullScreenOn: Boolean,
    onScreenToggle: () -> Unit,
    volume: Float,
    onVolumeChange: (Float) -> Unit
) {

    Column(
        modifier = Modifier
            // BARU (v1.5): sedikit lebih ramping saat Split Screen/Multi-Window
            // (isCompact) supaya kaset & VU Meter di sebelahnya tetap kebagian
            // ruang yang layak.
            .width(if (isCompact) 54.dp else 72.dp)
            .fillMaxHeight()
            .clip(
                RoundedCornerShape(7.dp)
            )
            .background(
                PanelBlackAlt
            )
            .border(
                width = 1.dp,
                color = StrokeGold,
                shape = RoundedCornerShape(7.dp)
            )
            .padding(
                vertical = 10.dp,
                horizontal = if (isCompact) 2.dp else 4.dp
            ),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.SpaceBetween
    ) {

        /* ========================================================
         * POWER + SCREEN + VOLUME AREA
         * ======================================================== */

        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            PowerSwitch(
                on = powerOn,
                onToggle = onPowerToggle
            )

            /*
             * FIX: jarak antara POWER, SCREEN, dan VOLUME dibuat lebih
             * estetik/lega (tidak mepet 0dp lagi, tapi tetap ringkas)
             */

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            ScreenToggleButton(
                fullScreenOn = fullScreenOn,
                onToggle = onScreenToggle
            )

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            VerticalVolumeSlider(
                volume = volume,
                onVolumeChange = onVolumeChange,
                trackHeight = 120.dp
            )
        }

        /* ========================================================
         * DECORATIVE VENT
         * ======================================================== */

        Column(
            modifier = Modifier
                .width(30.dp)
                .height(105.dp),
            verticalArrangement =
                Arrangement.spacedBy(5.dp)
        ) {

            repeat(6) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(
                            RoundedCornerShape(1.dp)
                        )
                        .background(
                            Color(0xFF080909)
                        )
                        .border(
                            width = 0.5.dp,
                            color = Color(0xFF242727),
                            shape = RoundedCornerShape(1.dp)
                        )
                )
            }
        }

        /* ========================================================
         * BRAND
         * ======================================================== */

        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Text(
                text = "TAPEAMP 32",
                color = GoldBright,
                fontFamily = MonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 8.sp,
                maxLines = 1
            )

            Spacer(
                modifier = Modifier.height(1.dp)
            )

            Text(
                text = "HI-FI 32-BIT",
                color = TextMuted,
                fontFamily = MonoFont,
                fontSize = 6.sp,
                maxLines = 1
            )
        }
    }
}

/* ================================================================
 * TRACK BAR
 * ================================================================ */

@Composable
internal fun PlayerTrackBar(
    position: Long,
    duration: Long,
    progressFraction: Float,
    format: String,
    // FIX (bug "24-BIT / 96kHz sama di semua lagu"): string resource
    // player_track_format_info dulu meng-hardcode literal "24-BIT / 96kHz" di
    // dalamnya, jadi baris di bawah kaset ini SELALU menampilkan angka itu apa pun
    // lagunya. Sekarang sample rate/bit depth REAL dari AudioTrack yang sedang
    // aktif (PlayerManager) diteruskan dari PlayerScreen.
    sampleRateHz: Int,
    bitDepth: Int,
    isHiRes: Boolean,
    waveform: FloatArray?,
    onSeek: (Float) -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(5.dp)
            )
            .background(
                Color(0xFF090A0A)
            )
            .border(
                width = 0.8.dp,
                color = Color(0xFF242626),
                shape = RoundedCornerShape(5.dp)
            )
            .padding(
                horizontal = 9.dp,
                vertical = 5.dp
            )
    ) {

        /* ========================================================
         * TIME + SLIDER
         * ======================================================== */

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(
                text = formatMs(position),
                color = GoldBright,
                fontFamily = MonoFont,
                fontSize = 9.sp
            )

            // BARU (v2.4): WAVEFORM SEEKBAR -- gambar amplitude asli lagu (lihat
            // WaveformExtractor) & bisa di-tap/drag langsung di atas bar-nya untuk
            // seek, gaya visual scrubbing presisi. Menggantikan Slider Material
            // polos yang cuma garis+thumb tanpa representasi audio sama sekali.
            WaveformSeekBar(
                waveform = waveform,
                progressFraction = progressFraction,
                onSeek = onSeek,
                activeColor = GoldBright,
                inactiveColor = Color(0xFF3A352A),
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .padding(
                        horizontal = 7.dp
                    )
            )

            Text(
                text = formatMs(duration),
                color = TextMuted,
                fontFamily = MonoFont,
                fontSize = 9.sp
            )
        }

        Spacer(
            modifier = Modifier.height(2.dp)
        )

        /* ========================================================
         * FORMAT
         * --------------------------------------------------------
         * FIX (bug "badge HI-RES emas selalu nyala walau lagu bukan
         * hi-res"): sebelumnya ada PlayerBadge("HI-RES") statis di sini
         * yang SELALU ditampilkan untuk lagu apa pun (MP3 128kbps pun
         * tetap dikasih badge ini) -- gak pernah dicek ke isHiRes sama
         * sekali, murni dekorasi/hardcoded. Indikator HI-RES yang BENERAN
         * real-time (nyambung ke PlayerManager.isHiRes, cuma nyala kalau
         * source-nya lossless DAN sample rate/bit depth aktual di
         * AudioTrack memang tinggi) sudah ada terpisah di VFD Status
         * Panel (baris cyan "HI-RES", lihat VfdStatusPanel.kt) -- jadi
         * badge statis di sini dihapus total, bukan disambungkan ulang,
         * supaya tidak dobel dengan indikator yang sudah benar itu.
         * ======================================================== */

        Text(
            text = if (sampleRateHz > 0) {
                stringResource(
                    R.string.player_track_format_info,
                    format,
                    formatAudioSpec(sampleRateHz, bitDepth, isHiRes)
                )
            } else {
                // Belum ada AudioTrack aktif -> jangan tampilkan angka karangan.
                format
            },
            color = TextMuted,
            fontFamily = MonoFont,
            fontSize = 8.sp,
            maxLines = 1
        )
    }
}

/* ================================================================
 * TIME FORMAT
 * ================================================================ */

fun formatMs(ms: Long): String {

    if (ms <= 0L) {
        return "00:00"
    }

    val totalSec = ms / 1000L

    val minutes =
        totalSec / 60L

    val seconds =
        totalSec % 60L

    return "%02d:%02d".format(
        minutes,
        seconds
    )
}

/**
 * Label spesifikasi audio REAL, mis. "24-BIT / 96kHz" (hi-res) atau "44.1kHz".
 * Bit depth cuma disertakan untuk sumber lossless hi-res, sama seperti di VFD.
 */
fun formatAudioSpec(sampleRateHz: Int, bitDepth: Int, isHiRes: Boolean): String {
    val khz = sampleRateHz / 1000f
    val khzLabel = if (khz == khz.toInt().toFloat()) "${khz.toInt()}kHz" else "%.1fkHz".format(khz)
    return if (isHiRes) "$bitDepth-BIT / $khzLabel" else khzLabel
}
