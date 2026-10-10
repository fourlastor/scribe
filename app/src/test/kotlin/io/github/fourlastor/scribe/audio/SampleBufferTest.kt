package io.github.fourlastor.scribe.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SampleBufferTest {

    @Test
    fun `positions count from the start of the recording after dropping`() {
        val buffer = SampleBuffer()
        buffer.append(floatArrayOf(0f, 1f, 2f, 3f))
        buffer.dropBefore(2)
        buffer.append(floatArrayOf(4f, 5f))

        assertEquals(6, buffer.end)
        assertArrayEquals(floatArrayOf(3f, 4f), buffer.copyOfRange(3, 5), 0f)
    }

    @Test
    fun `a range reaching outside what is held is clipped`() {
        val buffer = SampleBuffer()
        buffer.append(floatArrayOf(0f, 1f, 2f, 3f))
        buffer.dropBefore(1)

        assertArrayEquals(floatArrayOf(1f, 2f, 3f), buffer.copyOfRange(-5, 50), 0f)
        assertArrayEquals(floatArrayOf(), buffer.copyOfRange(10, 20), 0f)
    }

    @Test
    fun `it grows past its initial capacity`() {
        val buffer = SampleBuffer()
        repeat(10) { block -> buffer.append(FloatArray(20_000) { block.toFloat() }) }

        assertEquals(200_000, buffer.end)
        assertArrayEquals(floatArrayOf(8f, 9f), buffer.copyOfRange(179_999, 180_001), 0f)
    }
}
