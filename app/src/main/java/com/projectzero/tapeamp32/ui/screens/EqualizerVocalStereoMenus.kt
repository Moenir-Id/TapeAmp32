package com.projectzero.tapeamp32.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectzero.tapeamp32.R
import com.projectzero.tapeamp32.ui.theme.BgBlack
import com.projectzero.tapeamp32.ui.theme.Gold
import com.projectzero.tapeamp32.ui.theme.GoldBright
import com.projectzero.tapeamp32.ui.theme.MonoFont
import com.projectzero.tapeamp32.ui.theme.PanelBlackAlt
import com.projectzero.tapeamp32.ui.theme.StrokeGold
import com.projectzero.tapeamp32.ui.theme.TextLight
import com.projectzero.tapeamp32.ui.theme.TextMuted
import kotlin.math.roundToInt

@Composable
internal fun EqAutoStatusRow(
    savedPresetName: String?,
    onForget: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (savedPresetName != null) {
                stringResource(R.string.eq_song_preset_saved, savedPresetName)
            } else {
                stringResource(R.string.eq_song_no_preset)
            },
            color = TextMuted,
            fontFamily = MonoFont,
            fontSize = 7.sp,
            modifier = Modifier.weight(1f)
        )

        if (savedPresetName != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.eq_delete_short),
                color = GoldBright,
                fontFamily = MonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 7.sp,
                modifier = Modifier.clickable { onForget() }
            )
        }
    }
}

@Composable
internal fun EqReplayGainStatusRow(
    gainDb: Double?,
    onForget: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (gainDb != null) {
                val sign = if (gainDb >= 0.0) "+" else ""
                stringResource(R.string.eq_song_replaygain_saved, "$sign${"%.1f".format(gainDb)}")
            } else {
                stringResource(R.string.eq_song_replaygain_measuring)
            },
            color = TextMuted,
            fontFamily = MonoFont,
            fontSize = 7.sp,
            modifier = Modifier.weight(1f)
        )

        if (gainDb != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.eq_delete_short),
                color = GoldBright,
                fontFamily = MonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 7.sp,
                modifier = Modifier.clickable { onForget() }
            )
        }
    }
}

@Composable
internal fun EqCrossfadeDurationRow(
    seconds: Float,
    onChange: (Float) -> Unit
) {
    val options = listOf(2f, 4f, 6f, 8f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.eq_crossfade_duration_label),
            color = TextMuted,
            fontFamily = MonoFont,
            fontSize = 7.sp
        )
        Spacer(modifier = Modifier.width(6.dp))
        options.forEach { opt ->
            val active = kotlin.math.abs(seconds - opt) < 0.01f
            Text(
                text = stringResource(R.string.eq_crossfade_seconds_unit, opt.toInt()),
                color = if (active) GoldBright else TextMuted,
                fontFamily = MonoFont,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                fontSize = 7.sp,
                modifier = Modifier
                    .clickable { onChange(opt) }
                    .padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
internal fun EqOffloadStatusRow(offloadActive: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(50))
                .background(if (offloadActive) GoldBright else Color(0xFF5C5C5C))
        )

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = if (offloadActive) {
                stringResource(R.string.eq_offload_active)
            } else {
                stringResource(R.string.eq_offload_inactive)
            },
            color = if (offloadActive) GoldBright else TextMuted,
            fontFamily = MonoFont,
            fontSize = 7.sp
        )
    }
}

@Composable
internal fun EqToggleRow(
    label: String,
    description: String,
    isOn: Boolean,
    onToggle: () -> Unit,
    statusContent: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(PanelBlackAlt)
            .border(
                width = 1.dp,
                color = StrokeGold,
                shape = RoundedCornerShape(6.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }

                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = TextLight,
                    fontFamily = MonoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.5.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = description,
                    color = TextMuted,
                    fontFamily = MonoFont,

                    fontSize = 7.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            EqRockerSwitch(
                isOn = isOn,
                onToggle = onToggle
            )
        }

        if (statusContent != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.6.dp)
                    .background(StrokeGold.copy(alpha = 0.25f))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()

                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                statusContent()
            }
        }
    }
}

@Composable
internal fun EqHeadroomSafetySlider(
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val percent = (value * 100.0).roundToInt().coerceIn(0, 100)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(PanelBlackAlt)
            .border(
                width = 1.dp,
                color = StrokeGold,
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.eq_headroom_safety_label),
                color = TextLight,
                fontFamily = MonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 8.5.sp,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = "$percent%",
                color = GoldBright,
                fontFamily = MonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        ClassicHorizontalSlider(
            value = percent.toFloat(),
            onValueChange = { newPercent ->

                onValueChange(newPercent / 100.0)
            },
            range = 0f..100f,
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(R.string.eq_headroom_safety_desc),
            color = TextMuted,
            fontFamily = MonoFont,
            fontSize = 7.sp
        )
    }
}

@Composable
internal fun EqRockerSwitch(
    isOn: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val trackWidth = 56.dp
    val trackHeight = 30.dp
    val knobSize = 24.dp
    val travel = trackWidth - knobSize - 4.dp

    Box(
        modifier = modifier
            .size(width = trackWidth, height = trackHeight)
            .clip(RoundedCornerShape(15.dp))
            .background(
                Brush.verticalGradient(
                    if (isOn) {
                        listOf(Color(0xFF3A2E10), Color(0xFF1C1608))
                    } else {
                        listOf(Color(0xFF0E0E0E), Color(0xFF050505))
                    }
                )
            )
            .border(
                width = 1.2.dp,
                color = if (isOn) GoldBright else Color(0xFF3A3A3A),
                shape = RoundedCornerShape(15.dp)
            )
            .clickable { onToggle() },
        contentAlignment = Alignment.CenterStart
    ) {

        Text(
            text = stringResource(R.string.eq_rocker_off),
            color = if (!isOn) TextMuted else Color(0xFF2A2A2A),
            fontFamily = MonoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 6.sp,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 5.dp)
        )
        Text(
            text = stringResource(R.string.eq_rocker_on),
            color = if (isOn) BgBlack else Color(0xFF2A2A2A),
            fontFamily = MonoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 6.sp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp)
        )

        Box(
            modifier = Modifier
                .padding(start = 2.dp)
                .offset(x = if (isOn) travel else 0.dp)
                .size(knobSize)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.verticalGradient(
                        if (isOn) {
                            listOf(GoldBright, Gold, Color(0xFF7D5F17))
                        } else {
                            listOf(Color(0xFFC7C7C7), Color(0xFF8E8E8E), Color(0xFF5C5C5C))
                        }
                    )
                )
                .border(
                    width = 1.dp,
                    color = Color(0xFF2A2A2A),
                    shape = RoundedCornerShape(50)
                )
        )
    }
}

@Composable
internal fun EqVocalSubMenu(
    vocalOn: Boolean,
    vocalBass: Double,
    vocalTreble: Double,
    onVocalToggle: () -> Unit,
    onBassChange: (Double) -> Unit,
    onTrebleChange: (Double) -> Unit,
    knobSize: androidx.compose.ui.unit.Dp = 84.dp,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(23.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.eq_tab_vocal),
                color = GoldBright,
                fontFamily = MonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 0.4.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = stringResource(R.string.eq_vocal_subtitle),
                color = TextMuted,
                fontFamily = MonoFont,
                fontSize = 7.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        EqToggleRow(
            label = stringResource(R.string.eq_tab_vocal),
            description = stringResource(R.string.eq_vocal_toggle_desc),
            isOn = vocalOn,
            onToggle = onVocalToggle
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            EqKnob(
                label = stringResource(R.string.eq_vocal_bass_knob),
                value = vocalBass,
                minValue = -12.0,
                maxValue = 12.0,
                centerValue = 0.0,
                valueLabel = formatSignedDb(vocalBass),
                onValueChange = onBassChange,
                knobSize = knobSize,
                modifier = Modifier.weight(1f)
            )
            EqKnob(
                label = stringResource(R.string.eq_vocal_treble_knob),
                value = vocalTreble,
                minValue = -12.0,
                maxValue = 12.0,
                centerValue = 0.0,
                valueLabel = formatSignedDb(vocalTreble),
                onValueChange = onTrebleChange,
                knobSize = knobSize,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(7.dp))

        Text(
            text = stringResource(R.string.eq_vocal_description),
            color = TextMuted,
            fontFamily = MonoFont,
            fontSize = 7.sp
        )
    }
}

@Composable
internal fun EqStereoSubMenu(
    balance: Double,
    stereoExpansion: Double,
    monoOn: Boolean,
    onBalanceChange: (Double) -> Unit,
    onExpansionChange: (Double) -> Unit,
    onMonoToggle: () -> Unit,
    knobSize: androidx.compose.ui.unit.Dp = 84.dp,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(23.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.eq_tab_stereo),
                color = GoldBright,
                fontFamily = MonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 0.4.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = stringResource(R.string.eq_stereo_subtitle),
                color = TextMuted,
                fontFamily = MonoFont,
                fontSize = 7.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            EqKnob(
                label = stringResource(R.string.eq_balance_knob),
                value = balance,
                minValue = -1.0,
                maxValue = 1.0,
                centerValue = 0.0,
                valueLabel = formatBalance(balance),
                onValueChange = onBalanceChange,
                knobSize = knobSize,
                modifier = Modifier.weight(1f)
            )
            EqKnob(
                label = stringResource(R.string.eq_stereo_expansion_knob),
                value = stereoExpansion,
                minValue = 0.0,
                maxValue = 2.0,
                centerValue = 1.0,
                valueLabel = "${(stereoExpansion * 100).roundToInt()}%",
                onValueChange = onExpansionChange,
                knobSize = knobSize,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        EqToggleRow(
            label = stringResource(R.string.eq_mono_stereo_label),
            description = stringResource(R.string.eq_mono_stereo_desc),
            isOn = monoOn,
            onToggle = onMonoToggle
        )

        Spacer(modifier = Modifier.height(7.dp))

        Text(
            text = stringResource(R.string.eq_stereo_description),
            color = TextMuted,
            fontFamily = MonoFont,
            fontSize = 7.sp
        )
    }
}

internal fun formatSignedDb(db: Double): String {
    val rounded = db.roundToInt()
    return if (rounded > 0) "+$rounded" else "$rounded"
}

internal fun formatBalance(balance: Double): String {
    val pct = kotlin.math.round(kotlin.math.abs(balance) * 100).toInt()
    return when {
        pct == 0 -> "C"
        balance < 0 -> "L$pct"
        else -> "R$pct"
    }
}
