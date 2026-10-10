package io.github.fourlastor.scribe.ui.history

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fourlastor.scribe.R
import io.github.fourlastor.scribe.ui.theme.ScribeTheme
import io.github.fourlastor.scribe.ui.transcript.TranscriptSheet
import io.github.fourlastor.scribe.ui.transcript.TranscriptViewModel
import kotlinx.coroutines.launch

/** The launcher entry: past transcripts, each opening in the same sheet sharing uses. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScribeTheme { MainScreen() }
        }
    }
}

@Composable
private fun MainScreen(history: HistoryViewModel = viewModel(), session: TranscriptViewModel = viewModel()) {
    val transcripts by history.transcripts.collectAsStateWithLifecycle()
    val open by session.state.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) session.open(uri)
    }
    val deleted = stringResource(R.string.history_deleted)
    val undo = stringResource(R.string.history_undo)

    HistoryScreen(
        transcripts = transcripts,
        snackbars = snackbars,
        onOpen = session::show,
        onPickAudio = { pickAudio.launch("audio/*") },
    )

    open?.let { state ->
        TranscriptSheet(
            state = state,
            onLanguageSelected = session::selectLanguage,
            onDismissed = session::close,
            onDelete = {
                val transcript = state.saved ?: return@TranscriptSheet
                session.close()
                history.delete(transcript)
                scope.launch {
                    val result = snackbars.showSnackbar(deleted, actionLabel = undo, duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) history.restore(transcript)
                }
            },
        )
    }
}
