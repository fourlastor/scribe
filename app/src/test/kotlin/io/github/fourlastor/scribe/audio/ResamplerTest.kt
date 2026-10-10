package io.github.fourlastor.scribe.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResamplerTest {

    @Test
    fun `downsampling keeps a tone below the new Nyquist frequency`() {
        val output = resample(sine(1_000.0, 48_000, seconds = 1.0), 48_000)

        assertEquals(16_000, output.size)
        assertEquals(RMS_OF_UNIT_SINE, rms(output.steadyState()), 0.01)
        assertEquals(1_000.0, frequency(output.steadyState(), 16_000), 5.0)
    }

    @Test
    fun `downsampling removes a tone above the new Nyquist frequency`() {
        val output = resample(sine(10_000.0, 48_000, seconds = 1.0), 48_000)

        assertTrue("rms was ${rms(output.steadyState())}", rms(output.steadyState()) < 0.005)
    }

    @Test
    fun `non integer ratios keep pitch and level`() {
        val output = resample(sine(440.0, 44_100, seconds = 1.0), 44_100)

        assertEquals(16_000.0, output.size.toDouble(), 1.0)
        assertEquals(RMS_OF_UNIT_SINE, rms(output.steadyState()), 0.01)
        assertEquals(440.0, frequency(output.steadyState(), 16_000), 5.0)
    }

    @Test
    fun `upsampling keeps pitch and level`() {
        val output = resample(sine(1_000.0, 8_000, seconds = 1.0), 8_000)

        assertEquals(16_000.0, output.size.toDouble(), 1.0)
        assertEquals(RMS_OF_UNIT_SINE, rms(output.steadyState()), 0.01)
        assertEquals(1_000.0, frequency(output.steadyState(), 16_000), 5.0)
    }

    @Test
    fun `output does not depend on how the input is split into blocks`() {
        val input = sine(700.0, 44_100, seconds = 0.5)
        val whole = resample(input, 44_100)

        val resampler = Resampler(44_100, 16_000)
        val pieces = ArrayList<Float>()
        var offset = 0
        var block = 1
        while (offset < input.size) {
            val length = minOf(block, input.size - offset)
            pieces += resampler.process(input.copyOfRange(offset, offset + length)).asList()
            offset += length
            block = block * 3 % 1_000 + 1
        }
        pieces += resampler.flush().asList()

        assertArrayEquals(whole, pieces.toFloatArray(), 0f)
    }

    @Test
    fun `audio already at the target rate is passed through`() {
        val input = sine(300.0, 16_000, seconds = 0.1)

        assertArrayEquals(input, resample(input, 16_000), 0f)
    }

    private fun resample(input: FloatArray, rate: Int): FloatArray {
        val resampler = Resampler(rate, 16_000)
        return resampler.process(input) + resampler.flush()
    }

    private fun sine(frequency: Double, rate: Int, seconds: Double) =
        FloatArray((rate * seconds).toInt()) { sin(2 * PI * frequency * it / rate).toFloat() }

    /** Drops the filter's fade in and fade out at either end. */
    private fun FloatArray.steadyState() = copyOfRange(size / 10, size - size / 10)

    private fun rms(samples: FloatArray) = sqrt(samples.sumOf { it.toDouble() * it } / samples.size)

    private fun frequency(samples: FloatArray, rate: Int): Double {
        var crossings = 0
        for (i in 1 until samples.size) {
            if (samples[i - 1] < 0 && samples[i] >= 0) crossings++
        }
        return crossings * rate.toDouble() / samples.size
    }

    private companion object {
        val RMS_OF_UNIT_SINE = 1 / sqrt(2.0)
    }
}
