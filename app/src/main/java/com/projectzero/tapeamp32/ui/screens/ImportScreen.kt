package com.projectzero.tapeamp32.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.projectzero.tapeamp32.R
import com.projectzero.tapeamp32.ui.theme.*
import com.projectzero.tapeamp32.viewmodel.PlayerViewModel

/* ================================================================
 * IMPORT SCREEN
 * ================================================================ */

@Composable
fun ImportScreen(
    vm: PlayerViewModel,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit
) {

    val library by vm.library.collectAsStateWithLifecycle()

    val isScanning by vm.isScanning.collectAsStateWithLifecycle(
        initialValue = false
    )

    val scanProgress by vm.scanProgress.collectAsStateWithLifecycle(
        initialValue = 0f
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBlack)
            .padding(
                horizontal = 14.dp,
                vertical = 12.dp
            ),
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        /* ========================================================
         * HEADER
         * ======================================================== */

        ImportHeader()

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        /* ========================================================
         * MAIN DROP / FOLDER AREA
         * ======================================================== */

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(
                    RoundedCornerShape(8.dp)
                )
                .background(
                    PanelBlackAlt
                )
                .drawDashedBorder(
                    color = StrokeGold,
                    cornerRadius = 8.dp
                )
                .clickable {
                    onPickFolder()
                },
            contentAlignment =
                Alignment.Center
        ) {

            Column(
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {

                /* ================================================
                 * FOLDER ICON
                 * ================================================ */

                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(
                            RoundedCornerShape(12.dp)
                        )
                        // PATCH (klasik): ikon folder sebelumnya di atas panel
                        // abu-abu gelap (#111515) -- diganti PanelBlackAlt + border
                        // emas tipis supaya senada dengan panel gold lain.
                        .background(
                            PanelBlackAlt
                        )
                        .border(
                            width = 1.dp,
                            color = StrokeGold,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment =
                        Alignment.Center
                ) {

                    Icon(
                        imageVector =
                            Icons.Filled.FolderOpen,
                        contentDescription = null,
                        tint = GoldBright,
                        modifier = Modifier.size(45.dp)
                    )

                    Icon(
                        imageVector =
                            Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = BgBlack,
                        modifier = Modifier
                            .offset(
                                x = 1.dp,
                                y = 5.dp
                            )
                            .size(16.dp)
                    )
                }

                Spacer(
                    modifier = Modifier.height(13.dp)
                )

                /* ================================================
                 * PRIMARY TEXT
                 * ================================================ */

                Text(
                    text =
                        stringResource(R.string.import_select_folder_title),
                    color = TextLight,
                    fontFamily = MonoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 0.3.sp
                )

                Spacer(
                    modifier = Modifier.height(5.dp)
                )

                Text(
                    text =
                        stringResource(R.string.import_select_folder_subtitle),
                    color = TextMuted,
                    fontFamily = DisplayFont,
                    fontSize = 11.sp
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text =
                        "MP3 • FLAC • WAV • M4A • DSF • DFF • OGG",
                    color = GoldBright.copy(
                        alpha = 0.75f
                    ),
                    fontFamily = MonoFont,
                    fontSize = 8.sp
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                /* ================================================
                 * SECURITY / SCAN NOTE
                 * ================================================ */

                Text(
                    text =
                        stringResource(R.string.import_folder_only_note),
                    color = TextMuted,
                    fontFamily = MonoFont,
                    fontSize = 8.sp
                )
            }
        }

        Spacer(
            modifier = Modifier.height(9.dp)
        )

        /* ========================================================
         * SINGLE FILE IMPORT
         * ======================================================== */

        ImportFileButton(
            onClick = onPickFiles
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        /* ========================================================
         * PROGRESS PANEL
         * ======================================================== */

        ImportProgressPanel(
            isScanning = isScanning,
            scanProgress = scanProgress,
            librarySize = library.size
        )
    }
}

/* ================================================================
 * HEADER
 * ================================================================ */

@Composable
private fun ImportHeader() {

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text = stringResource(R.string.import_header_title),
                color = GoldBright,
                fontFamily = MonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 0.5.sp
            )

            Spacer(
                modifier = Modifier.height(2.dp)
            )

            Text(
                text =
                    stringResource(R.string.import_header_subtitle),
                color = TextMuted,
                fontFamily = DisplayFont,
                fontSize = 9.sp
            )
        }

        Text(
            text = "TAPEAMP 32",
            color = TextMuted,
            fontFamily = MonoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 8.sp
        )
    }
}

/* ================================================================
 * SINGLE FILE BUTTON
 * ================================================================ */

@Composable
private fun ImportFileButton(
    onClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(
                RoundedCornerShape(5.dp)
            )
            // PATCH (klasik): tombol import file sebelumnya panel abu-abu
            // (#101313) polos tanpa border -- diganti PanelBlackAlt + border
            // emas supaya seragam dengan tombol/panel klasik lain.
            .background(
                PanelBlackAlt
            )
            .border(
                width = 0.8.dp,
                color = StrokeGold,
                shape = RoundedCornerShape(5.dp)
            )
            .clickable {
                onClick()
            }
            .padding(
                horizontal = 10.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Icon(
            imageVector =
                Icons.Filled.UploadFile,
            contentDescription = null,
            tint = GoldBright,
            modifier = Modifier.size(16.dp)
        )

        Spacer(
            modifier = Modifier.width(8.dp)
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text =
                    stringResource(R.string.import_select_files_title),
                color = TextLight,
                fontFamily = MonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
            )

            Text(
                text =
                    stringResource(R.string.import_select_files_subtitle),
                color = TextMuted,
                fontFamily = DisplayFont,
                fontSize = 8.sp
            )
        }

        Text(
            text = stringResource(R.string.import_open),
            color = GoldBright,
            fontFamily = MonoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 8.sp
        )
    }
}

/* ================================================================
 * PROGRESS PANEL
 * ================================================================ */

@Composable
private fun ImportProgressPanel(
    isScanning: Boolean,
    scanProgress: Float,
    librarySize: Int
) {

    val progress =
        scanProgress.coerceIn(
            0f,
            1f
        )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(5.dp)
            )
            .background(
                PanelBlackAlt
            )
            .padding(
                horizontal = 10.dp,
                vertical = 8.dp
            )
    ) {

        /* ========================================================
         * LABEL + PERCENT
         * ======================================================== */

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween,
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(
                text =
                    if (isScanning) {
                        stringResource(R.string.import_scanning)
                    } else {
                        stringResource(R.string.import_progress_label)
                    },
                color = TextMuted,
                fontFamily = MonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 8.sp
            )

            Text(
                text =
                    if (isScanning) {
                        "${(progress * 100).toInt()}%"
                    } else {
                        stringResource(R.string.import_files_count, librarySize)
                    },
                color = GoldBright,
                fontFamily = MonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 8.sp
            )
        }

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        /* ========================================================
         * PROGRESS BAR
         * ======================================================== */

        LinearProgressIndicator(
            progress = {
                if (isScanning) {
                    progress
                } else {
                    if (librarySize > 0) {
                        1f
                    } else {
                        0f
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(
                    RoundedCornerShape(3.dp)
                ),
            // PATCH (klasik): track abu-abu (#292C2C) diganti StrokeGold supaya
            // progress bar terlihat seperti meter amplifier klasik, bukan progress
            // bar Material biasa.
            color = GoldBright,
            trackColor = StrokeGold
        )

        Spacer(
            modifier = Modifier.height(5.dp)
        )

        /* ========================================================
         * STATUS
         * ======================================================== */

        Text(
            text =
                when {
                    isScanning ->
                        stringResource(R.string.import_please_wait)

                    librarySize > 0 ->
                        stringResource(R.string.import_library_available, librarySize)

                    else ->
                        stringResource(R.string.import_no_music_yet)
                },
            color = TextMuted,
            fontFamily = MonoFont,
            fontSize = 7.sp
        )
    }
}

/* ================================================================
 * DASHED BORDER
 * ================================================================ */

private fun Modifier.drawDashedBorder(
    color: Color,
    cornerRadius: androidx.compose.ui.unit.Dp
) = this.drawWithContent {

    drawContent()

    val stroke = Stroke(
        width = 1.5.dp.toPx(),
        pathEffect =
            PathEffect.dashPathEffect(
                floatArrayOf(
                    9f,
                    8f
                ),
                0f
            )
    )

    drawRoundRect(
        color = color,
        style = stroke,
        cornerRadius =
            androidx.compose.ui.geometry.CornerRadius(
                cornerRadius.toPx(),
                cornerRadius.toPx()
            )
    )
}
