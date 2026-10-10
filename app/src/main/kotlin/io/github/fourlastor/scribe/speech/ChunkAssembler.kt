package io.github.fourlastor.scribe.speech

import kotlin.math.min

/** A stretch of the recording, in samples from its start. [end] is exclusive. */
data class SampleRange(val start: Int, val end: Int)

/**
 * Groups the stretches of speech found by the voice activity detector into chunks the engine can
 * take in one call. A chunk runs from the start of its first stretch to the end of its last,
 * pauses included.
 *
 * Where a chunk ends matters: the engine transcribes each chunk on its own, so a cut in the middle
 * of a sentence shows in the text. A chunk therefore ends at the first pause of at least
 * [sentencePauseSamples] once it holds [minChunkSamples], which is enough context for the engine
 * and makes text appear every few seconds. Speech that keeps going without such a pause is cut at
 * the longest pause there is before [maxChunkSamples]. A pause longer than [maxGapSamples] always
 * ends the chunk, whatever its length.
 */
class ChunkAssembler(
    private val minChunkSamples: Int,
    private val maxChunkSamples: Int,
    private val sentencePauseSamples: Int,
    private val maxGapSamples: Int,
) {
    private val pending = ArrayList<SampleRange>()

    /** Adds the next stretch of speech, in recording order, and returns the chunks it completed. */
    fun add(speech: SampleRange): List<SampleRange> {
        val completed = ArrayList<SampleRange>()
        var start = speech.start
        while (start < speech.end) {
            // Speech longer than a whole chunk has to be cut mid-word.
            val piece = SampleRange(start, min(speech.end, start + maxChunkSamples))
            if (pending.isNotEmpty()) {
                val pause = piece.start - pending.last().end
                val length = pending.last().end - pending.first().start
                if (pause > maxGapSamples || length >= minChunkSamples && pause >= sentencePauseSamples) {
                    completed += take(pending.size)
                }
            }
            while (pending.isNotEmpty() && piece.end - pending.first().start > maxChunkSamples) {
                completed += take(longestPause(before = piece))
            }
            pending += piece
            start = piece.end
        }
        return completed
    }

    /** Returns what is left once the recording has ended. */
    fun flush(): SampleRange? = if (pending.isEmpty()) null else take(pending.size)

    /**
     * How many pending stretches to put in the chunk, so that it ends at the longest pause.
     * Pauses that would leave the chunk under the minimum only count if there is no other.
     */
    private fun longestPause(before: SampleRange): Int {
        var best = pending.size
        var bestPause = -1
        var bestIsLongEnough = false
        for (count in 1..pending.size) {
            val next = pending.getOrElse(count) { before }
            val pause = next.start - pending[count - 1].end
            val longEnough = pending[count - 1].end - pending.first().start >= minChunkSamples
            if (longEnough && !bestIsLongEnough || longEnough == bestIsLongEnough && pause >= bestPause) {
                best = count
                bestPause = pause
                bestIsLongEnough = longEnough
            }
        }
        return best
    }

    private fun take(count: Int): SampleRange {
        val chunk = SampleRange(pending.first().start, pending[count - 1].end)
        pending.subList(0, count).clear()
        return chunk
    }
}
