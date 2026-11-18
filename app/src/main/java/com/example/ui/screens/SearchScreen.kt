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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AppListing
import com.example.data.model.LanguagePreference
import com.example.data.model.MarketplaceCategory
import com.example.ui.components.AppListRowCard
import com.example.ui.components.EmptyStateView
import com.example.ui.viewmodel.SortOrder

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    query: String,
    selectedCategoryId: String?,
    sortOrder: SortOrder,
    results: List<AppListing>,
    allApps: List<AppListing>,
    categories: List<MarketplaceCategory>,
    recentSearches: List<String>,
    language: LanguagePreference,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: (String) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onSortChange: (SortOrder) -> Unit,
    onClearHistory: () -> Unit,
    onBack: () -> Unit,
    onAppClick: (String) -> Unit
) {
    val suggestions = remember(query, allApps) {
        if (query.isBlank()) emptyList()
        else {
            val q = query.trim().lowercase()
            allApps.flatMap { listOf(it.appName, it.developerName, it.categoryName) }
                .distinct()
                .filter { it.lowercase().contains(q) }
                .take(5)
        }
    }

    val trendingTerms = remember(allApps) {
        val dynamic = allApps.sortedByDescending { it.trendingScore + it.downloadCount }
            .map { it.appName }
            .take(5)
        if (dynamic.isNotEmpty()) dynamic
        else listOf("Productivity", "Action", "Tools", "Finance", "Education")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search Marketplace") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_input_field"),
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmitSearch(query) })
                )
            }

            if (suggestions.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Suggestions", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        suggestions.forEach { sug ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSubmitSearch(sug) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(sug, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }

            if (query.isBlank() && recentSearches.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Recent Searches", style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = onClearHistory) { Text("Clear History") }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            recentSearches.forEach { term ->
                                AssistChip(
                                    onClick = { onSubmitSearch(term) },
                                    label = { Text(term) },
                                    leadingIcon = { Icon(Icons.Default.History, contentDescription = null) }
                                )
                            }
                        }
                    }
                }
            }

            if (query.isBlank()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Trending Searches", style = MaterialTheme.typography.titleSmall)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            trendingTerms.forEach { term ->
                                AssistChip(
                                    onClick = { onSubmitSearch(term) },
                                    label = { Text(term) },
                                    leadingIcon = { Icon(Icons.Default.TrendingUp, contentDescription = null) }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Filter & Sort", style = MaterialTheme.typography.titleSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = selectedCategoryId == null,
                                onClick = { onSelectCategory(null) },
                                label = { Text("All Categories") }
                            )
                        }
                        items(categories, key = { it.id }) { cat ->
                            FilterChip(
                                selected = selectedCategoryId == cat.id,
                                onClick = { onSelectCategory(if (selectedCategoryId == cat.id) null else cat.id) },
                                label = { Text(cat.name) }
                            )
                        }
                    }
                }
            }

            if (results.isEmpty()) {
                item {
                    EmptyStateView(
                        title = "No matching apps found",
                        subtitle = "Try another app name, developer, package ID, or clear your category filter."
                    )
                }
            } else {
                item {
                    Text(
                        text = "${results.size} Applications",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(results, key = { it.appId }) { app ->
                    AppListRowCard(app = app, onClick = { onAppClick(app.appId) })
                }
            }
        }
    }
}
