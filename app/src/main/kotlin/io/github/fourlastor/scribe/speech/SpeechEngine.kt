package io.github.fourlastor.scribe.speech

/** What a speech model can do, known without loading it. */
data class EngineSpec(
    /** Identifies the model in saved transcripts. */
    val id: String,
    /** Language codes the model can transcribe. */
    val languages: List<String>,
    /** Whether the model works out the language by itself when none is given. */
    val detectsLanguage: Boolean,
    /** Whether the model can be told which language to expect. */
    val acceptsLanguage: Boolean,
    /** Longest audio the model takes in a single call. */
    val maxChunkSeconds: Float,
)

class ChunkTranscript(val text: String, val language: String?)

/**
 * A loaded speech-to-text model. This is the seam for swapping models: everything outside the
 * speech package only sees this interface and [EngineSpec].
 *
 * Engines are not thread safe and hold native memory until closed.
 */
interface SpeechEngine : AutoCloseable {

    /**
     * Transcribes 16 kHz mono [samples], no longer than [EngineSpec.maxChunkSeconds].
     *
     * [language] is null to let the engine detect it, which needs [EngineSpec.detectsLanguage].
     * A non-null value needs [EngineSpec.acceptsLanguage].
     */
    fun transcribe(samples: FloatArray, language: String?): ChunkTranscript
}
