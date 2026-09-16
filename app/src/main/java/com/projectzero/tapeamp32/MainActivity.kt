package com.projectzero.tapeamp32

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.projectzero.tapeamp32.data.LocaleManager.wrapContext(newBase))
    }

    private lateinit var vm: PlayerViewModel

    private val closeAppReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finishAndRemoveTask()
        }
    }

    private val pickAudioLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        uris.forEach { uri ->
            runCatching {
                contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

    }

    private val pickFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        vm.scanFolder(uri)
    }

    private val pickPresetJsonLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()?.let { json ->
            vm.importPresetJson(json)
        }
    }

    private val exportPresetLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(vm.exportActivePresetJson())
            }
        }
    }

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

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {

        requestNotificationPermissionIfNeeded()
    }

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {

    }

    override fun onCreate(savedInstanceState: Bundle?) {

        installSplashScreen()
        super.onCreate(savedInstanceState)

        FullScreenController.applyImmersiveMode(
            window = window,
            enable = FullScreenController.isFullScreenEnabled(this)
        )

        vm = androidx.lifecycle.ViewModelProvider(this)[PlayerViewModel::class.java]

        handleIncomingMediaIntent(intent)

        requestAudioPermissionIfNeeded()
        requestIgnoreBatteryOptimizationsIfNeeded()

        startService(Intent(this, com.projectzero.tapeamp32.audio.PlaybackService::class.java))

        val closeAppFilter = IntentFilter(com.projectzero.tapeamp32.audio.ACTION_CLOSE_APP_UI)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(closeAppReceiver, closeAppFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(closeAppReceiver, closeAppFilter)
        }

        setContent {
            TapeAmp32Theme {
                Surface(modifier = Modifier.fillMaxSize(), color = BgBlack) {

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

    override fun onDestroy() {
        runCatching { unregisterReceiver(closeAppReceiver) }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingMediaIntent(intent)
    }

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

            permissionLauncher.launch(permission)
        } else {

            requestNotificationPermissionIfNeeded()
        }

    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBlack)

            .then(
                if (!fullScreenOn) {
                    Modifier.windowInsetsPadding(WindowInsets.systemBars)
                } else {
                    Modifier
                }
            )
    ) {

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
