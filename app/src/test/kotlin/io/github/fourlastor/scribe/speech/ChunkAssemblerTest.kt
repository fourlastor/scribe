package io.github.fourlastor.scribe.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChunkAssemblerTest {

    private fun assembler(min: Int = 10, max: Int = 30, sentencePause: Int = 3, maxGap: Int = 8) =
        ChunkAssembler(min, max, sentencePause, maxGap)

    @Test
    fun `a chunk covers the short pauses between its stretches of speech`() {
        val assembler = assembler()

        assertEquals(emptyList<SampleRange>(), assembler.add(SampleRange(0, 4)))
        assertEquals(emptyList<SampleRange>(), assembler.add(SampleRange(5, 9)))

        assertEquals(SampleRange(0, 9), assembler.flush())
    }

    @Test
    fun `a chunk ends at the first sentence pause once it is long enough`() {
        val assembler = assembler()

        // Pauses of 3 after 4 and after 8 samples come too early, the one after 12 ends the chunk.
        assembler.add(SampleRange(0, 4))
        assembler.add(SampleRange(7, 8))
        assembler.add(SampleRange(11, 12))
        val completed = assembler.add(SampleRange(15, 20))

        assertEquals(listOf(SampleRange(0, 12)), completed)
        assertEquals(SampleRange(15, 20), assembler.flush())
    }

    @Test
    fun `a short pause does not end a chunk that is long enough`() {
        val assembler = assembler()

        assembler.add(SampleRange(0, 12))
        val completed = assembler.add(SampleRange(14, 20))

        assertEquals(emptyList<SampleRange>(), completed)
    }

    @Test
    fun `a long pause ends the chunk however short it is`() {
        val assembler = assembler()

        assembler.add(SampleRange(0, 2))
        val completed = assembler.add(SampleRange(11, 15))

        assertEquals(listOf(SampleRange(0, 2)), completed)
    }

    @Test
    fun `speech without a sentence pause is cut at its longest pause`() {
        val assembler = assembler()

        assembler.add(SampleRange(0, 11))
        assembler.add(SampleRange(12, 18))
        assembler.add(SampleRange(20, 27))
        val completed = assembler.add(SampleRange(28, 35))

        assertEquals(listOf(SampleRange(0, 18)), completed)
        assertEquals(SampleRange(20, 35), assembler.flush())
    }

    @Test
    fun `a forced cut keeps the chunk above the minimum when it can`() {
        val assembler = assembler()

        // The longest pause comes after only 5 samples, so the next longest is used.
        assembler.add(SampleRange(0, 5))
        assembler.add(SampleRange(7, 20))
        assembler.add(SampleRange(21, 28))
        val completed = assembler.add(SampleRange(29, 33))

        assertEquals(listOf(SampleRange(0, 28)), completed)
    }

    @Test
    fun `speech longer than a chunk is cut`() {
        val assembler = assembler(max = 10)

        val completed = assembler.add(SampleRange(5, 30))

        assertEquals(listOf(SampleRange(5, 15), SampleRange(15, 25)), completed)
        assertEquals(SampleRange(25, 30), assembler.flush())
    }

    @Test
    fun `nothing is left when no speech was added`() {
        assertNull(assembler().flush())
    }

    @Test
    fun `flushing empties the assembler`() {
        val assembler = assembler()
        assembler.add(SampleRange(0, 4))
        assembler.flush()

        assertNull(assembler.flush())
    }
}
