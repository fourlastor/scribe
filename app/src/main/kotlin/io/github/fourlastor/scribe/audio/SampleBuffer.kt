package io.github.fourlastor.scribe.audio

import kotlin.math.max
import kotlin.math.min

/**
 * The tail of a recording that is being decoded: samples are appended as they arrive, and dropped
 * from the front once nothing needs them any more. Positions count from the start of the
 * recording, whatever has been dropped.
 */
class SampleBuffer {
    private var samples = FloatArray(1 shl 16)
    private var start = 0
    private var size = 0

    /** Position just past the last sample appended. */
    val end: Int get() = start + size

    fun append(block: FloatArray) {
        if (size + block.size > samples.size) {
            samples = samples.copyOf(max(samples.size * 2, size + block.size))
        }
        block.copyInto(samples, size)
        size += block.size
    }

    /** Copies the samples from [from] to [to], leaving out any part that is not held. */
    fun copyOfRange(from: Int, to: Int): FloatArray {
        val first = max(from, start)
        val last = min(to, end)
        return if (first >= last) FloatArray(0) else samples.copyOfRange(first - start, last - start)
    }

    /** Forgets everything before [position]. */
    fun dropBefore(position: Int) {
        val count = (position - start).coerceIn(0, size)
        samples.copyInto(samples, 0, count, size)
        start += count
        size -= count
    }
}
