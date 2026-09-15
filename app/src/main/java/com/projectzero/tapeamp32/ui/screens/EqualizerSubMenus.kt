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

// Sub-menu row composables for the Equalizer screen tabs (EQU tab tab-row, NADA, BATAS).
// Split out of EqualizerScreen.kt to keep that file focused on the screen's own layout/state.

@Composable
internal fun EqSubTabRow(
    selected: EqSubTab,
    onSelect: (EqSubTab) -> Unit
) {
    // BARU (patch "DSP control knobs"): dibungkus horizontalScroll -- sebelumnya cuma
    // 3 tab (EQU/NADA/BATAS) selalu muat, tapi sekarang ada 5 tab (+VOCAL/+STEREO)
    // yang bisa kesempitan di layar sempit. Row tetap terlihat identik di layar
    // lebar (tidak ada apa pun untuk digulir), cuma jadi aman digeser kalau perlu.
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
                // PATCH (v1.5, "seragamkan font"): disamakan dengan label tab
                // sejenis di layar lain (NavRail: 8sp/0.35sp) -- sebelumnya
                // 0.4sp di sini vs 0.35sp di NavRail vs 9sp/0.2sp di sidebar
                // Library, padahal ketiganya sama-sama "label tab".
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

// ================================================================
// SUB-MENU: NADA (PREAMP + QUICK BASS/TREBLE)
// ================================================================

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

// ================================================================
// SUB-MENU: BATAS (SOFT LIMITER & EQ BYPASS)
// ================================================================

@Composable
internal fun EqLimiterSubMenu(
    limiterOn: Boolean,
    eqBypassOn: Boolean,
    bitPerfectOn: Boolean,
    // BARU (v1.8): status offload hardware AKTUAL, ditampilkan sebagai status row
    // di dalam kartu toggle BIT-PERFECT MODE begitu toggle-nya aktif -- lihat
    // EqOffloadStatusRow di bawah.
    offloadActive: Boolean,
    onLimiterToggle: () -> Unit,
    onBypassToggle: () -> Unit,
    onBitPerfectToggle: () -> Unit,
    // BARU (v1.7): EQ per-lagu otomatis
    autoEqPerSong: Boolean,
    currentSongSavedPresetName: String?,
    onAutoEqToggle: () -> Unit,
    onForgetSongPreset: () -> Unit,
    // BARU (v1.9): REPLAY GAIN beneran
    replayGainOn: Boolean,
    currentSongReplayGainDb: Double?,
    onReplayGainToggle: () -> Unit,
    onForgetReplayGain: () -> Unit,
    // BARU (patch "Crossfade")
    crossfadeOn: Boolean,
    crossfadeSeconds: Float,
    onCrossfadeToggle: () -> Unit,
    onCrossfadeSecondsChange: (Float) -> Unit,
    // BARU (patch "headroom slider")
    headroomSafetyRatio: Double,
    onHeadroomSafetyRatioChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    // FIX (v1.8.1): Column ini sebelumnya TIDAK scrollable, padahal tinggi panel EQ
    // di sebelahnya (EQ MAIN PANEL) tetap/fillMaxHeight() -- di jendela/layar yang
    // lebih pendek (window desktop kecil, split-screen, atau HP dengan status/nav bar
    // besar), 4 kartu toggle + deskripsinya tidak selalu muat, dan kartu PALING BAWAH
    // (AUTO EQ PER LAGU) ke-CLIP oleh batas panel -- deskripsinya hilang dan rocker
    // switch-nya kepotong di tepi bawah, kelihatan seperti "ukurannya beda" padahal
    // sebenarnya semua toggle di sini sudah memakai EqToggleRow/EqRockerSwitch yang
    // identik (lihat di bawah) -- akar masalahnya konten submenu ini kelebihan
    // tinggi tanpa cara untuk di-scroll. verticalScroll() di bawah membuat seluruh
    // 4 kartu (dan status tambahan di dalamnya) selalu bisa digulir sampai terlihat
    // penuh, di layar setinggi apa pun.
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

        // BARU (patch "headroom slider"): satu-satunya kontrol kontinu di tab ini
        // yang BENAR-BENAR terhubung ke DSP -- lihat catatan lama di
        // EqualizerScreen.kt (di titik pemanggilan EqLimiterSubMenu) yang sengaja
        // TIDAK memasang slider apa pun sebelumnya karena nilai limiter threshold
        // memang tetap/tidak ada jalur ke DSP-nya. Beda dengan itu, slider ini
        // langsung mengubah ParametricEqAudioProcessor.headroomSafetyRatio lewat
        // PlayerViewModel.updateHeadroomSafetyRatio -> PlayerManager, dan
        // rebuildFilters() dipanggil ulang seketika di dalam prosesor supaya
        // preset EQ yang sedang aktif langsung terdengar berubah, bukan menunggu
        // ganti preset dulu.
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

        // BARU (v1.6): master override -- beda dari EQ BYPASS di atas, toggle ini
        // sekaligus melewati Vocal, Balance, Stereo Expansion, Mono/Stereo, DAN
        // Limiter (bukan cuma EQ 10-band) di sisi software, SEKALIGUS meminta jalur
        // AUDIO OFFLOAD hardware ke ExoPlayer (generik untuk device/USB DAC apa pun
        // -- bukan device tertentu). Kalau device/DAC/format lagu tidak mendukung
        // offload, ExoPlayer otomatis fallback diam-diam ke bypass software di atas
        // (tetap 100% berfungsi, cuma tanpa hardware direct-path tambahan).
        // PATCH (v1.8, "seragamkan ukuran toggle"): status tambahan (offload di sini,
        // preset tersimpan di AUTO EQ PER LAGU di bawah) sekarang dilewatkan lewat
        // parameter statusContent EqToggleRow, DIRENDER DI DALAM kartu toggle yang
        // sama (border + background + padding horizontal 12dp yang identik) alih-alih
        // sebagai baris teks lepas di bawahnya. Sebelumnya baris status AUTO EQ PER
        // LAGU cuma berinset 2dp tanpa kartu sendiri, jadi terlihat "nyempil" dan
        // membuat blok toggle itu terasa beda ukuran/proporsi dari SOFT LIMITER/EQ
        // BYPASS/BIT-PERFECT MODE di atasnya -- sekarang keempatnya konsisten satu
        // bahasa visual, tinggi kartu cuma beda kalau memang ada status ekstra untuk
        // ditampilkan (proporsional terhadap kontennya, bukan acak).
        EqToggleRow(
            label = stringResource(R.string.eq_bitperfect_label),
            description = stringResource(R.string.eq_bitperfect_desc),
            isOn = bitPerfectOn,
            onToggle = onBitPerfectToggle,
            // BARU (v1.8): status offload AKTUAL real-time -- lihat "JUJUR diakui"
            // di changelog v1.6, akhirnya ditampilkan di sini (bukan cuma adb logcat).
            statusContent = if (bitPerfectOn) {
                { EqOffloadStatusRow(offloadActive = offloadActive) }
            } else {
                null
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // BARU (v1.7): EQ per-lagu otomatis -- kalau nyala, preset yang dipilih manual
        // dari dropdown di atas otomatis "diingat" untuk lagu yang sedang diputar, lalu
        // dipasang lagi sendiri begitu lagu itu diputar ulang (next/prev, lockscreen,
        // atau replay), jadi tidak perlu ganti preset manual tiap pindah lagu.
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

        // BARU (v1.9): REPLAY GAIN beneran -- lihat changelog v1.9 untuk cerita
        // lengkapnya (dulu ada di kategori AUDIO & EFFECTS, dihapus di v1.4.1 karena
        // ternyata palsu). Sekarang mengukur RMS sample PCM asli lagu yang SEDANG
        // diputar (kalau belum pernah terukur), lalu menyimpan gain-nya supaya
        // pemutaran berikutnya level lagu ini otomatis disamakan dengan lagu lain
        // di koleksi. Nonaktif otomatis saat BIT-PERFECT MODE menyala.
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

        // BARU (patch "Crossfade"): fade-out lagu sekarang bertumpuk (overlap)
        // dengan fade-in lagu berikutnya -- beda dari GAPLESS (yang cuma
        // menghilangkan jeda hening, tanpa overlap sama sekali). Hanya berlaku
        // saat lagu berpindah SENDIRI (auto-advance dalam antrian/repeat),
        // bukan saat next/prev ditekan manual -- perpindahan manual tetap
        // instan seperti biasa supaya terasa responsif.
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

        // FIX (v1.8.1): Modifier.weight() TIDAK BOLEH dipakai di dalam Column yang
        // sudah verticalScroll() (Column butuh tinggi tak-terbatas untuk discroll,
        // weight() butuh tinggi terbatas untuk membagi ruang -- keduanya bentrok dan
        // akan crash saat runtime kalau tetap dipakai bareng). Diganti spacer tinggi
        // tetap sekadar kasih jarak napas di bawah kartu terakhir.
        Spacer(modifier = Modifier.height(16.dp))
    }
}

// BARU (v1.7): baris status kecil di dalam kartu toggle AUTO EQ PER LAGU --
// menunjukkan preset yang sudah tersimpan untuk lagu yang SEDANG diputar (kalau
// ada), plus tombol "HAPUS" buat lupakan preset itu lagi kalau salah pilih.
// PATCH (v1.8): padding horizontal 2dp sendiri DIHAPUS -- sekarang dirender lewat
// parameter statusContent EqToggleRow, yang sudah menyediakan inset 12dp yang
// sama dengan baris toggle di atasnya (lihat EqToggleRow), supaya ukuran/inset
// seragam dengan toggle lain, bukan nyempil sendiri di pinggir.
