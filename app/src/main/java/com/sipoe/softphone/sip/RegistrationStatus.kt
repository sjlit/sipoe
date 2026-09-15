package com.sipoe.softphone.sip

import androidx.annotation.StringRes
import com.sipoe.softphone.R

enum class RegistrationStatus {
    Idle,
    InProgress,
    Registered,
    Failed,
    Cleared,
}

data class SipRegistrationState(
    val status: RegistrationStatus = RegistrationStatus.Idle,
    val message: String? = null,
    val rawMessage: String? = null,
    val errorCode: Int? = null,
    val errorPhrase: String? = null,
    val identity: String? = null,
    val networkRestricted: Boolean = false,
) {
    val isRegistered: Boolean
        get() = status == RegistrationStatus.Registered

    val errorDetail: String?
        get() {
            val parts = buildList {
                errorCode?.takeIf { it > 0 }?.let { add("SIP $it") }
                errorPhrase?.takeIf { it.isNotBlank() }?.let { add(it) }
                rawMessage?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
            return parts.distinct().joinToString(" ").takeIf { it.isNotBlank() }
        }
}

@get:StringRes
val RegistrationStatus.labelRes: Int
    get() = when (this) {
        RegistrationStatus.Idle -> R.string.status_idle
        RegistrationStatus.InProgress -> R.string.status_registering
        RegistrationStatus.Registered -> R.string.status_registered
        RegistrationStatus.Failed -> R.string.status_failed
        RegistrationStatus.Cleared -> R.string.status_cleared
    }
