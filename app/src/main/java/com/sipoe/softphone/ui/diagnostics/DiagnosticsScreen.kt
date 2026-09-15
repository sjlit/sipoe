package com.sipoe.softphone.ui.diagnostics

import android.content.ClipData
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
                NetworkSelfTest.run(current.serverHost, current.serverPort)
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
            add("未保存")
        } else {
            add("地址: ${account.identityUri}")
            add("服务器: ${account.serverAddress}")
            add("传输: ${account.transport.label} 有效期: ${account.registerExpires}s")
            add("STUN: ${if (account.stunEnabled) account.stunServer else "关闭"}")
        }
        add("注册: ${registration.status}${registration.message?.let { " · $it" } ?: ""}")
        registration.errorDetail?.let { add("错误: $it") }
        registration.identity?.let { add("身份: $it") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("诊断信息") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val report = buildReport(
                                account = account,
                                registration = registration,
                                coreSummary = coreSummary,
                                selfTestLines = selfTest?.lines.orEmpty(),
                                logs = DiagLog.snapshot(),
                            )
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(ClipData.newPlainText("Sipoe 诊断报告", report)),
                                )
                                Toast.makeText(context, "已复制诊断信息", Toast.LENGTH_SHORT).show()
                            }
                        },
                    ) {
                        Text("复制")
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
                item { InfoCard("账号", accountLines) }
                item { InfoCard("内核", coreSummary) }
                item {
                    InfoCard(
                        title = if (selfTestRunning) "网络自检(进行中…)" else "网络自检",
                        lines = selfTest?.lines ?: listOf("未运行"),
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
                            Text("重新注册")
                        }
                        TextButton(onClick = { runSelfTest() }) { Text("重新自检") }
                        TextButton(onClick = { DiagLog.refresh() }) { Text("刷新") }
                        TextButton(onClick = { DiagLog.clear() }) { Text("清空") }
                    }
                }
                item {
                    Text(
                        text = "日志 ${entries.size} 行(最新在上)",
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
                text = "系统拦截了应用联网(EPERM)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            listOf(
                "1. 设置 → 应用管理 → Sipoe → 联网控制/流量:允许 WiFi 和移动数据",
                "2. 手机管家/安全中心 → 网络助手/防火墙:放行 Sipoe",
                "3. VPN/代理类应用的分应用规则:勾选 Sipoe,或关闭\"禁止未选择应用联网\"",
                "4. 关闭对该应用的省电/后台限制后重试",
            ).forEach { line ->
                Text(
                    text = line,
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
    account: AccountSettings?,
    registration: SipRegistrationState,
    coreSummary: List<String>,
    selfTestLines: List<String>,
    logs: String,
): String = buildString {
    appendLine("=== Sipoe 诊断报告 ===")
    appendLine("时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
    appendLine()
    appendLine("--- 账号 ---")
    if (account == null) {
        appendLine("(未保存)")
    } else {
        appendLine("identity: ${account.identityUri}")
        appendLine("server: ${account.serverAddress}")
        appendLine("transport: ${account.transport.label}")
        appendLine("expires: ${account.registerExpires}")
        appendLine("stun: ${if (account.stunEnabled) account.stunServer else "off"}")
    }
    appendLine()
    appendLine("--- 注册 ---")
    appendLine("status: ${registration.status}")
    appendLine("message: ${registration.message}")
    appendLine("detail: ${registration.errorDetail}")
    appendLine()
    appendLine("--- 内核 ---")
    coreSummary.forEach { appendLine(it) }
    appendLine()
    appendLine("--- 网络自检 ---")
    selfTestLines.forEach { appendLine(it) }
    appendLine()
    appendLine("--- 日志 ---")
    append(logs)
}
