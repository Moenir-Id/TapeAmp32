package com.projectzero.tapeamp32.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectzero.tapeamp32.R
import com.projectzero.tapeamp32.ui.theme.*

/* ================================================================
 * VFD STATUS PANEL
 * ----------------------------------------------------------------
 * Panel status ala layar Vacuum Fluorescent Display / LED Hi-Fi
 * klasik (Kenwood/Marantz/Pioneer era 80-90an): kaca cekung gelap,
 * scanline halus, "hantu" segmen mati di belakang teks aktif, dot
 * LED yang benar-benar menyala (glow ganda, bukan cuma warna teks),
 * dan bezel logam dengan sekrup di tiap sudut -- supaya panel ini
 * terasa sebagai UNIT HARDWARE TERPISAH, bukan sekadar 3 baris teks.
 *
 * Dipasang TEPAT DI BAWAH VuMeter, dalam kolom lebar yang SAMA
 * (lihat PlayerScreen: keduanya dibungkus satu Column ber-weight
 * supaya seluruh tinggi kolom terisi penuh, tidak menyisakan ruang
 * kosong dan tidak menempel ke elemen lain).
 * ================================================================ */

@Composable
fun VfdStatusPanel(
    isUsbDacConnected: Boolean,
    dacLabel: String?,
    dspEngineOn: Boolean,
    peakActive: Boolean,
    // BARU: Hi-Res Audio Badge Detector. isHiRes = true jika sampleRate >= 48000 Hz
    // ATAU bitDepth > 16-bit (lihat PlayerManager.bitDepthFromEncoding).
    isHiRes: Boolean = false,
    sampleRate: Int = 0,
    bitDepth: Int = 16,
    // BARU (v1.8): status OFFLOAD hardware AKTUAL (bukan cuma toggle BIT-PERFECT
    // MODE) -- bitPerfectOn = apakah toggle-nya diminta nyala, offloadActive =
    // apakah AudioTrackConfig yang BENAR-BENAR dipakai ExoPlayer sekarang melapor
    // offload=true. Sebelumnya (lihat changelog v1.6) status ini hanya bisa
    // dikonfirmasi manual lewat adb logcat; sekarang muncul real-time di sini juga.
    bitPerfectOn: Boolean = false,
    offloadActive: Boolean = false,
    // BARU (v2.1): SLEEP TIMER. sleepTimerActive = true kalau salah satu mode (MINUTES
    // atau END_OF_TRACK) sedang berjalan -- lihat PlayerViewModel.sleepTimerMode.
    // sleepTimerSubLabel sudah diformat siap-tampil oleh pemanggil (PlayerScreen), supaya
    // panel ini tidak perlu tahu soal SleepTimerMode/formatSleepCountdown sama sekali --
    // sama seperti dacLabel di atas yang juga sudah string jadi dari pemanggil.
    sleepTimerActive: Boolean = false,
    sleepTimerSubLabel: String = stringResource(R.string.vfd_off),
    modifier: Modifier = Modifier
) {

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        PanelBlackAlt,
                        PanelBlack
                    )
                )
            )
            .border(
                width = 1.dp,
                color = StrokeGold,
                shape = RoundedCornerShape(7.dp)
            )
            .padding(5.dp)
    ) {

        /* ============================================================
         * RECESSED GLASS
         * ============================================================ */

        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(4.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(VfdGlassDeep, VfdGlass, VfdGlassDeep)
                    )
                )
                .border(
                    width = 0.8.dp,
                    color = Color(0xFF000000).copy(alpha = 0.6f),
                    shape = RoundedCornerShape(4.dp)
                )
                .vfdScanlines()
                .padding(horizontal = 5.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            VfdLine(
                lit = isUsbDacConnected,
                mainLabel = if (isUsbDacConnected) stringResource(R.string.vfd_usb_dac) else stringResource(R.string.vfd_internal),
                subLabel = if (isUsbDacConnected) {
                    (dacLabel?.take(10)?.uppercase()) ?: stringResource(R.string.vfd_hires)
                } else {
                    stringResource(R.string.vfd_builtin)
                },
                litColor = VfdPhosphor,
                dimColor = VfdPhosphorDim
            )

            VfdDivider()

            VfdLine(
                lit = dspEngineOn,
                mainLabel = if (dspEngineOn) stringResource(R.string.vfd_dsp_on) else stringResource(R.string.vfd_bypass),
                subLabel = if (dspEngineOn) stringResource(R.string.vfd_tape_warm) else stringResource(R.string.vfd_flat),
                litColor = VfdPhosphor,
                dimColor = VfdPhosphorDim
            )

            VfdDivider()

            // BARU: baris badge Hi-Res. Saat isHiRes=false (audio standar 16-bit/44.1kHz
            // atau belum ada lagu diputar), tampilkan "STD" / "44.1K" redup (ghost).
            VfdLine(
                lit = isHiRes,
                mainLabel = if (isHiRes) stringResource(R.string.vfd_hires) else stringResource(R.string.vfd_std),
                subLabel = hiResSubLabel(isHiRes, sampleRate, bitDepth),
                litColor = VfdCyanHiRes,
                dimColor = VfdCyanHiResDim
            )

            VfdDivider()

            // BARU (v1.8): baris OFFLOAD -- "OFF" redup selagu BIT-PERFECT MODE belum
            // dinyalakan sama sekali (belum diminta). Begitu toggle aktif, baris ini
            // menyala/redup sesuai status AKTUAL yang dilaporkan ExoPlayer: "HARDWARE"
            // kalau device/USB DAC & format lagu benar-benar memakai jalur offload
            // direct-path, atau "FALLBACK" (tetap redup, TIDAK dianggap error) kalau
            // otomatis jatuh ke bypass software biasa.
            VfdLine(
                lit = offloadActive,
                mainLabel = stringResource(R.string.vfd_offload),
                subLabel = when {
                    !bitPerfectOn -> stringResource(R.string.vfd_off)
                    offloadActive -> stringResource(R.string.vfd_hardware)
                    else -> stringResource(R.string.vfd_fallback)
                },
                litColor = VfdCyanHiRes,
                dimColor = VfdCyanHiResDim
            )

            VfdDivider()

            // BARU (v2.1): baris SLEEP TIMER -- "OFF" redup selagi tidak ada timer aktif,
            // menyala begitu salah satu mode (hitung mundur / stop-setelah-lagu-ini) berjalan.
            VfdLine(
                lit = sleepTimerActive,
                mainLabel = stringResource(R.string.vfd_sleep),
                subLabel = sleepTimerSubLabel,
                litColor = VfdCyanHiRes,
                dimColor = VfdCyanHiResDim
            )

            VfdDivider()

            VfdPeakLine(peakActive = peakActive)
        }
    }
}

/* ================================================================
 * SCANLINE OVERLAY (efek kaca VFD)
 * ================================================================ */

private fun Modifier.vfdScanlines(): Modifier = this.drawWithContent {
    drawContent()
    val lineColor = Color.Black.copy(alpha = 0.22f)
    var y = 0f
    while (y < size.height) {
        drawLine(
            color = lineColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 0.6f
        )
        y += 3f
    }
}

/* ================================================================
 * SATU BARIS INDIKATOR (dot LED + label utama + label kecil)
 * ================================================================ */

@Composable
private fun VfdLine(
    lit: Boolean,
    mainLabel: String,
    subLabel: String,
    litColor: Color,
    dimColor: Color
) {

    val glowAlpha by animateFloatAsState(
        targetValue = if (lit) 1f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "vfd_glow"
    )

    val textColor = if (lit) litColor else dimColor

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {

        LedDot(
            color = litColor,
            glowAlpha = glowAlpha
        )

        Box(modifier = Modifier.weight(1f)) {

            // "Hantu" segmen mati di belakang teks aktif -- ciri khas VFD/dot-matrix
            // jadul di mana karakter yang tidak menyala tetap sedikit terlihat.
            Text(
                text = ghostFor(mainLabel),
                color = VfdPhosphorGhost,
                fontFamily = MonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 7.5.sp,
                maxLines = 1
            )

            Column {
                Text(
                    text = mainLabel,
                    color = textColor,
                    fontFamily = MonoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 7.5.sp,
                    maxLines = 1
                )
                Text(
                    text = subLabel,
                    color = textColor.copy(alpha = if (lit) 0.65f else 0.5f),
                    fontFamily = MonoFont,
                    fontWeight = FontWeight.Normal,
                    fontSize = 6.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/* ================================================================
 * BARIS PEAK (LED kotak, berkedip saat aktif)
 * ================================================================ */

@Composable
private fun VfdPeakLine(peakActive: Boolean) {

    val infiniteTransition = rememberInfiniteTransition(label = "peak_blink")

    val blink by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 220, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "peak_blink_value"
    )

    val glowAlpha = if (peakActive) blink else 0f
    val textColor = if (peakActive) VfdAmberPeak else VfdAmberPeakDim

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {

        // LED kotak (bukan bulat) supaya beda bentuk dari 2 indikator di atasnya --
        // menegaskan ini adalah indikator PERINGATAN, bukan status biasa.
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            VfdAmberPeak.copy(alpha = glowAlpha),
                            VfdAmberPeak.copy(alpha = glowAlpha * 0.25f)
                        )
                    )
                )
                .border(
                    width = 0.6.dp,
                    color = VfdAmberPeak.copy(alpha = 0.5f + glowAlpha * 0.5f),
                    shape = RoundedCornerShape(1.5.dp)
                )
        )

        Text(
            text = stringResource(R.string.vfd_peak),
            color = textColor,
            fontFamily = MonoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 7.5.sp,
            letterSpacing = 1.sp,
            maxLines = 1
        )
    }
}

/* ================================================================
 * DOT LED BULAT DENGAN GLOW GANDA (lapisan luar redup + inti terang)
 * ================================================================ */

@Composable
private fun LedDot(
    color: Color,
    glowAlpha: Float
) {

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(9.dp)
    ) {

        // Lapisan glow luar
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = glowAlpha * 0.55f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Inti LED
        Box(
            modifier = Modifier
                .size(4.5.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.35f + glowAlpha * 0.65f))
                .border(
                    width = 0.5.dp,
                    color = color.copy(alpha = 0.4f + glowAlpha * 0.6f),
                    shape = CircleShape
                )
        )
    }
}

/* ================================================================
 * DIVIDER TIPIS ANTAR BARIS (garis kaca, bukan garis UI biasa)
 * ================================================================ */

@Composable
private fun VfdDivider() {

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.6.dp)
            .background(Color.White.copy(alpha = 0.06f))
    )
}

/* ================================================================
 * HELPER: bentuk "hantu segmen" dari label (semua karakter jadi
 * blok padat redup di belakang teks, meniru sel dot-matrix mati)
 * ================================================================ */

private fun ghostFor(label: String): String =
    label.map { c -> if (c == ' ') ' ' else '█' }.joinToString("")

/* ================================================================
 * HELPER: sub-label baris Hi-Res -- "24BIT / 96K" saat menyala,
 * "44.1K" saat standar CD, "--" saat belum ada lagu (sampleRate 0).
 * ================================================================ */

@Composable
private fun hiResSubLabel(isHiRes: Boolean, sampleRateHz: Int, bitDepth: Int): String {
    if (sampleRateHz <= 0) return stringResource(R.string.vfd_dash_placeholder)

    val khz = sampleRateHz / 1000f
    val khzLabel = if (khz == khz.toInt().toFloat()) {
        "${khz.toInt()}K"
    } else {
        "%.1fK".format(khz)
    }

    return if (isHiRes) "${bitDepth}BIT / $khzLabel" else khzLabel
}
