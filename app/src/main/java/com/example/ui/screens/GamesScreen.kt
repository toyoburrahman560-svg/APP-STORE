package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.download.ActiveDownloadState
import com.example.data.download.InstalledPackageStatus
import com.example.data.model.AppListing
import com.example.data.model.MarketplaceCategory
import com.example.data.model.UiState
import com.example.ui.components.AppHorizontalCard
import com.example.ui.components.AppListRowCard
import com.example.ui.components.AppStoreBrandHeader
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.viewmodel.HomeSectionsState

@Composable
fun GamesScreen(
    homeState: UiState<HomeSectionsState>,
    categories: List<MarketplaceCategory>,
    userPhotoUrl: String?,
    unreadNotifications: Int,
    activeDownloadCount: Int,
    onRefresh: () -> Unit,
    onSearchClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onAppClick: (String) -> Unit,
    downloadsMap: Map<String, ActiveDownloadState> = emptyMap(),
    getInstalledStatus: (String) -> InstalledPackageStatus = { InstalledPackageStatus(false) },
    onInstallClick: (AppListing) -> Unit = {}
) {
    val gameCategories = remember(categories) {
        categories.filter { it.group.equals("games", ignoreCase = true) }
    }
    var selectedGameCat by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Games Arena",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedGameCat == null,
                            onClick = { selectedGameCat = null },
                            label = { Text("All Games") }
                        )
                    }
                    items(gameCategories, key = { it.id }) { cat ->
                        FilterChip(
                            selected = selectedGameCat == cat.id,
                            onClick = { selectedGameCat = if (selectedGameCat == cat.id) null else cat.id },
                            label = { Text(cat.name) }
                        )
                    }
                }
            }
        }

        when (homeState) {
            is UiState.Loading -> {
                item {
                    Text(
                        "Loading games…",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            is UiState.Error -> {
                item { ErrorStateView(message = homeState.message, onRetry = onRefresh) }
            }

            is UiState.Success -> {
                val allGames = homeState.data.allApprovedApps.filter { app ->
                    app.categoryGroup.equals("games", ignoreCase = true) ||
                        gameCategories.any { it.id.equals(app.categoryId, ignoreCase = true) }
                }
                val filtered = if (selectedGameCat == null) allGames
                else allGames.filter { it.categoryId.equals(selectedGameCat, ignoreCase = true) }

                if (filtered.isEmpty()) {
                    item {
                        EmptyStateView(
                            title = "No approved games in this genre yet",
                            subtitle = "Approved Android games submitted by developers will appear here."
                        )
                    }
                } else {
                    item {
                        Text(
                            text = "Trending Games",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filtered.sortedByDescending { it.trendingScore + it.downloadCount }.take(8), key = { "g_t_${it.appId}" }) { game ->
                                AppHorizontalCard(
                                    app = game,
                                    onClick = { onAppClick(game.appId) },
                                    downloadState = downloadsMap[game.appId],
                                    installedStatus = getInstalledStatus(game.packageName),
                                    onInstallClick = { onInstallClick(game) }
                                )
                            }
                        }
                    }
                    item {
                        Text(
                            text = "Top Charts",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    itemsIndexed(filtered.sortedByDescending { it.downloadCount }, key = { _, g -> "g_c_${g.appId}" }) { idx, game ->
                        AppListRowCard(
                            app = game,
                            rank = idx + 1,
                            onClick = { onAppClick(game.appId) },
                            modifier = Modifier.padding(horizontal = 16.dp),
                            downloadState = downloadsMap[game.appId],
                            installedStatus = getInstalledStatus(game.packageName),
                            onInstallClick = { onInstallClick(game) }
                        )
                    }
                }
            }
        }
    }
}
