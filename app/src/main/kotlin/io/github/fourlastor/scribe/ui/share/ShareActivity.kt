package io.github.fourlastor.scribe.ui.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fourlastor.scribe.ui.theme.ScribeTheme
import io.github.fourlastor.scribe.ui.transcript.TranscriptSheet
import io.github.fourlastor.scribe.ui.transcript.TranscriptViewModel

/**
 * Receives audio shared from another app and transcribes it in a sheet over that app. The window
 * itself is transparent, see Theme.Scribe.Overlay.
 */
class ShareActivity : ComponentActivity() {

    private val session: TranscriptViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val audio = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        if (audio == null) {
            finish()
            return
        }
        // The view model outlives rotation, so only a fresh one needs starting.
        if (session.state.value == null) session.open(audio)

        setContent {
            ScribeTheme {
                val state by session.state.collectAsStateWithLifecycle()
                state?.let {
                    TranscriptSheet(
                        state = it,
                        onLanguageSelected = session::selectLanguage,
                        onDismissed = ::finish,
                    )
                }
            }
        }
    }
}
