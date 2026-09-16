package com.projectzero.tapeamp32.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.max

@Composable
fun WaveformSeekBar(
    waveform: FloatArray?,
    progressFraction: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color(0xFFF2C94C),
    inactiveColor: Color = Color(0xFF3A352A),
    scrubLineColor: Color = Color(0xFFFFF6DE)
) {

    val currentOnSeek by rememberUpdatedState(onSeek)

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(progressFraction) }

    val displayFraction = if (isDragging) dragFraction else progressFraction

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                    currentOnSeek(fraction)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        isDragging = false
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onDrag = { change, _ ->
                        val fraction =
                            (change.position.x / size.width).coerceIn(0f, 1f)
                        dragFraction = fraction
                        currentOnSeek(fraction)
                        change.consume()
                    }
                )
            }
    ) {

        val w = size.width
        val h = size.height
        val midY = h / 2f

        val bars = waveform
        val activeX = w * displayFraction

        if (bars == null || bars.isEmpty()) {

            drawLine(
                color = inactiveColor,
                start = Offset(0f, midY),
                end = Offset(w, midY),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = activeColor,
                start = Offset(0f, midY),
                end = Offset(activeX, midY),
                strokeWidth = 2.dp.toPx()
            )

        } else {

            val barCount = bars.size
            val barSlotWidth = w / barCount

            val barWidth = max(1f, barSlotWidth * 0.62f)
            val minBarHeight = 2.dp.toPx()

            for (i in 0 until barCount) {

                val amplitude = bars[i].coerceIn(0f, 1f)
                val barHeight =
                    max(minBarHeight, amplitude * h)

                val barX = i * barSlotWidth + (barSlotWidth - barWidth) / 2f
                val barTop = midY - barHeight / 2f
                val barSize = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                val cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                    barWidth / 2.5f, barWidth / 2.5f
                )

                val barStartFraction = i.toFloat() / barCount
                val barEndFraction = (i + 1).toFloat() / barCount

                when {
                    displayFraction >= barEndFraction -> {
                        drawRoundRect(
                            color = activeColor,
                            topLeft = Offset(barX, barTop),
                            size = barSize,
                            cornerRadius = cornerRadius
                        )
                    }
                    displayFraction <= barStartFraction -> {
                        drawRoundRect(
                            color = inactiveColor,
                            topLeft = Offset(barX, barTop),
                            size = barSize,
                            cornerRadius = cornerRadius
                        )
                    }
                    else -> {

                        drawRoundRect(
                            color = inactiveColor,
                            topLeft = Offset(barX, barTop),
                            size = barSize,
                            cornerRadius = cornerRadius
                        )
                        val fillWithinBar =
                            ((displayFraction - barStartFraction) / (barEndFraction - barStartFraction))
                                .coerceIn(0f, 1f)
                        val activeWidth = barWidth * fillWithinBar
                        if (activeWidth > 0f) {
                            clipRect(left = barX, top = 0f, right = barX + activeWidth, bottom = h) {
                                drawRoundRect(
                                    color = activeColor,
                                    topLeft = Offset(barX, barTop),
                                    size = barSize,
                                    cornerRadius = cornerRadius
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isDragging) {
            drawLine(
                color = scrubLineColor,
                start = Offset(activeX, 0f),
                end = Offset(activeX, h),
                strokeWidth = 1.5.dp.toPx()
            )
        }
    }
}
