package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.AppListing
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
    onSeeAllCategory: (String?) -> Unit
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                AppStoreBrandHeader(
                    userPhotoUrl = userPhotoUrl,
                    unreadCount = unreadNotifications,
                    onSearchClick = onSearchClick,
                    onNotificationsClick = onNotificationsClick,
                    onAvatarClick = onAvatarClick,
                    activeDownloadCount = activeDownloadCount,
                    onDownloadsClick = onDownloadsClick
                )
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

                    if (data.announcements.isNotEmpty()) {
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

                    item {
                        FeaturedHeroBanner(
                            featuredApp = data.featured.firstOrNull(),
                            onAppClick = onAppClick,
                            onPublishClick = onPublishClick,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    if (data.allApprovedApps.isEmpty()) {
                        item {
                            EmptyStateView(
                                title = "Marketplace Ready for Launch",
                                subtitle = "No approved public apps are published yet. Developers can submit an APK from the Publish tab for admin review.",
                                actionLabel = "PUBLISH YOUR APP",
                                onAction = onPublishClick
                            )
                        }
                    } else {
                        // 1. Featured Applications
                        if (data.featured.isNotEmpty()) {
                            item {
                                HorizontalAppSection(
                                    title = "Featured Applications",
                                    apps = data.featured,
                                    onAppClick = onAppClick,
                                    onSeeAll = { onSeeAllCategory(null) }
                                )
                            }
                        }
                        // 2. Trending Applications
                        if (data.trending.isNotEmpty()) {
                            item {
                                HorizontalAppSection(
                                    title = "Trending Applications",
                                    apps = data.trending,
                                    onAppClick = onAppClick,
                                    onSeeAll = { onSeeAllCategory(null) }
                                )
                            }
                        }
                        // 3. Popular Applications (Ranked vertical list)
                        if (data.popular.isNotEmpty()) {
                            item {
                                SectionHeaderRow(
                                    title = "Popular Applications",
                                    onSeeAll = { onSeeAllCategory(null) }
                                )
                            }
                            itemsIndexed(data.popular.take(5), key = { _, app -> "pop_${app.appId}" }) { idx, app ->
                                AppListRowCard(
                                    app = app,
                                    rank = idx + 1,
                                    onClick = { onAppClick(app.appId) },
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                        // 4. Top Rated Applications
                        if (data.topRated.isNotEmpty()) {
                            item {
                                HorizontalAppSection(
                                    title = "Top Rated Applications",
                                    apps = data.topRated,
                                    onAppClick = onAppClick,
                                    onSeeAll = { onSeeAllCategory(null) }
                                )
                            }
                        }
                        // 5. New Releases
                        if (data.newReleases.isNotEmpty()) {
                            item {
                                HorizontalAppSection(
                                    title = "New Releases",
                                    apps = data.newReleases,
                                    onAppClick = onAppClick,
                                    onSeeAll = { onSeeAllCategory(null) }
                                )
                            }
                        }
                        // 6. Recommended Applications
                        if (data.recommended.isNotEmpty()) {
                            item {
                                HorizontalAppSection(
                                    title = "Recommended for You",
                                    apps = data.recommended,
                                    onAppClick = onAppClick,
                                    onSeeAll = { onSeeAllCategory(null) }
                                )
                            }
                        }
                        // 7. Recently Updated
                        if (data.recentlyUpdated.isNotEmpty()) {
                            item {
                                HorizontalAppSection(
                                    title = "Recently Updated",
                                    apps = data.recentlyUpdated,
                                    onAppClick = onAppClick,
                                    onSeeAll = { onSeeAllCategory(null) }
                                )
                            }
                        }
                        // 8. Popular Games
                        if (data.popularGames.isNotEmpty()) {
                            item {
                                HorizontalAppSection(
                                    title = "Popular Games",
                                    apps = data.popularGames,
                                    onAppClick = onAppClick,
                                    onSeeAll = { onSeeAllCategory("action") }
                                )
                            }
                        }
                        // 9. Popular Tools
                        if (data.popularTools.isNotEmpty()) {
                            item {
                                HorizontalAppSection(
                                    title = "Popular Tools",
                                    apps = data.popularTools,
                                    onAppClick = onAppClick,
                                    onSeeAll = { onSeeAllCategory("tools") }
                                )
                            }
                        }
                        // 10. Editor's Choice
                        if (data.editorsChoice.isNotEmpty()) {
                            item {
                                HorizontalAppSection(
                                    title = "Editor's Choice",
                                    apps = data.editorsChoice,
                                    onAppClick = onAppClick,
                                    onSeeAll = { onSeeAllCategory(null) }
                                )
                            }
                        }
                        // 11. Developer Spotlight
                        if (data.developerSpotlight.isNotEmpty()) {
                            item {
                                HorizontalAppSection(
                                    title = "Developer Spotlight",
                                    apps = data.developerSpotlight,
                                    onAppClick = onAppClick,
                                    onSeeAll = { onSeeAllCategory(null) }
                                )
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
            style = MaterialTheme.typography.titleLarge
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
    onAppClick: (String) -> Unit,
    onSeeAll: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeaderRow(title = title, onSeeAll = onSeeAll)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(apps, key = { "${title}_${it.appId}" }) { app ->
                AppHorizontalCard(app = app, onClick = { onAppClick(app.appId) })
            }
        }
    }
}
