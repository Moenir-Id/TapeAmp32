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

/**
 * Mengamati output audio HP secara real-time lewat AudioManager.registerAudioDeviceCallback
 * untuk mendeteksi kapan sebuah USB DAC / USB DAC-amp / USB headset eksternal
 * dicolok atau dicabut, dan mengeksposnya sebagai StateFlow agar UI (Status Panel VFD)
 * bisa langsung bereaksi tanpa polling.
 *
 * LIFECYCLE AWARENESS:
 * Class ini mengimplementasikan DefaultLifecycleObserver, jadi cara paling aman untuk
 * memakainya di Compose adalah menempelkannya ke LocalLifecycleOwner lewat DisposableEffect:
 *
 *   val lifecycleOwner = LocalLifecycleOwner.current
 *   DisposableEffect(lifecycleOwner) {
 *       lifecycleOwner.lifecycle.addObserver(usbDacObserver)
 *       onDispose { lifecycleOwner.lifecycle.removeObserver(usbDacObserver) }
 *   }
 *
 * register() dipanggil otomatis saat layar ON (onStart) dan unregister() otomatis saat
 * layar background (onStop), sehingga callback TIDAK PERNAH menggantung/leak walau
 * Activity di-recreate (rotasi, dsb). register()/unregister() juga aman dipanggil manual
 * berkali-kali (idempotent) kalau class ini mau dipakai di luar LifecycleOwner.
 */
class UsbDacObserver(context: Context) : DefaultLifecycleObserver {

    private val appContext = context.applicationContext

    private val audioManager: AudioManager? =
        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val mainHandler = Handler(Looper.getMainLooper())

    private var isRegistered = false

    // Tipe perangkat yang dianggap "DAC eksternal" untuk keperluan status panel ini.
    private val externalDacTypes = setOf(
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_ACCESSORY
    )

    private val _isUsbDacConnected = MutableStateFlow(false)
    val isUsbDacConnected: StateFlow<Boolean> = _isUsbDacConnected.asStateFlow()

    // Nama produk DAC yang sedang aktif (mis. "USB-C to 3.5mm DAC"), null kalau tidak ada.
    // Dipakai Status Panel VFD untuk menampilkan label yang lebih "hidup" daripada teks statis.
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

    /** Mendaftarkan callback ke AudioManager. Aman dipanggil berkali-kali. */
    fun register() {
        val manager = audioManager ?: return
        if (isRegistered) return

        // Ambil status awal duluan supaya panel tidak menunggu event pertama sebelum update.
        refreshState()

        manager.registerAudioDeviceCallback(deviceCallback, mainHandler)
        isRegistered = true
    }

    /** Melepas callback dari AudioManager. Aman dipanggil berkali-kali / tanpa register(). */
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
