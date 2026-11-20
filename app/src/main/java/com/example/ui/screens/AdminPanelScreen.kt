package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AppListing
import com.example.data.model.AppSubmission
import com.example.data.model.AppVersion
import com.example.data.model.ParsedApkMetadata
import com.example.data.model.UiState
import com.example.data.repository.AdminDashboardSnapshot
import com.example.ui.components.AppIconBox
import com.example.ui.components.StatusChip
import com.example.ui.components.formatBytes
import com.example.ui.viewmodel.AdminSection
import com.example.ui.viewmodel.ReviewFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    uiState: UiState<AdminDashboardSnapshot>,
    selectedSection: AdminSection,
    reviewFilter: ReviewFilter,
    resolvedLinks: Map<String, String>,
    busy: Boolean,
    feedbackMessage: String?,
    parsedApk: ParsedApkMetadata? = null,
    parsingApk: Boolean = false,
    onInspectApk: (Uri) -> Unit = {},
    onClearParsedApk: () -> Unit = {},
    onSetApkUrl: (AppListing, AppVersion?, String) -> Unit = { _, _, _ -> },
    onUploadApkForApp: (AppListing, AppVersion?) -> Unit = { _, _ -> },
    onClearFeedback: () -> Unit,
    onRefresh: () -> Unit,
    onSelectSection: (AdminSection) -> Unit,
    onSelectReviewFilter: (ReviewFilter) -> Unit,
    onApproveApp: (AppListing, AppVersion?, AppSubmission?) -> Unit,
    onRejectApp: (AppListing, AppVersion?, AppSubmission?, String) -> Unit,
    onSetAppStatus: (String, String) -> Unit,
    onToggleFeatured: (AppListing) -> Unit,
    onToggleEditorsChoice: (AppListing) -> Unit,
    onGenerateApkLink: (AppListing, AppVersion) -> Unit,
    onSeedDefaultCategories: () -> Unit,
    onAddCategory: (String, String, String, Long) -> Unit,
    onToggleCategory: (String, Boolean) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onSetUserStatus: (String, String) -> Unit,
    onSetDeveloperStatus: (String, String) -> Unit,
    onResolveReport: (String, String) -> Unit,
    onRemoveReview: (String) -> Unit,
    onSendAnnouncement: (String, String, String) -> Unit,
    onSaveSettings: (String, String, Long, Boolean) -> Unit,
    onBack: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(feedbackMessage) {
        if (!feedbackMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(feedbackMessage)
            onClearFeedback()
        }
    }

    var rejectTarget by remember { mutableStateOf<Triple<AppListing, AppVersion?, AppSubmission?>?>(null) }
    var rejectReason by remember { mutableStateOf("") }
    var attachTarget by remember { mutableStateOf<Pair<AppListing, AppVersion?>?>(null) }
    var attachUrlInput by remember { mutableStateOf("") }
    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onInspectApk)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Admin Control Center", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("admin_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, modifier = Modifier.testTag("admin_refresh_button")) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh admin data")
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
            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AdminSection.entries.forEach { section ->
                    FilterChip(
                        selected = selectedSection == section,
                        onClick = { onSelectSection(section) },
                        label = { Text(section.label) },
                        modifier = Modifier.testTag("admin_section_${section.name.lowercase()}")
                    )
                }
            }

            when (uiState) {
                is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is UiState.Error -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(uiState.message, color = MaterialTheme.colorScheme.error)
                        Button(onClick = onRefresh) { Text("Retry") }
                    }
                }
                is UiState.Success -> {
                    val data = uiState.data
                    when (selectedSection) {
                        AdminSection.OVERVIEW -> OverviewSection(
                            data = data,
                            onGoToReviewPending = {
                                onSelectReviewFilter(ReviewFilter.PENDING)
                                onSelectSection(AdminSection.REVIEW_CENTER)
                            },
                            onSeedCategories = onSeedDefaultCategories
                        )
                        AdminSection.REVIEW_CENTER -> ReviewCenterSection(
                            data = data,
                            reviewFilter = reviewFilter,
                            resolvedLinks = resolvedLinks,
                            onSelectFilter = onSelectReviewFilter,
                            onApprove = onApproveApp,
                            onRequestReject = { app, ver, sub ->
                                rejectReason = ""
                                rejectTarget = Triple(app, ver, sub)
                            },
                            onSetStatus = onSetAppStatus,
                            onToggleFeatured = onToggleFeatured,
                            onToggleEditorsChoice = onToggleEditorsChoice,
                            onGenerateApkLink = onGenerateApkLink,
                            onOpenAttachApk = { app, ver, currentUrl ->
                                onClearParsedApk()
                                attachUrlInput = currentUrl
                                attachTarget = app to ver
                            }
                        )
                        AdminSection.CATEGORIES -> CategoriesSection(
                            data = data,
                            onSeedDefaults = onSeedDefaultCategories,
                            onAddCategory = onAddCategory,
                            onToggleCategory = onToggleCategory,
                            onDeleteCategory = onDeleteCategory
                        )
                        AdminSection.USERS -> UsersAndDevsSection(
                            data = data,
                            onSetUserStatus = onSetUserStatus,
                            onSetDeveloperStatus = onSetDeveloperStatus
                        )
                        AdminSection.MODERATION -> ModerationSection(
                            data = data,
                            onResolveReport = onResolveReport,
                            onRemoveReview = onRemoveReview
                        )
                        AdminSection.SETTINGS -> BroadcastAndSettingsSection(
                            data = data,
                            onSendAnnouncement = onSendAnnouncement,
                            onSaveSettings = onSaveSettings
                        )
                    }
                }
            }
        }
    }

    rejectTarget?.let { (app, ver, sub) ->
        AlertDialog(
            onDismissRequest = { rejectTarget = null },
            title = { Text("Reject ${app.appName}") },
            text = {
                OutlinedTextField(
                    value = rejectReason,
                    onValueChange = { rejectReason = it },
                    label = { Text("Rejection reason for developer") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        rejectTarget = null
                        onRejectApp(app, ver, sub, rejectReason)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Reject App") }
            },
            dismissButton = {
                TextButton(onClick = { rejectTarget = null }) { Text("Cancel") }
            }
        )
    }

    attachTarget?.let { (app, ver) ->
        AlertDialog(
            onDismissRequest = {
                attachTarget = null
                onClearParsedApk()
            },
            title = { Text("Attach APK for ${app.appName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Pick an .apk file from your phone to upload, or paste a direct HTTPS APK URL (e.g. GitHub Releases / Google Drive).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(
                        onClick = {
                            apkPicker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "*/*"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (parsedApk == null) "Select .apk File" else "${parsedApk.fileName} (${formatBytes(parsedApk.fileSizeBytes)})")
                    }
                    if (parsingApk) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    if (parsedApk != null) {
                        Button(
                            onClick = {
                                val targetApp = app
                                val targetVer = ver
                                attachTarget = null
                                onUploadApkForApp(targetApp, targetVer)
                            },
                            enabled = parsedApk.validStructure && !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Upload & Attach Selected APK")
                        }
                    }
                    HorizontalDivider()
                    OutlinedTextField(
                        value = attachUrlInput,
                        onValueChange = { attachUrlInput = it },
                        label = { Text("Direct HTTPS APK URL") },
                        placeholder = { Text("https://.../app-release.apk") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val url = attachUrlInput.trim()
                        attachTarget = null
                        onSetApkUrl(app, ver, url)
                    },
                    enabled = attachUrlInput.trim().startsWith("http") && !busy
                ) {
                    Text("Save APK URL")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    attachTarget = null
                    onClearParsedApk()
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun OverviewSection(
    data: AdminDashboardSnapshot,
    onGoToReviewPending: () -> Unit,
    onSeedCategories: () -> Unit
) {
    val approvedCount = data.apps.count { it.status == "approved" }
    val pendingCount = data.apps.count { it.status in listOf("pending", "under_review", "validating") }
    val totalDownloads = data.apps.sumOf { it.downloadCount }
    val openReports = data.reports.count { it.status == "open" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminKpiCard("Pending Review", pendingCount.toString(), Modifier.weight(1f))
                AdminKpiCard("Published Apps", approvedCount.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminKpiCard("Total Users", data.users.size.toString(), Modifier.weight(1f))
                AdminKpiCard("Developers", data.developers.size.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminKpiCard("Total Downloads", totalDownloads.toString(), Modifier.weight(1f))
                AdminKpiCard("Open Reports", openReports.toString(), Modifier.weight(1f))
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Quick Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Button(
                        onClick = onGoToReviewPending,
                        modifier = Modifier.fillMaxWidth().testTag("admin_review_pending_btn")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Review Pending Apps ($pendingCount)")
                    }
                    OutlinedButton(
                        onClick = onSeedCategories,
                        modifier = Modifier.fillMaxWidth().testTag("admin_seed_categories_btn")
                    ) {
                        Text("Seed All 24 Default Categories (${data.categories.size} active)")
                    }
                }
            }
        }
        if (data.logs.isNotEmpty()) {
            item { Text("Recent Admin Logs", style = MaterialTheme.typography.titleSmall) }
            items(data.logs.take(10), key = { it.id }) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(log.action, style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace)
                            Text("${log.targetType}/${log.targetId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (log.notes.isNotBlank()) {
                            Text(log.notes, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewCenterSection(
    data: AdminDashboardSnapshot,
    reviewFilter: ReviewFilter,
    resolvedLinks: Map<String, String>,
    onSelectFilter: (ReviewFilter) -> Unit,
    onApprove: (AppListing, AppVersion?, AppSubmission?) -> Unit,
    onRequestReject: (AppListing, AppVersion?, AppSubmission?) -> Unit,
    onSetStatus: (String, String) -> Unit,
    onToggleFeatured: (AppListing) -> Unit,
    onToggleEditorsChoice: (AppListing) -> Unit,
    onGenerateApkLink: (AppListing, AppVersion) -> Unit,
    onOpenAttachApk: (AppListing, AppVersion?, String) -> Unit
) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    val filtered = remember(data.apps, reviewFilter) {
        data.apps.filter { app ->
            when (reviewFilter) {
                ReviewFilter.ALL -> true
                ReviewFilter.PENDING -> app.status in listOf("pending", "under_review", "validating")
                ReviewFilter.APPROVED -> app.status == "approved"
                ReviewFilter.REJECTED -> app.status in listOf("rejected", "suspended")
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReviewFilter.entries.forEach { f ->
                    FilterChip(
                        selected = reviewFilter == f,
                        onClick = { onSelectFilter(f) },
                        label = { Text(f.label) }
                    )
                }
            }
        }
        if (filtered.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "No apps match this filter.",
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filtered, key = { it.appId.ifBlank { it.id } }) { app ->
                val appId = app.appId.ifBlank { app.id }
                val sub = data.submissions.firstOrNull { it.appId == appId }
                val version = remember(app, sub, data.versions) {
                    sub?.versionId?.takeIf { it.isNotBlank() }?.let { vid ->
                        data.versions.firstOrNull { it.versionId == vid || it.id == vid }
                    } ?: data.versions
                        .filter { it.appId == appId }
                        .sortedByDescending { it.versionCode }
                        .firstOrNull()
                }
                val verKey = version?.let { it.versionId.ifBlank { it.id } }.orEmpty()
                val downloadUrl = resolvedLinks[verKey]
                    ?: version?.apkDownloadUrl?.trim()?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                    ?: ""

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AppIconBox(iconUrl = app.iconUrl, appName = app.appName, size = 48.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.appName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("${app.packageName} • v${app.versionName} (${app.versionCode})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("By ${app.developerName.ifBlank { app.ownerUid }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusChip(status = app.status)
                        }

                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (app.status != "approved") {
                                Button(onClick = { onApprove(app, version, sub) }) {
                                    Text("Approve & Publish")
                                }
                            }
                            if (app.status != "rejected") {
                                OutlinedButton(onClick = { onRequestReject(app, version, sub) }) {
                                    Text("Reject")
                                }
                            }
                            if (app.status == "approved") {
                                OutlinedButton(onClick = { onSetStatus(appId, "suspended") }) {
                                    Text("Suspend")
                                }
                            } else if (app.status == "suspended") {
                                Button(onClick = { onSetStatus(appId, "approved") }) {
                                    Text("Restore")
                                }
                            }
                        }

                        if (app.status == "approved") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = app.featured,
                                    onClick = { onToggleFeatured(app) },
                                    label = { Text(if (app.featured) "Featured ✓" else "Set Featured") }
                                )
                                FilterChip(
                                    selected = app.editorsChoice,
                                    onClick = { onToggleEditorsChoice(app) },
                                    label = { Text(if (app.editorsChoice) "Editor's Choice ✓" else "Set Editor's") }
                                )
                            }

                            HorizontalDivider()
                            Text("Published APK Link", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            if (downloadUrl.isNotBlank()) {
                                Text(
                                    text = downloadUrl,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (version != null && version.apkSize > 0L) {
                                    Text(
                                        text = "${formatBytes(version.apkSize)} • SHA-256: ${version.sha256.take(12)}...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = {
                                        clipboard.setText(AnnotatedString(downloadUrl))
                                        Toast.makeText(context, "APK download URL copied!", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy APK URL", modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Copy URL")
                                    }
                                    Button(onClick = {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)))
                                        }
                                    }) {
                                        Icon(Icons.Default.Download, contentDescription = "Download APK in browser", modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Download APK")
                                    }
                                    OutlinedButton(onClick = { onOpenAttachApk(app, version, downloadUrl) }) {
                                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Edit APK")
                                    }
                                }
                            } else {
                                Text(
                                    "Users cannot download until an APK file or direct HTTPS link is attached.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { onOpenAttachApk(app, version, "") }) {
                                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Attach APK / Set URL")
                                    }
                                    if (version != null && version.apkStoragePath.isNotBlank()) {
                                        OutlinedButton(onClick = { onGenerateApkLink(app, version) }) {
                                            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Resolve Link")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoriesSection(
    data: AdminDashboardSnapshot,
    onSeedDefaults: () -> Unit,
    onAddCategory: (String, String, String, Long) -> Unit,
    onToggleCategory: (String, Boolean) -> Unit,
    onDeleteCategory: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var nameBn by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("apps") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Manage Categories", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onSeedDefaults, modifier = Modifier.fillMaxWidth()) {
                        Text("Seed All 24 Default Categories")
                    }
                    HorizontalDivider()
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Category Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = nameBn, onValueChange = { nameBn = it }, label = { Text("Bengali Name (বাংলা)") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = group == "apps", onClick = { group = "apps" }, label = { Text("Apps") })
                        FilterChip(selected = group == "games", onClick = { group = "games" }, label = { Text("Games") })
                    }
                    OutlinedButton(
                        onClick = {
                            if (name.isNotBlank()) {
                                onAddCategory(name, nameBn, group, (data.categories.size + 1).toLong())
                                name = ""
                                nameBn = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Add Category") }
                }
            }
        }
        items(data.categories, key = { it.id }) { cat ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${cat.name} (${cat.nameBn})", style = MaterialTheme.typography.titleSmall)
                        Text("${cat.group} • Order ${cat.sortOrder}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = cat.enabled, onCheckedChange = { onToggleCategory(cat.id, it) })
                    IconButton(onClick = { onDeleteCategory(cat.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete category", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun UsersAndDevsSection(
    data: AdminDashboardSnapshot,
    onSetUserStatus: (String, String) -> Unit,
    onSetDeveloperStatus: (String, String) -> Unit
) {
    var search by remember { mutableStateOf("") }
    val users = remember(data.users, search) {
        if (search.isBlank()) data.users
        else data.users.filter {
            it.displayName.contains(search, true) || it.email.contains(search, true)
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Search users by name or email") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { Text("Developers (${data.developers.size})", style = MaterialTheme.typography.titleSmall) }
        items(data.developers, key = { "dev_${it.id}" }) { dev ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(dev.displayName, style = MaterialTheme.typography.titleSmall)
                        Text(dev.contactEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusChip(status = dev.status)
                    if (dev.status != "verified") {
                        Button(onClick = { onSetDeveloperStatus(dev.id, "verified") }) { Text("Verify") }
                    } else {
                        OutlinedButton(onClick = { onSetDeveloperStatus(dev.id, "suspended") }) { Text("Suspend") }
                    }
                }
            }
        }
        item { Text("Users (${users.size})", style = MaterialTheme.typography.titleSmall) }
        items(users, key = { "usr_${it.id}" }) { user ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusChip(status = user.accountStatus)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (user.accountStatus != "active") {
                            Button(onClick = { onSetUserStatus(user.id, "active") }) { Text("Restore") }
                        }
                        if (user.accountStatus != "suspended") {
                            OutlinedButton(onClick = { onSetUserStatus(user.id, "suspended") }) { Text("Suspend") }
                        }
                        if (user.accountStatus != "banned") {
                            OutlinedButton(onClick = { onSetUserStatus(user.id, "banned") }) { Text("Ban") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModerationSection(
    data: AdminDashboardSnapshot,
    onResolveReport: (String, String) -> Unit,
    onRemoveReview: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("App & Review Reports (${data.reports.size})", style = MaterialTheme.typography.titleSmall) }
        if (data.reports.isEmpty()) {
            item { Text("No user reports filed.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(data.reports, key = { it.id }) { r ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${r.targetType}: ${r.targetId}", style = MaterialTheme.typography.titleSmall)
                            StatusChip(status = r.status)
                        }
                        Text("Reason: ${r.reason} • ${r.details}", style = MaterialTheme.typography.bodySmall)
                        if (r.status == "open") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onResolveReport(r.id, "resolved") }) { Text("Resolve") }
                                OutlinedButton(onClick = { onResolveReport(r.id, "dismissed") }) { Text("Dismiss") }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(6.dp)); Text("Recent User Reviews (${data.reviews.size})", style = MaterialTheme.typography.titleSmall) }
        items(data.reviews.take(20), key = { it.id }) { rev ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${rev.userName} • ${rev.rating}★", style = MaterialTheme.typography.titleSmall)
                        Text(rev.comment, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    StatusChip(status = rev.moderationStatus)
                    if (rev.moderationStatus != "removed") {
                        OutlinedButton(onClick = { onRemoveReview(rev.id) }) { Text("Remove") }
                    }
                }
            }
        }
    }
}

@Composable
private fun BroadcastAndSettingsSection(
    data: AdminDashboardSnapshot,
    onSendAnnouncement: (String, String, String) -> Unit,
    onSaveSettings: (String, String, Long, Boolean) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var targetUid by remember { mutableStateOf("") }
    var platformName by remember(data.settings) { mutableStateOf(data.settings.platformName) }
    var supportEmail by remember(data.settings) { mutableStateOf(data.settings.supportEmail) }
    var maxUploadMb by remember(data.settings) { mutableStateOf(data.settings.maxUploadSizeMb.toString()) }
    var maintenance by remember(data.settings) { mutableStateOf(data.settings.maintenanceMode) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Send Global Announcement", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = targetUid, onValueChange = { targetUid = it }, label = { Text("Target User UID (optional, blank = everyone)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Message") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            if (title.isNotBlank() && body.isNotBlank()) {
                                onSendAnnouncement(title, body, targetUid)
                                title = ""
                                body = ""
                                targetUid = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Publish Notification") }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Platform Settings", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = platformName, onValueChange = { platformName = it }, label = { Text("Platform Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = supportEmail, onValueChange = { supportEmail = it }, label = { Text("Support Email") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = maxUploadMb, onValueChange = { maxUploadMb = it }, label = { Text("Max APK Upload Size (MB)") }, modifier = Modifier.fillMaxWidth())
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Maintenance Mode")
                        Switch(checked = maintenance, onCheckedChange = { maintenance = it })
                    }
                    Button(
                        onClick = { onSaveSettings(platformName, supportEmail, maxUploadMb.toLongOrNull() ?: 250L, maintenance) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save Platform Settings") }
                }
            }
        }
    }
}

@Composable
private fun AdminKpiCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}
