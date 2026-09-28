package com.projectzero.tapeamp32.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.projectzero.tapeamp32.BuildConfig
import com.projectzero.tapeamp32.data.Song
import java.io.File
import java.io.FileOutputStream
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/*
 * BARU (fitur "share card"): render kartu "now playing" bergaya label kaset
 * jadi PNG, lalu di-share lewat Intent.ACTION_SEND. Sengaja gambar manual
 * pakai android.graphics.Canvas (bukan GraphicsLayer.toImageBitmap() dari
 * Compose 1.7+), karena compose-bom project ini masih 2024.06.00 -- upgrade
 * BOM cuma buat fitur ini resikonya ga sepadan, bisa ganggu komponen lain
 * yang sensitif (EqualizerFrequencyCurve, WaveformSeekBar, dll).
 *
 * Sengaja TIDAK menampilkan album art -- app ini dari awal ga pernah narik
 * cover art dari lagu (lihat Song.kt), jadi kartu ini fokus ke ilustrasi
 * kaset ala TapeAmp32 sendiri sebagai "identitas visual", bukan cover lagu.
 *
 * UPDATE (realism pass): kartu share ini sebelumnya cuma bentuk kotak +
 * 2 lingkaran polos -- jauh lebih sederhana dari CassetteDeck.kt yang
 * dipakai di layar player. Sekarang disamain: shell + label kertas +
 * jendela pita terpisah, reel dengan hub/gigi, lubang capstan/guide pin,
 * sekrup sudut, drop shadow, rim light, sapuan gloss, dan goresan wear
 * halus. Semua digambar manual (bukan capture dari CassetteDeck Composable
 * di layar player) supaya renderer ini tetap berdiri sendiri dan bisa
 * dipanggil dari background/coroutine tanpa nyentuh UI thread Compose.
 */
object ShareCardRenderer {

    private const val CARD_WIDTH = 1080
    private const val CARD_HEIGHT = 1080

    fun render(
        song: Song,
        activePresetName: String?,
        // BARU (fix "bitDepthOrRate hardcoded"): sebelumnya kartu share selalu
        // nulis "24-BIT / 96kHz" buat SEMUA lagu (dari Song.bitDepthOrRate yang
        // emang gak pernah diisi data asli di mana pun). Sekarang dipakai angka
        // REAL yang sama persis kayak yang ditampilkan live di VFD Status Panel
        // (PlayerManager.sampleRate/bitDepth/isHiRes, diambil dari
        // AudioTrackConfig ExoPlayer yang beneran dikirim ke hardware) --
        // pemanggil (PlayerScreen.kt) yang nyuplai nilai-nilai ini.
        sampleRateHz: Int,
        bitDepth: Int,
        isHiRes: Boolean
    ): Bitmap {
        val bmp = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Background gradient -- senada tema "Gold Retro" default cassette skin.
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, CARD_HEIGHT.toFloat(),
                intArrayOf(0xFF2A1F14.toInt(), 0xFF15100B.toInt()),
                null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), bgPaint)

        val shellRect = RectF(90f, 320f, CARD_WIDTH - 90f, 760f)
        val shellCorner = 48f
        val shellPath = Path().apply {
            addRoundRect(shellRect, shellCorner, shellCorner, Path.Direction.CW)
        }

        /*
         * SHELL -- drop shadow (setShadowLayer aman dipakai di sini karena
         * canvas ini nge-gambar ke Bitmap software, bukan hardware layer view,
         * jadi tidak kena batasan shadow layer Android biasa) + gradient body
         * (bukan warna flat) biar plastiknya kerasa punya kedalaman.
         */

        val shellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                shellRect.left, shellRect.top, shellRect.right, shellRect.bottom,
                intArrayOf(0xFF4A3A22.toInt(), 0xFF2E2313.toInt()),
                null, Shader.TileMode.CLAMP
            )
            setShadowLayer(40f, 0f, 22f, 0x8A000000.toInt())
        }
        canvas.drawRoundRect(shellRect, shellCorner, shellCorner, shellPaint)

        // Semua elemen di dalam shell (label, window, gloss, wear) dipotong
        // rapi mengikuti bentuk rounded-rect shell.
        canvas.save()
        canvas.clipPath(shellPath)

        // Label kertas (bagian atas shell) -- warna lebih terang & hangat
        // dari plastik shell, meniru label kertas kaset asli.
        val labelRect = RectF(
            shellRect.left + 16f,
            shellRect.top + 16f,
            shellRect.right - 16f,
            shellRect.top + 166f
        )
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                labelRect.left, labelRect.top, labelRect.left, labelRect.bottom,
                intArrayOf(0xFFEFDFB8.toInt(), 0xFFD9C393.toInt()),
                null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(labelRect, 20f, 20f, labelPaint)

        // Vignette label "menua" -- radial gradient hangat/gelap di pinggir.
        val labelVignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                (labelRect.left + labelRect.right) / 2f,
                (labelRect.top + labelRect.bottom) / 2f,
                labelRect.width() * 0.62f,
                intArrayOf(0x00000000, 0x00000000, 0x2E3A2A12),
                floatArrayOf(0f, 0.72f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(labelRect, 20f, 20f, labelVignettePaint)

        // Garis lipatan tipis pemisah label dan jendela pita.
        val foldPaint = Paint().apply {
            color = 0x40000000
            strokeWidth = 3f
        }
        canvas.drawLine(
            shellRect.left + 16f, labelRect.bottom + 12f,
            shellRect.right - 16f, labelRect.bottom + 12f,
            foldPaint
        )

        // Jendela pita (bawah) -- gelap, tempat 2 reel diputar.
        val windowRect = RectF(
            shellRect.left + 16f,
            labelRect.bottom + 24f,
            shellRect.right - 16f,
            shellRect.bottom - 16f
        )
        val windowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF14100A.toInt() }
        canvas.drawRoundRect(windowRect, 16f, 16f, windowPaint)

        // Dua reel: hub metalik + gigi sprocket + cincin pita yang tergulung.
        val reelY = (windowRect.top + windowRect.bottom) / 2f
        val reelRadius = windowRect.height() * 0.42f
        val hubRadius = reelRadius * 0.32f
        listOf(
            windowRect.left + windowRect.width() * 0.30f,
            windowRect.right - windowRect.width() * 0.30f
        ).forEach { cx ->
            drawReel(canvas, cx, reelY, reelRadius, hubRadius)
        }

        // Lubang capstan (besar) + guide pin (kecil) dekat bawah jendela pita.
        val holeY = windowRect.bottom - windowRect.height() * 0.10f
        listOf(
            windowRect.left + windowRect.width() * 0.26f,
            windowRect.right - windowRect.width() * 0.26f
        ).forEach { hx ->
            canvas.drawCircle(hx, holeY, windowRect.height() * 0.075f, windowPaint)
            canvas.drawCircle(
                hx - 4f, holeY - 4f, windowRect.height() * 0.075f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x14FFFFFF }
            )
        }
        listOf(
            windowRect.left + windowRect.width() * 0.42f,
            windowRect.left + windowRect.width() * 0.58f
        ).forEach { hx ->
            canvas.drawCircle(hx, holeY, windowRect.height() * 0.032f, windowPaint)
        }

        // Rim light: tepi atas shell sedikit terang, tepi bawah sedikit gelap.
        val rimPaint = Paint().apply {
            shader = LinearGradient(
                0f, shellRect.top, 0f, shellRect.bottom,
                intArrayOf(0x2EFFFFFF, 0x00000000, 0x00000000, 0x40000000),
                floatArrayOf(0f, 0.16f, 0.84f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(shellRect, rimPaint)

        // Sapuan gloss diagonal (plastik mengkilap kena cahaya dari sudut).
        val glossPaint = Paint().apply {
            shader = LinearGradient(
                shellRect.left, shellRect.top,
                shellRect.left + shellRect.width() * 0.55f, shellRect.bottom,
                intArrayOf(0x00FFFFFF, 0x22FFFFFF, 0x00FFFFFF),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(shellRect, glossPaint)

        // Goresan wear halus -- seed konsisten per lagu (title+artist), jadi
        // tiap kartu punya pola beda tapi gak berubah tiap kartu dirender ulang.
        val wearRandom = Random(song.title.hashCode() xor song.artist.hashCode())
        val wearPaint = Paint().apply { strokeWidth = 2f }
        repeat(7) {
            val x1 = shellRect.left + wearRandom.nextFloat() * shellRect.width()
            val y1 = shellRect.top + wearRandom.nextFloat() * shellRect.height()
            val angle = wearRandom.nextFloat() * 360f
            val len = shellRect.width() * (0.05f + wearRandom.nextFloat() * 0.09f)
            val rad = Math.toRadians(angle.toDouble())
            val x2 = x1 + (cos(rad) * len).toFloat()
            val y2 = y1 + (sin(rad) * len).toFloat()
            wearPaint.color =
                ((0x14 + wearRandom.nextInt(12)) shl 24) or 0xFFFFFF
            canvas.drawLine(x1, y1, x2, y2, wearPaint)
        }

        canvas.restore()

        // Sekrup Phillips di 4 sudut shell -- digambar di luar clip (posisinya
        // sudah pasti di dalam bounds shell secara matematis, tidak perlu clip).
        val screwRadius = 16f
        val screwInset = 34f
        listOf(
            shellRect.left + screwInset to shellRect.top + screwInset,
            shellRect.right - screwInset to shellRect.top + screwInset,
            shellRect.left + screwInset to shellRect.bottom - screwInset,
            shellRect.right - screwInset to shellRect.bottom - screwInset
        ).forEach { (sx, sy) ->
            canvas.drawCircle(
                sx, sy, screwRadius * 1.3f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x59000000 }
            )
            canvas.drawCircle(
                sx, sy, screwRadius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        sx - screwRadius * 0.3f, sy - screwRadius * 0.3f,
                        screwRadius * 1.6f,
                        intArrayOf(0xFFC7C7C7.toInt(), 0xFF6E6E6E.toInt(), 0xFF2A2A2A.toInt()),
                        null, Shader.TileMode.CLAMP
                    )
                }
            )
            val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xBF000000.toInt()
                strokeWidth = 3f
            }
            canvas.drawLine(sx - screwRadius * 0.6f, sy, sx + screwRadius * 0.6f, sy, slotPaint)
            canvas.drawLine(sx, sy - screwRadius * 0.6f, sx, sy + screwRadius * 0.6f, slotPaint)
        }

        // Title & artist.
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF5E6C8.toInt()
            textSize = 64f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFCBB78A.toInt()
            textSize = 42f
        }
        val title = ellipsize(song.title, titlePaint, CARD_WIDTH - 180f)
        val artist = ellipsize(song.artist, artistPaint, CARD_WIDTH - 180f)
        canvas.drawText(title, 90f, 900f, titlePaint)
        canvas.drawText(artist, 90f, 955f, artistPaint)

        // Format / preset chip line -- pakai sample rate/bit depth REAL (lihat
        // catatan di parameter render(), bukan Song.bitDepthOrRate yang statis).
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF8A7550.toInt()
            textSize = 32f
        }
        val metaLine = buildString {
            append(song.format)
            // sampleRateHz <= 0 berarti info belum sempat diketahui (mis. share
            // dipicu sebelum AudioTrack sempat init) -- jangan tampilkan angka
            // karangan, cukup lewati bagian ini.
            if (sampleRateHz > 0) {
                append(" \u00B7 ")
                append(com.projectzero.tapeamp32.ui.screens.formatAudioSpec(sampleRateHz, bitDepth, isHiRes))
            }
            if (!activePresetName.isNullOrBlank()) {
                append(" \u00B7 EQ: ")
                append(activePresetName)
            }
        }
        canvas.drawText(metaLine, 90f, 1000f, metaPaint)

        // Watermark.
        val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFD9A441.toInt()
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        canvas.drawText("TAPEAMP32", 90f, 150f, watermarkPaint)

        return bmp
    }

    /**
     * Satu reel lengkap: cincin pita tergulung (statis 50/50, kartu share
     * tidak butuh progres playback yang presisi) + hub metalik + gigi
     * sprocket di sekeliling hub, sama seperti detail di CassetteDeck.kt.
     */
    private fun drawReel(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        hubRadius: Float
    ) {
        // Cincin pita tergulung.
        canvas.drawCircle(
            cx, cy, radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cx, cy, radius,
                    intArrayOf(0xFF4A3A22.toInt(), 0xFF1C160C.toInt()),
                    null, Shader.TileMode.CLAMP
                )
            }
        )

        // Hub metalik pusat reel -- gradient dipertipis kontrasnya (dari
        // versi sebelumnya) supaya tidak "ramai" dan bikin garis gigi susah
        // kebaca; sekarang cuma variasi terang-gelap yang halus.
        canvas.drawCircle(
            cx, cy, hubRadius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cx - hubRadius * 0.3f, cy - hubRadius * 0.3f, hubRadius * 1.6f,
                    intArrayOf(0xFFBBA671.toInt(), 0xFF8A754A.toInt()),
                    null, Shader.TileMode.CLAMP
                )
            }
        )

        // Titik pusat hub (dudukan poros) -- gelap, jadi "sumbu" tempat
        // gigi-gigi sprocket kelihatan memancar keluar darinya.
        canvas.drawCircle(
            cx, cy, hubRadius * 0.34f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF14100A.toInt() }
        )

        // Gigi sprocket -- 6 GARIS RADIAL pendek (bukan titik/lingkaran).
        // FIX ke-2: percobaan pertama masih kelihatan kayak titik karena
        // pakai Paint.Cap.ROUND (ujung membulat) + strokeWidth yang terlalu
        // besar dibanding panjangnya, jadi tiap "garis" malah jadi oval/pil
        // yang keliatan bulat dari jauh. Sekarang: Cap.BUTT (ujung rata,
        // sama kayak default Compose drawLine di CassetteDeck.kt), garis
        // dimulai HAMPIR DARI TENGAH hub (bukan mengambang jadi cincin
        // terpisah), dan strokeWidth jauh lebih tipis relatif ke
        // panjangnya -- biar jelas terbaca sebagai jari-jari/spoke.
        val teethPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF050505.toInt()
            strokeWidth = hubRadius * 0.26f
            strokeCap = Paint.Cap.BUTT
        }
        for (i in 0 until 6) {
            val angle = Math.toRadians((i * 60).toDouble())
            val innerR = hubRadius * 0.30f
            val outerR = hubRadius * 1.45f
            val x1 = cx + (cos(angle) * innerR).toFloat()
            val y1 = cy + (sin(angle) * innerR).toFloat()
            val x2 = cx + (cos(angle) * outerR).toFloat()
            val y2 = cy + (sin(angle) * outerR).toFloat()
            canvas.drawLine(x1, y1, x2, y2, teethPaint)
        }

        // Rim tipis di tepi luar reel biar kelihatan cembung.
        canvas.drawCircle(
            cx, cy, radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                color = 0x33D9A441
            }
        )
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var s = text
        while (s.isNotEmpty() && paint.measureText("$s\u2026") > maxWidth) {
            s = s.dropLast(1)
        }
        return "$s\u2026"
    }

    /**
     * Render, tulis ke cache dir, dan langsung buka share sheet.
     * Panggil dari Composable lewat LocalContext.current.
     */
    fun renderAndShare(
        context: Context,
        song: Song,
        activePresetName: String?,
        sampleRateHz: Int,
        bitDepth: Int,
        isHiRes: Boolean
    ) {
        val bmp = render(song, activePresetName, sampleRateHz, bitDepth, isHiRes)

        val cacheDir = File(context.cacheDir, "share_cards").apply { mkdirs() }
        val file = File(cacheDir, "tapeamp32_share_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share via"))
    }
}

