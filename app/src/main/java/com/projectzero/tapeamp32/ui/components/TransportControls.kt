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

/* ============================================================
 * POWER SWITCH
 * ============================================================
 * FIX: sebelumnya panel ini (ditambah PlayerSidePanel di PlayerScreen.kt
 * yang memanggilnya) menampilkan label "POWER" DAN teks "ON"/"OFF" DUA
 * KALI (satu di luar, satu lagi di dalam komponen ini) -- menumpuk dan
 * berantakan. Sekarang PowerSwitch HANYA menampilkan SATU label teks
 * ("POWER"). Status ON/OFF cukup ditunjukkan lewat LED merah + posisi
 * fisik saklar toggle di bawahnya -- tidak perlu diulang lagi sebagai teks.
 * ============================================================ */

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

        /*
         * POWER LABEL (SATU-SATUNYA teks indikator di panel ini;
         * status ON/OFF ditunjukkan oleh LED + posisi saklar di bawah)
         */

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

        /*
         * RED POWER LED
         */

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

        /*
         * PHYSICAL TOGGLE
         */

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

            /*
             * INNER TRACK
             */

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

                /*
                 * PHYSICAL SWITCH KNOB
                 */

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

/* ============================================================
 * SCREEN TOGGLE BUTTON (FIX -- saklar fisik, gaya sama dengan PowerSwitch)
 * ============================================================
 * Sebelumnya berupa tombol kotak dengan ikon Fullscreen saja -- sekarang
 * dibangun ulang memakai struktur SAMA PERSIS dengan PowerSwitch (label
 * teks di atas, LED bulat, lalu saklar toggle fisik naik/turun di
 * bawahnya) supaya kedua tombol terlihat sepasang/senada. Bedanya cuma
 * warna LED & knob: SCREEN pakai warna emas (Gold/GoldBright), POWER
 * pakai merah (LedRed) -- dan travel saklarnya lebih pendek karena
 * SCREEN tidak butuh ventilasi grille di bawahnya seperti PowerSwitch.
 *
 * Fungsinya tetap 100% untuk toggle Immersive/Full Screen Mode lewat
 * FullScreenController.
 * ============================================================ */

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

        /*
         * SCREEN LABEL (persis pola PowerSwitch: satu-satunya teks
         * indikator di komponen ini)
         */

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

        /*
         * GOLD SCREEN LED (versi emas dari LED merah PowerSwitch)
         * FIX: disamakan ukurannya dengan LED PowerSwitch (22dp/9dp)
         */

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

        /*
         * PHYSICAL TOGGLE (FIX: disamakan ukurannya persis dengan
         * saklar fisik PowerSwitch -- 34dp x 67dp, knob 26dp x 28dp)
         */

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

            /*
             * INNER TRACK
             */

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

                /*
                 * PHYSICAL SWITCH KNOB
                 */

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

/* ============================================================
 * NOTE: VerticalVolumeSlider dipindah ke TransportBar.kt.
 * ============================================================ */
