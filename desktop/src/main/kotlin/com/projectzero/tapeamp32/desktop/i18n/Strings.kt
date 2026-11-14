package com.projectzero.tapeamp32.desktop.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.projectzero.tapeamp32.GeneratedStringKeys
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Loader string untuk desktop: membaca values/strings.xml (default = Inggris) dan
 * values-XX/strings.xml dari classpath (disalin build.gradle.kts ke "tapeamp-strings/").
 * `R.string.xxx` (Int, di-generate dari strings.xml) -> nama key -> teks bahasa aktif.
 *
 * Bahasa aktif = tag Android: "system" (ikuti Windows), "en", "in", "de", "es", "fr", "ja",
 * "ko", "pt", "ru", "zh-rCN". Mengubah [languageTag] otomatis me-recompose semua teks.
 */
object Strings {

    /** Folder bahasa yang ada di app Android. "values" (tanpa sufiks) = Inggris/default. */
    val supportedTags = listOf("en", "in", "de", "es", "fr", "ja", "ko", "pt", "ru", "zh-rCN")

    var languageTag: String by mutableStateOf("system")

    private val cache = HashMap<String, Map<String, String>>()
    private val defaults: Map<String, String> by lazy { load("values") }

    private fun folderFor(tag: String): String {
        val resolved = if (tag == "system") systemTag() else tag
        return when (resolved) {
            "en" -> "values"
            "zh" -> "values-zh-rCN" // LocaleManager.LANGUAGE_CHINESE_SIMPLIFIED = "zh"
            else -> "values-$resolved"
        }
    }

    private fun systemTag(): String {
        val locale = Locale.getDefault()
        val lang = when (val l = locale.language) {
            "id" -> "in"      // Android memakai kode lama "in" untuk Indonesia
            else -> l
        }
        return if (lang == "zh") "zh-rCN" else lang
    }

    private fun load(folder: String): Map<String, String> = synchronized(cache) {
        cache.getOrPut(folder) {
            val stream = Strings::class.java.classLoader
                .getResourceAsStream("tapeamp-strings/$folder/strings.xml")
                ?: return@getOrPut emptyMap()
            stream.use { input ->
                val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input)
                val nodes = doc.getElementsByTagName("string")
                val map = HashMap<String, String>(nodes.length * 2)
                for (i in 0 until nodes.length) {
                    val el = nodes.item(i) as Element
                    map[el.getAttribute("name")] = unescape(el.textContent)
                }
                map
            }
        }
    }

    /** Aturan escape strings.xml Android: \' \" \n \t \\ \@ \? \uXXXX, dan tanda kutip pembungkus. */
    private fun unescape(raw: String): String {
        var s = raw.trim()
        if (s.length >= 2 && s.first() == '"' && s.last() == '"') s = s.substring(1, s.length - 1)
        if ('\\' !in s) return s
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                val n = s[i + 1]
                when (n) {
                    'n' -> { out.append('\n'); i += 2 }
                    't' -> { out.append('\t'); i += 2 }
                    'u' -> {
                        val hex = if (i + 6 <= s.length) s.substring(i + 2, i + 6) else ""
                        val code = hex.toIntOrNull(16)
                        if (code != null) { out.append(code.toChar()); i += 6 }
                        else { out.append(n); i += 2 }
                    }
                    else -> { out.append(n); i += 2 } // \' \" \\ \@ \?
                }
            } else {
                out.append(c); i++
            }
        }
        return out.toString()
    }

    private fun lookup(id: Int): String {
        val key = GeneratedStringKeys.getOrNull(id - 1) ?: return "?string#$id"
        val tag = languageTag // dibaca di sini supaya pemanggil @Composable ikut ter-recompose
        val localized = load(folderFor(tag))[key]
        return localized ?: defaults[key] ?: key
    }

    fun get(id: Int): String = lookup(id)

    fun format(id: Int, vararg args: Any): String {
        val template = lookup(id)
        // Placeholder di strings.xml hanya %s/%d/%f; angka diformat dengan locale default Windows.
        return runCatching { String.format(Locale.getDefault(), template, *args) }.getOrDefault(template)
    }
}
