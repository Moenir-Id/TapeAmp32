package com.projectzero.tapeamp32.ui.screens

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectzero.tapeamp32.ui.theme.*

// Sidebar item, section title, card, toggle and system-menu row composables
// used throughout the Settings screen. Split out of SettingsScreen.kt.

@Composable
internal fun SettingsSidebarItem(
    category: SettingsCategory,
    selected: Boolean,
    onClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(
                RoundedCornerShape(5.dp)
            )
            .background(
                if (selected) {
                    Gold
                } else {
                    Color.Transparent
                }
            )
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) {
                    GoldBright
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(5.dp)
            )
            .clickable {
                onClick()
            }
            .padding(
                horizontal = 10.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Icon(
            imageVector = category.icon,
            contentDescription = null,
            tint = if (selected) {
                BgBlack
            } else {
                TextMuted
            },
            modifier = Modifier.size(19.dp) // PATCH: 16dp -> 19dp, icon kategori settings
        )

        Spacer(
            modifier = Modifier.width(9.dp)
        )

        Text(
            text = stringResource(category.labelRes),
            color = if (selected) {
                BgBlack
            } else {
                TextLight
            },
            fontFamily = MonoFont,
            fontSize = 10.sp,
            fontWeight = if (selected) {
                FontWeight.Bold
            } else {
                FontWeight.Normal
            },
            letterSpacing = 0.15.sp,
            maxLines = 1
        )
    }
}

/* ================================================================
 * SYSTEM SUB-MENU ROW -- BARU: daftar sub-menu di dalam kategori SYSTEM
 * (mirip Settings > System bawaan Android): ikon + judul + deskripsi
 * singkat + chevron, tap untuk masuk ke sub-halamannya.
 * ================================================================ */

@Composable
internal fun SettingsSystemMenuRow(
    subPage: SettingsSystemSubPage,
    onClick: () -> Unit
) {

    SettingsRowCard {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .clickable { onClick() }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                imageVector = subPage.icon,
                contentDescription = null,
                tint = GoldBright,
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(subPage.titleRes),
                    color = TextLight,
                    fontFamily = DisplayFont,
                    fontSize = 11.sp
                )
                Text(
                    text = stringResource(subPage.descRes),
                    color = TextMuted,
                    fontFamily = MonoFont,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }

            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/* ================================================================
 * LANGUAGE ROW -- BARU: baris pilihan bahasa gaya radio button, dipakai
 * di System > Language.
 * ================================================================ */

@Composable
internal fun SettingsLanguageRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {

    SettingsRowCard {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(39.dp)
                .clickable { onClick() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text = label,
                color = if (selected) GoldBright else TextLight,
                fontFamily = DisplayFont,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 11.sp,
                maxLines = 1
            )

            Icon(
                imageVector = if (selected) {
                    Icons.Filled.RadioButtonChecked
                } else {
                    Icons.Filled.RadioButtonUnchecked
                },
                contentDescription = null,
                tint = if (selected) GoldBright else TextMuted,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

/* ================================================================
 * SECTION TITLE
 * ================================================================ */

@Composable
internal fun SettingsSectionTitle(
    text: String
) {

    Text(
        text = text,
        color = TextMuted,
        fontFamily = MonoFont,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(
            bottom = 3.dp
        )
    )
}

/* ================================================================
 * SETTINGS CARD
 * ================================================================ */

@Composable
internal fun SettingsRowCard(
    content: @Composable () -> Unit
) {

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                vertical = 2.dp
            )
            .clip(
                RoundedCornerShape(4.dp)
            )
            .background(
                PanelBlackAlt
            )
            // PATCH (klasik): SettingsRowCard dipakai di HAMPIR SEMUA baris
            // pengaturan -- border sebelumnya abu-abu flat (#292D2D), sekarang
            // StrokeGold (redup) supaya seluruh layar Settings konsisten dengan
            // panel EQ/knob yang sudah lebih dulu bergaya klasik.
            .border(
                width = 0.7.dp,
                color = StrokeGold.copy(alpha = 0.7f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(
                horizontal = 11.dp,
                vertical = 2.dp
            )
    ) {
        content()
    }
}

/* ================================================================
 * TOGGLE ROW
 * ================================================================ */

@Composable
internal fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {

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

            RetroToggle(
                checked = checked,
                onCheckedChange = onChange
            )
        }
    }
}

/* ================================================================
 * RETRO TOGGLE -- PATCH (Skin Klasik NavRail/Toggle/Icon)
 *
 * Versi lama pil hijau 29x16dp (warna hijau modern, tidak nyambung
 * dengan tema gold/kaset) diganti rocker switch metalik gaya panel
 * hardware klasik -- sama bahasa desainnya dengan EqRockerSwitch di
 * layar Equalizer, supaya toggle di Settings, Equalizer, dan menu
 * lain semuanya senada. Ukuran dibesarkan 29x16dp -> 46x24dp, knob
 * bulat metalik 18dp (dulu cuma titik putih 11dp).
 * ================================================================ */

@Composable
internal fun RetroToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {

    val trackWidth = 46.dp
    val trackHeight = 24.dp
    val knobSize = 18.dp
    val travel = trackWidth - knobSize - 4.dp

    Box(
        modifier = Modifier
            .size(width = trackWidth, height = trackHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    if (checked) {
                        listOf(Color(0xFF3A2E10), Color(0xFF1C1608))
                    } else {
                        listOf(Color(0xFF0E0E0E), Color(0xFF050505))
                    }
                )
            )
            .border(
                width = 1.dp,
                color = if (checked) GoldBright else Color(0xFF3A3A3A),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable {
                onCheckedChange(!checked)
            }
    ) {

        Box(
            modifier = Modifier
                .padding(start = 3.dp, top = 3.dp, bottom = 3.dp)
                .offset(x = if (checked) travel else 0.dp)
                .size(knobSize)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.verticalGradient(
                        if (checked) {
                            listOf(GoldBright, Gold, Color(0xFF7D5F17))
                        } else {
                            listOf(Color(0xFFC7C7C7), Color(0xFF8E8E8E), Color(0xFF5C5C5C))
                        }
                    )
                )
                .border(
                    width = 1.dp,
                    color = Color(0xFF2A2A2A),
                    shape = RoundedCornerShape(50)
                )
        )
    }
}

/* ================================================================
 * SLIDER ROW
 * ================================================================ */
