package com.sipoe.softphone.sip

import android.content.Context
import com.sipoe.softphone.R

fun mapRegistrationError(context: Context, raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val text = raw.lowercase()
    return when {
        text.contains("401") || text.contains("403") ||
            text.contains("unauthorized") || text.contains("forbidden") ->
            context.getString(R.string.error_auth_failed)

        text.contains("404") || text.contains("not found") ->
            context.getString(R.string.error_account_not_found)

        text.contains("408") || text.contains("timeout") ->
            context.getString(R.string.error_server_timeout)

        text.contains("500") -> context.getString(R.string.error_server_internal)
        text.contains("502") || text.contains("bad gateway") ->
            context.getString(R.string.error_bad_gateway)

        text.contains("503") || text.contains("unavailable") ->
            context.getString(R.string.error_service_unavailable)

        text.contains("407") || text.contains("proxy authentication") ->
            context.getString(R.string.error_proxy_auth)

        text.contains("dns") || text.contains("host") -> context.getString(R.string.error_dns)
        text.contains("ioerror") || text.contains("io error") -> context.getString(R.string.error_io)
        text.contains("unreachable") -> context.getString(R.string.error_unreachable)
        text.contains("transport") || text.contains("network") ->
            context.getString(R.string.error_transport)

        text.contains("registration failed") ->
            context.getString(R.string.error_registration_failed)

        else -> raw
    }
}

fun mapCallError(context: Context, raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val text = raw.lowercase()
    return when {
        text.contains("busy") -> context.getString(R.string.error_busy)
        text.contains("declined") || text.contains("486") -> context.getString(R.string.error_declined)
        text.contains("not found") || text.contains("404") ->
            context.getString(R.string.error_number_not_found)

        text.contains("timeout") || text.contains("408") -> context.getString(R.string.error_call_timeout)
        text.contains("forbidden") || text.contains("403") -> context.getString(R.string.error_forbidden)
        text.contains("unavailable") || text.contains("503") ->
            context.getString(R.string.error_service_down)

        text.contains("unauthorized") || text.contains("401") ->
            context.getString(R.string.error_unauthorized)

        else -> raw
    }
}
