package com.sipoe.softphone.ui.account

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sipoe.softphone.R
import com.sipoe.softphone.data.AccountSettings
import com.sipoe.softphone.data.SipTransport
import com.sipoe.softphone.sip.RegistrationStatus
import com.sipoe.softphone.sip.SipCoreManager
import com.sipoe.softphone.sip.SipRegistrationState
import com.sipoe.softphone.sip.labelRes
import com.sipoe.softphone.ui.theme.SipoeTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    viewModel: AccountViewModel = viewModel(),
) {
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val registration by viewModel.registration.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current

    var form by remember(saved) { mutableStateOf(saved ?: AccountSettings()) }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var awaitingOutcome by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    val isDirty = form != (saved ?: AccountSettings())

    fun attemptBack() {
        if (isDirty) showDiscardDialog = true else onBack()
    }

    BackHandler(enabled = isDirty) { showDiscardDialog = true }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
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
                        onClick = {
                            val error = form.validate()
                            if (error != null) {
                                scope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    snackbarHostState.showSnackbar(resources.getString(error))
                                }
                            } else {
                                awaitingOutcome = true
                                viewModel.save(form)
                            }
                        },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(
                                if (saving) R.string.account_saving else R.string.account_save,
                            ),
                        )
                    }
                }
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_title)) },
                navigationIcon = {
                    IconButton(onClick = { attemptBack() }) {
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
            RegistrationCard(
                state = registration,
                onRetry = { SipCoreManager.refreshRegistration() },
            )

            saved?.takeIf { it.isComplete }?.let { account ->
                Text(
                    text = "${account.identityUri} → ${account.serverAddress} · ${account.transport.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = form.domain,
                onValueChange = { form = form.copy(domain = it) },
                label = { Text(stringResource(R.string.account_field_domain)) },
                placeholder = { Text(stringResource(R.string.account_field_domain_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = form.proxy,
                onValueChange = { form = form.copy(proxy = it) },
                label = { Text(stringResource(R.string.account_field_proxy)) },
                placeholder = { Text(stringResource(R.string.account_field_proxy_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = form.username,
                onValueChange = { form = form.copy(username = it) },
                label = { Text(stringResource(R.string.account_field_username)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = form.password,
                onValueChange = { form = form.copy(password = it) },
                label = { Text(stringResource(R.string.account_field_password)) },
                singleLine = true,
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                trailingIcon = {
                    TextButton(onClick = { passwordVisible = !passwordVisible }) {
                        Text(
                            text = stringResource(
                                if (passwordVisible) {
                                    R.string.account_password_hide
                                } else {
                                    R.string.account_password_show
                                },
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider(Modifier.padding(top = 4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { advancedExpanded = !advancedExpanded }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.account_advanced),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (advancedExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = advancedExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.account_transport), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SipTransport.entries.forEach { transport ->
                            FilterChip(
                                selected = form.transport == transport,
                                onClick = { form = form.copy(transport = transport) },
                                label = { Text(transport.label) },
                            )
                        }
                    }

                    OutlinedTextField(
                        value = if (form.port == 0) "" else form.port.toString(),
                        onValueChange = { input ->
                            form = form.copy(port = input.filter { it.isDigit() }.take(5).toIntOrNull() ?: 0)
                        },
                        label = { Text(stringResource(R.string.account_port)) },
                        placeholder = {
                            Text(
                                text = stringResource(
                                    if (form.transport == SipTransport.TLS) {
                                        R.string.account_port_hint_tls
                                    } else {
                                        R.string.account_port_hint
                                    },
                                ),
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = form.registerExpires.toString(),
                        onValueChange = { input ->
                            form = form.copy(registerExpires = input.filter { it.isDigit() }.take(4).toIntOrNull() ?: 0)
                        },
                        label = { Text(stringResource(R.string.account_register_expires)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("STUN", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = stringResource(R.string.account_stun_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = form.stunEnabled,
                            onCheckedChange = { form = form.copy(stunEnabled = it) },
                        )
                    }

                    AnimatedVisibility(visible = form.stunEnabled) {
                        OutlinedTextField(
                            value = form.stunServer,
                            onValueChange = { form = form.copy(stunServer = it) },
                            label = { Text(stringResource(R.string.account_stun_server)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            if (saved?.isComplete == true) {
                TextButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.account_clear),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.account_discard_title)) },
            text = { Text(stringResource(R.string.account_discard_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onBack()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.account_discard_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.account_discard_keep))
                }
            },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.account_clear_title)) },
            text = { Text(stringResource(R.string.account_clear_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        form = AccountSettings()
                        viewModel.clear()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.common_clear),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun RegistrationCard(state: SipRegistrationState, onRetry: () -> Unit) {
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
