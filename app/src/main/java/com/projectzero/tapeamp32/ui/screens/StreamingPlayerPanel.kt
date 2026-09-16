package com.projectzero.tapeamp32.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectzero.tapeamp32.R
import com.projectzero.tapeamp32.data.RadioStation
import com.projectzero.tapeamp32.ui.theme.*
import kotlin.math.abs
import kotlin.math.sin

// Now-playing player panel, waveform visualizer, and small info-row composable
// for the Streaming screen. Split out of StreamingScreen.kt.

@Composable
internal fun StreamingPlayerPanel(
    station: RadioStation?,
    isPlaying: Boolean,
    bufferSeconds: Float,
    isStreamPlaying: Boolean,
    liveStreamBitrateKbps: Int,
    modifier: Modifier = Modifier
) {

    Column(
        modifier = modifier
            .clip(
                RoundedCornerShape(6.dp)
            )
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF151818),
                        Color(0xFF090B0B),
                        Color(0xFF111313)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = StrokeGold.copy(
                    alpha = 0.65f
                ),
                shape =
                    RoundedCornerShape(6.dp)
            )
            .padding(
                horizontal = 12.dp,
                vertical = 10.dp
            ),
        verticalArrangement =
            Arrangement.SpaceBetween
    ) {

        /*
         * ============================================================
         * HEADER
         * ============================================================
         */

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween,
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Column {

                Text(
                    text =
                        station?.name
                            ?: stringResource(R.string.streaming_no_station_selected),

                    color = TextLight,
                    fontFamily = DisplayFont,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1
                )

                Spacer(
                    modifier = Modifier.height(1.dp)
                )

                Text(
                    text = stringResource(R.string.streaming_icecast_stream),
                    color = TextMuted,
                    fontFamily = MonoFont,
                    fontSize = 8.sp,
                    letterSpacing = 0.5.sp
                )
            }

            /*
             * LIVE BADGE
             */

            if (isPlaying) {

                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(3.dp)
                        )
                        .background(
                            Color(0xFF8B2117)
                        )
                        .border(
                            0.5.dp,
                            Color(0xFFC54A38),
                            RoundedCornerShape(3.dp)
                        )
                        .padding(
                            horizontal = 7.dp,
                            vertical = 3.dp
                        )
                ) {

                    Text(
                        text = stringResource(R.string.streaming_live_badge),
                        color = Color.White,
                        fontFamily = MonoFont,
                        fontWeight =
                            FontWeight.Bold,
                        fontSize = 8.sp,
                        letterSpacing = 0.6.sp
                    )
                }
            }
        }

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        /*
         * ============================================================
         * WAVEFORM
         * ============================================================
         */

        AudioWaveformVisualizer(
            isPlaying = isPlaying,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(
                    RoundedCornerShape(4.dp)
                )
                .background(
                    Color(0xFF080B0C)
                )
                .border(
                    0.8.dp,
                    Color(0xFF252C2D),
                    RoundedCornerShape(4.dp)
                )
        )

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        /*
         * ============================================================
         * BUFFER
         * ============================================================
         */

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Text(
                    text = stringResource(R.string.streaming_buffer_label),
                    color = TextMuted,
                    fontFamily = MonoFont,
                    fontSize = 9.sp
                )

                Text(
                    text =
                        stringResource(R.string.streaming_buffer_value, bufferSeconds),

                    color = TextMuted,
                    fontFamily = MonoFont,
                    fontSize = 9.sp
                )
            }

            Spacer(
                modifier = Modifier.height(3.dp)
            )

            LinearProgressIndicator(
                progress = {
                    (
                        bufferSeconds / 5f
                    ).coerceIn(
                        0f,
                        1f
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(
                        RoundedCornerShape(1.dp)
                    ),
                color = GoldBright,
                trackColor =
                    Color(0xFF242525)
            )
        }

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        /*
         * ============================================================
         * STREAM INFORMATION
         * ============================================================
         */

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {

            InfoRow(
                label = stringResource(R.string.streaming_label_codec),
                value =
                    station?.codec
                        ?: "MP3"
            )

            InfoRow(
                label = stringResource(R.string.streaming_label_bitrate),
                value =
                    if (isStreamPlaying && liveStreamBitrateKbps > 0)
                        "$liveStreamBitrateKbps kbps"
                    else
                        // Belum ada stream aktif, ATAU server yang sedang diputar
                        // tidak kirim info bitrate sama sekali (tidak ada header
                        // icy-br maupun bitrate di container) -- daripada tampil
                        // "0 kbps" yang menyesatkan (seolah stream rusak),
                        // tampilkan "--" yang jujur.
                        "--"
            )

            InfoRow(
                label = stringResource(R.string.streaming_label_status),
                value =
                    if (isPlaying)
                        stringResource(R.string.streaming_status_playing)
                    else
                        stringResource(R.string.streaming_status_stopped),
                valueColor =
                    if (isPlaying)
                        Color(0xFF72C48A)
                    else
                        TextMuted
            )
        }
    }
}

/* ================================================================
 * WAVEFORM
 * ================================================================ */

@Composable
internal fun AudioWaveformVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {

    /*
     * Animasi kontinu, tetapi bentuk waveform tidak
     * di-random ulang setiap draw.
     */

    val transition =
        rememberInfiniteTransition(
            label = "waveform"
        )

    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec =
            infiniteRepeatable(
                animation =
                    tween(
                        durationMillis = 1200,
                        easing = LinearEasing
                    ),
                repeatMode =
                    RepeatMode.Restart
            ),
        label = "wave_phase"
    )

    Canvas(
        modifier = modifier
    ) {

        val width = size.width
        val height = size.height

        val centerY =
            height / 2f

        /*
         * GRID HORIZONTAL
         */

        val gridColor =
            Color(0xFF253032)

        for (i in 1..4) {

            val y =
                height *
                    (i / 5f)

            drawLine(
                color =
                    gridColor.copy(
                        alpha = 0.35f
                    ),
                start =
                    Offset(
                        0f,
                        y
                    ),
                end =
                    Offset(
                        width,
                        y
                    ),
                strokeWidth = 1.dp.toPx()
            )
        }

        /*
         * GRID VERTICAL
         */

        val verticalCount = 24

        for (i in 1 until verticalCount) {

            val x =
                width *
                    (i / verticalCount.toFloat())

            drawLine(
                color =
                    gridColor.copy(
                        alpha = 0.20f
                    ),
                start =
                    Offset(
                        x,
                        0f
                    ),
                end =
                    Offset(
                        x,
                        height
                    ),
                strokeWidth =
                    0.6.dp.toPx()
            )
        }

        /*
         * WAVEFORM
         */

        val sampleCount = 90

        val step =
            width /
                sampleCount

        for (i in 0 until sampleCount) {

            val x =
                i * step

            val normalized =
                i /
                    sampleCount.toFloat()

            /*
             * Kombinasi beberapa gelombang
             * membuat waveform terlihat natural
             * tanpa random flicker.
             */

            val wave1 =
                sin(
                    normalized * 18f +
                        phase
                )

            val wave2 =
                sin(
                    normalized * 42f -
                        phase * 1.7f
                )

            val wave3 =
                sin(
                    normalized * 75f +
                        phase * 0.6f
                )

            val amplitude =
                if (isPlaying) {

                    (
                        abs(wave1) * 0.48f +
                            abs(wave2) * 0.28f +
                            abs(wave3) * 0.14f
                    )

                } else {
                    0.035f
                }

            val barHeight =
                height *
                    amplitude.coerceIn(
                        0.025f,
                        0.90f
                    )

            drawLine(
                color =
                    Color(0xFF5AC8E8)
                        .copy(
                            alpha =
                                if (isPlaying)
                                    0.78f
                                else
                                    0.20f
                        ),

                start =
                    Offset(
                        x,
                        centerY -
                            barHeight / 2f
                    ),

                end =
                    Offset(
                        x,
                        centerY +
                            barHeight / 2f
                    ),

                strokeWidth =
                    1.2.dp.toPx()
            )
        }
    }
}

/* ================================================================
 * INFORMATION ROW
 * ================================================================ */

@Composable
internal fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = TextLight
) {

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 3.dp
                ),
            horizontalArrangement =
                Arrangement.SpaceBetween
        ) {

            Text(
                text = label,
                color = TextMuted,
                fontFamily = MonoFont,
                fontSize = 9.sp
            )

            Text(
                text = value,
                color = valueColor,
                fontFamily = MonoFont,
                fontSize = 9.sp
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.6.dp)
                .background(
                    Color(0xFF292B2B)
                )
        )
    }
}
