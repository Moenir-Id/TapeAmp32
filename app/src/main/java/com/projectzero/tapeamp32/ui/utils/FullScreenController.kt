package com.projectzero.tapeamp32.ui.utils

import android.app.Activity
import android.content.Context
import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

object FullScreenController {

    private const val PREFS_NAME = "tapeamp32_display_prefs"
    private const val KEY_FULLSCREEN_ENABLED = "fullscreen_enabled"

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

    fun applyAndPersist(activity: Activity, enable: Boolean) {
        setFullScreenEnabled(activity, enable)
        applyImmersiveMode(activity.window, enable)
    }

    fun toggle(activity: Activity): Boolean {
        val newState = !isFullScreenEnabled(activity)
        applyAndPersist(activity, newState)
        return newState
    }
}
