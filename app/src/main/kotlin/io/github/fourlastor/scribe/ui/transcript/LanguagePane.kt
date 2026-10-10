package io.github.fourlastor.scribe.ui.transcript

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fourlastor.scribe.R
import io.github.fourlastor.scribe.speech.Languages
import java.text.Collator

/** Picks the language to transcribe in. Selecting one calls [onSelected], with null for automatic. */
@Composable
fun LanguagePane(
    choice: LanguageChoice,
    selected: String?,
    onSelected: (String?) -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val languages = remember(choice.all) {
        val collator = Collator.getInstance()
        choice.all.map { it to Languages.displayName(it) }.sortedWith { a, b -> collator.compare(a.second, b.second) }
    }
    val names = remember(languages) { languages.toMap() }
    val matches = remember(languages, query) {
        languages.filter { (code, name) -> name.contains(query.trim(), ignoreCase = true) || code == query.trim() }
    }
    val chosen = selected.takeUnless { choice.automatic }

    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(stringResource(R.string.language_title), style = MaterialTheme.typography.titleLarge)
        }
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.language_search)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.weight(1f)) {
            if (query.isBlank()) {
                if (choice.canDetect) {
                    item(key = "automatic") {
                        LanguageRow(
                            name = stringResource(R.string.language_automatic),
                            selected = choice.automatic,
                            onClick = { onSelected(null) },
                            automatic = true,
                        )
                    }
                }
                if (choice.suggested.isNotEmpty()) {
                    item(key = "suggested") { SectionHeader(stringResource(R.string.language_suggested)) }
                    items(choice.suggested, key = { "suggested-$it" }) { code ->
                        LanguageRow(names[code] ?: code, selected = code == chosen, onClick = { onSelected(code) })
                    }
                }
                item(key = "all") { SectionHeader(stringResource(R.string.language_all)) }
            }
            items(matches, key = { it.first }) { (code, name) ->
                LanguageRow(name, selected = code == chosen, onClick = { onSelected(code) })
            }
            if (matches.isEmpty()) {
                item(key = "none") {
                    Text(
                        text = stringResource(R.string.language_none_found, query.trim()),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun LanguageRow(name: String, selected: Boolean, onClick: () -> Unit, automatic: Boolean = false) {
    ListItem(
        selected = selected,
        onClick = onClick,
        leadingContent = if (automatic) {
            { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) }
        } else {
            null
        },
        trailingContent = if (selected) {
            { Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.language_selected)) }
        } else {
            null
        },
        // The sheet is the surface, rows only tint when selected.
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        contentPadding = ListItemDefaults.ContentPadding,
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        Text(name)
    }
}
