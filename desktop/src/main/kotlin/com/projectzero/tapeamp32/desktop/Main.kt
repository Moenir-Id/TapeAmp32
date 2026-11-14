package com.projectzero.tapeamp32.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.projectzero.tapeamp32.data.LocaleManager
import com.projectzero.tapeamp32.desktop.shim.Configuration
import com.projectzero.tapeamp32.desktop.shim.DesktopApplication
import com.projectzero.tapeamp32.desktop.shim.LocalConfiguration
import com.projectzero.tapeamp32.desktop.shim.LibraryTabRequest
import com.projectzero.tapeamp32.desktop.shim.TypeToSearch
import com.projectzero.tapeamp32.desktop.shim.Uri
import com.projectzero.tapeamp32.ui.components.NavRail
import com.projectzero.tapeamp32.ui.screens.EqualizerScreen
import com.projectzero.tapeamp32.ui.screens.ImportScreen
import com.projectzero.tapeamp32.ui.screens.LibraryScreen
import com.projectzero.tapeamp32.ui.screens.LyricsScreen
import com.projectzero.tapeamp32.ui.screens.PlayerScreen
import com.projectzero.tapeamp32.ui.screens.SettingsScreen
import com.projectzero.tapeamp32.ui.screens.StreamingScreen
import com.projectzero.tapeamp32.ui.theme.BgBlack
import com.projectzero.tapeamp32.ui.theme.TapeAmp32Theme
import com.projectzero.tapeamp32.viewmodel.PlayerViewModel
import com.projectzero.tapeamp32.viewmodel.Screen
import java.io.File
import kotlinx.coroutines.launch

/**
 * Titik masuk desktop. Padanan MainActivity + AppRoot di app Android: semua layar
 * (Player, Equalizer, Library, Import, Lyrics, Settings, Streaming) dan PlayerViewModel
 * adalah file ASLI app Android; yang berbeda hanya "kulit" platform di sini
 * (dialog file, fullscreen, argumen baris perintah = "Open with").
 *
 * Argumen baris perintah: path file audio -> langsung diputar (Windows "Open with").
 */
fun main(args: Array<String>) {
    LocaleManager.applySavedLanguage(DesktopApplication)

    // DIAGNOSTIK: muat FFmpeg lebih awal. Kegagalan PERTAMA (penyebab asli "Could not initialize
    // class") ditulis lengkap ke %USERPROFILE%\\TapeAmp32-ffmpeg.log. Hapus blok ini kalau sudah beres.
    try {
        Class.forName("org.bytedeco.javacv.FFmpegFrameGrabber")
    } catch (t: Throwable) {
        runCatching {
            val f = java.io.File(System.getProperty("user.home"), "TapeAmp32-ffmpeg.log")
            f.writeText(
                "java=" + System.getProperty("java.version") + " os=" + System.getProperty("os.name") +
                    " arch=" + System.getProperty("os.arch") + "\n" +
                    java.io.StringWriter().also { t.printStackTrace(java.io.PrintWriter(it)) }.toString()
            )
        }
    }

    application {
        val vm = remember { PlayerViewModel(DesktopApplication) }
        // Jendela awal ukuran normal (tidak memenuhi layar) dan di tengah; saat di-maximize,
        // tata letak melebar mengikuti jendela (kaset ikut membesar lewat ScaledCassetteDeck).
        val windowState = rememberWindowState(
            position = WindowPosition(Alignment.Center),
            width = 900.dp,
            height = 600.dp
        )
        var fullScreenOn by remember { mutableStateOf(false) }
        var shiftHeld by remember { mutableStateOf(false) }
        var shiftClean by remember { mutableStateOf(false) }
        // Compose tidak punya isRepeat: lacak tombol yang sedang ditahan supaya auto-repeat diabaikan.
        val downKeys = remember { mutableSetOf<Key>() }
        var lastDownWasRepeat by remember { mutableStateOf(false) }

        Window(
            onCloseRequest = {
                vm.playerManager.release()
                exitApplication()
            },
            title = "TapeAmp 32",
            icon = remember { BitmapPainter(useResource("icon.png", ::loadImageBitmap)) },
            state = windowState,
            // Pengamat (preview): mencatat Shift "bersih" -- Shift hanya dihitung sebagai pintasan
            // kalau ditekan lalu dilepas TANPA tombol lain (supaya mengetik huruf kapital di kolom
            // pencarian tidak memindah sumber). Tidak pernah mengonsumsi event.
            onPreviewKeyEvent = { e ->
                val isShift = e.key == Key.ShiftLeft || e.key == Key.ShiftRight
                if (e.type == KeyEventType.KeyDown) {
                    lastDownWasRepeat = !downKeys.add(e.key)
                    if (isShift) { if (!shiftHeld) { shiftHeld = true; shiftClean = true } }
                    else shiftClean = false
                } else if (e.type == KeyEventType.KeyUp) {
                    downKeys.remove(e.key)
                }
                if (e.type == KeyEventType.KeyUp && isShift) {
                    val toggle = shiftHeld && shiftClean
                    shiftHeld = false
                    shiftClean = false
                    if (toggle) vm.toggleCassetteSide()   // Library <-> Radio/Stream, sama seperti double-tap kaset
                }
                false
            },
            // Pintasan (dijalankan setelah komponen yang fokus, jadi Spasi/Enter di kolom teks tetap normal).
            onKeyEvent = { e ->
                // Panah: boleh ditahan (auto-repeat) supaya volume/seek terus bergerak.
                if (e.type == KeyEventType.KeyDown && !e.isAltPressed && !e.isCtrlPressed &&
                    !e.isMetaPressed && handleArrowKey(vm, e.key)
                ) {
                    true
                } else if (e.type != KeyEventType.KeyDown || lastDownWasRepeat) {
                    false
                } else if (e.isAltPressed && !e.isCtrlPressed && !e.isMetaPressed && handleAltShortcut(vm, e.key)) {
                    true
                } else when (e.key) {
                    Key.MediaPlayPause, Key.Spacebar -> { vm.playPause(); true }
                    Key.MediaNext, Key.F2 -> { vm.next(); true }
                    Key.MediaPrevious, Key.F1 -> { vm.previous(); true }
                    Key.Enter, Key.NumPadEnter -> { vm.navigate(Screen.STREAMING); true }
                    Key.F11 -> { fullScreenOn = !fullScreenOn; true }
                    else -> {
                        // Ketik langsung = cari lagu. Kolom teks yang fokus sudah menelan huruf lebih dulu,
                        // jadi sampai sini berarti memang tidak sedang mengetik di kolom teks.
                        val cp = e.utf16CodePoint
                        val screen = vm.currentScreen.value
                        if (!e.isCtrlPressed && !e.isAltPressed && !e.isMetaPressed &&
                            cp > 32 && !Character.isISOControl(cp) &&
                            (screen == Screen.LIBRARY || screen == Screen.PLAYER)
                        ) {
                            TypeToSearch.pending.value =
                                TypeToSearch.Pending(String(Character.toChars(cp)), System.currentTimeMillis())
                            if (screen != Screen.LIBRARY) vm.navigate(Screen.LIBRARY)
                            true
                        } else false
                    }
                }
            }
        ) {
            val parent = window
            LaunchedEffect(Unit) { window.minimumSize = java.awt.Dimension(720, 500) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(fullScreenOn) {
                windowState.placement = if (fullScreenOn) WindowPlacement.Fullscreen else WindowPlacement.Floating
            }
            LaunchedEffect(Unit) {
                if (args.isNotEmpty()) {
                    vm.playExternalUris(args.map { Uri.fromFile(File(it)) })
                }
            }

            TapeAmp32Theme {
                Surface(modifier = Modifier.fillMaxSize(), color = BgBlack) {
                    AppRoot(
                        vm = vm,
                        fullScreenOn = fullScreenOn,
                        onFullScreenToggle = { fullScreenOn = !fullScreenOn },
                        onPickFiles = {
                            val files = DesktopDialogs.pickAudioFiles(parent)
                            if (files.isNotEmpty()) vm.playExternalUris(files.map { Uri.fromFile(it) })
                        },
                        onPickFolder = {
                            DesktopDialogs.pickFolder(parent)?.let { vm.addMusicFolder(Uri.fromFile(it)) }
                        },
                        onUploadPreset = {
                            DesktopDialogs.pickJsonToOpen(parent)?.let { f ->
                                runCatching { f.readText() }.getOrNull()?.let { vm.importPresetJson(it) }
                            }
                        },
                        onExportPreset = { suggestedName ->
                            DesktopDialogs.pickJsonToSave(parent, suggestedName)?.let { f ->
                                runCatching { f.writeText(vm.exportActivePresetJson()) }
                            }
                        },
                        onBackupSettings = { suggestedName ->
                            DesktopDialogs.pickJsonToSave(parent, suggestedName)?.let { f ->
                                scope.launch {
                                    runCatching { f.writeText(vm.exportAllSettingsJson()) }
                                        .onSuccess { DesktopDialogs.info(parent, "Backup saved:\n${f.path}") }
                                        .onFailure { DesktopDialogs.info(parent, "Backup failed: ${it.message}") }
                                }
                            }
                        },
                        onRestoreSettings = {
                            DesktopDialogs.pickJsonToOpen(parent)?.let { f ->
                                scope.launch {
                                    val json = runCatching { f.readText() }.getOrNull()
                                    if (json.isNullOrBlank()) {
                                        DesktopDialogs.info(parent, "File is empty or unreadable.")
                                    } else {
                                        vm.importAllSettingsJson(json)
                                            .onSuccess {
                                                // Sama seperti Android: pengaturan dibaca sekali di init VM,
                                                // jadi aplikasi ditutup agar dibuka ulang dengan nilai baru.
                                                DesktopDialogs.info(parent, "Settings restored. TapeAmp 32 will close; open it again.")
                                                vm.playerManager.release()
                                                exitApplication()
                                            }
                                            .onFailure { DesktopDialogs.info(parent, "Restore failed.") }
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Panah: Atas/Bawah = volume +/-5% (slider volume di panel kiri ikut bergerak, karena memakai
 * state yang sama), Kiri/Kanan = mundur/maju 5 detik. Kolom teks / slider yang sedang fokus
 * menelan panah lebih dulu, jadi mengetik di kolom pencarian tidak terganggu.
 */
private fun handleArrowKey(vm: PlayerViewModel, key: Key): Boolean = when (key) {
    Key.DirectionUp -> { vm.adjustVolume(0.05f); true }
    Key.DirectionDown -> { vm.adjustVolume(-0.05f); true }
    Key.DirectionRight -> { vm.seekTo(vm.positionMs.value + 5000L); true }
    Key.DirectionLeft -> { vm.seekTo((vm.positionMs.value - 5000L).coerceAtLeast(0L)); true }
    else -> false
}

/**
 * Pintasan pindah menu: Alt + huruf. (Huruf polos dipakai "ketik langsung untuk mencari".)
 * Daftar lengkap ada di README.md.
 */
private fun handleAltShortcut(vm: PlayerViewModel, key: Key): Boolean {
    val screen = when (key) {
        Key.D -> Screen.PLAYER
        Key.E -> Screen.EQUALIZER
        Key.I -> Screen.IMPORT
        Key.Y -> Screen.LYRICS
        Key.S -> Screen.SETTINGS
        Key.R -> Screen.STREAMING
        else -> null
    }
    if (screen != null) {
        vm.navigate(screen)
        return true
    }
    // Tab di dalam Library: 0 Songs, 1 Albums, 2 Artists, 3 Folders, 4 Playlists.
    val tab = when (key) {
        Key.L -> 0
        Key.A -> 1
        Key.T -> 2
        Key.F -> 3
        Key.P -> 4
        else -> return false
    }
    LibraryTabRequest.pending.value = LibraryTabRequest.Request(tab, System.currentTimeMillis())
    if (vm.currentScreen.value != Screen.LIBRARY) vm.navigate(Screen.LIBRARY)
    return true
}

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

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(BgBlack)) {
        // Ambang "compact" sama dengan Android (Split Screen): jendela sempit -> NavRail ringkas.
        val isCompact = maxWidth < 620.dp

        CompositionLocalProvider(
            LocalConfiguration provides Configuration(maxWidth.value.toInt(), maxHeight.value.toInt())
        ) {
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
}
