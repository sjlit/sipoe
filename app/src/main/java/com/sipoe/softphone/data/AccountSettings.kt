package com.sipoe.softphone.data

import androidx.annotation.StringRes
import com.sipoe.softphone.R
import kotlinx.serialization.Serializable

enum class SipTransport(val label: String) {
    UDP("UDP"),
    TCP("TCP"),
    TLS("TLS"),
}

/**
 * 一个 SIP 账号的配置。
 *
 * [id] 为空表示这条配置还没有落库(新建表单),由 [AccountStore] 在保存时补上 UUID;
 * [name] 是可选的备注名,只用于列表展示,不参与校验。
 */
@Serializable
data class AccountSettings(
    val id: String = "",
    val name: String = "",
    val domain: String = "",
    val proxy: String = "",
    val username: String = "",
    val password: String = "",
    val transport: SipTransport = SipTransport.UDP,
    val port: Int = 0,
    val stunEnabled: Boolean = true,
    val stunServer: String = DEFAULT_STUN_SERVER,
    val registerExpires: Int = DEFAULT_EXPIRES,
) {
    val isComplete: Boolean
        get() = domain.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    /** 列表展示名:优先备注名,否则退回账号地址。 */
    val label: String
        get() = name.trim().ifEmpty { identityUri }

    val identityUri: String
        get() = "sip:${username.trim()}@${domain.trim()}"

    val serverHost: String
        get() = proxy.trim().takeIf { it.isNotEmpty() }
            ?.let { parseHostPort(it).first }
            ?: domain.trim()

    val serverPort: Int
        get() {
            proxy.trim().takeIf { it.isNotEmpty() }?.let { raw ->
                parseHostPort(raw).second?.let { if (it in 1..65535) return it }
            }
            if (port in 1..65535) return port
            return if (transport == SipTransport.TLS) 5061 else 5060
        }

    val serverAddress: String
        get() = "sip:$serverHost:$serverPort"

    @StringRes
    fun validate(): Int? = when {
        domain.isBlank() -> R.string.validation_domain_required
        domain.contains(' ') -> R.string.validation_domain_space
        username.isBlank() -> R.string.validation_username_required
        username.contains('@') -> R.string.validation_username_at
        username.contains(' ') -> R.string.validation_username_space
        password.isBlank() -> R.string.validation_password_required
        serverHost.isBlank() -> R.string.validation_proxy_invalid
        port !in 0..65535 -> R.string.validation_port_range
        registerExpires !in 60..3600 -> R.string.validation_expires_range
        else -> null
    }

    fun normalized(): AccountSettings = copy(
        name = name.trim(),
        domain = stripScheme(domain),
        proxy = proxy.trim(),
        username = username.trim(),
        password = password,
    )

    companion object {
        const val DEFAULT_STUN_SERVER = "stun.l.google.com:19302"
        const val DEFAULT_EXPIRES = 300

        fun stripScheme(raw: String): String {
            var value = raw.trim()
            if (value.startsWith("sips:", ignoreCase = true)) {
                value = value.substring(5)
            } else if (value.startsWith("sip:", ignoreCase = true)) {
                value = value.substring(4)
            }
            return value.trim()
        }

        private fun parseHostPort(raw: String): Pair<String, Int?> {
            var value = raw.trim()
            if (value.startsWith("sip:", ignoreCase = true) || value.startsWith("sips:", ignoreCase = true)) {
                value = value.substringAfter(':')
            }
            value = value.substringBefore(';')
            val colon = value.lastIndexOf(':')
            val hasBrackets = value.contains('[')
            return if (colon > 0 && !hasBrackets) {
                val host = value.substring(0, colon)
                val port = value.substring(colon + 1).toIntOrNull()
                if (port != null) host to port else value to null
            } else {
                value to null
            }
        }
    }
}
