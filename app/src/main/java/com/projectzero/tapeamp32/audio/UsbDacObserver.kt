package com.projectzero.tapeamp32.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UsbDacObserver(context: Context) : DefaultLifecycleObserver {

    private val appContext = context.applicationContext

    private val audioManager: AudioManager? =
        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val mainHandler = Handler(Looper.getMainLooper())

    private var isRegistered = false

    private val externalDacTypes = setOf(
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_ACCESSORY
    )

    private val _isUsbDacConnected = MutableStateFlow(false)
    val isUsbDacConnected: StateFlow<Boolean> = _isUsbDacConnected.asStateFlow()

    private val _connectedDacName = MutableStateFlow<String?>(null)
    val connectedDacName: StateFlow<String?> = _connectedDacName.asStateFlow()

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            refreshState()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            refreshState()
        }
    }

    private fun refreshState() {
        val manager = audioManager ?: return
        val outputs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        val dac = outputs.firstOrNull { it.type in externalDacTypes }

        _isUsbDacConnected.value = dac != null
        _connectedDacName.value = dac?.productName?.toString()?.takeIf { it.isNotBlank() }
    }

    fun register() {
        val manager = audioManager ?: return
        if (isRegistered) return

        refreshState()

        manager.registerAudioDeviceCallback(deviceCallback, mainHandler)
        isRegistered = true
    }

    fun unregister() {
        val manager = audioManager ?: return
        if (!isRegistered) return

        manager.unregisterAudioDeviceCallback(deviceCallback)
        isRegistered = false
    }

    override fun onStart(owner: LifecycleOwner) {
        register()
    }

    override fun onStop(owner: LifecycleOwner) {
        unregister()
    }
}
