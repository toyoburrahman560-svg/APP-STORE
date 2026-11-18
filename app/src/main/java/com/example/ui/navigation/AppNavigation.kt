package com.example.ui.navigation

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.AppStoreApplication
import com.example.R
import com.example.data.model.LanguagePreference
import com.example.data.model.ThemePreference
import com.example.data.model.UiState
import com.example.ui.screens.AppDetailScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CategoriesScreen
import com.example.ui.screens.DeveloperDashboardScreen
import com.example.ui.screens.GamesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MyAppsScreen
import com.example.ui.screens.MyDownloadsScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.PublishScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.WishlistLibraryScreen
import com.example.ui.theme.AppStoreTheme
import com.example.ui.viewmodel.AppDetailViewModel
import com.example.ui.viewmodel.AuthSessionViewModel
import com.example.ui.viewmodel.DeveloperViewModel
import com.example.ui.viewmodel.DownloadsViewModel
import com.example.ui.viewmodel.MarketplaceViewModel
import java.util.Locale

object AppRoutes {
    const val SPLASH = "splash"
    const val AUTH = "auth"
    const val MAIN = "main"
    const val SEARCH = "search"
    const val APP_DETAIL = "app_detail/{appId}"
    const val MY_DOWNLOADS = "my_downloads"
    const val MY_APPS = "my_apps"
    const val DEV_DASHBOARD = "developer_dashboard"
    const val WISHLIST_LIBRARY = "wishlist_library"
    const val NOTIFICATIONS = "notifications"

    fun appDetail(appId: String) = "app_detail/$appId"
}

enum class MainTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME(R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home, "tab_home"),
    GAMES(R.string.nav_games, Icons.Filled.SportsEsports, Icons.Outlined.SportsEsports, "tab_games"),
    CATEGORIES(R.string.nav_categories, Icons.Filled.Category, Icons.Outlined.Category, "tab_categories"),
    PUBLISH(R.string.nav_publish, Icons.Filled.CloudUpload, Icons.Outlined.CloudUpload, "tab_publish"),
    PROFILE(R.string.nav_profile, Icons.Filled.Person, Icons.Outlined.Person, "tab_profile")
}

@Composable
fun AppStoreRoot() {
    val context = LocalContext.current
    val navController = rememberNavController()

    val authSessionVm: AuthSessionViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as AppStoreApplication
                AuthSessionViewModel(
                    app.container.authRepository,
                    app.container.interactionRepository,
                    app.container.preferencesRepository
                )
            }
        }
    )

    val marketplaceVm: MarketplaceViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as AppStoreApplication
                MarketplaceViewModel(
                    app.container.marketplaceRepository,
                    app.container.preferencesRepository
                )
            }
        }
    )

    val downloadsVm: DownloadsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as AppStoreApplication
                DownloadsViewModel(
                    app.container.apkDownloadService,
                    app.container.marketplaceRepository,
                    app.container.interactionRepository
                )
            }
        }
    )

    val settings by authSessionVm.settings.collectAsStateWithLifecycle()
    val currentUser by authSessionVm.currentUser.collectAsStateWithLifecycle()
    val userProfile by authSessionVm.userProfile.collectAsStateWithLifecycle()
    val developerProfile by authSessionVm.developerProfile.collectAsStateWithLifecycle()
    val wishlist by authSessionVm.wishlist.collectAsStateWithLifecycle()
    val downloadHistory by authSessionVm.downloadHistory.collectAsStateWithLifecycle()
    val notifications by authSessionVm.notifications.collectAsStateWithLifecycle()
    val myReviews by authSessionVm.myReviews.collectAsStateWithLifecycle()
    val authLoading by authSessionVm.authLoading.collectAsStateWithLifecycle()
    val authError by authSessionVm.authError.collectAsStateWithLifecycle()
    val splashDone by authSessionVm.splashDone.collectAsStateWithLifecycle()

    val homeState by marketplaceVm.homeState.collectAsStateWithLifecycle()
    val categories by marketplaceVm.categories.collectAsStateWithLifecycle()
    val isRefreshing by marketplaceVm.isRefreshing.collectAsStateWithLifecycle()
    val searchQuery by marketplaceVm.searchQuery.collectAsStateWithLifecycle()
    val selectedCategoryId by marketplaceVm.selectedCategoryId.collectAsStateWithLifecycle()
    val sortOrder by marketplaceVm.sortOrder.collectAsStateWithLifecycle()
    val filteredSearchResults by marketplaceVm.filteredSearchResults.collectAsStateWithLifecycle()
    val activeDownloadCount by downloadsVm.activeDownloadCount.collectAsStateWithLifecycle()
    val activeDownloadItems by downloadsVm.sortedDownloads.collectAsStateWithLifecycle()
    val downloadsFeedback by downloadsVm.feedbackMessage.collectAsStateWithLifecycle()

    val darkTheme = when (settings.themePreference) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }

    val activityResultRegistryOwner = LocalActivityResultRegistryOwner.current
    val onBackPressedDispatcherOwner = LocalOnBackPressedDispatcherOwner.current
    val localizedResources = remember(context, settings.languagePreference) {
        context.createLocalizedResources(settings.languagePreference)
    }
    val localizedContext = remember(context, localizedResources) {
        object : ContextWrapper(context) {
            override fun getResources(): Resources = localizedResources
            override fun getAssets(): AssetManager = localizedResources.assets
        }
    }

    var selectedTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    val unreadNotifications = remember(notifications) { notifications.count { !it.read } }
    val allApprovedApps = (homeState as? UiState.Success)?.data?.allApprovedApps ?: emptyList()

    val providedLocals = buildList {
        add(LocalContext provides localizedContext)
        add(LocalConfiguration provides localizedResources.configuration)
        if (activityResultRegistryOwner != null) {
            add(LocalActivityResultRegistryOwner provides activityResultRegistryOwner)
        }
        if (onBackPressedDispatcherOwner != null) {
            add(LocalOnBackPressedDispatcherOwner provides onBackPressedDispatcherOwner)
        }
    }.toTypedArray()

    CompositionLocalProvider(*providedLocals) {
        AppStoreTheme(darkTheme = darkTheme) {
            NavHost(
                navController = navController,
                startDestination = AppRoutes.SPLASH
            ) {
                composable(AppRoutes.SPLASH) {
                    SplashScreen(
                        onInitialize = { authSessionVm.initializeSession(context) },
                        isReady = splashDone,
                        onNavigateNext = {
                            val target = if (currentUser != null) AppRoutes.MAIN else AppRoutes.AUTH
                            navController.navigate(target) {
                                popUpTo(AppRoutes.SPLASH) { inclusive = true }
                            }
                        }
                    )
                }

                composable(AppRoutes.AUTH) {
                    AuthScreen(
                        isLoading = authLoading,
                        errorMessage = authError,
                        onGoogleSignIn = {
                            authSessionVm.signInWithGoogle(context) {
                                navController.navigate(AppRoutes.MAIN) {
                                    popUpTo(AppRoutes.AUTH) { inclusive = true }
                                }
                            }
                        },
                        onBrowseAsGuest = {
                            authSessionVm.enterGuestMode()
                            navController.navigate(AppRoutes.MAIN) {
                                popUpTo(AppRoutes.AUTH) { inclusive = true }
                            }
                        }
                    )
                }

                composable(AppRoutes.MAIN) {
                    val uid = currentUser?.uid
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val isExpanded = maxWidth >= 600.dp

                        Scaffold(
                            contentWindowInsets = WindowInsets.navigationBars,
                            bottomBar = {
                                if (!isExpanded) {
                                    NavigationBar {
                                        MainTab.entries.forEach { tab ->
                                            val selected = selectedTab == tab
                                            NavigationBarItem(
                                                selected = selected,
                                                onClick = { selectedTab = tab },
                                                icon = {
                                                    Icon(
                                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                        contentDescription = stringResource(tab.labelRes)
                                                    )
                                                },
                                                label = { Text(stringResource(tab.labelRes), maxLines = 1) },
                                                modifier = Modifier.testTag(tab.testTag)
                                            )
                                        }
                                    }
                                }
                            }
                        ) { innerPadding ->
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                if (isExpanded) {
                                    NavigationRail {
                                        MainTab.entries.forEach { tab ->
                                            val selected = selectedTab == tab
                                            NavigationRailItem(
                                                selected = selected,
                                                onClick = { selectedTab = tab },
                                                icon = {
                                                    Icon(
                                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                        contentDescription = stringResource(tab.labelRes)
                                                    )
                                                },
                                                label = { Text(stringResource(tab.labelRes)) }
                                            )
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize(),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    Box(modifier = Modifier.widthIn(max = 900.dp).fillMaxSize()) {
                                        when (selectedTab) {
                                            MainTab.HOME -> HomeScreen(
                                                homeState = homeState,
                                                isRefreshing = isRefreshing,
                                                userPhotoUrl = currentUser?.photoUrl?.toString(),
                                                unreadNotifications = unreadNotifications,
                                                activeDownloadCount = activeDownloadCount,
                                                onRefresh = { marketplaceVm.refresh() },
                                                onSearchClick = { navController.navigate(AppRoutes.SEARCH) },
                                                onDownloadsClick = { navController.navigate(AppRoutes.MY_DOWNLOADS) },
                                                onNotificationsClick = {
                                                    if (uid == null) selectedTab = MainTab.PROFILE
                                                    else navController.navigate(AppRoutes.NOTIFICATIONS)
                                                },
                                                onAvatarClick = { selectedTab = MainTab.PROFILE },
                                                onAppClick = { appId -> navController.navigate(AppRoutes.appDetail(appId)) },
                                                onPublishClick = { selectedTab = MainTab.PUBLISH },
                                                onSeeAllCategory = { catId ->
                                                    marketplaceVm.selectCategory(catId)
                                                    selectedTab = MainTab.CATEGORIES
                                                }
                                            )

                                            MainTab.GAMES -> GamesScreen(
                                                homeState = homeState,
                                                categories = categories,
                                                userPhotoUrl = currentUser?.photoUrl?.toString(),
                                                unreadNotifications = unreadNotifications,
                                                activeDownloadCount = activeDownloadCount,
                                                onRefresh = { marketplaceVm.refresh() },
                                                onSearchClick = { navController.navigate(AppRoutes.SEARCH) },
                                                onDownloadsClick = { navController.navigate(AppRoutes.MY_DOWNLOADS) },
                                                onNotificationsClick = {
                                                    if (uid == null) selectedTab = MainTab.PROFILE
                                                    else navController.navigate(AppRoutes.NOTIFICATIONS)
                                                },
                                                onAvatarClick = { selectedTab = MainTab.PROFILE },
                                                onAppClick = { appId -> navController.navigate(AppRoutes.appDetail(appId)) }
                                            )

                                            MainTab.CATEGORIES -> CategoriesScreen(
                                                categories = categories,
                                                selectedCategoryId = selectedCategoryId,
                                                sortOrder = sortOrder,
                                                filteredApps = filteredSearchResults,
                                                language = settings.languagePreference,
                                                userPhotoUrl = currentUser?.photoUrl?.toString(),
                                                unreadNotifications = unreadNotifications,
                                                activeDownloadCount = activeDownloadCount,
                                                onSelectCategory = { marketplaceVm.selectCategory(it) },
                                                onSortChange = { marketplaceVm.setSortOrder(it) },
                                                onSearchClick = { navController.navigate(AppRoutes.SEARCH) },
                                                onDownloadsClick = { navController.navigate(AppRoutes.MY_DOWNLOADS) },
                                                onNotificationsClick = {
                                                    if (uid == null) selectedTab = MainTab.PROFILE
                                                    else navController.navigate(AppRoutes.NOTIFICATIONS)
                                                },
                                                onAvatarClick = { selectedTab = MainTab.PROFILE },
                                                onAppClick = { appId -> navController.navigate(AppRoutes.appDetail(appId)) }
                                            )

                                            MainTab.PUBLISH -> {
                                                if (uid == null) {
                                                    PublishScreen(
                                                        currentUserUid = null,
                                                        userEmail = null,
                                                        userDisplayName = null,
                                                        userProfile = null,
                                                        developerProfile = null,
                                                        categories = categories,
                                                        parsedApk = null,
                                                        parsingApk = false,
                                                        submitting = false,
                                                        actionMessage = null,
                                                        authLoading = authLoading,
                                                        authError = authError,
                                                        onGoogleSignIn = { authSessionVm.signInWithGoogle(context) },
                                                        onRegisterDeveloper = { _, _, _, _, _, _ -> },
                                                        onInspectApk = {},
                                                        onSubmitPublish = { _, _ -> },
                                                        onOpenMyApps = {},
                                                        onOpenDashboard = {},
                                                        onClearMessage = {}
                                                    )
                                                } else {
                                                    key(uid) {
                                                        val devVm: DeveloperViewModel = viewModel(
                                                            key = uid,
                                                            factory = viewModelFactory {
                                                                initializer {
                                                                    val app = checkNotNull(this[APPLICATION_KEY]) as AppStoreApplication
                                                                    DeveloperViewModel(
                                                                        uid,
                                                                        app.container.developerPublishRepository,
                                                                        app.container.apkParserService
                                                                    )
                                                                }
                                                            }
                                                        )
                                                        val parsedApk by devVm.parsedApk.collectAsStateWithLifecycle()
                                                        val parsingApk by devVm.parsingApk.collectAsStateWithLifecycle()
                                                        val submitting by devVm.submitting.collectAsStateWithLifecycle()
                                                        val actionMsg by devVm.actionMessage.collectAsStateWithLifecycle()

                                                        PublishScreen(
                                                            currentUserUid = uid,
                                                            userEmail = currentUser?.email,
                                                            userDisplayName = currentUser?.displayName,
                                                            userProfile = userProfile,
                                                            developerProfile = developerProfile,
                                                            categories = categories,
                                                            parsedApk = parsedApk,
                                                            parsingApk = parsingApk,
                                                            submitting = submitting,
                                                            actionMessage = actionMsg,
                                                            authLoading = authLoading,
                                                            authError = authError,
                                                            onGoogleSignIn = { authSessionVm.signInWithGoogle(context) },
                                                            onRegisterDeveloper = { n, d, e, w, p, cb ->
                                                                authSessionVm.registerDeveloper(n, d, e, w, p, cb)
                                                            },
                                                            onInspectApk = { uri -> devVm.inspectApk(uri) },
                                                            onSubmitPublish = { draft, cb -> devVm.submitNewApplication(draft, cb) },
                                                            onOpenMyApps = { navController.navigate(AppRoutes.MY_APPS) },
                                                            onOpenDashboard = { navController.navigate(AppRoutes.DEV_DASHBOARD) },
                                                            onClearMessage = { devVm.clearMessage() }
                                                        )
                                                    }
                                                }
                                            }

                                            MainTab.PROFILE -> ProfileScreen(
                                                currentUser = currentUser,
                                                userProfile = userProfile,
                                                settings = settings,
                                                wishlistCount = wishlist.size,
                                                downloadsCount = downloadHistory.size,
                                                activeDownloadsCount = activeDownloadCount,
                                                downloadHistory = downloadHistory,
                                                unreadNotifications = unreadNotifications,
                                                myReviews = myReviews,
                                                authLoading = authLoading,
                                                authError = authError,
                                                onGoogleSignIn = { authSessionVm.signInWithGoogle(context) },
                                                onSignOut = {
                                                    authSessionVm.signOut(context)
                                                    navController.navigate(AppRoutes.AUTH) {
                                                        popUpTo(AppRoutes.MAIN) { inclusive = true }
                                                    }
                                                },
                                                onDeleteAccount = {
                                                    authSessionVm.deleteAccount(context) { res ->
                                                        if (res.isSuccess) {
                                                            navController.navigate(AppRoutes.AUTH) {
                                                                popUpTo(AppRoutes.MAIN) { inclusive = true }
                                                            }
                                                        }
                                                    }
                                                },
                                                onSetTheme = { authSessionVm.setTheme(it) },
                                                onSetLanguage = { authSessionVm.setLanguage(it) },
                                                onUpdateNotificationPref = { u, r, a, s ->
                                                    authSessionVm.updateNotificationPrefs(u, r, a, s)
                                                },
                                                onOpenMyDownloads = { navController.navigate(AppRoutes.MY_DOWNLOADS) },
                                                onOpenApp = { appId -> navController.navigate(AppRoutes.appDetail(appId)) },
                                                onOpenMyApps = { navController.navigate(AppRoutes.MY_APPS) },
                                                onOpenDashboard = { navController.navigate(AppRoutes.DEV_DASHBOARD) },
                                                onOpenWishlistLibrary = { navController.navigate(AppRoutes.WISHLIST_LIBRARY) },
                                                onOpenNotifications = { navController.navigate(AppRoutes.NOTIFICATIONS) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                composable(AppRoutes.SEARCH) {
                    SearchScreen(
                        query = searchQuery,
                        selectedCategoryId = selectedCategoryId,
                        sortOrder = sortOrder,
                        results = filteredSearchResults,
                        allApps = allApprovedApps,
                        categories = categories,
                        recentSearches = settings.recentSearches,
                        language = settings.languagePreference,
                        onQueryChange = { marketplaceVm.updateSearchQuery(it) },
                        onSubmitSearch = { marketplaceVm.submitSearchQuery(it) },
                        onSelectCategory = { marketplaceVm.selectCategory(it) },
                        onSortChange = { marketplaceVm.setSortOrder(it) },
                        onClearHistory = { marketplaceVm.clearSearchHistory() },
                        onBack = { navController.popBackStack() },
                        onAppClick = { appId -> navController.navigate(AppRoutes.appDetail(appId)) }
                    )
                }

                composable(
                    route = AppRoutes.APP_DETAIL,
                    arguments = listOf(navArgument("appId") { type = NavType.StringType })
                ) { entry ->
                    val appId = entry.arguments?.getString("appId") ?: return@composable
                    LaunchedEffect(appId) {
                        authSessionVm.recordRecentlyViewed(appId)
                    }
                    val detailVm: AppDetailViewModel = viewModel(
                        key = appId,
                        factory = viewModelFactory {
                            initializer {
                                val app = checkNotNull(this[APPLICATION_KEY]) as AppStoreApplication
                                AppDetailViewModel(
                                    appId,
                                    app.container.marketplaceRepository,
                                    app.container.interactionRepository,
                                    app.container.apkDownloadService
                                )
                            }
                        }
                    )
                    val detailState by detailVm.detailState.collectAsStateWithLifecycle()
                    val downloadState by detailVm.downloadState.collectAsStateWithLifecycle()
                    val feedback by detailVm.feedbackMessage.collectAsStateWithLifecycle()
                    val isSaved = wishlist.any { it.appId == appId }

                    AppDetailScreen(
                        detailState = detailState,
                        downloadState = downloadState,
                        currentUserId = currentUser?.uid,
                        isSavedInWishlist = isSaved,
                        feedbackMessage = feedback,
                        onClearFeedback = { detailVm.clearFeedback() },
                        onBack = { navController.popBackStack() },
                        onRefreshInstallStatus = { detailVm.refreshInstallStatus() },
                        onDownloadClick = { app, ver -> detailVm.startApkDownload(app, ver) },
                        onPauseDownload = { detailVm.pauseDownload() },
                        onResumeDownload = { detailVm.resumeDownload() },
                        onRetryDownload = { detailVm.retryDownload() },
                        onCancelDownload = { detailVm.cancelDownload() },
                        onInstallClick = { app, ver ->
                            val file = (detailState as? UiState.Success)?.data?.localApkFile
                                ?: downloadState?.localFilePath?.let { java.io.File(it) }?.takeIf { it.exists() }
                            if (file != null) {
                                detailVm.installLocalApk(file, app, ver)
                            }
                        },
                        onOpenDownloads = { navController.navigate(AppRoutes.MY_DOWNLOADS) },
                        onToggleWishlist = { app, saved -> detailVm.toggleWishlist(app, saved) },
                        onSubmitReview = { r, c -> detailVm.submitReview(r, c) },
                        onDeleteReview = { detailVm.deleteReview() },
                        onMarkHelpful = { reviewId -> detailVm.markHelpful(reviewId) },
                        onReplyToReview = { reviewId, reply -> detailVm.replyToReview(reviewId, reply) },
                        onSubmitReport = { t, id, reason, details -> detailVm.reportTarget(t, id, reason, details) },
                        onOpenSimilarApp = { simId -> navController.navigate(AppRoutes.appDetail(simId)) },
                        onRequireAuth = {
                            selectedTab = MainTab.PROFILE
                            navController.popBackStack(AppRoutes.MAIN, inclusive = false)
                        },
                        canInstallPackages = { detailVm.canInstallPackages() },
                        onOpenInstallPermissionSettings = {
                            context.startActivity(detailVm.installSettingsIntent())
                        }
                    )
                }

                composable(AppRoutes.MY_DOWNLOADS) {
                    MyDownloadsScreen(
                        activeDownloads = activeDownloadItems,
                        historyRecords = downloadHistory,
                        feedbackMessage = downloadsFeedback,
                        onClearFeedback = { downloadsVm.clearFeedback() },
                        onRefreshInstalledStates = { downloadsVm.refreshInstalledStates() },
                        onPause = { appId -> downloadsVm.pause(appId) },
                        onResume = { appId -> downloadsVm.resume(appId) },
                        onCancel = { appId -> downloadsVm.cancel(appId) },
                        onRetry = { appId -> downloadsVm.retry(appId) },
                        onRemoveActive = { appId, recordId ->
                            downloadsVm.removeEntry(appId, deleteApkFile = true, recordId = recordId)
                        },
                        onInstallActive = { state -> downloadsVm.installFromState(state) },
                        onInstallRecord = { record -> downloadsVm.installFromRecord(record) },
                        onRedownloadRecord = { appId -> downloadsVm.redownloadApp(appId) },
                        onDeleteHistoryRecord = { record -> downloadsVm.deleteHistoryRecordOnly(record) },
                        getLocalApk = { pkg, code -> downloadsVm.getLocalApk(pkg, code) },
                        canInstallPackages = { downloadsVm.canInstallPackages() },
                        onOpenInstallPermissionSettings = {
                            context.startActivity(downloadsVm.installSettingsIntent())
                        },
                        onOpenApp = { appId -> navController.navigate(AppRoutes.appDetail(appId)) },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(AppRoutes.MY_APPS) {
                    val uid = currentUser?.uid
                    if (uid == null) {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    } else {
                        key(uid) {
                            val devVm: DeveloperViewModel = viewModel(
                                key = uid,
                                factory = viewModelFactory {
                                    initializer {
                                        val app = checkNotNull(this[APPLICATION_KEY]) as AppStoreApplication
                                        DeveloperViewModel(
                                            uid,
                                            app.container.developerPublishRepository,
                                            app.container.apkParserService
                                        )
                                    }
                                }
                            )
                            val dashState by devVm.dashboardState.collectAsStateWithLifecycle()
                            val parsedApk by devVm.parsedApk.collectAsStateWithLifecycle()
                            val msg by devVm.actionMessage.collectAsStateWithLifecycle()

                            MyAppsScreen(
                                dashboardState = dashState,
                                parsedApk = parsedApk,
                                actionMessage = msg,
                                onClearMessage = { devVm.clearMessage() },
                                onInspectApk = { devVm.inspectApk(it) },
                                onUpdateMetadata = { id, n, sd, fd, wn, w, p, res ->
                                    devVm.updateAppMetadata(id, n, sd, fd, wn, w, p, res)
                                },
                                onUploadNewVersion = { app, url, notes ->
                                    devVm.uploadNewVersion(app, url, notes)
                                },
                                onArchiveApp = { devVm.archiveApp(it) },
                                onOpenAppDetail = { appId -> navController.navigate(AppRoutes.appDetail(appId)) },
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }

                composable(AppRoutes.DEV_DASHBOARD) {
                    val uid = currentUser?.uid
                    if (uid == null) {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    } else {
                        key(uid) {
                            val devVm: DeveloperViewModel = viewModel(
                                key = uid,
                                factory = viewModelFactory {
                                    initializer {
                                        val app = checkNotNull(this[APPLICATION_KEY]) as AppStoreApplication
                                        DeveloperViewModel(
                                            uid,
                                            app.container.developerPublishRepository,
                                            app.container.apkParserService
                                        )
                                    }
                                }
                            )
                            val dashState by devVm.dashboardState.collectAsStateWithLifecycle()
                            val range by devVm.dateRange.collectAsStateWithLifecycle()

                            DeveloperDashboardScreen(
                                dashboardState = dashState,
                                selectedRange = range,
                                onSelectRange = { devVm.setDateRange(it) },
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }

                composable(AppRoutes.WISHLIST_LIBRARY) {
                    WishlistLibraryScreen(
                        wishlist = wishlist,
                        downloads = downloadHistory,
                        allApprovedApps = allApprovedApps,
                        recentlyViewedIds = settings.recentlyViewedAppIds,
                        getInstalledStatus = { pkg -> downloadsVm.getInstalledStatus(pkg) },
                        getLocalApk = { pkg, code -> downloadsVm.getLocalApk(pkg, code) },
                        onRemoveDownloadEntry = { rec -> downloadsVm.deleteHistoryRecordOnly(rec) },
                        onOpenDownloads = { navController.navigate(AppRoutes.MY_DOWNLOADS) },
                        onOpenApp = { appId -> navController.navigate(AppRoutes.appDetail(appId)) },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(AppRoutes.NOTIFICATIONS) {
                    NotificationsScreen(
                        notifications = notifications,
                        onMarkRead = { authSessionVm.markNotificationRead(it) },
                        onOpenApp = { appId -> navController.navigate(AppRoutes.appDetail(appId)) },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

private fun Context.createLocalizedResources(language: LanguagePreference): Resources {
    val locale = Locale.forLanguageTag(language.tag)
    val config = Configuration(resources.configuration).apply {
        setLocale(locale)
    }
    return createConfigurationContext(config).resources
}
