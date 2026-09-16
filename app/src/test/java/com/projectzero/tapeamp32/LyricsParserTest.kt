package com.projectzero.tapeamp32

import com.projectzero.tapeamp32.data.parseLrc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test untuk parser .lrc. Ini logika murni (tidak menyentuh Android sama
 * sekali), tapi justru bagian yang paling sering bikin lirik "meleset
 * beberapa detik" -- kesalahan yang susah dilihat lewat testing manual
 * karena harus dengar sambil melototin layar.
 */
class LyricsParserTest {

    @Test
    fun `timestamp dasar dibaca jadi milidetik yang benar`() {
        val hasil = parseLrc("[00:12.50]Baris pertama")
        assertNotNull(hasil)
        assertEquals(1, hasil!!.size)
        assertEquals(12_500L, hasil[0].timeMs)
        assertEquals("Baris pertama", hasil[0].text)
    }

    @Test
    fun `menit ikut dihitung`() {
        val hasil = parseLrc("[03:07.25]Reff")!!
        assertEquals(3 * 60_000L + 7_000L + 250L, hasil[0].timeMs)
    }

    @Test
    fun `pecahan detik 1 2 dan 3 digit sama-sama benar`() {
        assertEquals(500L, parseLrc("[00:00.5]a")!![0].timeMs)
        assertEquals(500L, parseLrc("[00:00.50]a")!![0].timeMs)
        assertEquals(500L, parseLrc("[00:00.500]a")!![0].timeMs)
        assertEquals(0L, parseLrc("[00:00]a")!![0].timeMs)
    }

    @Test
    fun `pemisah titik dua juga diterima`() {
        assertEquals(1_250L, parseLrc("[00:01:25]a")!![0].timeMs)
    }

    @Test
    fun `satu baris dengan banyak timestamp jadi beberapa entri dengan teks sama`() {
        val hasil = parseLrc("[00:10.00][01:10.00]Reff diulang")!!
        assertEquals(2, hasil.size)
        assertEquals(10_000L, hasil[0].timeMs)
        assertEquals(70_000L, hasil[1].timeMs)
        assertTrue(hasil.all { it.text == "Reff diulang" })
    }

    @Test
    fun `hasil selalu terurut walau berkas lrc-nya acak`() {
        val hasil = parseLrc(
            """
            [00:30.00]ketiga
            [00:10.00]pertama
            [00:20.00]kedua
            """.trimIndent()
        )!!
        assertEquals(listOf("pertama", "kedua", "ketiga"), hasil.map { it.text })
    }

    @Test
    fun `baris metadata dan baris kosong tidak merusak hasil`() {
        val hasil = parseLrc(
            """
            [ar:Penyanyi]
            [ti:Judul]

            [00:05.00]mulai
            """.trimIndent()
        )!!
        // [ar:...] & [ti:...] bukan timestamp, jadi tidak boleh jadi baris lirik.
        assertEquals(1, hasil.size)
        assertEquals("mulai", hasil[0].text)
    }

    @Test
    fun `lirik tanpa timestamp dianggap tidak sinkron`() {
        assertNull(parseLrc("Cuma teks biasa\nTanpa waktu sama sekali"))
        assertNull(parseLrc(""))
    }
}
