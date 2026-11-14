package com.projectzero.tapeamp32.util

import com.projectzero.tapeamp32.data.Song
import com.projectzero.tapeamp32.desktop.shim.Context
import java.awt.Color
import java.awt.Desktop
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * ShareCardRenderer VERSI DESKTOP: kartu "now playing" sederhana (Java2D) disimpan sebagai PNG
 * di %APPDATA%\TapeAmp32\cache\share_cards lalu dibuka dengan penampil gambar bawaan Windows.
 * Desain kartu Android (reel kaset dsb.) belum dipindah.
 */
object ShareCardRenderer {

    fun renderAndShare(
        context: Context,
        song: Song,
        activePresetName: String?,
        sampleRateHz: Int,
        bitDepth: Int,
        isHiRes: Boolean
    ) {
        val img = BufferedImage(1080, 640, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        g.color = Color(0x12, 0x10, 0x0E)
        g.fillRect(0, 0, img.width, img.height)
        g.color = Color(0xD4, 0xA8, 0x43)
        g.drawRoundRect(24, 24, img.width - 48, img.height - 48, 28, 28)

        g.font = Font("SansSerif", Font.BOLD, 54)
        g.drawString(ellipsize(song.title, 28), 64, 190)
        g.font = Font("SansSerif", Font.PLAIN, 36)
        g.color = Color(0xE8, 0xE0, 0xD0)
        g.drawString(ellipsize(song.artist, 40), 64, 250)
        g.drawString(ellipsize(song.album, 40), 64, 300)

        g.font = Font("Monospaced", Font.BOLD, 30)
        g.color = Color(0x7F, 0xE0, 0x9A)
        val rate = if (sampleRateHz > 0) "%.1f kHz".format(sampleRateHz / 1000.0) else "--"
        g.drawString("${song.format}  $bitDepth-bit  $rate" + if (isHiRes) "  HI-RES" else "", 64, 420)
        if (!activePresetName.isNullOrBlank()) g.drawString("EQ: $activePresetName", 64, 470)

        g.font = Font("SansSerif", Font.BOLD, 28)
        g.color = Color(0xD4, 0xA8, 0x43)
        g.drawString("TapeAmp 32", 64, 570)
        g.dispose()

        val dir = File(context.cacheDir, "share_cards").apply { mkdirs() }
        val file = File(dir, "tapeamp32_share_${System.currentTimeMillis()}.png")
        ImageIO.write(img, "png", file)
        runCatching {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(file)
        }
    }

    private fun ellipsize(text: String, max: Int): String =
        if (text.length <= max) text else text.take(max - 1) + "…"
}
