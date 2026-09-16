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

@Composable
fun VfdStatusPanel(
    isUsbDacConnected: Boolean,
    dacLabel: String?,
    dspEngineOn: Boolean,
    peakActive: Boolean,

    isHiRes: Boolean = false,
    sampleRate: Int = 0,
    bitDepth: Int = 16,

    bitPerfectOn: Boolean = false,
    offloadActive: Boolean = false,

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

            VfdLine(
                lit = isHiRes,
                mainLabel = if (isHiRes) stringResource(R.string.vfd_hires) else stringResource(R.string.vfd_std),
                subLabel = hiResSubLabel(isHiRes, sampleRate, bitDepth),
                litColor = VfdCyanHiRes,
                dimColor = VfdCyanHiResDim
            )

            VfdDivider()

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

@Composable
private fun LedDot(
    color: Color,
    glowAlpha: Float
) {

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(9.dp)
    ) {

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

@Composable
private fun VfdDivider() {

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.6.dp)
            .background(Color.White.copy(alpha = 0.06f))
    )
}

private fun ghostFor(label: String): String =
    label.map { c -> if (c == ' ') ' ' else '█' }.joinToString("")

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
