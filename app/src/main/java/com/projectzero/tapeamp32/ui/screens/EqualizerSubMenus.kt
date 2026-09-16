package com.projectzero.tapeamp32.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectzero.tapeamp32.R
import com.projectzero.tapeamp32.ui.theme.BgBlack
import com.projectzero.tapeamp32.ui.theme.GoldBright
import com.projectzero.tapeamp32.ui.theme.MonoFont
import com.projectzero.tapeamp32.ui.theme.PanelBlackAlt
import com.projectzero.tapeamp32.ui.theme.StrokeGold
import com.projectzero.tapeamp32.ui.theme.TextMuted
import com.projectzero.tapeamp32.data.EqPreset

@Composable
internal fun EqSubTabRow(
    selected: EqSubTab,
    onSelect: (EqSubTab) -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        EqSubTab.values().forEach { tab ->
            val isSelected = tab == selected
            Text(
                text = stringResource(tab.labelRes),
                color = if (isSelected) BgBlack else GoldBright,
                fontFamily = MonoFont,
                fontWeight = FontWeight.Bold,

                fontSize = 8.sp,
                letterSpacing = 0.35.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isSelected) GoldBright else PanelBlackAlt)
                    .border(
                        width = 1.dp,
                        color = StrokeGold,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
internal fun EqToneSubMenu(
    preset: EqPreset,
    onPreampChange: (Double) -> Unit,
    onBassChange: (Double) -> Unit,
    onTrebleChange: (Double) -> Unit,
    sliderTrackWidth: androidx.compose.ui.unit.Dp = 26.dp,
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
                text = stringResource(R.string.eq_tab_tone),
                color = GoldBright,
                fontFamily = MonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 0.4.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = stringResource(R.string.eq_tone_subtitle),
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
            EqSlider(
                frequency = stringResource(R.string.eq_preamp_slider_label),
                gain = preset.preamp,
                onGainChange = onPreampChange,
                trackWidth = sliderTrackWidth,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            EqSlider(
                frequency = stringResource(R.string.eq_bass_slider_label),
                gain = preset.bands.firstOrNull()?.gain ?: 0.0,
                onGainChange = onBassChange,
                trackWidth = sliderTrackWidth,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            EqSlider(
                frequency = stringResource(R.string.eq_treble_slider_label),
                gain = preset.bands.lastOrNull()?.gain ?: 0.0,
                onGainChange = onTrebleChange,
                trackWidth = sliderTrackWidth,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        Spacer(modifier = Modifier.height(7.dp))

        Text(
            text = stringResource(R.string.eq_tone_description),
            color = TextMuted,
            fontFamily = MonoFont,
            fontSize = 7.sp
        )
    }
}

@Composable
internal fun EqLimiterSubMenu(
    limiterOn: Boolean,
    eqBypassOn: Boolean,
    bitPerfectOn: Boolean,

    offloadActive: Boolean,
    onLimiterToggle: () -> Unit,
    onBypassToggle: () -> Unit,
    onBitPerfectToggle: () -> Unit,

    autoEqPerSong: Boolean,
    currentSongSavedPresetName: String?,
    onAutoEqToggle: () -> Unit,
    onForgetSongPreset: () -> Unit,

    replayGainOn: Boolean,
    currentSongReplayGainDb: Double?,
    onReplayGainToggle: () -> Unit,
    onForgetReplayGain: () -> Unit,

    crossfadeOn: Boolean,
    crossfadeSeconds: Float,
    onCrossfadeToggle: () -> Unit,
    onCrossfadeSecondsChange: (Float) -> Unit,

    headroomSafetyRatio: Double,
    onHeadroomSafetyRatioChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) {

    Column(
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(23.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.eq_tab_limit),
                color = GoldBright,
                fontFamily = MonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 0.4.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = stringResource(R.string.eq_limit_subtitle),
                color = TextMuted,
                fontFamily = MonoFont,
                fontSize = 7.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        EqToggleRow(
            label = stringResource(R.string.eq_soft_limiter_label),
            description = stringResource(R.string.eq_soft_limiter_desc),
            isOn = limiterOn,
            onToggle = onLimiterToggle
        )

        Spacer(modifier = Modifier.height(10.dp))

        EqHeadroomSafetySlider(
            value = headroomSafetyRatio,
            onValueChange = onHeadroomSafetyRatioChange
        )

        Spacer(modifier = Modifier.height(10.dp))

        EqToggleRow(
            label = stringResource(R.string.eq_bypass_label),
            description = stringResource(R.string.eq_bypass_desc),
            isOn = eqBypassOn,
            onToggle = onBypassToggle
        )

        Spacer(modifier = Modifier.height(10.dp))

        EqToggleRow(
            label = stringResource(R.string.eq_bitperfect_label),
            description = stringResource(R.string.eq_bitperfect_desc),
            isOn = bitPerfectOn,
            onToggle = onBitPerfectToggle,

            statusContent = if (bitPerfectOn) {
                { EqOffloadStatusRow(offloadActive = offloadActive) }
            } else {
                null
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        EqToggleRow(
            label = stringResource(R.string.eq_autoeq_label),
            description = stringResource(R.string.eq_autoeq_desc),
            isOn = autoEqPerSong,
            onToggle = onAutoEqToggle,
            statusContent = if (autoEqPerSong) {
                {
                    EqAutoStatusRow(
                        savedPresetName = currentSongSavedPresetName,
                        onForget = onForgetSongPreset
                    )
                }
            } else {
                null
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        EqToggleRow(
            label = stringResource(R.string.eq_replaygain_label),
            description = stringResource(R.string.eq_replaygain_desc),
            isOn = replayGainOn,
            onToggle = onReplayGainToggle,
            statusContent = if (replayGainOn) {
                {
                    EqReplayGainStatusRow(
                        gainDb = currentSongReplayGainDb,
                        onForget = onForgetReplayGain
                    )
                }
            } else {
                null
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        EqToggleRow(
            label = stringResource(R.string.eq_crossfade_label),
            description = stringResource(R.string.eq_crossfade_desc),
            isOn = crossfadeOn,
            onToggle = onCrossfadeToggle,
            statusContent = if (crossfadeOn) {
                { EqCrossfadeDurationRow(seconds = crossfadeSeconds, onChange = onCrossfadeSecondsChange) }
            } else {
                null
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}
