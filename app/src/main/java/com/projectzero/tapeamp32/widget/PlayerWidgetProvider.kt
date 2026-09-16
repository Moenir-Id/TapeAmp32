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

/**
 * BARU (v2.3, "Widget Home Screen"): kontrol player minimal (prev/play-pause/next
 * + judul & artis lagu) langsung dari home screen, tanpa buka app.
 *
 * Sengaja pakai AppWidgetProvider + RemoteViews klasik (bawaan Android SDK),
 * BUKAN Glance -- supaya tidak menambah dependency baru ke project ini untuk
 * fitur yang scope-nya kecil. Konsekuensinya layout widget (widget_player.xml)
 * harus View biasa (LinearLayout/TextView/ImageView), tidak bisa Composable.
 *
 * Tombol widget MENGIRIM BROADCAST ACTION_MEDIA_BUTTON standar Android --
 * ditangkap otomatis oleh androidx.media3.session.MediaButtonReceiver yang
 * SUDAH terdaftar di AndroidManifest.xml (dipakai juga oleh kontrol media
 * statusbar/lockscreen yang sudah beres sebelumnya, lihat PlaybackService).
 * Jadi TIDAK perlu logic play/pause/next/prev baru sama sekali di sini --
 * cukup pakai jalur yang sudah ada & sudah terbukti bekerja.
 *
 * JUJUR diakui: karena RemoteViews cuma dirender ulang saat updateAppWidget()
 * dipanggil (bukan live-binding terus-menerus seperti Compose), tampilan lagu &
 * ikon play/pause di widget baru ikut berubah setelah PlaybackService memicu
 * updateAll() (lihat listener baru di PlaybackService.onCreate()) -- ada jeda
 * sepersekian detik yang wajar, bukan instan sepenuhnya seperti UI dalam app.
 */
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

        // BARU (Widget "kaset berputar"): 8 frame vector (rotasi 45 derajat per frame,
        // lihat res/drawable/ic_widget_reel_0..7.xml). Frame aktif dipilih dari WAKTU
        // SISTEM berjalan (SystemClock.elapsedRealtime()), bukan disimpan sebagai state
        // -- supaya tidak perlu variabel counter yang gampang tidak sinkron antara
        // beberapa pemicu updateAll() berbeda (event player vs tick berkala di
        // PlaybackService). Konsekuensinya: putaran reel terlihat "berlanjut" mulus
        // walau proses app sempat mati-hidup, karena patokannya jam sistem, bukan
        // hitungan internal widget ini.
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

        /**
         * Dipanggil dari PlaybackService setiap kali status playback/lagu berubah --
         * widget di-refresh SEGERA, tidak menunggu siklus updatePeriodMillis bawaan
         * Android yang minimal 30 menit sekali (jauh terlalu jarang untuk kontrol
         * musik). No-op kalau tidak ada widget yang sedang dipasang user (ids kosong).
         */
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

            // BARU: teks widget (judul lagu placeholder & "Tidak ada lagu diputar")
            // ikut bahasa tampilan yang dipilih di Settings > System > Language,
            // bukan cuma bahasa sistem HP -- context bawaan onUpdate()/updateAll()
            // dari OS TIDAK ikut attachBaseContext() milik MainActivity, jadi harus
            // dibungkus manual lagi di sini lewat LocaleManager.wrapContext().
            val localizedContext = com.projectzero.tapeamp32.data.LocaleManager.wrapContext(context)

            // Baca state LANGSUNG dari singleton PlayerManager yang sama dipakai UI
            // & PlaybackService (lihat PlayerManager.getInstance()) -- TIDAK membuat
            // instance/koneksi player baru khusus untuk widget.
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

            // BARU (Widget "kaset berputar"): cuma "berputar" selagi playing -- saat
            // pause/berhenti dibekukan di frame 0 (posisi netral), bukan ikut nge-freeze
            // di frame acak terakhir yang bisa terlihat seperti widget macet.
            val reelFrame = if (isPlaying) {
                REEL_FRAMES[((SystemClock.elapsedRealtime() / REEL_FRAME_INTERVAL_MS) % REEL_FRAMES.size).toInt()]
            } else {
                REEL_FRAMES[0]
            }
            views.setImageViewResource(R.id.widget_reel, reelFrame)

            // BARU (Widget "seekbar"): progres visual posisi lagu (lihat catatan jujur
            // soal belum bisa di-drag di widget_seekbar_progress.xml). Dibaca LANGSUNG
            // dari ExoPlayer (player.currentPosition/duration), bukan dari StateFlow
            // _positionMs milik PlayerManager -- StateFlow itu cuma di-update lewat
            // pollPosition() selama UI app hidup, sedangkan widget harus tetap akurat
            // walau app sedang diminimize/di-swipe (Service & player-nya tetap jalan).
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

            // Ketuk area judul/artis (widget_root) -> buka app langsung ke Player.
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

        /**
         * Broadcast ACTION_MEDIA_BUTTON standar Android -- ditangkap otomatis oleh
         * androidx.media3.session.MediaButtonReceiver (sudah terdaftar di manifest),
         * lalu diteruskan ke MediaSession aktif milik PlaybackService. Dibatasi ke
         * package sendiri (setPackage) supaya broadcast ini eksplisit, bukan implicit
         * broadcast terbuka yang bisa "didengar" app lain.
         */
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
