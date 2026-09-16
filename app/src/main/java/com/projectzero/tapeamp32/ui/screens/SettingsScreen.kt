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

    BACKUP_RESTORE(
        R.string.settings_cat_backup_label,
        R.string.settings_cat_backup_title,
        Icons.Filled.SettingsBackupRestore
    )
}

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

@Composable
fun SettingsScreen(
    vm: PlayerViewModel,
    onOpenStreaming: () -> Unit,

    onBackupSettings: () -> Unit = {},
    onRestoreSettings: () -> Unit = {}
) {
    var category by remember {
        mutableStateOf(SettingsCategory.STREAMING_NETWORK)
    }

    var systemSubPage by remember {
        mutableStateOf<SettingsSystemSubPage?>(null)
    }

    val scope = rememberCoroutineScope()
    val repo = vm.settingsRepository
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    var appLanguage by remember {
        mutableStateOf(LocaleManager.getSavedLanguage(context))
    }

    val autoScanOnStartup by repo.autoScanOnStartup.collectAsStateWithLifecycle(
        initialValue = true
    )

    val isScanningLibrary by vm.isScanning.collectAsStateWithLifecycle(
        initialValue = false
    )

    val themeAccent by repo.themeAccent.collectAsStateWithLifecycle(
        initialValue = "Gold Retro"
    )

    val compactVu by repo.compactVu.collectAsStateWithLifecycle(
        initialValue = false
    )

    val reelAnimation by repo.reelAnimation.collectAsStateWithLifecycle(
        initialValue = true
    )

    val ignoreShortTracks by repo.ignoreShortTracks.collectAsStateWithLifecycle(
        initialValue = true
    )

    val keepScreenAwake by repo.keepScreenAwake.collectAsStateWithLifecycle(
        initialValue = true
    )

    val sleepTimerMinutesSetting by repo.sleepTimerMin.collectAsStateWithLifecycle(
        initialValue = 0f
    )
    val sleepTimerMode by vm.sleepTimerMode.collectAsStateWithLifecycle()
    val sleepTimerRemainingMs by vm.sleepTimerRemainingMs.collectAsStateWithLifecycle()

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

    var showRestoreConfirmDialog by remember {
        mutableStateOf(false)
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBlack)
    ) {

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

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(
                    StrokeGold.copy(alpha = 0.75f)
                )
        )

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

                SettingsCategory.UI_APPEARANCE -> {

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

                SettingsCategory.SYSTEM -> {

                    if (activeSubPage == null) {

                        SettingsSystemSubPage.entries.forEach { subPage ->
                            SettingsSystemMenuRow(
                                subPage = subPage,
                                onClick = { systemSubPage = subPage }
                            )
                        }

                    } else when (activeSubPage) {

                        SettingsSystemSubPage.PLAYBACK -> {

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

                        SettingsSystemSubPage.NOTIFICATIONS -> {

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

                        SettingsSystemSubPage.ABOUT -> {

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
