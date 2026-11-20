package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.apk.ApkParserService
import com.example.data.firestore.unwrapFirestoreException
import com.example.data.model.AppListing
import com.example.data.model.AppSubmission
import com.example.data.model.DeveloperAnalytics
import com.example.data.model.ParsedApkMetadata
import com.example.data.model.UiState
import com.example.data.repository.DeveloperPublishRepository
import com.example.data.repository.PublishSubmissionDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AnalyticsRange(val label: String) {
    DAYS_7("7D"),
    DAYS_30("30D"),
    ALL("All")
}

data class DeveloperDashboardSummary(
    val apps: List<AppListing>,
    val submissions: List<AppSubmission>,
    val analytics: List<DeveloperAnalytics>,
    val totalPublished: Int,
    val totalPending: Int,
    val totalRejected: Int,
    val totalDownloads: Long,
    val totalViews: Long,
    val averageRating: Double,
    val totalReviews: Long,
    val chartPoints: List<Long>
)

class DeveloperViewModel(
    private val currentUserId: String,
    private val publishRepository: DeveloperPublishRepository,
    private val apkParserService: ApkParserService
) : ViewModel() {

    private val _dateRange = MutableStateFlow(AnalyticsRange.DAYS_30)
    val dateRange: StateFlow<AnalyticsRange> = _dateRange.asStateFlow()

    private val _parsedApk = MutableStateFlow<ParsedApkMetadata?>(null)
    val parsedApk: StateFlow<ParsedApkMetadata?> = _parsedApk.asStateFlow()

    private val _parsingApk = MutableStateFlow(false)
    val parsingApk: StateFlow<Boolean> = _parsingApk.asStateFlow()

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    val uploadProgress: StateFlow<Float?> = publishRepository.uploadProgress

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    val dashboardState: StateFlow<UiState<DeveloperDashboardSummary>> = combine(
        publishRepository.observeDeveloperApps(currentUserId),
        publishRepository.observeDeveloperSubmissions(currentUserId),
        publishRepository.observeDeveloperAnalytics(currentUserId),
        _dateRange
    ) { apps, submissions, analytics, range ->
        val published = apps.count { it.status == "approved" }
        val pending = apps.count { it.status in listOf("pending", "validating", "under_review", "uploading") }
        val rejected = apps.count { it.status == "rejected" }
        val downloads = analytics.sumOf { it.totalDownloads }.coerceAtLeast(apps.sumOf { it.downloadCount })
        val views = analytics.sumOf { it.totalViews }.coerceAtLeast(apps.sumOf { it.viewCount })
        val reviews = analytics.sumOf { it.totalReviews }.coerceAtLeast(apps.sumOf { it.totalReviews })
        val ratedApps = apps.filter { it.totalReviews > 0L }
        val avgRating = if (ratedApps.isNotEmpty()) ratedApps.map { it.averageRating }.average() else 0.0

        val rawSeries = analytics.flatMap { it.dailyDownloads }
        val series = when {
            rawSeries.isEmpty() -> List(if (range == AnalyticsRange.DAYS_7) 7 else 12) { 0L }
            range == AnalyticsRange.DAYS_7 -> rawSeries.takeLast(7)
            range == AnalyticsRange.DAYS_30 -> rawSeries.takeLast(30)
            else -> rawSeries
        }

        UiState.Success(
            DeveloperDashboardSummary(
                apps = apps,
                submissions = submissions,
                analytics = analytics,
                totalPublished = published,
                totalPending = pending,
                totalRejected = rejected,
                totalDownloads = downloads,
                totalViews = views,
                averageRating = avgRating,
                totalReviews = reviews,
                chartPoints = series
            )
        ) as UiState<DeveloperDashboardSummary>
    }.catch { error ->
        val root = error.unwrapFirestoreException()
        if (error is CancellationException && root == error) throw error
        emit(UiState.Error(root.localizedMessage ?: "Failed to load developer workspace"))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    fun setDateRange(range: AnalyticsRange) {
        _dateRange.value = range
    }

    fun inspectApk(uri: Uri) {
        viewModelScope.launch {
            _parsingApk.value = true
            val result = apkParserService.parseAndValidateApk(uri)
            _parsingApk.value = false
            result.onSuccess { _parsedApk.value = it }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Failed to parse APK file" }
        }
    }

    fun clearParsedApk() {
        _parsedApk.value = null
    }

    fun submitNewApplication(draft: PublishSubmissionDraft, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            _submitting.value = true
            val res = publishRepository.submitApplicationForReview(draft)
            _submitting.value = false
            res.onSuccess { appId ->
                _parsedApk.value = null
                _actionMessage.value = "Submitted for Admin Review!"
                onSuccess(appId)
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Submission failed"
            }
        }
    }

    fun updateAppMetadata(
        appId: String,
        appName: String,
        shortDescription: String,
        fullDescription: String,
        whatsNew: String,
        websiteUrl: String,
        privacyPolicyUrl: String,
        resubmitForReview: Boolean
    ) {
        viewModelScope.launch {
            publishRepository.updateAppMetadata(
                appId, appName, shortDescription, fullDescription, whatsNew, websiteUrl, privacyPolicyUrl, resubmitForReview
            ).onSuccess {
                _actionMessage.value = if (resubmitForReview) "Resubmitted for review" else "Metadata updated"
            }.onFailure {
                _actionMessage.value = it.localizedMessage ?: "Update failed"
            }
        }
    }

    fun uploadNewVersion(app: AppListing, apkUrl: String, whatsNew: String) {
        val parsed = _parsedApk.value ?: run {
            _actionMessage.value = "Select and validate a new APK file first"
            return
        }
        viewModelScope.launch {
            _submitting.value = true
            val res = publishRepository.submitNewApkVersion(app, parsed, apkUrl, whatsNew)
            _submitting.value = false
            res.onSuccess {
                _parsedApk.value = null
                _actionMessage.value = "Update v${parsed.versionName} submitted for review"
            }.onFailure {
                _actionMessage.value = it.localizedMessage ?: "Update submission failed"
            }
        }
    }

    fun archiveApp(appId: String) {
        viewModelScope.launch {
            publishRepository.archiveApp(appId)
                .onSuccess { _actionMessage.value = "Application archived" }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Failed to archive" }
        }
    }

    fun clearMessage() {
        _actionMessage.value = null
    }
}
