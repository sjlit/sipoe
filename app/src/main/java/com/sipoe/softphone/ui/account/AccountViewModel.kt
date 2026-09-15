package com.sipoe.softphone.ui.account

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sipoe.softphone.R
import com.sipoe.softphone.data.AccountSettings
import com.sipoe.softphone.data.AccountStore
import com.sipoe.softphone.diag.DiagLog
import com.sipoe.softphone.service.SipForegroundService
import com.sipoe.softphone.sip.SipCoreManager
import com.sipoe.softphone.sip.SipRegistrationState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountViewModel(application: Application) : AndroidViewModel(application) {

    private val store = AccountStore(application)

    val saved: StateFlow<AccountSettings?> = store.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val registration: StateFlow<SipRegistrationState> = SipCoreManager.registration

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    fun save(settings: AccountSettings) {
        if (_saving.value) return
        _saving.value = true
        viewModelScope.launch {
            try {
                runCatching {
                    store.save(settings)
                    val stored = store.flow.first()
                    if (!stored.isComplete) {
                        DiagLog.e(TAG, "Password storage failed, stored account incomplete")
                        _messages.emit(getApplication<Application>().getString(R.string.account_save_error_storage))
                        return@runCatching
                    }
                    DiagLog.i(TAG, "Account saved: ${stored.identityUri} -> ${stored.serverAddress}")
                    SipForegroundService.start(getApplication())
                }.onFailure {
                    DiagLog.e(TAG, "Save account failed", it)
                    _messages.emit(
                        getApplication<Application>().getString(
                            R.string.account_save_error,
                            it.message.orEmpty(),
                        ),
                    )
                }
            } finally {
                _saving.value = false
            }
        }
    }

    fun clear() {
        viewModelScope.launch {
            store.clear()
            SipCoreManager.clearAccount()
            SipForegroundService.stop(getApplication())
        }
    }

    private companion object {
        const val TAG = "AccountViewModel"
    }
}
