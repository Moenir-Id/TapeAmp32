package com.projectzero.tapeamp32.data

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

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

    private const val PREFS_NAME = "tapeamp32_locale"
    private const val KEY_LANGUAGE = "app_language"

    fun getSavedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, LANGUAGE_SYSTEM) ?: LANGUAGE_SYSTEM
    }

    private fun setLanguage(context: Context, languageCode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, languageCode)
            .apply()
    }

    fun wrapContext(base: Context): Context {
        val languageCode = getSavedLanguage(base)

        if (languageCode == LANGUAGE_SYSTEM) return base

        val locale = if (languageCode == LANGUAGE_CHINESE_SIMPLIFIED) {
            Locale(languageCode, "CN")
        } else {
            Locale(languageCode)
        }
        Locale.setDefault(locale)

        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)

        return base.createConfigurationContext(config)
    }

    fun applyAndRestart(activity: Activity, languageCode: String) {
        setLanguage(activity, languageCode)
        activity.recreate()
    }
}
