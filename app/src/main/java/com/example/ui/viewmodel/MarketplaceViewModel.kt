package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firestore.unwrapFirestoreException
import com.example.data.model.Announcement
import com.example.data.model.AppListing
import com.example.data.model.MarketplaceCategory
import com.example.data.model.UiState
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.MarketplaceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortOrder(val labelEn: String, val labelBn: String) {
    POPULARITY("Most Popular", "জনপ্রিয়তা"),
    RATING("Highest Rated", "সেরা রেটিং"),
    NEWEST("Newest", "নতুন রিলিজ"),
    DOWNLOADS("Most Downloads", "সর্বাধিক ডাউনলোড")
}

data class HomeSectionsState(
    val allApprovedApps: List<AppListing> = emptyList(),
    val featured: List<AppListing> = emptyList(),
    val trending: List<AppListing> = emptyList(),
    val popular: List<AppListing> = emptyList(),
    val topRated: List<AppListing> = emptyList(),
    val newReleases: List<AppListing> = emptyList(),
    val recommended: List<AppListing> = emptyList(),
    val recentlyUpdated: List<AppListing> = emptyList(),
    val popularGames: List<AppListing> = emptyList(),
    val popularTools: List<AppListing> = emptyList(),
    val editorsChoice: List<AppListing> = emptyList(),
    val developerSpotlight: List<AppListing> = emptyList(),
    val announcements: List<Announcement> = emptyList()
)

class MarketplaceViewModel(
    private val marketplaceRepository: MarketplaceRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.POPULARITY)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _refreshTrigger = MutableStateFlow(0)
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val categories: StateFlow<List<MarketplaceCategory>> = marketplaceRepository.observeCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = MarketplaceRepository.DefaultCategories
        )

    val homeState: StateFlow<UiState<HomeSectionsState>> = combine(
        marketplaceRepository.observeApprovedApps(),
        marketplaceRepository.observeAnnouncements(),
        _refreshTrigger
    ) { apps, announcements, _ ->
        _isRefreshing.value = false
        val featured = apps.filter { it.featured }.ifEmpty { apps.sortedByDescending { it.downloadCount }.take(5) }
        val trending = apps.sortedByDescending { it.trendingScore + it.downloadCount }.take(10)
        val popular = apps.sortedByDescending { it.downloadCount }.take(10)
        val topRated = apps.sortedByDescending { it.averageRating * 100 + it.totalReviews }.take(10)
        val newReleases = apps.sortedByDescending { it.createdAt?.seconds ?: 0L }.take(10)
        val recommended = apps.sortedByDescending { (it.averageRating * 20) + (if (it.editorsChoice) 30 else 0) }.take(10)
        val recentlyUpdated = apps.sortedByDescending { it.updatedAt?.seconds ?: 0L }.take(10)
        val popularGames = apps.filter { it.categoryGroup == "games" }.sortedByDescending { it.downloadCount }.take(10)
        val popularTools = apps.filter {
            it.categoryId.equals("tools", true) || it.categoryId.equals("productivity", true)
        }.sortedByDescending { it.downloadCount }.take(10)
        val editorsChoice = apps.filter { it.editorsChoice }.ifEmpty { topRated.take(6) }
        val spotlight = apps.distinctBy { it.ownerUid }.take(8)

        UiState.Success(
            HomeSectionsState(
                allApprovedApps = apps,
                featured = featured,
                trending = trending,
                popular = popular,
                topRated = topRated,
                newReleases = newReleases,
                recommended = recommended,
                recentlyUpdated = recentlyUpdated,
                popularGames = popularGames,
                popularTools = popularTools,
                editorsChoice = editorsChoice,
                developerSpotlight = spotlight,
                announcements = announcements
            )
        ) as UiState<HomeSectionsState>
    }.catch { error ->
        _isRefreshing.value = false
        val root = error.unwrapFirestoreException()
        if (error is CancellationException && root == error) throw error
        emit(UiState.Error(root.localizedMessage ?: "Failed to load marketplace catalog"))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Loading
    )

    val filteredSearchResults: StateFlow<List<AppListing>> = combine(
        homeState,
        _searchQuery,
        _selectedCategoryId,
        _sortOrder
    ) { state, query, categoryId, sort ->
        val apps = (state as? UiState.Success)?.data?.allApprovedApps ?: emptyList()
        val q = query.trim().lowercase()
        val filtered = apps.filter { app ->
            val matchesCategory = categoryId == null || app.categoryId.equals(categoryId, ignoreCase = true)
            val matchesQuery = q.isEmpty() ||
                app.appName.lowercase().contains(q) ||
                app.developerName.lowercase().contains(q) ||
                app.packageName.lowercase().contains(q) ||
                app.categoryName.lowercase().contains(q) ||
                app.shortDescription.lowercase().contains(q) ||
                app.description.lowercase().contains(q)
            matchesCategory && matchesQuery
        }
        when (sort) {
            SortOrder.POPULARITY -> filtered.sortedByDescending { it.trendingScore + it.downloadCount }
            SortOrder.RATING -> filtered.sortedByDescending { it.averageRating }
            SortOrder.NEWEST -> filtered.sortedByDescending { it.createdAt?.seconds ?: 0L }
            SortOrder.DOWNLOADS -> filtered.sortedByDescending { it.downloadCount }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun submitSearchQuery(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            preferencesRepository.addSearchQuery(query)
        }
    }

    fun selectCategory(categoryId: String?) {
        _selectedCategoryId.value = categoryId
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            preferencesRepository.clearSearchHistory()
        }
    }

    fun refresh() {
        _isRefreshing.value = true
        _refreshTrigger.value += 1
    }
}
