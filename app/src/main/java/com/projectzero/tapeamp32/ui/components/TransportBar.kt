package com.projectzero.tapeamp32.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectzero.tapeamp32.R
import com.projectzero.tapeamp32.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun VerticalVolumeSlider(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    trackHeight: androidx.compose.ui.unit.Dp = 130.dp
) {

    val clampedVolume = volume.coerceIn(0f, 1f)

    var trackHeightPx by remember { mutableFloatStateOf(0f) }

    fun updateFromOffsetY(offsetY: Float) {
        if (trackHeightPx <= 0f) return
        val fraction = 1f - (offsetY / trackHeightPx)
        onVolumeChange(fraction.coerceIn(0f, 1f))
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = stringResource(R.string.transport_volume),
            color = TextMuted,
            fontSize = 8.sp,
            fontFamily = MonoFont,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        Box(
            modifier = Modifier
                .width(32.dp)
                .height(trackHeight)
                .onSizeChanged {
                    trackHeightPx = it.height.toFloat()
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        updateFromOffsetY(offset.y)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        updateFromOffsetY(change.position.y)
                        change.consume()
                    }
                }
        ) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF171717),
                                Color(0xFF0A0A0A),
                                Color(0xFF171717)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        Color(0xFF333333),
                        RoundedCornerShape(3.dp)
                    )
            )

            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val tickCount = 10
                val majorLength = 5.dp.toPx()
                val minorLength = 3.dp.toPx()
                val stroke = 1.dp.toPx()

                for (i in 0..tickCount) {
                    val y = size.height * (i / tickCount.toFloat())
                    val isMajor = i % 2 == 0
                    val length = if (isMajor) majorLength else minorLength
                    val tickColor = if (isMajor) Color(0xFF565656) else Color(0xFF3A3A3A)

                    drawLine(
                        color = tickColor,
                        start = Offset(0f, y),
                        end = Offset(length, y),
                        strokeWidth = stroke
                    )

                    drawLine(
                        color = tickColor,
                        start = Offset(size.width - length, y),
                        end = Offset(size.width, y),
                        strokeWidth = stroke
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .width(7.dp)
                    .fillMaxHeight()
                    .padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF050505))
                    .border(
                        0.5.dp,
                        Color(0xFF2A2A2A),
                        RoundedCornerShape(2.dp)
                    )
            ) {

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(clampedVolume)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    GoldBright,
                                    Gold,
                                    Color(0xFF7D5F17)
                                )
                            )
                        )
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset {
                        IntOffset(
                            x = 0,
                            y = -(clampedVolume * trackHeightPx).roundToInt()
                        )
                    }
                    .width(32.dp)
                    .height(15.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFFF0F0F0),
                                Color(0xFFC7C7C7),
                                Color(0xFF8E8E8E),
                                Color(0xFF5C5C5C)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        Color(0xFF3A3A3A),
                        RoundedCornerShape(2.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(1.dp)
                        .background(Color(0xFF3A3A3A))
                )
            }
        }

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        Text(
            text = "${(clampedVolume * 100).roundToInt()}%",
            color = GoldBright,
            fontSize = 8.sp,
            fontFamily = MonoFont,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun TransportBar(
    isPlaying: Boolean,
    shuffleOn: Boolean,
    repeatMode: com.projectzero.tapeamp32.viewmodel.RepeatMode,

    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,

    modifier: Modifier = Modifier
) {

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement =

            Arrangement.SpaceEvenly,
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        TransportButton(
            icon = Icons.Filled.FastRewind,
            label = stringResource(R.string.transport_rew),
            onClick = onPrev
        )

        TransportButton(
            icon = Icons.Filled.PlayArrow,
            label = stringResource(R.string.transport_play),
            active = isPlaying,
            isPrimaryPlay = true,
            onClick = onPlayPause
        )

        TransportButton(
            icon = Icons.Filled.Pause,
            label = stringResource(R.string.transport_pause),
            active = false,
            onClick = onPause
        )

        TransportButton(
            icon = Icons.Filled.Stop,
            label = stringResource(R.string.transport_stop),
            onClick = onStop
        )

        TransportButton(
            icon = Icons.Filled.FastForward,
            label = stringResource(R.string.transport_ffwd),
            onClick = onNext
        )

        TransportButton(
            icon = Icons.Filled.Shuffle,
            label = stringResource(R.string.transport_shuffle),
            active = shuffleOn,
            onClick = onShuffle
        )

        TransportButton(
            icon = Icons.Filled.Repeat,
            label = stringResource(R.string.transport_repeat),
            active =
                repeatMode !=
                    com.projectzero.tapeamp32.viewmodel.RepeatMode.OFF,
            onClick = onRepeat
        )
    }
}

@Composable
internal fun TransportButton(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    isPrimaryPlay: Boolean = false,
    onClick: () -> Unit
) {

    Column(
        modifier = Modifier
            .width(61.dp),
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Box(
            modifier = Modifier
                .width(58.dp)
                .height(50.dp)
                .clip(
                    RoundedCornerShape(5.dp)
                )
                .background(
                    Brush.verticalGradient(
                        when {

                            isPrimaryPlay && active -> {
                                listOf(
                                    Color(0xFFFFD15A),
                                    GoldBright,
                                    Color(0xFFC38A20)
                                )
                            }

                            active -> {
                                listOf(
                                    Color(0xFF302916),
                                    Color(0xFF19160E)
                                )
                            }

                            else -> {
                                listOf(
                                    Color(0xFF292929),
                                    Color(0xFF171717),
                                    Color(0xFF0F0F0F)
                                )
                            }
                        }
                    )
                )
                .border(
                    width =
                        if (active) 1.5.dp
                        else 1.dp,

                    color = when {

                        isPrimaryPlay && active ->
                            GoldBright

                        active ->
                            Gold

                        else ->
                            Color(0xFF3D3D3D)
                    },

                    shape =
                        RoundedCornerShape(5.dp)
                )
                .clickable {
                    onClick()
                },
            contentAlignment =
                Alignment.Center
        ) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Color.White.copy(
                            alpha = 0.10f
                        )
                    )
            )

            Icon(
                imageVector = icon,
                contentDescription = label,

                tint = when {

                    isPrimaryPlay && active ->
                        Color(0xFF17120A)

                    active ->
                        GoldBright

                    else ->
                        Color(0xFFD4D4D4)
                },

                modifier = Modifier.size(
                    when {
                        isPrimaryPlay -> 25.dp
                        else -> 21.dp
                    }
                )
            )
        }

        Spacer(
            modifier = Modifier.height(3.dp)
        )

        Text(
            text = label,
            color =
                if (active)
                    GoldBright
                else
                    TextMuted,

            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = MonoFont,
            letterSpacing = 0.2.sp,
            maxLines = 1
        )
    }
}
