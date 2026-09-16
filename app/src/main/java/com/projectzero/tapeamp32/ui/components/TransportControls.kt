package com.projectzero.tapeamp32.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectzero.tapeamp32.R
import com.projectzero.tapeamp32.ui.theme.*

@Composable
fun PowerSwitch(
    on: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = stringResource(R.string.transport_power),
            color = if (on) GoldBright else TextMuted,
            fontSize = 9.sp,
            fontFamily = MonoFont,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )

        Spacer(
            modifier = Modifier.height(5.dp)
        )

        Box(
            modifier = Modifier.size(22.dp),
            contentAlignment = Alignment.Center
        ) {

            if (on) {

                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    LedRed.copy(alpha = 0.50f),
                                    LedRed.copy(alpha = 0.16f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = if (on) {
                                listOf(
                                    Color(0xFFFF6B63),
                                    LedRed,
                                    Color(0xFF7D1118)
                                )
                            } else {
                                listOf(
                                    Color(0xFF321316),
                                    Color(0xFF1A0B0D)
                                )
                            }
                        )
                    )
                    .border(
                        1.dp,
                        if (on)
                            Color(0xFFFF9A96)
                        else
                            Color(0xFF321114),
                        CircleShape
                    )
            )
        }

        Spacer(
            modifier = Modifier.height(7.dp)
        )

        Box(
            modifier = Modifier
                .width(34.dp)
                .height(67.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF202020),
                            Color(0xFF0D0D0D),
                            Color(0xFF191919)
                        )
                    )
                )
                .border(
                    1.dp,
                    Color(0xFF3D3D3D),
                    RoundedCornerShape(4.dp)
                )
                .shadow(
                    elevation = 3.dp,
                    shape = RoundedCornerShape(4.dp),
                    clip = false
                )
                .clickable {
                    onToggle()
                }
                .padding(3.dp),
            contentAlignment =
                if (on)
                    Alignment.TopCenter
                else
                    Alignment.BottomCenter
        ) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Color(0xFF090909)
                    )
                    .border(
                        1.dp,
                        Color(0xFF292929),
                        RoundedCornerShape(3.dp)
                    )
                    .padding(2.dp),
                contentAlignment =
                    if (on)
                        Alignment.TopCenter
                    else
                        Alignment.BottomCenter
            ) {

                Box(
                    modifier = Modifier
                        .width(26.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.verticalGradient(
                                if (on) {
                                    listOf(
                                        GoldBright,
                                        Gold,
                                        Color(0xFF80631B)
                                    )
                                } else {
                                    listOf(
                                        Color(0xFF363636),
                                        Color(0xFF202020),
                                        Color(0xFF121212)
                                    )
                                }
                            )
                        )
                        .border(
                            1.dp,
                            if (on)
                                GoldBright
                            else
                                Color(0xFF4A4A4A),
                            RoundedCornerShape(3.dp)
                        )
                )
            }
        }
    }
}

@Composable
fun ScreenToggleButton(
    fullScreenOn: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = stringResource(R.string.transport_screen),
            color = if (fullScreenOn) GoldBright else TextMuted,
            fontSize = 9.sp,
            fontFamily = MonoFont,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )

        Spacer(
            modifier = Modifier.height(5.dp)
        )

        Box(
            modifier = Modifier.size(22.dp),
            contentAlignment = Alignment.Center
        ) {

            if (fullScreenOn) {

                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    GoldBright.copy(alpha = 0.50f),
                                    GoldBright.copy(alpha = 0.16f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = if (fullScreenOn) {
                                listOf(
                                    Color(0xFFFFE9A8),
                                    GoldBright,
                                    Color(0xFF7D5F17)
                                )
                            } else {
                                listOf(
                                    Color(0xFF332E14),
                                    Color(0xFF1A170A)
                                )
                            }
                        )
                    )
                    .border(
                        1.dp,
                        if (fullScreenOn)
                            Color(0xFFFFE9A8)
                        else
                            Color(0xFF332E14),
                        CircleShape
                    )
            )
        }

        Spacer(
            modifier = Modifier.height(7.dp)
        )

        Box(
            modifier = Modifier
                .width(34.dp)
                .height(67.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF202020),
                            Color(0xFF0D0D0D),
                            Color(0xFF191919)
                        )
                    )
                )
                .border(
                    1.dp,
                    Color(0xFF3D3D3D),
                    RoundedCornerShape(4.dp)
                )
                .shadow(
                    elevation = 3.dp,
                    shape = RoundedCornerShape(4.dp),
                    clip = false
                )
                .clickable {
                    onToggle()
                }
                .padding(3.dp),
            contentAlignment =
                if (fullScreenOn)
                    Alignment.TopCenter
                else
                    Alignment.BottomCenter
        ) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Color(0xFF090909)
                    )
                    .border(
                        1.dp,
                        Color(0xFF292929),
                        RoundedCornerShape(3.dp)
                    )
                    .padding(2.dp),
                contentAlignment =
                    if (fullScreenOn)
                        Alignment.TopCenter
                    else
                        Alignment.BottomCenter
            ) {

                Box(
                    modifier = Modifier
                        .width(26.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.verticalGradient(
                                if (fullScreenOn) {
                                    listOf(
                                        GoldBright,
                                        Gold,
                                        Color(0xFF80631B)
                                    )
                                } else {
                                    listOf(
                                        Color(0xFF363636),
                                        Color(0xFF202020),
                                        Color(0xFF121212)
                                    )
                                }
                            )
                        )
                        .border(
                            1.dp,
                            if (fullScreenOn)
                                GoldBright
                            else
                                Color(0xFF4A4A4A),
                            RoundedCornerShape(3.dp)
                        )
                )
            }
        }
    }
}
