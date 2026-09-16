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

@Composable
fun PlayerScreen(
    vm: PlayerViewModel,
    isCompact: Boolean = false,
    fullScreenOn: Boolean = false,
    onFullScreenToggle: () -> Unit = {}
) {

    val song by vm.currentSong.collectAsStateWithLifecycle()

    val streamStationName by vm.currentStreamTitle.collectAsStateWithLifecycle()

    val cassetteSide by vm.cassetteSide.collectAsStateWithLifecycle()
    val isPlaying by vm.isPlaying.collectAsStateWithLifecycle()
    val position by vm.positionMs.collectAsStateWithLifecycle()
    val duration by vm.durationMs.collectAsStateWithLifecycle()
    val vuLevels by vm.vuLevels.collectAsStateWithLifecycle()

    val waveform by vm.waveform.collectAsStateWithLifecycle()
    val shuffleOn by vm.shuffleOn.collectAsStateWithLifecycle()
    val repeatMode by vm.repeatMode.collectAsStateWithLifecycle()
    val powerOn by vm.powerOn.collectAsStateWithLifecycle()
    val skin by vm.currentSkin.collectAsStateWithLifecycle()
    val lastError by vm.lastError.collectAsStateWithLifecycle()
    val volume by vm.volume.collectAsStateWithLifecycle()

    val isUsbDacConnected by vm.isUsbDacConnected.collectAsStateWithLifecycle()
    val connectedDacName by vm.connectedDacName.collectAsStateWithLifecycle()
    val dspEngineOn by vm.dspEngineOn.collectAsStateWithLifecycle()
    val peakActive by vm.peakActive.collectAsStateWithLifecycle()

    val isHiRes by vm.isHiRes.collectAsStateWithLifecycle()
    val sampleRate by vm.sampleRate.collectAsStateWithLifecycle()
    val bitDepth by vm.bitDepth.collectAsStateWithLifecycle()

    val bitPerfectOn by vm.bitPerfectOn.collectAsStateWithLifecycle()
    val offloadActive by vm.offloadActive.collectAsStateWithLifecycle()

    val sleepTimerMode by vm.sleepTimerMode.collectAsStateWithLifecycle()
    val sleepTimerRemainingMs by vm.sleepTimerRemainingMs.collectAsStateWithLifecycle()

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

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = vm.playerManager.usbDacObserver
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBlack)

            .padding(if (isCompact) 6.dp else 12.dp)
    ) {

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

                vm.adjustVolume(newAbsoluteVolume - volume)
            }
        )

        Spacer(
            modifier = Modifier.width(10.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {

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

                                side = cassetteSide,

                                onDoubleTap = {
                                    vm.toggleCassetteSide()
                                },

                                onSeekDelta = { fractionDelta ->
                                    val newFraction =
                                        (progressFraction + fractionDelta)
                                            .coerceIn(0f, 1f)
                                    vm.seekTo(
                                        (newFraction * safeDuration).toLong()
                                    )
                                },

                                onScrubSpeedChange = { speedMultiplier ->
                                    if (speedMultiplier == 1f) {
                                        vm.resetTapeScrubSpeed()
                                    } else {
                                        vm.setTapeScrubSpeed(speedMultiplier)
                                    }
                                },

                                reelAnimationEnabled = reelAnimationEnabled,

                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )

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

                Column(
                    modifier = Modifier

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
