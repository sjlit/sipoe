package com.sipoe.softphone.data

import kotlinx.serialization.Serializable

enum class CallLogDirection { Incoming, Outgoing, Missed }

@Serializable
data class CallLogEntry(
    val id: Long,
    val number: String,
    val direction: CallLogDirection,
    val timestamp: Long,
    val durationSeconds: Int,
)
