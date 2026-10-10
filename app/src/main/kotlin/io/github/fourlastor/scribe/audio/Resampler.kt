package io.github.fourlastor.scribe.audio

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Streaming sample rate converter for mono audio, using a windowed-sinc polyphase filter so that
 * downsampling does not alias. Feed blocks through [process], then call [flush] once.
 */
class Resampler(inputRate: Int, outputRate: Int) {

    private val passthrough = inputRate == outputRate

    // Output sample n sits at input position n * step / phases.
    private val phases: Int
    private val step: Int
    private val halfWidth: Int
    private val taps: Int
    private val kernels: FloatArray

    // Input not yet fully consumed, preceded by halfWidth zeros so the filter can be centred on
    // the first sample. pending[0] is at padded input index pendingStart.
    private var pending: FloatArray
    private var pendingSize: Int
    private var pendingStart = 0L
    private var nextOutput = 0L

    init {
        require(inputRate > 0 && outputRate > 0) { "Sample rates must be positive" }
        val divisor = gcd(inputRate, outputRate)
        phases = outputRate / divisor
        step = inputRate / divisor

        val cutoff = min(1.0, outputRate.toDouble() / inputRate) * CUTOFF
        halfWidth = ceil(ZERO_CROSSINGS / cutoff).toInt()
        taps = 2 * halfWidth + 1
        kernels = if (passthrough) FloatArray(0) else buildKernels(cutoff)

        pending = FloatArray(maxOf(taps * 2, 4096))
        pendingSize = halfWidth
    }

    fun process(input: FloatArray, length: Int = input.size): FloatArray {
        if (passthrough) return input.copyOf(length)
        append(input, length)
        return drain()
    }

    /** Emits the samples still held back waiting for input that will not come. */
    fun flush(): FloatArray {
        if (passthrough) return FloatArray(0)
        append(FloatArray(halfWidth), halfWidth)
        return drain()
    }

    private fun append(input: FloatArray, length: Int) {
        if (pendingSize + length > pending.size) {
            pending = pending.copyOf(maxOf(pending.size * 2, pendingSize + length))
        }
        System.arraycopy(input, 0, pending, pendingSize, length)
        pendingSize += length
    }

    private fun drain(): FloatArray {
        val end = pendingStart + pendingSize
        // Output n needs padded input [n * step / phases, n * step / phases + taps).
        val lastReady = (end - taps) * phases / step
        if (end < taps || lastReady < nextOutput) return FloatArray(0)

        val output = FloatArray((lastReady - nextOutput + 1).toInt())
        for (i in output.indices) {
            val position = nextOutput * step
            val start = (position / phases - pendingStart).toInt()
            val kernel = (position % phases).toInt() * taps
            var sum = 0f
            for (tap in 0 until taps) {
                sum += pending[start + tap] * kernels[kernel + tap]
            }
            output[i] = sum
            nextOutput++
        }

        val keepFrom = (nextOutput * step / phases - pendingStart).toInt().coerceAtMost(pendingSize)
        System.arraycopy(pending, keepFrom, pending, 0, pendingSize - keepFrom)
        pendingSize -= keepFrom
        pendingStart += keepFrom
        return output
    }

    private fun buildKernels(cutoff: Double): FloatArray {
        val kernels = FloatArray(phases * taps)
        for (phase in 0 until phases) {
            val fraction = phase.toDouble() / phases
            var sum = 0.0
            for (tap in 0 until taps) {
                val distance = tap - halfWidth - fraction
                val weight = cutoff * sinc(cutoff * distance) * blackman(distance / (halfWidth + 1))
                kernels[phase * taps + tap] = weight.toFloat()
                sum += weight
            }
            // Unity gain at DC for every phase, otherwise the output carries a faint ripple.
            for (tap in 0 until taps) {
                kernels[phase * taps + tap] = (kernels[phase * taps + tap] / sum).toFloat()
            }
        }
        return kernels
    }

    private fun sinc(x: Double): Double = if (x == 0.0) 1.0 else sin(PI * x) / (PI * x)

    private fun blackman(x: Double): Double =
        if (x <= -1.0 || x >= 1.0) 0.0 else 0.42 + 0.5 * cos(PI * x) + 0.08 * cos(2 * PI * x)

    private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    private companion object {
        /** Sinc lobes on each side of the centre, at the cutoff frequency. */
        const val ZERO_CROSSINGS = 16.0

        /** Fraction of the output Nyquist frequency kept, leaving room for the filter roll-off. */
        const val CUTOFF = 0.92
    }
}
