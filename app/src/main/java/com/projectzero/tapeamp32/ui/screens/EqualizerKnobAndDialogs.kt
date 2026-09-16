package com.projectzero.tapeamp32.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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

// The rotary EqKnob control plus the save/delete preset dialogs used by the
// Equalizer screen. Split out of EqualizerScreen.kt.

@Composable
internal fun EqKnob(
    label: String,
    value: Double,
    minValue: Double,
    maxValue: Double,
    valueLabel: String,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    centerValue: Double? = null,
    // PATCH (v1.6, "proporsional ke layar"): dulu selalu 84.dp keras (lihat
    // catatan lama di atas -- 64->76->84dp). Sekarang jadi parameter dengan
    // default 84dp yang sama persis (tidak mengubah tampilan lama kalau
    // dipanggil tanpa argumen ini), tapi EqualizerScreen mengirim ukuran
    // yang sudah diskalakan ke lebar layar (lihat eqKnobSize).
    knobSize: androidx.compose.ui.unit.Dp = 84.dp
) {
    val minAngleDeg = -135.0
    val maxAngleDeg = 135.0
    val range = (maxValue - minValue).coerceAtLeast(0.0001)
    val fraction = ((value - minValue) / range).coerceIn(0.0, 1.0)
    val angleDeg = minAngleDeg + fraction * (maxAngleDeg - minAngleDeg)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = valueLabel,
            color = GoldBright,
            fontFamily = MonoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 8.sp
        )

        Spacer(modifier = Modifier.height(5.dp))

        // PATCH (Skin Klasik / Slider-Knob-Toggle): knop diperbesar lagi
        // (64dp -> 76dp) -- versi 64dp masih terasa kecil dibanding rocker
        // switch & fader baru di layar yang sama, jadi disamakan skalanya
        // supaya seluruh panel EQ terasa satu keluarga "hardware klasik".
        // PATCH (v1.5): diperbesar SEDIKIT lagi (76dp -> 84dp) khusus untuk
        // knop-knop di tab lanjutan VOCAL & STEREO (Vocal Bass/Treble,
        // Balance, Stereo Expansion) -- dikeluhkan masih terasa kecil untuk
        // diputar dengan jari dibanding kontrol lain di sekitarnya.
        Box(
            modifier = Modifier
                .size(knobSize)
                .pointerInput(minValue, maxValue) {
                    // Konversi posisi sentuh (relatif ke pusat knop) jadi sudut,
                    // lalu langsung ke nilai -- BUKAN akumulasi jarak drag.
                    val cx = size.width / 2f
                    val cy = size.height / 2f

                    fun updateFromPosition(pos: Offset) {
                        val dx = (pos.x - cx).toDouble()
                        val dy = (pos.y - cy).toDouble()
                        // atan2(dx, -dy): 0 derajat = arah jam 12 (atas), positif
                        // searah jarum jam -- sama persis dengan konvensi yang
                        // dipakai untuk menggambar jarum penunjuk di bawah.
                        val rawAngle = Math.toDegrees(kotlin.math.atan2(dx, -dy))
                        val clampedAngle = rawAngle.coerceIn(minAngleDeg, maxAngleDeg)
                        val newFraction = (clampedAngle - minAngleDeg) / (maxAngleDeg - minAngleDeg)
                        val newValue = minValue + newFraction * (maxValue - minValue)
                        onValueChange(newValue.coerceIn(minValue, maxValue))
                    }

                    detectDragGestures(
                        onDragStart = { offset -> updateFromPosition(offset) },
                        onDrag = { change, _ ->
                            change.consume()
                            updateFromPosition(change.position)
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val outerRadius = size.minDimension / 2f

                // ------------------------------------------------------
                // Bayangan halus di bawah knop supaya terlihat "timbul"
                // dari panel, bukan gambar datar.
                // ------------------------------------------------------
                drawCircle(
                    color = Color.Black.copy(alpha = 0.4f),
                    radius = outerRadius - 1.dp.toPx(),
                    center = center + Offset(0f, 1.5.dp.toPx())
                )

                // ------------------------------------------------------
                // Skala tick melingkar (mirip pelat potensiometer klasik) --
                // digambar SEBELUM badan knop supaya badan menutupi pangkal
                // tick dan hanya ujungnya yang terlihat mencuat di tepi.
                // ------------------------------------------------------
                val tickCount = 11
                for (i in 0 until tickCount) {
                    val t = i / (tickCount - 1).toDouble()
                    val tickAngle = minAngleDeg + t * (maxAngleDeg - minAngleDeg)
                    val rad = Math.toRadians(tickAngle).toFloat()
                    val isEdgeOrCenterTick = i == 0 || i == tickCount - 1 ||
                        (centerValue != null &&
                            kotlin.math.abs(
                                tickAngle - (minAngleDeg + ((centerValue - minValue) / range)
                                    .coerceIn(0.0, 1.0) * (maxAngleDeg - minAngleDeg))
                            ) < 1.0)
                    val outerP = Offset(
                        center.x + outerRadius * kotlin.math.sin(rad),
                        center.y - outerRadius * kotlin.math.cos(rad)
                    )
                    val innerP = Offset(
                        center.x + (outerRadius - if (isEdgeOrCenterTick) 5.dp.toPx() else 3.dp.toPx()) * kotlin.math.sin(rad),
                        center.y - (outerRadius - if (isEdgeOrCenterTick) 5.dp.toPx() else 3.dp.toPx()) * kotlin.math.cos(rad)
                    )
                    drawLine(
                        color = if (isEdgeOrCenterTick) GoldBright else TextMuted,
                        start = innerP,
                        end = outerP,
                        strokeWidth = if (isEdgeOrCenterTick) 1.4.dp.toPx() else 0.8.dp.toPx()
                    )
                }

                // ------------------------------------------------------
                // Badan knop -- gradasi radial mensimulasikan logam disorot
                // dari kiri-atas (highlight terang) ke kanan-bawah (gelap),
                // gaya knop amplifier/tape deck jadul.
                // ------------------------------------------------------
                val bodyRadius = outerRadius - 7.dp.toPx()
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF5A5852),
                            Color(0xFF3A3834),
                            Color(0xFF171613)
                        ),
                        center = center + Offset(-bodyRadius * 0.35f, -bodyRadius * 0.35f),
                        radius = bodyRadius * 1.8f
                    ),
                    radius = bodyRadius,
                    center = center
                )
                // Cincin gold tipis di tepi badan knop
                drawCircle(
                    color = StrokeGold,
                    radius = bodyRadius,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )

                // ------------------------------------------------------
                // Jarum penunjuk -- tebal, ujung bulat, dari dekat pusat
                // sampai mendekati tepi badan knop (bukan dari titik pusat
                // persis, meniru jarum knop fisik yang "menempel" di badan).
                // ------------------------------------------------------
                val rad = Math.toRadians(angleDeg).toFloat()
                val pointerStartR = bodyRadius * 0.22f
                val pointerEndR = bodyRadius * 0.88f
                val startP = Offset(
                    center.x + pointerStartR * kotlin.math.sin(rad),
                    center.y - pointerStartR * kotlin.math.cos(rad)
                )
                val endP = Offset(
                    center.x + pointerEndR * kotlin.math.sin(rad),
                    center.y - pointerEndR * kotlin.math.cos(rad)
                )
                drawLine(
                    color = GoldBright,
                    start = startP,
                    end = endP,
                    strokeWidth = 2.6.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Tudung tengah (cap) -- lingkaran kecil menutupi pangkal jarum,
                // memberi kesan knop punya "poros" seperti barang fisik.
                drawCircle(
                    color = Color(0xFF201F1C),
                    radius = bodyRadius * 0.3f,
                    center = center
                )
                drawCircle(
                    color = StrokeGold.copy(alpha = 0.8f),
                    radius = bodyRadius * 0.3f,
                    center = center,
                    style = Stroke(width = 0.8.dp.toPx())
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            color = TextMuted,
            fontFamily = MonoFont,
            // PATCH (v1.6, "seragamkan font"): disamakan dengan label kecil
            // sejenis di bawah kontrol lain (label frekuensi slider, teks
            // ON/OFF rocker, skala dB -- semuanya 6sp), sebelumnya 6.5sp
            // sendirian di label knop ini.
            fontSize = 6.sp,
            letterSpacing = 0.2.sp
        )
    }
}

// ================================================================
// SAVE DIALOG - SEPARATE COMPOSABLE
// ================================================================

@Composable
internal fun SavePresetDialog(
    saveNameInput: String,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelBlack,
        // PATCH (klasik): dialog dibungkus border emas + sudut kotak-membulat
        // supaya seragam dengan panel EQ/knob lain, bukan dialog Material polos.
        shape = RoundedCornerShape(7.dp),
        modifier = Modifier.border(
            width = 1.dp,
            color = StrokeGold,
            shape = RoundedCornerShape(7.dp)
        ),
        title = {
            Text(
                text = stringResource(R.string.eq_save_dialog_title),
                fontFamily = MonoFont,
                fontWeight = FontWeight.Bold,
                color = GoldBright
            )
        },
        text = {
            OutlinedTextField(
                value = saveNameInput,
                onValueChange = onNameChange,
                singleLine = true,
                label = {
                    Text(
                        text = stringResource(R.string.eq_preset_name_label),
                        fontFamily = MonoFont,
                        color = TextMuted
                    )
                },
                shape = RoundedCornerShape(4.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextLight,
                    unfocusedTextColor = TextLight,
                    focusedBorderColor = GoldBright,
                    unfocusedBorderColor = StrokeGold,
                    cursorColor = GoldBright,
                    focusedContainerColor = PanelBlackAlt,
                    unfocusedContainerColor = PanelBlackAlt,
                    focusedLabelColor = GoldBright,
                    unfocusedLabelColor = TextMuted
                ),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = MonoFont,
                    fontSize = 12.sp
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.eq_save_button),
                    fontFamily = MonoFont,
                    fontWeight = FontWeight.Bold,
                    color = GoldBright
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.eq_cancel_button),
                    fontFamily = MonoFont,
                    color = TextMuted
                )
            }
        }
    )
}

// ================================================================
// DELETE PRESET DIALOG -- BARU (fitur "hapus preset")
// Konfirmasi sebelum menghapus preset custom (hasil SAVE/UPLOAD) secara
// permanen dari daftar & DataStore. Preset bawaan tidak pernah sampai ke
// dialog ini -- lihat customPresetNames di EqualizerScreen & EqualizerHeader,
// yang menyaring supaya ikon hapus cuma muncul di preset custom.
// ================================================================

@Composable
internal fun DeletePresetDialog(
    presetName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelBlack,
        shape = RoundedCornerShape(7.dp),
        modifier = Modifier.border(
            width = 1.dp,
            color = StrokeGold,
            shape = RoundedCornerShape(7.dp)
        ),
        title = {
            Text(
                text = stringResource(R.string.eq_delete_preset_title),
                fontFamily = MonoFont,
                fontWeight = FontWeight.Bold,
                color = GoldBright
            )
        },
        text = {
            Text(
                text = stringResource(R.string.eq_delete_preset_body, presetName),
                fontFamily = MonoFont,
                fontSize = 11.sp,
                color = TextMuted
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.eq_delete_button),
                    fontFamily = MonoFont,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE57373)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.eq_cancel_button),
                    fontFamily = MonoFont,
                    color = TextMuted
                )
            }
        }
    )
}

// ================================================================
// HEADER
// ================================================================
