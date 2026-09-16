package com.projectzero.tapeamp32.ui.screens

import android.provider.Settings
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectzero.tapeamp32.ui.theme.*

@Composable
internal fun SettingsSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    suffix: String,
    onChange: (Float) -> Unit,

    offLabel: String = "Off"
) {

    SettingsRowCard {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 5.dp
                )
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(
                    text = label,
                    color = TextLight,
                    fontFamily = DisplayFont,
                    fontSize = 11.sp
                )

                Text(
                    text = formatSettingValue(
                        value = value,
                        suffix = suffix,
                        offLabel = offLabel
                    ),
                    color = TextMuted,
                    fontFamily = MonoFont,
                    fontSize = 9.sp
                )
            }

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            ClassicHorizontalSlider(
                value = value,
                onValueChange = onChange,
                range = range,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
            )
        }
    }
}

@Composable
internal fun ClassicHorizontalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    val span = (range.endInclusive - range.start).let { if (it == 0f) 0.0001f else it }
    val fraction = ((value - range.start) / span).coerceIn(0f, 1f)
    val capWidth = 22.dp

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF171717), Color(0xFF0A0A0A), Color(0xFF171717))
                )
            )
            .border(1.dp, Color(0xFF333333), RoundedCornerShape(4.dp))
            .pointerInput(range) {
                fun updateFromX(x: Float) {
                    val usable = (size.width - capWidth.toPx()).coerceAtLeast(1f)
                    val f = ((x - capWidth.toPx() / 2f) / usable).coerceIn(0f, 1f)
                    onValueChange(range.start + f * span)
                }
                detectTapGestures { offset -> updateFromX(offset.x) }
            }
            .pointerInput(range) {
                fun updateFromX(x: Float) {
                    val usable = (size.width - capWidth.toPx()).coerceAtLeast(1f)
                    val f = ((x - capWidth.toPx() / 2f) / usable).coerceIn(0f, 1f)
                    onValueChange(range.start + f * span)
                }
                detectDragGestures { change, _ ->
                    change.consume()
                    updateFromX(change.position.x)
                }
            }
    ) {
        val trackWidthPx = with(LocalDensity.current) { maxWidth.toPx() }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val tickCount = 12
            val majorLength = 3.dp.toPx()
            val minorLength = 1.8.dp.toPx()
            for (i in 0..tickCount) {
                val x = size.width * (i / tickCount.toFloat())
                val isMajor = i % 3 == 0
                val length = if (isMajor) majorLength else minorLength
                val tickColor = if (isMajor) Color(0xFF565656) else Color(0xFF3A3A3A)
                drawLine(tickColor, Offset(x, 0f), Offset(x, length), strokeWidth = 1.dp.toPx())
                drawLine(tickColor, Offset(x, size.height - length), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(6.dp)
                .padding(horizontal = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF050505))
                .border(0.5.dp, Color(0xFF2A2A2A), RoundedCornerShape(2.dp))
        ) {

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF7D5F17), Gold, GoldBright)
                        )
                    )
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .offset {
                    androidx.compose.ui.unit.IntOffset(
                        x = (fraction * (trackWidthPx - capWidth.toPx())).roundToIntSafe(),
                        y = 0
                    )
                }
                .width(capWidth)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFFF0F0F0),
                            Color(0xFFC7C7C7),
                            Color(0xFF8E8E8E),
                            Color(0xFF5C5C5C)
                        )
                    )
                )
                .border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.55f)
                    .width(1.dp)
                    .background(Color(0xFF3A3A3A))
            )
        }
    }
}

internal fun Float.roundToIntSafe(): Int = kotlin.math.round(this).toInt()

internal fun formatSleepCountdown(remainingMs: Long): String {
    val totalSec = (remainingMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return "%d:%02d".format(minutes, seconds)
}

internal fun formatSettingValue(
    value: Float,
    suffix: String,
    offLabel: String = "Off"
): String {

    return when (suffix) {

        "%" -> {
            "%.2f%s".format(
                value,
                suffix
            )
        }

        " sec" -> {
            if (value == value.toInt().toFloat()) {
                "${value.toInt()} sec"
            } else {
                "%.1f sec".format(value)
            }
        }

        " min" -> {
            if (value <= 0f) offLabel else "${value.toInt()} min"
        }

        else -> {
            "%.2f%s".format(
                value,
                suffix
            )
        }
    }
}

@Composable
internal fun SettingsDropdownRow(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {

    var expanded by remember {
        mutableStateOf(false)
    }

    SettingsRowCard {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(39.dp),
            horizontalArrangement =
                Arrangement.SpaceBetween,
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(
                text = label,
                color = TextLight,
                fontFamily = DisplayFont,
                fontSize = 11.sp,
                maxLines = 1
            )

            Box {

                Row(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(3.dp)
                        )
                        .clickable {
                            expanded = true
                        }
                        .padding(
                            horizontal = 4.dp,
                            vertical = 5.dp
                        ),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Text(
                        text = value,
                        color = TextMuted,
                        fontFamily = MonoFont,
                        fontSize = 9.sp,
                        maxLines = 1
                    )

                    Spacer(
                        modifier = Modifier.width(4.dp)
                    )

                    Icon(
                        imageVector =
                            Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = {
                        expanded = false
                    },

                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(PanelBlack)
                        .border(
                            width = 1.dp,
                            color = StrokeGold,
                            shape = RoundedCornerShape(6.dp)
                        )
                ) {

                    options.forEach { option ->

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option,
                                    color = if (option == value) GoldBright else TextLight,
                                    fontFamily = MonoFont,
                                    fontWeight = if (option == value) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 10.sp
                                )
                            },
                            onClick = {

                                onSelect(option)

                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
