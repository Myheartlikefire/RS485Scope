package com.serialscope.rs485.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/** Iterative radix-2 Cooley-Tukey FFT plus helpers for the spectrum view. */
object Fft {

    /** Largest power of two that is <= [n] and >= 2. */
    fun floorPow2(n: Int): Int {
        if (n < 2) return 2
        var p = 1
        while (p shl 1 <= n) p = p shl 1
        return p
    }

    fun hannWindow(size: Int): FloatArray {
        val w = FloatArray(size)
        if (size <= 1) {
            if (size == 1) w[0] = 1f
            return w
        }
        for (i in 0 until size) {
            w[i] = (0.5 - 0.5 * cos(2.0 * PI * i / (size - 1))).toFloat()
        }
        return w
    }

    /** In-place complex FFT. [re] and [im] must have the same power-of-two length. */
    fun transform(re: FloatArray, im: FloatArray) {
        val n = re.size
        if (n < 2) return

        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wr = cos(angle)
            val wi = sin(angle)
            val half = len shr 1
            var i = 0
            while (i < n) {
                var curR = 1.0
                var curI = 0.0
                for (k in 0 until half) {
                    val a = i + k
                    val b = a + half
                    val br = re[b].toDouble()
                    val bi = im[b].toDouble()
                    val xr = br * curR - bi * curI
                    val xi = br * curI + bi * curR
                    val ar = re[a].toDouble()
                    val ai = im[a].toDouble()
                    re[b] = (ar - xr).toFloat()
                    im[b] = (ai - xi).toFloat()
                    re[a] = (ar + xr).toFloat()
                    im[a] = (ai + xi).toFloat()
                    val nr = curR * wr - curI * wi
                    curI = curR * wi + curI * wr
                    curR = nr
                }
                i += len
            }
            len = len shl 1
        }
    }

    /**
     * Magnitude spectrum in dB of `input[offset until offset+length]`.
     * Writes `n/2 + 1` bins into [outDb] and returns that bin count.
     */
    fun spectrumDb(
        input: FloatArray,
        offset: Int,
        length: Int,
        outDb: FloatArray,
        window: FloatArray? = null
    ): Int {
        val n = floorPow2(length)
        if (n < 2) return 0
        val re = FloatArray(n)
        val im = FloatArray(n)
        val start = offset + (length - n).coerceAtLeast(0)
        for (i in 0 until n) {
            val v = if (start + i < input.size) input[start + i] else 0f
            re[i] = if (window != null && i < window.size) v * window[i] else v
        }
        transform(re, im)

        val bins = n / 2 + 1
        val scale = 2.0 / n
        for (k in 0 until bins) {
            val mag = sqrt(re[k].toDouble() * re[k] + im[k].toDouble() * im[k]) * scale
            val db = 20.0 * log10(if (mag > 1e-9) mag else 1e-9)
            outDb[k] = db.toFloat().coerceAtLeast(-140f)
        }
        return bins
    }
}