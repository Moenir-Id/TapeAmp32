package com.projectzero.tapeamp32.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectzero.tapeamp32.ui.theme.GoldBright
import com.projectzero.tapeamp32.ui.theme.MonoFont
import com.projectzero.tapeamp32.ui.theme.PanelBlackAlt
import com.projectzero.tapeamp32.ui.theme.StrokeGold
import com.projectzero.tapeamp32.ui.theme.TextMuted

// Frequency-response curve overlay drawn on the Equalizer graph. Split out of
// EqualizerScreen.kt.

@Composable
internal fun FrequencyCurveWithScale(
    gains: List<Double>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(5.dp))
                .background(PanelBlackAlt)
                .border(
                    width = 1.dp,
                    color = StrokeGold,
                    shape = RoundedCornerShape(5.dp)
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (gains.isEmpty()) return@Canvas

                val width = size.width
                val height = size.height
                val midY = height / 2f

                // ==============================================
                // HORIZONTAL GRID LINES
                // ==============================================

                // +6dB line
                drawLine(
                    color = Color(0xFF2A2D2D),
                    start = Offset(0f, height * 0.25f),
                    end = Offset(width, height * 0.25f),
                    strokeWidth = 0.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))
                )

                // 0dB center line
                drawLine(
                    color = StrokeGold.copy(alpha = 0.6f),
                    start = Offset(0f, midY),
                    end = Offset(width, midY),
                    strokeWidth = 0.8.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
                )

                // -6dB line
                drawLine(
                    color = Color(0xFF2A2D2D),
                    start = Offset(0f, height * 0.75f),
                    end = Offset(width, height * 0.75f),
                    strokeWidth = 0.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))
                )

                // ==============================================
                // VERTICAL GRID LINES
                // ==============================================

                val stepX = width / (gains.size - 1).coerceAtLeast(1)
                for (i in gains.indices) {
                    drawLine(
                        color = Color(0xFF1E2121),
                        start = Offset(i * stepX, 0f),
                        end = Offset(i * stepX, height),
                        strokeWidth = 0.3.dp.toPx()
                    )
                }

                // ==============================================
                // FILL AREA UNDER CURVE
                // ==============================================

                val fillPath = Path()
                gains.forEachIndexed { index, gain ->
                    val x = index * stepX
                    val normalized = (gain.toFloat().coerceIn(-12f, 12f) / 12f)
                    val y = midY - normalized * (height / 2f) * 0.82f

                    if (index == 0) {
                        fillPath.moveTo(x, y)
                    } else {
                        fillPath.lineTo(x, y)
                    }
                }
                fillPath.lineTo(width, midY)
                fillPath.lineTo(0f, midY)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    color = GoldBright.copy(alpha = 0.08f)
                )

                // ==============================================
                // CURVE LINE
                // ==============================================

                val path = Path()
                gains.forEachIndexed { index, gain ->
                    val x = index * stepX
                    val normalized = (gain.toFloat().coerceIn(-12f, 12f) / 12f)
                    val y = midY - normalized * (height / 2f) * 0.82f

                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }

                drawPath(
                    path = path,
                    color = GoldBright,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // ==============================================
                // CURVE POINTS
                // ==============================================

                gains.forEachIndexed { index, gain ->
                    val x = index * stepX
                    val normalized = (gain.toFloat().coerceIn(-12f, 12f) / 12f)
                    val y = midY - normalized * (height / 2f) * 0.82f

                    // Outer glow
                    drawCircle(
                        color = GoldBright.copy(alpha = 0.3f),
                        radius = 5.dp.toPx(),
                        center = Offset(x, y)
                    )

                    // Inner point
                    drawCircle(
                        color = GoldBright,
                        radius = 2.5.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }

            // ====================================================
            // DB LABELS
            // ====================================================

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(start = 5.dp, top = 3.dp, bottom = 3.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text("+12", color = TextMuted, fontFamily = MonoFont, fontSize = 6.sp)
                Text("0", color = TextMuted, fontFamily = MonoFont, fontSize = 6.sp)
                Text("-12", color = TextMuted, fontFamily = MonoFont, fontSize = 6.sp)
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        // ========================================================
        // FREQUENCY SCALE
        // ========================================================

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("20", "50", "100", "200", "500", "1K", "2K", "5K", "10K", "20K")
                .forEach { frequency ->
                    Text(
                        text = frequency,
                        color = TextMuted,
                        fontFamily = MonoFont,
                        fontSize = 6.sp
                    )
                }
        }
    }
}
