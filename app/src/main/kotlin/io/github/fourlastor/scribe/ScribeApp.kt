package io.github.fourlastor.scribe

import android.app.Application
import androidx.room.Room
import io.github.fourlastor.scribe.data.LanguagePreferences
import io.github.fourlastor.scribe.data.ScribeDatabase
import io.github.fourlastor.scribe.data.TranscriptDao
import io.github.fourlastor.scribe.speech.Transcriber
import java.io.File

class ScribeApp : Application() {

    val transcriber: Transcriber by lazy { Transcriber(assets) }

    val transcripts: TranscriptDao by lazy {
        Room.databaseBuilder(this, ScribeDatabase::class.java, "scribe.db").build().transcripts()
    }

    val languages: LanguagePreferences by lazy { LanguagePreferences(this) }

    /** Where shared audio is copied to for as long as its transcript is on screen. */
    val incomingAudio: File get() = File(cacheDir, "incoming")

    override fun onCreate() {
        super.onCreate()
        // Copies outlive their screen only if the process died with it.
        incomingAudio.deleteRecursively()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // The model is by far the largest thing held, and is reloaded on the next transcription.
        if (level >= TRIM_MEMORY_BACKGROUND) transcriber.releaseModel()
    }
}
