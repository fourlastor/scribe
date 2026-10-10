package io.github.fourlastor.scribe.speech

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import io.github.fourlastor.scribe.audio.AudioDecoder

/**
 * Finds the stretches of speech in a recording with the Silero voice activity detector, so that
 * long recordings can be cut at pauses and long silences never reach the speech model.
 */
class VoiceSegmenter(assets: AssetManager, maxSpanSeconds: Float) : AutoCloseable {

    private val vad = Vad(
        assets,
        VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = "vad/silero_vad.onnx",
                minSilenceDuration = 0.4f,
                // Past this the detector gets stricter about what counts as speech, to force a cut.
                maxSpeechDuration = maxSpanSeconds,
                windowSize = WINDOW,
            ),
            sampleRate = AudioDecoder.SAMPLE_RATE,
        ),
    )

    private val window = FloatArray(WINDOW)
    private var filled = 0

    /** Feeds the next block of the recording and returns the speech that ended inside it. */
    fun accept(samples: FloatArray): List<SampleRange> {
        // The detector places the start and end of speech relative to the end of what it was last
        // given. Handing it more than one window at a time puts them up to a whole block off.
        val speech = ArrayList<SampleRange>()
        var offset = 0
        while (offset < samples.size) {
            val count = minOf(WINDOW - filled, samples.size - offset)
            samples.copyInto(window, filled, offset, offset + count)
            filled += count
            offset += count
            if (filled == WINDOW) {
                vad.acceptWaveform(window)
                filled = 0
                drainTo(speech)
            }
        }
        return speech
    }

    /** Returns the speech still going when the recording ended, if any. */
    fun finish(): List<SampleRange> {
        val speech = ArrayList<SampleRange>()
        if (filled > 0) {
            vad.acceptWaveform(window.copyOf(filled))
            filled = 0
        }
        vad.flush()
        drainTo(speech)
        return speech
    }

    override fun close() = vad.release()

    private fun drainTo(speech: MutableList<SampleRange>) {
        while (!vad.empty()) {
            val segment = vad.front()
            speech += SampleRange(segment.start, segment.start + segment.samples.size)
            vad.pop()
        }
    }

    private companion object {
        /** Samples the detector looks at in one step, 32 ms. */
        const val WINDOW = 512
    }
}
