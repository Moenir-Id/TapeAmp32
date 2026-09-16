package com.projectzero.tapeamp32.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.projectzero.tapeamp32.R
import com.projectzero.tapeamp32.ui.components.VuMeter
import com.projectzero.tapeamp32.ui.theme.BgBlack
import com.projectzero.tapeamp32.ui.theme.GoldBright
import com.projectzero.tapeamp32.ui.theme.MonoFont
import com.projectzero.tapeamp32.ui.theme.PanelBlack
import com.projectzero.tapeamp32.ui.theme.StrokeGold
import com.projectzero.tapeamp32.ui.theme.TextMuted
import com.projectzero.tapeamp32.viewmodel.PlayerViewModel
import com.projectzero.tapeamp32.data.EqPreset

internal enum class EqSubTab(val labelRes: Int) {
    EQU(R.string.eq_tab_equ),
    NADA(R.string.eq_tab_tone),
    VOCAL(R.string.eq_tab_vocal),
    STEREO(R.string.eq_tab_stereo),
    BATAS(R.string.eq_tab_limit)
}

@Composable
fun EqualizerScreen(
    vm: PlayerViewModel,
    onSavePreset: () -> Unit = {},
    onUploadPreset: () -> Unit = {},

    onExportPreset: () -> Unit = {}
) {
    val preset by vm.activePreset.collectAsStateWithLifecycle()
    val presets by vm.presets.collectAsStateWithLifecycle()

    val customPresets by vm.customPresets.collectAsStateWithLifecycle()
    val customPresetNames = customPresets.map { it.name }.toSet()
    val vuLevels by vm.vuLevels.collectAsStateWithLifecycle()
    val limiterOn by vm.limiterOn.collectAsStateWithLifecycle()
    val eqBypassOn by vm.eqBypassOn.collectAsStateWithLifecycle()

    val bitPerfectOn by vm.bitPerfectOn.collectAsStateWithLifecycle()

    val offloadActive by vm.offloadActive.collectAsStateWithLifecycle()

    val crossfadeOn by vm.crossfadeOn.collectAsStateWithLifecycle()
    val crossfadeSeconds by vm.crossfadeSeconds.collectAsStateWithLifecycle()

    val autoEqPerSong by vm.autoEqPerSong.collectAsStateWithLifecycle()
    val currentSongSavedPresetName by vm.currentSongSavedPresetName.collectAsStateWithLifecycle()

    val replayGainOn by vm.replayGainOn.collectAsStateWithLifecycle()
    val currentSongReplayGainDb by vm.currentSongReplayGainDb.collectAsStateWithLifecycle()

    val vocalOn by vm.vocalOn.collectAsStateWithLifecycle()
    val vocalBassDb by vm.vocalBassDb.collectAsStateWithLifecycle()
    val vocalTrebleDb by vm.vocalTrebleDb.collectAsStateWithLifecycle()
    val stereoBalance by vm.stereoBalance.collectAsStateWithLifecycle()
    val stereoExpansion by vm.stereoExpansion.collectAsStateWithLifecycle()
    val monoStereoOn by vm.monoStereoOn.collectAsStateWithLifecycle()

    val headroomSafetyRatio by vm.headroomSafetyRatio.collectAsStateWithLifecycle()

    val vuL = vuLevels.first
    val vuR = vuLevels.second

    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val eqScale = (screenWidthDp / 360.dp).coerceIn(0.85f, 1.35f)
    val eqKnobSize = (84.dp * eqScale).coerceIn(72.dp, 112.dp)
    val eqSliderTrackWidth = (26.dp * eqScale).coerceIn(22.dp, 34.dp)

    var menuOpen by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveNameInput by remember { mutableStateOf("") }

    var presetPendingDelete by remember { mutableStateOf<EqPreset?>(null) }
    var subTab by remember { mutableStateOf(EqSubTab.EQU) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBlack)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {

        EqualizerHeader(
            presetName = preset.name,
            presets = presets,
            customPresetNames = customPresetNames,
            menuOpen = menuOpen,
            onMenuOpen = { menuOpen = true },
            onMenuDismiss = { menuOpen = false },
            onPresetSelected = { selected ->
                vm.applyPreset(selected)
                menuOpen = false
            },
            onDeleteRequest = { selected ->
                presetPendingDelete = selected
                menuOpen = false
            },
            onSave = {
                saveNameInput = preset.name.takeIf { it != "Custom" } ?: ""
                showSaveDialog = true
                onSavePreset()
            },
            onUpload = onUploadPreset,
            onExport = onExportPreset
        )

        Spacer(modifier = Modifier.height(8.dp))

        EqSubTabRow(
            selected = subTab,
            onSelect = { subTab = it }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(7.dp))
                    .background(PanelBlack)
                    .border(
                        width = 1.dp,
                        color = StrokeGold,
                        shape = RoundedCornerShape(7.dp)
                    )
                    .padding(horizontal = 9.dp, vertical = 8.dp)
            ) {
                when (subTab) {

                    EqSubTab.EQU -> {

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(23.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.eq_graphic_equalizer_header),
                                color = GoldBright,
                                fontFamily = MonoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                letterSpacing = 0.4.sp
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            Text(
                                text = stringResource(R.string.eq_10_band),
                                color = TextMuted,
                                fontFamily = MonoFont,
                                fontSize = 7.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        EqualizerSliderPanel(
                            preset = preset,
                            onGainChange = { index, value ->
                                vm.updateBandGain(index, value)
                            },
                            sliderTrackWidth = eqSliderTrackWidth,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(7.dp))

                        FrequencyCurveWithScale(
                            gains = preset.bands.map { it.gain },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(88.dp)
                        )
                    }

                    EqSubTab.NADA -> {
                        EqToneSubMenu(
                            preset = preset,
                            onPreampChange = { vm.updatePreampGain(it) },
                            onBassChange = { vm.updateBandGain(0, it) },
                            onTrebleChange = { vm.updateBandGain(preset.bands.lastIndex, it) },
                            sliderTrackWidth = eqSliderTrackWidth,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    }

                    EqSubTab.VOCAL -> {
                        EqVocalSubMenu(
                            vocalOn = vocalOn,
                            vocalBass = vocalBassDb,
                            vocalTreble = vocalTrebleDb,
                            onVocalToggle = { vm.setVocalEnabled(!vocalOn) },
                            onBassChange = { vm.updateVocalBass(it) },
                            onTrebleChange = { vm.updateVocalTreble(it) },
                            knobSize = eqKnobSize,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    }

                    EqSubTab.STEREO -> {
                        EqStereoSubMenu(
                            balance = stereoBalance,
                            stereoExpansion = stereoExpansion,
                            monoOn = monoStereoOn,
                            onBalanceChange = { vm.updateStereoBalance(it) },
                            onExpansionChange = { vm.updateStereoExpansion(it) },
                            onMonoToggle = { vm.setMonoStereo(!monoStereoOn) },
                            knobSize = eqKnobSize,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    }

                    EqSubTab.BATAS -> {
                        EqLimiterSubMenu(
                            limiterOn = limiterOn,
                            eqBypassOn = eqBypassOn,
                            bitPerfectOn = bitPerfectOn,

                            offloadActive = offloadActive,
                            onLimiterToggle = { vm.setLimiterEnabled(!limiterOn) },
                            onBypassToggle = { vm.setEqBypass(!eqBypassOn) },
                            onBitPerfectToggle = { vm.setBitPerfectMode(!bitPerfectOn) },

                            autoEqPerSong = autoEqPerSong,
                            currentSongSavedPresetName = currentSongSavedPresetName,
                            onAutoEqToggle = { vm.setAutoEqPerSong(!autoEqPerSong) },
                            onForgetSongPreset = { vm.forgetEqForCurrentSong() },

                            replayGainOn = replayGainOn,
                            currentSongReplayGainDb = currentSongReplayGainDb,
                            onReplayGainToggle = { vm.setReplayGainOn(!replayGainOn) },
                            onForgetReplayGain = { vm.forgetReplayGainForCurrentSong() },

                            crossfadeOn = crossfadeOn,
                            crossfadeSeconds = crossfadeSeconds,
                            onCrossfadeToggle = { vm.setCrossfadeEnabled(!crossfadeOn) },
                            onCrossfadeSecondsChange = { vm.setCrossfadeSeconds(it) },

                            headroomSafetyRatio = headroomSafetyRatio,
                            onHeadroomSafetyRatioChange = { vm.updateHeadroomSafetyRatio(it) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .width(76.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(7.dp))
                    .background(PanelBlack)
                    .border(
                        width = 1.dp,
                        color = StrokeGold,
                        shape = RoundedCornerShape(7.dp)
                    )
                    .padding(5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.eq_vu_level),
                    color = GoldBright,
                    fontFamily = MonoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.sp
                )

                Spacer(modifier = Modifier.height(5.dp))

                VuMeter(
                    left = vuL,
                    right = vuR,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.eq_lr_label),
                    color = TextMuted,
                    fontFamily = MonoFont,
                    fontSize = 7.sp
                )
            }
        }
    }

    if (showSaveDialog) {
        SavePresetDialog(
            saveNameInput = saveNameInput,
            onNameChange = { saveNameInput = it },
            onDismiss = { showSaveDialog = false },
            onConfirm = {
                if (saveNameInput.isNotBlank()) {
                    vm.saveCustomPreset(saveNameInput)
                    showSaveDialog = false
                }
            }
        )
    }

    presetPendingDelete?.let { target ->
        DeletePresetDialog(
            presetName = target.name,
            onDismiss = { presetPendingDelete = null },
            onConfirm = {
                vm.deleteCustomPreset(target.name)
                presetPendingDelete = null
            }
        )
    }
}
