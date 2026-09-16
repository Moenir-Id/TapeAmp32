package com.projectzero.tapeamp32.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectzero.tapeamp32.R
import com.projectzero.tapeamp32.ui.theme.GoldBright
import com.projectzero.tapeamp32.ui.theme.MonoFont
import com.projectzero.tapeamp32.ui.theme.PanelBlack
import com.projectzero.tapeamp32.ui.theme.PanelBlackAlt
import com.projectzero.tapeamp32.ui.theme.StrokeGold
import com.projectzero.tapeamp32.ui.theme.TextLight
import com.projectzero.tapeamp32.ui.theme.TextMuted
import com.projectzero.tapeamp32.data.EqPreset

@Composable
internal fun EqualizerHeader(
    presetName: String,
    presets: List<EqPreset>,

    customPresetNames: Set<String>,
    menuOpen: Boolean,
    onMenuOpen: () -> Unit,
    onMenuDismiss: () -> Unit,
    onPresetSelected: (EqPreset) -> Unit,
    onDeleteRequest: (EqPreset) -> Unit,
    onSave: () -> Unit,
    onUpload: () -> Unit,
    onExport: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Text(
            text = stringResource(R.string.eq_header_title),
            color = GoldBright,
            fontFamily = MonoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.width(15.dp))

        Text(
            text = stringResource(R.string.eq_header_preset_label),
            color = TextMuted,
            fontFamily = MonoFont,
            fontSize = 8.sp
        )

        Spacer(modifier = Modifier.width(6.dp))

        Box {
            Row(
                modifier = Modifier
                    .height(29.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(PanelBlackAlt)
                    .border(
                        width = 1.dp,
                        color = StrokeGold,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clickable { onMenuOpen() }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = presetName,
                    color = TextLight,
                    fontFamily = MonoFont,
                    fontSize = 9.sp,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.width(4.dp))

                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = GoldBright,
                    modifier = Modifier.size(14.dp)
                )
            }

            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = onMenuDismiss,

                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(PanelBlack)
                    .border(
                        width = 1.dp,
                        color = StrokeGold,
                        shape = RoundedCornerShape(6.dp)
                    )
            ) {
                presets.forEach { preset ->

                    val isCustom = preset.name in customPresetNames
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = preset.name,
                                fontFamily = MonoFont,

                                fontSize = 9.sp,
                                color = TextLight
                            )
                        },
                        trailingIcon = if (isCustom) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.eq_delete_preset_desc, preset.name),
                                    tint = Color(0xFFE57373),

                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onDeleteRequest(preset) }
                                )
                            }
                        } else null,
                        onClick = { onPresetSelected(preset) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        EqActionButton(text = stringResource(R.string.eq_save_button), onClick = onSave)

        Spacer(modifier = Modifier.width(6.dp))

        EqActionButton(text = stringResource(R.string.eq_upload_action), onClick = onUpload)

        Spacer(modifier = Modifier.width(6.dp))

        EqActionButton(text = stringResource(R.string.eq_export_action), onClick = onExport)
    }
}

@Composable
internal fun EqActionButton(
    text: String,
    onClick: () -> Unit
) {

    Text(
        text = text,
        color = GoldBright,
        fontFamily = MonoFont,
        fontWeight = FontWeight.Bold,
        fontSize = 8.5.sp,
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF2A2A28), PanelBlackAlt, Color(0xFF161614))
                )
            )
            .border(
                width = 1.dp,
                color = StrokeGold,
                shape = RoundedCornerShape(5.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
internal fun EqualizerSliderPanel(
    preset: EqPreset,
    onGainChange: (Int, Double) -> Unit,
    sliderTrackWidth: androidx.compose.ui.unit.Dp = 26.dp,
    modifier: Modifier = Modifier
) {
    val frequencies = listOf(
        "20", "50", "100", "200", "500",
        "1K", "2K", "5K", "10K", "20K"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        preset.bands.forEachIndexed { index, band ->
            EqSlider(
                frequency = frequencies.getOrElse(index) { "" },
                gain = band.gain,
                onGainChange = { newGain -> onGainChange(index, newGain) },
                trackWidth = sliderTrackWidth,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
internal fun EqSlider(
    frequency: String,
    gain: Double,
    onGainChange: (Double) -> Unit,

    trackWidth: androidx.compose.ui.unit.Dp = 26.dp,
    modifier: Modifier = Modifier
) {
    val minGain = -12.0
    val maxGain = 12.0

    val normalizedGain = ((gain - minGain) / (maxGain - minGain)).toFloat().coerceIn(0f, 1f)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {

        Text(
            text = "${gain.roundToInt()}",
            color = if (gain > 0) Color(0xFF4CAF50)
                   else if (gain < 0) Color(0xFFE57373)
                   else TextMuted,
            fontFamily = MonoFont,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(3.dp))

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .width(trackWidth)
        ) {
            val trackHeightPx = with(LocalDensity.current) { maxHeight.toPx() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF171717), Color(0xFF0A0A0A), Color(0xFF171717))
                        )
                    )
                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(3.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                calculateNewGain(offset.y, size.height.toFloat(), minGain, maxGain, onGainChange)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                calculateNewGain(change.position.y, size.height.toFloat(), minGain, maxGain, onGainChange)
                            }
                        )
                    }
            ) {

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val tickCount = 8
                    val majorLength = 3.5.dp.toPx()
                    val minorLength = 2.dp.toPx()
                    for (i in 0..tickCount) {
                        val y = size.height * (i / tickCount.toFloat())
                        val isMajor = i % 2 == 0
                        val length = if (isMajor) majorLength else minorLength
                        val tickColor = if (isMajor) Color(0xFF565656) else Color(0xFF3A3A3A)
                        drawLine(tickColor, Offset(0f, y), Offset(length, y), strokeWidth = 1.dp.toPx())
                        drawLine(tickColor, Offset(size.width - length, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(6.dp)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF050505))
                        .border(0.5.dp, Color(0xFF2A2A2A), RoundedCornerShape(2.dp))
                ) {

                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(StrokeGold.copy(alpha = 0.7f))
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(kotlin.math.abs(normalizedGain - 0.5f) * 2f)
                            .align(if (gain >= 0) Alignment.BottomCenter else Alignment.TopCenter)
                            .background(
                                if (gain >= 0) GoldBright.copy(alpha = 0.35f)
                                else Color(0xFFE57373).copy(alpha = 0.25f)
                            )
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset {
                            IntOffset(
                                x = 0,
                                y = ((1f - normalizedGain) * (trackHeightPx - 12.dp.toPx())).toDouble().roundToInt()
                            )
                        }
                        .width(trackWidth)
                        .height(12.dp)
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
                        .border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(2.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(1.dp)
                            .background(Color(0xFF3A3A3A))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = frequency,
            color = TextMuted,
            fontFamily = MonoFont,
            fontSize = 6.sp
        )
    }
}

internal fun calculateNewGain(
    dragY: Float,
    trackHeight: Float,
    minGain: Double,
    maxGain: Double,
    onGainChange: (Double) -> Unit
) {
    val ratio = (dragY / trackHeight).coerceIn(0f, 1f)

    val newGain = maxGain - (ratio * (maxGain - minGain))
    onGainChange(newGain.coerceIn(minGain, maxGain))
}

internal fun Double.roundToInt(): Int = kotlin.math.round(this).toInt()
