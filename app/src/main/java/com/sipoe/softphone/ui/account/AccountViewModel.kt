package com.sipoe.softphone.ui.account

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sipoe.softphone.R
import com.sipoe.softphone.data.AccountSettings
import com.sipoe.softphone.data.AccountState
import com.sipoe.softphone.data.AccountStore
import com.sipoe.softphone.diag.DiagLog
import com.sipoe.softphone.service.SipForegroundService
import com.sipoe.softphone.sip.CallController
import com.sipoe.softphone.sip.CallStatus
import com.sipoe.softphone.sip.CallUiState
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 账号页的一次性事件。 */
sealed interface AccountEvent {
    /** 保存成功,[activated] 表示这条账号是否成为当前激活账号。 */
    data class Saved(val id: String, val activated: Boolean) : AccountEvent

    data class Failed(val message: String) : AccountEvent

    data class Notice(val message: String) : AccountEvent
}

class AccountViewModel(application: Application) : AndroidViewModel(application) {

    private val store = AccountStore(application)

    val accounts: StateFlow<AccountState> = store.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountState())

    val registration: StateFlow<SipRegistrationState> = SipCoreManager.registration

    /** 供界面禁用切换入口;真正的拦截以 [rejectWhileInCall] 的实时判断为准。 */
    val inCall: StateFlow<Boolean> = CallController.state
        .map { it.isOngoing() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _events = MutableSharedFlow<AccountEvent>()
    val events: SharedFlow<AccountEvent> = _events.asSharedFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /**
     * 保存账号。已有其它账号处于激活状态时只保存、不抢占激活位;
     * 编辑当前激活账号或当前无激活账号时,保存后立即注册。
     */
    fun save(settings: AccountSettings) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val state = store.flow.first()
                val editingActive = settings.id.isNotBlank() && state.activeId == settings.id
                val shouldActivate = editingActive || state.active == null
                runCatching {
                    val stored = store.upsert(settings)
                    val persisted = store.flow.first().find(stored.id)
                    if (persisted == null || !persisted.isComplete) {
                        DiagLog.e(TAG, "Password storage failed, stored account incomplete")
                        _events.emit(AccountEvent.Failed(app.getString(R.string.account_save_error_storage)))
                        return@runCatching
                    }
                    DiagLog.i(
                        TAG,
                        "Account saved: ${persisted.identityUri} -> ${persisted.serverAddress} " +
                            "activate=$shouldActivate",
                    )
                    if (shouldActivate) {
                        if (!editingActive) store.setActive(stored.id)
                        SipForegroundService.start(app)
                    }
                    _events.emit(AccountEvent.Saved(stored.id, shouldActivate))
                }.onFailure {
                    DiagLog.e(TAG, "Save account failed", it)
                    _events.emit(
                        AccountEvent.Failed(app.getString(R.string.account_save_error, it.message.orEmpty())),
                    )
                }
            } finally {
                _busy.value = false
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            if (rejectWhileInCall()) return@launch
            val wasActive = store.flow.first().activeId == id
            store.delete(id)
            if (wasActive) {
                SipCoreManager.clearAccount()
                SipForegroundService.stop(app)
            }
            DiagLog.i(TAG, "Account deleted active=$wasActive")
            _events.emit(AccountEvent.Notice(app.getString(R.string.account_deleted)))
        }
    }

    fun activate(id: String) {
        viewModelScope.launch {
            if (rejectWhileInCall()) return@launch
            if (store.flow.first().activeId == id) return@launch
            DiagLog.i(TAG, "Activating account")
            store.setActive(id)
            SipForegroundService.start(app)
        }
    }

    fun deactivate() {
        viewModelScope.launch {
            if (rejectWhileInCall()) return@launch
            store.setActive(null)
            SipCoreManager.clearAccount()
            SipForegroundService.stop(app)
            DiagLog.i(TAG, "Active account deactivated")
            _events.emit(AccountEvent.Notice(app.getString(R.string.account_deactivated)))
        }
    }

    /** 通话中切换会让内核 clearAccounts 直接掐断通话,因此一律拒绝。 */
    private suspend fun rejectWhileInCall(): Boolean {
        if (!CallController.state.value.isOngoing()) return false
        DiagLog.w(TAG, "Account switch rejected: call in progress")
        _events.emit(AccountEvent.Notice(app.getString(R.string.account_switch_blocked)))
        return true
    }

    private val app: Application
        get() = getApplication<Application>()

    private fun CallUiState?.isOngoing(): Boolean = when (this?.status) {
        null, CallStatus.Ended, CallStatus.Error -> false
        else -> true
    }

    private companion object {
        const val TAG = "AccountViewModel"
    }
}