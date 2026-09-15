package com.projectzero.tapeamp32.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.projectzero.tapeamp32.R
import com.projectzero.tapeamp32.ui.components.CassetteDeck
import com.projectzero.tapeamp32.ui.components.PowerSwitch
import com.projectzero.tapeamp32.ui.components.ScreenToggleButton
import com.projectzero.tapeamp32.ui.components.TransportBar
import com.projectzero.tapeamp32.ui.components.VerticalVolumeSlider
import com.projectzero.tapeamp32.ui.components.VfdStatusPanel
import com.projectzero.tapeamp32.ui.components.VuMeter
import com.projectzero.tapeamp32.ui.components.WaveformSeekBar
import com.projectzero.tapeamp32.ui.theme.*
import com.projectzero.tapeamp32.viewmodel.PlayerViewModel
import kotlin.math.max

/* ================================================================
 * PLAYER SCREEN
 * ================================================================ */

@Composable
fun PlayerScreen(
    vm: PlayerViewModel,
    isCompact: Boolean = false,
    fullScreenOn: Boolean = false,
    onFullScreenToggle: () -> Unit = {}
) {

    val song by vm.currentSong.collectAsStateWithLifecycle()
    // BARU (patch "streaming label"): nama stasiun/URL yang sedang di-stream, dipakai
    // sebagai fallback title/artist kaset selagi `song` null KARENA sedang streaming
    // (bukan karena benar-benar belum ada apa pun yang diputar).
    val streamStationName by vm.currentStreamTitle.collectAsStateWithLifecycle()
    // BARU (patch "cassette side A/B"): "A" = Library, "B" = Stream. Lihat catatan
    // di PlayerViewModel.cassetteSide soal kenapa ini di-derive, bukan state manual.
    val cassetteSide by vm.cassetteSide.collectAsStateWithLifecycle()
    val isPlaying by vm.isPlaying.collectAsStateWithLifecycle()
    val position by vm.positionMs.collectAsStateWithLifecycle()
    val duration by vm.durationMs.collectAsStateWithLifecycle()
    val vuLevels by vm.vuLevels.collectAsStateWithLifecycle()
    // BARU (v2.4): WAVEFORM SEEKBAR -- null selagi masih di-decode/gagal, lihat
    // catatan fallback-nya di WaveformSeekBar.
    val waveform by vm.waveform.collectAsStateWithLifecycle()
    val shuffleOn by vm.shuffleOn.collectAsStateWithLifecycle()
    val repeatMode by vm.repeatMode.collectAsStateWithLifecycle()
    val powerOn by vm.powerOn.collectAsStateWithLifecycle()
    val skin by vm.currentSkin.collectAsStateWithLifecycle()
    val lastError by vm.lastError.collectAsStateWithLifecycle()
    val volume by vm.volume.collectAsStateWithLifecycle()

    // BARU: status untuk Status Panel VFD (hardware USB DAC + DSP Engine).
    val isUsbDacConnected by vm.isUsbDacConnected.collectAsStateWithLifecycle()
    val connectedDacName by vm.connectedDacName.collectAsStateWithLifecycle()
    val dspEngineOn by vm.dspEngineOn.collectAsStateWithLifecycle()
    val peakActive by vm.peakActive.collectAsStateWithLifecycle()

    // BARU: status untuk badge Hi-Res Audio di Status Panel VFD.
    val isHiRes by vm.isHiRes.collectAsStateWithLifecycle()
    val sampleRate by vm.sampleRate.collectAsStateWithLifecycle()
    val bitDepth by vm.bitDepth.collectAsStateWithLifecycle()

    // BARU (v1.8): status offload hardware AKTUAL untuk baris OFFLOAD di Status
    // Panel VFD -- lihat catatan "JUJUR diakui" di changelog v1.6, sekarang status
    // ini akhirnya ditampilkan real-time di UI, tidak perlu lagi adb logcat manual.
    val bitPerfectOn by vm.bitPerfectOn.collectAsStateWithLifecycle()
    val offloadActive by vm.offloadActive.collectAsStateWithLifecycle()

    // BARU (v2.1): SLEEP TIMER -- lihat VfdStatusPanel & Settings > System > Sleep Timer.
    val sleepTimerMode by vm.sleepTimerMode.collectAsStateWithLifecycle()
    val sleepTimerRemainingMs by vm.sleepTimerRemainingMs.collectAsStateWithLifecycle()

    // FIX (v1.4.1): dua setting ini sekarang benar-benar dibaca di sini -- sebelumnya
    // toggle "Show VU Meter in Compact Mode" dan "Enable Reel Spinning Animation" di
    // Settings tersimpan tapi tidak pernah sampai ke Player screen sama sekali.
    val compactVu by vm.settingsRepository.compactVu.collectAsStateWithLifecycle(
        initialValue = false
    )
    val reelAnimationEnabled by vm.settingsRepository.reelAnimation.collectAsStateWithLifecycle(
        initialValue = true
    )

    val vuL = vuLevels.first
    val vuR = vuLevels.second

    val safeDuration = max(duration, 1L)

    val progressFraction =
        (position.toFloat() / safeDuration.toFloat())
            .coerceIn(0f, 1f)

    /* ============================================================
     * FULL SCREEN TOGGLE STATE
     * ------------------------------------------------------------
     * FIX (v1.5): status ON/OFF-nya sekarang diangkat ke MainActivity
     * (lewat parameter [fullScreenOn]/[onFullScreenToggle]) alih-alih
     * disimpan lokal di sini -- AppRoot juga butuh tahu status ini supaya
     * bisa menambahkan padding systemBars() yang tepat (lihat FIX di
     * FullScreenController & MainActivity.AppRoot).
     * ============================================================ */

    /* ============================================================
     * LIFECYCLE-SAFE USB DAC REGISTRATION
     * ------------------------------------------------------------
     * UsbDacObserver di-attach ke LocalLifecycleOwner di sini (bukan
     * di ViewModel/PlayerManager) supaya register()/unregister() ke
     * AudioManager benar-benar mengikuti siklus hidup LAYAR ini --
     * otomatis lepas saat screen berpindah/di-destroy, tidak pernah
     * menggantung/leak, dan aman dipasang ulang saat recomposition.
     * ============================================================ */

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = vm.playerManager.usbDacObserver
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    /* ============================================================
     * ROOT
     * ============================================================ */

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBlack)
            // BARU (v1.5): padding lebih tipis saat window sedang dipersempit
            // (Split Screen/Multi-Window) supaya ruang efektif untuk kaset & VU
            // Meter tidak makin terpotong oleh margin yang sama besarnya dengan
            // mode fullscreen normal.
            .padding(if (isCompact) 6.dp else 12.dp)
    ) {

        /* ========================================================
         * LEFT CONTROL PANEL
         * ======================================================== */

        PlayerSidePanel(
            isCompact = isCompact,
            powerOn = powerOn,
            onPowerToggle = {
                vm.togglePower()
            },
            fullScreenOn = fullScreenOn,
            onScreenToggle = onFullScreenToggle,
            volume = volume,
            onVolumeChange = { newAbsoluteVolume ->
                // API ViewModel berbasis DELTA (adjustVolume), jadi konversi
                // dari nilai absolut baru yang dikirim VerticalVolumeSlider.
                vm.adjustVolume(newAbsoluteVolume - volume)
            }
        )

        Spacer(
            modifier = Modifier.width(10.dp)
        )

        /* ========================================================
         * MAIN PLAYER
         * ======================================================== */

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {

            /* ====================================================
             * DECK (+ TRANSPORT) + VU
             * ----------------------------------------------------
             * FIX: Row ini sekarang mengisi TINGGI PENUH sisa kolom
             * MAIN PLAYER (bukan cuma sebagian lewat weight(1f) yang
             * berhenti di atas TransportBar). Cassette Deck + Spacer
             * + TransportBar dipindah ke dalam SATU Column kiri di
             * bawah ini, supaya kolom VU+VFD di kanan (fillMaxHeight)
             * betul-betul membentang sepanjang tinggi Cassette Deck
             * DAN TransportBar sekaligus -- hasilnya batas bawah
             * VfdStatusPanel akhirnya sejajar persis dengan batas
             * bawah TransportBar, bukan cuma sejajar dengan batas
             * bawah Cassette Deck seperti sebelumnya.
             * ==================================================== */

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {

                /* =================================================
                 * LEFT: CASSETTE DECK FRAME + TRANSPORT BAR
                 * ================================================= */

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(
                                RoundedCornerShape(8.dp)
                            )
                            .background(
                                PanelBlack
                            )
                            .border(
                                width = 1.dp,
                                color = StrokeGold,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    ) {

                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {

                            /* ==========================================
                             * CASSETTE
                             * ========================================== */

                            CassetteDeck(
                                skin = skin,

                                title =
                                    song?.title
                                        ?: streamStationName
                                        ?: stringResource(R.string.player_no_song_loaded),

                                artist =
                                    song?.artist
                                        ?: streamStationName?.let {
                                            stringResource(R.string.player_streaming_label)
                                        }
                                        ?: stringResource(R.string.player_select_song_hint),

                                isPlaying = isPlaying,

                                progressFraction =
                                    progressFraction,

                                // BARU (patch "cassette side A/B"): label sisi kaset
                                // ikut sumber audio aktif (A = Library, B = Stream).
                                side = cassetteSide,

                                // BARU (patch "cassette side A/B"): double tap di
                                // bodi kaset untuk pindah Library <-> Stream.
                                onDoubleTap = {
                                    vm.toggleCassetteSide()
                                },

                                // FITUR BARU: Rotary Wheel Gesture Seeking -- putar roda
                                // kaset searah jarum jam = seek maju, berlawanan = mundur.
                                // Menggantikan swipe horizontal next/prev & swipe vertikal
                                // volume yang sudah dihapus dari CassetteDeck.
                                onSeekDelta = { fractionDelta ->
                                    val newFraction =
                                        (progressFraction + fractionDelta)
                                            .coerceIn(0f, 1f)
                                    vm.seekTo(
                                        (newFraction * safeDuration).toLong()
                                    )
                                },

                                // BARU: efek suara FF/RW ala kaset asli selama roda diputar
                                // manual untuk seeking -- pitch/tempo audio ikut berubah lewat
                                // ExoPlayer.playbackParameters, lalu otomatis kembali normal
                                // (1f) begitu jari diangkat dari roda.
                                onScrubSpeedChange = { speedMultiplier ->
                                    if (speedMultiplier == 1f) {
                                        vm.resetTapeScrubSpeed()
                                    } else {
                                        vm.setTapeScrubSpeed(speedMultiplier)
                                    }
                                },

                                // FIX (v1.4.1): benar-benar membekukan roda kaset saat
                                // toggle "Enable Reel Spinning Animation" dimatikan.
                                reelAnimationEnabled = reelAnimationEnabled,

                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )

                            /* ==========================================
                             * NO SONG MESSAGE
                             * ========================================== */

                            // FIX (patch "streaming label"): banner ini dulu tampil setiap
                            // kali `song == null`, termasuk selagi radio/stream sedang
                            // aktif diputar (karena playStreamUrl() memang men-set `song`
                            // ke null) -- jadi pengguna melihat "NO SONG LOADED" padahal
                            // ada siaran yang jalan. Sekarang cuma tampil kalau BENAR-BENAR
                            // tidak ada apa pun yang diputar (bukan lagu, bukan juga stream).
                            if (song == null && streamStationName == null) {

                                Text(
                                    text =
                                        stringResource(R.string.player_no_song_banner),
                                    color = TextMuted,
                                    fontFamily = MonoFont,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Normal,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            top = 3.dp,
                                            start = 3.dp
                                        )
                                )
                            }

                            /* ==========================================
                             * ERROR
                             * ========================================== */

                            if (lastError != null) {

                                Text(
                                    text =
                                        stringResource(R.string.player_error_prefix, lastError ?: ""),
                                    color = Color(0xFFFF6B6B),
                                    fontFamily = MonoFont,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            top = 3.dp,
                                            start = 3.dp
                                        )
                                )
                            }

                            Spacer(
                                modifier = Modifier.height(6.dp)
                            )

                            /* ==========================================
                             * TRACK INFORMATION / PROGRESS
                             * ========================================== */

                            PlayerTrackBar(
                                position = position,
                                duration = duration,
                                progressFraction = progressFraction,
                                format = song?.format ?: "FLAC",
                                waveform = waveform,
                                onSeek = {
                                    vm.seekTo(
                                        (it * safeDuration).toLong()
                                    )
                                }
                            )
                        }
                    }

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    /* =================================================
                     * TRANSPORT BAR
                     * -------------------------------------------------
                     * FIX: dipindah ke sini (dalam Column kiri yang sama
                     * dengan Cassette Deck, bukan lagi baris terpisah di
                     * bawah Row DECK+VU) supaya lebarnya otomatis persis
                     * selebar Cassette Deck (weight(1f) pada Column ini),
                     * tanpa perlu lagi padding(end) buatan untuk meniru
                     * lebar kolom VU/VFD di sebelahnya -- dan supaya
                     * tingginya ikut terhitung dalam fillMaxHeight() Column
                     * kiri ini, yang jadi acuan tinggi kolom VU/VFD di kanan.
                     * ================================================= */

                    TransportBar(
                        isPlaying = isPlaying,
                        shuffleOn = shuffleOn,
                        repeatMode = repeatMode,

                        onPrev = {
                            vm.previous()
                        },

                        onPlayPause = {
                            vm.playPause()
                        },

                        onPause = {
                            vm.playPause()
                        },

                        onStop = {
                            vm.playerManager.stop()
                        },

                        onNext = {
                            vm.next()
                        },

                        onShuffle = {
                            vm.toggleShuffle()
                        },

                        onRepeat = {
                            vm.cycleRepeat()
                        },

                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(
                    modifier = Modifier.width(8.dp)
                )

                /* =================================================
                 * VU METER + STATUS PANEL VFD
                 * -------------------------------------------------
                 * Satu kolom lebar tetap (74dp) dengan fillMaxHeight().
                 *
                 * FIX (v4): sebelumnya kolom ini adalah SAUDARA Row dari
                 * Box Cassette Deck saja, jadi fillMaxHeight()-nya cuma
                 * setinggi Cassette Deck -- batas bawah VfdStatusPanel
                 * berhenti DI ATAS TransportBar (yang saat itu masih jadi
                 * baris terpisah di bawah Row DECK+VU), menyisakan celah.
                 *
                 * Sekarang Cassette Deck + Spacer + TransportBar sudah
                 * dipindah jadi SATU Column kiri (lihat di atas) yang jadi
                 * saudara Row dari kolom VU+VFD ini. Karena fillMaxHeight()
                 * kolom VU+VFD mengikuti tinggi Row, dan tinggi Row itu kini
                 * ditentukan oleh Column kiri yang SUDAH mencakup TransportBar,
                 * batas bawah VfdStatusPanel (weight 0.5f, mentok ke bawah
                 * kolom) akhirnya sejajar PERSIS dengan batas bawah TransportBar.
                 *
                 * VU Meter dan VfdStatusPanel tetap dibagi rata lewat weight
                 * (0.5f / 0.5f) supaya proporsi keduanya seimbang dan otomatis
                 * mengisi penuh tinggi kolom tanpa sisa ruang kosong.
                 * ================================================= */

                Column(
                    modifier = Modifier
                        // BARU (v1.5): kolom VU+VFD juga menyempit sedikit saat
                        // isCompact (Split Screen/Multi-Window), sejalan dengan
                        // PlayerSidePanel, supaya Cassette Deck di tengah tetap
                        // kebagian ruang paling besar.
                        .width(if (isCompact) 56.dp else 74.dp)
                        .fillMaxHeight()
                ) {

                    Box(
                        modifier = Modifier
                            .weight(0.5f)
                            .fillMaxWidth()
                            .clip(
                                RoundedCornerShape(7.dp)
                            )
                            .background(
                                PanelBlack
                            )
                            .border(
                                width = 1.dp,
                                color = StrokeGold,
                                shape = RoundedCornerShape(7.dp)
                            )
                            .padding(5.dp)
                    ) {

                        VuMeter(
                            left = vuL,
                            right = vuR,
                            // FIX (v1.4.1): benar-benar mengecilkan tampilan VU Meter
                            // saat toggle "Show VU Meter in Compact Mode" dinyalakan.
                            // BARU (v1.5): juga otomatis compact saat window sedang
                            // dipersempit (Split Screen), terlepas dari toggle
                            // pengguna sendiri -- di lebar sesempit itu, skala dB penuh
                            // tidak lagi muat dengan nyaman.
                            compact = compactVu || isCompact,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(
                        modifier = Modifier.height(6.dp)
                    )

                    VfdStatusPanel(
                        isUsbDacConnected = isUsbDacConnected,
                        dacLabel = connectedDacName,
                        dspEngineOn = dspEngineOn,
                        peakActive = peakActive,
                        isHiRes = isHiRes,
                        sampleRate = sampleRate,
                        bitDepth = bitDepth,
                        bitPerfectOn = bitPerfectOn,
                        offloadActive = offloadActive,
                        sleepTimerActive = sleepTimerMode != com.projectzero.tapeamp32.viewmodel.SleepTimerMode.OFF,
                        sleepTimerSubLabel = when (sleepTimerMode) {
                            com.projectzero.tapeamp32.viewmodel.SleepTimerMode.MINUTES -> {
                                val totalSec = (sleepTimerRemainingMs / 1000L).coerceAtLeast(0L)
                                "%d:%02d".format(totalSec / 60, totalSec % 60)
                            }
                            com.projectzero.tapeamp32.viewmodel.SleepTimerMode.END_OF_TRACK -> stringResource(R.string.player_vfd_sleep_end_of_track)
                            com.projectzero.tapeamp32.viewmodel.SleepTimerMode.OFF -> stringResource(R.string.player_vfd_sleep_off)
                        },
                        modifier = Modifier
                            .weight(0.5f)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }
}

/* ================================================================
 * LEFT SIDE PANEL
 * ================================================================
 * FIX: sebelumnya panel ini menulis SENDIRI teks "POWER" + "ON"/"OFF"
 * lalu memanggil PowerSwitch yang JUGA menggambar "POWER" + "ON"/"OFF"
 * lagi di dalamnya -- menumpuk dan tidak presisi. Teks duplikat di sini
 * sudah DIHAPUS; PowerSwitch sekarang satu-satunya sumber label "POWER"
 * (lihat TransportControls.kt).
 *
 * BARU: ScreenToggleButton (full screen) dan VerticalVolumeSlider
 * (fader volume Hi-Fi retro) ditambahkan tepat di bawah PowerSwitch.
 * ================================================================ */
