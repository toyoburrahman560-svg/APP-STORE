package com.example.ui.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.download.ActiveDownloadState
import com.example.data.download.ApkDownloadService
import com.example.data.download.InstalledPackageStatus
import com.example.data.model.AppListing
import com.example.data.model.AppVersion
import com.example.data.model.DownloadPhase
import com.example.data.model.DownloadRecord
import com.example.data.repository.MarketplaceRepository
import com.example.data.repository.UserInteractionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class DownloadsViewModel(
    private val downloadService: ApkDownloadService,
    private val marketplaceRepository: MarketplaceRepository,
    private val interactionRepository: UserInteractionRepository
) : ViewModel() {

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    val downloadsMap: StateFlow<Map<String, ActiveDownloadState>> = downloadService.downloads

    val activeDownloadCount: StateFlow<Int> = downloadService.downloads
        .map { map ->
            map.values.count {
                it.phase == DownloadPhase.QUEUED ||
                    it.phase == DownloadPhase.DOWNLOADING ||
                    it.phase == DownloadPhase.VERIFYING
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val sortedDownloads: StateFlow<List<ActiveDownloadState>> = downloadService.downloads
        .map { map ->
            map.values.sortedWith(
                compareBy<ActiveDownloadState> { state ->
                    when (state.phase) {
                        DownloadPhase.DOWNLOADING -> 0
                        DownloadPhase.QUEUED -> 1
                        DownloadPhase.VERIFYING -> 2
                        DownloadPhase.PAUSED -> 3
                        DownloadPhase.READY_TO_INSTALL -> 4
                        DownloadPhase.FAILED -> 5
                        DownloadPhase.INSTALLED -> 6
                        DownloadPhase.CANCELLED -> 7
                        DownloadPhase.IDLE -> 8
                    }
                }.thenByDescending { it.updatedAtMillis }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val lastSyncedPhases = ConcurrentHashMap<String, Pair<DownloadPhase, Long>>()

    init {
        viewModelScope.launch {
            downloadService.syncInstalledStatesFromDevice()
        }
        viewModelScope.launch {
            combine(
                downloadService.downloads,
                marketplaceRepository.observeApprovedApps()
            ) { downloads, approvedApps ->
                downloads to approvedApps
            }.collectLatest { (downloads, approvedApps) ->
                val appLookup = approvedApps.associateBy { it.appId }
                downloads.forEach { (appId, state) ->
                    if (state.phase == DownloadPhase.IDLE) return@forEach
                    val phaseKey = state.phase to state.versionCode
                    val prevKey = lastSyncedPhases[appId]
                    if (prevKey == phaseKey) return@forEach
                    lastSyncedPhases[appId] = phaseKey

                    val listing = appLookup[appId] ?: AppListing(
                        appId = state.appId,
                        appName = state.appName,
                        packageName = state.packageName,
                        iconUrl = state.iconUrl,
                        versionName = state.versionName,
                        versionCode = state.versionCode,
                        apkSizeBytes = state.totalBytes,
                        status = "approved"
                    )
                    val statusStr = when (state.phase) {
                        DownloadPhase.QUEUED -> "queued"
                        DownloadPhase.DOWNLOADING, DownloadPhase.VERIFYING -> "downloading"
                        DownloadPhase.PAUSED -> "paused"
                        DownloadPhase.READY_TO_INSTALL -> "verified"
                        DownloadPhase.INSTALLED -> "installed"
                        DownloadPhase.FAILED -> "failed"
                        DownloadPhase.CANCELLED -> "cancelled"
                        DownloadPhase.IDLE -> return@forEach
                    }
                    interactionRepository.recordDownloadEvent(
                        app = listing,
                        versionId = state.versionId.ifBlank { "${appId}_v${state.versionCode}" },
                        versionName = state.versionName,
                        versionCode = state.versionCode,
                        sha256 = state.expectedSha256,
                        status = statusStr,
                        downloadedBytes = state.bytesDownloaded,
                        fileSizeBytes = state.totalBytes
                    )
                }
            }
        }
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun refreshInstalledStates() {
        downloadService.syncInstalledStatesFromDevice()
    }

    fun getInstalledStatus(packageName: String): InstalledPackageStatus {
        return downloadService.getInstalledStatus(packageName)
    }

    fun getLocalApk(packageName: String, versionCode: Long): File? {
        return downloadService.getLocalDownloadedApk(packageName, versionCode)
    }

    fun canInstallPackages(): Boolean = downloadService.canRequestPackageInstalls()

    fun installSettingsIntent(): Intent = downloadService.createInstallUnknownAppsSettingsIntent()

    fun pause(appId: String) {
        downloadService.pauseDownload(appId)
    }

    fun resume(appId: String) {
        downloadService.resumeDownload(appId)
    }

    fun cancel(appId: String) {
        downloadService.cancelDownload(appId)
    }

    fun retry(appId: String) {
        downloadService.retryDownload(appId)
    }

    fun removeEntry(appId: String, deleteApkFile: Boolean = true, recordId: String? = null) {
        downloadService.removeDownload(appId, deleteApkFile)
        lastSyncedPhases.remove(appId)
        if (!recordId.isNullOrBlank()) {
            viewModelScope.launch {
                interactionRepository.deleteDownloadRecord(recordId)
            }
        }
    }

    fun deleteHistoryRecordOnly(record: DownloadRecord) {
        downloadService.deleteDownloadedApk(record.packageName, record.versionCode)
        viewModelScope.launch {
            interactionRepository.deleteDownloadRecord(record.id)
                .onFailure { _feedbackMessage.value = it.localizedMessage ?: "Failed to remove record" }
        }
    }

    fun installFromState(state: ActiveDownloadState) {
        val file = state.localFilePath?.let { File(it) }?.takeIf { it.exists() }
            ?: downloadService.getLocalDownloadedApk(state.packageName, state.versionCode)
        if (file == null) {
            _feedbackMessage.value = "APK file no longer exists on device. Tap Retry to re-download."
            return
        }
        downloadService.launchPackageInstaller(file)
            .onSuccess {
                viewModelScope.launch {
                    val listing = AppListing(
                        appId = state.appId,
                        appName = state.appName,
                        packageName = state.packageName,
                        iconUrl = state.iconUrl,
                        versionName = state.versionName,
                        versionCode = state.versionCode,
                        apkSizeBytes = file.length(),
                        status = "approved"
                    )
                    interactionRepository.recordDownloadEvent(
                        app = listing,
                        versionId = state.versionId.ifBlank { "${state.appId}_v${state.versionCode}" },
                        versionName = state.versionName,
                        versionCode = state.versionCode,
                        sha256 = state.expectedSha256,
                        status = "installed",
                        downloadedBytes = file.length(),
                        fileSizeBytes = file.length()
                    )
                    downloadService.syncInstalledStatesFromDevice()
                }
            }
            .onFailure {
                _feedbackMessage.value = it.localizedMessage ?: "Failed to open Android Package Installer"
            }
    }

    fun installFromRecord(record: DownloadRecord) {
        val file = downloadService.getLocalDownloadedApk(record.packageName, record.versionCode)
        if (file == null) {
            _feedbackMessage.value = "Local APK missing. Re-downloading verified build…"
            redownloadApp(record.appId)
            return
        }
        downloadService.launchPackageInstaller(file)
            .onSuccess {
                viewModelScope.launch {
                    val listing = AppListing(
                        appId = record.appId,
                        appName = record.appName,
                        packageName = record.packageName,
                        iconUrl = record.iconUrl,
                        versionName = record.versionName,
                        versionCode = record.versionCode,
                        apkSizeBytes = file.length(),
                        status = "approved"
                    )
                    interactionRepository.recordDownloadEvent(
                        app = listing,
                        versionId = record.versionId.ifBlank { "${record.appId}_v${record.versionCode}" },
                        versionName = record.versionName,
                        versionCode = record.versionCode,
                        sha256 = record.sha256,
                        status = "installed",
                        downloadedBytes = file.length(),
                        fileSizeBytes = file.length()
                    )
                    downloadService.syncInstalledStatesFromDevice()
                }
            }
            .onFailure {
                _feedbackMessage.value = it.localizedMessage ?: "Failed to open Android Package Installer"
            }
    }

    fun redownloadApp(appId: String) {
        viewModelScope.launch {
            val app = marketplaceRepository.observeAppById(appId).firstOrNull()
            if (app == null || !app.status.equals("approved", ignoreCase = true)) {
                _feedbackMessage.value = "This application is currently unavailable for download."
                return@launch
            }
            val latestVersion: AppVersion? = marketplaceRepository.observeApprovedVersions(appId)
                .firstOrNull()
                ?.firstOrNull()
            downloadService.startDownload(app, latestVersion)
        }
    }
}
