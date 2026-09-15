package com.sipoe.softphone.ui.call

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sipoe.softphone.R
import com.sipoe.softphone.sip.AudioRoute
import com.sipoe.softphone.sip.CallController
import com.sipoe.softphone.sip.CallStatus
import com.sipoe.softphone.sip.displayLabelRes
import com.sipoe.softphone.sip.labelRes
import com.sipoe.softphone.ui.components.Dialpad
import com.sipoe.softphone.ui.permissions.hasMicPermission
import com.sipoe.softphone.ui.permissions.rememberResumedFlag
import com.sipoe.softphone.ui.theme.SipoeTheme
import kotlinx.coroutines.delay

@Composable
fun InCallScreen(onFinished: () -> Unit) {
    val call by CallController.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(call) {
        if (call == null) onFinished()
    }

    val state = call ?: return
    var showKeypad by rememberSaveable { mutableStateOf(false) }
    val ending = state.status == CallStatus.Ending
    val endHaptic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.REJECT
    } else {
        HapticFeedbackConstants.LONG_PRESS
    }
    val view = LocalView.current
    val sipoe = SipoeTheme.colors
    val micGranted = rememberResumedFlag { hasMicPermission(context) }

    BackHandler {
        context.findActivity()?.moveTaskToBack(true)
    }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(state.status) {
        val constant = when (state.status) {
            CallStatus.Connected -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.KEYBOARD_TAP
            }

            CallStatus.Ended, CallStatus.Error -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.REJECT
            } else {
                HapticFeedbackConstants.LONG_PRESS
            }

            else -> null
        }
        constant?.let { view.performHapticFeedback(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        Text(
            text = stringResource(state.displayLabelRes),
            style = MaterialTheme.typography.titleMedium,
            color = when (state.status) {
                CallStatus.Error -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = state.number,
            fontSize = 34.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )

        state.startedAt?.let { startedAt ->
            Spacer(Modifier.height(8.dp))
            CallTimer(startedAt)
        }

        state.errorMessage?.let { message ->
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }

        if (!micGranted) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.call_mic_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.weight(1f))

        if (showKeypad && state.status == CallStatus.Connected) {
            Dialpad(
                onKeyPress = { key -> CallController.sendDtmf(key.label.first()) },
                keySize = 62.dp,
            )
            Spacer(Modifier.height(28.dp))
        }

        if (state.status == CallStatus.Incoming) {
            Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                CallAction(
                    label = stringResource(R.string.call_decline),
                    iconRes = R.drawable.ic_call_end,
                    containerColor = sipoe.hangup,
                    contentColor = sipoe.onHangup,
                    busy = ending,
                    haptic = endHaptic,
                    onClick = { CallController.decline() },
                )
                CallAction(
                    label = stringResource(R.string.call_accept),
                    iconRes = R.drawable.ic_phone,
                    containerColor = sipoe.accept,
                    contentColor = sipoe.onAccept,
                    enabled = !ending,
                    onClick = { CallController.accept() },
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                CallToggle(
                    label = stringResource(
                        if (state.isMuted) R.string.call_muted else R.string.call_mute,
                    ),
                    iconRes = if (state.isMuted) R.drawable.ic_mic_off else R.drawable.ic_mic,
                    active = state.isMuted,
                    enabled = !ending,
                    onClick = { CallController.toggleMute() },
                )
                CallToggle(
                    label = stringResource(state.currentRoute.labelRes),
                    iconRes = routeIcon(state.currentRoute),
                    active = state.currentRoute != AudioRoute.Earpiece,
                    enabled = !ending,
                    onClick = { CallController.cycleRoute() },
                )
                CallToggle(
                    label = stringResource(R.string.call_keypad),
                    iconRes = R.drawable.ic_dialpad,
                    active = showKeypad,
                    enabled = !ending,
                    onClick = { showKeypad = !showKeypad },
                )
            }

            Spacer(Modifier.height(28.dp))

            CallAction(
                label = stringResource(R.string.call_hangup),
                iconRes = R.drawable.ic_call_end,
                containerColor = sipoe.hangup,
                contentColor = sipoe.onHangup,
                busy = ending,
                haptic = endHaptic,
                onClick = { CallController.hangup() },
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun CallAction(
    label: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    size: Dp = 72.dp,
    busy: Boolean = false,
    enabled: Boolean = true,
    haptic: Int = HapticFeedbackConstants.KEYBOARD_TAP,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !busy) 0.9f else 1f,
        label = "callActionScale",
    )
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = when {
                    busy -> 0.8f
                    !enabled -> 0.5f
                    else -> 1f
                }
            }
            .clip(CircleShape)
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled && !busy,
            ) {
                view.performHapticFeedback(haptic)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(26.dp),
                color = contentColor,
                strokeWidth = 2.5.dp,
            )
        } else {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
private fun CallToggle(
    label: String,
    @DrawableRes iconRes: Int,
    active: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val view = LocalView.current
    val container = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (active) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
                .clip(CircleShape)
                .background(container)
                .clickable(enabled = enabled) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onClick()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = content,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@DrawableRes
private fun routeIcon(route: AudioRoute): Int = when (route) {
    AudioRoute.Earpiece -> R.drawable.ic_phone
    AudioRoute.Speaker -> R.drawable.ic_volume_up
    AudioRoute.Bluetooth -> R.drawable.ic_bluetooth
    AudioRoute.Headset -> R.drawable.ic_headset
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun CallTimer(startedAt: Long) {
    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(startedAt) {
        while (true) {
            elapsedSeconds = ((System.currentTimeMillis() - startedAt).coerceAtLeast(0)) / 1000
            delay(500)
        }
    }

    Text(
        text = formatDuration(elapsedSeconds),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%02d:%02d".format(minutes, secs)
    }
}
