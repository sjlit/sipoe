package com.sipoe.softphone.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sipoe.softphone.data.AccountStore
import com.sipoe.softphone.diag.DiagLog
import com.sipoe.softphone.sip.CallController
import com.sipoe.softphone.sip.CallStatus
import com.sipoe.softphone.sip.CallUiState
import com.sipoe.softphone.sip.SipCoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SipForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observing = false
    private var foregroundTypes: Int? = null
    private var notifiedIncomingNumber: String? = null
    private val ringer by lazy { InAppRinger(this) }

    override fun onCreate() {
        super.onCreate()
        DiagLog.i(TAG, "Service onCreate")
        SipCoreManager.initialize(applicationContext)
        NotificationHelper.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DiagLog.i(TAG, "onStartCommand action=${intent?.action} startId=$startId")
        val core = SipCoreManager.ensureStarted()
        DiagLog.i(TAG, "Core ready: ${core != null}")
        when (intent?.action) {
            ACTION_ANSWER -> {
                if (CallController.state.value != null) {
                    CallController.accept()
                } else {
                    DiagLog.w(TAG, "Answer ignored: no incoming call")
                }
            }

            ACTION_DECLINE -> CallController.decline()
            ACTION_HANGUP -> CallController.hangup()
        }
        refreshNotification(CallController.state.value)
        startObserving()
        return START_STICKY
    }

    private fun startObserving() {
        if (observing) return
        observing = true

        scope.launch {
            AccountStore(applicationContext).flow.collect { settings ->
                DiagLog.i(
                    TAG,
                    "Account emission complete=${settings.isComplete} " +
                        "identity=${settings.identityUri} server=${settings.serverAddress}",
                )
                if (settings.isComplete) {
                    SipCoreManager.applyAccount(settings)
                } else {
                    SipCoreManager.clearAccount()
                    DiagLog.w(TAG, "Account incomplete, stopping service")
                    stopSelf()
                }
            }
        }

        scope.launch {
            combine(SipCoreManager.registration, CallController.state) { registration, call ->
                registration to call
            }.collect { (_, call) ->
                if (call?.status == CallStatus.Incoming) {
                    if (notifiedIncomingNumber != call.number) {
                        notifiedIncomingNumber = call.number
                        DiagLog.i(TAG, "Incoming call from ${call.number}")
                        NotificationHelper.showIncomingCall(
                            this@SipForegroundService,
                            call.number,
                            callActionIntent(ACTION_ANSWER),
                            callActionIntent(ACTION_DECLINE),
                        )
                        if (!NotificationManagerCompat.from(this@SipForegroundService).areNotificationsEnabled()) {
                            DiagLog.w(TAG, "Notifications disabled, using in-app ringer")
                            ringer.start()
                        }
                    }
                } else if (notifiedIncomingNumber != null) {
                    notifiedIncomingNumber = null
                    NotificationHelper.cancelIncomingCall(this@SipForegroundService)
                    ringer.stop()
                }
                refreshNotification(call)
            }
        }
    }

    @android.annotation.SuppressLint("InlinedApi")
    private fun refreshNotification(call: CallUiState?) {
        val inCall = call != null && call.status != CallStatus.Ended && call.status != CallStatus.Error
        val types = if (inCall) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        }
        val notification = NotificationHelper.buildStatusNotification(
            this,
            SipCoreManager.registration.value,
            call,
            callActionIntent(ACTION_HANGUP),
        )
        if (foregroundTypes != types) {
            foregroundTypes = types
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val started = runCatching {
                    startForeground(NotificationHelper.STATUS_NOTIFICATION_ID, notification, types)
                }
                if (started.isFailure) {
                    DiagLog.e(TAG, "startForeground failed for types=$types", started.exceptionOrNull())
                }
                if (started.isFailure && types != ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) {
                    runCatching {
                        startForeground(
                            NotificationHelper.STATUS_NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                        )
                    }.onFailure {
                        DiagLog.e(TAG, "startForeground fallback failed", it)
                    }
                }
            } else {
                startForeground(NotificationHelper.STATUS_NOTIFICATION_ID, notification)
            }
        } else {
            val manager = getSystemService(android.app.NotificationManager::class.java) ?: return
            manager.notify(NotificationHelper.STATUS_NOTIFICATION_ID, notification)
        }
    }

    private fun callActionIntent(action: String): PendingIntent {
        val intent = Intent(this, SipForegroundService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    override fun onDestroy() {
        DiagLog.i(TAG, "Service onDestroy")
        ringer.stop()
        NotificationHelper.cancelIncomingCall(this)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "SipService"
        private const val ACTION_ANSWER = "com.sipoe.softphone.action.ANSWER"
        private const val ACTION_DECLINE = "com.sipoe.softphone.action.DECLINE"
        private const val ACTION_HANGUP = "com.sipoe.softphone.action.HANGUP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, SipForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SipForegroundService::class.java))
        }
    }
}
