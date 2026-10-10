package io.github.fourlastor.scribe.speech

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import io.github.fourlastor.scribe.audio.AudioDecoder

/**
 * NVIDIA Parakeet TDT, run by sherpa-onnx from the model bundled under assets/model. It works out
 * the language from the audio and has no way to be told one, nor does it say which one it heard.
 */
class ParakeetEngine(assets: AssetManager, threads: Int) : SpeechEngine {

    private val recognizer = OfflineRecognizer(
        assets,
        OfflineRecognizerConfig(
            modelConfig = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = "model/encoder.onnx",
                    decoder = "model/decoder.onnx",
                    joiner = "model/joiner.onnx",
                ),
                tokens = "model/tokens.txt",
                modelType = "nemo_transducer",
                numThreads = threads,
            ),
        ),
    )

    override fun transcribe(samples: FloatArray, language: String?): ChunkTranscript {
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(samples, AudioDecoder.SAMPLE_RATE)
            recognizer.decode(stream)
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
            acceptsLanguage = false,
            // The model has no fixed window, but its memory use grows with the square of the length.
            maxChunkSeconds = 60f,
        )

        /** The languages of Parakeet TDT 0.6b v3. */
        private val LANGUAGES = listOf(
            "bg", "hr", "cs", "da", "nl", "en", "et", "fi", "fr", "de", "el", "hu", "it", "lv", "lt", "mt", "pl",
            "pt", "ro", "sk", "sl", "es", "sv", "ru", "uk",
        )
    }
}
