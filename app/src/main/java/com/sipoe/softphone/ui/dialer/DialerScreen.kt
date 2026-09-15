package com.sipoe.softphone.ui.dialer

import android.Manifest
import android.content.ClipData
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sipoe.softphone.R
import com.sipoe.softphone.sip.CallController
import com.sipoe.softphone.sip.RegistrationStatus
import com.sipoe.softphone.sip.SipCoreManager
import com.sipoe.softphone.sip.SipRegistrationState
import com.sipoe.softphone.sip.labelRes
import com.sipoe.softphone.ui.components.Dialpad
import com.sipoe.softphone.ui.permissions.PermissionWarningCard
import com.sipoe.softphone.ui.permissions.findActivity
import com.sipoe.softphone.ui.permissions.fullScreenIntentAllowed
import com.sipoe.softphone.ui.permissions.hasMicPermission
import com.sipoe.softphone.ui.permissions.hasNotificationPermission
import com.sipoe.softphone.ui.permissions.isPermanentlyDenied
import com.sipoe.softphone.ui.permissions.notificationsAllowed
import com.sipoe.softphone.ui.permissions.openAppSettings
import com.sipoe.softphone.ui.permissions.openFullScreenIntentSettings
import com.sipoe.softphone.ui.permissions.openNotificationSettings
import com.sipoe.softphone.ui.permissions.rememberResumedFlag
import com.sipoe.softphone.ui.theme.SipoeTheme
import kotlinx.coroutines.launch

@Composable
fun DialerScreen(
    onOpenHistory: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val registration by SipCoreManager.registration.collectAsStateWithLifecycle()
    var number by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    val activity = context.findActivity()
    val micGranted = rememberResumedFlag { hasMicPermission(context) }
    val notifAllowed = rememberResumedFlag { notificationsAllowed(context) }
    val fullScreenAllowed = rememberResumedFlag { fullScreenIntentAllowed(context) }
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    suspend fun readClipboardNumber(): String =
        clipboard.getClipEntry()
            ?.clipData
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            .orEmpty()
            .filter { it.isDigit() || it == '+' || it == '*' || it == '#' }

    fun dialNumber(candidate: String) {
        if (registration.status != RegistrationStatus.Registered) {
            Toast.makeText(context, R.string.dialer_not_registered, Toast.LENGTH_SHORT).show()
        }
        CallController.dial(candidate)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RegistrationPill(state = registration, onClick = onOpenAccount)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenHistory) {
                Icon(
                    painter = painterResource(R.drawable.ic_history),
                    contentDescription = stringResource(R.string.dialer_history),
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.dialer_settings),
                )
            }
        }

        when {
            registration.identity == null -> SetupCard(onOpenAccount = onOpenAccount)
            registration.status == RegistrationStatus.Failed -> RegistrationIssueCard(
                message = registration.message,
                detail = registration.errorDetail,
                onRetry = { SipCoreManager.refreshRegistration() },
            )
        }

        if (!micGranted) {
            Spacer(Modifier.height(12.dp))
            PermissionWarningCard(
                title = stringResource(R.string.permission_mic_title),
                message = stringResource(R.string.permission_mic_message),
                actionLabel = if (activity != null &&
                    isPermanentlyDenied(activity, Manifest.permission.RECORD_AUDIO)
                ) {
                    stringResource(R.string.common_open_settings)
                } else {
                    stringResource(R.string.permission_mic_action)
                },
                onAction = {
                    if (activity != null &&
                        isPermanentlyDenied(activity, Manifest.permission.RECORD_AUDIO)
                    ) {
                        openAppSettings(context)
                    } else {
                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
            )
        }

        if (!notifAllowed) {
            Spacer(Modifier.height(12.dp))
            PermissionWarningCard(
                title = stringResource(R.string.permission_notification_title),
                message = stringResource(R.string.permission_notification_message),
                actionLabel = if (hasNotificationPermission(context)) {
                    stringResource(R.string.common_open_settings)
                } else {
                    stringResource(R.string.permission_notification_action)
                },
                onAction = {
                    if (hasNotificationPermission(context)) {
                        openNotificationSettings(context)
                    } else {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
        } else if (!fullScreenAllowed) {
            Spacer(Modifier.height(12.dp))
            PermissionWarningCard(
                title = stringResource(R.string.permission_fullscreen_title),
                message = stringResource(R.string.permission_fullscreen_message),
                actionLabel = stringResource(R.string.common_open_settings),
                onAction = { openFullScreenIntentSettings(context) },
            )
        }

        Spacer(Modifier.weight(1f))

        NumberDisplay(
            number = number,
            onBackspace = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                number = number.dropLast(1)
            },
            onClearAll = {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                number = ""
            },
            onCopy = {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("number", number)))
                    Toast.makeText(context, R.string.dialer_copied, Toast.LENGTH_SHORT).show()
                }
            },
            onPaste = {
                scope.launch {
                    val pasted = readClipboardNumber()
                    if (pasted.isEmpty()) {
                        Toast.makeText(context, R.string.dialer_clipboard_empty, Toast.LENGTH_SHORT).show()
                    } else {
                        number += pasted
                    }
                }
            },
            onPasteAndDial = {
                scope.launch {
                    val pasted = readClipboardNumber()
                    if (pasted.isEmpty()) {
                        Toast.makeText(context, R.string.dialer_clipboard_empty, Toast.LENGTH_SHORT).show()
                    } else {
                        number = pasted
                        dialNumber(pasted)
                    }
                }
            },
        )

        Spacer(Modifier.weight(1f))

        Dialpad(
            onKeyPress = { key -> number += key.label },
            onLongZero = { number += "+" },
        )

        Spacer(Modifier.height(28.dp))

        CallButton(
            enabled = number.isNotBlank() && registration.identity != null,
            onClick = { dialNumber(number) },
        )

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RegistrationPill(state: SipRegistrationState, onClick: () -> Unit) {
    val sipoe = SipoeTheme.colors
    val color = when (state.status) {
        RegistrationStatus.Registered -> sipoe.accept
        RegistrationStatus.InProgress -> sipoe.warning
        RegistrationStatus.Failed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(state.status.labelRes),
                style = MaterialTheme.typography.labelMedium,
                color = color,
            )
        }
    }
}

@Composable
private fun SetupCard(onOpenAccount: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.dialer_setup_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = stringResource(R.string.dialer_setup_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Button(onClick = onOpenAccount, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.common_open_settings))
            }
        }
    }
}

@Composable
private fun RegistrationIssueCard(
    message: String?,
    detail: String?,
    onRetry: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.status_failed),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            message
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            detail
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                    )
                }
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.account_retry_register))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NumberDisplay(
    number: String,
    onBackspace: () -> Unit,
    onClearAll: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onPasteAndDial: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val menuDescription = stringResource(R.string.dialer_number_menu_desc)
    val openMenuLabel = stringResource(R.string.dialer_open_menu)
    val backspaceDescription = stringResource(R.string.dialer_backspace_desc)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fontSize = when {
            number.length <= 10 -> 42.sp
            number.length <= 14 -> 32.sp
            else -> 26.sp
        }
        Box(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuExpanded = true
                            },
                        )
                    }
                    .semantics {
                        contentDescription = menuDescription
                        onLongClick(label = openMenuLabel) {
                            menuExpanded = true
                            true
                        }
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = number.ifEmpty { stringResource(R.string.dialer_number_placeholder) },
                    fontSize = fontSize,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (number.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                if (number.isNotEmpty()) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_copy)) },
                        onClick = {
                            menuExpanded = false
                            onCopy()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_paste)) },
                    onClick = {
                        menuExpanded = false
                        onPaste()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.dialer_paste_and_dial)) },
                    onClick = {
                        menuExpanded = false
                        onPasteAndDial()
                    },
                )
            }
        }
        if (number.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .combinedClickable(onClick = onBackspace, onLongClick = onClearAll)
                    .semantics { contentDescription = backspaceDescription },
                contentAlignment = Alignment.Center,
            ) {
                Text("⌫", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
}

@Composable
private fun CallButton(enabled: Boolean, onClick: () -> Unit) {
    val sipoe = SipoeTheme.colors
    val view = LocalView.current
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(if (enabled) sipoe.accept else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Phone,
            contentDescription = stringResource(R.string.dialer_call),
            tint = if (enabled) {
                sipoe.onAccept
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            },
        )
    }
}
