package com.projectzero.tapeamp32.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val MonoFont = FontFamily.Monospace
val DisplayFont = FontFamily.SansSerif

val LabelStyle = TextStyle(
    fontFamily = MonoFont,
    fontWeight = FontWeight.Bold,
    letterSpacing = 1.5.sp
)

// Menyuplai Typography bawaan agar otomatis menggunakan font Retro Hi-Fi
private val TapeAmpTypography = Typography(
    bodyLarge = TextStyle(fontFamily = DisplayFont, fontSize = 14.sp, color = TextLight),
    bodyMedium = TextStyle(fontFamily = DisplayFont, fontSize = 12.sp, color = TextLight),
    labelSmall = LabelStyle.copy(fontSize = 9.sp, color = TextMuted)
)

@Composable
fun TapeAmp32Theme(content: @Composable () -> Unit) {
    // PATCH (v1.4): dipindah ke dalam fungsi @Composable (sebelumnya top-level
    // private val yang cuma dihitung SEKALI) supaya darkColorScheme ikut dihitung
    // ULANG setiap Gold/GoldDim berubah (mis. saat user ganti Theme Accent Color di
    // Settings) -- kalau tetap top-level val, MaterialTheme tidak akan pernah tahu
    // accent berubah walau Gold/GoldDim sendiri sudah reactive.
    val colorScheme = darkColorScheme(
        primary = Gold,
        secondary = GoldDim,
        background = BgBlack,
        surface = PanelBlack,
        onPrimary = BgBlack,
        onSecondary = TextLight,
        onBackground = TextLight,
        onSurface = TextLight,
        error = LedRed
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TapeAmpTypography,
        content = content
    )
}
