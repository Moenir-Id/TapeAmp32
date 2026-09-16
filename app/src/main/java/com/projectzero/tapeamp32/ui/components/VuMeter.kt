package com.projectzero.tapeamp32.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectzero.tapeamp32.R
import com.projectzero.tapeamp32.ui.theme.*
import kotlin.math.log10

private fun linearToDb(value: Float): Float {

    if (value <= 0.0001f) {
        return -40f
    }

    return (
        20f * log10(value.toDouble())
    )
        .toFloat()
        .coerceIn(-40f, 3f)
}

@Composable
fun VuMeter(
    left: Float,
    right: Float,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {

    val leftDb by animateFloatAsState(
        targetValue = linearToDb(left),
        animationSpec = tween(
            durationMillis = 55
        ),
        label = "vu_left"
    )

    val rightDb by animateFloatAsState(
        targetValue = linearToDb(right),
        animationSpec = tween(
            durationMillis = 55
        ),
        label = "vu_right"
    )

    Box(
        modifier = modifier
            .clip(
                RoundedCornerShape(7.dp)
            )
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF171A1A),
                        Color(0xFF090B0B),
                        Color(0xFF111313)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = StrokeGold.copy(
                    alpha = 0.75f
                ),
                shape = RoundedCornerShape(7.dp)
            )
            .padding(
                horizontal = 6.dp,
                vertical = 6.dp
            )
    ) {

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(5.dp)
            ) {

                VuChannelLabel(
                    text = stringResource(R.string.vu_left),
                    modifier =
                        Modifier.weight(1f)
                )

                VuChannelLabel(
                    text = stringResource(R.string.vu_right),
                    modifier =
                        Modifier.weight(1f)
                )
            }

            Spacer(
                modifier = Modifier.height(5.dp)
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                VuBar(
                    db = leftDb,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )

                VuDbScale(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(
                            if (compact)
                                20.dp
                            else
                                25.dp
                        )
                )

                VuBar(
                    db = rightDb,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            if (!compact) {

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text = stringResource(R.string.vu_db_unit),
                    color = GoldBright,
                    fontSize = 8.sp,
                    fontFamily = MonoFont,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun VuChannelLabel(
    text: String,
    modifier: Modifier = Modifier
) {

    Box(
        modifier = modifier
            .height(17.dp)
            .clip(
                RoundedCornerShape(3.dp)
            )
            .background(
                Color(0xFF101313)
            )
            .border(
                width = 0.7.dp,
                color = StrokeGold.copy(
                    alpha = 0.40f
                ),
                shape =
                    RoundedCornerShape(3.dp)
            ),
        contentAlignment =
            Alignment.Center
    ) {

        Text(
            text = text,
            color = TextMuted,
            fontSize = 7.sp,
            fontFamily = MonoFont,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun VuDbScale(
    modifier: Modifier = Modifier
) {

    val scales = listOf(
        "+3",
        "0",
        "-3",
        "-10",
        "-20",
        "-30"
    )

    Column(
        modifier = modifier,
        verticalArrangement =
            Arrangement.SpaceBetween,
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        scales.forEach { scale ->

            Text(
                text = scale,
                color =
                    if (scale == "+3")
                        VuRed
                    else
                        TextMuted,

                fontSize = 7.sp,
                fontFamily = MonoFont,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun VuBar(
    db: Float,
    modifier: Modifier = Modifier
) {

    val fraction =
        ((db + 40f) / 43f)
            .coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .clip(
                RoundedCornerShape(2.dp)
            )
            .background(
                Color(0xFF070909)
            )
            .border(
                width = 0.6.dp,
                color = Color(0xFF252828),
                shape =
                    RoundedCornerShape(2.dp)
            )
            .padding(
                horizontal = 2.dp,
                vertical = 2.dp
            )
    ) {

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {

            val width = size.width
            val height = size.height

            val segmentCount = 24

            val gap =
                1.6.dp.toPx()

            val segmentHeight =
                (
                    height -
                        gap *
                        (segmentCount - 1)
                ) / segmentCount

            val activeSegments =
                (
                    fraction *
                        segmentCount
                ).toInt()

            for (i in 0 until segmentCount) {

                val fromBottom = i

                val y =
                    height -
                        (
                            (i + 1) *
                                segmentHeight
                        ) -
                        (
                            i * gap
                        )

                val active =
                    fromBottom <
                        activeSegments

                val position =
                    fromBottom.toFloat() /
                        segmentCount.toFloat()

                val ledColor =
                    when {

                        position >= 0.88f ->
                            VuRed

                        position >= 0.72f ->
                            Color(0xFFFF8C18)

                        else ->
                            VuYellow
                    }

                val offColor =
                    when {

                        position >= 0.88f ->
                            VuRed.copy(
                                alpha = 0.09f
                            )

                        position >= 0.72f ->
                            Color(0xFFFF8C18)
                                .copy(alpha = 0.08f)

                        else ->
                            VuYellow.copy(
                                alpha = 0.07f
                            )
                    }

                if (active) {

                    drawRect(
                        color = ledColor.copy(
                            alpha = 0.12f
                        ),
                        topLeft =
                            Offset(
                                -1.dp.toPx(),
                                y - 1.dp.toPx()
                            ),
                        size =
                            Size(
                                width +
                                    2.dp.toPx(),
                                segmentHeight +
                                    2.dp.toPx()
                            )
                    )

                    drawRoundRect(
                        color = ledColor,
                        topLeft =
                            Offset(
                                0f,
                                y
                            ),
                        size =
                            Size(
                                width,
                                segmentHeight
                            ),
                        cornerRadius =
                            androidx.compose.ui.geometry
                                .CornerRadius(
                                    1.5.dp.toPx()
                                )
                    )

                    drawRect(
                        color = Color.White.copy(
                            alpha = 0.12f
                        ),
                        topLeft =
                            Offset(
                                0f,
                                y
                            ),
                        size =
                            Size(
                                width,
                                segmentHeight *
                                    0.22f
                            )
                    )

                } else {

                    drawRoundRect(
                        color = offColor,
                        topLeft =
                            Offset(
                                0f,
                                y
                            ),
                        size =
                            Size(
                                width,
                                segmentHeight
                            ),
                        cornerRadius =
                            androidx.compose.ui.geometry
                                .CornerRadius(
                                    1.5.dp.toPx()
                                )
                    )
                }
            }
        }
    }
}
