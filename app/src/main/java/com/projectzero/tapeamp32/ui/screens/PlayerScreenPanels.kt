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

        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            PowerSwitch(
                on = powerOn,
                onToggle = onPowerToggle
            )

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

@Composable
internal fun PlayerTrackBar(
    position: Long,
    duration: Long,
    progressFraction: Float,
    format: String,
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween,
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(
                text = stringResource(R.string.player_track_format_info, format),
                color = TextMuted,
                fontFamily = MonoFont,
                fontSize = 8.sp,
                maxLines = 1
            )

            PlayerBadge(
                text = stringResource(R.string.player_badge_hires)
            )
        }
    }
}

@Composable
internal fun PlayerBadge(
    text: String
) {

    Text(
        text = text,
        color = BgBlack,
        fontFamily = MonoFont,
        fontWeight = FontWeight.Bold,
        fontSize = 7.sp,
        modifier = Modifier
            .clip(
                RoundedCornerShape(2.dp)
            )
            .background(
                GoldBright
            )
            .border(
                width = 0.7.dp,
                color = GoldBright,
                shape = RoundedCornerShape(2.dp)
            )
            .padding(
                horizontal = 5.dp,
                vertical = 2.dp
            )
    )
}

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
