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

private val TapeAmpTypography = Typography(
    bodyLarge = TextStyle(fontFamily = DisplayFont, fontSize = 14.sp, color = TextLight),
    bodyMedium = TextStyle(fontFamily = DisplayFont, fontSize = 12.sp, color = TextLight),
    labelSmall = LabelStyle.copy(fontSize = 9.sp, color = TextMuted)
)

@Composable
fun TapeAmp32Theme(content: @Composable () -> Unit) {

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
