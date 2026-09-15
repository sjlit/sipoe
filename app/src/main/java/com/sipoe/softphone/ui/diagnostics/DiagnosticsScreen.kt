package com.sipoe.softphone.ui.diagnostics

import android.content.ClipData
import android.content.res.Resources
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sipoe.softphone.R
import com.sipoe.softphone.data.AccountSettings
import com.sipoe.softphone.diag.DiagLog
import com.sipoe.softphone.diag.NetworkSelfTest
import com.sipoe.softphone.sip.SipCoreManager
import com.sipoe.softphone.sip.SipRegistrationState
import com.sipoe.softphone.ui.account.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    accountViewModel: AccountViewModel = viewModel(),
) {
    val saved by accountViewModel.saved.collectAsStateWithLifecycle()
    val registration by accountViewModel.registration.collectAsStateWithLifecycle()
    val entries by DiagLog.entries.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val clipboard = LocalClipboard.current

    val account = saved
    val coreSummary = remember(registration, account) { SipCoreManager.debugSummary() }

    var selfTest by remember { mutableStateOf<NetworkSelfTest.Report?>(null) }
    var selfTestRunning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun runSelfTest() {
        val current = account ?: return
        if (selfTestRunning) return
        selfTestRunning = true
        scope.launch {
            selfTest = withContext(Dispatchers.IO) {
                NetworkSelfTest.run(context, current.serverHost, current.serverPort)
            }
            selfTestRunning = false
        }
    }

    LaunchedEffect(Unit) {
        DiagLog.refresh()
    }

    LaunchedEffect(account?.serverHost, account?.serverPort) {
        if (account != null && selfTest == null) {
            runSelfTest()
        }
    }
    val accountLines = buildList {
        if (account == null) {
            add(stringResource(R.string.diag_account_not_saved))
        } else {
            add(stringResource(R.string.diag_line_address, account.identityUri))
            add(stringResource(R.string.diag_line_server, account.serverAddress))
            add(
                stringResource(
                    R.string.diag_line_transport,
                    account.transport.label,
                    account.registerExpires,
                ),
            )
            add(
                stringResource(
                    R.string.diag_line_stun,
                    if (account.stunEnabled) {
                        account.stunServer
                    } else {
                        stringResource(R.string.diag_stun_off)
                    },
                ),
            )
        }
        add(
            stringResource(
                R.string.diag_line_registration,
                "${registration.status}${registration.message?.let { " · $it" } ?: ""}",
            ),
        )
        registration.errorDetail?.let { add(stringResource(R.string.diag_line_error, it)) }
        registration.identity?.let { add(stringResource(R.string.diag_line_identity, it)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diag_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val report = buildReport(
                                resources = resources,
                                account = account,
                                registration = registration,
                                coreSummary = coreSummary,
                                selfTestLines = selfTest?.lines.orEmpty(),
                                logs = DiagLog.snapshot(),
                            )
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(
                                        ClipData.newPlainText(
                                            resources.getString(R.string.diag_clip_label),
                                            report,
                                        ),
                                    ),
                                )
                                Toast.makeText(
                                    context,
                                    R.string.diag_copied,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                    ) {
                        Text(stringResource(R.string.common_copy))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { InfoCard(stringResource(R.string.diag_account), accountLines) }
                item { InfoCard(stringResource(R.string.diag_core), coreSummary) }
                item {
                    InfoCard(
                        title = stringResource(
                            if (selfTestRunning) {
                                R.string.diag_self_test_running
                            } else {
                                R.string.diag_self_test
                            },
                        ),
                        lines = selfTest?.lines ?: listOf(stringResource(R.string.diag_self_test_idle)),
                    )
                }
                if (selfTest?.restricted == true || registration.networkRestricted) {
                    item { RestrictionCard() }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { SipCoreManager.refreshRegistration() },
                        ) {
                            Text(stringResource(R.string.diag_register_again))
                        }
                        TextButton(onClick = { runSelfTest() }) {
                            Text(stringResource(R.string.diag_run_self_test))
                        }
                        TextButton(onClick = { DiagLog.refresh() }) {
                            Text(stringResource(R.string.diag_refresh))
                        }
                        TextButton(onClick = { DiagLog.clear() }) {
                            Text(stringResource(R.string.common_clear))
                        }
                    }
                }
                item {
                    Text(
                        text = pluralStringResource(R.plurals.diag_log_count, entries.size, entries.size),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                items(entries.asReversed()) { line ->
                    Text(
                        text = line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RestrictionCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.diag_restricted_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            listOf(
                R.string.diag_restricted_step_1,
                R.string.diag_restricted_step_2,
                R.string.diag_restricted_step_3,
                R.string.diag_restricted_step_4,
            ).forEach { lineRes ->
                Text(
                    text = stringResource(lineRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, lines: List<String>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            lines.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun buildReport(
    resources: Resources,
    account: AccountSettings?,
    registration: SipRegistrationState,
    coreSummary: List<String>,
    selfTestLines: List<String>,
    logs: String,
): String = buildString {
    appendLine(resources.getString(R.string.diag_report_title))
    appendLine(
        resources.getString(
            R.string.diag_report_time,
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
        ),
    )
    appendLine()
    appendLine(resources.getString(R.string.diag_report_section_account))
    if (account == null) {
        appendLine(resources.getString(R.string.diag_account_not_saved))
    } else {
        appendLine("identity: ${account.identityUri}")
        appendLine("server: ${account.serverAddress}")
        appendLine("transport: ${account.transport.label}")
        appendLine("expires: ${account.registerExpires}")
        appendLine("stun: ${if (account.stunEnabled) account.stunServer else "off"}")
    }
    appendLine()
    appendLine(resources.getString(R.string.diag_report_section_registration))
    appendLine("status: ${registration.status}")
    appendLine("message: ${registration.message}")
    appendLine("detail: ${registration.errorDetail}")
    appendLine()
    appendLine(resources.getString(R.string.diag_report_section_core))
    coreSummary.forEach { appendLine(it) }
    appendLine()
    appendLine(resources.getString(R.string.diag_report_section_self_test))
    selfTestLines.forEach { appendLine(it) }
    appendLine()
    appendLine(resources.getString(R.string.diag_report_section_logs))
    append(logs)
}
