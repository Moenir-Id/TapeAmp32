package com.projectzero.tapeamp32.desktop.shim

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * "Ketik langsung untuk mencari" di Library desktop.
 *
 * Main.kt menaruh huruf yang diketik (saat tidak ada kolom teks yang fokus) ke [pending].
 * Kolom pencarian Library (lihat aturan rewrite di build.gradle.kts) mengambilnya, menambahkan
 * huruf itu ke query, lalu mengambil fokus supaya ketikan berikutnya langsung masuk ke kolom.
 * Esc atau Enter = keluar dari kolom pencarian (fokus dilepas, Spasi kembali jadi play/pause).
 */
object TypeToSearch {
    class Pending(val text: String, val at: Long)

    val pending = mutableStateOf<Pending?>(null)
}

class TypeToSearchState(
    val value: TextFieldValue,
    val onValueChange: (TextFieldValue) -> Unit,
    val modifier: Modifier,
)

@Composable
fun rememberTypeToSearch(query: String, onQueryChange: (String) -> Unit): TypeToSearchState {
    var tfv by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
    // Query bisa berubah dari luar (mis. dikosongkan saat pindah tab): ikuti, kursor di akhir.
    val shown = if (tfv.text == query) tfv else TextFieldValue(query, TextRange(query.length))

    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val currentQuery by rememberUpdatedState(query)
    val currentCallback by rememberUpdatedState(onQueryChange)

    val p = TypeToSearch.pending.value
    LaunchedEffect(p) {
        if (p != null) {
            TypeToSearch.pending.value = null
            // Abaikan huruf lama yang tidak sempat diambil (toolbar belum tampil).
            if (System.currentTimeMillis() - p.at < 1500) {
                val t = currentQuery + p.text
                tfv = TextFieldValue(t, TextRange(t.length))
                currentCallback(t)
                runCatching { focus.requestFocus() }
            }
        }
    }

    val mod = Modifier
        .focusRequester(focus)
        .onPreviewKeyEvent { e ->
            if (e.type == KeyEventType.KeyDown && (e.key == Key.Escape || e.key == Key.Enter || e.key == Key.NumPadEnter)) {
                focusManager.clearFocus()
                true
            } else false
        }

    return TypeToSearchState(
        value = shown,
        onValueChange = { v ->
            val textChanged = v.text != shown.text
            tfv = v
            if (textChanged) currentCallback(v.text)
        },
        modifier = mod,
    )
}
