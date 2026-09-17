package com.sipoe.softphone.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sipoe.softphone.R
import com.sipoe.softphone.sip.RegistrationStatus
import com.sipoe.softphone.sip.SipRegistrationState
import com.sipoe.softphone.sip.labelRes
import com.sipoe.softphone.ui.theme.SipoeTheme

/** 当前激活账号的注册状态,账号列表页与编辑页共用。 */
@Composable
internal fun RegistrationCard(state: SipRegistrationState, onRetry: () -> Unit) {
    val sipoe = SipoeTheme.colors
    val color = when (state.status) {
        RegistrationStatus.Registered -> sipoe.accept
        RegistrationStatus.InProgress -> sipoe.warning
        RegistrationStatus.Failed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(state.status.labelRes),
                color = color,
                style = MaterialTheme.typography.titleSmall,
            )
            state.identity?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.message
                ?.takeIf { it.isNotBlank() && state.status == RegistrationStatus.Failed }
                ?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            state.errorDetail
                ?.takeIf { state.status == RegistrationStatus.Failed }
                ?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            if (state.networkRestricted) {
                Text(
                    text = stringResource(R.string.account_network_restricted),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (state.status == RegistrationStatus.Failed) {
                TextButton(
                    onClick = onRetry,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                ) {
                    Text(stringResource(R.string.account_retry_register))
                }
            }
        }
    }
}