package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.download.ActiveDownloadState
import com.example.data.download.ApkDownloadService
import com.example.data.download.InstalledPackageStatus
import com.example.data.firestore.unwrapFirestoreException
import com.example.data.model.AppListing
import com.example.data.model.AppMedia
import com.example.data.model.AppReview
import com.example.data.model.AppVersion
import com.example.data.model.DownloadPhase
import com.example.data.model.UiState
import com.example.data.repository.MarketplaceRepository
import com.example.data.repository.UserInteractionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class AppDetailData(
    val app: AppListing,
    val versions: List<AppVersion>,
    val media: List<AppMedia>,
    val reviews: List<AppReview>,
    val similarApps: List<AppListing>,
    val installedStatus: InstalledPackageStatus,
    val localApkFile: File?
)

class AppDetailViewModel(
    private val appId: String,
    private val marketplaceRepository: MarketplaceRepository,
    private val interactionRepository: UserInteractionRepository,
    private val downloadService: ApkDownloadService
) : ViewModel() {

    private val _installCheckTick = MutableStateFlow(0)
    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    val downloadState: StateFlow<ActiveDownloadState?> = downloadService.downloads
        .combine(_installCheckTick) { map, _ -> map[appId] }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val detailState: StateFlow<UiState<AppDetailData>> = combine(
        marketplaceRepository.observeAppById(appId),
        marketplaceRepository.observeApprovedVersions(appId),
        marketplaceRepository.observePublicMedia(appId),
        marketplaceRepository.observeReviews(appId),
        marketplaceRepository.observeApprovedApps(),
        _installCheckTick
    ) { values ->
        val app = values[0] as AppListing?
        @Suppress("UNCHECKED_CAST")
        val versions = values[1] as List<AppVersion>
        @Suppress("UNCHECKED_CAST")
        val media = values[2] as List<AppMedia>
        @Suppress("UNCHECKED_CAST")
        val reviews = values[3] as List<AppReview>
        @Suppress("UNCHECKED_CAST")
        val allApps = values[4] as List<AppListing>

        if (app == null) {
            UiState.Error("Application not found or no longer available.")
        } else {
            val latestCode = versions.firstOrNull()?.versionCode ?: app.versionCode
            val installed = downloadService.getInstalledStatus(app.packageName)
            val localFile = downloadService.getLocalDownloadedApk(app.packageName, latestCode)
            val similar = allApps.filter { it.appId != app.appId && it.categoryId == app.categoryId }.take(6)
            UiState.Success(
                AppDetailData(
                    app = app,
                    versions = versions,
                    media = media,
                    reviews = reviews,
                    similarApps = similar,
                    installedStatus = installed,
                    localApkFile = localFile
                )
            )
        }
    }.catch { error ->
        val root = error.unwrapFirestoreException()
        if (error is CancellationException && root == error) throw error
        emit(UiState.Error(root.localizedMessage ?: "Failed to load app details"))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    fun refreshInstallStatus() {
        _installCheckTick.value += 1
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun startApkDownload(app: AppListing, latestVersion: AppVersion?) {
        downloadService.startDownload(app, latestVersion) { state ->
            if (state.phase == DownloadPhase.READY_TO_INSTALL || state.phase == DownloadPhase.INSTALLED) {
                refreshInstallStatus()
            }
        }
    }

    fun pauseDownload() {
        downloadService.pauseDownload(appId)
    }

    fun resumeDownload() {
        downloadService.resumeDownload(appId) { state ->
            if (state.phase == DownloadPhase.READY_TO_INSTALL || state.phase == DownloadPhase.INSTALLED) {
                refreshInstallStatus()
            }
        }
    }

    fun retryDownload() {
        downloadService.retryDownload(appId) { state ->
            if (state.phase == DownloadPhase.READY_TO_INSTALL || state.phase == DownloadPhase.INSTALLED) {
                refreshInstallStatus()
            }
        }
    }

    fun cancelDownload() {
        downloadService.cancelDownload(appId)
    }

    fun canInstallPackages(): Boolean = downloadService.canRequestPackageInstalls()

    fun installSettingsIntent() = downloadService.createInstallUnknownAppsSettingsIntent()

    fun installLocalApk(file: File, app: AppListing, version: AppVersion?) {
        val versionId = version?.versionId ?: "${app.appId}_v${app.versionCode}"
        val versionName = version?.versionName ?: app.versionName
        val versionCode = version?.versionCode ?: app.versionCode
        val sha256 = version?.sha256.orEmpty()
        val fileSize = file.length().coerceAtLeast(version?.apkSize ?: app.apkSizeBytes)

        downloadService.launchPackageInstaller(file)
            .onSuccess {
                viewModelScope.launch {
                    interactionRepository.recordDownloadEvent(
                        app = app,
                        versionId = versionId,
                        versionName = versionName,
                        versionCode = versionCode,
                        sha256 = sha256,
                        status = "installed",
                        downloadedBytes = fileSize,
                        fileSizeBytes = fileSize
                    )
                    downloadService.syncInstalledStatesFromDevice()
                    refreshInstallStatus()
                }
            }
            .onFailure {
                _feedbackMessage.value = it.localizedMessage ?: "Failed to open Android Package Installer"
            }
    }

    fun toggleWishlist(app: AppListing, saved: Boolean) {
        viewModelScope.launch {
            interactionRepository.toggleWishlist(app, saved)
                .onSuccess { added ->
                    _feedbackMessage.value = if (added) "Added to Wishlist" else "Removed from Wishlist"
                }
                .onFailure {
                    _feedbackMessage.value = it.localizedMessage ?: "Could not update wishlist"
                }
        }
    }

    fun submitReview(rating: Int, comment: String) {
        viewModelScope.launch {
            interactionRepository.submitOrUpdateReview(appId, rating, comment)
                .onSuccess { _feedbackMessage.value = "Review saved" }
                .onFailure { _feedbackMessage.value = it.localizedMessage ?: "Failed to submit review" }
        }
    }

    fun deleteReview() {
        viewModelScope.launch {
            interactionRepository.deleteOwnReview(appId)
                .onSuccess { _feedbackMessage.value = "Review deleted" }
                .onFailure { _feedbackMessage.value = it.localizedMessage ?: "Failed to delete review" }
        }
    }

    fun markHelpful(reviewId: String) {
        viewModelScope.launch {
            interactionRepository.voteReviewHelpful(reviewId)
                .onFailure { _feedbackMessage.value = it.localizedMessage ?: "Sign in to vote" }
        }
    }

    fun replyToReview(reviewId: String, reply: String) {
        viewModelScope.launch {
            interactionRepository.replyToReview(reviewId, reply)
                .onSuccess { _feedbackMessage.value = "Reply posted" }
                .onFailure { _feedbackMessage.value = it.localizedMessage ?: "Failed to post reply" }
        }
    }

    fun reportTarget(targetType: String, targetId: String, reason: String, details: String) {
        viewModelScope.launch {
            interactionRepository.submitReport(targetType, targetId, reason, details)
                .onSuccess { _feedbackMessage.value = "Report submitted for admin review" }
                .onFailure { _feedbackMessage.value = it.localizedMessage ?: "Failed to submit report" }
        }
    }
}
