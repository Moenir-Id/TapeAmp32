package com.projectzero.tapeamp32.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * BARU (v2.4, "Waveform Seekbar"): pengganti Slider polos di PlayerTrackBar,
 * gambar bar waveform amplitude asli lagu (lihat WaveformExtractor) dan bisa
 * di-tap / di-drag langsung di atas bar-nya untuk seek -- gaya visual
 * scrubbing presisi ala aplikasi pemutar musik modern, bukan cuma garis/thumb builat seperti Slider
 * Material biasa.
 *
 * @param waveform data amplitude 0f..1f per bucket (lihat WaveformExtractor).
 *   NULL selagi masih loading/gagal -- ditampilkan sebagai garis datar tipis
 *   supaya seekbar tetap kelihatan & tetap bisa dipakai seek walau waveform
 *   belum siap (tidak nge-block interaksi user).
 * @param progressFraction posisi putar sekarang, 0f..1f.
 * @param onSeek dipanggil terus-menerus SELAMA drag/tap berlangsung dengan
 *   fraksi baru 0f..1f (dikonsumsi sama seperti onSeek Slider sebelumnya di
 *   PlayerTrackBar -- pemanggil tinggal kalikan ke durasi).
 */
@Composable
fun WaveformSeekBar(
    waveform: FloatArray?,
    progressFraction: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color(0xFFF2C94C),
    inactiveColor: Color = Color(0xFF3A352A),
    scrubLineColor: Color = Color(0xFFFFF6DE)
) {

    // FIX (pola sama dengan CassetteDeck onSeekDelta): onSeek adalah lambda BARU
    // tiap recompose (posisi playback berubah beberapa kali per detik), jadi
    // di-bungkus rememberUpdatedState supaya pointerInput() di bawah TIDAK perlu
    // di-restart tiap kali posisi berubah -- gesture drag tetap "nyambung".
    val currentOnSeek by rememberUpdatedState(onSeek)

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(progressFraction) }

    val displayFraction = if (isDragging) dragFraction else progressFraction

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                    currentOnSeek(fraction)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        isDragging = false
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onDrag = { change, _ ->
                        val fraction =
                            (change.position.x / size.width).coerceIn(0f, 1f)
                        dragFraction = fraction
                        currentOnSeek(fraction)
                        change.consume()
                    }
                )
            }
    ) {

        val w = size.width
        val h = size.height
        val midY = h / 2f

        val bars = waveform
        val activeX = w * displayFraction

        if (bars == null || bars.isEmpty()) {

            /*
             * FALLBACK: waveform belum siap (masih di-decode di background) atau
             * gagal di-decode -- tampilkan garis datar tipis, tetap merefleksikan
             * progress lewat warna, supaya seekbar tidak kelihatan kosong/rusak.
             */

            drawLine(
                color = inactiveColor,
                start = Offset(0f, midY),
                end = Offset(w, midY),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = activeColor,
                start = Offset(0f, midY),
                end = Offset(activeX, midY),
                strokeWidth = 2.dp.toPx()
            )

        } else {

            val barCount = bars.size
            val barSlotWidth = w / barCount
            // Beri sedikit celah antar bar (gaya ramping modern, bukan
            // blok penuh nyambung) -- minimal 1px supaya tidak hilang di layar
            // kerapatan rendah.
            val barWidth = max(1f, barSlotWidth * 0.62f)
            val minBarHeight = 2.dp.toPx()

            for (i in 0 until barCount) {

                val amplitude = bars[i].coerceIn(0f, 1f)
                val barHeight =
                    max(minBarHeight, amplitude * h)

                val barX = i * barSlotWidth + (barSlotWidth - barWidth) / 2f
                val barTop = midY - barHeight / 2f
                val barSize = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                val cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                    barWidth / 2.5f, barWidth / 2.5f
                )

                // FIX ("waveform kayak nunggu lalu loncat per-bar, tidak mulus"):
                // sebelumnya SATU bar cuma dicek posisi TENGAHNYA saja
                // (barCenterFraction <= displayFraction) -> tiap bar cuma dua kemungkinan,
                // aktif 100% atau nonaktif 100%. Karena tiap bar mewakili rentang waktu
                // yang lumayan lebar, hasilnya playhead terasa "diam" selama masih di
                // separuh awal bar, lalu tiba-tiba SELURUH bar itu berubah warna sekaligus
                // begitu playhead lewat titik tengahnya -- bukan nyapu halus.
                //
                // Sekarang tiap bar dicek rentang penuhnya (barStartFraction..barEndFraction).
                // Kalau playhead ada DI DALAM bar ini, bar itu digambar SETENGAH aktif
                // (potong tepat di fraksi playhead di dalam bar itu) + setengah nonaktif,
                // jadi batas warna bergerak halus mengikuti posisi persis playhead --
                // bukan cuma lompat per-bar.
                val barStartFraction = i.toFloat() / barCount
                val barEndFraction = (i + 1).toFloat() / barCount

                when {
                    displayFraction >= barEndFraction -> {
                        drawRoundRect(
                            color = activeColor,
                            topLeft = Offset(barX, barTop),
                            size = barSize,
                            cornerRadius = cornerRadius
                        )
                    }
                    displayFraction <= barStartFraction -> {
                        drawRoundRect(
                            color = inactiveColor,
                            topLeft = Offset(barX, barTop),
                            size = barSize,
                            cornerRadius = cornerRadius
                        )
                    }
                    else -> {
                        // Playhead persis di dalam bar ini -- gambar nonaktif dulu sebagai
                        // dasar (dengan sudut rounded utuh), lalu timpa bagian kiri sejauh
                        // fraksi playhead di dalam bar ini dengan warna aktif memakai clip
                        // rect, supaya sapuan warnanya presisi ke posisi playhead, bukan
                        // cuma menyala/mati per seluruh bar.
                        drawRoundRect(
                            color = inactiveColor,
                            topLeft = Offset(barX, barTop),
                            size = barSize,
                            cornerRadius = cornerRadius
                        )
                        val fillWithinBar =
                            ((displayFraction - barStartFraction) / (barEndFraction - barStartFraction))
                                .coerceIn(0f, 1f)
                        val activeWidth = barWidth * fillWithinBar
                        if (activeWidth > 0f) {
                            clipRect(left = barX, top = 0f, right = barX + activeWidth, bottom = h) {
                                drawRoundRect(
                                    color = activeColor,
                                    topLeft = Offset(barX, barTop),
                                    size = barSize,
                                    cornerRadius = cornerRadius
                                )
                            }
                        }
                    }
                }
            }
        }

        /*
         * GARIS SCRUB: ditampilkan HANYA selagi jari masih menyentuh (isDragging),
         * menampilkan indikator posisi presisi saat sedang di-drag --
         * tidak dipaksa selalu tampil supaya tidak menumpuk visual dengan bar
         * aktif/nonaktif selagi playback normal.
         */

        if (isDragging) {
            drawLine(
                color = scrubLineColor,
                start = Offset(activeX, 0f),
                end = Offset(activeX, h),
                strokeWidth = 1.5.dp.toPx()
            )
        }
    }
}
