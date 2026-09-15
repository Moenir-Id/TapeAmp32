package com.projectzero.tapeamp32.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.projectzero.tapeamp32.BuildConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.projectzero.tapeamp32.R
import com.projectzero.tapeamp32.data.LocaleManager
import com.projectzero.tapeamp32.data.SettingsKeys
import com.projectzero.tapeamp32.ui.theme.*
import com.projectzero.tapeamp32.viewmodel.PlayerViewModel
import com.projectzero.tapeamp32.viewmodel.SleepTimerMode
import kotlinx.coroutines.launch

/* ================================================================
 * SETTINGS CATEGORY
 * ================================================================ */

internal enum class SettingsCategory(
    val labelRes: Int,
    val titleRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    STREAMING_NETWORK(
        R.string.settings_cat_streaming_label,
        R.string.settings_cat_streaming_title,
        Icons.Filled.Wifi
    ),

    UI_APPEARANCE(
        R.string.settings_cat_ui_label,
        R.string.settings_cat_ui_title,
        Icons.Filled.Palette
    ),

    LIBRARY(
        R.string.settings_cat_library_label,
        R.string.settings_cat_library_title,
        Icons.Filled.LibraryMusic
    ),

    SYSTEM(
        R.string.settings_cat_system_label,
        R.string.settings_cat_system_title,
        Icons.Filled.Memory
    ),

    // BARU (v2.0): kategori sendiri untuk Backup & Restore -- sebelumnya sempat
    // ditaruh sebagai subsection di dalam SYSTEM, sekarang dipisah jadi menu
    // tersendiri di sidebar supaya lebih gampang ditemukan (bukan tersembunyi di
    // bawah toggle System lain).
    BACKUP_RESTORE(
        R.string.settings_cat_backup_label,
        R.string.settings_cat_backup_title,
        Icons.Filled.SettingsBackupRestore
    )
}

// BARU: sub-menu di dalam kategori SYSTEM -- sebelumnya SEMUA isi System
// (Keep Screen Awake, Sleep Timer, Statusbar/Lock Screen, About/Changelog)
// ditumpuk jadi satu scroll panjang. Sekarang SYSTEM menampilkan daftar
// sub-menu dulu (mirip Settings > System bawaan Android), lalu tiap sub-menu
// punya halamannya sendiri yang jauh lebih pendek.
internal enum class SettingsSystemSubPage(
    val titleRes: Int,
    val descRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    PLAYBACK(
        R.string.settings_sys_playback_title,
        R.string.settings_sys_playback_desc,
        Icons.Filled.ScreenLockPortrait
    ),
    SLEEP_TIMER(
        R.string.settings_sys_sleep_timer_title,
        R.string.settings_sys_sleep_timer_desc,
        Icons.Filled.Bedtime
    ),
    NOTIFICATIONS(
        R.string.settings_sys_notifications_title,
        R.string.settings_sys_notifications_desc,
        Icons.Filled.Notifications
    ),
    LANGUAGE(
        R.string.settings_sys_language_title,
        R.string.settings_sys_language_desc,
        Icons.Filled.Language
    ),
    ABOUT(
        R.string.settings_sys_about_title,
        R.string.settings_sys_about_desc,
        Icons.Filled.Info
    )
}

/* ================================================================
 * SETTINGS SCREEN
 * ================================================================ */

@Composable
fun SettingsScreen(
    vm: PlayerViewModel,
    onOpenStreaming: () -> Unit,
    // BARU (v2.0): Backup & Restore semua pengaturan sekaligus -- lihat
    // SettingsCategory.SYSTEM di bawah & MainActivity.backupSettingsLauncher/
    // restoreSettingsLauncher. Default lambda kosong supaya preview/pemanggil lama
    // tidak wajib diubah.
    onBackupSettings: () -> Unit = {},
    onRestoreSettings: () -> Unit = {}
) {
    var category by remember {
        mutableStateOf(SettingsCategory.STREAMING_NETWORK)
    }

    // BARU: sub-halaman aktif di dalam kategori SYSTEM. null = tampilkan daftar
    // sub-menu (lihat SettingsSystemSubPage) alih-alih langsung isi pengaturan.
    var systemSubPage by remember {
        mutableStateOf<SettingsSystemSubPage?>(null)
    }

    val scope = rememberCoroutineScope()
    val repo = vm.settingsRepository
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    // BARU: bahasa tampilan aplikasi saat ini (System > Language) -- dibaca sinkron
    // dari SharedPreferences khusus LocaleManager, bukan dari DataStore/repo di atas.
    var appLanguage by remember {
        mutableStateOf(LocaleManager.getSavedLanguage(context))
    }

    /* ------------------------------------------------------------
     * LIBRARY SETTINGS
     * ------------------------------------------------------------ */

    val autoScanOnStartup by repo.autoScanOnStartup.collectAsStateWithLifecycle(
        initialValue = true
    )

    // BARU (v1.2): status pemindaian untuk tombol "Rescan Music Library Now" di
    // bawah -- dipakai supaya label tombol berubah jadi "Scanning..." dan tidak
    // bisa dipicu dobel selagi pemindaian sebelumnya masih berjalan.
    val isScanningLibrary by vm.isScanning.collectAsStateWithLifecycle(
        initialValue = false
    )

    /* ------------------------------------------------------------
     * UI / PLAYBACK / SYSTEM SETTINGS -- PATCH (v1.4)
     *
     * SEBELUMNYA: semua nilai di bawah ini cuma `var ... by remember
     * { mutableStateOf(...) }` -- state lokal Composable ini SAJA.
     * Efeknya dua lapis: (1) Theme Accent Color memang tidak pernah
     * dibaca di mana pun jadi betul-betul tanpa efek visual, dan (2)
     * seluruh toggle/dropdown di kategori UI & Appearance, Playback,
     * dan sebagian System diam-diam RESET ke default setiap kali user
     * pindah kategori/layar atau menutup app, karena tidak pernah
     * ditulis ke DataStore sama sekali.
     *
     * SEKARANG: dibaca dari SettingsRepository (persist permanen,
     * pola sama seperti autoScanOnStartup/keepScreenAwake/dll di atas) supaya
     * pilihan user benar-benar tersimpan.
     * ------------------------------------------------------------ */

    val themeAccent by repo.themeAccent.collectAsStateWithLifecycle(
        initialValue = "Gold Retro"
    )

    val compactVu by repo.compactVu.collectAsStateWithLifecycle(
        initialValue = false
    )

    val reelAnimation by repo.reelAnimation.collectAsStateWithLifecycle(
        initialValue = true
    )

    // FIX (v1.4.1): Gapless Playback, ReplayGain, Audio Output Engine, dan
    // High-Performance DSP Threading DIHAPUS dari Settings -- keempatnya cuma
    // toggle/dropdown dekoratif yang tidak pernah benar-benar mengubah perilaku
    // ExoPlayer/DSP (dicek ulang: tidak direferensikan di file audio mana pun
    // selain layar Settings & repository penyimpanannya sendiri), dan kontrol
    // audio yang SUNGGUH nyata (EQ, Vocal, Stereo, Limiter, Bypass) sudah lengkap
    // di layar Equalizer. Daripada dibiarkan jadi tombol yang menipu (kelihatan
    // berfungsi padahal tidak), lebih jujur untuk dihapus sekalian dengan seluruh
    // kategori PLAYBACK-nya. Key DataStore-nya sengaja dibiarkan ada di
    // SettingsRepository (tidak dihapus) supaya tidak ada breaking change kalau
    // suatu saat mau diimplementasi beneran.

    val ignoreShortTracks by repo.ignoreShortTracks.collectAsStateWithLifecycle(
        initialValue = true
    )

    val keepScreenAwake by repo.keepScreenAwake.collectAsStateWithLifecycle(
        initialValue = true
    )

    // BARU (v2.1): SLEEP TIMER. sleepTimerMinutesSetting = durasi terakhir yang dipilih
    // (dari SettingsRepository, cuma dipakai untuk posisi awal slider). sleepTimerMode &
    // sleepTimerRemainingMs = status HIDUP aktual dari PlayerViewModel (lihat komentar
    // panjang di PlayerViewModel.startSleepTimer/startSleepTimerEndOfTrack).
    val sleepTimerMinutesSetting by repo.sleepTimerMin.collectAsStateWithLifecycle(
        initialValue = 0f
    )
    val sleepTimerMode by vm.sleepTimerMode.collectAsStateWithLifecycle()
    val sleepTimerRemainingMs by vm.sleepTimerRemainingMs.collectAsStateWithLifecycle()

    // BARU (v1.4): "Keep Screen Awake During Playback" sekarang benar-benar
    // mengunci layar lewat FLAG_KEEP_SCREEN_ON pada window Activity -- sebelumnya
    // toggle ini cuma kosmetik, layar tetap bisa mati sendiri walau ON. Karena app
    // ini single-Activity, flag yang di-set di sini tetap berlaku walau user lalu
    // pindah ke layar Player/Library lain (bukan cuma selagi Settings terbuka).
    LaunchedEffect(keepScreenAwake) {
        (context as? android.app.Activity)?.window?.let { win ->
            if (keepScreenAwake) {
                win.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    var showChangelogDialog by remember {
        mutableStateOf(false)
    }

    // BARU (v2.0): dialog konfirmasi sebelum RESTORE ALL SETTINGS -- aksi ini
    // meng-CLEAR lalu menimpa SELURUH pengaturan & preset tersimpan (lihat
    // SettingsRepository.importAllSettingsJson), jadi tidak boleh langsung terpicu
    // dari satu tap saja seperti tombol EXPORT/BACKUP yang aman (read-only).
    var showRestoreConfirmDialog by remember {
        mutableStateOf(false)
    }

    /* ============================================================
     * ROOT
     * ============================================================ */

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBlack)
    ) {

        /* ========================================================
         * LEFT SIDEBAR
         * ======================================================== */

        Column(
            modifier = Modifier
                .width(205.dp)
                .fillMaxHeight()
                .background(PanelBlackAlt)
                .padding(
                    horizontal = 8.dp,
                    vertical = 9.dp
                )
        ) {

            SettingsCategory.entries.forEach { cat ->

                val selected = category == cat

                SettingsSidebarItem(
                    category = cat,
                    selected = selected,
                    onClick = {

                        category = cat
                        systemSubPage = null

                        if (
                            cat ==
                            SettingsCategory.STREAMING_NETWORK
                        ) {
                            onOpenStreaming()
                        }
                    }
                )

                Spacer(
                    modifier = Modifier.height(3.dp)
                )
            }
        }

        /* ========================================================
         * GOLD DIVIDER
         * ======================================================== */

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(
                    StrokeGold.copy(alpha = 0.75f)
                )
        )

        /* ========================================================
         * RIGHT CONTENT
         * ======================================================== */

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    horizontal = 18.dp,
                    vertical = 14.dp
                )
        ) {

            val activeSubPage = systemSubPage
            if (category == SettingsCategory.SYSTEM && activeSubPage != null) {

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            systemSubPage = null
                        }
                        .padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ChevronLeft,
                        contentDescription = stringResource(R.string.settings_back),
                        tint = GoldBright,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = stringResource(activeSubPage.titleRes),
                        color = GoldBright,
                        fontFamily = MonoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 0.25.sp
                    )
                }
            } else {
                Text(
                    text = stringResource(category.titleRes),
                    color = GoldBright,
                    fontFamily = MonoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 0.25.sp
                )
            }

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            when (category) {

                /* =================================================
                 * STREAMING & NETWORK
                 * ================================================= */

                SettingsCategory.STREAMING_NETWORK -> {

                    SettingsSectionTitle(
                        text = stringResource(R.string.settings_section_streaming_network)
                    )

                    SettingsInfoRow(
                        label = stringResource(R.string.settings_label_status),
                        value = stringResource(R.string.settings_value_open_streaming)
                    )

                    SettingsInfoRow(
                        label = stringResource(R.string.settings_label_buffer),
                        value = stringResource(R.string.settings_value_buffer_amount)
                    )

                    SettingsInfoRow(
                        label = stringResource(R.string.settings_label_connection),
                        value = stringResource(R.string.settings_value_icecast)
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    SettingsActionRow(
                        label = stringResource(R.string.settings_action_open_streaming),
                        onClick = onOpenStreaming
                    )
                }

                /* =================================================
                 * UI & APPEARANCE
                 * ================================================= */

                SettingsCategory.UI_APPEARANCE -> {

                    // FIX (v1.4): dulu betul-betul tanpa efek sama sekali (lihat
                    // catatan di Color.kt / Theme.kt) -- sekarang benar-benar
                    // mengganti Gold/GoldBright/GoldDim/StrokeGold di SELURUH app
                    // lewat vm.setThemeAccent(), plus tersimpan permanen.
                    SettingsDropdownRow(
                        label = stringResource(R.string.settings_label_theme_accent),
                        value = themeAccent,
                        options = listOf(
                            "Gold Retro",
                            "Neon 80s",
                            "Silver Hi-Fi"
                        )
                    ) {
                        vm.setThemeAccent(it)
                    }

                    // FIX (v1.4): tersimpan permanen ke DataStore (sebelumnya reset
                    // tiap keluar dari layar Settings).
                    SettingsToggleRow(
                        label = stringResource(R.string.settings_label_compact_vu),
                        checked = compactVu
                    ) {
                        scope.launch {
                            repo.setBool(
                                SettingsKeys.COMPACT_VU,
                                it
                            )
                        }
                    }

                    SettingsToggleRow(
                        label = stringResource(R.string.settings_label_reel_animation),
                        checked = reelAnimation
                    ) {
                        scope.launch {
                            repo.setBool(
                                SettingsKeys.REEL_ANIMATION,
                                it
                            )
                        }
                    }
                }

                /* =================================================
                 * LIBRARY
                 * ================================================= */

                SettingsCategory.LIBRARY -> {

                    SettingsToggleRow(
                        label = stringResource(R.string.settings_label_auto_scan),
                        checked = autoScanOnStartup
                    ) {
                        scope.launch {
                            repo.setBool(
                                SettingsKeys.AUTO_SCAN_ON_STARTUP,
                                it
                            )
                        }
                    }

                    // FIX (v1.4.1): sekarang beneran memfilter hasil scan (lihat
                    // applyIgnoreShortTracksFilter() di PlayerViewModel) -- lagu dengan
                    // durasi diketahui < 30 detik dikeluarkan dari library & antrian
                    // setelah scan berikutnya (Rescan Music Library Now / restart app).
                    SettingsToggleRow(
                        label = stringResource(R.string.settings_label_ignore_short),
                        checked = ignoreShortTracks
                    ) {
                        scope.launch {
                            repo.setBool(
                                SettingsKeys.IGNORE_SHORT_TRACKS,
                                it
                            )
                        }
                    }

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    // BARU (v1.2): fitur "scan lagu lama" -- pemindaian manual yang
                    // bisa dipicu kapan saja pengguna mau (bukan cuma diam-diam saat
                    // startup), berguna setelah menambah file baru ke folder musik
                    // yang sudah tersimpan tanpa perlu menutup-buka ulang aplikasi.
                    SettingsActionRow(
                        label = if (isScanningLibrary) {
                            stringResource(R.string.settings_action_scanning)
                        } else {
                            stringResource(R.string.settings_action_rescan)
                        },
                        onClick = {
                            if (!isScanningLibrary) vm.rescanLibrary()
                        }
                    )
                }

                /* =================================================
                 * SYSTEM
                 * ================================================= */

                SettingsCategory.SYSTEM -> {

                    // BARU: SYSTEM sekarang dua tingkat -- kalau belum ada sub-menu
                    // dipilih, tampilkan daftarnya dulu (masing-masing baris pendek
                    // + deskripsi), bukan langsung menumpuk semua isi System jadi
                    // satu scroll panjang seperti sebelumnya.
                    if (activeSubPage == null) {

                        SettingsSystemSubPage.entries.forEach { subPage ->
                            SettingsSystemMenuRow(
                                subPage = subPage,
                                onClick = { systemSubPage = subPage }
                            )
                        }

                    } else when (activeSubPage) {

                        /* =========================================
                         * SYSTEM > PLAYBACK & SCREEN
                         * ========================================= */

                        SettingsSystemSubPage.PLAYBACK -> {

                            // FIX (v1.4): sekarang benar-benar mengunci layar (lihat
                            // LaunchedEffect(keepScreenAwake) di atas) DAN tersimpan
                            // permanen -- sebelumnya cuma kosmetik, layar tetap bisa
                            // mati sendiri walau toggle ini ON.
                            SettingsToggleRow(
                                label = stringResource(R.string.settings_label_keep_screen_awake),
                                checked = keepScreenAwake
                            ) {
                                scope.launch {
                                    repo.setBool(
                                        SettingsKeys.KEEP_SCREEN_AWAKE,
                                        it
                                    )
                                }
                            }
                        }

                        /* =========================================
                         * SYSTEM > SLEEP TIMER -- BARU (v2.1)
                         *
                         * Dua opsi independen (pilih salah satu otomatis membatalkan
                         * yang lain -- lihat PlayerViewModel.startSleepTimer/
                         * startSleepTimerEndOfTrack): geser slider ke durasi tetap,
                         * ATAU nyalakan "Stop After Current Track" untuk berhenti
                         * begitu lagu yang sedang diputar sekarang selesai (tanpa
                         * hitung mundur waktu). Status hidup (sisa waktu / "menunggu
                         * lagu selesai") + tombol BATALKAN cuma muncul selagi salah
                         * satu timer aktif.
                         * ========================================= */

                        SettingsSystemSubPage.SLEEP_TIMER -> {

                            val offLabel = stringResource(R.string.settings_slider_off)

                            SettingsSliderRow(
                                label = stringResource(R.string.settings_label_stop_after),
                                value = sleepTimerMinutesSetting,
                                range = 0f..90f,
                                suffix = " min",
                                offLabel = offLabel,
                                onChange = { minutes ->
                                    if (minutes <= 0f) {
                                        vm.cancelSleepTimer()
                                        scope.launch {
                                            repo.setFloat(SettingsKeys.SLEEP_TIMER_MIN, 0f)
                                        }
                                    } else {
                                        vm.startSleepTimer(minutes)
                                    }
                                }
                            )

                            SettingsToggleRow(
                                label = stringResource(R.string.settings_label_stop_after_track),
                                checked = sleepTimerMode == SleepTimerMode.END_OF_TRACK
                            ) { checked ->
                                if (checked) {
                                    vm.startSleepTimerEndOfTrack()
                                } else {
                                    vm.cancelSleepTimer()
                                }
                            }

                            if (sleepTimerMode != SleepTimerMode.OFF) {
                                SettingsInfoRow(
                                    label = stringResource(R.string.settings_label_sleep_timer_status),
                                    value = when (sleepTimerMode) {
                                        SleepTimerMode.MINUTES -> stringResource(
                                            R.string.settings_value_stops_in,
                                            formatSleepCountdown(sleepTimerRemainingMs)
                                        )
                                        SleepTimerMode.END_OF_TRACK -> stringResource(R.string.settings_value_stopping_after_track)
                                        SleepTimerMode.OFF -> ""
                                    }
                                )

                                SettingsActionRow(
                                    label = stringResource(R.string.settings_action_cancel_sleep_timer),
                                    onClick = { vm.cancelSleepTimer() }
                                )
                            }
                        }

                        /* =========================================
                         * SYSTEM > NOTIFICATIONS
                         * ========================================= */

                        SettingsSystemSubPage.NOTIFICATIONS -> {

                            // PATCH: kalau izin notifikasi pernah ditolak, sistem TIDAK
                            // akan pernah menampilkan dialog izin itu lagi lewat kode --
                            // ini penyebab paling umum control bar (statusbar/lockscreen)
                            // tidak pernah muncul sama sekali walau musik tetap terdengar
                            // diputar. Satu-satunya jalan keluar buat pengguna adalah
                            // lewat halaman notification settings App ini secara manual,
                            // jadi tombol ini langsung membukanya.
                            SettingsActionRow(
                                label = stringResource(R.string.settings_action_open_notification_settings),
                                onClick = {
                                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    } else {
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                            .setData(android.net.Uri.parse("package:${context.packageName}"))
                                    }
                                    runCatching { context.startActivity(intent) }
                                }
                            )
                        }

                        /* =========================================
                         * SYSTEM > LANGUAGE -- BARU: multi-bahasa (ID/EN)
                         * ========================================= */

                        SettingsSystemSubPage.LANGUAGE -> {

                            SettingsLanguageRow(
                                label = stringResource(R.string.settings_lang_system),
                                selected = appLanguage == LocaleManager.LANGUAGE_SYSTEM
                            ) {
                                appLanguage = LocaleManager.LANGUAGE_SYSTEM
                                activity?.let {
                                    LocaleManager.applyAndRestart(it, LocaleManager.LANGUAGE_SYSTEM)
                                }
                            }

                            SettingsLanguageRow(
                                label = stringResource(R.string.settings_lang_indonesian),
                                selected = appLanguage == LocaleManager.LANGUAGE_INDONESIAN
                            ) {
                                appLanguage = LocaleManager.LANGUAGE_INDONESIAN
                                activity?.let {
                                    LocaleManager.applyAndRestart(it, LocaleManager.LANGUAGE_INDONESIAN)
                                }
                            }

                            SettingsLanguageRow(
                                label = stringResource(R.string.settings_lang_english),
                                selected = appLanguage == LocaleManager.LANGUAGE_ENGLISH
                            ) {
                                appLanguage = LocaleManager.LANGUAGE_ENGLISH
                                activity?.let {
                                    LocaleManager.applyAndRestart(it, LocaleManager.LANGUAGE_ENGLISH)
                                }
                            }

                            // BARU: 8 bahasa tambahan -- pola persis sama seperti System/ID/EN
                            // di atas, cuma label & kode bahasa yang beda tiap baris.
                            SettingsLanguageRow(
                                label = stringResource(R.string.settings_lang_spanish),
                                selected = appLanguage == LocaleManager.LANGUAGE_SPANISH
                            ) {
                                appLanguage = LocaleManager.LANGUAGE_SPANISH
                                activity?.let {
                                    LocaleManager.applyAndRestart(it, LocaleManager.LANGUAGE_SPANISH)
                                }
                            }

                            SettingsLanguageRow(
                                label = stringResource(R.string.settings_lang_portuguese),
                                selected = appLanguage == LocaleManager.LANGUAGE_PORTUGUESE
                            ) {
                                appLanguage = LocaleManager.LANGUAGE_PORTUGUESE
                                activity?.let {
                                    LocaleManager.applyAndRestart(it, LocaleManager.LANGUAGE_PORTUGUESE)
                                }
                            }

                            SettingsLanguageRow(
                                label = stringResource(R.string.settings_lang_french),
                                selected = appLanguage == LocaleManager.LANGUAGE_FRENCH
                            ) {
                                appLanguage = LocaleManager.LANGUAGE_FRENCH
                                activity?.let {
                                    LocaleManager.applyAndRestart(it, LocaleManager.LANGUAGE_FRENCH)
                                }
                            }

                            SettingsLanguageRow(
                                label = stringResource(R.string.settings_lang_german),
                                selected = appLanguage == LocaleManager.LANGUAGE_GERMAN
                            ) {
                                appLanguage = LocaleManager.LANGUAGE_GERMAN
                                activity?.let {
                                    LocaleManager.applyAndRestart(it, LocaleManager.LANGUAGE_GERMAN)
                                }
                            }

                            SettingsLanguageRow(
                                label = stringResource(R.string.settings_lang_russian),
                                selected = appLanguage == LocaleManager.LANGUAGE_RUSSIAN
                            ) {
                                appLanguage = LocaleManager.LANGUAGE_RUSSIAN
                                activity?.let {
                                    LocaleManager.applyAndRestart(it, LocaleManager.LANGUAGE_RUSSIAN)
                                }
                            }

                            SettingsLanguageRow(
                                label = stringResource(R.string.settings_lang_japanese),
                                selected = appLanguage == LocaleManager.LANGUAGE_JAPANESE
                            ) {
                                appLanguage = LocaleManager.LANGUAGE_JAPANESE
                                activity?.let {
                                    LocaleManager.applyAndRestart(it, LocaleManager.LANGUAGE_JAPANESE)
                                }
                            }

                            SettingsLanguageRow(
                                label = stringResource(R.string.settings_lang_korean),
                                selected = appLanguage == LocaleManager.LANGUAGE_KOREAN
                            ) {
                                appLanguage = LocaleManager.LANGUAGE_KOREAN
                                activity?.let {
                                    LocaleManager.applyAndRestart(it, LocaleManager.LANGUAGE_KOREAN)
                                }
                            }

                            SettingsLanguageRow(
                                label = stringResource(R.string.settings_lang_chinese_simplified),
                                selected = appLanguage == LocaleManager.LANGUAGE_CHINESE_SIMPLIFIED
                            ) {
                                appLanguage = LocaleManager.LANGUAGE_CHINESE_SIMPLIFIED
                                activity?.let {
                                    LocaleManager.applyAndRestart(it, LocaleManager.LANGUAGE_CHINESE_SIMPLIFIED)
                                }
                            }

                            Spacer(
                                modifier = Modifier.height(8.dp)
                            )

                            Text(
                                text = stringResource(R.string.settings_lang_note),
                                color = TextMuted,
                                fontFamily = MonoFont,
                                fontSize = 10.sp
                            )
                        }

                        /* =========================================
                         * SYSTEM > ABOUT
                         * ========================================= */

                        SettingsSystemSubPage.ABOUT -> {

                            // FIX (v2.0): sebelumnya hardcode "1.5" sejak v1.5 dan tidak
                            // pernah diperbarui lagi walau app sudah beberapa kali naik
                            // versi (sempat ganjil menampilkan "1.5" padahal build sudah
                            // versionName "1.9") -- sekarang disamakan manual dengan
                            // versionName di app/build.gradle.kts & entri paling atas
                            // changelogEntries di bawah, supaya App Version di sini,
                            // Changelog di dalam app, dan TapeAmp32_changelog.html selalu
                            // menunjuk ke nomor versi yang sama persis.
                            // FIX (v2.4): kejadian yang sama persis terulang lagi -- label ini
                            // sempat ketinggalan di "2.2" walau app sudah naik ke versionName
                            // "2.3" (v2.3 tidak sempat update baris ini). Sekarang disamakan lagi
                            // manual dengan versionName di app/build.gradle.kts & entri paling
                            // atas changelogEntries di bawah.
                            // FIX AKAR MASALAH (v2.6): value di sini tadinya SELALU hardcode
                            // manual, jadi bug "ketinggalan versi" ini terbukti berulang tiga
                            // kali (v1.5, v2.2/2.3, dan lagi sebelum v2.6 disinkronkan).
                            // Sekarang baca langsung dari BuildConfig.VERSION_NAME (di-generate
                            // otomatis dari versionName di app/build.gradle.kts) -- baris ini
                            // tidak akan pernah ketinggalan lagi karena tidak ada lagi angka
                            // yang perlu diketik manual di sini. changelogEntries di bawah
                            // masih string manual (karena berisi teks historis per versi),
                            // tapi entri PALING ATAS-nya juga sudah ikut BuildConfig.VERSION_NAME
                            // (lihat komentar di atas daftar changelogEntries).
                            SettingsInfoRow(
                                label = stringResource(R.string.settings_label_app_version),
                                value = BuildConfig.VERSION_NAME
                            )

                            SettingsActionRow(
                                label = stringResource(R.string.settings_action_view_changelog),
                                onClick = {
                                    showChangelogDialog = true
                                }
                            )
                        }
                    }
                }

                /* =================================================
                 * BACKUP & RESTORE -- BARU (v2.0)
                 *
                 * Sebelumnya sempat jadi subsection di dalam SYSTEM, sekarang
                 * kategori sendiri di sidebar. Kedua tombol di bawah memakai SAF
                 * (Save As / Open) yang sama seperti EXPORT/UPLOAD preset EQ di
                 * tab BATAS (Equalizer, ada sejak v1.2) -- lihat
                 * MainActivity.backupSettingsLauncher/restoreSettingsLauncher.
                 * Beda dari EXPORT preset EQ (cuma satu preset aktif), Backup di
                 * sini mencakup SELURUH pengaturan sekaligus.
                 * ================================================= */

                SettingsCategory.BACKUP_RESTORE -> {

                    SettingsSectionTitle(
                        text = stringResource(R.string.settings_section_backup)
                    )

                    SettingsActionRow(
                        label = stringResource(R.string.settings_action_backup),
                        onClick = onBackupSettings
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    SettingsActionRow(
                        label = stringResource(R.string.settings_action_restore),
                        onClick = {
                            showRestoreConfirmDialog = true
                        }
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    // Sedikit penjelasan inline di bawah kedua tombol -- dipakai
                    // supaya jelas cakupannya "SEMUA" tanpa harus buka dialog
                    // konfirmasi dulu untuk tahu.
                    Text(
                        text = stringResource(R.string.settings_backup_restore_note),
                        color = TextMuted,
                        fontFamily = MonoFont,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }

    if (showChangelogDialog) {
        ChangelogDialog(
            onDismiss = {
                showChangelogDialog = false
            }
        )
    }

    // BARU (v2.0): konfirmasi RESTORE ALL SETTINGS -- baru memanggil
    // onRestoreSettings() (buka SAF "Open" utk pilih file backup) setelah pengguna
    // benar-benar menekan tombol konfirmasi, bukan langsung dari SettingsActionRow.
    if (showRestoreConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                showRestoreConfirmDialog = false
            },
            title = {
                Text(
                    text = stringResource(R.string.settings_restore_confirm_title),
                    fontFamily = MonoFont,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.settings_restore_confirm_body),
                    fontFamily = MonoFont,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirmDialog = false
                    onRestoreSettings()
                }) {
                    Text(stringResource(R.string.settings_restore_confirm_button), fontFamily = MonoFont, color = GoldBright)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRestoreConfirmDialog = false
                }) {
                    Text(stringResource(R.string.settings_cancel), fontFamily = MonoFont, color = TextMuted)
                }
            }
        )
    }
}

/* ================================================================
 * SIDEBAR ITEM
 * ================================================================ */
