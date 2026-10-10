package io.github.fourlastor.scribe.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.fourlastor.scribe.ScribeApp
import io.github.fourlastor.scribe.data.Transcript
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = (application as ScribeApp).transcripts

    /** Newest first. Null until the first read from disk. */
    val transcripts: StateFlow<List<Transcript>?> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(transcript: Transcript) {
        viewModelScope.launch { dao.delete(transcript) }
    }

    /** Puts a deleted transcript back where it was. */
    fun restore(transcript: Transcript) {
        viewModelScope.launch { dao.save(transcript) }
    }
}
