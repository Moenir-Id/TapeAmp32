package com.projectzero.tapeamp32.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.projectzero.tapeamp32.data.EqPreset
import com.projectzero.tapeamp32.data.flatTenBandPreset
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

@UnstableApi
class ParametricEqAudioProcessor : BaseAudioProcessor() {

    companion object {

        private const val REPLAY_GAIN_TARGET_RMS_DB = -18.0
    }

    @Volatile var bypass: Boolean = false
    @Volatile var limiterEnabled: Boolean = true
    @Volatile var vuCallback: ((left: Float, right: Float) -> Unit)? = null

    @Volatile var bitPerfectMode: Boolean = false

    @Volatile var replayGainEnabled: Boolean = false
    @Volatile private var replayGainLinear: Double = 1.0

    fun setReplayGainDb(db: Double) {
        replayGainLinear = 10.0.pow(db.coerceIn(-12.0, 12.0) / 20.0)
    }

    @Volatile var measuringReplayGain: Boolean = false
        private set
    private var rgSumSquares: Double = 0.0
    private var rgSampleCount: Long = 0L
    private var rgPeak: Double = 0.0

    fun beginReplayGainMeasurement() {
        rgSumSquares = 0.0
        rgSampleCount = 0L
        rgPeak = 0.0
        measuringReplayGain = true
    }

    fun cancelReplayGainMeasurement() {
        measuringReplayGain = false
    }

    fun finishReplayGainMeasurement(): Double? {
        measuringReplayGain = false
        if (rgSampleCount <= 0L) return null
        val meanSquare = rgSumSquares / rgSampleCount
        if (meanSquare <= 0.0) return null
        val rmsDb = 10.0 * kotlin.math.log10(meanSquare)
        var gain = REPLAY_GAIN_TARGET_RMS_DB - rmsDb
        if (rgPeak > 0.0) {
            val peakDb = 20.0 * kotlin.math.log10(rgPeak)
            val maxGainBeforeClip = -peakDb
            if (gain > maxGainBeforeClip) gain = maxGainBeforeClip
        }
        return gain.coerceIn(-12.0, 12.0)
    }

    @Volatile var vocalEnabled: Boolean = false
        private set
    @Volatile var vocalBassDb: Double = 0.0
        private set
    @Volatile var vocalTrebleDb: Double = 0.0
        private set

    @Volatile var balance: Double = 0.0
        private set
    @Volatile var stereoExpansion: Double = 1.0
        private set
    @Volatile var monoStereoOn: Boolean = false
        private set

    @Volatile var dspStateCallback: ((dspActive: Boolean, peakActive: Boolean) -> Unit)? = null
    private var peakHitThisWindow = false

    private var sampleRate = 44100
    private var channelCount = 2

    private var outputEncoding = C.ENCODING_PCM_16BIT

    @Volatile private var filters: Array<Array<BiquadFilter>> = arrayOf()
    private var currentPreset: EqPreset = flatTenBandPreset()
    @Volatile private var autoPreampLinear = 1.0

    private var accumPeakL = 0f
    private var accumPeakR = 0f
    private var samplesSinceVuEmit = 0
    private val vuEmitInterval = 512

    fun setPreset(preset: EqPreset) {
        currentPreset = preset
        rebuildFilters()
    }

    fun setVocalEnabled(enabled: Boolean) {
        vocalEnabled = enabled
    }

    fun setVocalBass(db: Double) {
        vocalBassDb = db.coerceIn(-12.0, 12.0)
        rebuildVocalFilters()
    }

    fun setVocalTreble(db: Double) {
        vocalTrebleDb = db.coerceIn(-12.0, 12.0)
        rebuildVocalFilters()
    }

    fun setBalance(value: Double) {
        balance = value.coerceIn(-1.0, 1.0)
    }

    fun setStereoExpansion(value: Double) {
        stereoExpansion = value.coerceIn(0.0, 2.0)
    }

    fun setMonoStereo(monoOn: Boolean) {
        monoStereoOn = monoOn
    }

    private val VOCAL_BASS_HZ = 200.0
    private val VOCAL_TREBLE_HZ = 4500.0
    private val VOCAL_Q = 0.9

    @Volatile private var vocalFilters: Array<Array<BiquadFilter>> = arrayOf()

    @Synchronized
    private fun rebuildVocalFilters() {
        if (channelCount <= 0) return
        val newVocalFilters = Array(channelCount) { Array(2) { BiquadFilter() } }
        for (ch in 0 until channelCount) {
            newVocalFilters[ch][0].configure(
                BiquadType.PEAKING, sampleRate.toDouble(), VOCAL_BASS_HZ, vocalBassDb, VOCAL_Q
            )
            newVocalFilters[ch][1].configure(
                BiquadType.PEAKING, sampleRate.toDouble(), VOCAL_TREBLE_HZ, vocalTrebleDb, VOCAL_Q
            )
        }
        vocalFilters = newVocalFilters
    }

    @Synchronized
    private fun rebuildFilters() {
        if (channelCount <= 0) return
        val newFilters = Array(channelCount) { Array(currentPreset.bands.size) { BiquadFilter() } }
        for (ch in 0 until channelCount) {
            currentPreset.bands.forEachIndexed { i, band ->
                val type = when (band.type) {
                    0 -> BiquadType.LOW_SHELF
                    1 -> BiquadType.HIGH_SHELF
                    else -> BiquadType.PEAKING
                }
                val q = if (band.q <= 0.0) 0.707 else band.q
                newFilters[ch][i].configure(type, sampleRate.toDouble(), band.frequency, band.gain, q)
            }
        }

        var peakGain = 1.0
        if (newFilters.isNotEmpty()) {
            val cascade = newFilters[0]
            var f = 20.0
            while (f <= 20000.0) {
                var combined = 1.0
                for (bq in cascade) combined *= bq.magnitudeAt(f, sampleRate.toDouble())
                if (combined > peakGain) peakGain = combined
                f *= 1.03
            }
        }

        val safetyRatio = headroomSafetyRatio
        val peakGainDb = 20.0 * kotlin.math.log10(peakGain)
        val compensatedDb = peakGainDb * safetyRatio
        val headroomCompensation = 10.0.pow(-compensatedDb / 20.0)
        autoPreampLinear = (10.0.pow(currentPreset.preamp / 20.0) * headroomCompensation).coerceAtMost(1.0)

        filters = newFilters
    }

    private val limiterThresholdDb = -0.5
    private val limiterThreshold = 10.0.pow(limiterThresholdDb / 20.0)
    private val limiterAttackSeconds = 0.003
    private val limiterReleaseSeconds = 0.120
    private val limiterHoldSeconds = 0.010
    private val limiterLookaheadSeconds = 0.003
    private var limiterAttackCoeff = 0.0
    private var limiterReleaseCoeff = 0.0
    private var limiterHoldSamples = 0

    private var limiterEnvelope = 0.0
    private var limiterHoldCounter = 0

    private var limiterLookaheadSamples = 0
    @Volatile private var delayBuffers: Array<DoubleArray> = arrayOf()
    private var delayIndex = 0

    @Volatile var headroomSafetyRatio: Double = 0.3
        private set

    fun setHeadroomSafetyRatio(value: Double) {
        headroomSafetyRatio = value.coerceIn(0.0, 1.0)
        rebuildFilters()
    }

    private fun recalculateLimiterCoefficients() {
        val sr = sampleRate.toDouble().coerceAtLeast(1.0)
        limiterAttackCoeff = exp(-1.0 / (limiterAttackSeconds * sr))
        limiterReleaseCoeff = exp(-1.0 / (limiterReleaseSeconds * sr))
        limiterHoldSamples = (limiterHoldSeconds * sr).toInt().coerceAtLeast(0)

        val newLookaheadSamples = (limiterLookaheadSeconds * sr).toInt().coerceAtLeast(1)
        val chCount = channelCount.coerceIn(1, frameScratch.size)
        limiterLookaheadSamples = newLookaheadSamples

        delayBuffers = Array(chCount) { DoubleArray(newLookaheadSamples) }
        delayIndex = 0
    }

    private val frameScratch = DoubleArray(8)

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        outputEncoding = inputAudioFormat.encoding
        rebuildFilters()
        rebuildVocalFilters()
        recalculateLimiterCoefficients()
        return AudioFormat(inputAudioFormat.sampleRate, inputAudioFormat.channelCount, outputEncoding)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val inputEncoding = inputAudioFormat.encoding
        val frameCount = if (inputEncoding == C.ENCODING_PCM_FLOAT) {
            inputBuffer.remaining() / 4 / channelCount
        } else {
            inputBuffer.remaining() / 2 / channelCount
        }
        if (frameCount <= 0) return

        val outputBytesPerSample = if (outputEncoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val outputBuffer = replaceOutputBuffer(frameCount * outputBytesPerSample * channelCount)
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        val localFilters = filters
        val activeChannels = channelCount.coerceAtMost(frameScratch.size)
        val scratch = frameScratch
        val threshold = limiterThreshold
        val attackC = limiterAttackCoeff
        val releaseC = limiterReleaseCoeff
        val holdSamples = limiterHoldSamples
        var envelope = limiterEnvelope
        var holdCounter = limiterHoldCounter

        val localDelayBuffers = delayBuffers
        val lookaheadSize = limiterLookaheadSamples
        var delayIdx = delayIndex

        for (frame in 0 until frameCount) {

            val localVocalFilters = vocalFilters
            for (ch in 0 until activeChannels) {
                var sample: Double = if (inputEncoding == C.ENCODING_PCM_FLOAT) {
                    inputBuffer.float.toDouble()
                } else {
                    (inputBuffer.short / 32768.0)
                }

                if (measuringReplayGain) {
                    rgSumSquares += sample * sample
                    rgSampleCount++
                    val mag = abs(sample)
                    if (mag > rgPeak) rgPeak = mag
                }

                if (replayGainEnabled && !bitPerfectMode) {
                    sample *= replayGainLinear
                }

                if (!bitPerfectMode && !bypass && localFilters.size > ch) {
                    val chFilters = localFilters[ch]
                    for (band in chFilters) {
                        sample = band.process(sample)
                    }
                    sample *= autoPreampLinear

                    if (vocalEnabled && localVocalFilters.size > ch) {
                        sample = localVocalFilters[ch][0].process(sample)
                        sample = localVocalFilters[ch][1].process(sample)
                    }
                }

                scratch[ch] = sample
            }

            if (activeChannels >= 2 && !bitPerfectMode) {
                var l = scratch[0]
                var r = scratch[1]

                if (monoStereoOn) {

                    val mid = (l + r) * 0.5
                    l = mid
                    r = mid
                } else if (stereoExpansion != 1.0) {

                    val mid = (l + r) * 0.5
                    val side = (l - r) * 0.5 * stereoExpansion
                    l = mid + side
                    r = mid - side
                }

                if (balance != 0.0) {

                    if (balance > 0.0) l *= (1.0 - balance) else r *= (1.0 + balance)
                }

                scratch[0] = l
                scratch[1] = r
            }

            var framePeak = 0.0
            for (ch in 0 until activeChannels) {
                val mag = abs(scratch[ch])
                if (mag > framePeak) framePeak = mag
            }

            var frameGain = 1.0
            if (limiterEnabled && !bitPerfectMode) {
                val coeff: Double
                if (framePeak > envelope) {
                    coeff = attackC
                    holdCounter = holdSamples
                } else if (holdCounter > 0) {
                    coeff = 1.0
                    holdCounter--
                } else {
                    coeff = releaseC
                }
                envelope = coeff * envelope + (1.0 - coeff) * framePeak
                if (envelope > threshold) {
                    peakHitThisWindow = true
                    frameGain = threshold / envelope
                }
            }

            for (ch in 0 until activeChannels) {
                val delayed: Double
                if (limiterEnabled && !bitPerfectMode && localDelayBuffers.size > ch &&
                    localDelayBuffers[ch].size == lookaheadSize
                ) {
                    val buf = localDelayBuffers[ch]
                    delayed = buf[delayIdx]
                    buf[delayIdx] = scratch[ch]
                } else {

                    delayed = scratch[ch]
                }
                val outSample = delayed * frameGain
                val f = outSample.toFloat().coerceIn(-1f, 1f)
                if (outputEncoding == C.ENCODING_PCM_FLOAT) {
                    outputBuffer.putFloat(f)
                } else {
                    val s16 = (f * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
                    outputBuffer.putShort(s16)
                }

                val mag = abs(f)
                if (activeChannels == 1) {
                    if (mag > accumPeakL) accumPeakL = mag
                    if (mag > accumPeakR) accumPeakR = mag
                } else if (ch == 0) {
                    if (mag > accumPeakL) accumPeakL = mag
                } else if (ch == 1) {
                    if (mag > accumPeakR) accumPeakR = mag
                }
            }

            if (lookaheadSize > 0) {
                delayIdx++
                if (delayIdx >= lookaheadSize) delayIdx = 0
            }
        }

        limiterEnvelope = envelope
        limiterHoldCounter = holdCounter
        delayIndex = delayIdx

        samplesSinceVuEmit += frameCount
        if (samplesSinceVuEmit >= vuEmitInterval) {
            vuCallback?.invoke(accumPeakL, accumPeakR)
            dspStateCallback?.invoke(!bitPerfectMode && !bypass, peakHitThisWindow)
            accumPeakL = 0f
            accumPeakR = 0f
            peakHitThisWindow = false
            samplesSinceVuEmit = 0
        }

        outputBuffer.flip()
    }

    override fun onFlush() {
        filters.forEach { chFilters -> chFilters.forEach { it.reset() } }
        vocalFilters.forEach { chFilters -> chFilters.forEach { it.reset() } }
        limiterEnvelope = 0.0

        limiterHoldCounter = 0
        delayBuffers.forEach { it.fill(0.0) }
        delayIndex = 0
        accumPeakL = 0f
        accumPeakR = 0f
        peakHitThisWindow = false
        samplesSinceVuEmit = 0
    }

    override fun onReset() {
        filters = arrayOf()
        vocalFilters = arrayOf()
        limiterEnvelope = 0.0
        limiterHoldCounter = 0
        delayBuffers = arrayOf()
        delayIndex = 0
        accumPeakL = 0f
        accumPeakR = 0f
        peakHitThisWindow = false
        samplesSinceVuEmit = 0
    }
}
