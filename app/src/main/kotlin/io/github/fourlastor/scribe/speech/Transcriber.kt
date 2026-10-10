package io.github.fourlastor.scribe.speech

import android.content.res.AssetManager
import android.os.SystemClock
import android.util.Log
import io.github.fourlastor.scribe.audio.AudioDecoder
import io.github.fourlastor.scribe.audio.SampleBuffer
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface TranscriptionEvent {
    /** The file states how long the recording is. */
    data class Duration(val millis: Long) : TranscriptionEvent

    /** The model is in memory and the first speech is being transcribed. */
    data object ModelReady : TranscriptionEvent

    /** The engine worked out which language is being spoken. */
    data class Language(val code: String) : TranscriptionEvent

    /** The next piece of the transcript. */
    data class Text(val text: String) : TranscriptionEvent

    /** Everything up to [millis] into the recording has been transcribed. */
    data class Progress(val millis: Long) : TranscriptionEvent
}

/**
 * Turns an audio file into text with the bundled speech model: decode, split at pauses, then
 * transcribe chunk by chunk so the text can be shown as it is produced.
 */
class Transcriber(private val assets: AssetManager, private val decoder: AudioDecoder = AudioDecoder()) {

    val spec: EngineSpec = SpeechModels.bundled

    // One recording at a time: the engine is not thread safe, and two would only fight for cores.
    private val mutex = Mutex()
    private val worker = Dispatchers.Default.limitedParallelism(1)

    @Volatile
    private var engine: SpeechEngine? = null

    /**
     * Transcribes [audio]. With a null [language] the engine detects it from the start of the speech.
     * The flow completes when the whole recording is done, and emits no [TranscriptionEvent.Text]
     * at all if it holds no speech.
     */
    fun transcribe(audio: File, language: String?): Flow<TranscriptionEvent> = flow {
        mutex.withLock {
            coroutineScope {
                // Loading the model takes seconds on a cold start, so it overlaps with decoding.
                val loading = async(Dispatchers.Default) {
                    engine ?: timed("Model ${spec.id} loaded") { SpeechModels.load(assets) }.also { engine = it }
                }
                val assembler = ChunkAssembler(
                    minChunkSamples = MIN_CHUNK,
                    maxChunkSamples = (spec.maxChunkSeconds * AudioDecoder.SAMPLE_RATE).toInt() - MAX_GAP - TAIL,
                    sentencePauseSamples = SENTENCE_PAUSE,
                    maxGapSamples = MAX_GAP,
                )
                val recording = SampleBuffer()
                var transcribedTo = 0
                var selected = language
                var ready = false

                suspend fun transcribe(chunk: SampleRange, last: Boolean = false) {
                    // The detector misses short words next to a pause, so a chunk also takes the
                    // audio around its speech: back to the previous chunk, and on to the end of
                    // the recording for the last one. Only pauses longer than MAX_GAP are skipped.
                    val from = max(chunk.start - MAX_GAP, transcribedTo)
                    val to = min(chunk.end + if (last) MAX_GAP else TAIL, recording.end)
                    val samples = recording.copyOfRange(from, to)
                    transcribedTo = to
                    recording.dropBefore(to)

                    val loaded = loading.await()
                    if (!ready) {
                        ready = true
                        emit(TranscriptionEvent.ModelReady)
                    }
                    currentCoroutineContext().ensureActive()
                    val seconds = samples.size / AudioDecoder.SAMPLE_RATE.toFloat()
                    val result = timed("Chunk of %.1f s transcribed".format(seconds)) {
                        loaded.transcribe(samples, selected)
                    }
                    if (selected == null && result.language != null) {
                        emit(TranscriptionEvent.Language(result.language))
                        // Detecting on every chunk could flip language mid-recording, so the first
                        // chunk long enough to be trusted settles it.
                        if (samples.size >= TRUSTED_FOR_LANGUAGE) selected = result.language
                    }
                    // Some engines leave runs of spaces between sentences.
                    val text = result.text.replace(WHITESPACE, " ").trim()
                    if (text.isNotEmpty()) emit(TranscriptionEvent.Text(text))
                    emit(TranscriptionEvent.Progress(chunk.end * 1000L / AudioDecoder.SAMPLE_RATE))
                }

                VoiceSegmenter(assets, MAX_SPEECH_SECONDS).use { segmenter ->
                    decoder.decode(audio, onDuration = { emit(TranscriptionEvent.Duration(it)) }) { samples ->
                        recording.append(samples)
                        for (speech in segmenter.accept(samples)) {
                            for (chunk in assembler.add(speech)) transcribe(chunk)
                        }
                    }
                    for (speech in segmenter.finish()) {
                        for (chunk in assembler.add(speech)) transcribe(chunk)
                    }
                }
                assembler.flush()?.let { transcribe(it, last = true) }
            }
        }
    }.flowOn(worker)

    /** Frees the model's memory unless a transcription is using it. It is reloaded on demand. */
    fun releaseModel() {
        if (!mutex.tryLock()) return
        try {
            engine?.close()
            engine = null
        } finally {
            mutex.unlock()
        }
    }

    private inline fun <T> timed(what: String, block: () -> T): T {
        val start = SystemClock.elapsedRealtime()
        return block().also { Log.d(TAG, "$what in ${SystemClock.elapsedRealtime() - start} ms") }
    }

    private companion object {
        const val TAG = "Scribe"

        val WHITESPACE = Regex("\\s+")

        /**
         * A chunk is not ended by a sentence pause before it holds this much audio. Every cut can
         * show in the text as a sentence break that is not there, so chunks are kept long.
         */
        const val MIN_CHUNK = 20 * AudioDecoder.SAMPLE_RATE

        /** A pause this long is taken for the end of a sentence. */
        const val SENTENCE_PAUSE = AudioDecoder.SAMPLE_RATE * 8 / 10

        /** Audio shorter than this is too little to settle the language of the whole recording. */
        const val TRUSTED_FOR_LANGUAGE = 4 * AudioDecoder.SAMPLE_RATE

        /** A pause longer than this ends the chunk, and the rest of it is skipped. */
        const val MAX_GAP = 2 * AudioDecoder.SAMPLE_RATE

        /** Audio kept after the speech in a chunk, so its last sound is not cut short. */
        const val TAIL = AudioDecoder.SAMPLE_RATE / 4

        /** Speech running longer than this without a pause is cut by the detector. */
        const val MAX_SPEECH_SECONDS = 20f
    }
}
