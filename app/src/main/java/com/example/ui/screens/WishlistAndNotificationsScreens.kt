package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.download.InstalledPackageStatus
import com.example.data.model.AppListing
import com.example.data.model.DownloadRecord
import com.example.data.model.NotificationItem
import com.example.data.model.WishlistEntry
import com.example.ui.components.AppIconBox
import com.example.ui.components.EmptyStateView
import com.example.ui.components.RatingPill
import com.example.ui.components.StatusChip
import com.example.ui.components.formatBytes
import com.example.ui.components.formatTimestamp
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistLibraryScreen(
    wishlist: List<WishlistEntry>,
    downloads: List<DownloadRecord>,
    allApprovedApps: List<AppListing>,
    recentlyViewedIds: List<String>,
    getInstalledStatus: (String) -> InstalledPackageStatus,
    getLocalApk: (String, Long) -> File?,
    onRemoveDownloadEntry: (DownloadRecord) -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenApp: (String) -> Unit,
    onBack: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wishlist & Library") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenDownloads) {
                        Icon(
                            imageVector = Icons.Default.DownloadForOffline,
                            contentDescription = stringResource(R.string.my_downloads)
                        )
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
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Wishlist (${wishlist.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Library (${downloads.size})") })
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (tab == 0) {
                    if (wishlist.isEmpty()) {
                        item {
                            EmptyStateView(
                                title = "Your wishlist is empty",
                                subtitle = "Bookmark any app from its details page to track releases here."
                            )
                        }
                    } else {
                        items(wishlist, key = { it.id }) { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenApp(item.appId) },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    AppIconBox(iconUrl = item.iconUrl, appName = item.appName, size = 52.dp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.appName, style = MaterialTheme.typography.titleSmall)
                                        Text(item.developerName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    RatingPill(rating = item.averageRating)
                                }
                            }
                        }
                    }
                } else {
                    if (downloads.isEmpty()) {
                        item {
                            EmptyStateView(
                                title = "No downloads yet",
                                subtitle = "Downloaded APKs, installed status, and available updates appear here.",
                                actionLabel = stringResource(R.string.my_downloads),
                                onAction = onOpenDownloads
                            )
                        }
                    } else {
                        items(downloads, key = { it.id }) { dl ->
                            val installed = getInstalledStatus(dl.packageName)
                            val localFile = getLocalApk(dl.packageName, dl.versionCode)
                            val liveApp = allApprovedApps.firstOrNull { it.appId == dl.appId }
                            val hasUpdate = installed.isInstalled && liveApp != null && liveApp.versionCode > installed.installedVersionCode

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenApp(dl.appId) },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        AppIconBox(iconUrl = dl.iconUrl, appName = dl.appName, size = 50.dp)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(dl.appName, style = MaterialTheme.typography.titleSmall)
                                            val sizeLabel = if (dl.fileSizeBytes > 0L) " • ${formatBytes(dl.fileSizeBytes)}" else ""
                                            Text(
                                                "v${dl.versionName}$sizeLabel • ${formatTimestamp(dl.updatedAt ?: dl.createdAt)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = when {
                                                    hasUpdate -> "Update available: v${liveApp?.versionName}"
                                                    installed.isInstalled -> "Installed on device (v${installed.installedVersionName})"
                                                    localFile != null -> "APK saved on device (not yet installed)"
                                                    else -> "Status: ${dl.status}"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (hasUpdate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        StatusChip(status = if (installed.isInstalled) "installed" else dl.status)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = { onOpenApp(dl.appId) }) {
                                            Text(if (hasUpdate) "Update" else "Open Details")
                                        }
                                        TextButton(onClick = onOpenDownloads) {
                                            Text("Downloads")
                                        }
                                        if (localFile != null) {
                                            TextButton(onClick = { onRemoveDownloadEntry(dl) }) {
                                                Text("Remove APK")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val recentApps = recentlyViewedIds.mapNotNull { id -> allApprovedApps.firstOrNull { it.appId == id } }
                    if (recentApps.isNotEmpty()) {
                        item {
                            Text("Recently Viewed", style = MaterialTheme.typography.titleMedium)
                        }
                        items(recentApps, key = { "rv_${it.appId}" }) { app ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenApp(app.appId) },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    AppIconBox(iconUrl = app.iconUrl, appName = app.appName, size = 44.dp)
                                    Text(app.appName, style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    notifications: List<NotificationItem>,
    onMarkRead: (String) -> Unit,
    onOpenApp: (String) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (notifications.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                EmptyStateView(
                    title = "No notifications yet",
                    subtitle = "App approvals, update alerts, developer replies, and security notices appear here."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(notifications, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!item.read) onMarkRead(item.id)
                                if (item.targetAppId.isNotBlank()) onOpenApp(item.targetAppId)
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (item.read) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = if (item.read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall)
                                Text(item.body, style = MaterialTheme.typography.bodySmall)
                                Text(formatTimestamp(item.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
