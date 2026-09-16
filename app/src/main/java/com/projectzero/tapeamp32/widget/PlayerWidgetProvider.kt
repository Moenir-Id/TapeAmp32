package com.projectzero.tapeamp32.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import com.projectzero.tapeamp32.MainActivity
import com.projectzero.tapeamp32.R
import com.projectzero.tapeamp32.audio.PlayerManager

@UnstableApi
class PlayerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val views = buildRemoteViews(context)
        for (widgetId in appWidgetIds) {
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    companion object {

        private val REEL_FRAMES = intArrayOf(
            R.drawable.ic_widget_reel_0,
            R.drawable.ic_widget_reel_1,
            R.drawable.ic_widget_reel_2,
            R.drawable.ic_widget_reel_3,
            R.drawable.ic_widget_reel_4,
            R.drawable.ic_widget_reel_5,
            R.drawable.ic_widget_reel_6,
            R.drawable.ic_widget_reel_7
        )
        private const val REEL_FRAME_INTERVAL_MS = 700L

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, PlayerWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(componentName)
            if (ids.isEmpty()) return

            val views = buildRemoteViews(context)
            for (id in ids) {
                manager.updateAppWidget(id, views)
            }
        }

        private fun buildRemoteViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_player)

            val localizedContext = com.projectzero.tapeamp32.data.LocaleManager.wrapContext(context)

            val playerManager = runCatching { PlayerManager.getInstance(context) }.getOrNull()
            val song = playerManager?.currentSong?.value
            val isPlaying = playerManager?.player?.isPlaying == true

            views.setTextViewText(
                R.id.widget_title,
                song?.title ?: localizedContext.getString(R.string.app_name)
            )
            views.setTextViewText(
                R.id.widget_artist,
                song?.artist ?: localizedContext.getString(R.string.widget_no_song)
            )
            views.setImageViewResource(
                R.id.widget_play_pause,
                if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            )

            val reelFrame = if (isPlaying) {
                REEL_FRAMES[((SystemClock.elapsedRealtime() / REEL_FRAME_INTERVAL_MS) % REEL_FRAMES.size).toInt()]
            } else {
                REEL_FRAMES[0]
            }
            views.setImageViewResource(R.id.widget_reel, reelFrame)

            val durationMs = playerManager?.player?.duration
                ?.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
            val positionMs = playerManager?.player?.currentPosition?.coerceAtLeast(0L) ?: 0L
            if (durationMs > 0L) {
                views.setProgressBar(
                    R.id.widget_seekbar,
                    durationMs.toInt(),
                    positionMs.coerceAtMost(durationMs).toInt(),
                    false
                )
            } else {
                views.setProgressBar(R.id.widget_seekbar, 100, 0, false)
            }

            views.setOnClickPendingIntent(
                R.id.widget_prev,
                mediaButtonPendingIntent(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS, requestCode = 1)
            )
            views.setOnClickPendingIntent(
                R.id.widget_play_pause,
                mediaButtonPendingIntent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, requestCode = 2)
            )
            views.setOnClickPendingIntent(
                R.id.widget_next,
                mediaButtonPendingIntent(context, KeyEvent.KEYCODE_MEDIA_NEXT, requestCode = 3)
            )

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPending = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openAppPending)

            return views
        }

        private fun mediaButtonPendingIntent(
            context: Context,
            keyCode: Int,
            requestCode: Int
        ): PendingIntent {
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                setPackage(context.packageName)
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
