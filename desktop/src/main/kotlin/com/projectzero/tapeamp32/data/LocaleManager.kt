package com.projectzero.tapeamp32.data

import com.projectzero.tapeamp32.desktop.i18n.Strings
import com.projectzero.tapeamp32.desktop.shim.Activity
import com.projectzero.tapeamp32.desktop.shim.Context
import java.io.File
import java.util.Properties

/**
 * LocaleManager VERSI DESKTOP: konstanta & fungsi sama dengan Android. Bedanya, bahasa
 * diterapkan LANGSUNG (live, lewat Strings.languageTag) -- tidak perlu recreate/restart.
 */
object LocaleManager {

    const val LANGUAGE_SYSTEM = "system"
    const val LANGUAGE_INDONESIAN = "in"
    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_SPANISH = "es"
    const val LANGUAGE_PORTUGUESE = "pt"
    const val LANGUAGE_FRENCH = "fr"
    const val LANGUAGE_GERMAN = "de"
    const val LANGUAGE_RUSSIAN = "ru"
    const val LANGUAGE_JAPANESE = "ja"
    const val LANGUAGE_KOREAN = "ko"
    const val LANGUAGE_CHINESE_SIMPLIFIED = "zh"

    private const val KEY_LANGUAGE = "app_language"

    private fun prefsFile(context: Context) = File(context.filesDir, "locale.properties")

    fun getSavedLanguage(context: Context): String = runCatching {
        val f = prefsFile(context)
        if (!f.exists()) LANGUAGE_SYSTEM
        else Properties().apply { f.inputStream().use { load(it) } }.getProperty(KEY_LANGUAGE, LANGUAGE_SYSTEM)
    }.getOrDefault(LANGUAGE_SYSTEM)

    private fun setLanguage(context: Context, languageCode: String) {
        runCatching {
            Properties().apply { setProperty(KEY_LANGUAGE, languageCode) }
                .let { p -> prefsFile(context).outputStream().use { p.store(it, null) } }
        }
    }

    /** Dipanggil sekali saat start supaya tampilan langsung memakai bahasa tersimpan. */
    fun applySavedLanguage(context: Context) {
        Strings.languageTag = getSavedLanguage(context)
    }

    fun applyAndRestart(activity: Activity, languageCode: String) {
        setLanguage(activity, languageCode)
        Strings.languageTag = languageCode
    }
}
