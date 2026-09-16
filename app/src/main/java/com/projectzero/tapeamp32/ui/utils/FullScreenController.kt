package com.projectzero.tapeamp32.ui.utils

import android.app.Activity
import android.content.Context
import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * FullScreenController
 * ============================================================
 * Helper MODULAR untuk mengontrol Immersive Mode (sembunyikan status bar +
 * navigation bar) lewat WindowInsetsControllerCompat -- kompatibel dari
 * Android 8.0 (API 26) sampai Android 14/15+ tanpa API deprecated
 * (systemUiVisibility) sama sekali.
 *
 * Status toggle (ON/OFF) disimpan di SharedPreferences terpisah supaya:
 * 1) Dibaca SEGERA & SYNCHRONOUS saat MainActivity.onCreate() (sebelum
 *    Compose pertama kali digambar) -- tidak perlu observe Flow/DataStore
 *    yang asynchronous, sehingga tidak ada "flicker" system bar saat app
 *    baru dibuka.
 * 2) Tetap konsisten dipakai ulang oleh tombol SCREEN di PlayerScreen
 *    (lewat Activity yang sama) tanpa duplikasi sumber kebenaran.
 */
object FullScreenController {

    private const val PREFS_NAME = "tapeamp32_display_prefs"
    private const val KEY_FULLSCREEN_ENABLED = "fullscreen_enabled"

    /**
     * Default TRUE supaya perilaku out-of-the-box tetap sama seperti
     * sebelumnya (Fullscreen Retro Deck aktif sejak app pertama dibuka).
     */
    fun isFullScreenEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_FULLSCREEN_ENABLED, true)
    }

    fun setFullScreenEnabled(context: Context, enabled: Boolean) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FULLSCREEN_ENABLED, enabled)
            .apply()
    }

    /**
     * Terapkan immersive mode ke sebuah Window secara langsung, TANPA
     * menyentuh SharedPreferences (dipakai saat restore state di onCreate,
     * supaya tidak menulis ulang preferensi yang baru saja dibaca).
     *
     * - enable = true  -> sembunyikan systemBars(), tetap bisa dipanggil
     *                     lagi via swipe tepi layar
     *                     (BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE).
     * - enable = false -> tampilkan systemBars() lagi.
     *
     * FIX (v1.5): decorFitsSystemWindows SEKARANG SELALU false (edge-to-edge)
     * di KEDUA mode -- sebelumnya diset TRUE saat immersive OFF, yang
     * menyerahkan keputusan "sisi mana yang di-inset" ke SISTEM (bukan
     * Compose). Di beberapa perangkat/orientasi, fit-otomatis sistem ini
     * ikut mengikutsertakan cutout/caption insets di kedua tepi kiri-kanan
     * SEKALIGUS dengan status bar di atas -- padahal seharusnya cuma status
     * bar (atas) & navigation bar (di sisi manapun dia benar-benar berada)
     * yang menyempitkan konten. Sekarang window SELALU digambar edge-to-edge
     * apa pun status toggle-nya, dan Compose sendiri (lihat AppRoot di
     * MainActivity.kt) yang menambahkan padding PERSIS untuk
     * WindowInsets.systemBars() saja -- tidak termasuk displayCutout/
     * captionBar -- setiap kali mode ini OFF.
     */
    fun applyImmersiveMode(window: Window, enable: Boolean) {

        val controller = WindowInsetsControllerCompat(window, window.decorView)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (enable) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    /**
     * Terapkan sekaligus simpan preferensi. Dipakai oleh tombol SCREEN
     * di PlayerScreen setiap kali di-toggle.
     */
    fun applyAndPersist(activity: Activity, enable: Boolean) {
        setFullScreenEnabled(activity, enable)
        applyImmersiveMode(activity.window, enable)
    }

    /**
     * Balik status saat ini (baca -> toggle -> terapkan -> simpan) dan
     * kembalikan status BARU-nya, supaya caller (UI) bisa langsung update
     * state lokal untuk alpha LED tombol SCREEN.
     */
    fun toggle(activity: Activity): Boolean {
        val newState = !isFullScreenEnabled(activity)
        applyAndPersist(activity, newState)
        return newState
    }
}
