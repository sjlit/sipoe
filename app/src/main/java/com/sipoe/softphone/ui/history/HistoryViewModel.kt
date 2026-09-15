package com.sipoe.softphone.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sipoe.softphone.data.CallLogEntry
import com.sipoe.softphone.data.CallLogStore
import com.sipoe.softphone.sip.CallController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val store = CallLogStore(application)

    val entries: StateFlow<List<CallLogEntry>> = store.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun redial(entry: CallLogEntry) {
        CallController.dial(entry.number)
    }

    fun remove(entry: CallLogEntry) {
        viewModelScope.launch { store.remove(entry.id) }
    }

    fun restore(entry: CallLogEntry) {
        viewModelScope.launch { store.restore(entry) }
    }

    fun clearAll() {
        viewModelScope.launch { store.clear() }
    }
}
