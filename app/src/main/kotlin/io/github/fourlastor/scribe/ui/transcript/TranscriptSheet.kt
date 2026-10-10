package io.github.fourlastor.scribe.ui.transcript

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.fourlastor.scribe.R
import io.github.fourlastor.scribe.speech.Languages
import io.github.fourlastor.scribe.ui.theme.rememberIndicatorVisible
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

private enum class Pane { Transcript, Languages }

/**
 * The transcript of one recording, in a bottom sheet over whatever is behind it. The same sheet
 * serves a recording that is still being transcribed and an entry opened from history.
 *
 * @param onDismissed called once the sheet has finished closing.
 * @param onDelete if set, saved transcripts can be deleted, and this is called after the sheet
 *   has closed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptSheet(
    state: TranscriptUiState,
    onLanguageSelected: (String?) -> Unit,
    onDismissed: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    val motion = MaterialTheme.motionScheme

    ModalBottomSheet(onDismissRequest = onDismissed, sheetState = sheetState) {
        var pane by rememberSaveable { mutableStateOf(Pane.Transcript) }
        val paneState = remember { SeekableTransitionState(pane) }
        LaunchedEffect(pane) { paneState.animateTo(pane) }

        // Back from the language list returns to the transcript, following the gesture. On the
        // transcript this is off, and the sheet's own back handling dismisses it.
        PredictiveBackHandler(enabled = pane == Pane.Languages) { progress ->
            try {
                progress.collect { paneState.seekTo(it.progress, Pane.Transcript) }
                pane = Pane.Transcript
            } catch (e: CancellationException) {
                scope.launch { paneState.animateTo(Pane.Languages) }
                throw e
            }
        }

        // The language list is a level below the transcript, so it comes in from the trailing
        // edge and goes back out the same way.
        val trailing = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
        rememberTransition(paneState, label = "pane").AnimatedContent(
            transitionSpec = {
                val towards = if (targetState == Pane.Languages) trailing else -trailing
                val enter = slideInHorizontally(motion.defaultSpatialSpec()) { towards * it / 4 } +
                    fadeIn(motion.defaultEffectsSpec())
                val exit = slideOutHorizontally(motion.defaultSpatialSpec()) { -towards * it / 4 } +
                    fadeOut(motion.defaultEffectsSpec())
                enter togetherWith exit using SizeTransform { _, _ -> motion.defaultSpatialSpec() }
            },
        ) { target ->
            when (target) {
                Pane.Transcript -> TranscriptPane(
                    state = state,
                    onChangeLanguage = { pane = Pane.Languages },
                    onDelete = onDelete?.let { delete ->
                        // Leave the way the sheet came in before the entry disappears behind it.
                        { scope.launch { sheetState.hide() }.invokeOnCompletion { delete() } }
                    },
                )

                Pane.Languages -> state.languageChoice?.let { choice ->
                    LanguagePane(
                        choice = choice,
                        selected = state.language,
                        onSelected = {
                            onLanguageSelected(it)
                            pane = Pane.Transcript
                        },
                        onBack = { pane = Pane.Transcript },
                    )
                }
            }
        }
    }
}

@Composable
private fun TranscriptPane(
    state: TranscriptUiState,
    onChangeLanguage: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.transcript_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            LanguageLabel(state, onChangeLanguage)
        }
        Spacer(Modifier.size(12.dp))
        TranscriptBody(state, Modifier.weight(1f, fill = false))
        TranscriptProgress(state)
        Spacer(Modifier.size(16.dp))
        TranscriptActions(state, onDelete)
        Spacer(Modifier.size(16.dp))
    }
}

@Composable
private fun LanguageLabel(state: TranscriptUiState, onChange: () -> Unit) {
    val choice = state.languageChoice
    val name = state.language?.let { Languages.displayName(it) }
    // Audio that could not be read cannot be transcribed in another language either.
    if (state.phase == Phase.Failed) return
    if (choice == null) {
        // A saved transcript: its language is a fact, not a control.
        if (name != null) {
            Text(name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val change = stringResource(R.string.language_change)
    val detected = stringResource(R.string.language_detected)
    AssistChip(
        onClick = onChange,
        label = {
            // Not every engine says which language it heard.
            val placeholder = if (choice.automatic) R.string.language_automatic_short else R.string.language_title
            Text(name ?: stringResource(placeholder))
        },
        leadingIcon = {
            Icon(
                imageVector = if (choice.automatic) Icons.Rounded.AutoAwesome else Icons.Rounded.Translate,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
        trailingIcon = {
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize))
        },
        modifier = Modifier.semantics {
            contentDescription = change
            if (name != null && choice.automatic) stateDescription = detected
        },
    )
}

private enum class BodyKind { Status, Text, Message }

/** What the body shows, kept apart from [TranscriptUiState] so each kind can fade as a whole. */
private data class Body(val run: Int, val kind: BodyKind)

@Composable
private fun TranscriptBody(state: TranscriptUiState, modifier: Modifier = Modifier) {
    val motion = MaterialTheme.motionScheme
    val body = Body(
        run = state.run,
        kind = when {
            state.text.isNotEmpty() -> BodyKind.Text
            state.phase.isRunning -> BodyKind.Status
            else -> BodyKind.Message
        },
    )
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            // The sheet grows with the transcript. Animating that keeps new lines from making it jump.
            .animateContentSize(motion.defaultSpatialSpec())
            .verticalScroll(rememberScrollState()),
    ) {
        Crossfade(targetState = body, animationSpec = motion.defaultEffectsSpec(), label = "body") { shown ->
            // While fading out, keep showing what was there, not the state that replaced it.
            val content = remember { mutableStateOf(state) }.apply { if (shown == body) value = state }.value
            when (shown.kind) {
                BodyKind.Text -> SelectionContainer {
                    Text(content.text, style = MaterialTheme.typography.bodyLarge)
                }

                BodyKind.Status, BodyKind.Message -> Text(
                    text = stringResource(
                        when (content.phase) {
                            Phase.LoadingModel -> R.string.transcript_loading_model
                            Phase.Transcribing -> R.string.transcript_transcribing
                            Phase.Failed -> R.string.transcript_failed
                            Phase.NoWords -> R.string.transcript_no_words
                            Phase.NoSpeech, Phase.Done -> R.string.transcript_no_speech
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TranscriptProgress(state: TranscriptUiState) {
    val motion = MaterialTheme.motionScheme
    val indicatorVisible = rememberIndicatorVisible(loading = state.phase.isRunning)
    // The row holds its place from the start, so the indicator arriving late does not move the sheet.
    AnimatedVisibility(
        visible = state.phase.isRunning || indicatorVisible,
        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
    ) {
        val alpha by animateFloatAsState(
            targetValue = if (indicatorVisible) 1f else 0f,
            animationSpec = motion.defaultEffectsSpec(),
            label = "indicator",
        )
        val description = stringResource(R.string.transcript_progress)
        val indicator = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .graphicsLayer { this.alpha = alpha }
            .semantics { contentDescription = description }
        val progress = state.progress
        if (progress == null) {
            LinearProgressIndicator(indicator)
        } else {
            val animated by animateFloatAsState(
                targetValue = progress,
                animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                label = "progress",
            )
            LinearProgressIndicator(progress = { animated }, modifier = indicator)
        }
    }
}

@Composable
private fun TranscriptActions(state: TranscriptUiState, onDelete: (() -> Unit)?) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val clipLabel = stringResource(R.string.clipboard_label)
    val hasText = state.text.isNotEmpty()

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onDelete != null && state.saved != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        }
        Spacer(Modifier.weight(1f))
        FilledTonalButton(onClick = { share(context, state.text) }, enabled = hasText) {
            Icon(Icons.Rounded.Share, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.action_share))
        }
        Button(
            onClick = {
                // Android shows its own confirmation when the clipboard changes.
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(clipLabel, state.text))) }
            },
            enabled = hasText,
        ) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.action_copy))
        }
    }
}

private fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}
