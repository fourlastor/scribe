package io.github.fourlastor.scribe.speech

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import io.github.fourlastor.scribe.audio.AudioDecoder

/**
 * NVIDIA Nemotron 3.5 ASR, run by sherpa-onnx from the model bundled under assets/model. It can
 * be told the language or left to work it out, but does not say which one it settled on.
 *
 * It is a streaming model, used here one chunk at a time like the others.
 */
class NemotronEngine(assets: AssetManager, threads: Int) : SpeechEngine {

    private val recognizer = OnlineRecognizer(
        assets,
        OnlineRecognizerConfig(
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "model/encoder.onnx",
                    decoder = "model/decoder.onnx",
                    joiner = "model/joiner.onnx",
                ),
                tokens = "model/tokens.txt",
                numThreads = threads,
            ),
            // Chunks are already cut at pauses, the recognizer need not look for them.
            enableEndpoint = false,
        ),
    )

    override fun transcribe(samples: FloatArray, language: String?): ChunkTranscript {
        val stream = recognizer.createStream()
        try {
            stream.setOption("language", language ?: "auto")
            stream.acceptWaveform(samples, AudioDecoder.SAMPLE_RATE)
            // The model only emits a word once it has heard a little past it.
            stream.acceptWaveform(FloatArray(TAIL_SAMPLES), AudioDecoder.SAMPLE_RATE)
            stream.inputFinished()
            while (recognizer.isReady(stream)) recognizer.decode(stream)
            return ChunkTranscript(recognizer.getResult(stream).text.trim(), language = null)
        } finally {
            stream.release()
        }
    }

    override fun close() = recognizer.release()

    companion object {
        fun spec(modelId: String) = EngineSpec(
            id = modelId,
            languages = LANGUAGES,
            detectsLanguage = true,
            acceptsLanguage = true,
            // The model has no fixed window. This keeps text arriving steadily.
            maxChunkSeconds = 60f,
        )

        /** Silence added after each chunk so the last words are flushed out. */
        private const val TAIL_SAMPLES = AudioDecoder.SAMPLE_RATE * 3 / 2

        /** The languages of Nemotron 3.5 ASR, best supported first. */
        private val LANGUAGES = listOf(
            "en", "es", "fr", "it", "pt", "nl", "de", "tr", "ru", "ar", "hi", "ja", "ko", "vi", "uk",
            "bg", "cs", "da", "el", "et", "fi", "hu", "hr", "lt", "lv", "pl", "ro", "sk", "sl", "sv",
            "he", "nb", "nn", "th", "zh",
        )
    }
}
