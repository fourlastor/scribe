package io.github.fourlastor.scribe.ui.history

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.fourlastor.scribe.R
import io.github.fourlastor.scribe.data.Transcript
import io.github.fourlastor.scribe.speech.Languages

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    transcripts: List<Transcript>?,
    snackbars: SnackbarHostState,
    onOpen: (Transcript) -> Unit,
    onPickAudio: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }, scrollBehavior = scrollBehavior) },
        snackbarHost = { SnackbarHost(snackbars) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onPickAudio,
                icon = { Icon(Icons.Rounded.AudioFile, contentDescription = null) },
                text = { Text(stringResource(R.string.history_open_audio)) },
            )
        },
    ) { padding ->
        when {
            // Reading the list takes a few milliseconds: showing nothing beats flashing a spinner.
            transcripts == null -> Unit
            transcripts.isEmpty() -> EmptyHistory(Modifier.padding(padding))
            else -> TranscriptList(transcripts, padding, onOpen)
        }
    }
}

@Composable
private fun TranscriptList(transcripts: List<Transcript>, padding: PaddingValues, onOpen: (Transcript) -> Unit) {
    val motion = MaterialTheme.motionScheme
    val direction = LocalLayoutDirection.current
    LazyColumn(
        contentPadding = PaddingValues(
            start = padding.calculateStartPadding(direction) + 16.dp,
            end = padding.calculateEndPadding(direction) + 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            // Room for the last card to scroll clear of the button.
            bottom = padding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(transcripts, key = { it.id }) { transcript ->
            TranscriptCard(
                transcript = transcript,
                onClick = { onOpen(transcript) },
                modifier = Modifier.animateItem(
                    fadeInSpec = motion.defaultEffectsSpec(),
                    placementSpec = motion.defaultSpatialSpec(),
                    fadeOutSpec = motion.fastEffectsSpec(),
                ),
            )
        }
    }
}

@Composable
private fun TranscriptCard(transcript: Transcript, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = transcript.text,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = details(transcript),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** When it was transcribed, how long the recording was, and in which language. */
private fun details(transcript: Transcript): String = listOfNotNull(
    DateUtils.getRelativeTimeSpanString(
        transcript.createdAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ),
    transcript.durationMs?.let { DateUtils.formatElapsedTime(it / 1000) },
    transcript.language?.let { Languages.displayName(it) },
).joinToString(" · ")

@Composable
private fun EmptyHistory(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 320.dp)) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.size(16.dp))
            Text(stringResource(R.string.history_empty_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.history_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
