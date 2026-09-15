package com.sipoe.softphone.diag

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

object NetworkSelfTest {

    data class Report(
        val lines: List<String>,
        val restricted: Boolean,
    )

    fun isLocalNetworkRestricted(): Boolean = try {
        DatagramSocket(0).use { }
        false
    } catch (error: Throwable) {
        isPermissionError(error)
    }

    fun run(serverHost: String, serverPort: Int, timeoutMs: Int = 2500): Report {
        val lines = mutableListOf<String>()
        var restricted = false

        runCatching { InetAddress.getAllByName(serverHost).joinToString { it.hostAddress.orEmpty() } }
            .onSuccess { lines += "DNS 解析: $it" }
            .onFailure { lines += "DNS 解析失败: ${it.message}" }

        runCatching { DatagramSocket(0).use { } }
            .onSuccess { lines += "本地 UDP 绑定(随机端口): 成功" }
            .onFailure {
                if (isPermissionError(it)) restricted = true
                lines += "本地 UDP 绑定(随机端口)失败: ${it.message}"
            }

        runCatching { DatagramSocket(5060).use { } }
            .onSuccess { lines += "本地 UDP 绑定(5060): 成功" }
            .onFailure {
                if (isPermissionError(it)) restricted = true
                lines += "本地 UDP 绑定(5060)失败: ${it.message}"
            }

        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                val address = InetAddress.getByName(serverHost)
                val payload = buildString {
                    append("OPTIONS sip:$serverHost SIP/2.0\r\n")
                    append("Via: SIP/2.0/UDP ${socket.localAddress?.hostAddress}:${socket.localPort};rport\r\n")
                    append("Max-Forwards: 70\r\n")
                    append("From: <sip:selftest@$serverHost>;tag=${System.currentTimeMillis()}\r\n")
                    append("To: <sip:$serverHost>\r\n")
                    append("Call-ID: selftest-${System.currentTimeMillis()}@sipoe\r\n")
                    append("CSeq: 1 OPTIONS\r\n")
                    append("User-Agent: Sipoe-SelfTest\r\n")
                    append("Content-Length: 0\r\n\r\n")
                }.toByteArray()
                socket.send(DatagramPacket(payload, payload.size, address, serverPort))
                val buffer = ByteArray(2048)
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                val firstLine = String(response.data, 0, response.length)
                    .lineSequence()
                    .firstOrNull()
                    .orEmpty()
                lines += "UDP 探测 $serverHost:$serverPort: 收到响应 → $firstLine"
            }
        } catch (timeout: SocketTimeoutException) {
            lines += "UDP 探测 $serverHost:$serverPort: 已发出但 ${timeoutMs / 1000} 秒内无响应" +
                "(服务器可能不回应 OPTIONS,或被中间设备丢弃)"
        } catch (error: Throwable) {
            if (isPermissionError(error)) restricted = true
            lines += "UDP 探测 $serverHost:$serverPort 失败: ${error.message}"
        }

        if (restricted) {
            lines += "结论: 系统拦截了应用联网(EPERM),REGISTER 无法发出"
        }

        return Report(lines, restricted)
    }

    private fun isPermissionError(error: Throwable): Boolean {
        val message = error.message.orEmpty()
        return error is SecurityException ||
            message.contains("not permitted", ignoreCase = true) ||
            message.contains("EPERM", ignoreCase = true)
    }
}
