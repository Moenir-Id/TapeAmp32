// SHIM API Android untuk desktop. Dipakai lewat PENULISAN ULANG IMPOR saat task `syncShared`
// menyalin sumber app Android (lihat tabel `rewrites` di build.gradle.kts): mis.
// `import android.net.Uri` -> `import com.projectzero.tapeamp32.desktop.shim.Uri`.
// Jadi tidak ada deklarasi di package milik pihak lain, dan sumber di app/ tidak disentuh.
package com.projectzero.tapeamp32.desktop.shim

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.ViewModel
import com.projectzero.tapeamp32.desktop.i18n.Strings
import java.io.File
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ------------------------------------------------------------------ lokasi data aplikasi

object DesktopPaths {
    /** %APPDATA%\TapeAmp32 di Windows, ~/.tapeamp32 di OS lain. */
    val appDir: File by lazy {
        val base = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
        val dir = if (base != null) File(base, "TapeAmp32")
        else File(System.getProperty("user.home"), ".tapeamp32")
        dir.apply { mkdirs() }
    }
}

// ------------------------------------------------------------------ Context / Application / Activity

open class Context {
    open val packageName: String = "com.projectzero.tapeamp32"
    val applicationContext: Context get() = DesktopApplication
    open val filesDir: File get() = DesktopPaths.appDir
    open val cacheDir: File get() = File(DesktopPaths.appDir, "cache").apply { mkdirs() }

    /** Di Android membuka Intent; di desktop tidak ada padanannya -- diabaikan. */
    open fun startActivity(intent: Intent) {}

    companion object {
        const val MODE_PRIVATE: Int = 0
    }
}

open class Application : Context()

open class Activity : Context() {
    /** Di Android me-recreate Activity (dipakai ganti bahasa). Desktop: bahasa langsung live. */
    open fun recreate() {}

    /** Window Android (flag layar menyala dsb.) tidak ada padanannya di desktop. */
    val window: Window? get() = null
}

object DesktopApplication : Application()

/** Dipakai sebagai nilai LocalContext supaya `context as? Activity` di SettingsScreen berhasil. */
object DesktopActivity : Activity()

val LocalContext = staticCompositionLocalOf<Context> { DesktopActivity }

// ------------------------------------------------------------------ Intent / Settings / DocumentsContract / Build / Log

class Intent(val action: String? = null) {
    private val extras = HashMap<String, Any?>()

    // Kode Android memakai dua gaya: `data = uri` (di dalam apply) dan `.setData(uri)` (berantai).
    // JvmName pada accessor properti mencegah bentrok dengan fungsi setData() di bawah.
    @get:JvmName("getDataUri")
    @set:JvmName("setDataUri")
    var data: Uri? = null

    fun putExtra(name: String, value: Any?): Intent { extras[name] = value; return this }
    fun setData(uri: Uri?): Intent { data = uri; return this }

    companion object {
        const val ACTION_SENDTO = "android.intent.action.SENDTO"
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val EXTRA_EMAIL = "android.intent.extra.EMAIL"
        const val EXTRA_SUBJECT = "android.intent.extra.SUBJECT"
        const val EXTRA_TEXT = "android.intent.extra.TEXT"
    }
}

/** Pengganti android.view.Window / WindowManager: flag layar tidak berlaku di desktop. */
class Window {
    fun addFlags(flags: Int) {}
    fun clearFlags(flags: Int) {}
}

object WindowManager {
    object LayoutParams {
        const val FLAG_KEEP_SCREEN_ON: Int = 128
    }
}

object Settings {
    const val ACTION_APP_NOTIFICATION_SETTINGS = "android.settings.APP_NOTIFICATION_SETTINGS"
    const val ACTION_APPLICATION_DETAILS_SETTINGS = "android.settings.APPLICATION_DETAILS_SETTINGS"
    const val ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS =
        "android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"
    const val EXTRA_APP_PACKAGE = "android.provider.extra.APP_PACKAGE"
}

object DocumentsContract {
    /** Di desktop "tree" = folder biasa (URI file:), jadi docId = path-nya. */
    fun getTreeDocumentId(uri: Uri): String =
        runCatching { File(URI(uri.toString())).path }.getOrNull() ?: uri.toString()
}

object Build {
    // Dipakai di teks "Kirim Feedback" (info perangkat); di desktop diisi info OS.
    val MANUFACTURER: String = "PC"
    val MODEL: String = "${System.getProperty("os.name")} ${System.getProperty("os.arch")}"

    object VERSION {
        const val SDK_INT: Int = 34
        val RELEASE: String = System.getProperty("os.version") ?: ""
    }

    object VERSION_CODES {
        const val O: Int = 26
        const val Q: Int = 29
        const val S: Int = 31
        const val TIRAMISU: Int = 33
    }
}

object Log {
    fun d(tag: String, msg: String): Int { println("D/$tag: $msg"); return 0 }
    fun w(tag: String, msg: String): Int { System.err.println("W/$tag: $msg"); return 0 }
    fun e(tag: String, msg: String): Int { System.err.println("E/$tag: $msg"); return 0 }
}

// ------------------------------------------------------------------ Uri

class Uri private constructor(private val raw: String) {

    val scheme: String? get() = runCatching { URI(raw).scheme }.getOrNull()
    val path: String? get() = runCatching { URI(raw).path }.getOrNull()
    val lastPathSegment: String?
        get() = (path ?: raw).trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\').ifEmpty { null }

    override fun toString(): String = raw
    override fun equals(other: Any?): Boolean = other is Uri && other.raw == raw
    override fun hashCode(): Int = raw.hashCode()

    companion object {
        @JvmField
        val EMPTY: Uri = Uri("")

        @JvmStatic
        fun parse(uriString: String): Uri = Uri(uriString)

        @JvmStatic
        fun fromFile(file: File): Uri = Uri(file.toURI().toString())
    }
}

/** URI (file: / path biasa / http(s)) -> sumber yang bisa dibuka FFmpeg/File. */
fun Uri.toSourceString(): String {
    val s = toString()
    return if (scheme.equals("file", ignoreCase = true)) {
        runCatching { File(URI(s)).absolutePath }.getOrDefault(s)
    } else s
}

fun Uri.toFileOrNull(): File? =
    if (scheme.equals("file", ignoreCase = true)) runCatching { File(URI(toString())) }.getOrNull()
    else if (scheme == null) File(toString()) else null

// ------------------------------------------------------------------ ViewModel

open class AndroidViewModel(private val app: Application) : ViewModel() {
    @Suppress("UNCHECKED_CAST")
    fun <T : Application> getApplication(): T = app as T
}

// ------------------------------------------------------------------ Compose: string, konfigurasi, gambar

@Composable
fun stringResource(id: Int): String = Strings.get(id)

@Composable
fun stringResource(id: Int, vararg formatArgs: Any): String = Strings.format(id, *formatArgs)

class Configuration(val screenWidthDp: Int, val screenHeightDp: Int)

/** Diisi dari ukuran jendela oleh Main.kt (provides). */
val LocalConfiguration = compositionLocalOf { Configuration(1000, 700) }

/** Pengganti coil AsyncImage: unduh gambar (favicon radio) dengan ImageIO; gagal = kosong. */
@Composable
fun AsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val url = model?.toString().orEmpty()
    val bitmap by produceState<ImageBitmap?>(initialValue = null, url) {
        value = if (url.isBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                URL(url).openStream().use { javax.imageio.ImageIO.read(it) }?.toComposeImageBitmap()
            }.getOrNull()
        }
    }
    val img = bitmap
    if (img != null) {
        Image(bitmap = img, contentDescription = contentDescription, modifier = modifier, contentScale = contentScale)
    } else {
        Box(modifier)
    }
}
