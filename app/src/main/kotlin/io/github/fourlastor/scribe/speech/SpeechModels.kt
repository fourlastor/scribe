package io.github.fourlastor.scribe.speech

import android.content.res.AssetManager
import io.github.fourlastor.scribe.BuildConfig

/**
 * Maps the model Gradle bundled into the APK to the engine that runs it.
 *
 * To support another model family, add it to the catalogue in app/build.gradle.kts, implement
 * [SpeechEngine] for it, and add a branch to each `when` here.
 */
object SpeechModels {

    val bundled: EngineSpec = when (BuildConfig.SPEECH_MODEL_FAMILY) {
        "whisper" -> WhisperEngine.spec(BuildConfig.SPEECH_MODEL_ID)
        "parakeet" -> ParakeetEngine.spec(BuildConfig.SPEECH_MODEL_ID)
        "nemotron" -> NemotronEngine.spec(BuildConfig.SPEECH_MODEL_ID)
        else -> error("No engine for model family ${BuildConfig.SPEECH_MODEL_FAMILY}")
    }

    /** Loads the bundled model. Slow, and allocates hundreds of megabytes: keep off the main thread. */
    fun load(assets: AssetManager): SpeechEngine = when (BuildConfig.SPEECH_MODEL_FAMILY) {
        "whisper" -> WhisperEngine(assets, threads())
        "parakeet" -> ParakeetEngine(assets, threads())
        "nemotron" -> NemotronEngine(assets, threads())
        else -> error("No engine for model family ${BuildConfig.SPEECH_MODEL_FAMILY}")
    }

    // More threads than this land on the efficiency cores and slow inference down.
    private fun threads() = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
}
