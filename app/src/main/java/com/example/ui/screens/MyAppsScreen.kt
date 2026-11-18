package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import com.example.data.model.AppListing
import com.example.data.model.AppSubmission
import com.example.data.model.ParsedApkMetadata
import com.example.data.model.UiState
import com.example.ui.components.AppIconBox
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.StatusChip
import com.example.ui.components.formatCompactCount
import com.example.ui.viewmodel.DeveloperDashboardSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyAppsScreen(
    dashboardState: UiState<DeveloperDashboardSummary>,
    parsedApk: ParsedApkMetadata?,
    actionMessage: String?,
    onClearMessage: () -> Unit,
    onInspectApk: (Uri) -> Unit,
    onUpdateMetadata: (String, String, String, String, String, String, String, Boolean) -> Unit,
    onUploadNewVersion: (AppListing, String, String) -> Unit,
    onArchiveApp: (String) -> Unit,
    onOpenAppDetail: (String) -> Unit,
    onBack: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val statusFilters = listOf("all", "approved", "pending", "rejected", "suspended", "archived")
    var selectedFilter by remember { mutableStateOf("all") }
    var editingApp by remember { mutableStateOf<AppListing?>(null) }
    var updatingVersionApp by remember { mutableStateOf<AppListing?>(null) }

    LaunchedEffect(actionMessage) {
        if (!actionMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(actionMessage)
            onClearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("My Applications") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        when (dashboardState) {
            is UiState.Loading -> {
                Column(modifier = Modifier.padding(innerPadding).padding(20.dp)) {
                    Text("Loading your applications…")
                }
            }

            is UiState.Error -> {
                Column(modifier = Modifier.padding(innerPadding)) {
                    ErrorStateView(message = dashboardState.message, onRetry = onBack)
                }
            }

            is UiState.Success -> {
                val summary = dashboardState.data
                val filteredApps = if (selectedFilter == "all") summary.apps
                else summary.apps.filter { it.status.equals(selectedFilter, ignoreCase = true) }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(statusFilters) { st ->
                                FilterChip(
                                    selected = selectedFilter == st,
                                    onClick = { selectedFilter = st },
                                    label = { Text(st.replaceFirstChar { it.uppercase() }) }
                                )
                            }
                        }
                    }

                    if (filteredApps.isEmpty()) {
                        item {
                            EmptyStateView(
                                title = "No applications in '$selectedFilter'",
                                subtitle = "Applications you submit for review appear here with live approval status."
                            )
                        }
                    } else {
                        items(filteredApps, key = { it.appId }) { app ->
                            val latestSubmission: AppSubmission? = summary.submissions
                                .firstOrNull { it.appId == app.appId }

                            MyAppManagementCard(
                                app = app,
                                latestSubmission = latestSubmission,
                                onOpenDetail = { onOpenAppDetail(app.appId) },
                                onEditClick = { editingApp = app },
                                onUploadVersionClick = { updatingVersionApp = app },
                                onArchiveClick = { onArchiveApp(app.appId) }
                            )
                        }
                    }
                }

                editingApp?.let { target ->
                    EditMetadataDialog(
                        app = target,
                        onDismiss = { editingApp = null },
                        onSave = { name, shortD, fullD, whatsNew, web, priv, resubmit ->
                            editingApp = null
                            onUpdateMetadata(target.appId, name, shortD, fullD, whatsNew, web, priv, resubmit)
                        }
                    )
                }

                updatingVersionApp?.let { target ->
                    UploadNewVersionDialog(
                        app = target,
                        parsedApk = parsedApk,
                        onInspectApk = onInspectApk,
                        onDismiss = { updatingVersionApp = null },
                        onSubmit = { url, notes ->
                            updatingVersionApp = null
                            onUploadNewVersion(target, url, notes)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MyAppManagementCard(
    app: AppListing,
    latestSubmission: AppSubmission?,
    onOpenDetail: () -> Unit,
    onEditClick: () -> Unit,
    onUploadVersionClick: () -> Unit,
    onArchiveClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppIconBox(iconUrl = app.iconUrl, appName = app.appName, size = 56.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.appName, style = MaterialTheme.typography.titleMedium)
                    Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("v${app.versionName} • ${formatCompactCount(app.downloadCount)} downloads", style = MaterialTheme.typography.bodySmall)
                }
                StatusChip(status = app.status)
            }

            if (!latestSubmission?.rejectionReason.isNullOrBlank()) {
                Text(
                    text = "Review Note: ${latestSubmission!!.rejectionReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenDetail, modifier = Modifier.weight(1f)) { Text("View") }
                if (app.status != "suspended") {
                    OutlinedButton(onClick = onEditClick, modifier = Modifier.weight(1f)) {
                        Text(if (app.status == "rejected") "Fix & Resubmit" else "Edit")
                    }
                }
            }
            if (app.status != "suspended" && app.status != "archived") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onUploadVersionClick, modifier = Modifier.weight(1f)) { Text("New Version") }
                    TextButton(onClick = onArchiveClick) { Text("Archive") }
                }
            }
        }
    }
}

@Composable
private fun EditMetadataDialog(
    app: AppListing,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(app.appName) }
    var shortD by remember { mutableStateOf(app.shortDescription) }
    var fullD by remember { mutableStateOf(app.description) }
    var whatsNew by remember { mutableStateOf(app.whatsNew) }
    var web by remember { mutableStateOf(app.websiteUrl) }
    var priv by remember { mutableStateOf(app.privacyPolicyUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Application") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("App Name") })
                OutlinedTextField(value = shortD, onValueChange = { shortD = it }, label = { Text("Short Description") })
                OutlinedTextField(value = fullD, onValueChange = { fullD = it }, label = { Text("Full Description") }, minLines = 2)
                OutlinedTextField(value = whatsNew, onValueChange = { whatsNew = it }, label = { Text("What's New") })
                OutlinedTextField(value = web, onValueChange = { web = it }, label = { Text("Website URL") })
                OutlinedTextField(value = priv, onValueChange = { priv = it }, label = { Text("Privacy Policy URL") })
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(name, shortD, fullD, whatsNew, web, priv, app.status == "rejected")
            }) { Text(if (app.status == "rejected") "Resubmit" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun UploadNewVersionDialog(
    app: AppListing,
    parsedApk: ParsedApkMetadata?,
    onInspectApk: (Uri) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var apkUrl by remember { mutableStateOf("") }
    var whatsNew by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onInspectApk)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Upload New APK (${app.appName})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Current versionCode: ${app.versionCode}. New APK must match package ${app.packageName} and have a higher versionCode.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { picker.launch(arrayOf("application/vnd.android.package-archive", "*/*")) }) {
                    Text(if (parsedApk == null) "Select New APK" else "Picked: v${parsedApk.versionName} (${parsedApk.versionCode})")
                }
                OutlinedTextField(value = apkUrl, onValueChange = { apkUrl = it }, label = { Text("APK Release URL") })
                OutlinedTextField(value = whatsNew, onValueChange = { whatsNew = it }, label = { Text("Release Notes") })
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(apkUrl, whatsNew) },
                enabled = parsedApk != null && parsedApk.validStructure
            ) { Text("Submit Update") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
