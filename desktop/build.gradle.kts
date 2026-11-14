import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Proyek Gradle TERPISAH dari app Android (tidak di-include di settings.gradle.kts root),
// supaya plugin AGP 9 milik app Android tidak bercampur dengan plugin Compose Desktop.
//
// Sumber app Android (UI, ViewModel, DSP, SettingsRepository, ...) DISALIN ke
// build/generated/shared-src oleh task `syncShared` dengan penulisan ulang impor Android ->
// shim desktop (tabel `rewrites`). File asli di ../app TIDAK diubah; ubah kode di app/
// lalu build ulang desktop, maka perubahan ikut otomatis.

plugins {
    kotlin("jvm") version "2.2.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
    id("org.jetbrains.compose") version "1.9.3"
}

group = "com.projectzero.tapeamp32"
version = "0.2.0"

apply(from = "gradle/genstrings.gradle.kts")

val appMain = rootDir.resolve("../app/src/main")

// File app Android yang TIDAK disalin karena diganti implementasi desktop (di src/main/kotlin
// dengan nama kelas & package yang sama) atau memang khusus Android.
val replacedOrAndroidOnly = listOf(
    "com/projectzero/tapeamp32/MainActivity.kt",                // -> desktop/Main.kt
    "com/projectzero/tapeamp32/audio/PlaybackService.kt",       // -> (tray/SMTC: belum)
    "com/projectzero/tapeamp32/audio/PlayerManager.kt",         // -> audio/PlayerManager.kt (desktop)
    "com/projectzero/tapeamp32/audio/UsbDacObserver.kt",        // -> audio/UsbDacObserver.kt (desktop)
    "com/projectzero/tapeamp32/widget/**",                      // widget home screen: khusus Android
    "com/projectzero/tapeamp32/util/ShareCardRenderer.kt",      // -> util/ShareCardRenderer.kt (desktop)
    "com/projectzero/tapeamp32/ui/utils/**",                    // FullScreenController: diganti F11 di Main
    "com/projectzero/tapeamp32/data/LocaleManager.kt",          // -> data/LocaleManager.kt (desktop)
    "com/projectzero/tapeamp32/data/MusicRepository.kt",        // -> data/MusicRepository.kt (desktop)
    "com/projectzero/tapeamp32/data/WaveformExtractor.kt",      // -> data/WaveformExtractor.kt (desktop)
    "com/projectzero/tapeamp32/data/LyricsUtils.kt",            // -> data/LyricsUtils.kt (desktop)
)

private val P = "com.projectzero.tapeamp32.desktop.shim"

// Penulisan ulang per baris. Urutan tidak penting (pola saling lepas).
val rewrites: List<Pair<Regex, String>> = listOf(
    Regex("""\bandroid\.net\.Uri\b""") to "$P.Uri",
    Regex("""\bandroid\.app\.Application\b""") to "$P.Application",
    Regex("""\bandroid\.app\.Activity\b""") to "$P.Activity",
    Regex("""\bandroid\.content\.Context\b""") to "$P.Context",
    Regex("""\bandroid\.content\.Intent\b""") to "$P.Intent",
    Regex("""\bandroid\.provider\.Settings\b""") to "$P.Settings",
    Regex("""\bandroid\.provider\.DocumentsContract\b""") to "$P.DocumentsContract",
    Regex("""\bandroid\.os\.Build\b""") to "$P.Build",
    Regex("""\bandroid\.util\.Log\b""") to "$P.Log",
    Regex("""\bandroid\.view\.WindowManager\b""") to "$P.WindowManager",
    Regex("""\bandroidx\.lifecycle\.AndroidViewModel\b""") to "$P.AndroidViewModel",
    Regex("""\bandroidx\.compose\.ui\.platform\.LocalContext\b""") to "$P.LocalContext",
    Regex("""\bandroidx\.compose\.ui\.platform\.LocalConfiguration\b""") to "$P.LocalConfiguration",
    Regex("""\bandroidx\.compose\.ui\.platform\.LocalLifecycleOwner\b""") to "androidx.lifecycle.compose.LocalLifecycleOwner",
    Regex("""\bandroidx\.compose\.ui\.res\.stringResource\b""") to "$P.stringResource",
    Regex("""\bandroidx\.media3\.common\.audio\.AudioProcessor\b""") to "$P.AudioProcessor",
    Regex("""\bandroidx\.media3\.common\.audio\.BaseAudioProcessor\b""") to "$P.BaseAudioProcessor",
    Regex("""\bandroidx\.media3\.common\.util\.UnstableApi\b""") to "$P.UnstableApi",
    Regex("""\bandroidx\.media3\.common\.Player\b""") to "$P.Player",
    Regex("""\bandroidx\.media3\.common\.C\b""") to "$P.C",
    Regex("""\bcoil\.compose\.AsyncImage\b""") to "$P.AsyncImage",
    // Spasi = play/pause global. Di desktop, composable .clickable ikut menerima fokus saat diklik mouse,
    // lalu Spasi malah "menekan" tombol yang terakhir diklik (mis. F.FWD = next). Jadikan tidak fokusable.
    Regex("""^import androidx\.compose\.foundation\.clickable\s*$""") to
        "import androidx.compose.foundation.clickable\nimport androidx.compose.ui.focus.focusProperties",
    Regex("""^(\s*)\.clickable \{""") to "\$1.focusProperties { canFocus = false }.clickable {",
    // Kaset: pakai pembungkus yang memperbesar kaset di jendela desktop (lihat CassetteScale.kt).
    Regex("""^(\s*)CassetteDeck\($""") to "\$1$P.ScaledCassetteDeck(",
    // SettingsRepository: DataStore Android (Context.preferencesDataStore) -> DataStore file JVM.
    Regex("""^import androidx\.datastore\.preferences\.preferencesDataStore\s*$""") to "",
    Regex("""^private val Context\.dataStore by preferencesDataStore\(.*\)\s*$""") to
        "private val Context.dataStore: androidx.datastore.core.DataStore<Preferences> " +
        "get() = $P.DesktopDataStore.instance",
)

// Penyesuaian KHUSUS desktop per file (layar lebih besar & ada keyboard). Kunci = akhiran path file.
// Urutan aturan penting (jangan sampai hasil satu aturan kena aturan berikutnya).
val fileRewrites: List<Pair<String, List<Pair<Regex, String>>>> = listOf(
    // Daftar lagu: baris & huruf diperbesar (aslinya ukuran HP).
    "ui/screens/LibrarySongsView.kt" to listOf(
        Regex("""\.height\(30\.dp\)""") to ".height(42.dp)",
        Regex("""\.height\(25\.dp\)""") to ".height(32.dp)",
        Regex("""fontSize = 9\.sp""") to "fontSize = 13.sp",
        Regex("""fontSize = 8\.sp""") to "fontSize = 11.sp",
        Regex("""\.size\(18\.dp\)""") to ".size(22.dp)",
    ),
    // Pintasan Alt+huruf pindah tab Library (lihat shim/LibraryTabRequest.kt & Main.kt).
    "ui/screens/LibraryScreen.kt" to listOf(
        Regex("""^(\s*)val openPlaylist = remember\(playlists, openPlaylistId\) \{$""") to
            "\$1val dsTabReq = $P.LibraryTabRequest.pending.value\n" +
            "\$1LaunchedEffect(dsTabReq) {\n" +
            "\$1    if (dsTabReq != null) {\n" +
            "\$1        $P.LibraryTabRequest.pending.value = null\n" +
            "\$1        if (System.currentTimeMillis() - dsTabReq.at < 1500) {\n" +
            "\$1            LibraryTab.values().getOrNull(dsTabReq.index)?.let { switchTab(it) }\n" +
            "\$1        }\n" +
            "\$1    }\n" +
            "\$1}\n" +
            "\$1val openPlaylist = remember(playlists, openPlaylistId) {",
    ),
    // Toolbar/pencarian: lebih besar + "ketik langsung untuk mencari" (lihat shim/TypeToSearch.kt).
    "ui/screens/LibrarySidebarAndToolbar.kt" to listOf(
        Regex("""fontSize = 11\.sp""") to "fontSize = 14.sp",
        Regex("""fontSize = 10\.sp""") to "fontSize = 13.sp",
        Regex("""fontSize = 8\.5\.sp""") to "fontSize = 11.sp",
        Regex("""fontSize = 8\.sp""") to "fontSize = 10.sp",
        Regex("""\.height\(32\.dp\)""") to ".height(38.dp)",
        Regex("""\.height\(34\.dp\)""") to ".height(40.dp)",
        Regex("""^(\s*)BasicTextField\($""") to
            "\$1val dsSearch = $P.rememberTypeToSearch(query, onQueryChange)\n\$1BasicTextField(",
        Regex("""^(\s*)value = query,\s*$""") to "\$1value = dsSearch.value,",
        Regex("""^(\s*)onValueChange = onQueryChange,\s*$""") to "\$1onValueChange = dsSearch.onValueChange,",
        Regex("""modifier = Modifier\.fillMaxWidth\(\),""") to
            "modifier = Modifier.fillMaxWidth().then(dsSearch.modifier),",
    ),
)

val sharedSrcDir = layout.buildDirectory.dir("generated/shared-src")

val syncShared = tasks.register<Sync>("syncShared") {
    group = "tapeamp32"
    description = "Copy shared Android sources with imports rewritten to desktop shims"
    from(appMain.resolve("java")) {
        include("**/*.kt")
        exclude(replacedOrAndroidOnly)
        eachFile {
            val relPath = this@eachFile.path
            filter { line ->
                var out: String = line ?: ""
                for ((re, rep) in rewrites) out = re.replace(out, rep)
                for ((suffix, rules) in fileRewrites) {
                    if (relPath.endsWith(suffix)) for ((re, rep) in rules) out = re.replace(out, rep)
                }
                out
            }
        }
    }
    into(sharedSrcDir)
    inputs.property("rewrites", rewrites.map { it.first.pattern + " -> " + it.second })
    inputs.property("fileRewrites", fileRewrites.flatMap { (f, rs) -> rs.map { f + ": " + it.first.pattern + " -> " + it.second } })
}

// Kunci target bytecode ke 17 supaya build jalan di JDK 17..25 (Java & Kotlin harus sama).
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    sourceSets.main {
        kotlin.srcDir(sharedSrcDir)
        kotlin.srcDir(layout.buildDirectory.dir("generated/r"))
    }
}

tasks.withType<KotlinCompile>().configureEach {
    dependsOn(syncShared, "generateR", "generateBuildConfig")
}

// strings.xml semua bahasa dikemas ke classpath supaya bisa dibaca saat runtime.
val syncStrings = tasks.register<Sync>("syncStrings") {
    from(appMain.resolve("res")) { include("values*/strings.xml") }
    into(layout.buildDirectory.dir("generated/strings/tapeamp-strings"))
}
sourceSets.main {
    resources.srcDir(layout.buildDirectory.dir("generated/strings"))
}
tasks.named("processResources") { dependsOn(syncStrings) }

val lifecycleVersion = "2.9.4"

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.foundation)
    implementation(compose.animation)
    // Icon extended: versi di atas 1.7.3 tidak dipublikasikan lagi, aman dipakai bareng CMP 1.9.
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")

    // ViewModel/viewModelScope/collectAsStateWithLifecycle/LocalLifecycleOwner di desktop.
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:$lifecycleVersion")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:$lifecycleVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")

    // SettingsRepository memakai DataStore Preferences (versi JVM, bukan Android).
    implementation("androidx.datastore:datastore-preferences-core:1.1.7")

    // Sama seperti app Android (Song/Playlist/EqPreset/Radio pakai org.json).
    implementation("org.json:json:20240303")
    // RadioBrowserRepository memakai OkHttp.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Decoder: FFmpeg (MP3/FLAC/OGG/Opus/AAC/WAV/HLS/ICY radio). Hanya native Windows x64.
    implementation("org.bytedeco:javacv:1.5.12")
    implementation("org.bytedeco:javacpp:1.5.12:windows-x86_64")
    implementation("org.bytedeco:ffmpeg:7.1.1-1.5.12:windows-x86_64")

    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "com.projectzero.tapeamp32.desktop.MainKt"
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED")

        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "TapeAmp32"
            packageVersion = "1.0.2" // MSI/EXE mewajibkan MAJOR > 0; naikkan tiap rilis supaya bisa upgrade
            // Java mini bawaan installer hanya berisi modul yang terdeteksi. Tambahkan yang dibutuhkan
            // saat runtime: Unsafe (javacpp/okio/coroutines), TLS EC untuk radio https, JMX, dll.
            // JavaCV/JavaCPP memuat library native lewat Loader yang butuh banyak modul JDK
            // (java.logging, java.desktop, java.xml, dll). Daftar modul manual gampang kurang, sehingga
            // FFmpegFrameGrabber gagal init di EXE hasil package. Sertakan semua modul JDK.
            includeAllModules = true
            description = "TapeAmp 32 - retro cassette music player"
            vendor = "Project Zero"
            windows {
                iconFile.set(project.file("icons/tapeamp32.ico"))
                menu = true
                menuGroup = "TapeAmp32"
                shortcut = true
                dirChooser = true
                perUserInstall = true   // tidak butuh hak admin
            }
        }
    }
}
