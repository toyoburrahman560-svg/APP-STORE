package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.download.ActiveDownloadState
import com.example.data.download.InstalledPackageStatus
import com.example.data.model.AppListing
import com.example.data.model.LanguagePreference
import com.example.data.model.MarketplaceCategory
import com.example.ui.components.AppListRowCard
import com.example.ui.components.AppStoreBrandHeader
import com.example.ui.components.EmptyStateView
import com.example.ui.viewmodel.SortOrder

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoriesScreen(
    categories: List<MarketplaceCategory>,
    selectedCategoryId: String?,
    sortOrder: SortOrder,
    filteredApps: List<AppListing>,
    language: LanguagePreference,
    userPhotoUrl: String?,
    unreadNotifications: Int,
    activeDownloadCount: Int,
    onSelectCategory: (String?) -> Unit,
    onSortChange: (SortOrder) -> Unit,
    onSearchClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onAppClick: (String) -> Unit,
    downloadsMap: Map<String, ActiveDownloadState> = emptyMap(),
    getInstalledStatus: (String) -> InstalledPackageStatus = { InstalledPackageStatus(false) },
    onInstallClick: (AppListing) -> Unit = {}
) {
    val gameCats = categories.filter { it.group.equals("games", ignoreCase = true) }
    val appCats = categories.filter { !it.group.equals("games", ignoreCase = true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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

        if (selectedCategoryId != null) {
            val activeCat = categories.firstOrNull { it.id == selectedCategoryId }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (language == LanguagePreference.BENGALI && !activeCat?.nameBn.isNullOrBlank())
                            activeCat!!.nameBn else (activeCat?.name ?: selectedCategoryId),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    TextButton(onClick = { onSelectCategory(null) }) {
                        Text("All Categories")
                    }
                }
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(SortOrder.entries) { order ->
                        FilterChip(
                            selected = sortOrder == order,
                            onClick = { onSortChange(order) },
                            label = {
                                Text(if (language == LanguagePreference.BENGALI) order.labelBn else order.labelEn)
                            }
                        )
                    }
                }
            }
            if (filteredApps.isEmpty()) {
                item {
                    EmptyStateView(
                        title = "No apps in this category yet",
                        subtitle = "Check back soon or browse another category.",
                        actionLabel = "All Categories",
                        onAction = { onSelectCategory(null) }
                    )
                }
            } else {
                items(filteredApps, key = { it.appId }) { app ->
                    AppListRowCard(
                        app = app,
                        onClick = { onAppClick(app.appId) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                        downloadState = downloadsMap[app.appId],
                        installedStatus = getInstalledStatus(app.packageName),
                        onInstallClick = { onInstallClick(app) }
                    )
                }
            }
        } else {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Games", style = MaterialTheme.typography.titleLarge)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        maxItemsInEachRow = 2,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        gameCats.forEach { cat ->
                            CategoryTile(
                                category = cat,
                                language = language,
                                onClick = { onSelectCategory(cat.id) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Applications", style = MaterialTheme.typography.titleLarge)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        maxItemsInEachRow = 2,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        appCats.forEach { cat ->
                            CategoryTile(
                                category = cat,
                                language = language,
                                onClick = { onSelectCategory(cat.id) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(
    category: MarketplaceCategory,
    language: LanguagePreference,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = if (language == LanguagePreference.BENGALI && category.nameBn.isNotBlank()) {
        category.nameBn
    } else category.name

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("category_${category.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = categoryIconForName(category.iconName),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(22.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2
            )
        }
    }
}

fun categoryIconForName(iconName: String): ImageVector = when (iconName) {
    "sports_esports" -> Icons.Default.SportsEsports
    "explore" -> Icons.Default.Explore
    "directions_car" -> Icons.Default.DirectionsCar
    "extension" -> Icons.Default.Extension
    "psychology" -> Icons.Default.Psychology
    "videogame_asset" -> Icons.Default.VideogameAsset
    "sports_soccer" -> Icons.Default.SportsSoccer
    "school" -> Icons.Default.School
    "theaters" -> Icons.Default.Theaters
    "photo_camera" -> Icons.Default.PhotoCamera
    "smart_display" -> Icons.Default.SmartDisplay
    "music_note" -> Icons.Default.MusicNote
    "business_center" -> Icons.Default.BusinessCenter
    "task_alt" -> Icons.Default.TaskAlt
    "build" -> Icons.Default.Build
    "spa" -> Icons.Default.Spa
    "account_balance" -> Icons.Default.AccountBalance
    "fitness_center" -> Icons.Default.FitnessCenter
    "forum" -> Icons.Default.Forum
    "shopping_bag" -> Icons.Default.ShoppingBag
    "newspaper" -> Icons.Default.Newspaper
    "flight" -> Icons.Default.Flight
    "palette" -> Icons.Default.Palette
    else -> Icons.Default.Apps
}
