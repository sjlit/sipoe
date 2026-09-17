package com.sipoe.softphone

import android.app.Application
import android.content.Context
import android.util.Log
import com.sipoe.softphone.data.AccountStore
import com.sipoe.softphone.data.LocaleSupport
import com.sipoe.softphone.service.SipForegroundService
import com.sipoe.softphone.sip.SipCoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SipoeApp : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleSupport.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        LocaleSupport.applyToApplication(this)
        SipCoreManager.initialize(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val active = runCatching { AccountStore(this@SipoeApp).flow.first().active }.getOrNull()
                ?: return@launch
            if (active.isComplete) {
                runCatching { SipForegroundService.start(this@SipoeApp) }
                    .onFailure { Log.e(TAG, "Unable to auto start SIP service", it) }
            }
        }
    }

    private companion object {
        const val TAG = "SipoeApp"
    }
}
