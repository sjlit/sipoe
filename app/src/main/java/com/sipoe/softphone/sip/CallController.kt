package com.sipoe.softphone.sip

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import com.sipoe.softphone.R
import com.sipoe.softphone.data.CallLogDirection
import com.sipoe.softphone.data.CallLogEntry
import com.sipoe.softphone.data.CallLogStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.Reason

enum class CallDirection { Incoming, Outgoing }

enum class CallStatus { Incoming, Connecting, Connected, Ended, Error }

enum class AudioRoute { Earpiece, Speaker, Bluetooth, Headset }

data class CallUiState(
    val number: String,
    val direction: CallDirection,
    val status: CallStatus,
    val startedAt: Long? = null,
    val isMuted: Boolean = false,
    val currentRoute: AudioRoute = AudioRoute.Earpiece,
    val availableRoutes: List<AudioRoute> = emptyList(),
    val errorMessage: String? = null,
)

@get:StringRes
val CallStatus.labelRes: Int
    get() = when (this) {
        CallStatus.Incoming -> R.string.call_status_incoming
        CallStatus.Connecting -> R.string.call_status_connecting
        CallStatus.Connected -> R.string.call_status_connected
        CallStatus.Ended -> R.string.call_status_ended
        CallStatus.Error -> R.string.call_status_error
    }

@get:StringRes
val CallUiState.displayLabelRes: Int
    get() = if (status == CallStatus.Connecting && direction == CallDirection.Incoming) {
        R.string.call_status_answering
    } else {
        status.labelRes
    }

object CallController {
    private const val TAG = "CallController"

    private var core: Core? = null

    @field:android.annotation.SuppressLint("StaticFieldLeak")
    private var store: CallLogStore? = null
    private var currentCall: Call? = null
    private var recorded = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var clearJob: Job? = null

    private val _state = MutableStateFlow<CallUiState?>(null)
    val state: StateFlow<CallUiState?> = _state.asStateFlow()

    fun attach(core: Core, context: Context) {
        this.core = core
        this.store = CallLogStore(context.applicationContext)
        core.addListener(listener)
    }

    fun dial(number: String) {
        val current = core ?: return
        val trimmed = number.trim()
        if (trimmed.isEmpty()) return
        val domain = current.defaultAccount?.params?.identityAddress?.domain ?: return
        val address = Factory.instance().createAddress("sip:$trimmed@$domain") ?: return
        clearJob?.cancel()
        recorded = false
        _state.value = CallUiState(
            number = trimmed,
            direction = CallDirection.Outgoing,
            status = CallStatus.Connecting,
        )
        currentCall = current.inviteAddress(address)
    }

    fun accept() {
        currentCall?.accept()
    }

    fun decline() {
        currentCall?.decline(Reason.Declined)
    }

    fun hangup() {
        currentCall?.terminate()
    }

    fun toggleMute() {
        val call = currentCall ?: return
        val muted = !call.microphoneMuted
        call.setMicrophoneMuted(muted)
        updateState { it.copy(isMuted = muted) }
    }

    fun selectRoute(route: AudioRoute) {
        val call = currentCall ?: return
        val current = core ?: return
        val device = current.audioDevices.firstOrNull {
            it.hasCapability(AudioDevice.Capabilities.CapabilityPlay) && it.type.toAudioRoute() == route
        } ?: return
        call.setOutputAudioDevice(device)
        updateState { it.copy(currentRoute = route) }
    }

    fun cycleRoute() {
        val snapshot = _state.value ?: return
        refreshAudioRoutes()
        val routes = _state.value?.availableRoutes.orEmpty()
        val cycle = buildList {
            add(AudioRoute.Earpiece)
            routes.filter { it != AudioRoute.Earpiece }
                .sortedBy { routePriority(it) }
                .forEach { add(it) }
        }
        val index = cycle.indexOf(snapshot.currentRoute).takeIf { it >= 0 } ?: 0
        selectRoute(cycle[(index + 1) % cycle.size])
    }

    fun sendDtmf(digit: Char) {
        currentCall?.sendDtmf(digit)
    }

    private fun refreshAudioRoutes() {
        val call = currentCall ?: return
        val current = core ?: return
        val routes = current.audioDevices
            .filter { it.hasCapability(AudioDevice.Capabilities.CapabilityPlay) }
            .mapNotNull { it.type.toAudioRoute() }
            .distinct()
        val currentRoute = call.outputAudioDevice?.type?.toAudioRoute() ?: AudioRoute.Earpiece
        updateState { it.copy(availableRoutes = routes, currentRoute = currentRoute) }
    }

    private fun routePriority(route: AudioRoute): Int = when (route) {
        AudioRoute.Bluetooth -> 0
        AudioRoute.Speaker -> 1
        AudioRoute.Headset -> 2
        AudioRoute.Earpiece -> 3
    }

    private inline fun updateState(transform: (CallUiState) -> CallUiState) {
        _state.value = _state.value?.let(transform)
    }

    private fun scheduleClear(delayMillis: Long = 1_000) {
        clearJob?.cancel()
        clearJob = scope.launch {
            delay(delayMillis)
            _state.value = null
        }
    }

    private fun recordCall(call: Call) {
        if (recorded) return
        recorded = true
        val logStore = store ?: return
        val number = _state.value?.number?.takeIf { it.isNotBlank() }
            ?: call.remoteAddress?.username
            ?: return
        val wasConnected = _state.value?.startedAt != null
        val direction = when {
            call.dir == Call.Dir.Incoming && !wasConnected -> CallLogDirection.Missed
            call.dir == Call.Dir.Incoming -> CallLogDirection.Incoming
            else -> CallLogDirection.Outgoing
        }
        val entry = CallLogEntry(
            id = System.currentTimeMillis(),
            number = number,
            direction = direction,
            timestamp = System.currentTimeMillis(),
            durationSeconds = runCatching { call.duration }.getOrDefault(0).coerceAtLeast(0),
        )
        scope.launch { logStore.add(entry) }
    }

    private val listener = object : CoreListenerStub() {
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State,
            message: String,
        ) {
            Log.i(TAG, "Call state: $state remote=${call.remoteAddressAsString} message=$message")
            com.sipoe.softphone.diag.DiagLog.i(
                TAG,
                "Call state=$state remote=${call.remoteAddressAsString} message=$message",
            )
            when (state) {
                Call.State.IncomingReceived, Call.State.IncomingEarlyMedia -> {
                    currentCall = call
                    recorded = false
                    clearJob?.cancel()
                    _state.value = CallUiState(
                        number = call.remoteAddress?.username ?: call.remoteAddressAsString.orEmpty(),
                        direction = CallDirection.Incoming,
                        status = CallStatus.Incoming,
                    )
                }

                Call.State.OutgoingInit,
                Call.State.OutgoingProgress,
                Call.State.OutgoingRinging,
                Call.State.OutgoingEarlyMedia,
                -> {
                    currentCall = call
                    recorded = false
                    updateState { it.copy(status = CallStatus.Connecting) }
                }

                Call.State.Connected,
                Call.State.StreamsRunning,
                Call.State.Updating,
                Call.State.UpdatedByRemote,
                -> {
                    updateState {
                        it.copy(
                            status = CallStatus.Connected,
                            startedAt = it.startedAt ?: System.currentTimeMillis(),
                        )
                    }
                    refreshAudioRoutes()
                }

                Call.State.End, Call.State.Released -> {
                    recordCall(call)
                    currentCall = null
                    updateState { it.copy(status = CallStatus.Ended) }
                    scheduleClear(1_000)
                }

                Call.State.Error -> {
                    recordCall(call)
                    currentCall = null
                    updateState {
                        it.copy(
                            status = CallStatus.Error,
                            errorMessage = mapCallError(message) ?: "呼叫失败",
                        )
                    }
                    scheduleClear(2_400)
                }

                else -> Unit
            }
        }

        override fun onAudioDevicesListUpdated(core: Core) {
            refreshAudioRoutes()
        }
    }
}

private fun AudioDevice.Type.toAudioRoute(): AudioRoute? = when (this) {
    AudioDevice.Type.Earpiece -> AudioRoute.Earpiece
    AudioDevice.Type.Speaker -> AudioRoute.Speaker
    AudioDevice.Type.Bluetooth, AudioDevice.Type.BluetoothA2DP -> AudioRoute.Bluetooth
    AudioDevice.Type.Headset, AudioDevice.Type.Headphones -> AudioRoute.Headset
    else -> null
}
