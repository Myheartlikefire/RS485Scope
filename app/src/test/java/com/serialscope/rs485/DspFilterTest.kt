package com.serialscope.rs485

import com.serialscope.rs485.dsp.Biquad
import com.serialscope.rs485.dsp.BiquadSpec
import com.serialscope.rs485.dsp.BiquadType
import com.serialscope.rs485.dsp.FilterChain
import com.serialscope.rs485.dsp.FilterStage
import com.serialscope.rs485.dsp.MovingAverageSpec
import com.serialscope.rs485.dsp.NotchSpec
import com.serialscope.rs485.dsp.SampleFilter
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DspFilterTest {

    private val sampleRate = 1000.0

    /** RMS of a unit amplitude sine, i.e. the pass band reference level. */
    private val sineRms = 0.70710678f

    private fun sine(freq: Double, samples: Int, amplitude: Float = 1f): FloatArray =
        FloatArray(samples) { i ->
            (amplitude * sin(2.0 * PI * freq * i / sampleRate)).toFloat()
        }

    private fun steadyStateRms(filter: SampleFilter, input: FloatArray): Float {
        filter.reset()
        var sum = 0.0
        var count = 0
        for (i in input.indices) {
            val y = filter.process(input[i])
            if (i >= input.size / 2) {
                sum += y.toDouble() * y
                count++
            }
        }
        return sqrt(sum / count).toFloat()
    }

    @Test
    fun `low pass keeps dc and rejects a high tone`() {
        val lowPass = Biquad.design(BiquadType.LOW_PASS, 50.0, sampleRate, 0.7071)!!

        val dc = FloatArray(400) { 1f }
        assertEquals(1f, steadyStateRms(lowPass, dc), 0.02f)

        val highTone = sine(300.0, 2000)
        assertTrue(
            "300 Hz should be attenuated well below the 50 Hz cutoff",
            steadyStateRms(lowPass, highTone) < 0.25f
        )
    }

    @Test
    fun `high pass rejects dc`() {
        val highPass = Biquad.design(BiquadType.HIGH_PASS, 50.0, sampleRate, 0.7071)!!
        val dc = FloatArray(2000) { 1f }
        assertTrue(steadyStateRms(highPass, dc) < 0.05f)
    }

    @Test
    fun `notch removes the mains tone but keeps its neighbours`() {
        val notch = NotchSpec(freqHz = 50f, bandwidthHz = 2f, depth = 1f)
            .create(sampleRate)

        val atNotch = sine(50.0, 4000)
        val nearby = sine(200.0, 4000)

        val rejected = steadyStateRms(notch, atNotch)
        val passed = steadyStateRms(notch, nearby)

        assertTrue("50 Hz should be strongly attenuated, rms=$rejected", rejected < 0.05f)
        assertEquals("200 Hz should pass untouched", sineRms, passed, 0.02f)
    }

    @Test
    fun `band pass isolates the tuned frequency`() {
        val band = Biquad.design(BiquadType.BAND_PASS, 100.0, sampleRate, 5.0)!!
        assertEquals("the tuned frequency must pass at unit gain", sineRms, steadyStateRms(band, sine(100.0, 4000)), 0.05f)
        assertTrue(steadyStateRms(band, sine(400.0, 4000)) < 0.1f)
    }

    @Test
    fun `chain warmup makes the result independent of history`() {
        val stages = listOf(FilterStage(1, true, BiquadSpec(BiquadType.LOW_PASS, 40f, 0.7071f)))
        val chain = FilterChain.of(stages, sampleRate)

        val signal = sine(10.0, 3000)
        val out = FloatArray(1000)
        val produced = chain.applyStream(signal, 0, signal.size, 500, out)

        assertEquals(1000, produced)
        // A 10 Hz tone through a 40 Hz low pass must come out at roughly unit amplitude.
        val rms = sqrt(out.sumOf { it.toDouble() * it } / out.size).toFloat()
        assertEquals("warmup must remove the start up transient", sineRms, rms, 0.05f)
    }

    @Test
    fun `empty chain copies the requested tail`() {
        val signal = FloatArray(50) { it.toFloat() }
        val out = FloatArray(10)
        assertEquals(10, FilterChain.EMPTY.applyStream(signal, 0, signal.size, 0, out))
        assertEquals(40f, out[0], 0f)
        assertEquals(49f, out[9], 0f)
    }

    @Test
    fun `moving average smooths an impulse`() {
        val filter = MovingAverageSpec(5).create(sampleRate)
        val output = FloatArray(6) { i ->
            filter.process(if (i == 0) 5f else 0f)
        }
        assertEquals(1f, output[0], 1e-5f)
        assertEquals(0f, output[5], 1e-5f)
    }

    @Test
    fun `filter design clamps frequencies above nyquist`() {
        // 10 kHz at 1 kHz sample rate must not produce NaN coefficients.
        val filter = Biquad.design(BiquadType.LOW_PASS, 10_000.0, sampleRate, 0.7071)!!
        val y = filter.process(1f)
        assertTrue(!y.isNaN() && !y.isInfinite())
    }
}