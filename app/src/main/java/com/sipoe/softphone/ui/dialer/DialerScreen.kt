package com.sipoe.softphone.ui.dialer

import android.content.ClipData
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
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
import com.sipoe.softphone.ui.theme.SipoeTheme
import kotlinx.coroutines.launch

@Composable
fun DialerScreen(
    onOpenHistory: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    val registration by SipCoreManager.registration.collectAsStateWithLifecycle()
    var number by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current

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
                    contentDescription = "通话记录",
                )
            }
            IconButton(onClick = onOpenAccount) {
                Icon(Icons.Filled.Settings, contentDescription = "账号设置")
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
                    Toast.makeText(context, "已复制号码", Toast.LENGTH_SHORT).show()
                }
            },
            onPaste = {
                scope.launch {
                    val pasted = clipboard.getClipEntry()
                        ?.clipData
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)
                        ?.coerceToText(context)
                        ?.toString()
                        .orEmpty()
                        .filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
                    if (pasted.isEmpty()) {
                        Toast.makeText(context, "剪贴板没有可用号码", Toast.LENGTH_SHORT).show()
                    } else {
                        number += pasted
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
            onClick = {
                if (registration.status != RegistrationStatus.Registered) {
                    Toast.makeText(context, "当前未注册,呼叫可能失败", Toast.LENGTH_SHORT).show()
                }
                CallController.dial(number)
            },
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
                text = "尚未配置账号",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "填写 SIP 服务器、用户名和密码后即可拨打和接听电话。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Button(onClick = onOpenAccount, modifier = Modifier.fillMaxWidth()) {
                Text("去设置")
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
                text = "注册失败",
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
                Text("重试注册")
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
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fontSize = when {
            number.length <= 10 -> 42.sp
            number.length <= 14 -> 32.sp
            else -> 26.sp
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = {},
                    onLongClick = { if (number.isEmpty()) onPaste() else onCopy() },
                )
                .semantics { contentDescription = "号码,长按复制或粘贴" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.ifEmpty { "输入号码" },
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
        if (number.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .combinedClickable(onClick = onBackspace, onLongClick = onClearAll)
                    .semantics { contentDescription = "删除,长按清空" },
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
            contentDescription = "拨号",
            tint = if (enabled) {
                sipoe.onAccept
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            },
        )
    }
}
