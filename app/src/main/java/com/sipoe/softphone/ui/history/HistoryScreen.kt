package com.sipoe.softphone.ui.history

import android.content.res.Resources
import android.view.HapticFeedbackConstants
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sipoe.softphone.R
import com.sipoe.softphone.data.CallLogDirection
import com.sipoe.softphone.data.CallLogEntry
import com.sipoe.softphone.sip.RegistrationStatus
import com.sipoe.softphone.sip.SipCoreManager
import com.sipoe.softphone.ui.theme.SipoeTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val registration by SipCoreManager.registration.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<CallLogEntry?>(null) }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current

    fun redial(entry: CallLogEntry) {
        if (registration.status != RegistrationStatus.Registered) {
            scope.launch {
                snackbarHostState.showSnackbar(resources.getString(R.string.dialer_not_registered))
            }
        }
        viewModel.redial(entry)
    }

    fun deleteWithUndo(entry: CallLogEntry) {
        viewModel.remove(entry)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = resources.getString(R.string.history_deleted, entry.number),
                actionLabel = resources.getString(R.string.history_undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restore(entry)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        TextButton(onClick = { showClearDialog = true }) {
                            Text(stringResource(R.string.common_clear))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            EmptyHistory(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        } else {
            val groups = remember(entries, resources) {
                entries.groupBy { dayLabel(resources, it.timestamp) }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                groups.forEach { (label, dayEntries) ->
                    item(key = "header-$label") { DayHeader(label) }
                    items(dayEntries, key = { it.id }) { entry ->
                        SwipeToDeleteRow(
                            onDelete = { deleteWithUndo(entry) },
                        ) {
                            CallLogRow(
                                entry = entry,
                                onClick = { redial(entry) },
                                onRedial = { redial(entry) },
                                onLongClick = { pendingDelete = entry },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 68.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = { Text(entry.number) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteWithUndo(entry)
                        pendingDelete = null
                    },
                ) {
                    Text(
                        text = stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(pluralStringResource(R.plurals.history_clear_message, entries.size, entries.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAll()
                        showClearDialog = false
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
private fun SwipeToDeleteRow(
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val dismissState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        onDismiss = {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onDelete()
        },
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.common_delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background),
        ) {
            content()
        }
    }
}

@Composable
private fun DayHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

@Composable
private fun EmptyHistory(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.ic_history),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.history_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.history_empty_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallLogRow(
    entry: CallLogEntry,
    onClick: () -> Unit,
    onRedial: () -> Unit,
    onLongClick: () -> Unit,
) {
    val sipoe = SipoeTheme.colors
    val resources = LocalResources.current
    val (symbol, color) = when (entry.direction) {
        CallLogDirection.Outgoing -> "↗" to MaterialTheme.colorScheme.primary
        CallLogDirection.Incoming -> "↙" to sipoe.accept
        CallLogDirection.Missed -> "↙" to MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 20.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = symbol, color = color, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.number,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (entry.direction == CallLogDirection.Missed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = buildString {
                    append(stringResource(entry.direction.labelRes))
                    if (entry.durationSeconds > 0) {
                        append(" · ")
                        append(formatDuration(resources, entry.durationSeconds))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = shortTime(entry.timestamp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onRedial) {
            Icon(
                painter = painterResource(R.drawable.ic_phone),
                contentDescription = stringResource(R.string.history_redial),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@get:StringRes
private val CallLogDirection.labelRes: Int
    get() = when (this) {
        CallLogDirection.Incoming -> R.string.history_direction_incoming
        CallLogDirection.Outgoing -> R.string.history_direction_outgoing
        CallLogDirection.Missed -> R.string.history_direction_missed
    }

private fun formatDuration(resources: Resources, seconds: Int): String =
    if (seconds >= 60) {
        resources.getString(R.string.history_duration_minutes, seconds / 60, seconds % 60)
    } else {
        resources.getString(R.string.history_duration_seconds, seconds)
    }

private fun shortTime(timestamp: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val pattern = if (sameDay) "HH:mm" else "MM-dd"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
}

private fun dayLabel(resources: Resources, timestamp: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }

    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return resources.getString(R.string.history_today)

    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val isYesterday = yesterday.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        yesterday.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    if (isYesterday) return resources.getString(R.string.history_yesterday)

    val beforeYesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -2) }
    val isBeforeYesterday = beforeYesterday.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        beforeYesterday.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    if (isBeforeYesterday) return resources.getString(R.string.history_day_before_yesterday)

    if (now.timeInMillis - timestamp < 7L * 24 * 60 * 60 * 1000) {
        return SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestamp))
    }

    val pattern = resources.getString(R.string.history_date_format)
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
}
