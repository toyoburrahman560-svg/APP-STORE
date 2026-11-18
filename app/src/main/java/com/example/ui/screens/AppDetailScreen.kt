package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil.compose.AsyncImage
import com.example.R
import com.example.data.download.ActiveDownloadState
import com.example.data.model.AppListing
import com.example.data.model.AppReview
import com.example.data.model.AppVersion
import com.example.data.model.DownloadPhase
import com.example.data.model.UiState
import com.example.ui.components.AppHorizontalCard
import com.example.ui.components.AppIconBox
import com.example.ui.components.ErrorStateView
import com.example.ui.components.StatusChip
import com.example.ui.components.formatBytes
import com.example.ui.components.formatCompactCount
import com.example.ui.components.formatEta
import com.example.ui.components.formatSpeed
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.AmberStar
import com.example.ui.theme.EmeraldTertiary
import com.example.ui.viewmodel.AppDetailData
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    detailState: UiState<AppDetailData>,
    downloadState: ActiveDownloadState?,
    currentUserId: String?,
    isSavedInWishlist: Boolean,
    feedbackMessage: String?,
    onClearFeedback: () -> Unit,
    onBack: () -> Unit,
    onRefreshInstallStatus: () -> Unit,
    onDownloadClick: (AppListing, AppVersion?) -> Unit,
    onPauseDownload: () -> Unit,
    onResumeDownload: () -> Unit,
    onRetryDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstallClick: (AppListing, AppVersion?) -> Unit,
    onOpenDownloads: () -> Unit,
    onToggleWishlist: (AppListing, Boolean) -> Unit,
    onSubmitReview: (Int, String) -> Unit,
    onDeleteReview: () -> Unit,
    onMarkHelpful: (String) -> Unit,
    onReplyToReview: (String, String) -> Unit,
    onSubmitReport: (String, String, String, String) -> Unit,
    onOpenSimilarApp: (String) -> Unit,
    onRequireAuth: () -> Unit,
    canInstallPackages: () -> Boolean,
    onOpenInstallPermissionSettings: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var expandDescription by remember { mutableStateOf(false) }
    var showReviewDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showPermissionsDialog by remember { mutableStateOf(false) }
    var showInstallPermDialog by remember { mutableStateOf(false) }
    var replyTargetReviewId by remember { mutableStateOf<String?>(null) }
    var localSnackMessage by remember { mutableStateOf<String?>(null) }
    var pendingDownloadArgs by remember { mutableStateOf<Pair<AppListing, AppVersion?>?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        pendingDownloadArgs?.let { (targetApp, targetVer) ->
            pendingDownloadArgs = null
            onDownloadClick(targetApp, targetVer)
        }
    }

    fun triggerSecureDownload(app: AppListing, version: AppVersion?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingDownloadArgs = app to version
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onDownloadClick(app, version)
        }
    }

    LifecycleResumeEffect(Unit) {
        onRefreshInstallStatus()
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    val title = (detailState as? UiState.Success)?.data?.app?.appName ?: "Application Details"
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenDownloads,
                        modifier = Modifier.testTag("detail_downloads_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DownloadForOffline,
                            contentDescription = stringResource(R.string.my_downloads)
                        )
                    }
                    val app = (detailState as? UiState.Success)?.data?.app
                    if (app != null) {
                        IconButton(
                            onClick = {
                                if (currentUserId == null) onRequireAuth()
                                else onToggleWishlist(app, isSavedInWishlist)
                            },
                            modifier = Modifier.testTag("detail_wishlist_button")
                        ) {
                            Icon(
                                imageVector = if (isSavedInWishlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Wishlist",
                                tint = if (isSavedInWishlist) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, app.appName)
                                    putExtra(Intent.EXTRA_TEXT, "Check out ${app.appName} (${app.packageName}) on APP STORE!")
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share ${app.appName}"))
                            },
                            modifier = Modifier.testTag("detail_share_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share app")
                        }
                        IconButton(
                            onClick = {
                                if (currentUserId == null) onRequireAuth() else showReportDialog = true
                            },
                            modifier = Modifier.testTag("detail_report_button")
                        ) {
                            Icon(Icons.Default.Flag, contentDescription = "Report app")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        when (detailState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    ErrorStateView(message = detailState.message, onRetry = onBack)
                }
            }

            is UiState.Success -> {
                val data = detailState.data
                val app = data.app
                val latestVersion = data.versions.firstOrNull()
                val versionName = latestVersion?.versionName ?: app.versionName
                val versionCode = latestVersion?.versionCode ?: app.versionCode
                val apkSize = latestVersion?.apkSize ?: app.apkSizeBytes
                val minSdk = (latestVersion?.minSdk ?: app.minSdk).toInt()
                val isDeviceCompatible = Build.VERSION.SDK_INT >= minSdk
                val updateAvailable = data.installedStatus.isInstalled &&
                    data.installedStatus.installedVersionCode < versionCode

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Header
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconBox(iconUrl = app.iconUrl, appName = app.appName, size = 80.dp)
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(app.appName, style = MaterialTheme.typography.headlineSmall)
                                Text(
                                    text = app.developerName.ifBlank { "Independent Developer" },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Text(
                                            text = app.categoryName.ifBlank { app.categoryId },
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Text(
                                        text = app.ageRating,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Key Metrics Bar
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                MetricCell(
                                    value = if (app.averageRating > 0) String.format(Locale.US, "%.1f ★", app.averageRating) else "New",
                                    label = "${app.totalReviews} reviews"
                                )
                                MetricCell(value = formatCompactCount(app.downloadCount), label = "Downloads")
                                MetricCell(value = formatBytes(apkSize), label = "APK Size")
                                MetricCell(value = "API $minSdk+", label = "Android")
                            }
                        }
                    }

                    // Download / Install CTA
                    item {
                        val isApprovedApp = app.status.equals("approved", ignoreCase = true) &&
                            (latestVersion == null || latestVersion.approvalStatus.equals("approved", ignoreCase = true))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!isDeviceCompatible) {
                                Text(
                                    text = "Incompatible with this device (requires Android API $minSdk+)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            if (!isApprovedApp) {
                                Text(
                                    text = "Only admin-approved releases are cleared for public download.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            when (downloadState?.phase) {
                                DownloadPhase.QUEUED, DownloadPhase.DOWNLOADING, DownloadPhase.VERIFYING -> {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = when (downloadState.phase) {
                                                            DownloadPhase.QUEUED -> "Preparing download…"
                                                            DownloadPhase.VERIFYING -> "Verifying SHA-256 & signature…"
                                                            else -> "Downloading ${downloadState.progressPercent}%"
                                                        },
                                                        style = MaterialTheme.typography.titleSmall
                                                    )
                                                    val sizeLine = if (downloadState.totalBytes > 0L) {
                                                        "${formatBytes(downloadState.bytesDownloaded)} / ${formatBytes(downloadState.totalBytes)}"
                                                    } else {
                                                        "${formatBytes(downloadState.bytesDownloaded)} downloaded"
                                                    }
                                                    val etaStr = formatEta(downloadState.etaSeconds)
                                                    val telemetry = buildString {
                                                        append(sizeLine)
                                                        if (downloadState.speedBytesPerSec > 0L) {
                                                            append(" • ")
                                                            append(formatSpeed(downloadState.speedBytesPerSec))
                                                        }
                                                        if (etaStr.isNotBlank()) {
                                                            append(" • ")
                                                            append(etaStr)
                                                        }
                                                    }
                                                    Text(
                                                        text = telemetry,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            if (downloadState.phase == DownloadPhase.QUEUED || downloadState.phase == DownloadPhase.VERIFYING) {
                                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                            } else {
                                                LinearProgressIndicator(
                                                    progress = { (downloadState.progressPercent / 100f).coerceIn(0f, 1f) },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                            if (downloadState.phase != DownloadPhase.VERIFYING) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    OutlinedButton(
                                                        onClick = onPauseDownload,
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .testTag("detail_pause_button")
                                                    ) {
                                                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(stringResource(R.string.pause))
                                                    }
                                                    TextButton(
                                                        onClick = onCancelDownload,
                                                        modifier = Modifier.testTag("detail_cancel_button")
                                                    ) {
                                                        Text("Cancel")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                DownloadPhase.PAUSED -> {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val sizeLine = if (downloadState.totalBytes > 0L) {
                                                "${formatBytes(downloadState.bytesDownloaded)} / ${formatBytes(downloadState.totalBytes)}"
                                            } else {
                                                formatBytes(downloadState.bytesDownloaded)
                                            }
                                            Text(
                                                text = "Download Paused (${downloadState.progressPercent}%) • $sizeLine",
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            LinearProgressIndicator(
                                                progress = { (downloadState.progressPercent / 100f).coerceIn(0f, 1f) },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = onResumeDownload,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .testTag("detail_resume_button")
                                                ) {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(stringResource(R.string.resume))
                                                }
                                                OutlinedButton(
                                                    onClick = onCancelDownload,
                                                    modifier = Modifier.testTag("detail_cancel_button")
                                                ) {
                                                    Text("Cancel")
                                                }
                                            }
                                        }
                                    }
                                }

                                DownloadPhase.READY_TO_INSTALL -> {
                                    Button(
                                        onClick = {
                                            if (canInstallPackages()) {
                                                onInstallClick(app, latestVersion)
                                            } else {
                                                showInstallPermDialog = true
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .testTag("detail_install_button"),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(Icons.Default.InstallMobile, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Install Verified APK (v$versionName)")
                                    }
                                }

                                DownloadPhase.FAILED -> {
                                    Text(
                                        text = downloadState.errorMessage ?: "Download failed",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Button(
                                        onClick = onRetryDownload,
                                        enabled = isDeviceCompatible && isApprovedApp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .testTag("detail_retry_button"),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Retry Download")
                                    }
                                }

                                else -> {
                                    if (data.localApkFile != null && (!data.installedStatus.isInstalled || updateAvailable)) {
                                        Button(
                                            onClick = {
                                                if (canInstallPackages()) onInstallClick(app, latestVersion)
                                                else showInstallPermDialog = true
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp)
                                                .testTag("detail_install_button"),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Icon(Icons.Default.InstallMobile, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(if (updateAvailable) "Install Update v$versionName" else "Install")
                                        }
                                    } else if (data.installedStatus.isInstalled && !updateAvailable) {
                                        OutlinedButton(
                                            onClick = {
                                                val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                                if (launchIntent != null) {
                                                    context.startActivity(launchIntent)
                                                } else {
                                                    localSnackMessage = "${app.appName} is installed (no launchable Activity)."
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp)
                                                .testTag("detail_open_installed_button"),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldTertiary)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Installed (v${data.installedStatus.installedVersionName}) • Open")
                                            Spacer(Modifier.width(6.dp))
                                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    } else {
                                        Button(
                                            onClick = { triggerSecureDownload(app, latestVersion) },
                                            enabled = isDeviceCompatible && isApprovedApp,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp)
                                                .testTag("detail_download_button"),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Text(if (updateAvailable) "Update to v$versionName" else "Download (${formatBytes(apkSize)})")
                                        }
                                    }
                                }
                            }

                            if (downloadState != null && downloadState.phase != DownloadPhase.IDLE) {
                                TextButton(
                                    onClick = onOpenDownloads,
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Manage in My Downloads")
                                }
                            }
                        }
                    }

                    // Screenshots
                    if (data.media.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Screenshots", style = MaterialTheme.typography.titleLarge)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(data.media.filter { it.mediaType == "screenshot" }, key = { it.id }) { itemMedia ->
                                        AsyncImage(
                                            model = itemMedia.url,
                                            contentDescription = "${app.appName} screenshot",
                                            modifier = Modifier
                                                .width(180.dp)
                                                .height(300.dp)
                                                .clip(RoundedCornerShape(16.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // About & What's New
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("About this app", style = MaterialTheme.typography.titleLarge)
                                if (app.shortDescription.isNotBlank()) {
                                    Text(app.shortDescription, style = MaterialTheme.typography.titleSmall)
                                }
                                Text(
                                    text = app.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = if (expandDescription) Int.MAX_VALUE else 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                                TextButton(onClick = { expandDescription = !expandDescription }) {
                                    Text(if (expandDescription) "Show less" else "Read full description")
                                }

                                if (app.whatsNew.isNotBlank()) {
                                    HorizontalDivider()
                                    Text("What's New", style = MaterialTheme.typography.titleSmall)
                                    Text(app.whatsNew, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }

                    // Technical & Security Metadata
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("App Info & Verification", style = MaterialTheme.typography.titleLarge)
                                InfoPair("Package ID", app.packageName)
                                InfoPair("Version", "$versionName ($versionCode)")
                                InfoPair("Released", formatTimestamp(app.createdAt))
                                InfoPair("Updated", formatTimestamp(app.updatedAt))
                                InfoPair("Target Android", "API ${latestVersion?.targetSdk ?: app.targetSdk}")
                                if (!latestVersion?.sha256.isNullOrBlank()) {
                                    InfoPair("SHA-256", "${latestVersion!!.sha256.take(18)}…")
                                }
                                if (latestVersion != null) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Security, contentDescription = null, tint = EmeraldTertiary, modifier = Modifier.size(16.dp))
                                        Text("Validation:", style = MaterialTheme.typography.bodySmall)
                                        StatusChip(status = latestVersion.validationStatus)
                                        StatusChip(status = latestVersion.scanStatus)
                                    }
                                }
                                if (!latestVersion?.permissions.isNullOrEmpty()) {
                                    TextButton(onClick = { showPermissionsDialog = true }) {
                                        Text("View ${latestVersion!!.permissions.size} declared permissions")
                                    }
                                }
                                if (app.privacyPolicyUrl.isNotBlank()) {
                                    TextButton(onClick = {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(app.privacyPolicyUrl)))
                                        }
                                    }) {
                                        Text("Privacy Policy")
                                    }
                                }
                                if (app.websiteUrl.isNotBlank()) {
                                    TextButton(onClick = {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(app.websiteUrl)))
                                        }
                                    }) {
                                        Text("Developer Website")
                                    }
                                }
                                if (app.supportContact.isNotBlank()) {
                                    InfoPair("Developer Support", app.supportContact)
                                }
                            }
                        }
                    }

                    // Ratings & Reviews
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Ratings & Reviews", style = MaterialTheme.typography.titleLarge)
                                Button(
                                    onClick = {
                                        if (currentUserId == null) onRequireAuth()
                                        else showReviewDialog = true
                                    },
                                    modifier = Modifier.testTag("write_review_button")
                                ) {
                                    Text("Write a Review")
                                }
                            }

                            RatingBreakdownCard(reviews = data.reviews, avgRating = app.averageRating)
                        }
                    }

                    items(data.reviews, key = { it.id }) { review ->
                        ReviewCard(
                            review = review,
                            isOwnReview = currentUserId != null && review.userId == currentUserId,
                            isAppOwner = currentUserId != null && app.ownerUid == currentUserId,
                            onHelpfulClick = {
                                if (currentUserId == null) onRequireAuth() else onMarkHelpful(review.id)
                            },
                            onEditClick = { showReviewDialog = true },
                            onDeleteClick = onDeleteReview,
                            onReplyClick = { replyTargetReviewId = review.id },
                            onReportClick = {
                                if (currentUserId == null) onRequireAuth()
                                else onSubmitReport("review", review.id, "abusive_review", "Reported from app details")
                            }
                        )
                    }

                    // Similar Applications
                    if (data.similarApps.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Similar Applications", style = MaterialTheme.typography.titleLarge)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(data.similarApps, key = { "sim_${it.appId}" }) { sim ->
                                        AppHorizontalCard(app = sim, onClick = { onOpenSimilarApp(sim.appId) })
                                    }
                                }
                            }
                        }
                    }
                }

                // Dialogs
                if (showReviewDialog) {
                    val ownReview = data.reviews.firstOrNull { it.userId == currentUserId }
                    WriteReviewDialog(
                        initialRating = ownReview?.rating?.toInt() ?: 5,
                        initialComment = ownReview?.comment.orEmpty(),
                        onDismiss = { showReviewDialog = false },
                        onSubmit = { r, c ->
                            showReviewDialog = false
                            onSubmitReview(r, c)
                        }
                    )
                }

                if (showReportDialog) {
                    ReportAppDialog(
                        onDismiss = { showReportDialog = false },
                        onSubmit = { reason, details ->
                            showReportDialog = false
                            onSubmitReport("app", app.appId, reason, details)
                        }
                    )
                }

                if (showPermissionsDialog && latestVersion != null) {
                    AlertDialog(
                        onDismissRequest = { showPermissionsDialog = false },
                        title = { Text("Requested Permissions") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                latestVersion.permissions.forEach { perm ->
                                    Text("• $perm", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showPermissionsDialog = false }) { Text("Close") }
                        }
                    )
                }

                if (showInstallPermDialog) {
                    AlertDialog(
                        onDismissRequest = { showInstallPermDialog = false },
                        title = { Text("Allow App Installations") },
                        text = {
                            Text("Android requires granting APP STORE permission to install APKs from this marketplace via the system Package Installer.")
                        },
                        confirmButton = {
                            Button(onClick = {
                                showInstallPermDialog = false
                                onOpenInstallPermissionSettings()
                            }) { Text("Open Settings") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showInstallPermDialog = false }) { Text("Cancel") }
                        }
                    )
                }

                if (replyTargetReviewId != null) {
                    var replyText by remember { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = { replyTargetReviewId = null },
                        title = { Text("Developer Reply") },
                        text = {
                            OutlinedTextField(
                                value = replyText,
                                onValueChange = { replyText = it },
                                label = { Text("Reply to user") },
                                minLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val id = replyTargetReviewId ?: return@Button
                                    replyTargetReviewId = null
                                    onReplyToReview(id, replyText)
                                },
                                enabled = replyText.isNotBlank()
                            ) { Text("Post Reply") }
                        },
                        dismissButton = {
                            TextButton(onClick = { replyTargetReviewId = null }) { Text("Cancel") }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCell(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoPair(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun RatingBreakdownCard(reviews: List<AppReview>, avgRating: Double) {
    val total = reviews.size.coerceAtLeast(1)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (avgRating > 0) String.format(Locale.US, "%.1f", avgRating)
                    else if (reviews.isNotEmpty()) String.format(Locale.US, "%.1f", reviews.map { it.rating }.average())
                    else "—",
                    style = MaterialTheme.typography.displaySmall
                )
                Text("${reviews.size} reviews", style = MaterialTheme.typography.bodySmall)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (5 downTo 1).forEach { star ->
                    val count = reviews.count { it.rating.toInt() == star }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("$star", style = MaterialTheme.typography.labelSmall)
                        LinearProgressIndicator(
                            progress = { if (reviews.isEmpty()) 0f else count.toFloat() / total.toFloat() },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(50))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(
    review: AppReview,
    isOwnReview: Boolean,
    isAppOwner: Boolean,
    onHelpfulClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onReplyClick: () -> Unit,
    onReportClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(review.userName, style = MaterialTheme.typography.titleSmall)
                Text(formatTimestamp(review.updatedAt ?: review.createdAt), style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(5) { idx ->
                    Icon(
                        imageVector = if (idx < review.rating) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = null,
                        tint = AmberStar,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (review.comment.isNotBlank()) {
                Text(review.comment, style = MaterialTheme.typography.bodyMedium)
            }
            if (review.developerReply.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Developer Response", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(review.developerReply, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onHelpfulClick) {
                    Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Helpful (${review.helpfulCount})")
                }
                Row {
                    if (isAppOwner) {
                        TextButton(onClick = onReplyClick) { Text("Reply") }
                    }
                    if (isOwnReview) {
                        TextButton(onClick = onEditClick) { Text("Edit") }
                        IconButton(onClick = onDeleteClick) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete review", tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        TextButton(onClick = onReportClick) { Text("Report") }
                    }
                }
            }
        }
    }
}

@Composable
private fun WriteReviewDialog(
    initialRating: Int,
    initialComment: String,
    onDismiss: () -> Unit,
    onSubmit: (Int, String) -> Unit
) {
    var rating by remember { mutableIntStateOf(initialRating) }
    var comment by remember { mutableStateOf(initialComment) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rate & Review") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    (1..5).forEach { star ->
                        IconButton(onClick = { rating = star }) {
                            Icon(
                                imageVector = if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "$star stars",
                                tint = AmberStar,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Share your experience") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(rating, comment) }) { Text("Submit") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ReportAppDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    val reasons = listOf(
        "malware" to "Malware or harmful behavior",
        "copyright" to "Copyright infringement",
        "privacy" to "Privacy violation",
        "fake_app" to "Fake or impersonating app",
        "fraud" to "Fraud or deceptive claims"
    )
    var selectedReason by remember { mutableStateOf("malware") }
    var details by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report Application") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                reasons.forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReason = key },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedReason == key, onClick = { selectedReason = key })
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text("Additional details") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedReason, details.ifBlank { "Reported via Android app" }) }
            ) { Text("Submit Report") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
