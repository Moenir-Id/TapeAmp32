package com.projectzero.tapeamp32.audio

import androidx.lifecycle.DefaultLifecycleObserver
import com.projectzero.tapeamp32.desktop.shim.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Versi desktop: API sama dengan UsbDacObserver Android, tapi belum mendeteksi DAC USB
 * (selalu "tidak terhubung"). Pendeteksian lewat javax.sound mixer = pekerjaan lanjutan.
 */
class UsbDacObserver(@Suppress("UNUSED_PARAMETER") context: Context) : DefaultLifecycleObserver {

    private val _isUsbDacConnected = MutableStateFlow(false)
    val isUsbDacConnected: StateFlow<Boolean> = _isUsbDacConnected.asStateFlow()

    private val _connectedDacName = MutableStateFlow<String?>(null)
    val connectedDacName: StateFlow<String?> = _connectedDacName.asStateFlow()

    fun register() {}
    fun unregister() {}
}
