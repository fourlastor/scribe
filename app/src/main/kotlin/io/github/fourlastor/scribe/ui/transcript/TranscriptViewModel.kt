package io.github.fourlastor.scribe.ui.transcript

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.fourlastor.scribe.ScribeApp
import io.github.fourlastor.scribe.data.Transcript
import io.github.fourlastor.scribe.speech.Languages
import io.github.fourlastor.scribe.speech.TranscriptionEvent
import java.io.File
import java.io.FileNotFoundException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Phase {
    LoadingModel,
    Transcribing,
    Done,

    /** The recording holds no speech. */
    NoSpeech,

    /** There was speech, but the engine got no words out of it. */
    NoWords,
    Failed;

    val isRunning: Boolean get() = this == LoadingModel || this == Transcribing
}

/** What the language picker offers, for a recording that can be transcribed again. */
data class LanguageChoice(
    /** The engine is picking the language, rather than the user. */
    val automatic: Boolean,
    val canDetect: Boolean,
    val suggested: List<String>,
    val all: List<String>,
)

data class TranscriptUiState(
    val phase: Phase,
    /** Counts transcription attempts, so the screen can tell a fresh start from more text. */
    val run: Int = 0,
    val text: String = "",
    val language: String? = null,
    /** Null when the language cannot be changed: saved transcripts have no audio to go back to. */
    val languageChoice: LanguageChoice? = null,
    /** Fraction of the recording done so far, when its length is known. */
    val progress: Float? = null,
    /** The history entry for this transcript, once there is one. */
    val saved: Transcript? = null,
)

/** Drives one transcript sheet: either a recording being transcribed, or an entry from history. */
class TranscriptViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ScribeApp
    private val spec = app.transcriber.spec

    private val _state = MutableStateFlow<TranscriptUiState?>(null)

    /** Null while there is nothing to show. */
    val state: StateFlow<TranscriptUiState?> = _state.asStateFlow()

    private var job: Job? = null
    private var audio: File? = null
    private var runs = 0

    /** Transcribes the audio behind [uri], which must stay readable until it has been copied. */
    fun open(uri: Uri) {
        close()
        _state.value = TranscriptUiState(
            phase = Phase.LoadingModel,
            run = ++runs,
            languageChoice = if (spec.acceptsLanguage) {
                LanguageChoice(
                    automatic = spec.detectsLanguage,
                    canDetect = spec.detectsLanguage,
                    suggested = emptyList(),
                    all = spec.languages,
                )
            } else {
                null
            },
        )
        job = viewModelScope.launch {
            val file = try {
                withContext(Dispatchers.IO) { copy(uri) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not read shared audio", e)
                _state.update { it?.copy(phase = Phase.Failed) }
                return@launch
            }
            audio = file
            // Engines that cannot detect the language start from the one used last.
            val language = if (spec.detectsLanguage) null else suggestions().firstOrNull() ?: spec.languages.first()
            transcribe(file, language)
        }
    }

    /** Shows a transcript from history. */
    fun show(transcript: Transcript) {
        close()
        _state.value = TranscriptUiState(
            phase = Phase.Done,
            run = ++runs,
            text = transcript.text,
            language = transcript.language,
            saved = transcript,
        )
    }

    /** Transcribes the recording again in [language], or with automatic detection when null. */
    fun selectLanguage(language: String?) {
        val file = audio ?: return
        val current = _state.value ?: return
        val choice = current.languageChoice ?: return
        val unchanged = if (language == null) choice.automatic else !choice.automatic && language == current.language
        if (unchanged) return

        job?.cancel()
        job = viewModelScope.launch {
            if (language != null) app.languages.remember(language)
            transcribe(file, language)
        }
    }

    fun close() {
        job?.cancel()
        job = null
        audio?.delete()
        audio = null
        _state.value = null
    }

    override fun onCleared() {
        audio?.delete()
    }

    private suspend fun transcribe(file: File, language: String?) {
        val suggested = suggestions()
        _state.update {
            it?.copy(
                phase = Phase.LoadingModel,
                run = ++runs,
                text = "",
                language = language,
                languageChoice = it.languageChoice?.copy(automatic = language == null, suggested = suggested),
                progress = null,
            )
        }

        var duration: Long? = null
        var transcribed = 0L
        try {
            app.transcriber.transcribe(file, language).collect { event ->
                when (event) {
                    is TranscriptionEvent.Duration -> duration = event.millis
                    TranscriptionEvent.ModelReady -> _state.update { it?.copy(phase = Phase.Transcribing) }
                    is TranscriptionEvent.Language -> _state.update { it?.copy(language = event.code) }
                    is TranscriptionEvent.Text -> _state.update {
                        it?.copy(text = Languages.join(it.text, event.text, it.language))
                    }
                    is TranscriptionEvent.Progress -> {
                        transcribed = event.millis
                        val total = duration ?: return@collect
                        _state.update { it?.copy(progress = (event.millis.toFloat() / total).coerceIn(0f, 1f)) }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Transcription failed", e)
            _state.update { it?.copy(phase = Phase.Failed) }
            return
        }

        val finished = _state.value ?: return
        if (finished.text.isEmpty()) {
            // Progress is only reported for audio the engine was given, which is speech.
            _state.update { it?.copy(phase = if (transcribed > 0) Phase.NoWords else Phase.NoSpeech) }
            return
        }
        // Transcribing the same recording again replaces its history entry instead of adding one.
        val transcript = Transcript(
            id = finished.saved?.id ?: 0,
            createdAt = finished.saved?.createdAt ?: System.currentTimeMillis(),
            text = finished.text,
            language = finished.language,
            languageDetected = language == null,
            durationMs = duration ?: transcribed,
            model = spec.id,
        )
        val id = app.transcripts.save(transcript)
        _state.update { it?.copy(phase = Phase.Done, saved = transcript.copy(id = id)) }
    }

    /** Languages worth offering first: the ones picked before, then the ones the device is set to. */
    private suspend fun suggestions(): List<String> {
        val locales = app.resources.configuration.locales
        val device = List(locales.size()) { locales[it].language }
        return (app.languages.recent.first() + device).distinct().filter { it in spec.languages }
    }

    private fun copy(uri: Uri): File {
        val directory = app.incomingAudio.apply { mkdirs() }
        val file = File.createTempFile("audio", null, directory)
        val input = app.contentResolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
        input.use { source -> file.outputStream().use { source.copyTo(it) } }
        return file
    }

    private companion object {
        const val TAG = "Scribe"
    }
}
