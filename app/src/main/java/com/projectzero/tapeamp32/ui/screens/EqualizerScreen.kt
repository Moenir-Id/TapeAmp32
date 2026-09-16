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

// ================================================================
// EQUALIZER SCREEN - MAIN ENTRY POINT
// ================================================================

/**
 * Sub-menu di dalam layar Equalizer. Reverb SENGAJA tidak dibuat sub-menu di sini --
 * bukan bagian dari scope EQ, jadi diabaikan sepenuhnya:
 *   - EQU    : grafik 10-band parametric EQ (panel utama, sudah ada sebelumnya)
 *   - NADA   : preamp + quick tone (Bass/Treble) -- kontrol cepat tanpa harus geser
 *              semua 10 slider satu-satu
 *   - VOCAL  : BARU (patch "DSP control knobs") -- tombol VOCAL (on/off) + knop bass &
 *              treble khusus vokal, terpisah dari 10-band graphic EQ di tab EQU.
 *   - STEREO : BARU (patch "DSP control knobs") -- knop Balance, knop Stereo Expansion,
 *              dan tombol Mono/Stereo, menyediakan sub-menu image stereo tambahan.
 *   - BATAS  : kontrol "batas" sinyal -- toggle soft-limiter & EQ bypass, plus info
 *              ambang batas limiter yang sedang aktif di DSP
 */
internal enum class EqSubTab(val labelRes: Int) {
    EQU(R.string.eq_tab_equ),
    NADA(R.string.eq_tab_tone),
    VOCAL(R.string.eq_tab_vocal),
    STEREO(R.string.eq_tab_stereo),
    BATAS(R.string.eq_tab_limit)
}

/**
 * Gunakan nama unik jika ada konflik dengan file lain.
 * Atau hapus fungsi duplikat di file lain.
 */
@Composable
fun EqualizerScreen(
    vm: PlayerViewModel,
    onSavePreset: () -> Unit = {},
    onUploadPreset: () -> Unit = {},
    // BARU (v1.2): export preset EQ aktif ke file JSON (format kompatibel dengan aplikasi EQ populer lain,
    // sama seperti hasil UPLOAD) lewat SAF "Save As" -- lihat MainActivity.exportPresetLauncher.
    onExportPreset: () -> Unit = {}
) {
    val preset by vm.activePreset.collectAsStateWithLifecycle()
    val presets by vm.presets.collectAsStateWithLifecycle()
    // BARU (fitur "hapus preset"): dipakai untuk membedakan preset custom (hasil
    // SAVE/UPLOAD, boleh dihapus) dari preset bawaan (Flat/Rock/Pop/Jazz/Bass Boost/
    // Vocal, TIDAK boleh dihapus) di daftar dropdown preset.
    val customPresets by vm.customPresets.collectAsStateWithLifecycle()
    val customPresetNames = customPresets.map { it.name }.toSet()
    val vuLevels by vm.vuLevels.collectAsStateWithLifecycle()
    val limiterOn by vm.limiterOn.collectAsStateWithLifecycle()
    val eqBypassOn by vm.eqBypassOn.collectAsStateWithLifecycle()
    // BARU (v1.6)
    val bitPerfectOn by vm.bitPerfectOn.collectAsStateWithLifecycle()
    // BARU (v1.8): status offload hardware AKTUAL -- lihat "JUJUR diakui" di
    // changelog v1.6, sekarang ditampilkan real-time di bawah toggle BIT-PERFECT
    // MODE (dan di Status Panel VFD), bukan cuma lewat adb logcat.
    val offloadActive by vm.offloadActive.collectAsStateWithLifecycle()

    // BARU (patch "Crossfade")
    val crossfadeOn by vm.crossfadeOn.collectAsStateWithLifecycle()
    val crossfadeSeconds by vm.crossfadeSeconds.collectAsStateWithLifecycle()

    // BARU (v1.7): EQ per-lagu otomatis
    val autoEqPerSong by vm.autoEqPerSong.collectAsStateWithLifecycle()
    val currentSongSavedPresetName by vm.currentSongSavedPresetName.collectAsStateWithLifecycle()

    // BARU (v1.9): REPLAY GAIN beneran
    val replayGainOn by vm.replayGainOn.collectAsStateWithLifecycle()
    val currentSongReplayGainDb by vm.currentSongReplayGainDb.collectAsStateWithLifecycle()

    // BARU (patch "DSP control knobs"): state untuk tab VOCAL & STEREO.
    val vocalOn by vm.vocalOn.collectAsStateWithLifecycle()
    val vocalBassDb by vm.vocalBassDb.collectAsStateWithLifecycle()
    val vocalTrebleDb by vm.vocalTrebleDb.collectAsStateWithLifecycle()
    val stereoBalance by vm.stereoBalance.collectAsStateWithLifecycle()
    val stereoExpansion by vm.stereoExpansion.collectAsStateWithLifecycle()
    val monoStereoOn by vm.monoStereoOn.collectAsStateWithLifecycle()

    // BARU (patch "headroom slider")
    val headroomSafetyRatio by vm.headroomSafetyRatio.collectAsStateWithLifecycle()

    val vuL = vuLevels.first
    val vuR = vuLevels.second

    // ============================================================
    // PATCH (v1.6, "proporsional ke layar"): sebelumnya knop (EqKnob)
    // selalu 84dp dan lebar slider (EqSlider) selalu 26dp -- angka dp
    // mentah yang sama persis di HP kecil maupun HP/tablet lebar,
    // jadi kelihatan mungil di layar besar dan bisa mepet di layar
    // sempit. Sekarang keduanya ikut skala lebar layar (referensi
    // 360dp, lebar HP umum saat 84dp/26dp itu didesain), dibatasi
    // coerceIn supaya tetap nyaman disentuh di HP sempit dan tidak
    // membesar berlebihan di layar lebar.
    // ============================================================
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val eqScale = (screenWidthDp / 360.dp).coerceIn(0.85f, 1.35f)
    val eqKnobSize = (84.dp * eqScale).coerceIn(72.dp, 112.dp)
    val eqSliderTrackWidth = (26.dp * eqScale).coerceIn(22.dp, 34.dp)

    var menuOpen by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveNameInput by remember { mutableStateOf("") }
    // BARU (fitur "hapus preset"): preset custom yang sedang dikonfirmasi untuk
    // dihapus (null = tidak ada dialog konfirmasi yang tampil).
    var presetPendingDelete by remember { mutableStateOf<EqPreset?>(null) }
    var subTab by remember { mutableStateOf(EqSubTab.EQU) }

    // ============================================================
    // ROOT COLUMN
    // ============================================================

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBlack)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {

        // ========================================================
        // TOP HEADER
        // ========================================================

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

        // ========================================================
        // SUB-MENU TABS (EQU / NADA / BATAS)
        // ========================================================

        EqSubTabRow(
            selected = subTab,
            onSelect = { subTab = it }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ========================================================
        // MAIN EQ PANEL
        // ========================================================

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // ====================================================
            // EQ MAIN PANEL
            // ====================================================

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

                    // ============================================
                    // TAB: EQU -- grafik 10-band (perilaku lama,
                    // tidak diubah)
                    // ============================================
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

                    // ============================================
                    // TAB: NADA -- preamp + quick bass/treble.
                    // Bass/Treble di sini adalah shortcut yang
                    // langsung menulis ke band 0 (low-shelf) dan
                    // band terakhir (high-shelf) di preset yang
                    // sama dipakai tab EQU -- bukan jalur DSP
                    // terpisah, jadi kedua tab selalu konsisten.
                    // ============================================
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

                    // ============================================
                    // TAB: VOCAL -- BARU (patch "DSP control knobs").
                    // Tombol VOCAL (on/off) + knop bass & treble khusus
                    // vokal, diproses lewat filter TERPISAH dari 10-band
                    // graphic EQ (lihat ParametricEqAudioProcessor) --
                    // slider di tab EQU/NADA tidak ikut bergerak.
                    // ============================================
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

                    // ============================================
                    // TAB: STEREO -- BARU (patch "DSP control knobs").
                    // Knop Balance, knop Stereo Expansion, dan tombol
                    // Mono/Stereo. Berdiri sendiri di luar jalur tone
                    // control (tetap aktif walau EQ BYPASS menyala --
                    // lihat ParametricEqAudioProcessor).
                    // ============================================
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

                    // ============================================
                    // TAB: BATAS -- soft-limiter & EQ bypass.
                    // Nilai ambang batas limiter (-0.9 dBFS,
                    // stereo-linked soft-knee) sudah tetap di
                    // ParametricEqAudioProcessor -- di sini cuma
                    // menampilkan status & saklar on/off-nya,
                    // tidak berpura-pura ada slider yang tidak
                    // benar-benar terhubung ke DSP.
                    //
                    // PATCH (headroom slider): pengecualiannya
                    // adalah slider MAX LOUDNESS <-> SAFE HEADROOM
                    // di bawah -- itu BENAR-BENAR terhubung ke
                    // ParametricEqAudioProcessor.headroomSafetyRatio,
                    // beda dari limiter threshold di atas yang
                    // memang sengaja tetap (fixed).
                    // ============================================
                    EqSubTab.BATAS -> {
                        EqLimiterSubMenu(
                            limiterOn = limiterOn,
                            eqBypassOn = eqBypassOn,
                            bitPerfectOn = bitPerfectOn,
                            // BARU (v1.8)
                            offloadActive = offloadActive,
                            onLimiterToggle = { vm.setLimiterEnabled(!limiterOn) },
                            onBypassToggle = { vm.setEqBypass(!eqBypassOn) },
                            onBitPerfectToggle = { vm.setBitPerfectMode(!bitPerfectOn) },
                            // BARU (v1.7)
                            autoEqPerSong = autoEqPerSong,
                            currentSongSavedPresetName = currentSongSavedPresetName,
                            onAutoEqToggle = { vm.setAutoEqPerSong(!autoEqPerSong) },
                            onForgetSongPreset = { vm.forgetEqForCurrentSong() },
                            // BARU (v1.9)
                            replayGainOn = replayGainOn,
                            currentSongReplayGainDb = currentSongReplayGainDb,
                            onReplayGainToggle = { vm.setReplayGainOn(!replayGainOn) },
                            onForgetReplayGain = { vm.forgetReplayGainForCurrentSong() },
                            // BARU (patch "Crossfade")
                            crossfadeOn = crossfadeOn,
                            crossfadeSeconds = crossfadeSeconds,
                            onCrossfadeToggle = { vm.setCrossfadeEnabled(!crossfadeOn) },
                            onCrossfadeSecondsChange = { vm.setCrossfadeSeconds(it) },
                            // BARU (patch "headroom slider")
                            headroomSafetyRatio = headroomSafetyRatio,
                            onHeadroomSafetyRatioChange = { vm.updateHeadroomSafetyRatio(it) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    }
                }
            }

            // ====================================================
            // VU METER PANEL
            // ====================================================

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

    // ============================================================
    // SAVE DIALOG
    // ============================================================

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

    // ============================================================
    // DELETE PRESET DIALOG -- BARU (fitur "hapus preset")
    // ============================================================

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

// ================================================================
// SUB-MENU TAB ROW (EQU / NADA / BATAS)
// ================================================================
