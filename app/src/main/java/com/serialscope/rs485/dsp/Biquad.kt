package com.serialscope.rs485.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class BiquadType(val label: String) {
    LOW_PASS("低通"),
    HIGH_PASS("高通"),
    BAND_PASS("带通"),
    NOTCH("陷波"),
    PEAKING("峰值EQ"),
    LOW_SHELF("低架增益"),
    HIGH_SHELF("高架增益"),
    ALL_PASS("全通移相")
}

/**
 * Second order IIR section, coefficients designed with the Audio EQ Cookbook
 * (Robert Bristow-Johnson) and processed in transposed direct form II.
 */
class Biquad private constructor(
    private val b0: Float,
    private val b1: Float,
    private val b2: Float,
    private val a1: Float,
    private val a2: Float
) : SampleFilter {

    private var s1 = 0f
    private var s2 = 0f

    override fun reset() {
        s1 = 0f
        s2 = 0f
    }

    override fun process(x: Float): Float {
        val y = b0 * x + s1
        s1 = b1 * x - a1 * y + s2
        s2 = b2 * x - a2 * y
        return y
    }

    companion object {

        fun design(
            type: BiquadType,
            freqHz: Double,
            sampleRate: Double,
            q: Double = 0.7071,
            gainDb: Double = 0.0
        ): Biquad? {
            if (sampleRate <= 0.0) return null
            val nyquist = sampleRate / 2.0
            val f0 = freqHz.coerceIn(sampleRate / 100000.0, nyquist * 0.999)
            val qq = q.coerceIn(0.05, 30.0)
            val a = 10.0.pow(gainDb / 40.0)

            val w0 = 2.0 * PI * f0 / sampleRate
            val cosw = cos(w0)
            val sinw = sin(w0)
            val alpha = sinw / (2.0 * qq)
            val shelfAlpha = sinw / 2.0 * sqrt((a + 1.0 / a) * (1.0 / 1.0 - 1.0) + 2.0)
            val twoSqrtAAlpha = 2.0 * sqrt(a) * shelfAlpha

            val b0: Double
            val b1: Double
            val b2: Double
            val a0: Double
            val a1: Double
            val a2: Double

            when (type) {
                BiquadType.LOW_PASS -> {
                    b0 = (1.0 - cosw) / 2.0; b1 = 1.0 - cosw; b2 = (1.0 - cosw) / 2.0
                    a0 = 1.0 + alpha; a1 = -2.0 * cosw; a2 = 1.0 - alpha
                }
                BiquadType.HIGH_PASS -> {
                    b0 = (1.0 + cosw) / 2.0; b1 = -(1.0 + cosw); b2 = (1.0 + cosw) / 2.0
                    a0 = 1.0 + alpha; a1 = -2.0 * cosw; a2 = 1.0 - alpha
                }
                BiquadType.BAND_PASS -> {
                    b0 = alpha; b1 = 0.0; b2 = -alpha
                    a0 = 1.0 + alpha; a1 = -2.0 * cosw; a2 = 1.0 - alpha
                }
                BiquadType.NOTCH -> {
                    b0 = 1.0; b1 = -2.0 * cosw; b2 = 1.0
                    a0 = 1.0 + alpha; a1 = -2.0 * cosw; a2 = 1.0 - alpha
                }
                BiquadType.ALL_PASS -> {
                    b0 = 1.0 - alpha; b1 = -2.0 * cosw; b2 = 1.0 + alpha
                    a0 = 1.0 + alpha; a1 = -2.0 * cosw; a2 = 1.0 - alpha
                }
                BiquadType.PEAKING -> {
                    b0 = 1.0 + alpha * a; b1 = -2.0 * cosw; b2 = 1.0 - alpha * a
                    a0 = 1.0 + alpha / a; a1 = -2.0 * cosw; a2 = 1.0 - alpha / a
                }
                BiquadType.LOW_SHELF -> {
                    b0 = a * ((a + 1.0) - (a - 1.0) * cosw + twoSqrtAAlpha)
                    b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosw)
                    b2 = a * ((a + 1.0) - (a - 1.0) * cosw - twoSqrtAAlpha)
                    a0 = (a + 1.0) + (a - 1.0) * cosw + twoSqrtAAlpha
                    a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosw)
                    a2 = (a + 1.0) + (a - 1.0) * cosw - twoSqrtAAlpha
                }
                BiquadType.HIGH_SHELF -> {
                    b0 = a * ((a + 1.0) + (a - 1.0) * cosw + twoSqrtAAlpha)
                    b1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosw)
                    b2 = a * ((a + 1.0) + (a - 1.0) * cosw - twoSqrtAAlpha)
                    a0 = (a + 1.0) - (a - 1.0) * cosw + twoSqrtAAlpha
                    a1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosw)
                    a2 = (a + 1.0) - (a - 1.0) * cosw - twoSqrtAAlpha
                }
            }

            if (a0 == 0.0) return null
            return Biquad(
                b0 = (b0 / a0).toFloat(),
                b1 = (b1 / a0).toFloat(),
                b2 = (b2 / a0).toFloat(),
                a1 = (a1 / a0).toFloat(),
                a2 = (a2 / a0).toFloat()
            )
        }
    }
}