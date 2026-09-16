package com.projectzero.tapeamp32

import com.projectzero.tapeamp32.data.ShuffleQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Yang dijaga di sini bukan "acaknya terasa acak", tapi hal yang lebih
 * penting dan objektif: shuffle TIDAK BOLEH menghilangkan atau menggandakan
 * lagu. Bug semacam itu di player musik muncul sebagai "ada lagu yang tidak
 * pernah kebagian diputar" -- gejala yang hampir mustahil ditemukan lewat
 * testing manual.
 */
class ShuffleQueueTest {

    private val lagu = (1..200).map { "lagu-$it" }

    @Test
    fun `isi antrian tetap sama persis setelah diacak`() {
        val hasil = ShuffleQueue.shuffled(lagu)
        assertEquals(lagu.size, hasil.size)
        assertEquals(lagu.toSet(), hasil.toSet())
        assertEquals("tidak boleh ada duplikat", hasil.size, hasil.distinct().size)
    }

    @Test
    fun `daftar kosong dan satu lagu tidak bikin error`() {
        assertEquals(emptyList<String>(), ShuffleQueue.shuffled(emptyList<String>()))
        assertEquals(listOf("solo"), ShuffleQueue.shuffled(listOf("solo")))
    }

    @Test
    fun `urutan benar-benar berubah untuk daftar panjang`() {
        // Peluang 200 lagu kebetulan kembali ke urutan awal praktis nol,
        // jadi kalau ini gagal berarti shuffle-nya tidak jalan sama sekali.
        assertNotEquals(lagu, ShuffleQueue.shuffled(lagu))
    }

    @Test
    fun `seed yang sama menghasilkan urutan yang sama`() {
        val a = ShuffleQueue.shuffled(lagu, Random(42))
        val b = ShuffleQueue.shuffled(lagu, Random(42))
        assertEquals(a, b)
    }

    @Test
    fun `daftar sumber tidak ikut berubah`() {
        val asli = lagu.toList()
        ShuffleQueue.shuffled(lagu)
        assertEquals(asli, lagu)
    }

    @Test
    fun `setiap lagu bisa mendarat di posisi pertama`() {
        // Fisher-Yates yang benar memberi peluang merata. Kalau implementasinya
        // bias (mis. salah batas indeks), sebagian lagu tidak akan pernah
        // muncul di posisi awal.
        val kecil = listOf("a", "b", "c", "d")
        val pertama = (1..2000).map { ShuffleQueue.shuffled(kecil, Random(it))[0] }.toSet()
        assertTrue("Ada lagu yang tidak pernah jadi urutan pertama: $pertama", pertama.size == 4)
    }
}
