package com.projectzero.tapeamp32.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectzero.tapeamp32.R
import com.projectzero.tapeamp32.ui.theme.*
import com.projectzero.tapeamp32.viewmodel.Screen

private data class NavItem(
    val screen: Screen,
    val labelRes: Int,
    val icon: ImageVector
)

private val navItems = listOf(
    NavItem(
        Screen.PLAYER,
        R.string.navrail_player,
        Icons.Filled.Album
    ),
    NavItem(
        Screen.EQUALIZER,
        R.string.navrail_equalizer,
        Icons.Filled.Tune
    ),
    NavItem(
        Screen.LIBRARY,
        R.string.navrail_library,
        Icons.Filled.MusicNote
    ),
    NavItem(
        Screen.IMPORT,
        R.string.navrail_import,
        Icons.Filled.Download
    ),
    // FITUR BARU: tab lirik sendiri, terpisah dari PLAYER, sesuai permintaan (bukan overlay).
    NavItem(
        Screen.LYRICS,
        R.string.navrail_lyrics,
        Icons.Filled.Lyrics
    ),
    NavItem(
        Screen.SETTINGS,
        R.string.navrail_settings,
        Icons.Filled.Settings
    )
)

@Composable
fun NavRail(
    current: Screen,
    onSelect: (Screen) -> Unit,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {

    Column(
        modifier = modifier
            .fillMaxHeight()
            // BARU (v1.5): lebih ramping + ikon saja (tanpa label teks) saat window
            // sedang dipersempit sistem (Split Screen/Multi-Window) -- lihat isCompact
            // di MainActivity.AppRoot -- supaya rail navigasi tidak memakan porsi besar
            // dari lebar yang sudah terbatas.
            .width(if (isCompact) 52.dp else 82.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF111414),
                        Color(0xFF080A0A),
                        Color(0xFF111313)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = StrokeGold.copy(alpha = 0.65f)
            )
            .padding(
                horizontal = 5.dp,
                vertical = 7.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        /*
         * ============================================================
         * NAVIGATION ITEMS
         * ============================================================
         */

        navItems.forEach { item ->

            val selected =
                current == item.screen ||
                    (
                        item.screen == Screen.SETTINGS &&
                            current == Screen.STREAMING
                    )

            NavRailItem(
                item = item,
                selected = selected,
                isCompact = isCompact,
                onClick = {
                    onSelect(item.screen)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

/* ================================================================
 * SINGLE NAV ITEM
 * ================================================================ */

@Composable
private fun NavRailItem(
    item: NavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {

        /*
         * ------------------------------------------------------------
         * ACTIVE PLATE -- BARU (patch "Skin Klasik NavRail/Toggle/Icon")
         * ------------------------------------------------------------
         *
         * Ambience glow lama diganti PLAT LOGAM bergaya tombol panel
         * hardware klasik: kotak bersudut tumpul dengan bezel gold tipis
         * + gradasi metalik gelap, supaya nav item aktif terasa seperti
         * tombol fisik yang "ditekan", bukan cuma cahaya samar di
         * belakang icon.
         */

        if (selected) {

            Box(
                modifier = Modifier
                    .size(
                        // BARU (v1.5): plat aktif sedikit lebih ramping saat isCompact,
                        // sejalan dengan lebar rail 52dp (bukan 82dp).
                        width = if (isCompact) 42.dp else 58.dp,
                        height = 72.dp
                    )
                    .clip(
                        RoundedCornerShape(10.dp)
                    )
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF2A2416),
                                Color(0xFF171410),
                                Color(0xFF0D0C09)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = GoldBright.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Gold.copy(alpha = 0.14f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.Center
        ) {

            /*
             * --------------------------------------------------------
             * ICON
             * --------------------------------------------------------
             * PATCH: icon diperbesar (23/25dp -> 25/29dp) dan plat kotak
             * di belakangnya diberi bezel logam tipis sendiri (bukan
             * cuma kotak transparan) supaya konsisten dengan gaya
             * "tombol klasik" di layar lain -- baik saat aktif maupun
             * tidak.
             * --------------------------------------------------------
             */

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) Color.Transparent else Color(0xFF141616)
                    )
                    .border(
                        width = if (selected) 0.dp else 0.7.dp,
                        color = Color(0xFF2A2D2D),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment =
                    Alignment.Center
            ) {

                Icon(
                    imageVector = item.icon,
                    contentDescription = stringResource(item.labelRes),
                    tint =
                        if (selected)
                            GoldBright
                        else
                            TextMuted.copy(
                                alpha = 0.90f
                            ),
                    modifier = Modifier.size(
                        if (selected)
                            29.dp
                        else
                            25.dp
                    )
                )
            }

            Spacer(
                modifier = Modifier.height(3.dp)
            )

            /*
             * --------------------------------------------------------
             * LABEL
             * --------------------------------------------------------
             * BARU (v1.5): disembunyikan saat isCompact (Split Screen/Multi-
             * Window) -- di rail selebar 52dp, label teks penuh gampang
             * terpotong/wrap; ikon saja sudah cukup jelas menunjukkan tab.
             * --------------------------------------------------------
             */

            if (!isCompact) {
                Text(
                    text = stringResource(item.labelRes),
                    color =
                        if (selected)
                            GoldBright
                        else
                            TextMuted,

                    fontSize = 8.sp,
                    fontWeight =
                        if (selected)
                            FontWeight.Bold
                        else
                            FontWeight.Medium,

                    fontFamily = MonoFont,
                    letterSpacing = 0.35.sp,
                    maxLines = 1
                )
            }

            /*
             * --------------------------------------------------------
             * ACTIVE INDICATOR
             * --------------------------------------------------------
             *
             * Garis kecil emas di bawah label.
             * Bukan box, sehingga tetap mengikuti mockup.
             */

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Box(
                modifier = Modifier
                    .width(
                        if (selected)
                            28.dp
                        else
                            10.dp
                    )
                    .height(
                        if (selected)
                            1.5.dp
                        else
                            1.dp
                    )
                    .clip(
                        RoundedCornerShape(1.dp)
                    )
                    .background(
                        if (selected)
                            GoldBright
                        else
                            Color(0xFF252727)
                    )
            )
        }
    }
}
