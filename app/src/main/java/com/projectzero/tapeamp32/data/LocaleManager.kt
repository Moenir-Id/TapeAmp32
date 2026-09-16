package com.projectzero.tapeamp32.data

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * BARU: pengaturan BAHASA TAMPILAN aplikasi (Settings > System > Language) --
 * terpisah dari bahasa sistem HP, mirip fitur "App language" bawaan Android 13+
 * tapi diimplementasikan manual di sini supaya tetap jalan mulai dari minSdk 26
 * (bukan cuma HP Android 13+ ke atas).
 *
 * KENAPA TIDAK PAKAI AppCompatDelegate.setApplicationLocales() BAWAAN ANDROIDX:
 * dokumentasi resmi Android mewajibkan Activity meng-extend AppCompatActivity
 * kalau dipakai bareng Compose -- yang berarti juga wajib ganti tema Activity
 * dari android:Theme.Material.NoActionBar (dipakai app ini) ke turunan
 * Theme.AppCompat, supaya tidak crash "You need to use a Theme.AppCompat theme".
 * Itu perubahan besar & berisiko untuk app yang 100% Jetpack Compose seperti
 * TapeAmp 32 (tidak ada satu pun View/AppCompat widget lain yang butuh itu),
 * jadi dipakai pendekatan klasik attachBaseContext() di bawah ini -- pola yang
 * sama dipakai banyak app Compose murni yang tidak mau tergantung AppCompat.
 *
 * CARANYA:
 * 1. Kode bahasa pilihan user ("system"/"in"/"en") disimpan di SharedPreferences
 *    KHUSUS (BUKAN DataStore SettingsRepository) supaya bisa dibaca SINKRON di
 *    MainActivity.attachBaseContext() -- titik paling awal siklus hidup
 *    Activity, jauh sebelum DataStore Flow sempat siap dibaca.
 * 2. attachBaseContext() membungkus base Context dengan Locale pilihan user
 *    lewat createConfigurationContext() SEBELUM diteruskan ke Activity --
 *    seluruh resource (termasuk stringResource() di semua layar Compose)
 *    otomatis mengikuti Locale ini.
 * 3. Ganti bahasa dari dalam app (Settings > System > Language) menulis pilihan
 *    baru lalu memanggil activity.recreate() supaya attachBaseContext()
 *    terpanggil ulang dengan Locale baru dan seluruh UI Compose ikut berubah.
 *
 * SENGAJA TIDAK ikut ter-backup di System > Backup & Restore -- backup di sana
 * mencakup preferensi audio/EQ/library, bukan preferensi perangkat seperti
 * bahasa tampilan (kalau ikut, memulihkan backup dari HP lain bisa diam-diam
 * mengganti bahasa yang sedang dipakai user tanpa mereka minta).
 */
object LocaleManager {

    const val LANGUAGE_SYSTEM = "system"
    const val LANGUAGE_INDONESIAN = "in"
    const val LANGUAGE_ENGLISH = "en"

    // BARU: 8 bahasa tambahan (total 10 termasuk System Default) -- masing-masing
    // kode ini harus persis sama dengan nama folder res/values-<kode> (mis. "es" ->
    // res/values-es/strings.xml). "zh" khusus pakai region "rCN" (lihat wrapContext()
    // di bawah) supaya Android me-resolve ke res/values-zh-rCN, bukan values-zh generik.
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

    /** Panggil dari MainActivity.attachBaseContext(newBase). */
    fun wrapContext(base: Context): Context {
        val languageCode = getSavedLanguage(base)

        // "system" -> tidak dibungkus sama sekali, biarkan Android pilih
        // resource sesuai bahasa sistem HP seperti biasa (values-in/values).
        if (languageCode == LANGUAGE_SYSTEM) return base

        // "zh" sendirian me-resolve ke res/values-zh (tidak ada di project ini) --
        // perlu region "CN" secara eksplisit supaya Android jatuh ke res/values-zh-rCN
        // yang benar-benar berisi string Chinese (Simplified) kita.
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

    /** Dipanggil dari Settings > System > Language saat user memilih bahasa baru. */
    fun applyAndRestart(activity: Activity, languageCode: String) {
        setLanguage(activity, languageCode)
        activity.recreate()
    }
}
