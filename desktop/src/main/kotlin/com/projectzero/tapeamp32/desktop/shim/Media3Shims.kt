// SHIM Media3: hanya bagian kecil yang dipakai ParametricEqAudioProcessor & PlayerViewModel.
// Perilaku BaseAudioProcessor mengikuti androidx.media3.common.audio.BaseAudioProcessor (1.4.0).
// Dipakai lewat penulisan ulang impor saat `syncShared` (lihat build.gradle.kts).
package com.projectzero.tapeamp32.desktop.shim

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Penanda "API belum stabil" Media3; di sini level WARNING supaya @OptIn(UnstableApi::class) valid. */
@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
@Retention(AnnotationRetention.BINARY)
annotation class UnstableApi

object C {
    const val ENCODING_PCM_16BIT: Int = 2
    const val ENCODING_PCM_FLOAT: Int = 4
    const val TIME_UNSET: Long = Long.MIN_VALUE + 1
}

object Player {
    const val REPEAT_MODE_OFF: Int = 0
    const val REPEAT_MODE_ONE: Int = 1
    const val REPEAT_MODE_ALL: Int = 2

    const val STATE_IDLE: Int = 1
    const val STATE_BUFFERING: Int = 2
    const val STATE_READY: Int = 3
    const val STATE_ENDED: Int = 4
}

interface AudioProcessor {

    class AudioFormat(
        @JvmField val sampleRate: Int,
        @JvmField val channelCount: Int,
        @JvmField val encoding: Int
    ) {
        companion object {
            @JvmField
            val NOT_SET = AudioFormat(-1, -1, -1)
        }

        override fun equals(other: Any?): Boolean =
            other is AudioFormat && sampleRate == other.sampleRate &&
                channelCount == other.channelCount && encoding == other.encoding

        override fun hashCode(): Int = (sampleRate * 31 + channelCount) * 31 + encoding

        override fun toString(): String =
            "AudioFormat[sampleRate=$sampleRate, channelCount=$channelCount, encoding=$encoding]"
    }

    class UnhandledAudioFormatException(inputAudioFormat: AudioFormat) :
        Exception("Unhandled input format: $inputAudioFormat")

    @Throws(UnhandledAudioFormatException::class)
    fun configure(inputAudioFormat: AudioFormat): AudioFormat

    fun isActive(): Boolean
    fun queueInput(inputBuffer: ByteBuffer)
    fun queueEndOfStream()
    fun getOutput(): ByteBuffer
    fun isEnded(): Boolean
    fun flush()
    fun reset()
}

abstract class BaseAudioProcessor : AudioProcessor {

    protected var inputAudioFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
        private set
    protected var outputAudioFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
        private set

    private var pendingInputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var pendingOutputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var buffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        pendingInputAudioFormat = inputAudioFormat
        pendingOutputAudioFormat = onConfigure(inputAudioFormat)
        return if (isActive()) pendingOutputAudioFormat else AudioProcessor.AudioFormat.NOT_SET
    }

    override fun isActive(): Boolean = pendingOutputAudioFormat != AudioProcessor.AudioFormat.NOT_SET

    override fun queueEndOfStream() {
        inputEnded = true
        onQueueEndOfStream()
    }

    override fun getOutput(): ByteBuffer {
        val result = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return result
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === EMPTY_BUFFER

    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        inputAudioFormat = pendingInputAudioFormat
        outputAudioFormat = pendingOutputAudioFormat
        onFlush()
    }

    override fun reset() {
        flush()
        buffer = EMPTY_BUFFER
        pendingInputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        pendingOutputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        onReset()
    }

    /** Sama seperti Media3: siapkan buffer output (cukup besar), kosongkan, jadikan output saat ini. */
    protected fun replaceOutputBuffer(count: Int): ByteBuffer {
        if (buffer.capacity() < count) {
            buffer = ByteBuffer.allocateDirect(count).order(ByteOrder.nativeOrder())
        } else {
            buffer.clear()
        }
        outputBuffer = buffer
        return buffer
    }

    protected fun hasPendingOutput(): Boolean = outputBuffer.hasRemaining()

    protected open fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        AudioProcessor.AudioFormat.NOT_SET

    protected open fun onQueueEndOfStream() {}
    protected open fun onFlush() {}
    protected open fun onReset() {}

    private companion object {
        val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
