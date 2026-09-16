package com.projectzero.tapeamp32.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectzero.tapeamp32.R
import com.projectzero.tapeamp32.ui.theme.CassetteSkin
import com.projectzero.tapeamp32.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sin

private fun cassetteLabelShape(cutPx: Float) = GenericShape { size, _ ->

    val c = cutPx

    moveTo(c, 0f)
    lineTo(size.width - c, 0f)
    lineTo(size.width, c)
    lineTo(size.width, size.height - c)
    lineTo(size.width - c, size.height)
    lineTo(c, size.height)
    lineTo(0f, size.height - c)
    lineTo(0f, c)

    close()
}

private fun angleDegrees(p: Offset, c: Offset): Float {
    return Math.toDegrees(
        atan2(
            (p.y - c.y).toDouble(),
            (p.x - c.x).toDouble()
        )
    ).toFloat()
}

@Composable
fun CassetteDeck(
    skin: CassetteSkin,
    title: String,
    artist: String,
    isPlaying: Boolean,
    progressFraction: Float,
    modifier: Modifier = Modifier,
    reelSpeed: Float = 1f,
    tapeType: String = "TYPE II\nCHROME\nBIAS",
    nrOn: Boolean = true,
    serial: String = "D90",
    side: String = "A",

    onSeekDelta: (Float) -> Unit = {},

    onScrubSpeedChange: (Float) -> Unit = {},

    seekFractionPerFullRotation: Float = 0.10f,

    reelAnimationEnabled: Boolean = true,

    onDoubleTap: () -> Unit = {}
) {

    var manualSpinBoost by remember { mutableFloatStateOf(0f) }
    var isManualSeeking by remember { mutableStateOf(false) }

    LaunchedEffect(isManualSeeking) {
        if (!isManualSeeking) {
            while (manualSpinBoost > 0.05f) {
                manualSpinBoost *= 0.85f
                delay(50)
            }
            manualSpinBoost = 0f
        }
    }

    val effectiveReelSpeed =
        (reelSpeed + manualSpinBoost).coerceAtLeast(0.05f)

    val infinite = rememberInfiniteTransition(label = "cassette_reels")

    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis =
                    (3000 / effectiveReelSpeed)
                        .toInt()
                        .coerceAtLeast(60),
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "reel_rotation"
    )

    val reelRotation =
        if (reelAnimationEnabled && (isPlaying || isManualSeeking)) rotation else 0f

    val density =
        androidx.compose.ui.platform.LocalDensity.current

    val cutPx =
        with(density) {
            11.dp.toPx()
        }

    var tapeWindowSizePx by remember { mutableStateOf(IntSize.Zero) }

    var activeReelCenter by remember { mutableStateOf(Offset.Zero) }
    var lastAngle by remember { mutableFloatStateOf(0f) }

    val currentOnSeekDelta by rememberUpdatedState(onSeekDelta)
    val currentOnScrubSpeedChange by rememberUpdatedState(onScrubSpeedChange)
    val currentSeekFractionPerFullRotation by rememberUpdatedState(seekFractionPerFullRotation)

    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(235.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        skin.body.copy(alpha = 0.98f),
                        skin.bodyDark,
                        Color(0xFF090B0C)
                    )
                )
            )
            .border(
                width = 1.5.dp,
                color = skin.accent.copy(alpha = 0.65f),
                shape = RoundedCornerShape(10.dp)
            )

            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { currentOnDoubleTap() }
                )
            }
            .padding(8.dp)
    ) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    1.dp,
                    Color.White.copy(alpha = 0.08f),
                    RoundedCornerShape(7.dp)
                )
                .padding(5.dp)
        ) {

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(103.dp)
                        .clip(cassetteLabelShape(cutPx))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    skin.labelBg.copy(alpha = 0.98f),
                                    skin.labelBg,
                                    skin.labelBg.copy(alpha = 0.90f)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            skin.labelText.copy(alpha = 0.45f),
                            cassetteLabelShape(cutPx)
                        )
                        .padding(
                            horizontal = 11.dp,
                            vertical = 7.dp
                        )
                ) {

                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.SpaceBetween
                        ) {

                            Column {

                                tapeType
                                    .split("\n")
                                    .forEach { line ->

                                        Text(
                                            text = line,
                                            color = skin.labelText,
                                            fontFamily = MonoFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 8.sp,
                                            lineHeight = 9.sp
                                        )
                                    }
                            }

                            Column(
                                horizontalAlignment =
                                    Alignment.End
                            ) {

                                Text(
                                    text = stringResource(R.string.cassette_nr_label),
                                    color = skin.labelText,
                                    fontFamily = MonoFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.sp
                                )

                                Row(
                                    verticalAlignment =
                                        Alignment.CenterVertically,
                                    horizontalArrangement =
                                        Arrangement.spacedBy(4.dp)
                                ) {

                                    Text(
                                        text =
                                            if (nrOn) stringResource(R.string.cassette_yes)
                                            else stringResource(R.string.cassette_no),
                                        color = skin.labelText,
                                        fontFamily = MonoFont,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 8.sp
                                    )

                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .border(
                                                1.dp,
                                                skin.labelText.copy(
                                                    alpha = 0.7f
                                                )
                                            )
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalAlignment =
                                Alignment.CenterHorizontally,
                            verticalArrangement =
                                Arrangement.Center
                        ) {

                            Text(
                                text = title,
                                color = skin.labelText,
                                fontFamily = DisplayFont,
                                fontWeight = FontWeight.Medium,
                                fontSize = 18.sp,
                                maxLines = 1
                            )

                            Spacer(
                                modifier = Modifier.height(1.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.82f)
                                    .height(1.dp)
                                    .background(
                                        skin.labelText.copy(
                                            alpha = 0.35f
                                        )
                                    )
                            )

                            Text(
                                text = artist,
                                color =
                                    skin.labelText.copy(
                                        alpha = 0.85f
                                    ),
                                fontFamily = DisplayFont,
                                fontSize = 10.sp,
                                maxLines = 1
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.82f)
                                    .height(1.dp)
                                    .background(
                                        skin.labelText.copy(
                                            alpha = 0.35f
                                        )
                                    )
                            )
                        }

                        val sideADescription =
                            stringResource(R.string.cassette_side_a_description)
                        val sideBDescription =
                            stringResource(R.string.cassette_side_b_description)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment =
                                Alignment.CenterVertically,
                            horizontalArrangement =
                                Arrangement.SpaceBetween
                        ) {

                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .border(
                                        1.dp,
                                        skin.labelText.copy(
                                            alpha = 0.85f
                                        ),
                                        RoundedCornerShape(2.dp)
                                    )

                                    .semantics {
                                        contentDescription =
                                            if (side == "B") sideBDescription
                                            else sideADescription
                                    },
                                contentAlignment =
                                    Alignment.Center
                            ) {

                                Text(
                                    text =
                                        if (side == "B") stringResource(R.string.cassette_side_b)
                                        else stringResource(R.string.cassette_side_a),
                                    color = skin.labelText,
                                    fontFamily = MonoFont,
                                    fontWeight =
                                        FontWeight.Bold,
                                    fontSize = 9.sp
                                )
                            }

                            Text(
                                text = serial,
                                color = skin.labelText,
                                fontFamily = MonoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(103.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF151719),
                                    Color(0xFF070809),
                                    Color(0xFF111314)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            Color(0xFF36383A),
                            RoundedCornerShape(5.dp)
                        )
                        .onSizeChanged {
                            tapeWindowSizePx = it
                        }

                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    val w = tapeWindowSizePx.width.toFloat()
                                    val h = tapeWindowSizePx.height.toFloat()
                                    if (w <= 0f || h <= 0f) return@detectDragGestures

                                    val tapeY = h * 0.50f
                                    val leftX = w * 0.30f
                                    val rightX = w * 0.70f

                                    val distToLeft =
                                        abs(startOffset.x - leftX)
                                    val distToRight =
                                        abs(startOffset.x - rightX)

                                    activeReelCenter =
                                        if (distToLeft <= distToRight) {
                                            Offset(leftX, tapeY)
                                        } else {
                                            Offset(rightX, tapeY)
                                        }

                                    lastAngle =
                                        angleDegrees(startOffset, activeReelCenter)

                                    isManualSeeking = true
                                },
                                onDragEnd = {
                                    isManualSeeking = false

                                    currentOnScrubSpeedChange(1f)
                                },
                                onDragCancel = {
                                    isManualSeeking = false
                                    currentOnScrubSpeedChange(1f)
                                },
                                onDrag = { change, _ ->

                                    val currentAngle =
                                        angleDegrees(change.position, activeReelCenter)

                                    var deltaAngle =
                                        currentAngle - lastAngle

                                    if (deltaAngle > 180f) deltaAngle -= 360f
                                    if (deltaAngle < -180f) deltaAngle += 360f

                                    lastAngle = currentAngle

                                    val seekDelta =
                                        (deltaAngle / 360f) * currentSeekFractionPerFullRotation

                                    currentOnSeekDelta(seekDelta)

                                    manualSpinBoost =
                                        (manualSpinBoost * 0.6f + abs(deltaAngle) * 0.45f)
                                            .coerceIn(0f, 40f)

                                    val scrubIntensity =
                                        (manualSpinBoost / 40f).coerceIn(0f, 1f)

                                    val scrubSpeedMultiplier =
                                        if (deltaAngle >= 0f) {

                                            1f + scrubIntensity * 2f
                                        } else {

                                            1f - scrubIntensity * 0.75f
                                        }

                                    currentOnScrubSpeedChange(scrubSpeedMultiplier)

                                    change.consume()
                                }
                            )
                        }
                ) {

                    Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {

                        val w = size.width
                        val h = size.height

                        drawRoundRect(
                            color = Color(0xFF030405),
                            topLeft = Offset(
                                w * 0.055f,
                                h * 0.14f
                            ),
                            size = Size(
                                w * 0.89f,
                                h * 0.72f
                            ),
                            cornerRadius =
                                CornerRadius(5f, 5f)
                        )

                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.025f),
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.015f)
                                )
                            ),
                            topLeft = Offset(
                                w * 0.055f,
                                h * 0.14f
                            ),
                            size = Size(
                                w * 0.89f,
                                h * 0.72f
                            ),
                            cornerRadius =
                                CornerRadius(5f, 5f)
                        )

                        val tapeY = h * 0.50f

                        drawLine(
                            color = Color(0xFF3A2917),
                            start = Offset(
                                w * 0.23f,
                                tapeY
                            ),
                            end = Offset(
                                w * 0.77f,
                                tapeY
                            ),
                            strokeWidth = h * 0.14f
                        )

                        drawLine(
                            color = Color(0xFF73502A)
                                .copy(alpha = 0.20f),
                            start = Offset(
                                w * 0.23f,
                                tapeY - h * 0.025f
                            ),
                            end = Offset(
                                w * 0.77f,
                                tapeY - h * 0.025f
                            ),
                            strokeWidth = h * 0.025f
                        )

                        fun drawReel(
                            cx: Float,
                            tapeFraction: Float
                        ) {

                            val cy = tapeY

                            val baseRadius =
                                min(w, h) * 0.205f

                            val tapeRadius =
                                baseRadius +
                                    baseRadius *
                                    0.75f *
                                    tapeFraction

                            drawCircle(
                                color = Color(0xFF15100B),
                                radius = tapeRadius,
                                center = Offset(cx, cy)
                            )

                            drawCircle(
                                color = skin.accent.copy(
                                    alpha = 0.88f
                                ),
                                radius = baseRadius,
                                center = Offset(cx, cy)
                            )

                            drawCircle(
                                color = Color(0xFF080808),
                                radius = baseRadius * 0.72f,
                                center = Offset(cx, cy)
                            )

                            drawCircle(
                                color = Color(0xFF272727),
                                radius = baseRadius * 0.43f,
                                center = Offset(cx, cy)
                            )

                            drawCircle(
                                color = Color(0xFF050505),
                                radius = baseRadius * 0.18f,
                                center = Offset(cx, cy)
                            )

                            rotate(
                                degrees = reelRotation,
                                pivot = Offset(cx, cy)
                            ) {

                                for (i in 0 until 6) {

                                    rotate(
                                        degrees = i * 60f,
                                        pivot = Offset(cx, cy)
                                    ) {

                                        drawLine(
                                            color = Color(0xFF050505),
                                            start = Offset(
                                                cx,
                                                cy -
                                                    baseRadius * 0.20f
                                            ),
                                            end = Offset(
                                                cx,
                                                cy -
                                                    baseRadius * 0.48f
                                            ),
                                            strokeWidth =
                                                baseRadius * 0.13f
                                        )
                                    }
                                }
                            }

                            drawCircle(
                                color = Color.White.copy(
                                    alpha = 0.08f
                                ),
                                radius = baseRadius * 0.91f,
                                center = Offset(
                                    cx - baseRadius * 0.12f,
                                    cy - baseRadius * 0.12f
                                )
                            )
                        }

                        val leftX = w * 0.30f
                        val rightX = w * 0.70f

                        drawReel(
                            cx = leftX,
                            tapeFraction =
                                1f -
                                    progressFraction
                                        .coerceIn(0f, 1f)
                        )

                        drawReel(
                            cx = rightX,
                            tapeFraction =
                                progressFraction
                                    .coerceIn(0f, 1f)
                        )

                        if (isManualSeeking) {

                            val glitchIntensity =
                                (manualSpinBoost / 40f)
                                    .coerceIn(0.18f, 1f)

                            for (band in 0 until 5) {

                                val seed =
                                    rotation + band * 47f

                                val bandY =
                                    abs(
                                        sin(
                                            Math.toRadians(
                                                (seed * 3.7).toDouble()
                                            )
                                        ).toFloat()
                                    ) * h

                                val bandHeight =
                                    2f + (band % 3) * 2.4f

                                val xShift =
                                    sin(
                                        Math.toRadians(
                                            (seed * 5.3).toDouble()
                                        )
                                    ).toFloat() * w * 0.07f * glitchIntensity

                                drawRect(
                                    color = Color.Cyan.copy(
                                        alpha = 0.11f * glitchIntensity
                                    ),
                                    topLeft = Offset(xShift, bandY),
                                    size = Size(w, bandHeight)
                                )

                                drawRect(
                                    color = Color.Magenta.copy(
                                        alpha = 0.11f * glitchIntensity
                                    ),
                                    topLeft = Offset(-xShift, bandY),
                                    size = Size(w, bandHeight)
                                )
                            }

                            drawRect(
                                color = Color.White.copy(
                                    alpha = 0.035f * glitchIntensity
                                ),
                                topLeft = Offset(0f, 0f),
                                size = Size(w, h)
                            )
                        }
                    }
                }
            }
        }
    }
}
