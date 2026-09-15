package com.sipoe.softphone.ui.account

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sipoe.softphone.data.AccountSettings
import com.sipoe.softphone.data.AccountStore
import com.sipoe.softphone.diag.DiagLog
import com.sipoe.softphone.service.SipForegroundService
import com.sipoe.softphone.sip.SipCoreManager
import com.sipoe.softphone.sip.SipRegistrationState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    fun save(settings: AccountSettings) {
        viewModelScope.launch {
            runCatching {
                store.save(settings)
                val stored = store.flow.first()
                if (!stored.isComplete) {
                    DiagLog.e(TAG, "Password storage failed, stored account incomplete")
                    _messages.emit("账号保存异常:密码存储失败,请重试或重启应用")
                    return@launch
                }
                DiagLog.i(TAG, "Account saved: ${stored.identityUri} -> ${stored.serverAddress}")
                SipForegroundService.start(getApplication())
                _messages.emit("已保存,正在注册…")
            }.onFailure {
                DiagLog.e(TAG, "Save account failed", it)
                _messages.emit("保存失败:${it.message}")
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
