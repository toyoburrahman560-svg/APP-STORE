package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.R
import com.example.data.download.ActiveDownloadState
import com.example.data.model.DownloadPhase
import com.example.data.model.DownloadRecord
import com.example.ui.components.AppIconBox
import com.example.ui.components.EmptyStateView
import com.example.ui.components.StatusChip
import com.example.ui.components.formatBytes
import com.example.ui.components.formatEta
import com.example.ui.components.formatSpeed
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.EmeraldTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class DownloadFilterTab(val label: String) {
    ALL("All"),
    DOWNLOADING("Downloading"),
    PAUSED("Paused"),
    COMPLETED("Completed"),
    FAILED("Failed")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDownloadsScreen(
    activeDownloads: List<ActiveDownloadState>,
    historyRecords: List<DownloadRecord>,
    feedbackMessage: String?,
    onClearFeedback: () -> Unit,
    onRefreshInstalledStates: () -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
    onRemoveActive: (String, String?) -> Unit,
    onInstallActive: (ActiveDownloadState) -> Unit,
    onInstallRecord: (DownloadRecord) -> Unit,
    onRedownloadRecord: (String) -> Unit,
    onDeleteHistoryRecord: (DownloadRecord) -> Unit,
    getLocalApk: (String, Long) -> java.io.File?,
    canInstallPackages: () -> Boolean,
    onOpenInstallPermissionSettings: () -> Unit,
    onOpenApp: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedFilter by remember { mutableStateOf(DownloadFilterTab.ALL) }
    var showInstallPermDialog by remember { mutableStateOf(false) }
    var localSnackMessage by remember { mutableStateOf<String?>(null) }

    LifecycleResumeEffect(Unit) {
        onRefreshInstalledStates()
        onPauseOrDispose { }
    }

    LaunchedEffect(feedbackMessage, localSnackMessage) {
        val msg = feedbackMessage ?: localSnackMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            if (feedbackMessage != null) onClearFeedback()
            localSnackMessage = null
        }
    }

    val recordByAppId = remember(historyRecords) {
        historyRecords.associateBy { it.appId }
    }

    val activeIds = remember(activeDownloads) {
        activeDownloads.map { it.appId }.toSet()
    }

    val cloudOnlyRecords = remember(historyRecords, activeIds) {
        historyRecords.filter { it.appId !in activeIds }
    }

    val filteredActive = remember(activeDownloads, selectedFilter) {
        when (selectedFilter) {
            DownloadFilterTab.ALL -> activeDownloads
            DownloadFilterTab.DOWNLOADING -> activeDownloads.filter {
                it.phase == DownloadPhase.QUEUED ||
                    it.phase == DownloadPhase.DOWNLOADING ||
                    it.phase == DownloadPhase.VERIFYING
            }
            DownloadFilterTab.PAUSED -> activeDownloads.filter { it.phase == DownloadPhase.PAUSED }
            DownloadFilterTab.COMPLETED -> activeDownloads.filter {
                it.phase == DownloadPhase.READY_TO_INSTALL || it.phase == DownloadPhase.INSTALLED
            }
            DownloadFilterTab.FAILED -> activeDownloads.filter {
                it.phase == DownloadPhase.FAILED || it.phase == DownloadPhase.CANCELLED
            }
        }
    }

    val filteredCloudHistory = remember(cloudOnlyRecords, selectedFilter) {
        when (selectedFilter) {
            DownloadFilterTab.ALL -> cloudOnlyRecords
            DownloadFilterTab.DOWNLOADING -> cloudOnlyRecords.filter {
                it.status in setOf("queued", "downloading")
            }
            DownloadFilterTab.PAUSED -> cloudOnlyRecords.filter { it.status == "paused" }
            DownloadFilterTab.COMPLETED -> cloudOnlyRecords.filter {
                it.status in setOf("verified", "completed", "installed")
            }
            DownloadFilterTab.FAILED -> cloudOnlyRecords.filter {
                it.status in setOf("failed", "cancelled")
            }
        }
    }

    val activeCount = activeDownloads.count {
        it.phase == DownloadPhase.QUEUED || it.phase == DownloadPhase.DOWNLOADING || it.phase == DownloadPhase.VERIFYING
    }
    val pausedCount = activeDownloads.count { it.phase == DownloadPhase.PAUSED }
    val completedCount = activeDownloads.count {
        it.phase == DownloadPhase.READY_TO_INSTALL || it.phase == DownloadPhase.INSTALLED
    } + cloudOnlyRecords.count { it.status in setOf("verified", "completed", "installed") }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_downloads)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("downloads_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Security & Summary Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = EmeraldTertiary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SHA-256 & Signature Verified Downloads",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "$activeCount active • $pausedCount paused • $completedCount completed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Filter Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(DownloadFilterTab.entries) { tab ->
                    FilterChip(
                        selected = selectedFilter == tab,
                        onClick = { selectedFilter = tab },
                        label = { Text(tab.label) },
                        modifier = Modifier.testTag("downloads_filter_${tab.name.lowercase()}")
                    )
                }
            }

            if (filteredActive.isEmpty() && filteredCloudHistory.isEmpty()) {
                EmptyStateView(
                    title = "No downloads in ${selectedFilter.label}",
                    subtitle = "Approved apps downloaded from APP STORE continue safely in the background and appear here."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredActive, key = { "active_${it.appId}" }) { item ->
                        ActiveDownloadCard(
                            state = item,
                            onPause = { onPause(item.appId) },
                            onResume = { onResume(item.appId) },
                            onCancel = { onCancel(item.appId) },
                            onRetry = { onRetry(item.appId) },
                            onRemove = { onRemoveActive(item.appId, recordByAppId[item.appId]?.id) },
                            onInstall = {
                                if (canInstallPackages()) {
                                    onInstallActive(item)
                                } else {
                                    showInstallPermDialog = true
                                }
                            },
                            onLaunchApp = {
                                val launchIntent = context.packageManager.getLaunchIntentForPackage(item.packageName)
                                if (launchIntent != null) {
                                    context.startActivity(launchIntent)
                                } else {
                                    localSnackMessage = "${item.appName} has no launchable activity."
                                }
                            },
                            onOpenApp = { onOpenApp(item.appId) }
                        )
                    }

                    if (filteredCloudHistory.isNotEmpty()) {
                        item {
                            Text(
                                text = "Account Download History",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        items(filteredCloudHistory, key = { "cloud_${it.id}" }) { record ->
                            val hasLocalApk = getLocalApk(record.packageName, record.versionCode) != null
                            CloudDownloadHistoryCard(
                                record = record,
                                hasLocalApk = hasLocalApk,
                                onInstallLocal = {
                                    if (canInstallPackages()) {
                                        onInstallRecord(record)
                                    } else {
                                        showInstallPermDialog = true
                                    }
                                },
                                onRedownload = { onRedownloadRecord(record.appId) },
                                onDelete = { onDeleteHistoryRecord(record) },
                                onOpenApp = { onOpenApp(record.appId) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showInstallPermDialog) {
        AlertDialog(
            onDismissRequest = { showInstallPermDialog = false },
            title = { Text("Allow App Installations") },
            text = {
                Text("Android requires granting APP STORE permission to install APKs from unknown sources using the official system Package Installer.")
            },
            confirmButton = {
                Button(onClick = {
                    showInstallPermDialog = false
                    onOpenInstallPermissionSettings()
                }) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInstallPermDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ActiveDownloadCard(
    state: ActiveDownloadState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onInstall: () -> Unit,
    onLaunchApp: () -> Unit,
    onOpenApp: () -> Unit
) {
    val dateText = remember(state.updatedAtMillis) {
        SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(state.updatedAtMillis))
    }

    val statusText = when (state.phase) {
        DownloadPhase.QUEUED -> "Queued"
        DownloadPhase.DOWNLOADING -> "Downloading"
        DownloadPhase.PAUSED -> "Paused"
        DownloadPhase.VERIFYING -> "Verifying"
        DownloadPhase.READY_TO_INSTALL -> "Verified"
        DownloadPhase.INSTALLED -> "Installed"
        DownloadPhase.FAILED -> "Failed"
        DownloadPhase.CANCELLED -> "Cancelled"
        DownloadPhase.IDLE -> "Idle"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenApp)
            .testTag("download_card_${state.appId}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppIconBox(iconUrl = state.iconUrl, appName = state.appName, size = 54.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = state.appName.ifBlank { state.packageName },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "v${state.versionName} (${state.versionCode}) • ${if (state.totalBytes > 0L) formatBytes(state.totalBytes) else formatBytes(state.bytesDownloaded)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusChip(status = statusText)
            }

            if (state.phase == DownloadPhase.QUEUED ||
                state.phase == DownloadPhase.DOWNLOADING ||
                state.phase == DownloadPhase.PAUSED ||
                state.phase == DownloadPhase.VERIFYING
            ) {
                val progressRatio = (state.progressPercent / 100f).coerceIn(0f, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (state.phase == DownloadPhase.QUEUED || state.phase == DownloadPhase.VERIFYING) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(50))
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { progressRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(50))
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val sizeProgress = if (state.totalBytes > 0L) {
                            "${formatBytes(state.bytesDownloaded)} / ${formatBytes(state.totalBytes)} (${state.progressPercent}%)"
                        } else {
                            "${formatBytes(state.bytesDownloaded)} downloaded"
                        }
                        Text(
                            text = when (state.phase) {
                                DownloadPhase.QUEUED -> "Connecting securely to Firebase Storage…"
                                DownloadPhase.VERIFYING -> "Checking SHA-256 & APK signature…"
                                DownloadPhase.PAUSED -> "Paused • $sizeProgress"
                                else -> sizeProgress
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (state.phase == DownloadPhase.DOWNLOADING && state.speedBytesPerSec > 0L) {
                            val etaStr = formatEta(state.etaSeconds)
                            val telemetry = if (etaStr.isNotBlank()) {
                                "${formatSpeed(state.speedBytesPerSec)} • $etaStr"
                            } else {
                                formatSpeed(state.speedBytesPerSec)
                            }
                            Text(
                                text = telemetry,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (state.phase == DownloadPhase.FAILED && !state.errorMessage.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = state.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (state.phase) {
                    DownloadPhase.QUEUED, DownloadPhase.DOWNLOADING -> {
                        OutlinedButton(
                            onClick = onPause,
                            modifier = Modifier.testTag("pause_download_${state.appId}")
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.pause))
                        }
                        TextButton(
                            onClick = onCancel,
                            modifier = Modifier.testTag("cancel_download_${state.appId}")
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cancel")
                        }
                    }

                    DownloadPhase.VERIFYING -> {
                        Text(
                            text = "Verifying package integrity…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    DownloadPhase.PAUSED -> {
                        Button(
                            onClick = onResume,
                            modifier = Modifier.testTag("resume_download_${state.appId}")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.resume))
                        }
                        TextButton(onClick = onCancel) {
                            Text("Cancel")
                        }
                    }

                    DownloadPhase.READY_TO_INSTALL -> {
                        Button(
                            onClick = onInstall,
                            modifier = Modifier.testTag("install_download_${state.appId}")
                        ) {
                            Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Install")
                        }
                        TextButton(onClick = onRemove) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Remove")
                        }
                    }

                    DownloadPhase.INSTALLED -> {
                        OutlinedButton(
                            onClick = onLaunchApp,
                            modifier = Modifier.testTag("open_installed_${state.appId}")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open")
                        }
                        TextButton(onClick = onRemove) {
                            Text("Clear")
                        }
                    }

                    DownloadPhase.FAILED, DownloadPhase.CANCELLED -> {
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.testTag("retry_download_${state.appId}")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.retry))
                        }
                        TextButton(onClick = onRemove) {
                            Text("Remove")
                        }
                    }

                    DownloadPhase.IDLE -> Unit
                }
            }
        }
    }
}

@Composable
private fun CloudDownloadHistoryCard(
    record: DownloadRecord,
    hasLocalApk: Boolean,
    onInstallLocal: () -> Unit,
    onRedownload: () -> Unit,
    onDelete: () -> Unit,
    onOpenApp: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenApp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppIconBox(iconUrl = record.iconUrl, appName = record.appName, size = 48.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(record.appName, style = MaterialTheme.typography.titleSmall)
                    val sizeText = if (record.fileSizeBytes > 0L) " • ${formatBytes(record.fileSizeBytes)}" else ""
                    Text(
                        text = "v${record.versionName}$sizeText • ${formatTimestamp(record.updatedAt ?: record.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusChip(status = record.status)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasLocalApk) {
                    Button(onClick = onInstallLocal) {
                        Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Install")
                    }
                } else {
                    OutlinedButton(onClick = onRedownload) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Download")
                    }
                }
                TextButton(onClick = onOpenApp) {
                    Text("Details")
                }
                TextButton(onClick = onDelete) {
                    Text("Remove")
                }
            }
        }
    }
}
