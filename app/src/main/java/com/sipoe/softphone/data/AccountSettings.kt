package com.sipoe.softphone.data

enum class SipTransport(val label: String) {
    UDP("UDP"),
    TCP("TCP"),
    TLS("TLS"),
}

data class AccountSettings(
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

    fun validate(): String? = when {
        domain.isBlank() -> "请填写域"
        domain.contains(' ') -> "域不能包含空格"
        username.isBlank() -> "请填写用户名"
        username.contains('@') -> "用户名请勿包含 @,域请填在域名段"
        username.contains(' ') -> "用户名不能包含空格"
        password.isBlank() -> "请填写密码"
        serverHost.isBlank() -> "代理地址不合法"
        port !in 0..65535 -> "端口需在 1-65535 之间"
        registerExpires !in 60..3600 -> "注册有效期需在 60-3600 秒之间"
        else -> null
    }

    fun normalized(): AccountSettings = copy(
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
