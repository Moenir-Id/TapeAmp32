package com.projectzero.tapeamp32.audio

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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

// BARU (Android Auto): id node root & folder "Semua Lagu" di pohon Browse yang
// ditampilkan Android Auto. Cuma 2 level (root -> Semua Lagu -> daftar lagu datar)
// karena scope patch ini sengaja kecil -- app di dalam HP sendiri sudah punya
// Library/Playlist yang jauh lebih lengkap, jadi Browse tree mobil cukup dibuat
// "cukup bisa dipakai", bukan tiruan penuh dari UI Library dalam app.
private const val BROWSE_ROOT_ID = "tapeamp_root"
private const val BROWSE_ALL_SONGS_ID = "tapeamp_all_songs"

@UnstableApi
class PlaybackService : MediaLibraryService() {

    // BARU: notifikasi media (judul fallback, kategori "All Songs" di Android Auto)
    // ikut bahasa tampilan yang dipilih di Settings > System > Language, bukan cuma
    // bahasa sistem HP -- Service punya Context sendiri dari OS, terpisah dari
    // Activity, jadi perlu dibungkus manual lagi di sini (lihat catatan lengkap di
    // LocaleManager.kt).
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.projectzero.tapeamp32.data.LocaleManager.wrapContext(newBase))
    }

    private var mediaSession: MediaLibrarySession? = null
    lateinit var playerManager: PlayerManager
        private set

    // VERIFIKASI: listener terpisah yang kemarin dipakai untuk mendiagnosa & berhasil
    // menemukan akar masalah kontrol media statusbar/lockscreen (lihat komentar FIX UTAMA
    // di onCreate()). Dibiarkan aktif sebagai log ringan buat memastikan fix bekerja.
    // TIDAK menggantikan listener yang sudah dipasang PlayerManager sendiri (addListener
    // boleh dipanggil berkali-kali oleh pihak berbeda, Media3/ExoPlayer memang begitu).
    private var debugListener: Player.Listener? = null

    // BARU (v2.3, Widget Home Screen): listener TERPISAH dari debugListener di atas,
    // khusus memicu refresh widget home screen (kalau ada yang dipasang user) setiap
    // kali status play/pause atau lagu aktif berubah. Dipisah dari debugListener
    // supaya tidak ikut terhapus/berubah kalau listener debug di atas nanti dirapikan.
    private var widgetUpdateListener: Player.Listener? = null

    // BARU (Widget "kaset berputar" + seekbar): widget home screen sekarang butuh
    // di-refresh BERKALA (bukan cuma pas event play/pause/ganti lagu seperti
    // widgetUpdateListener di atas) supaya animasi reel kelihatan "berputar" & posisi
    // seekbar-nya ikut maju. Handler ringan ini cukup jalan tiap 700ms, dan HANYA benar-
    // benar memicu render ulang RemoteViews kalau player sedang play (PlayerWidgetProvider.
    // updateAll() sendiri juga sudah no-op kalau tidak ada widget yang dipasang user).
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

    // BARU (Android Auto): scope ringan khusus resolusi Browse tree (query MediaStore
    // bisa agak lambat) -- SENGAJA TERPISAH dari coroutine scope lain di app ini supaya
    // dibatalkan sendiri begitu Service ini mati (lihat onDestroy()), tidak ikut nyangkut
    // ke lifecycle UI/ViewModel yang punya scope sendiri-sendiri.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // BARU (Android Auto): cache daftar lagu terakhir yang ditampilkan sebagai folder
    // "Semua Lagu" di Browse tree. Dipakai onSetMediaItems() untuk membangun ulang
    // ANTRIAN PENUH (bukan cuma 1 lagu yang di-tap) begitu user memilih satu lagu dari
    // Android Auto -- supaya tombol next/prev di mobil tetap jalan wajar, sama seperti
    // buka lagu dari Library di dalam app.
    @Volatile private var lastBrowsedSongs: List<Song> = emptyList()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate() dipanggil")

        // AUDIT FIX (multi-bahasa): sebelumnya notification_channel_name/description di
        // strings.xml disiapkan tapi TIDAK PERNAH disambungkan -- Media3 diam-diam memakai
        // nama channel bawaannya sendiri ("Media playback", bahasa Inggris tetap, tidak
        // ikut bahasa app yang dipilih user). setChannelName() menerima @StringRes Int
        // (bukan CharSequence), jadi otomatis ikut Locale yang sudah dibungkus lewat
        // LocaleManager.wrapContext()/attachBaseContext() di MainActivity, sama seperti
        // resource string lain. HARUS dipanggil paling awal di onCreate() Service ini,
        // sebelum apa pun lain yang bisa memicu MediaNotificationManager dibuat duluan
        // (kalau telat, setMediaNotificationProvider() ini tidak akan berpengaruh).
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .setChannelName(R.string.notification_channel_name)
                .build()
        )

        // Pakai instance PlayerManager yang SAMA dengan yang dipakai PlayerViewModel (UI),
        // bukan bikin ExoPlayer/AudioTrack baru -> lihat PlayerManager.getInstance().
        playerManager = PlayerManager.getInstance(applicationContext)

        // FIX (controlbar statusbar/lockscreen tidak pernah muncul): sebelumnya intent buat
        // buka lagi MainActivity didapat lewat packageManager.getLaunchIntentForPackage(),
        // yang BOLEH balikin null (mis. saat PackageManager belum sepenuhnya "melihat"
        // manifest app-nya sendiri persis di titik Service ini pertama kali dibuat oleh
        // sistem, bukan dari MainActivity). PendingIntent.getActivity() diminta Intent
        // NON-NULL -> kalau null yang lolos ke sana, MediaSession.Builder().build() di
        // bawah bisa gagal (exception) sebelum sempat assign `mediaSession`, dan
        // onCreate() Service ini CRASH diam-diam di proses background -> onGetSession()
        // akhirnya selalu null, dan Media3 TIDAK PERNAH punya sesi untuk ditampilkan jadi
        // notifikasi -> control bar statusbar/lockscreen kosong sama sekali walau musik
        // tetap terdengar diputar. Sekarang intent-nya dibangun eksplisit ke MainActivity,
        // dijamin tidak pernah null.
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

        // BARU (Android Auto): MediaSession biasa diganti MediaLibrarySession (masih sama-
        // sama androidx.media3.session.MediaSession di bawahnya, cuma nambah kemampuan
        // "Browse tree" lewat MediaLibrarySession.Callback) -- diperlukan supaya Android
        // Auto bisa menampilkan daftar lagu untuk dipilih, bukan cuma kontrol now-playing
        // pasif. Kontrol statusbar/lockscreen yang sudah beres sebelumnya TIDAK terpengaruh
        // sama sekali (MediaLibrarySession tetap MediaSession yang sama di mata mereka).
        mediaSession = MediaLibrarySession.Builder(this, playerManager.player, LibrarySessionCallback())
            .setSessionActivity(pendingIntent)
            .build()

        Log.d(TAG, "MediaLibrarySession dibuat: ${mediaSession != null}")

        // FIX UTAMA (root cause kontrol media statusbar/lockscreen TIDAK PERNAH muncul,
        // dikonfirmasi via debug logcat: onIsPlayingChanged/onPlaybackStateChanged jalan
        // normal, tapi onGetSession() & onUpdateNotification() TIDAK PERNAH sekalipun
        // terpanggil, di semua kondisi/device): MediaSessionService (dari source resminya,
        // lihat MediaSessionService.addSession()) hanya mendaftarkan sesi ke
        // MediaNotificationManager -- komponen yang bertanggung jawab manggil
        // onUpdateNotification() lalu startForeground() -- lewat 2 jalur: (1) otomatis
        // ketika ada MediaController dari LUAR service ini yang bind & memicu
        // onGetSession(), atau (2) manual lewat addSession(session).
        //
        // App ini TIDAK PERNAH memakai jalur (1): PlayerViewModel/UI mengontrol ExoPlayer
        // LANGSUNG lewat PlayerManager.getInstance().player (singleton yang dibagi),
        // bukan lewat MediaController yang connect ke PlaybackService ini. Makanya
        // onGetSession() memang tidak pernah dipanggil framework -- bukan bug/gagal,
        // memang tidak ada yang pernah memicunya -- dan sesi kita jadi sama sekali tidak
        // dikenal MediaNotificationManager, walau player-nya sendiri jalan normal
        // (listener isPlaying/playbackState tetap nyambung ke session karena itu memang
        // listener langsung ke Player, bukan lewat notification manager).
        //
        // Fix: daftarkan sesi secara EKSPLISIT ke jalur (2). Ini juga persis dianjurkan
        // dokumentasi resmi Media3 untuk skenario di luar pola standar "1 MediaController
        // per session".
        addSession(mediaSession!!)
        Log.d(TAG, "addSession() dipanggil manual")

        // VERIFIKASI (listener ini yang kemarin dipakai buat diagnosa & berhasil
        // mengonfirmasi akar masalah di atas: event isPlaying/playbackState terbukti
        // jalan normal sampai ke Service ini, tapi onGetSession()/onUpdateNotification()
        // TIDAK PERNAH kepanggil sebelum addSession() manual ditambahkan). Dibiarkan di
        // sini sebagai verifikasi ringan -- kalau nanti onUpdateNotification() di atas
        // ikut muncul di logcat setelah addSession() dipanggil, berarti fix ini beres.
        debugListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(TAG, "onIsPlayingChanged: isPlaying=$isPlaying")
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d(TAG, "onPlaybackStateChanged: state=$playbackState")
            }
        }
        playerManager.player.addListener(debugListener!!)

        // BARU (v2.3, Widget Home Screen): pasang listener widget SETELAH
        // addSession() -- posisinya tidak krusial (listener Player independen dari
        // MediaSession), tapi dikelompokkan di sini biar dekat dengan listener
        // sejenis (debugListener) di atas.
        widgetUpdateListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                PlayerWidgetProvider.updateAll(applicationContext)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                PlayerWidgetProvider.updateAll(applicationContext)
            }
        }
        playerManager.player.addListener(widgetUpdateListener!!)

        // BARU (Widget "kaset berputar" + seekbar): mulai tick berkala (lihat
        // widgetTickRunnable di atas untuk alasan kenapa ini perlu, terpisah dari
        // widgetUpdateListener yang cuma nembak sekali per event, bukan terus-menerus).
        widgetTickHandler.postDelayed(widgetTickRunnable, widgetTickIntervalMs)
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
        // BARU (v2.3, Widget Home Screen)
        widgetUpdateListener?.let { playerManager.player.removeListener(it) }
        // BARU (Widget "kaset berputar" + seekbar)
        widgetTickHandler.removeCallbacks(widgetTickRunnable)
        // JANGAN release() playerManager di sini: instance-nya dibagi bersama dengan
        // PlayerViewModel (lihat PlayerManager.getInstance()). Service boleh mati duluan
        // (mis. task di-swipe) sementara UI/ViewModel masih hidup dan tetap butuh player-nya.
        // Yang di-release cukup MediaSession milik service ini.
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    /**
     * BARU (Android Auto): implementasi Browse tree minimal supaya app ini muncul & bisa
     * dipakai dari layar mobil -- 2 level saja (Root -> "Semua Lagu" -> daftar lagu datar),
     * plus resolusi playFromMediaId/tap-di-Browse jadi antrian penuh (bukan cuma 1 lagu)
     * lewat onSetMediaItems(). Kontrol next/prev/play/pause/seek dari mobil sendiri TIDAK
     * perlu callback tambahan apa pun -- itu semua sudah otomatis diteruskan Media3 lewat
     * MediaSession.player yang sama (ExoPlayer milik PlayerManager) seperti kontrol
     * statusbar/lockscreen/headset yang sudah beres sebelumnya.
     */
    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {

        // REVISI KEDUA (bug "double click earphone/TWS sama saja dengan klik sekali,
        // selalu jadi play/pause"): fix pertama (onPlayerCommandRequest di bawah)
        // hanya menangani kasus "mentok di ujung antrian" -- itu asumsi bahwa
        // deteksi klik-ganda BAWAAN Media3/Android sendiri sudah jalan benar,
        // cuma gagal di titik itu. Ternyata masalahnya lebih dasar: deteksi
        // klik-ganda bawaan itu SAMA SEKALI TIDAK memicu 2 klik terpisah sebagai
        // "next" -- tiap klik (satu ATAU dua kali) selalu diproses Media3 sebagai
        // play/pause tunggal, tanda-tanda default multi-click grouping-nya Media3
        // tidak konsisten jalan di kombinasi Android/OEM/perangkat tertentu (ini
        // dikenal luas sebagai limitasi Media3 -- banyak music player custom
        // akhirnya menghitung klik sendiri, bukan mengandalkan bawaan Media3).
        //
        // Solusinya: TIDAK lagi mengandalkan deteksi klik-ganda bawaan sama sekali.
        // Di sini kita intersep RAW KeyEvent dari earphone kabel/TWS/Bluetooth
        // (semuanya sama-sama masuk lewat onMediaButtonEvent, dikirim sebagai
        // KEYCODE_HEADSETHOOK atau KEYCODE_MEDIA_PLAY_PAUSE) SEBELUM Media3 sempat
        // memprosesnya sendiri, lalu hitung jumlah klik manual pakai timer
        // (clickWindowMs) -- kalau tidak ada klik susulan dalam jendela waktu itu,
        // baru dieksekusi sesuai hitungan: 1x = play/pause, 2x = next, 3x = previous.
        // Return true di akhir artinya "event ini KAMI yang tangani", supaya Media3
        // tidak ikut-ikutan memprosesnya sendiri (mencegah double-handling/konflik).
        private var pendingClickCount = 0
        private val clickHandler = Handler(Looper.getMainLooper())
        private var pendingClickRunnable: Runnable? = null
        private val clickWindowMs = 350L

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

            // Cuma tangani manual untuk keycode yang relevan buat earphone
            // kabel/TWS/Bluetooth (play-pause & headsethook). Keycode lain (mis.
            // KEYCODE_MEDIA_NEXT/PREVIOUS eksplisit dari beberapa headset yang
            // memang langsung kirim tombol next/prev sendiri tanpa perlu dihitung
            // klik) dibiarkan lewat jalur default Media3 seperti biasa.
            if (keyEvent == null ||
                (keyEvent.keyCode != KeyEvent.KEYCODE_HEADSETHOOK &&
                    keyEvent.keyCode != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            ) {
                return super.onMediaButtonEvent(session, controllerInfo, intent)
            }

            // Cuma proses sekali per klik fisik (ACTION_DOWN) -- ACTION_UP untuk
            // keycode yang sama sengaja "ditelan"/di-consume diam-diam (return
            // true) supaya Media3 tidak ikut memprosesnya lagi sebagai event
            // terpisah, yang bisa bikin klik kehitung dobel.
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

        // FIX (bug "double click earphone kabel / double-tap TWS untuk next tidak
        // jalan"): tombol Next/Prev DI DALAM APP (kaset, statusbar, lockscreen biasa)
        // sebenarnya jalan lewat PlayerViewModel.next()/previous(), yang wrap-around
        // manual pakai modulo (lihat catatan panjang soal REPEAT_MODE_OFF di
        // PlayerManager.buildPlayer()). TAPI kontrol dari LUAR app -- klik ganda di
        // earphone kabel (KEYCODE_HEADSETHOOK), double-tap TWS, maupun tombol
        // next/prev di headset Bluetooth (AVRCP) -- semuanya diterjemahkan Android
        // jadi COMMAND_SEEK_TO_NEXT/PREVIOUS standar yang dikirim LANGSUNG ke
        // ExoPlayer oleh Media3, TIDAK lewat PlayerViewModel sama sekali. ExoPlayer
        // sendiri repeatMode-nya sengaja OFF (lihat catatan di atas), jadi begitu
        // lagu yang sedang diputar adalah lagu TERAKHIR/PERTAMA di antrian,
        // hasNextMediaItem()/hasPreviousMediaItem() jadi false -> command dianggap
        // tidak tersedia -> klik ganda dari earphone/TWS terasa "tidak jalan" persis
        // di titik itu (padahal tombol di layar app tetap wrap-around normal).
        //
        // Sekarang deteksi klik utamanya sudah dialihkan ke onMediaButtonEvent() di
        // atas (manual click-counting), jadi override ini jadi JARING PENGAMAN KEDUA
        // -- buat kontrol next/prev yang datang dari jalur LAIN di luar
        // onMediaButtonEvent (mis. tombol next/prev eksplisit di beberapa headset
        // Bluetooth yang tidak lewat KEYCODE_HEADSETHOOK, atau kontrol dari Android
        // Auto/Wear OS/notifikasi sistem) supaya tetap wrap-around dengan benar
        // juga, bukan cuma diam di ujung antrian.
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
                        // Urutan prioritas: antrian yang sedang/pernah dimuat app (paling
                        // konsisten dengan apa yang sedang diputar) -> kalau kosong (app
                        // belum pernah dibuka sama sekali sebelum masuk mobil), baru scan
                        // pustaka MediaStore lokal seadanya supaya Android Auto tidak
                        // menampilkan folder kosong sama sekali.
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

        /**
         * Dipanggil Media3 setiap kali controller eksternal (Android Auto/Assistant/dll)
         * minta player.setMediaItems(...) -- termasuk saat user TAP satu lagu di Browse
         * tree di atas (yang cuma datang sebagai 1 MediaItem placeholder ber-mediaId, TANPA
         * URI asli). Di sini item placeholder itu "diterjemahkan" balik ke antrian PENUH
         * (persis daftar yang barusan ditampilkan folder "Semua Lagu") plus URI asli
         * masing-masing lagu, supaya timeline ExoPlayer yang dihasilkan identik dengan
         * kalau lagu yang sama dibuka dari Library di dalam app -- next/prev di mobil pun
         * ikut jalan wajar, bukan berhenti di satu lagu doang.
         */
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
                // Daftarkan ulang songsById PlayerManager (TANPA memuat ulang player di
                // sini) -- lihat komentar lengkap di PlayerManager.registerQueueSongs().
                // Media3 sendiri yang akan memanggil player.setMediaItems(...) dengan
                // hasil resolusi di bawah setelah future ini selesai.
                playerManager.registerQueueSongs(fullQueue)
                val resolvedItems = fullQueue.map { playerManager.buildPlayableMediaItem(it) }
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(resolvedItems, resolvedIndex, startPositionMs)
                )
            }

            // Fallback: item yang diminta sudah lengkap (mis. sudah punya URI dari sumber
            // lain) atau tidak ditemukan di cache Browse -- teruskan apa adanya, jangan
            // dijegal supaya tetap ada percobaan pemutaran daripada diam saja.
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
            )
        }
    }
}
