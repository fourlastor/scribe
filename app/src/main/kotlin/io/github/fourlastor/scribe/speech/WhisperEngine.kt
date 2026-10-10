package io.github.fourlastor.scribe.speech

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import io.github.fourlastor.scribe.audio.AudioDecoder

/** OpenAI Whisper, run by sherpa-onnx from the model bundled under assets/model. */
class WhisperEngine(assets: AssetManager, private val threads: Int) : SpeechEngine {

    private var language: String? = null
    private val recognizer = OfflineRecognizer(assets, config(language))

    override fun transcribe(samples: FloatArray, language: String?): ChunkTranscript {
        if (language != this.language) {
            // Only the Whisper options are re-read, the model stays loaded.
            recognizer.setConfig(config(language))
            this.language = language
        }
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(samples, AudioDecoder.SAMPLE_RATE)
            recognizer.decode(stream)
            val result = recognizer.getResult(stream)
            return ChunkTranscript(result.text.trim(), result.lang.filter { it.isLetter() }.ifEmpty { null })
        } finally {
            stream.release()
        }
    }

    override fun close() = recognizer.release()

    private fun config(language: String?) = OfflineRecognizerConfig(
        modelConfig = OfflineModelConfig(
            whisper = OfflineWhisperModelConfig(
                encoder = "model/encoder.onnx",
                decoder = "model/decoder.onnx",
                // Empty makes Whisper detect the language.
                language = language.orEmpty(),
                task = "transcribe",
            ),
            tokens = "model/tokens.txt",
            modelType = "whisper",
            numThreads = threads,
        ),
    )

    companion object {
        fun spec(modelId: String) = EngineSpec(
            id = modelId,
            languages = LANGUAGES,
            detectsLanguage = true,
            acceptsLanguage = true,
            // Whisper's window is 30 seconds, and sherpa-onnx wants some of it left as padding.
            maxChunkSeconds = 28f,
        )

        /** The languages of the multilingual Whisper models, most to least training data. */
        private val LANGUAGES = listOf(
            "en", "zh", "de", "es", "ru", "ko", "fr", "ja", "pt", "tr", "pl", "ca", "nl", "ar", "sv", "it", "id",
            "hi", "fi", "vi", "he", "uk", "el", "ms", "cs", "ro", "da", "hu", "ta", "no", "th", "ur", "hr", "bg",
            "lt", "la", "mi", "ml", "cy", "sk", "te", "fa", "lv", "bn", "sr", "az", "sl", "kn", "et", "mk", "br",
            "eu", "is", "hy", "ne", "mn", "bs", "kk", "sq", "sw", "gl", "mr", "pa", "si", "km", "sn", "yo", "so",
            "af", "oc", "ka", "be", "tg", "sd", "gu", "am", "yi", "lo", "uz", "fo", "ht", "ps", "tk", "nn", "mt",
            "sa", "lb", "my", "bo", "tl", "mg", "as", "tt", "haw", "ln", "ha", "ba", "jw", "su",
        )
    }
}
