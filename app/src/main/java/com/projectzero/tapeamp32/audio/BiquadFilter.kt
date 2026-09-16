package com.projectzero.tapeamp32.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class BiquadType { LOW_SHELF, HIGH_SHELF, PEAKING }

class BiquadFilter {

    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0

    private var w1 = 0.0
    private var w2 = 0.0

    fun reset() {
        w1 = 0.0; w2 = 0.0
    }

    fun configure(type: BiquadType, sampleRate: Double, freqHz: Double, gainDb: Double, q: Double) {
        val f0 = freqHz.coerceIn(10.0, sampleRate / 2.0 - 100.0)
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * f0 / sampleRate
        val cosw0 = cos(w0)
        val sinw0 = sin(w0)
        val qSafe = if (q <= 0.0) 0.707 else q.coerceIn(0.1, 10.0)
        val alpha = sinw0 / (2.0 * qSafe)

        var nb0: Double; var nb1: Double; var nb2: Double; var na1: Double; var na2: Double

        when (type) {
            BiquadType.PEAKING -> {
                val a0 = 1.0 + alpha / a
                nb0 = (1.0 + alpha * a) / a0
                nb1 = (-2.0 * cosw0) / a0
                nb2 = (1.0 - alpha * a) / a0
                na1 = (-2.0 * cosw0) / a0
                na2 = (1.0 - alpha / a) / a0
            }
            BiquadType.LOW_SHELF -> {
                val sq = 2.0 * sqrt(a) * alpha
                val a0 = (a + 1.0) + (a - 1.0) * cosw0 + sq
                nb0 = (a * ((a + 1.0) - (a - 1.0) * cosw0 + sq)) / a0
                nb1 = (2.0 * a * ((a - 1.0) - (a + 1.0) * cosw0)) / a0
                nb2 = (a * ((a + 1.0) - (a - 1.0) * cosw0 - sq)) / a0
                na1 = (-2.0 * ((a - 1.0) + (a + 1.0) * cosw0)) / a0
                na2 = ((a + 1.0) + (a - 1.0) * cosw0 - sq) / a0
            }
            BiquadType.HIGH_SHELF -> {
                val sq = 2.0 * sqrt(a) * alpha
                val a0 = (a + 1.0) - (a - 1.0) * cosw0 + sq
                nb0 = (a * ((a + 1.0) + (a - 1.0) * cosw0 + sq)) / a0
                nb1 = (-2.0 * a * ((a - 1.0) + (a + 1.0) * cosw0)) / a0
                nb2 = (a * ((a + 1.0) + (a - 1.0) * cosw0 - sq)) / a0
                na1 = (2.0 * ((a - 1.0) - (a + 1.0) * cosw0)) / a0
                na2 = ((a + 1.0) - (a - 1.0) * cosw0 - sq) / a0
            }
        }

        if (!nb0.isFinite() || !nb1.isFinite() || !nb2.isFinite() || !na1.isFinite() || !na2.isFinite()) {
            nb0 = 1.0; nb1 = 0.0; nb2 = 0.0; na1 = 0.0; na2 = 0.0
        }

        b0 = nb0; b1 = nb1; b2 = nb2; a1 = na1; a2 = na2
    }

    fun magnitudeAt(freqHz: Double, sampleRate: Double): Double {
        val w = 2.0 * PI * freqHz / sampleRate
        val cosw = cos(w)
        val cos2w = cos(2.0 * w)
        val sinw = sin(w)
        val sin2w = sin(2.0 * w)

        val bRe = b0 + b1 * cosw + b2 * cos2w
        val bIm = -(b1 * sinw + b2 * sin2w)
        val aRe = 1.0 + a1 * cosw + a2 * cos2w
        val aIm = -(a1 * sinw + a2 * sin2w)

        val bMag = sqrt(bRe * bRe + bIm * bIm)
        val aMag = sqrt(aRe * aRe + aIm * aIm)
        return if (aMag < 1e-12) bMag else bMag / aMag
    }

    fun process(x0: Double): Double {
        var y0 = b0 * x0 + w1

        if (kotlin.math.abs(y0) < 1e-15) {
            y0 = 0.0
        }

        w1 = b1 * x0 - a1 * y0 + w2
        w2 = b2 * x0 - a2 * y0

        return y0
    }

    fun processBuffer(samples: DoubleArray) {
        for (i in samples.indices) {
            samples[i] = process(samples[i])
        }
    }
}
