package com.projectzero.tapeamp32

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import com.projectzero.tapeamp32.ui.components.NavRail
import com.projectzero.tapeamp32.ui.screens.*
import com.projectzero.tapeamp32.ui.theme.BgBlack
import com.projectzero.tapeamp32.ui.theme.TapeAmp32Theme
import com.projectzero.tapeamp32.ui.utils.FullScreenController
import com.projectzero.tapeamp32.viewmodel.PlayerViewModel
import com.projectzero.tapeamp32.viewmodel.Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@UnstableApi
class MainActivity : ComponentActivity() {

    // BARU: bahasa tampilan aplikasi (Settings > System > Language) -- lihat
    // catatan lengkap di LocaleManager.kt untuk kenapa ini dilakukan lewat
    // attachBaseContext() manual, bukan AppCompatDelegate.setApplicationLocales().
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.projectzero.tapeamp32.data.LocaleManager.wrapContext(newBase))
    }

    private lateinit var vm: PlayerViewModel

    private val pickAudioLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        uris.forEach { uri ->
            runCatching {
                contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        // Catatan: pemilihan file individual (bukan folder) tidak memicu scan apa pun;
        // pemutaran file lepas ditangani terpisah dari library folder-based.
    }

    private val pickFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // BARU (fitur multi-folder): dulu vm.scanFolder(uri) di sini MENIMPA
        // folder yang sudah tersimpan sebelumnya (cuma nyimpen 1 string URI).
        // Sekarang vm.addMusicFolder(uri) MENAMBAHKAN folder ini ke daftar
        // yang sudah ada, bukan mengganti -- lihat catatan panjang di
        // PlayerViewModel.addMusicFolder().
        vm.addMusicFolder(uri)
    }

    private val pickPresetJsonLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()?.let { json ->
            vm.importPresetJson(json)
        }
    }

    // BARU (v1.2): fitur EXPORT preset EQ -- buka file picker "Save As" (SAF
    // CreateDocument) supaya pengguna bisa memilih sendiri nama & lokasi file JSON
    // preset yang sedang aktif, lalu tulis hasil vm.exportActivePresetJson() ke sana.
    // Pola ini sejalan dengan pickPresetJsonLauncher (UPLOAD) di atas, cuma arah
    // baca/tulisnya dibalik.
    private val exportPresetLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(vm.exportActivePresetJson())
            }
        }
    }

    // BARU (v2.0): fitur BACKUP & RESTORE semua pengaturan sekaligus (bukan cuma
    // preset EQ seperti exportPresetLauncher/pickPresetJsonLauncher di atas) --
    // pola SAF-nya sama persis (CreateDocument utk backup, OpenDocument utk
    // restore), cuma di sini baca/tulisnya lewat viewModelScope karena
    // exportAllSettingsJson()/importAllSettingsJson() adalah fungsi suspend
    // (membaca/menulis SELURUH DataStore, bukan satu StateFlow di memori seperti
    // preset aktif).
    private val backupSettingsLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            runCatching {
                val json = vm.exportAllSettingsJson()
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
            }.onSuccess {
                Toast.makeText(this@MainActivity, getString(R.string.toast_backup_success), Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@MainActivity, getString(R.string.toast_backup_failed, it.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    private val restoreSettingsLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            val json = runCatching {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (json.isNullOrBlank()) {
                Toast.makeText(this@MainActivity, getString(R.string.toast_backup_file_unreadable), Toast.LENGTH_LONG).show()
                return@launch
            }
            vm.importAllSettingsJson(json)
                .onSuccess {
                    // FIX (sebelumnya cuma Toast bahasa Inggris menyuruh pengguna tutup &
                    // buka ulang app sendiri): DataStore sudah ditulis ulang dengan benar
                    // di titik ini (lihat SettingsRepository.importAllSettingsJson), tapi
                    // sebagian besar pengaturan yang dipulihkan PlayerViewModel ke layar
                    // cuma dibaca SEKALI saat init{} -- tidak ikut ter-refresh live selama
                    // app masih berjalan. Menyuruh pengguna melakukannya manual gampang
                    // lupa/terlewat. Sekarang app RESTART SENDIRI beberapa saat setelah
                    // Restore berhasil: proses (termasuk PlaybackService, karena tidak
                    // dipisah android:process di manifest) benar-benar dimatikan lalu
                    // MainActivity dibuka ulang dari awal, supaya seluruh pengaturan hasil
                    // Restore langsung dipulihkan penuh via init{} -- bukan cuma sebagian
                    // yang kebetulan dipantau live.
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.toast_restore_success),
                        Toast.LENGTH_LONG
                    ).show()
                    restartAppToApplyRestoredSettings()
                }
                .onFailure {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.toast_restore_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    /**
     * BARU (patch "auto-restart setelah Restore"): mematikan proses app sepenuhnya lalu
     * membuka ulang MainActivity dari awal, dipanggil begitu Restore All Settings berhasil.
     * Delay singkat dulu supaya Toast konfirmasi sempat kebaca pengguna sebelum layar
     * berkedip restart. `Intent.makeRestartActivityTask` sudah menyertakan
     * FLAG_ACTIVITY_NEW_TASK + FLAG_ACTIVITY_CLEAR_TASK, jadi task lama (termasuk seluruh
     * back stack) benar-benar dibuang, bukan cuma recreate() Activity saat ini saja --
     * yang tidak akan cukup karena ViewModel/PlayerManager/PlaybackService yang sudah
     * telanjur hidup dengan state lama tidak ikut ter-reset oleh recreate() biasa.
     */
    private fun restartAppToApplyRestoredSettings() {
        lifecycleScope.launch {
            delay(1200)
            val restartIntent = packageManager.getLaunchIntentForPackage(packageName)?.component?.let {
                Intent.makeRestartActivityTask(it)
            }
            if (restartIntent != null) {
                startActivity(restartIntent)
            }
            Runtime.getRuntime().exit(0)
        }
    }

    // BUG UTAMA (POST_NOTIFICATIONS kemungkinan besar TIDAK PERNAH benar-benar granted,
    // walau dialog-nya sempat kelihatan sekilas): sebelumnya requestAudioPermissionIfNeeded()
    // memanggil permissionLauncher.launch(audio) LALU langsung notificationPermissionLauncher
    // .launch(POST_NOTIFICATIONS) tanpa menunggu hasil permintaan pertama selesai. Sistem
    // Android cuma bisa menampilkan SATU dialog izin dalam satu waktu per Activity -- kalau
    // launch() kedua dipanggil selagi launch() pertama masih pending, request kedua itu bisa
    // diam-diam dibatalkan/diabaikan oleh ActivityResultRegistry (bahkan sebelum dialog kedua
    // sempat tampil), sehingga callback-nya langsung balik dengan hasil "tidak granted" tanpa
    // pengguna sempat menekan apa pun. Fix: rantai izin sekarang SEKUENSIAL -- callback izin
    // audio yang memicu (bukan langsung memanggil) permintaan POST_NOTIFICATIONS berikutnya,
    // hanya setelah dialog pertama benar-benar selesai/ditutup pengguna.
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Izin ini hanya dipakai untuk pemutaran/pembacaan metadata; TIDAK lagi memicu
        // scan otomatis seluruh device saat diberikan.
        requestNotificationPermissionIfNeeded()
    }

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Tidak perlu aksi tambahan; MediaSessionService akan mulai menampilkan notif
        // begitu izin diberikan dan playback berjalan.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // BARU (v1.2) - FIX layar putih polos saat app dibuka: HARUS dipanggil
        // sebelum super.onCreate(), sesuai kontrak androidx.core.splashscreen.
        // installSplashScreen() otomatis membaca tema Theme.TapeAmp32.Splash yang
        // dipasang di manifest, lalu berpindah ke postSplashScreenTheme
        // (Theme.TapeAmp32, gelap) begitu window pertama activity ini siap
        // digambar -- menggantikan splash sistem default (putih) di Android 12+.
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // FIX: Immersive Mode sekarang dikelola lewat FullScreenController (modular,
        // kompatibel Android 8.0 - 14/15+ via WindowInsetsControllerCompat) dan status
        // ON/OFF-nya dibaca dari SharedPreferences -- supaya toggle tombol SCREEN di
        // PlayerScreen tetap konsisten diterapkan ulang setiap kali Activity ini dibuat.
        FullScreenController.applyImmersiveMode(
            window = window,
            enable = FullScreenController.isFullScreenEnabled(this)
        )

        vm = androidx.lifecycle.ViewModelProvider(this)[PlayerViewModel::class.java]

        // BARU (bug "app tidak muncul di 'Buka dengan' saat tap file lagu di file
        // manager/app downloader lain"): begitu MainActivity ini yang dipilih
        // sistem untuk menangani Intent.ACTION_VIEW (lihat intent-filter baru di
        // AndroidManifest.xml), Uri file yang mau dibuka ada di `intent.data`
        // (single file) atau `intent.clipData` (kalau user pilih banyak file
        // sekaligus di file manager). Ditangani di sini untuk cold start (app
        // belum jalan sama sekali). Untuk kasus app SUDAH jalan di background lalu
        // user tap file lagi, lihat onNewIntent() di bawah -- Activity ini
        // launchMode="singleTop" jadi onCreate() TIDAK dipanggil ulang untuk kasus
        // itu, harus ditangani terpisah.
        handleIncomingMediaIntent(intent)

        requestAudioPermissionIfNeeded()
        requestIgnoreBatteryOptimizationsIfNeeded()

        // FIX: startForegroundService() sebelumnya dipanggil di sini secara TIDAK BERSYARAT
        // saat app baru dibuka (belum tentu ada playback aktif). Service yang di-start lewat
        // startForegroundService() WAJIB memanggil Service.startForeground() sendiri dalam
        // waktu singkat, kalau tidak (atau kalau android:foregroundServiceType di manifest
        // belum diisi benar untuk target API 34+), sistem akan memaksa app CRASH setiap
        // dibuka. PlaybackService tidak memanggil startForeground() secara manual -- dia
        // mengandalkan MediaSessionService bawaan Media3 yang baru mempromosikan diri ke
        // foreground begitu benar-benar ada sesi/playback aktif. Pakai startService() biasa
        // di sini (tidak terikat batas waktu startForeground), biar Media3 yang menentukan
        // kapan pindah ke foreground.
        startService(Intent(this, com.projectzero.tapeamp32.audio.PlaybackService::class.java))

        setContent {
            TapeAmp32Theme {
                Surface(modifier = Modifier.fillMaxSize(), color = BgBlack) {

                    // BARU (v1.5): status toggle SCREEN (immersive) sekarang diangkat ke
                    // sini (bukan lokal di dalam PlayerScreen saja) supaya AppRoot juga
                    // tahu status ini -- dipakai untuk menambahkan padding systemBars()
                    // lewat Compose sendiri saat immersive OFF (lihat FIX di
                    // FullScreenController).
                    var fullScreenOn by remember {
                        mutableStateOf(FullScreenController.isFullScreenEnabled(this@MainActivity))
                    }

                    AppRoot(
                        vm = vm,
                        fullScreenOn = fullScreenOn,
                        onFullScreenToggle = {
                            fullScreenOn = FullScreenController.toggle(this@MainActivity)
                        },
                        onPickFiles = {
                            pickAudioLauncher.launch(arrayOf("audio/*"))
                        },
                        onPickFolder = {
                            pickFolderLauncher.launch(null)
                        },
                        onUploadPreset = {
                            pickPresetJsonLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        onExportPreset = { suggestedFileName ->
                            exportPresetLauncher.launch(suggestedFileName)
                        },
                        onBackupSettings = { suggestedFileName ->
                            backupSettingsLauncher.launch(suggestedFileName)
                        },
                        onRestoreSettings = {
                            restoreSettingsLauncher.launch(arrayOf("application/json", "*/*"))
                        }
                    )
                }
            }
        }
    }

    // BARU (bug "app tidak muncul di 'Buka dengan'..."): dipanggil sistem saat app
    // ini SUDAH berjalan (di foreground/background) dan user tap file lagu lagi
    // dari file manager/app lain. Karena Activity ini launchMode="singleTop",
    // instance yang sudah ada dipakai ulang -- onCreate() TIDAK dipanggil lagi,
    // jadi Uri file baru harus ditangkap di sini, bukan cuma di onCreate().
    // setIntent(intent) WAJIB dipanggil supaya getIntent() ke depannya (mis. kalau
    // Activity di-recreate karena rotasi/config change) mengembalikan intent yang
    // baru ini, bukan intent lama yang sudah "basi".
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingMediaIntent(intent)
    }

    // BARU (bug "app tidak muncul di 'Buka dengan'..."): file tunggal ada di
    // intent.data (ACTION_VIEW normal). File JAMAK (user pilih banyak sekaligus di
    // file manager yang mendukungnya) dikirim lewat intent.clipData, BUKAN
    // intent.data -- makanya dua-duanya dicek di sini, bukan cuma salah satu.
    private fun handleIncomingMediaIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return

        val clip = intent.clipData
        if (clip != null && clip.itemCount > 0) {
            val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
            if (uris.isNotEmpty()) {
                vm.playExternalUris(uris)
                return
            }
        }

        intent.data?.let { uri ->
            vm.playExternalUri(uri)
        }
    }

    private fun requestAudioPermissionIfNeeded() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            // Dialog izin notifikasi BARU diminta lewat callback permissionLauncher di atas,
            // setelah dialog izin audio ini benar-benar selesai -- lihat catatan bug di
            // deklarasi permissionLauncher.
            permissionLauncher.launch(permission)
        } else {
            // Izin audio sudah ada dari sebelumnya (tidak ada dialog yang perlu ditunggu),
            // jadi aman langsung lanjut cek izin notifikasi sekarang juga.
            requestNotificationPermissionIfNeeded()
        }
        // Tidak ada pemanggilan scan otomatis di sini lagi. Library hanya terisi dari:
        // 1) folder tersimpan yang di-restore PlayerViewModel saat init (jika toggle aktif), atau
        // 2) folder yang dipilih manual oleh pengguna lewat pickFolderLauncher di atas.
    }

    /**
     * Helper universal utk izin POST_NOTIFICATIONS (dibutuhkan supaya control media di
     * Statusbar/Quick Settings/Lockscreen dari PlaybackService bisa muncul):
     * - Android 13+ (API 33+, TIRAMISU): izin ini runtime permission sungguhan, wajib
     *   diminta lewat launcher seperti izin lain.
     * - Android 12 ke bawah: POST_NOTIFICATIONS belum ada sebagai runtime permission (baru
     *   diperkenalkan di API 33) -- checkSelfPermission()/requestPermission() untuk string
     *   ini di versi lama BISA memicu crash/perilaku tidak terduga di sebagian OEM, jadi
     *   fungsi ini langsung no-op aman di bawah API 33. Notifikasi di versi lama otomatis
     *   diizinkan selama Notification Channel-nya sendiri tidak diblokir manual oleh user.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Tanpa ini, sistem operasi (battery saver / Doze / vendor task-killer) menganggap app ini
     * sebagai app biasa yang boleh dibekukan begitu layar mati atau app di-background, sehingga
     * musik yang sedang diputar bisa berhenti sendiri. Minta pengguna mengecualikan app dari
     * optimasi baterai supaya playback tidak dibunuh sistem.
     */
    private fun requestIgnoreBatteryOptimizationsIfNeeded() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            }
        }
    }
}

@UnstableApi
@Composable
private fun AppRoot(
    vm: PlayerViewModel,
    fullScreenOn: Boolean,
    onFullScreenToggle: () -> Unit,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit,
    onUploadPreset: () -> Unit,
    onExportPreset: (String) -> Unit,
    onBackupSettings: (String) -> Unit,
    onRestoreSettings: () -> Unit
) {
    val screen by vm.currentScreen

    // BoxWithConstraints supaya kita tahu lebar window yang BENAR-BENAR tersedia
    // sekarang -- bukan lebar layar fisik. Ini yang membedakan app berjalan normal
    // (fullscreen) vs sedang dipersempit sistem lewat mode Split Screen/Multi-Window
    // (BARU v1.5, lihat isCompact di bawah).
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBlack)
            // FIX (v1.5): dulu "menyempit"-nya konten saat immersive OFF diserahkan ke
            // sistem lewat decorFitsSystemWindows(true) di FullScreenController, yang di
            // beberapa perangkat ikut menyempitkan sisi kiri & kanan sekaligus (bukan
            // cuma atas & bawah). Sekarang window SELALU edge-to-edge, dan Compose di
            // sini yang menambahkan padding PERSIS untuk systemBars() saja (status bar +
            // navigation bar, di sisi manapun nav bar itu sungguhan berada) -- tidak
            // termasuk displayCutout/captionBar -- dan HANYA saat immersive OFF.
            .then(
                if (!fullScreenOn) {
                    Modifier.windowInsetsPadding(WindowInsets.systemBars)
                } else {
                    Modifier
                }
            )
    ) {

        // BARU (v1.5): ambang lebar "compact" untuk Split Screen/Multi-Window. Di
        // bawah ambang ini, NavRail & sidebar Library beralih ke tata letak ringkas
        // (ikon saja, lebih sempit) dan Player screen mengecilkan padding + VU Meter
        // supaya semua kontrol tetap muat berdampingan tanpa terpotong/tumpang tindih.
        val isCompact = maxWidth < 620.dp

        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxSize()) {
                when (screen) {
                    Screen.PLAYER -> PlayerScreen(
                        vm,
                        isCompact = isCompact,
                        fullScreenOn = fullScreenOn,
                        onFullScreenToggle = onFullScreenToggle
                    )
                    Screen.EQUALIZER -> EqualizerScreen(
                        vm,
                        onUploadPreset = onUploadPreset,
                        // BARU (v1.2): nama file default = nama preset aktif, disanitasi
                        // sedikit supaya aman dipakai SAF CreateDocument di semua provider.
                        onExportPreset = {
                            val safeName = vm.activePreset.value.name
                                .ifBlank { "Preset" }
                                .replace(Regex("[^A-Za-z0-9 _-]"), "_")
                            onExportPreset("$safeName.json")
                        }
                    )
                    Screen.LIBRARY -> LibraryScreen(vm, isCompact = isCompact)
                    Screen.IMPORT -> ImportScreen(vm, onPickFiles = onPickFiles, onPickFolder = onPickFolder)
                    Screen.LYRICS -> LyricsScreen(vm)
                    Screen.SETTINGS -> SettingsScreen(
                        vm,
                        onOpenStreaming = { vm.navigate(Screen.STREAMING) },
                        // BARU (v2.0): nama file default backup = "TapeAmp32_Backup.json",
                        // pola sama dengan safeName preset EQ di onExportPreset di atas.
                        onBackupSettings = { onBackupSettings("TapeAmp32_Backup.json") },
                        onRestoreSettings = onRestoreSettings
                    )
                    Screen.STREAMING -> StreamingScreen(vm)
                }
            }
            NavRail(current = screen, onSelect = { vm.navigate(it) }, isCompact = isCompact)
        }
    }
}
