package com.projectzero.tapeamp32

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Menjaga supaya SETIAP string yang ada di values/strings.xml (bahasa Inggris,
 * sumber kebenaran) juga ada di 9 berkas terjemahan lainnya.
 *
 * Kenapa test ini ada: bug "teks baru lupa diterjemahkan" sudah berulang di
 * app ini -- v1.5, v2.2/v2.3, lalu v2.6 (tombol notifikasi SHUFFLE/CLOSE dan
 * label slider headroom EQ cuma ada dalam bahasa Inggris, bahkan values-in
 * pun tidak punya). Gejalanya halus: app tetap build, tetap jalan, tidak
 * crash -- Android diam-diam jatuh ke teks Inggris. Jadi satu-satunya cara
 * menangkapnya adalah lewat test seperti ini, bukan lewat testing manual.
 *
 * Test ini murni JVM: tidak butuh HP, emulator, maupun Android SDK.
 * Jalankan dengan: gradlew testDebugUnitTest
 */
class StringResourceParityTest {

    private fun resDir(): File {
        // Gradle menjalankan unit test dengan working directory = folder modul (app/),
        // tapi sebagian IDE menjalankannya dari root project. Dukung keduanya.
        val fromModule = File("src/main/res")
        return if (fromModule.isDirectory) fromModule else File("app/src/main/res")
    }

    private fun stringKeys(file: File): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length)
            .mapNotNull { nodes.item(it).attributes.getNamedItem("name")?.nodeValue }
            .toSet()
    }

    @Test
    fun `semua string bahasa Inggris punya terjemahan di setiap bahasa`() {
        val res = resDir()
        assertTrue("Folder res tidak ketemu dari ${File(".").absolutePath}", res.isDirectory)

        val baseKeys = stringKeys(File(res, "values/strings.xml"))
        assertTrue("values/strings.xml kosong?", baseKeys.isNotEmpty())

        val localeDirs = res.listFiles { f ->
            f.isDirectory && f.name.startsWith("values-") && File(f, "strings.xml").exists()
        }.orEmpty().sortedBy { it.name }

        assertTrue("Tidak ada folder terjemahan sama sekali", localeDirs.isNotEmpty())

        val laporan = StringBuilder()
        localeDirs.forEach { dir ->
            val missing = (baseKeys - stringKeys(File(dir, "strings.xml"))).sorted()
            if (missing.isNotEmpty()) {
                laporan.append("\n  ${dir.name} kurang ${missing.size}: ${missing.joinToString(", ")}")
            }
        }

        assertTrue(
            "Ada string yang belum diterjemahkan (user bahasa itu akan lihat teks Inggris):$laporan",
            laporan.isEmpty()
        )
    }

    @Test
    fun `tidak ada string kadaluwarsa yang cuma tersisa di berkas terjemahan`() {
        val res = resDir()
        val baseKeys = stringKeys(File(res, "values/strings.xml"))

        val laporan = StringBuilder()
        res.listFiles { f ->
            f.isDirectory && f.name.startsWith("values-") && File(f, "strings.xml").exists()
        }.orEmpty().sortedBy { it.name }.forEach { dir ->
            val extra = (stringKeys(File(dir, "strings.xml")) - baseKeys).sorted()
            if (extra.isNotEmpty()) {
                laporan.append("\n  ${dir.name} punya key yang sudah tidak dipakai: ${extra.joinToString(", ")}")
            }
        }

        assertTrue("Ada sisa string lama yang perlu dibersihkan:$laporan", laporan.isEmpty())
    }
}
