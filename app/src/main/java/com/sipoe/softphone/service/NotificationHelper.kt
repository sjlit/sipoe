package com.sipoe.softphone.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.sipoe.softphone.MainActivity
import com.sipoe.softphone.R
import com.sipoe.softphone.sip.CallStatus
import com.sipoe.softphone.sip.CallUiState
import com.sipoe.softphone.sip.SipRegistrationState
import com.sipoe.softphone.sip.displayLabelRes
import com.sipoe.softphone.sip.labelRes

object NotificationHelper {
    const val CHANNEL_STATUS = "sip_status"
    const val CHANNEL_CALL = "sip_call"
    const val STATUS_NOTIFICATION_ID = 1001
    const val INCOMING_NOTIFICATION_ID = 1002

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val statusChannel = NotificationChannel(
            CHANNEL_STATUS,
            context.getString(R.string.notification_channel_status),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_status_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(statusChannel)

        val callChannel = NotificationChannel(
            CHANNEL_CALL,
            context.getString(R.string.notification_channel_call),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_call_desc)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 800)
        }
        manager.createNotificationChannel(callChannel)
    }

    fun buildStatusNotification(
        context: Context,
        state: SipRegistrationState,
        call: CallUiState? = null,
        hangupIntent: PendingIntent? = null,
    ): Notification {
        val activeCall = call?.takeIf {
            it.status == CallStatus.Connecting ||
                it.status == CallStatus.Connected ||
                it.status == CallStatus.Ending
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_sip)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(openAppIntent(context))
        if (activeCall != null && hangupIntent != null) {
            val person = Person.Builder().setName(activeCall.number).build()
            builder
                .setStyle(NotificationCompat.CallStyle.forOngoingCall(person, hangupIntent))
                .setContentTitle(activeCall.number)
                .setContentText(context.getString(activeCall.displayLabelRes))
                .setCategory(NotificationCompat.CATEGORY_CALL)
        } else {
            builder
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(
                    if (call != null) {
                        "${context.getString(call.displayLabelRes)} · ${call.number}"
                    } else {
                        // 带上当前激活账号,多账号时一眼能看出在用哪个身份
                        listOfNotNull(
                            context.getString(state.status.labelRes),
                            state.identity?.withoutSipScheme(),
                        ).joinToString(" · ")
                    },
                )
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
        }
        return builder.build()
    }

    fun showIncomingCall(
        context: Context,
        number: String,
        answerIntent: PendingIntent,
        declineIntent: PendingIntent,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val person = Person.Builder().setName(number).build()
        val notification = NotificationCompat.Builder(context, CHANNEL_CALL)
            .setSmallIcon(R.drawable.ic_stat_sip)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(person, declineIntent, answerIntent))
            .setContentTitle(number)
            .setContentText(context.getString(R.string.call_status_incoming))
            .setFullScreenIntent(openAppIntent(context), true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
        manager.notify(INCOMING_NOTIFICATION_ID, notification)
    }

    fun cancelIncomingCall(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.cancel(INCOMING_NOTIFICATION_ID)
    }

    private fun String.withoutSipScheme(): String = when {
        startsWith("sips:", ignoreCase = true) -> substring(5)
        startsWith("sip:", ignoreCase = true) -> substring(4)
        else -> this
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
