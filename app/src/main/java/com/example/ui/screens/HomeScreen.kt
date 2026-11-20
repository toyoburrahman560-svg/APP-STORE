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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.download.ActiveDownloadState
import com.example.data.download.InstalledPackageStatus
import com.example.data.model.AppListing
import com.example.data.model.DownloadPhase
import com.example.data.model.UiState
import com.example.ui.components.AppHorizontalCard
import com.example.ui.components.AppListRowCard
import com.example.ui.components.AppStoreBrandHeader
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.FeaturedHeroBanner
import com.example.ui.components.SkeletonAppCard
import com.example.ui.viewmodel.HomeSectionsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeState: UiState<HomeSectionsState>,
    isRefreshing: Boolean,
    userPhotoUrl: String?,
    unreadNotifications: Int,
    activeDownloadCount: Int,
    onRefresh: () -> Unit,
    onSearchClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onAppClick: (String) -> Unit,
    onPublishClick: () -> Unit,
    onSeeAllCategory: (String?) -> Unit,
    downloadsMap: Map<String, ActiveDownloadState> = emptyMap(),
    getInstalledStatus: (String) -> InstalledPackageStatus = { InstalledPackageStatus(false) },
    onInstallClick: (AppListing) -> Unit = {}
) {
    var selectedSubTab by rememberSaveable { mutableIntStateOf(0) }
    val subTabs = listOf(
        R.string.tab_for_you,
        R.string.tab_top_charts,
        R.string.tab_new_releases,
        R.string.tab_picks
    )

    val activeBannerDownloads = remember(downloadsMap) {
        downloadsMap.values.filter {
            it.phase == DownloadPhase.QUEUED ||
                it.phase == DownloadPhase.DOWNLOADING ||
                it.phase == DownloadPhase.VERIFYING ||
                it.phase == DownloadPhase.READY_TO_INSTALL
        }.sortedByDescending { it.updatedAtMillis }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column {
                    AppStoreBrandHeader(
                        userPhotoUrl = userPhotoUrl,
                        unreadCount = unreadNotifications,
                        onSearchClick = onSearchClick,
                        onNotificationsClick = onNotificationsClick,
                        onAvatarClick = onAvatarClick,
                        activeDownloadCount = activeDownloadCount,
                        onDownloadsClick = onDownloadsClick
                    )
                    SecondaryScrollableTabRow(
                        selectedTabIndex = selectedSubTab,
                        edgePadding = 16.dp,
                        containerColor = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        subTabs.forEachIndexed { index, labelRes ->
                            Tab(
                                selected = selectedSubTab == index,
                                onClick = { selectedSubTab = index },
                                text = {
                                    Text(
                                        text = stringResource(labelRes),
                                        fontWeight = if (selectedSubTab == index) FontWeight.SemiBold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                },
                                modifier = Modifier.testTag("home_subtab_$index")
                            )
                        }
                    }
                }
            }

            if (activeBannerDownloads.isNotEmpty()) {
                item {
                    val primary = activeBannerDownloads.first()
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clickable(onClick = onDownloadsClick),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Downloading,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = if (activeBannerDownloads.size == 1) {
                                            when (primary.phase) {
                                                DownloadPhase.READY_TO_INSTALL -> "${primary.appName} ready to install"
                                                DownloadPhase.VERIFYING -> "Verifying ${primary.appName}…"
                                                DownloadPhase.QUEUED -> "Queued ${primary.appName}…"
                                                else -> "Downloading ${primary.appName} (${primary.progressPercent}%)"
                                            }
                                        } else {
                                            "${activeBannerDownloads.size} active downloads (${primary.appName} ${primary.progressPercent}%)"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.my_downloads),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (primary.phase == DownloadPhase.DOWNLOADING) {
                                LinearProgressIndicator(
                                    progress = { (primary.progressPercent / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            when (homeState) {
                is UiState.Loading -> {
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FeaturedHeroBanner(
                                featuredApp = null,
                                onAppClick = onAppClick,
                                onPublishClick = onPublishClick
                            )
                            Text("Loading Marketplace…", style = MaterialTheme.typography.titleMedium)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(4) { SkeletonAppCard() }
                            }
                        }
                    }
                }

                is UiState.Error -> {
                    item {
                        ErrorStateView(
                            message = homeState.message,
                            onRetry = onRefresh
                        )
                    }
                }

                is UiState.Success -> {
                    val data = homeState.data

                    if (data.announcements.isNotEmpty() && selectedSubTab == 0) {
                        item {
                            val topAnnouncement = data.announcements.first()
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Campaign,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(topAnnouncement.title, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            topAnnouncement.body,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (data.allApprovedApps.isEmpty()) {
                        item {
                            FeaturedHeroBanner(
                                featuredApp = null,
                                onAppClick = onAppClick,
                                onPublishClick = onPublishClick,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        item {
                            EmptyStateView(
                                title = "Marketplace Ready for Launch",
                                subtitle = "No approved public apps are published yet. Developers can submit an APK from the Publish tab for admin review.",
                                actionLabel = stringResource(R.string.publish_your_app),
                                onAction = onPublishClick
                            )
                        }
                    } else {
                        when (selectedSubTab) {
                            // 0 = For you (Google Play style Spotlight + Installable rows + horizontal shelves)
                            0 -> {
                                val heroApp = data.featured.firstOrNull() ?: data.allApprovedApps.firstOrNull()
                                item {
                                    FeaturedHeroBanner(
                                        featuredApp = heroApp,
                                        onAppClick = onAppClick,
                                        onPublishClick = onPublishClick,
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        downloadState = heroApp?.let { downloadsMap[it.appId] },
                                        installedStatus = heroApp?.let { getInstalledStatus(it.packageName) }
                                            ?: InstalledPackageStatus(false),
                                        onInstallClick = heroApp?.let { app -> { onInstallClick(app) } }
                                    )
                                }

                                // Top Play-Store style installable list right below the hero banner
                                val quickList = data.popular.ifEmpty { data.allApprovedApps }
                                item {
                                    SectionHeaderRow(
                                        title = "Popular & Ready to Install",
                                        onSeeAll = { selectedSubTab = 1 }
                                    )
                                }
                                itemsIndexed(quickList.take(5), key = { _, app -> "pop_${app.appId}" }) { idx, app ->
                                    AppListRowCard(
                                        app = app,
                                        rank = idx + 1,
                                        onClick = { onAppClick(app.appId) },
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        downloadState = downloadsMap[app.appId],
                                        installedStatus = getInstalledStatus(app.packageName),
                                        onInstallClick = { onInstallClick(app) }
                                    )
                                }

                                if (data.featured.isNotEmpty()) {
                                    item {
                                        HorizontalAppSection(
                                            title = "Featured Applications",
                                            apps = data.featured,
                                            downloadsMap = downloadsMap,
                                            getInstalledStatus = getInstalledStatus,
                                            onAppClick = onAppClick,
                                            onInstallClick = onInstallClick,
                                            onSeeAll = { onSeeAllCategory(null) }
                                        )
                                    }
                                }
                                if (data.trending.isNotEmpty()) {
                                    item {
                                        HorizontalAppSection(
                                            title = "Trending Applications",
                                            apps = data.trending,
                                            downloadsMap = downloadsMap,
                                            getInstalledStatus = getInstalledStatus,
                                            onAppClick = onAppClick,
                                            onInstallClick = onInstallClick,
                                            onSeeAll = { onSeeAllCategory(null) }
                                        )
                                    }
                                }
                                if (data.topRated.isNotEmpty()) {
                                    item {
                                        HorizontalAppSection(
                                            title = "Top Rated Applications",
                                            apps = data.topRated,
                                            downloadsMap = downloadsMap,
                                            getInstalledStatus = getInstalledStatus,
                                            onAppClick = onAppClick,
                                            onInstallClick = onInstallClick,
                                            onSeeAll = { onSeeAllCategory(null) }
                                        )
                                    }
                                }
                                if (data.newReleases.isNotEmpty()) {
                                    item {
                                        HorizontalAppSection(
                                            title = "New Releases",
                                            apps = data.newReleases,
                                            downloadsMap = downloadsMap,
                                            getInstalledStatus = getInstalledStatus,
                                            onAppClick = onAppClick,
                                            onInstallClick = onInstallClick,
                                            onSeeAll = { selectedSubTab = 2 }
                                        )
                                    }
                                }
                                if (data.recommended.isNotEmpty()) {
                                    item {
                                        HorizontalAppSection(
                                            title = "Recommended for You",
                                            apps = data.recommended,
                                            downloadsMap = downloadsMap,
                                            getInstalledStatus = getInstalledStatus,
                                            onAppClick = onAppClick,
                                            onInstallClick = onInstallClick,
                                            onSeeAll = { onSeeAllCategory(null) }
                                        )
                                    }
                                }
                                if (data.recentlyUpdated.isNotEmpty()) {
                                    item {
                                        HorizontalAppSection(
                                            title = "Recently Updated",
                                            apps = data.recentlyUpdated,
                                            downloadsMap = downloadsMap,
                                            getInstalledStatus = getInstalledStatus,
                                            onAppClick = onAppClick,
                                            onInstallClick = onInstallClick,
                                            onSeeAll = { onSeeAllCategory(null) }
                                        )
                                    }
                                }
                                if (data.popularGames.isNotEmpty()) {
                                    item {
                                        HorizontalAppSection(
                                            title = "Popular Games",
                                            apps = data.popularGames,
                                            downloadsMap = downloadsMap,
                                            getInstalledStatus = getInstalledStatus,
                                            onAppClick = onAppClick,
                                            onInstallClick = onInstallClick,
                                            onSeeAll = { onSeeAllCategory("action") }
                                        )
                                    }
                                }
                                if (data.popularTools.isNotEmpty()) {
                                    item {
                                        HorizontalAppSection(
                                            title = "Popular Tools",
                                            apps = data.popularTools,
                                            downloadsMap = downloadsMap,
                                            getInstalledStatus = getInstalledStatus,
                                            onAppClick = onAppClick,
                                            onInstallClick = onInstallClick,
                                            onSeeAll = { onSeeAllCategory("tools") }
                                        )
                                    }
                                }
                                if (data.editorsChoice.isNotEmpty()) {
                                    item {
                                        HorizontalAppSection(
                                            title = "Editor's Choice",
                                            apps = data.editorsChoice,
                                            downloadsMap = downloadsMap,
                                            getInstalledStatus = getInstalledStatus,
                                            onAppClick = onAppClick,
                                            onInstallClick = onInstallClick,
                                            onSeeAll = { selectedSubTab = 3 }
                                        )
                                    }
                                }
                                if (data.developerSpotlight.isNotEmpty()) {
                                    item {
                                        HorizontalAppSection(
                                            title = "Developer Spotlight",
                                            apps = data.developerSpotlight,
                                            downloadsMap = downloadsMap,
                                            getInstalledStatus = getInstalledStatus,
                                            onAppClick = onAppClick,
                                            onInstallClick = onInstallClick,
                                            onSeeAll = { onSeeAllCategory(null) }
                                        )
                                    }
                                }
                            }

                            // 1 = Top charts
                            1 -> {
                                val chartList = data.allApprovedApps.sortedByDescending { it.downloadCount }
                                item {
                                    SectionHeaderRow(
                                        title = stringResource(R.string.tab_top_charts),
                                        onSeeAll = { onSeeAllCategory(null) }
                                    )
                                }
                                itemsIndexed(chartList, key = { _, app -> "chart_${app.appId}" }) { idx, app ->
                                    AppListRowCard(
                                        app = app,
                                        rank = idx + 1,
                                        onClick = { onAppClick(app.appId) },
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        downloadState = downloadsMap[app.appId],
                                        installedStatus = getInstalledStatus(app.packageName),
                                        onInstallClick = { onInstallClick(app) }
                                    )
                                }
                            }

                            // 2 = New releases
                            2 -> {
                                val newList = data.newReleases.ifEmpty { data.allApprovedApps }
                                item {
                                    SectionHeaderRow(
                                        title = stringResource(R.string.tab_new_releases),
                                        onSeeAll = { onSeeAllCategory(null) }
                                    )
                                }
                                itemsIndexed(newList, key = { _, app -> "new_${app.appId}" }) { idx, app ->
                                    AppListRowCard(
                                        app = app,
                                        rank = idx + 1,
                                        onClick = { onAppClick(app.appId) },
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        downloadState = downloadsMap[app.appId],
                                        installedStatus = getInstalledStatus(app.packageName),
                                        onInstallClick = { onInstallClick(app) }
                                    )
                                }
                            }

                            // 3 = Editor's picks
                            else -> {
                                val picksList = data.editorsChoice.ifEmpty { data.featured.ifEmpty { data.allApprovedApps } }
                                item {
                                    SectionHeaderRow(
                                        title = stringResource(R.string.tab_picks),
                                        onSeeAll = { onSeeAllCategory(null) }
                                    )
                                }
                                itemsIndexed(picksList, key = { _, app -> "pick_${app.appId}" }) { idx, app ->
                                    AppListRowCard(
                                        app = app,
                                        rank = idx + 1,
                                        onClick = { onAppClick(app.appId) },
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        downloadState = downloadsMap[app.appId],
                                        installedStatus = getInstalledStatus(app.packageName),
                                        onInstallClick = { onInstallClick(app) }
                                    )
                                }
                            }
                        }

                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    onSeeAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onSeeAll) {
            Text("See all")
        }
    }
}

@Composable
private fun HorizontalAppSection(
    title: String,
    apps: List<AppListing>,
    downloadsMap: Map<String, ActiveDownloadState>,
    getInstalledStatus: (String) -> InstalledPackageStatus,
    onAppClick: (String) -> Unit,
    onInstallClick: (AppListing) -> Unit,
    onSeeAll: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeaderRow(title = title, onSeeAll = onSeeAll)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(apps, key = { "${title}_${it.appId}" }) { app ->
                AppHorizontalCard(
                    app = app,
                    onClick = { onAppClick(app.appId) },
                    downloadState = downloadsMap[app.appId],
                    installedStatus = getInstalledStatus(app.packageName),
                    onInstallClick = { onInstallClick(app) }
                )
            }
        }
    }
}
