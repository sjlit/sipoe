package com.sipoe.softphone.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sipoe.softphone.R
import com.sipoe.softphone.data.AccountSettings
import com.sipoe.softphone.sip.RegistrationStatus
import com.sipoe.softphone.sip.SipCoreManager
import com.sipoe.softphone.ui.theme.SipoeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountListScreen(
    onBack: () -> Unit,
    onAddAccount: () -> Unit,
    onEditAccount: (String) -> Unit,
    onOpenDiagnostics: () -> Unit,
    viewModel: AccountViewModel = viewModel(),
) {
    val state by viewModel.accounts.collectAsStateWithLifecycle()
    val registration by viewModel.registration.collectAsStateWithLifecycle()
    val inCall by viewModel.inCall.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showDeactivateDialog by rememberSaveable { mutableStateOf(false) }
    var awaitingOutcome by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is AccountEvent.Notice -> event.message
                is AccountEvent.Failed -> event.message
                is AccountEvent.Saved -> return@collect
            }
            awaitingOutcome = false
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(awaitingOutcome, registration.status) {
        if (!awaitingOutcome) return@LaunchedEffect
        val outcome = when (registration.status) {
            RegistrationStatus.Registered -> resources.getString(R.string.account_register_success)
            RegistrationStatus.Failed -> resources.getString(R.string.account_register_failed)
            else -> null
        } ?: return@LaunchedEffect
        awaitingOutcome = false
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(outcome)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
                        )
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Button(
                        onClick = onAddAccount,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.account_add))
                    }
                }
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onOpenDiagnostics) {
                        Text(stringResource(R.string.account_diagnostics))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.active != null) {
                RegistrationCard(
                    state = registration,
                    onRetry = { SipCoreManager.refreshRegistration() },
                )
            } else if (state.accounts.isNotEmpty()) {
                NoActiveAccountCard()
            }

            if (state.accounts.isEmpty()) {
                EmptyAccountsCard()
            } else {
                state.accounts.forEach { account ->
                    AccountRow(
                        account = account,
                        isActive = account.id == state.activeId,
                        status = registration.status,
                        enabled = !busy && !inCall,
                        onEdit = { onEditAccount(account.id) },
                        onActivate = {
                            awaitingOutcome = true
                            viewModel.activate(account.id)
                        },
                        onDeactivate = { showDeactivateDialog = true },
                    )
                }
                if (inCall) {
                    Text(
                        text = stringResource(R.string.account_switch_blocked),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showDeactivateDialog) {
        AlertDialog(
            onDismissRequest = { showDeactivateDialog = false },
            title = { Text(stringResource(R.string.account_deactivate_title)) },
            text = { Text(stringResource(R.string.account_deactivate_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeactivateDialog = false
                        awaitingOutcome = false
                        viewModel.deactivate()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.account_deactivate),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeactivateDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    // awaitingOutcome 只对"激活"有意义,停用后不会再有注册结果
    LaunchedEffect(state.activeId) {
        if (state.activeId == null) awaitingOutcome = false
    }
}

@Composable
private fun AccountRow(
    account: AccountSettings,
    isActive: Boolean,
    status: RegistrationStatus,
    enabled: Boolean,
    onEdit: () -> Unit,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
) {
    val sipoe = SipoeTheme.colors
    val dotColor = when {
        !isActive -> MaterialTheme.colorScheme.outline
        status == RegistrationStatus.Registered -> sipoe.accept
        status == RegistrationStatus.InProgress -> sipoe.warning
        status == RegistrationStatus.Failed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = account.label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = account.identityUri,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${account.serverAddress} · ${account.transport.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!account.isComplete) {
                    Text(
                        text = stringResource(R.string.account_incomplete_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            when {
                !account.isComplete -> Unit
                isActive -> TextButton(onClick = onDeactivate, enabled = enabled) {
                    Text(stringResource(R.string.account_deactivate))
                }

                else -> TextButton(onClick = onActivate, enabled = enabled) {
                    Text(stringResource(R.string.account_activate))
                }
            }
        }
    }
}

@Composable
private fun NoActiveAccountCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.account_no_active_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.account_no_active_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyAccountsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.account_list_empty_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = stringResource(R.string.account_list_empty_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}