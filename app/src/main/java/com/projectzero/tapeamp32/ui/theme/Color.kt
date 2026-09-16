package com.projectzero.tapeamp32.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

val BgBlack = Color(0xFF0A0906)
val PanelBlack = Color(0xFF15130E)
val PanelBlackAlt = Color(0xFF1B1912)
val CreamPaper = Color(0xFFE9DCC0)

enum class ThemeAccent(
    val displayName: String,
    val gold: Color,
    val goldBright: Color,
    val goldDim: Color,
    val strokeGold: Color
) {
    GOLD_RETRO(
        displayName = "Gold Retro",
        gold = Color(0xFFD4AF37),
        goldBright = Color(0xFFF2C94C),
        goldDim = Color(0xFF8A6D2E),
        strokeGold = Color(0xFF3A2F1C)
    ),
    NEON_80S(
        displayName = "Neon 80s",
        gold = Color(0xFFDA2CF6),
        goldBright = Color(0xFF4CE0FF),
        goldDim = Color(0xFF8A2C9E),
        strokeGold = Color(0xFF3D1A4D)
    ),
    SILVER_HIFI(
        displayName = "Silver Hi-Fi",
        gold = Color(0xFFC7CDD1),
        goldBright = Color(0xFFF2F4F6),
        goldDim = Color(0xFF7C8388),
        strokeGold = Color(0xFF2E3235)
    );

    companion object {
        fun fromLabel(label: String): ThemeAccent =
            entries.firstOrNull { it.displayName == label } ?: GOLD_RETRO
    }
}

object ThemeAccentState {
    var current: ThemeAccent by mutableStateOf(ThemeAccent.GOLD_RETRO)
}

val Gold: Color get() = ThemeAccentState.current.gold
val GoldBright: Color get() = ThemeAccentState.current.goldBright
val GoldDim: Color get() = ThemeAccentState.current.goldDim
val StrokeGold: Color get() = ThemeAccentState.current.strokeGold
val TextLight = Color(0xFFEDE6D6)
val TextMuted = Color(0xFF9C927A)
val LedRed = Color(0xFFE63946)
val VuGreen = Color(0xFF7CFC7C)
val VuYellow = Color(0xFFE8D24C)
val VuRed = Color(0xFFE63946)

val VfdGlass = Color(0xFF04100D)
val VfdGlassDeep = Color(0xFF010705)
val VfdPhosphor = Color(0xFF5CFFC0)
val VfdPhosphorDim = Color(0xFF1D4C40)
val VfdPhosphorGhost = Color(0xFF10241F)
val VfdAmberPeak = Color(0xFFFF5A3C)
val VfdAmberPeakDim = Color(0xFF3A1E16)

val VfdCyanHiRes = Color(0xFF4CE0FF)
val VfdCyanHiResDim = Color(0xFF1A3A44)

data class CassetteSkin(
    val id: String,
    val label: String,
    val body: Color,
    val bodyDark: Color,
    val accent: Color,
    val labelBg: Color,
    val labelText: Color,
    val translucent: Boolean = false
)

val CassetteSkins = listOf(
    CassetteSkin("black_matte", "BLACK MATTE", Color(0xFF1C1C1C), Color(0xFF0E0E0E), Color(0xFF3A3A3A), Color(0xFFEDE6D6), Color(0xFF1C1C1C)),
    CassetteSkin("clear_transparent", "CLEAR TRANSPARENT", Color(0xFFB9C2C6), Color(0xFF8C979C), Color(0xFFE3E9EA), Color(0xFFEDE6D6), Color(0xFF1C1C1C), translucent = true),
    CassetteSkin("gold_chrome", "VINTAGE GOLD CHROME", Color(0xFFD4AF37), Color(0xFF8A6D2E), Color(0xFFF2C94C), Color(0xFF1C1608), Color(0xFFF2C94C)),
    CassetteSkin("neon_80s", "NEON 80S", Color(0xFF2A1B4D), Color(0xFF160D2B), Color(0xFFDA2CF6), Color(0xFF120823), Color(0xFF4CE0FF)),
    CassetteSkin("smoky_gray", "SMOKY GRAY", Color(0xFF4A4A4A), Color(0xFF2C2C2C), Color(0xFF6E6E6E), Color(0xFFEDE6D6), Color(0xFF2C2C2C)),
    CassetteSkin("retro_yellow", "RETRO YELLOW/ORANGE", Color(0xFFE0A020), Color(0xFFAF7412), Color(0xFFF6C453), Color(0xFF241606), CreamPaper),
    CassetteSkin("pure_white", "PURE WHITE", Color(0xFFEDEAE2), Color(0xFFC9C4B6), Color(0xFFFFFFFF), Color(0xFF1C1C1C), Color(0xFFEDE6D6)),
    CassetteSkin("deep_cobalt", "DEEP COBALT BLUE", Color(0xFF1F3F8F), Color(0xFF13275C), Color(0xFF4C7CFF), Color(0xFFEDE6D6), Color(0xFF13275C)),
    CassetteSkin("red_crimson", "RED CRIMSON", Color(0xFF8F1F2B), Color(0xFF5C131C), Color(0xFFE0333F), Color(0xFFEDE6D6), Color(0xFF5C131C)),
    CassetteSkin("beige_cream", "BEIGE CREAM", Color(0xFFE0C9A6), Color(0xFFB89E76), Color(0xFFF2E3C6), Color(0xFF241606), CreamPaper),
)
