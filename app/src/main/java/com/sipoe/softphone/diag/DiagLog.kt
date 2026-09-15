package com.sipoe.softphone.diag

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.linphone.core.Factory
import org.linphone.core.LogLevel
import org.linphone.core.LoggingService
import org.linphone.core.LoggingServiceListenerStub
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagLog {
    private const val MAX_LINES = 800
    private const val EMIT_INTERVAL_MS = 200L

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val buffer = ArrayDeque<String>()
    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    private var lastEmitAt = 0L
    private var linphoneLoggerAttached = false

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        append("I/$tag", message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        append("W/$tag", message)
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        Log.e(tag, message, error)
        val detail = if (error != null) {
            "$message: ${error.javaClass.simpleName} ${error.message.orEmpty()}"
        } else {
            message
        }
        append("E/$tag", detail)
    }

    fun refresh() {
        synchronized(buffer) {
            _entries.value = buffer.toList()
        }
    }

    fun clear() {
        synchronized(buffer) {
            buffer.clear()
            _entries.value = emptyList()
        }
    }

    fun snapshot(): String = synchronized(buffer) { buffer.joinToString("\n") }

    fun attachLinphoneLogger() {
        if (linphoneLoggerAttached) return
        linphoneLoggerAttached = true
        runCatching {
            val service = Factory.instance().loggingService
            service.addListener(object : LoggingServiceListenerStub() {
                override fun onLogMessageWritten(
                    service: LoggingService,
                    domain: String,
                    level: LogLevel,
                    message: String,
                ) {
                    if (level.ordinal >= LogLevel.Message.ordinal) {
                        append("lp/$domain", message)
                    }
                }
            })
            Log.i(TAG, "Linphone logging attached")
        }.onFailure {
            Log.e(TAG, "Unable to attach linphone logger", it)
        }
    }

    private fun append(source: String, message: String) {
        synchronized(buffer) {
            buffer.addLast("${timeFormat.format(Date())} [$source] $message")
            while (buffer.size > MAX_LINES) {
                buffer.removeFirst()
            }
            if (_entries.subscriptionCount.value == 0) return
            val now = System.currentTimeMillis()
            if (now - lastEmitAt >= EMIT_INTERVAL_MS) {
                lastEmitAt = now
                _entries.value = buffer.toList()
            }
        }
    }

    private const val TAG = "DiagLog"
}
