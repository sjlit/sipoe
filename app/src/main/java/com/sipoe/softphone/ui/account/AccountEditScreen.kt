package com.sipoe.softphone.ui.account

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.sipoe.softphone.sip.SipCoreManager
import kotlinx.coroutines.launch

/**
 * 新增或编辑单个账号。[accountId] 为 null 表示新建。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditScreen(
    accountId: String?,
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    viewModel: AccountViewModel = viewModel(),
) {
    val state by viewModel.accounts.collectAsStateWithLifecycle()
    val registration by viewModel.registration.collectAsStateWithLifecycle()
    val saving by viewModel.busy.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current

    // 新建账号在首次保存后才会拿到 id,后续保存要落在同一条记录上(空串代表尚未落库)
    var editingId by rememberSaveable(accountId) { mutableStateOf(accountId.orEmpty()) }
    val stored = state.find(editingId)
    val isActive = stored != null && stored.id == state.activeId

    var form by remember(stored) { mutableStateOf(stored ?: AccountSettings()) }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    val isDirty = form != (stored ?: AccountSettings())

    fun attemptBack() {
        if (isDirty) showDiscardDialog = true else onBack()
    }

    BackHandler(enabled = isDirty) { showDiscardDialog = true }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is AccountEvent.Saved -> {
                    editingId = event.id
                    resources.getString(
                        if (event.activated) {
                            R.string.account_saved_active
                        } else {
                            R.string.account_saved_inactive
                        },
                    )
                }

                is AccountEvent.Failed -> event.message
                is AccountEvent.Notice -> event.message
            }
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
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
                                viewModel.save(form)
                            }
                        },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(
                                when {
                                    saving -> R.string.account_saving
                                    isActive || state.active == null -> R.string.account_save
                                    else -> R.string.account_save_only
                                },
                            ),
                        )
                    }
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (stored == null) R.string.account_add else R.string.account_edit,
                        ),
                    )
                },
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
            if (isActive) {
                RegistrationCard(
                    state = registration,
                    onRetry = { SipCoreManager.refreshRegistration() },
                )
            }

            stored?.takeIf { it.isComplete }?.let { account ->
                Text(
                    text = "${account.identityUri} → ${account.serverAddress} · ${account.transport.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = form.name,
                onValueChange = { form = form.copy(name = it) },
                label = { Text(stringResource(R.string.account_field_name)) },
                placeholder = { Text(stringResource(R.string.account_field_name_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

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

            if (stored != null) {
                TextButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.account_delete),
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

    if (showDeleteDialog && stored != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.account_delete_title)) },
            text = {
                Text(
                    stringResource(
                        if (isActive) {
                            R.string.account_delete_message_active
                        } else {
                            R.string.account_delete_message
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete(stored.id)
                        onBack()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}