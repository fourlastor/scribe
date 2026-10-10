package io.github.fourlastor.scribe.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.languageStore: DataStore<Preferences> by preferencesDataStore("languages")

/** Remembers the languages the user picked by hand, most recent first. */
class LanguagePreferences(private val context: Context) {

    val recent: Flow<List<String>> = context.languageStore.data.map { preferences ->
        preferences[RECENT]?.split(',')?.filter { it.isNotEmpty() }.orEmpty()
    }

    suspend fun remember(language: String) {
        context.languageStore.edit { preferences ->
            val previous = preferences[RECENT]?.split(',').orEmpty()
            preferences[RECENT] = (listOf(language) + (previous - language)).take(MAX_RECENT).joinToString(",")
        }
    }

    private companion object {
        val RECENT = stringPreferencesKey("recent")
        const val MAX_RECENT = 5
    }
}
