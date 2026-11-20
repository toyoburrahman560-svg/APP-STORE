package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.apk.ApkParserService
import com.example.data.model.AppListing
import com.example.data.model.AppSubmission
import com.example.data.model.AppVersion
import com.example.data.model.ParsedApkMetadata
import com.example.data.model.UiState
import com.example.data.repository.AdminDashboardSnapshot
import com.example.data.repository.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AdminSection(val label: String) {
    OVERVIEW("Overview"),
    REVIEW_CENTER("App Review"),
    CATEGORIES("Categories"),
    USERS("Users & Devs"),
    MODERATION("Reports"),
    SETTINGS("Broadcast")
}

enum class ReviewFilter(val label: String) {
    ALL("All"),
    PENDING("Pending"),
    APPROVED("Published"),
    REJECTED("Rejected")
}

class AdminViewModel(
    private val adminRepository: AdminRepository,
    private val apkParserService: ApkParserService? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<AdminDashboardSnapshot>>(UiState.Loading)
    val uiState: StateFlow<UiState<AdminDashboardSnapshot>> = _uiState.asStateFlow()

    private val _selectedSection = MutableStateFlow(AdminSection.OVERVIEW)
    val selectedSection: StateFlow<AdminSection> = _selectedSection.asStateFlow()

    private val _reviewFilter = MutableStateFlow(ReviewFilter.ALL)
    val reviewFilter: StateFlow<ReviewFilter> = _reviewFilter.asStateFlow()

    private val _resolvedLinks = MutableStateFlow<Map<String, String>>(emptyMap())
    val resolvedLinks: StateFlow<Map<String, String>> = _resolvedLinks.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    private val _parsedApk = MutableStateFlow<ParsedApkMetadata?>(null)
    val parsedApk: StateFlow<ParsedApkMetadata?> = _parsedApk.asStateFlow()

    private val _parsingApk = MutableStateFlow(false)
    val parsingApk: StateFlow<Boolean> = _parsingApk.asStateFlow()

    init {
        refresh()
    }

    fun selectSection(section: AdminSection) {
        _selectedSection.value = section
    }

    fun selectReviewFilter(filter: ReviewFilter) {
        _reviewFilter.value = filter
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    fun inspectApkForAdmin(uri: Uri) {
        val parser = apkParserService ?: return
        viewModelScope.launch {
            _parsingApk.value = true
            parser.parseAndValidateApk(uri)
                .onSuccess { _parsedApk.value = it }
                .onFailure { _feedback.value = it.localizedMessage ?: "Failed to parse APK" }
            _parsingApk.value = false
        }
    }

    fun clearParsedApk() {
        _parsedApk.value = null
    }

    fun refresh() {
        viewModelScope.launch {
            if (_uiState.value !is UiState.Success) {
                _uiState.value = UiState.Loading
            }
            adminRepository.loadDashboardSnapshot()
                .onSuccess { snapshot ->
                    _uiState.value = UiState.Success(snapshot)
                    val initialLinks = buildMap {
                        snapshot.versions.forEach { ver ->
                            val url = ver.apkDownloadUrl.trim()
                            if (url.startsWith("https://") || url.startsWith("http://")) {
                                val key = ver.versionId.ifBlank { ver.id }
                                if (key.isNotBlank()) put(key, url)
                            }
                        }
                    }
                    _resolvedLinks.value = _resolvedLinks.value + initialLinks
                }
                .onFailure { err ->
                    _uiState.value = UiState.Error(err.localizedMessage ?: "Failed to load admin data")
                }
        }
    }

    fun approveApp(app: AppListing, version: AppVersion?, submission: AppSubmission?) {
        runAction("Application approved & published!") {
            adminRepository.approveApp(app, version, submission)
        }
    }

    fun rejectApp(app: AppListing, version: AppVersion?, submission: AppSubmission?, reason: String) {
        runAction("Application rejected.") {
            adminRepository.rejectApp(app, version, submission, reason)
        }
    }

    fun setAppStatus(appId: String, status: String) {
        runAction("App status updated to $status") {
            adminRepository.setAppStatus(appId, status)
        }
    }

    fun toggleFeatured(app: AppListing) {
        val appId = app.appId.ifBlank { app.id }
        runAction(if (!app.featured) "Marked as Featured" else "Removed from Featured") {
            adminRepository.toggleAppFlag(appId, "featured", !app.featured)
        }
    }

    fun toggleEditorsChoice(app: AppListing) {
        val appId = app.appId.ifBlank { app.id }
        runAction(if (!app.editorsChoice) "Marked Editor's Choice" else "Removed Editor's Choice") {
            adminRepository.toggleAppFlag(appId, "editorsChoice", !app.editorsChoice)
        }
    }

    fun generateApkLink(app: AppListing, version: AppVersion) {
        viewModelScope.launch {
            _busy.value = true
            adminRepository.resolveAndPersistApkLink(app, version)
                .onSuccess { url ->
                    val key = version.versionId.ifBlank { version.id }
                    _resolvedLinks.value = _resolvedLinks.value + (key to url)
                    _feedback.value = "APK download link ready!"
                }
                .onFailure { err ->
                    _feedback.value = err.localizedMessage ?: "Could not generate APK link"
                }
            _busy.value = false
        }
    }

    fun setVersionApkUrl(app: AppListing, version: AppVersion?, url: String) {
        viewModelScope.launch {
            _busy.value = true
            adminRepository.setVersionApkUrl(app, version, url)
                .onSuccess { savedUrl ->
                    val key = version?.let { it.versionId.ifBlank { it.id } } ?: "${app.appId}_v${app.versionCode}"
                    _resolvedLinks.value = _resolvedLinks.value + (key to savedUrl)
                    _feedback.value = "APK download URL updated for ${app.appName}!"
                    adminRepository.loadDashboardSnapshot().onSuccess { _uiState.value = UiState.Success(it) }
                }
                .onFailure { err ->
                    _feedback.value = err.localizedMessage ?: "Failed to save APK URL"
                }
            _busy.value = false
        }
    }

    fun uploadApkForApp(app: AppListing, version: AppVersion?) {
        val parsed = _parsedApk.value ?: run {
            _feedback.value = "Select a valid .apk file first."
            return
        }
        viewModelScope.launch {
            _busy.value = true
            adminRepository.uploadAndAttachApkForVersion(app, version, parsed)
                .onSuccess { savedUrl ->
                    _parsedApk.value = null
                    val key = version?.let { it.versionId.ifBlank { it.id } } ?: "${app.appId}_v${parsed.versionCode}"
                    _resolvedLinks.value = _resolvedLinks.value + (key to savedUrl)
                    _feedback.value = "APK uploaded & attached to ${app.appName}!"
                    adminRepository.loadDashboardSnapshot().onSuccess { _uiState.value = UiState.Success(it) }
                }
                .onFailure { err ->
                    _feedback.value = err.localizedMessage ?: "Failed to upload APK"
                }
            _busy.value = false
        }
    }

    fun seedDefaultCategories() {
        runAction("Seeded all 24 default categories!") {
            adminRepository.seedDefaultCategories().map { Unit }
        }
    }

    fun addCategory(name: String, nameBn: String, group: String, sortOrder: Long) {
        runAction("Category added!") {
            adminRepository.addCategory(name, nameBn, group, sortOrder)
        }
    }

    fun toggleCategory(categoryId: String, enabled: Boolean) {
        runAction(if (enabled) "Category enabled" else "Category disabled") {
            adminRepository.toggleCategory(categoryId, enabled)
        }
    }

    fun deleteCategory(categoryId: String) {
        runAction("Category deleted") {
            adminRepository.deleteCategory(categoryId)
        }
    }

    fun setUserStatus(uid: String, status: String) {
        runAction("User status set to $status") {
            adminRepository.setUserStatus(uid, status)
        }
    }

    fun setDeveloperStatus(uid: String, status: String) {
        runAction("Developer marked $status") {
            adminRepository.setDeveloperStatus(uid, status)
        }
    }

    fun resolveReport(reportId: String, status: String) {
        runAction("Report marked $status") {
            adminRepository.resolveReport(reportId, status)
        }
    }

    fun removeReview(reviewId: String) {
        runAction("Review removed") {
            adminRepository.removeReview(reviewId)
        }
    }

    fun sendAnnouncement(title: String, body: String, targetUid: String) {
        runAction("Announcement published!") {
            adminRepository.sendAnnouncement(title, body, targetUid)
        }
    }

    fun saveSettings(platformName: String, supportEmail: String, maxUploadMb: Long, maintenance: Boolean) {
        runAction("Platform settings saved!") {
            adminRepository.savePlatformSettings(platformName, supportEmail, maxUploadMb, maintenance)
        }
    }

    private fun runAction(successMessage: String, block: suspend () -> Result<Any?>) {
        viewModelScope.launch {
            _busy.value = true
            block()
                .onSuccess {
                    _feedback.value = successMessage
                    adminRepository.loadDashboardSnapshot()
                        .onSuccess { _uiState.value = UiState.Success(it) }
                }
                .onFailure { err ->
                    _feedback.value = err.localizedMessage ?: "Action failed"
                }
            _busy.value = false
        }
    }
}
