package com.projectzero.tapeamp32.audio

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.CommandButton
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.projectzero.tapeamp32.MainActivity
import com.projectzero.tapeamp32.R
import com.projectzero.tapeamp32.data.MusicRepository
import com.projectzero.tapeamp32.data.Song
import com.projectzero.tapeamp32.widget.PlayerWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "TapeAmpPlaybackSvc"
private const val NOTIFICATION_CHANNEL_ID = "tapeamp32_media_playback_channel"

private const val BROWSE_ROOT_ID = "tapeamp_root"
private const val BROWSE_ALL_SONGS_ID = "tapeamp_all_songs"

private const val CUSTOM_COMMAND_SHUFFLE = "com.projectzero.tapeamp32.SHUFFLE_TOGGLE"
private const val CUSTOM_COMMAND_CLOSE = "com.projectzero.tapeamp32.CLOSE_APP"

const val ACTION_CLOSE_APP_UI = "com.projectzero.tapeamp32.ACTION_CLOSE_APP_UI"

@UnstableApi
class PlaybackService : MediaLibraryService() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.projectzero.tapeamp32.data.LocaleManager.wrapContext(newBase))
    }

    private var mediaSession: MediaLibrarySession? = null
    lateinit var playerManager: PlayerManager
        private set

    private var debugListener: Player.Listener? = null

    private var widgetUpdateListener: Player.Listener? = null

    private val widgetTickHandler = Handler(Looper.getMainLooper())
    private val widgetTickIntervalMs = 700L
    private val widgetTickRunnable = object : Runnable {
        override fun run() {
            if (::playerManager.isInitialized && playerManager.player.isPlaying) {
                PlayerWidgetProvider.updateAll(applicationContext)
            }
            widgetTickHandler.postDelayed(this, widgetTickIntervalMs)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var lastBrowsedSongs: List<Song> = emptyList()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate() dipanggil")

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .setChannelName(R.string.notification_channel_name)
                .build()
        )

        playerManager = PlayerManager.getInstance(applicationContext)

        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaLibrarySession.Builder(this, playerManager.player, LibrarySessionCallback())
            .setSessionActivity(pendingIntent)
            .build()

        Log.d(TAG, "MediaLibrarySession dibuat: ${mediaSession != null}")

        addSession(mediaSession!!)
        Log.d(TAG, "addSession() dipanggil manual")

        mediaSession?.setCustomLayout(buildNotificationCustomLayout(playerManager.player.shuffleModeEnabled))

        debugListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(TAG, "onIsPlayingChanged: isPlaying=$isPlaying")
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d(TAG, "onPlaybackStateChanged: state=$playbackState")
            }
        }
        playerManager.player.addListener(debugListener!!)

        widgetUpdateListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                PlayerWidgetProvider.updateAll(applicationContext)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                PlayerWidgetProvider.updateAll(applicationContext)
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                mediaSession?.setCustomLayout(buildNotificationCustomLayout(shuffleModeEnabled))
            }
        }
        playerManager.player.addListener(widgetUpdateListener!!)

        widgetTickHandler.postDelayed(widgetTickRunnable, widgetTickIntervalMs)
    }

    private fun buildNotificationCustomLayout(shuffleOn: Boolean): List<CommandButton> = listOf(
        CommandButton.Builder()
            .setDisplayName(getString(R.string.notif_action_shuffle))
            .setSessionCommand(SessionCommand(CUSTOM_COMMAND_SHUFFLE, Bundle.EMPTY))
            .setIconResId(if (shuffleOn) R.drawable.ic_notif_shuffle_on else R.drawable.ic_notif_shuffle_off)
            .build(),
        CommandButton.Builder()
            .setDisplayName(getString(R.string.notif_action_close))
            .setSessionCommand(SessionCommand(CUSTOM_COMMAND_CLOSE, Bundle.EMPTY))
            .setIconResId(R.drawable.ic_notif_close)
            .build()
    )

    private fun closeAppFromNotification() {
        Log.d(TAG, "closeAppFromNotification() dipanggil")
        sendBroadcast(Intent(ACTION_CLOSE_APP_UI).setPackage(packageName))
        runCatching {
            playerManager.player.stop()
            playerManager.player.clearMediaItems()
        }
        stopSelf()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        Log.d(TAG, "onGetSession() dipanggil oleh package=${controllerInfo.packageName}")
        return mediaSession
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        Log.d(TAG, "onUpdateNotification() dipanggil, startInForegroundRequired=$startInForegroundRequired, sdk=${Build.VERSION.SDK_INT}")
        super.onUpdateNotification(session, startInForegroundRequired)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "onTaskRemoved() dipanggil")
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy() dipanggil")
        debugListener?.let { playerManager.player.removeListener(it) }

        widgetUpdateListener?.let { playerManager.player.removeListener(it) }

        widgetTickHandler.removeCallbacks(widgetTickRunnable)

        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {

        private var pendingClickCount = 0
        private val clickHandler = Handler(Looper.getMainLooper())
        private var pendingClickRunnable: Runnable? = null
        private val clickWindowMs = 350L

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val defaultResult = super.onConnect(session, controller)
            val sessionCommands = defaultResult.availableSessionCommands.buildUpon()
                .add(SessionCommand(CUSTOM_COMMAND_SHUFFLE, Bundle.EMPTY))
                .add(SessionCommand(CUSTOM_COMMAND_CLOSE, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.accept(sessionCommands, defaultResult.availablePlayerCommands)
        }

        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            session.setCustomLayout(controller, buildNotificationCustomLayout(playerManager.player.shuffleModeEnabled))
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CUSTOM_COMMAND_SHUFFLE -> {
                    val player = session.player
                    player.shuffleModeEnabled = !player.shuffleModeEnabled
                    session.setCustomLayout(buildNotificationCustomLayout(player.shuffleModeEnabled))
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                CUSTOM_COMMAND_CLOSE -> {
                    closeAppFromNotification()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent
        ): Boolean {
            val keyEvent: KeyEvent? =
                if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                }

            if (keyEvent == null ||
                (keyEvent.keyCode != KeyEvent.KEYCODE_HEADSETHOOK &&
                    keyEvent.keyCode != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            ) {
                return super.onMediaButtonEvent(session, controllerInfo, intent)
            }

            if (keyEvent.action != KeyEvent.ACTION_DOWN) {
                return true
            }

            pendingClickCount++
            pendingClickRunnable?.let { clickHandler.removeCallbacks(it) }

            val runnable = Runnable {
                val player = session.player
                when (pendingClickCount) {
                    1 -> {
                        if (player.isPlaying) player.pause() else player.play()
                    }
                    2 -> {
                        if (player.hasNextMediaItem()) {
                            player.seekToNextMediaItem()
                        } else if (player.mediaItemCount > 0) {
                            player.seekTo(0, 0L)
                        }
                    }
                    else -> {
                        if (player.hasPreviousMediaItem()) {
                            player.seekToPreviousMediaItem()
                        } else if (player.mediaItemCount > 0) {
                            player.seekTo(player.mediaItemCount - 1, 0L)
                        }
                    }
                }
                pendingClickCount = 0
            }
            pendingClickRunnable = runnable
            clickHandler.postDelayed(runnable, clickWindowMs)

            return true
        }

        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int
        ): Int {
            val player = session.player
            when (playerCommand) {
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                    if (!player.hasNextMediaItem() && player.mediaItemCount > 0) {
                        player.seekTo(0, 0L)
                        return androidx.media3.session.SessionResult.RESULT_ERROR_NOT_SUPPORTED
                    }
                }
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                    if (!player.hasPreviousMediaItem() && player.mediaItemCount > 0) {
                        player.seekTo(player.mediaItemCount - 1, 0L)
                        return androidx.media3.session.SessionResult.RESULT_ERROR_NOT_SUPPORTED
                    }
                }
            }
            return super.onPlayerCommandRequest(session, controller, playerCommand)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootItem = MediaItem.Builder()
                .setMediaId(BROWSE_ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(getString(R.string.app_name))
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return when (parentId) {
                BROWSE_ROOT_ID -> {
                    val allSongsFolder = MediaItem.Builder()
                        .setMediaId(BROWSE_ALL_SONGS_ID)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(getString(R.string.android_auto_all_songs))
                                .setIsBrowsable(true)
                                .setIsPlayable(false)
                                .build()
                        )
                        .build()
                    Futures.immediateFuture(
                        LibraryResult.ofItemList(ImmutableList.of(allSongsFolder), params)
                    )
                }

                BROWSE_ALL_SONGS_ID -> {
                    val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                    serviceScope.launch {

                        val songs = playerManager.queueSongs().ifEmpty {
                            runCatching { MusicRepository(applicationContext).scanLibrary() }
                                .getOrDefault(emptyList())
                        }
                        lastBrowsedSongs = songs

                        val items = songs.map { song ->
                            MediaItem.Builder()
                                .setMediaId(song.id.toString())
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(song.title)
                                        .setArtist(song.artist)
                                        .setAlbumTitle(song.album)
                                        .setIsBrowsable(false)
                                        .setIsPlayable(true)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                        .build()
                                )
                                .build()
                        }
                        future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                    }
                    future
                }

                else -> Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            }
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val requestedId = mediaItems.singleOrNull()?.mediaId
            val fullQueue = lastBrowsedSongs.ifEmpty { playerManager.queueSongs() }
            val resolvedIndex = requestedId?.let { id -> fullQueue.indexOfFirst { it.id.toString() == id } } ?: -1

            if (requestedId != null && resolvedIndex >= 0 && fullQueue.isNotEmpty()) {

                playerManager.registerQueueSongs(fullQueue)
                val resolvedItems = fullQueue.map { playerManager.buildPlayableMediaItem(it) }
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(resolvedItems, resolvedIndex, startPositionMs)
                )
            }

            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
            )
        }
    }
}
