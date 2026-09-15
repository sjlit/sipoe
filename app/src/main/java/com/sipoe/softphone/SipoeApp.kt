package com.sipoe.softphone

import android.app.Application
import android.util.Log
import com.sipoe.softphone.data.AccountStore
import com.sipoe.softphone.service.SipForegroundService
import com.sipoe.softphone.sip.SipCoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SipoeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        SipCoreManager.initialize(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val settings = runCatching { AccountStore(this@SipoeApp).flow.first() }.getOrNull()
                ?: return@launch
            if (settings.isComplete) {
                runCatching { SipForegroundService.start(this@SipoeApp) }
                    .onFailure { Log.e(TAG, "Unable to auto start SIP service", it) }
            }
        }
    }

    private companion object {
        const val TAG = "SipoeApp"
    }
}
