package com.sipoe.softphone.diag

import android.content.Context
import com.sipoe.softphone.R
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

    fun run(context: Context, serverHost: String, serverPort: Int, timeoutMs: Int = 2500): Report {
        val lines = mutableListOf<String>()
        var restricted = false

        runCatching { InetAddress.getAllByName(serverHost).joinToString { it.hostAddress.orEmpty() } }
            .onSuccess { lines += context.getString(R.string.selftest_dns_ok, it) }
            .onFailure {
                lines += context.getString(R.string.selftest_dns_failed, it.message.orEmpty())
            }

        runCatching { DatagramSocket(0).use { } }
            .onSuccess { lines += context.getString(R.string.selftest_bind_random_ok) }
            .onFailure {
                if (isPermissionError(it)) restricted = true
                lines += context.getString(R.string.selftest_bind_random_failed, it.message.orEmpty())
            }

        runCatching { DatagramSocket(5060).use { } }
            .onSuccess { lines += context.getString(R.string.selftest_bind_5060_ok) }
            .onFailure {
                if (isPermissionError(it)) restricted = true
                lines += context.getString(R.string.selftest_bind_5060_failed, it.message.orEmpty())
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
                lines += context.getString(
                    R.string.selftest_probe_ok,
                    serverHost,
                    serverPort,
                    firstLine,
                )
            }
        } catch (timeout: SocketTimeoutException) {
            lines += context.getString(
                R.string.selftest_probe_timeout,
                serverHost,
                serverPort,
                timeoutMs / 1000,
            )
        } catch (error: Throwable) {
            if (isPermissionError(error)) restricted = true
            lines += context.getString(
                R.string.selftest_probe_failed,
                serverHost,
                serverPort,
                error.message.orEmpty(),
            )
        }

        if (restricted) {
            lines += context.getString(R.string.selftest_restricted)
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
