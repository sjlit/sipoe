package com.sipoe.softphone.sip

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.sipoe.softphone.R
import com.sipoe.softphone.data.CallLogDirection
import com.sipoe.softphone.data.CallLogEntry
import com.sipoe.softphone.data.CallLogStore
import com.sipoe.softphone.diag.DiagLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.Reason

enum class CallDirection { Incoming, Outgoing }

enum class CallStatus { Incoming, Connecting, Connected, Ending, Ended, Error }

enum class AudioRoute { Earpiece, Speaker, Bluetooth, Headset }

val AudioRoute.labelRes: Int
    @StringRes
    get() = when (this) {
        AudioRoute.Earpiece -> R.string.route_earpiece
        AudioRoute.Speaker -> R.string.route_speaker
        AudioRoute.Bluetooth -> R.string.route_bluetooth
        AudioRoute.Headset -> R.string.route_headset
    }

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
        CallStatus.Ending -> R.string.call_status_ending
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
    private const val TERMINATE_TIMEOUT_MILLIS = 3_000L

    private var core: Core? = null
    private var appContext: Context? = null

    @field:android.annotation.SuppressLint("StaticFieldLeak")
    private var store: CallLogStore? = null
    private var currentCall: Call? = null
    private var recorded = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var clearJob: Job? = null
    private var terminateJob: Job? = null

    private val _state = MutableStateFlow<CallUiState?>(null)
    val state: StateFlow<CallUiState?> = _state.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun attach(core: Core, context: Context) {
        this.core = core
        this.appContext = context.applicationContext
        this.store = CallLogStore(context.applicationContext)
        core.addListener(listener)
    }

    private fun notifyEvent(message: String) {
        _events.tryEmit(message)
    }

    private fun notifyEvent(@StringRes messageRes: Int, vararg args: Any) {
        val context = appContext ?: return
        _events.tryEmit(context.getString(messageRes, *args))
    }

    private fun isBusy(): Boolean = when (_state.value?.status) {
        null, CallStatus.Ended, CallStatus.Error -> false
        else -> true
    }

    fun dial(number: String) {
        val current = core ?: return
        val trimmed = number.trim()
        if (trimmed.isEmpty()) return
        if (isBusy()) {
            notifyEvent(R.string.call_event_busy)
            return
        }
        val domain = current.defaultAccount?.params?.identityAddress?.domain ?: return
        val address = Factory.instance().createAddress("sip:$trimmed@$domain") ?: return
        clearJob?.cancel()
        terminateJob?.cancel()
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
        val call = currentCall ?: return
        endCall { call.decline(Reason.Declined) }
    }

    fun hangup() {
        val call = currentCall ?: return
        endCall { call.terminate() }
    }

    private fun endCall(action: () -> Unit) {
        if (_state.value?.status == CallStatus.Ending) return
        updateState { it.copy(status = CallStatus.Ending) }
        DiagLog.i(TAG, "Hangup requested")
        action()
        terminateJob?.cancel()
        terminateJob = scope.launch {
            delay(TERMINATE_TIMEOUT_MILLIS)
            if (_state.value?.status == CallStatus.Ending) {
                DiagLog.w(TAG, "Hangup timed out, forcing ended state")
                updateState { it.copy(status = CallStatus.Ended) }
                scheduleClear(1_000)
            }
        }
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
        if (route == AudioRoute.Bluetooth && !hasBluetoothPermission()) {
            notifyEvent(R.string.call_event_bluetooth_permission)
            return
        }
        val device = current.audioDevices.firstOrNull {
            it.hasCapability(AudioDevice.Capabilities.CapabilityPlay) && it.type.toAudioRoute() == route
        } ?: run {
            val label = appContext?.getString(route.labelRes).orEmpty()
            notifyEvent(R.string.call_event_route_missing, label)
            return
        }
        val result = runCatching { call.setOutputAudioDevice(device) }
        if (result.isFailure) {
            DiagLog.e(TAG, "Unable to select audio route $route", result.exceptionOrNull())
            notifyEvent(R.string.call_event_route_failed)
        }
        val actual = runCatching { call.outputAudioDevice?.type?.toAudioRoute() }.getOrNull()
        if (actual != null && actual != route) {
            DiagLog.w(TAG, "Audio route selection fell back to $actual")
        }
        updateState { it.copy(currentRoute = actual ?: route) }
    }

    private fun callErrorMessage(raw: String): String? {
        val context = appContext ?: return raw.ifBlank { null }
        return mapCallError(context, raw) ?: context.getString(R.string.call_failed)
    }

    private fun hasBluetoothPermission(): Boolean {
        val context = appContext ?: return true
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
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
            ?: call.remoteAddress.username
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
            DiagLog.i(
                TAG,
                "Call state=$state remote=${call.remoteAddress.asString()} message=$message",
            )
            when (state) {
                Call.State.IncomingReceived, Call.State.IncomingEarlyMedia -> {
                    if (currentCall != null) {
                        DiagLog.w(
                            TAG,
                            "Rejecting second incoming call from ${call.remoteAddress.asString()}",
                        )
                        notifyEvent(R.string.call_event_second_call)
                        call.decline(Reason.Busy)
                        return
                    }
                    currentCall = call
                    recorded = false
                    clearJob?.cancel()
                    _state.value = CallUiState(
                        number = call.remoteAddress.username ?: call.remoteAddress.asString(),
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
                    terminateJob?.cancel()
                    recordCall(call)
                    currentCall = null
                    updateState { it.copy(status = CallStatus.Ended) }
                    scheduleClear(1_000)
                }

                Call.State.Error -> {
                    terminateJob?.cancel()
                    recordCall(call)
                    currentCall = null
                    updateState {
                        it.copy(
                            status = CallStatus.Error,
                            errorMessage = callErrorMessage(message),
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
