package com.example.data.download

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.model.AppListing
import com.example.data.model.AppVersion
import com.example.data.model.DownloadPhase
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

data class ActiveDownloadState(
    val appId: String,
    val versionId: String,
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val iconUrl: String = "",
    val downloadUrl: String = "",
    val storagePath: String = "",
    val expectedSha256: String = "",
    val expectedSigningFingerprint: String = "",
    val phase: DownloadPhase = DownloadPhase.IDLE,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val progressPercent: Int = 0,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long? = null,
    val localFilePath: String? = null,
    val errorMessage: String? = null,
    val startedAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val speedKbps: Long get() = speedBytesPerSec / 1024L
}

data class InstalledPackageStatus(
    val isInstalled: Boolean,
    val installedVersionCode: Long = 0L,
    val installedVersionName: String = ""
)

class ApkDownloadService(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val stateFile = File(context.filesDir, "app_downloads_state.json")
    private val _downloads = MutableStateFlow<Map<String, ActiveDownloadState>>(emptyMap())
    val downloads: StateFlow<Map<String, ActiveDownloadState>> = _downloads.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val stopIntentReason = ConcurrentHashMap<String, DownloadPhase>()
    private val statusCallbacks = ConcurrentHashMap<String, (ActiveDownloadState) -> Unit>()

    init {
        ApkDownloadForegroundService.ensureChannel(context)
        restorePersistedDownloads()
    }

    fun getInstalledStatus(packageName: String): InstalledPackageStatus {
        if (packageName.isBlank()) return InstalledPackageStatus(false)
        return try {
            val info: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
            else @Suppress("DEPRECATION") info.versionCode.toLong()
            InstalledPackageStatus(
                isInstalled = true,
                installedVersionCode = code,
                installedVersionName = info.versionName.orEmpty()
            )
        } catch (_: Exception) {
            InstalledPackageStatus(false)
        }
    }

    fun getDownloadsDirectory(): File {
        return context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "apks").apply { mkdirs() }
    }

    fun getLocalDownloadedApk(packageName: String, versionCode: Long): File? {
        if (packageName.isBlank()) return null
        val target = File(getDownloadsDirectory(), "${packageName}_v${versionCode}.apk")
        return if (target.exists() && target.length() > 0L) target else null
    }

    fun isDownloadActiveFor(appId: String, versionCode: Long): Boolean {
        val current = _downloads.value[appId] ?: return false
        return current.versionCode == versionCode && current.phase in setOf(
            DownloadPhase.QUEUED,
            DownloadPhase.DOWNLOADING,
            DownloadPhase.VERIFYING
        )
    }

    fun startDownload(
        app: AppListing,
        version: AppVersion?,
        onStateChanged: (ActiveDownloadState) -> Unit = {}
    ) {
        val versionId = version?.versionId ?: "${app.appId}_v${app.versionCode}"
        val versionName = version?.versionName ?: app.versionName
        val versionCode = version?.versionCode ?: app.versionCode
        val downloadUrl = version?.apkDownloadUrl.orEmpty()
        val storagePath = version?.apkStoragePath.orEmpty()
        val expectedSha256 = version?.sha256.orEmpty()
        val expectedSigner = version?.signingCertificateFingerprint.orEmpty()
        val expectedSize = (version?.apkSize ?: app.apkSizeBytes).coerceAtLeast(0L)

        // Security gate: strictly reject unapproved apps/versions
        if (!app.status.equals("approved", ignoreCase = true) ||
            (version != null && !version.approvalStatus.equals("approved", ignoreCase = true))
        ) {
            val failed = ActiveDownloadState(
                appId = app.appId,
                versionId = versionId,
                appName = app.appName,
                packageName = app.packageName,
                versionName = versionName,
                versionCode = versionCode,
                iconUrl = app.iconUrl,
                downloadUrl = downloadUrl,
                storagePath = storagePath,
                expectedSha256 = expectedSha256,
                expectedSigningFingerprint = expectedSigner,
                phase = DownloadPhase.FAILED,
                totalBytes = expectedSize,
                errorMessage = "Only admin-approved applications can be downloaded."
            )
            updateState(app.appId) { failed }
            onStateChanged(failed)
            return
        }

        // Prevent duplicate active downloads for the same app version
        if (isDownloadActiveFor(app.appId, versionCode)) {
            _downloads.value[app.appId]?.let(onStateChanged)
            return
        }

        statusCallbacks[app.appId] = onStateChanged

        // If already downloaded locally and verified, surface READY_TO_INSTALL immediately
        val existingApk = getLocalDownloadedApk(app.packageName, versionCode)
        if (existingApk != null) {
            scope.launch {
                val verificationError = verifyApkFile(
                    file = existingApk,
                    expectedPackageName = app.packageName,
                    expectedVersionCode = versionCode,
                    expectedSha256 = expectedSha256,
                    expectedSigningFingerprint = expectedSigner
                )
                if (verificationError == null) {
                    val readyState = ActiveDownloadState(
                        appId = app.appId,
                        versionId = versionId,
                        appName = app.appName,
                        packageName = app.packageName,
                        versionName = versionName,
                        versionCode = versionCode,
                        iconUrl = app.iconUrl,
                        downloadUrl = downloadUrl,
                        storagePath = storagePath,
                        expectedSha256 = expectedSha256,
                        expectedSigningFingerprint = expectedSigner,
                        phase = DownloadPhase.READY_TO_INSTALL,
                        bytesDownloaded = existingApk.length(),
                        totalBytes = existingApk.length(),
                        progressPercent = 100,
                        localFilePath = existingApk.absolutePath
                    )
                    updateState(app.appId) { readyState }
                    notifyStateCallback(readyState)
                    return@launch
                } else {
                    existingApk.delete()
                }
                launchDownloadJob(
                    appId = app.appId,
                    versionId = versionId,
                    appName = app.appName,
                    packageName = app.packageName,
                    versionName = versionName,
                    versionCode = versionCode,
                    iconUrl = app.iconUrl,
                    rawDownloadUrl = downloadUrl,
                    storagePath = storagePath,
                    expectedSha256 = expectedSha256,
                    expectedSigner = expectedSigner,
                    expectedSizeBytes = expectedSize
                )
            }
            return
        }

        launchDownloadJob(
            appId = app.appId,
            versionId = versionId,
            appName = app.appName,
            packageName = app.packageName,
            versionName = versionName,
            versionCode = versionCode,
            iconUrl = app.iconUrl,
            rawDownloadUrl = downloadUrl,
            storagePath = storagePath,
            expectedSha256 = expectedSha256,
            expectedSigner = expectedSigner,
            expectedSizeBytes = expectedSize
        )
    }

    fun pauseDownload(appId: String) {
        val current = _downloads.value[appId] ?: return
        if (current.phase != DownloadPhase.DOWNLOADING && current.phase != DownloadPhase.QUEUED) return
        stopIntentReason[appId] = DownloadPhase.PAUSED
        activeJobs[appId]?.cancel()
        activeJobs.remove(appId)
        val partFile = File(getDownloadsDirectory(), "${current.packageName}_v${current.versionCode}.apk.part")
        val bytes = if (partFile.exists()) partFile.length() else current.bytesDownloaded
        val pct = if (current.totalBytes > 0L) ((bytes * 100L) / current.totalBytes).toInt().coerceIn(0, 99) else current.progressPercent
        val pausedState = updateAndReturn(appId) {
            it.copy(
                phase = DownloadPhase.PAUSED,
                bytesDownloaded = bytes,
                progressPercent = pct,
                speedBytesPerSec = 0L,
                etaSeconds = null,
                errorMessage = null
            )
        }
        notifyStateCallback(pausedState)
        syncForegroundService()
    }

    fun resumeDownload(appId: String, onStateChanged: ((ActiveDownloadState) -> Unit)? = null) {
        val current = _downloads.value[appId] ?: return
        if (isDownloadActiveFor(appId, current.versionCode)) return
        if (onStateChanged != null) {
            statusCallbacks[appId] = onStateChanged
        }
        launchDownloadJob(
            appId = current.appId,
            versionId = current.versionId,
            appName = current.appName,
            packageName = current.packageName,
            versionName = current.versionName,
            versionCode = current.versionCode,
            iconUrl = current.iconUrl,
            rawDownloadUrl = current.downloadUrl,
            storagePath = current.storagePath,
            expectedSha256 = current.expectedSha256,
            expectedSigner = current.expectedSigningFingerprint,
            expectedSizeBytes = current.totalBytes
        )
    }

    fun retryDownload(appId: String, onStateChanged: ((ActiveDownloadState) -> Unit)? = null) {
        resumeDownload(appId, onStateChanged)
    }

    fun cancelDownload(appId: String) {
        val current = _downloads.value[appId]
        stopIntentReason[appId] = DownloadPhase.CANCELLED
        activeJobs[appId]?.cancel()
        activeJobs.remove(appId)
        if (current != null) {
            val partFile = File(getDownloadsDirectory(), "${current.packageName}_v${current.versionCode}.apk.part")
            if (partFile.exists()) partFile.delete()
        }
        val cancelledState = updateAndReturn(appId) {
            it.copy(
                phase = DownloadPhase.CANCELLED,
                bytesDownloaded = 0L,
                progressPercent = 0,
                speedBytesPerSec = 0L,
                etaSeconds = null,
                errorMessage = null
            )
        }
        notifyStateCallback(cancelledState)
        syncForegroundService()
    }

    fun removeDownload(appId: String, deleteApkFile: Boolean = false) {
        val current = _downloads.value[appId]
        if (current != null && (current.phase == DownloadPhase.DOWNLOADING || current.phase == DownloadPhase.QUEUED)) {
            cancelDownload(appId)
        }
        if (current != null) {
            File(getDownloadsDirectory(), "${current.packageName}_v${current.versionCode}.apk.part").delete()
            if (deleteApkFile) {
                File(getDownloadsDirectory(), "${current.packageName}_v${current.versionCode}.apk").delete()
            }
        }
        _downloads.update { it - appId }
        persistDownloads()
        syncForegroundService()
    }

    fun syncInstalledStatesFromDevice() {
        _downloads.update { currentMap ->
            var changed = false
            val updated = currentMap.mapValues { (_, state) ->
                if (state.phase == DownloadPhase.READY_TO_INSTALL || state.phase == DownloadPhase.INSTALLED) {
                    val installed = getInstalledStatus(state.packageName)
                    if (installed.isInstalled && installed.installedVersionCode >= state.versionCode && state.phase != DownloadPhase.INSTALLED) {
                        changed = true
                        state.copy(phase = DownloadPhase.INSTALLED, updatedAtMillis = System.currentTimeMillis())
                    } else state
                } else state
            }
            if (changed) updated else currentMap
        }
        persistDownloads()
    }

    private fun launchDownloadJob(
        appId: String,
        versionId: String,
        appName: String,
        packageName: String,
        versionName: String,
        versionCode: Long,
        iconUrl: String,
        rawDownloadUrl: String,
        storagePath: String,
        expectedSha256: String,
        expectedSigner: String,
        expectedSizeBytes: Long
    ) {
        stopIntentReason.remove(appId)
        activeJobs[appId]?.cancel()

        val initialState = ActiveDownloadState(
            appId = appId,
            versionId = versionId,
            appName = appName,
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            iconUrl = iconUrl,
            downloadUrl = rawDownloadUrl,
            storagePath = storagePath,
            expectedSha256 = expectedSha256,
            expectedSigningFingerprint = expectedSigner,
            phase = DownloadPhase.QUEUED,
            totalBytes = expectedSizeBytes,
            errorMessage = null,
            startedAtMillis = _downloads.value[appId]?.startedAtMillis ?: System.currentTimeMillis()
        )
        updateState(appId) { initialState }
        notifyStateCallback(initialState)
        syncForegroundService()

        val job = scope.launch {
            val dir = getDownloadsDirectory()
            val destFile = File(dir, "${packageName}_v${versionCode}.apk")
            val partFile = File(dir, "${packageName}_v${versionCode}.apk.part")

            try {
                if (!hasNetworkConnection()) {
                    throw IllegalStateException("No internet connection. Connect to Wi-Fi or mobile data and tap Retry.")
                }

                // Resolve secure Firebase Storage download URL if needed
                val resolvedUrl = resolveApkDownloadUrl(rawDownloadUrl, storagePath)
                if (resolvedUrl.isBlank() || (!resolvedUrl.startsWith("https://") && !resolvedUrl.startsWith("http://"))) {
                    throw IllegalStateException("Secure APK download location is not configured yet for this release.")
                }

                // Check available disk space before writing
                val existingBytes = if (partFile.exists()) partFile.length() else 0L
                val remainingNeeded = (expectedSizeBytes - existingBytes).coerceAtLeast(0L)
                val availableBytes = getAvailableStorageBytes(dir)
                if (expectedSizeBytes > 0L && availableBytes < remainingNeeded + 5L * 1024L * 1024L) {
                    throw IllegalStateException(
                        "Insufficient storage space. Need ${formatSizeMb(remainingNeeded)} free, only ${formatSizeMb(availableBytes)} available."
                    )
                }

                val reqBuilder = Request.Builder().url(resolvedUrl)
                if (existingBytes > 0L) {
                    reqBuilder.header("Range", "bytes=$existingBytes-")
                }
                if (resolvedUrl.contains("firebasestorage.googleapis.com")) {
                    val token = runCatching {
                        Firebase.auth.currentUser?.getIdToken(false)?.await()?.token
                    }.getOrNull()
                    if (!token.isNullOrBlank()) {
                        reqBuilder.header("Authorization", "Firebase $token")
                    }
                }

                httpClient.newCall(reqBuilder.build()).execute().use { response ->
                    if (response.code == 416 && existingBytes > 0L) {
                        // Server indicates local .part already reached end of file; proceed to verify
                    } else if (!response.isSuccessful && response.code != 206) {
                        val msg = when (response.code) {
                            401, 403 -> "Access denied by Firebase Storage. Please sign in with Google and try again."
                            404 -> "APK file was not found in Firebase Storage."
                            else -> "Download server returned HTTP ${response.code}."
                        }
                        throw IllegalStateException(msg)
                    } else {
                        val body = response.body ?: throw IllegalStateException("Empty download response from server.")
                        val append = response.code == 206 && existingBytes > 0L
                        val contentLength = body.contentLength()
                        val totalBytes = when {
                            contentLength > 0L && append -> existingBytes + contentLength
                            contentLength > 0L -> contentLength
                            else -> expectedSizeBytes
                        }

                        if (totalBytes > 0L && availableBytes < (totalBytes - if (append) existingBytes else 0L) + 5L * 1024L * 1024L) {
                            throw IllegalStateException("Insufficient storage space to complete this download.")
                        }

                        var downloaded = if (append) existingBytes else 0L
                        var lastSampleTime = System.currentTimeMillis()
                        var lastSampleBytes = downloaded
                        var emaSpeedBytesPerSec = 0.0
                        var lastUiEmitTime = 0L
                        var lastServiceUpdateTime = 0L

                        val downloadingStart = updateAndReturn(appId) {
                            it.copy(
                                downloadUrl = resolvedUrl,
                                phase = DownloadPhase.DOWNLOADING,
                                bytesDownloaded = downloaded,
                                totalBytes = totalBytes,
                                progressPercent = if (totalBytes > 0L) ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100) else 0
                            )
                        }
                        notifyStateCallback(downloadingStart)
                        syncForegroundService()

                        body.byteStream().use { input ->
                            FileOutputStream(partFile, append).use { output ->
                                val buffer = ByteArray(32 * 1024)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read <= 0) break
                                    output.write(buffer, 0, read)
                                    downloaded += read

                                    val now = System.currentTimeMillis()
                                    val deltaMs = now - lastSampleTime
                                    if (deltaMs >= 400L) {
                                        val deltaBytes = downloaded - lastSampleBytes
                                        val instantSpeed = (deltaBytes * 1000.0) / deltaMs.coerceAtLeast(1L)
                                        emaSpeedBytesPerSec = if (emaSpeedBytesPerSec == 0.0) instantSpeed
                                        else (0.35 * instantSpeed) + (0.65 * emaSpeedBytesPerSec)
                                        lastSampleTime = now
                                        lastSampleBytes = downloaded
                                    }

                                    if (now - lastUiEmitTime >= 220L || (totalBytes > 0L && downloaded >= totalBytes)) {
                                        lastUiEmitTime = now
                                        val speedLong = emaSpeedBytesPerSec.toLong().coerceAtLeast(0L)
                                        val eta = if (totalBytes > downloaded && speedLong > 0L) {
                                            ((totalBytes - downloaded) / speedLong).coerceAtLeast(1L)
                                        } else null
                                        val pct = if (totalBytes > 0L) {
                                            ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                        } else 0

                                        updateState(appId, persist = false) {
                                            it.copy(
                                                phase = DownloadPhase.DOWNLOADING,
                                                bytesDownloaded = downloaded,
                                                totalBytes = totalBytes,
                                                progressPercent = pct,
                                                speedBytesPerSec = speedLong,
                                                etaSeconds = eta
                                            )
                                        }
                                    }

                                    if (now - lastServiceUpdateTime >= 850L) {
                                        lastServiceUpdateTime = now
                                        syncForegroundService()
                                    }
                                }
                                output.flush()
                            }
                        }
                    }
                }

                // Transition to VERIFYING integrity
                val verifyingState = updateAndReturn(appId) {
                    it.copy(
                        phase = DownloadPhase.VERIFYING,
                        progressPercent = 100,
                        speedBytesPerSec = 0L,
                        etaSeconds = null
                    )
                }
                notifyStateCallback(verifyingState)
                syncForegroundService()

                val verifyError = verifyApkFile(
                    file = partFile,
                    expectedPackageName = packageName,
                    expectedVersionCode = versionCode,
                    expectedSha256 = expectedSha256,
                    expectedSigningFingerprint = expectedSigner
                )
                if (verifyError != null) {
                    partFile.delete()
                    throw SecurityException(verifyError)
                }

                if (destFile.exists()) destFile.delete()
                if (!partFile.renameTo(destFile)) {
                    partFile.copyTo(destFile, overwrite = true)
                    partFile.delete()
                }

                val readyState = updateAndReturn(appId) {
                    it.copy(
                        phase = DownloadPhase.READY_TO_INSTALL,
                        bytesDownloaded = destFile.length(),
                        totalBytes = destFile.length(),
                        progressPercent = 100,
                        speedBytesPerSec = 0L,
                        etaSeconds = null,
                        localFilePath = destFile.absolutePath,
                        errorMessage = null
                    )
                }
                notifyStateCallback(readyState)
                postCompleteNotification(appName)
            } catch (e: CancellationException) {
                val explicitReason = stopIntentReason.remove(appId)
                if (explicitReason == DownloadPhase.PAUSED) {
                    val pausedBytes = if (partFile.exists()) partFile.length() else (_downloads.value[appId]?.bytesDownloaded ?: 0L)
                    val paused = updateAndReturn(appId) {
                        it.copy(phase = DownloadPhase.PAUSED, bytesDownloaded = pausedBytes, speedBytesPerSec = 0L, etaSeconds = null)
                    }
                    notifyStateCallback(paused)
                } else if (explicitReason == DownloadPhase.CANCELLED) {
                    if (partFile.exists()) partFile.delete()
                    val cancelled = updateAndReturn(appId) {
                        it.copy(phase = DownloadPhase.CANCELLED, bytesDownloaded = 0L, progressPercent = 0, speedBytesPerSec = 0L, etaSeconds = null)
                    }
                    notifyStateCallback(cancelled)
                }
                throw e
            } catch (e: Exception) {
                val friendlyMessage = mapDownloadException(e)
                val failed = updateAndReturn(appId) {
                    it.copy(
                        phase = DownloadPhase.FAILED,
                        speedBytesPerSec = 0L,
                        etaSeconds = null,
                        errorMessage = friendlyMessage
                    )
                }
                notifyStateCallback(failed)
                postFailedNotification(appName, friendlyMessage)
            } finally {
                activeJobs.remove(appId)
                syncForegroundService()
            }
        }
        activeJobs[appId] = job
    }

    private suspend fun resolveApkDownloadUrl(rawUrl: String, storagePath: String): String {
        val trimmedUrl = rawUrl.trim()
        val trimmedPath = storagePath.trim()

        if (trimmedUrl.startsWith("https://") || trimmedUrl.startsWith("http://")) {
            return trimmedUrl
        }
        if (trimmedUrl.startsWith("gs://")) {
            return Firebase.storage.getReferenceFromUrl(trimmedUrl).downloadUrl.await().toString()
        }
        if (trimmedPath.isNotBlank()) {
            if (trimmedPath.startsWith("staging/")) {
                throw SecurityException("APK is still in private developer staging and cannot be downloaded publicly.")
            }
            val ref = if (trimmedPath.startsWith("gs://")) {
                Firebase.storage.getReferenceFromUrl(trimmedPath)
            } else {
                Firebase.storage.reference.child(trimmedPath)
            }
            return ref.downloadUrl.await().toString()
        }
        return ""
    }

    private fun verifyApkFile(
        file: File,
        expectedPackageName: String,
        expectedVersionCode: Long,
        expectedSha256: String,
        expectedSigningFingerprint: String
    ): String? {
        if (!file.exists() || file.length() <= 0L) {
            return "Downloaded APK file is empty or missing."
        }

        // 1. Verify SHA-256 checksum
        val actualSha = computeSha256(file)
        if (expectedSha256.isNotBlank() && !actualSha.equals(expectedSha256, ignoreCase = true)) {
            return "SHA-256 verification failed. The APK file appears corrupted or modified."
        }

        // 2. Verify ZIP/APK archive structure
        try {
            ZipFile(file).use { zip ->
                if (zip.getEntry("AndroidManifest.xml") == null) {
                    return "Invalid APK: AndroidManifest.xml missing."
                }
            }
        } catch (_: Exception) {
            return "Downloaded file is not a valid Android APK archive."
        }

        // 3. Verify packageName, versionCode, and signing certificate
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val pkgInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
        } ?: return "Android PackageManager rejected the downloaded APK archive."

        if (expectedPackageName.isNotBlank() && pkgInfo.packageName != expectedPackageName) {
            return "Security mismatch: APK package (${pkgInfo.packageName}) does not match $expectedPackageName."
        }

        val apkVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkgInfo.longVersionCode
        else @Suppress("DEPRECATION") pkgInfo.versionCode.toLong()
        if (expectedVersionCode > 0L && apkVersionCode != expectedVersionCode) {
            return "Version mismatch: downloaded APK versionCode ($apkVersionCode) does not match $expectedVersionCode."
        }

        val signer = extractSigningFingerprint(pkgInfo)
        if (signer.isBlank()) {
            return "Security error: APK has no valid signing certificate."
        }
        if (expectedSigningFingerprint.isNotBlank() && !signer.equals(expectedSigningFingerprint, ignoreCase = true)) {
            return "Signing certificate mismatch: downloaded APK signature does not match approved release."
        }

        return null
    }

    private fun extractSigningFingerprint(pkgInfo: PackageInfo): String {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.signingInfo?.apkContentsSigners ?: pkgInfo.signingInfo?.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures
        }
        val firstSig = signatures?.firstOrNull() ?: return ""
        val digest = MessageDigest.getInstance("SHA-256").digest(firstSig.toByteArray())
        return digest.joinToString(":") { "%02X".format(it) }
    }

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true
    }

    fun createInstallUnknownAppsSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        }
    }

    fun launchPackageInstaller(apkFile: File): Result<Unit> {
        return try {
            if (!apkFile.exists() || apkFile.length() <= 0L) {
                return Result.failure(IllegalStateException("APK file not found on device. Please re-download."))
            }
            if (!canRequestPackageInstalls()) {
                return Result.failure(SecurityException("Permission to install unknown apps is required in Android Settings."))
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) == null) {
                return Result.failure(IllegalStateException("No Android Package Installer found on this device."))
            }
            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deleteDownloadedApk(packageName: String, versionCode: Long): Boolean {
        val file = getLocalDownloadedApk(packageName, versionCode) ?: return false
        return file.delete()
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun hasNetworkConnection(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    private fun getAvailableStorageBytes(dir: File): Long {
        return try {
            val stat = StatFs(dir.absolutePath)
            stat.availableBytes
        } catch (_: Exception) {
            Long.MAX_VALUE
        }
    }

    private fun formatSizeMb(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return String.format(java.util.Locale.US, "%.1f MB", mb)
    }

    private fun mapDownloadException(e: Exception): String = when (e) {
        is StorageException -> when (e.errorCode) {
            StorageException.ERROR_OBJECT_NOT_FOUND -> "APK file not found on Firebase Storage."
            StorageException.ERROR_NOT_AUTHENTICATED,
            StorageException.ERROR_NOT_AUTHORIZED -> "Sign in with Google to download this release from Firebase Storage."
            else -> e.localizedMessage ?: "Firebase Storage download failed."
        }
        is UnknownHostException -> "Unable to reach download server. Check your internet connection."
        is SocketTimeoutException -> "Download timed out. Tap Retry to resume where you left off."
        is ConnectException -> "Connection failed. Check your network and tap Retry."
        else -> e.localizedMessage ?: "Download failed"
    }

    private fun updateState(
        appId: String,
        persist: Boolean = true,
        reducer: (ActiveDownloadState) -> ActiveDownloadState
    ) {
        _downloads.update { map ->
            val current = map[appId] ?: ActiveDownloadState(appId, "", "", "", "", 1L)
            map + (appId to reducer(current).copy(updatedAtMillis = System.currentTimeMillis()))
        }
        if (persist) persistDownloads()
    }

    private fun updateAndReturn(
        appId: String,
        reducer: (ActiveDownloadState) -> ActiveDownloadState
    ): ActiveDownloadState {
        lateinit var next: ActiveDownloadState
        _downloads.update { map ->
            val current = map[appId] ?: ActiveDownloadState(appId, "", "", "", "", 1L)
            next = reducer(current).copy(updatedAtMillis = System.currentTimeMillis())
            map + (appId to next)
        }
        persistDownloads()
        return next
    }

    private fun notifyStateCallback(state: ActiveDownloadState) {
        statusCallbacks[state.appId]?.invoke(state)
    }

    private fun syncForegroundService() {
        val active = _downloads.value.values.filter {
            it.phase == DownloadPhase.QUEUED || it.phase == DownloadPhase.DOWNLOADING || it.phase == DownloadPhase.VERIFYING
        }
        try {
            if (active.isEmpty()) {
                val stopIntent = Intent(context, ApkDownloadForegroundService::class.java).apply {
                    action = ApkDownloadForegroundService.ACTION_STOP
                }
                context.startService(stopIntent)
            } else {
                val primary = active.first()
                val title = if (active.size == 1) "Downloading ${primary.appName}" else "Downloading ${active.size} apps"
                val subtitle = when (primary.phase) {
                    DownloadPhase.QUEUED -> "Connecting to Firebase Storage…"
                    DownloadPhase.VERIFYING -> "Verifying SHA-256 & APK signature…"
                    else -> "${primary.progressPercent}% • ${primary.speedKbps} KB/s"
                }
                val startIntent = Intent(context, ApkDownloadForegroundService::class.java).apply {
                    action = ApkDownloadForegroundService.ACTION_UPDATE
                    putExtra(ApkDownloadForegroundService.EXTRA_TITLE, title)
                    putExtra(ApkDownloadForegroundService.EXTRA_SUBTITLE, subtitle)
                    putExtra(ApkDownloadForegroundService.EXTRA_PROGRESS, primary.progressPercent)
                    putExtra(
                        ApkDownloadForegroundService.EXTRA_INDETERMINATE,
                        primary.phase != DownloadPhase.DOWNLOADING || primary.totalBytes <= 0L
                    )
                }
                ContextCompat.startForegroundService(context, startIntent)
            }
        } catch (_: Exception) {
        }
    }

    private fun postCompleteNotification(appName: String) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(context, ApkDownloadForegroundService.CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("$appName ready to install")
                .setContentText("SHA-256 & signature verified. Tap Install in APP STORE.")
                .setAutoCancel(true)
                .build()
            nm.notify(appName.hashCode(), notification)
        } catch (_: SecurityException) {
        }
    }

    private fun postFailedNotification(appName: String, reason: String) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(context, ApkDownloadForegroundService.CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("$appName download failed")
                .setContentText(reason)
                .setAutoCancel(true)
                .build()
            nm.notify(appName.hashCode(), notification)
        } catch (_: SecurityException) {
        }
    }

    private fun persistDownloads() {
        try {
            val arr = JSONArray()
            _downloads.value.values.forEach { d ->
                arr.put(JSONObject().apply {
                    put("appId", d.appId)
                    put("versionId", d.versionId)
                    put("appName", d.appName)
                    put("packageName", d.packageName)
                    put("versionName", d.versionName)
                    put("versionCode", d.versionCode)
                    put("iconUrl", d.iconUrl)
                    put("downloadUrl", d.downloadUrl)
                    put("storagePath", d.storagePath)
                    put("expectedSha256", d.expectedSha256)
                    put("expectedSigningFingerprint", d.expectedSigningFingerprint)
                    put("phase", d.phase.name)
                    put("bytesDownloaded", d.bytesDownloaded)
                    put("totalBytes", d.totalBytes)
                    put("progressPercent", d.progressPercent)
                    put("localFilePath", d.localFilePath ?: "")
                    put("errorMessage", d.errorMessage ?: "")
                    put("startedAtMillis", d.startedAtMillis)
                    put("updatedAtMillis", d.updatedAtMillis)
                })
            }
            stateFile.writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    private fun restorePersistedDownloads() {
        if (!stateFile.exists()) return
        try {
            val arr = JSONArray(stateFile.readText())
            val restored = linkedMapOf<String, ActiveDownloadState>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val appId = obj.optString("appId")
                if (appId.isBlank()) continue
                val packageName = obj.optString("packageName")
                val versionCode = obj.optLong("versionCode", 1L)
                var phase = runCatching { DownloadPhase.valueOf(obj.optString("phase")) }
                    .getOrDefault(DownloadPhase.IDLE)

                // If app was killed mid-download, cleanly treat active states as PAUSED if .part exists
                if (phase == DownloadPhase.QUEUED || phase == DownloadPhase.DOWNLOADING || phase == DownloadPhase.VERIFYING) {
                    val part = File(getDownloadsDirectory(), "${packageName}_v${versionCode}.apk.part")
                    phase = if (part.exists() && part.length() > 0L) DownloadPhase.PAUSED else DownloadPhase.FAILED
                }

                // Check if APK file is actually installed or still present
                val localApk = getLocalDownloadedApk(packageName, versionCode)
                val installed = getInstalledStatus(packageName)
                if (phase == DownloadPhase.READY_TO_INSTALL && installed.isInstalled && installed.installedVersionCode >= versionCode) {
                    phase = DownloadPhase.INSTALLED
                } else if (phase == DownloadPhase.READY_TO_INSTALL && localApk == null) {
                    phase = DownloadPhase.FAILED
                }

                restored[appId] = ActiveDownloadState(
                    appId = appId,
                    versionId = obj.optString("versionId"),
                    appName = obj.optString("appName"),
                    packageName = packageName,
                    versionName = obj.optString("versionName"),
                    versionCode = versionCode,
                    iconUrl = obj.optString("iconUrl"),
                    downloadUrl = obj.optString("downloadUrl"),
                    storagePath = obj.optString("storagePath"),
                    expectedSha256 = obj.optString("expectedSha256"),
                    expectedSigningFingerprint = obj.optString("expectedSigningFingerprint"),
                    phase = phase,
                    bytesDownloaded = obj.optLong("bytesDownloaded", 0L),
                    totalBytes = obj.optLong("totalBytes", 0L),
                    progressPercent = obj.optInt("progressPercent", 0),
                    localFilePath = localApk?.absolutePath,
                    errorMessage = obj.optString("errorMessage").takeIf { it.isNotBlank() },
                    startedAtMillis = obj.optLong("startedAtMillis", System.currentTimeMillis()),
                    updatedAtMillis = obj.optLong("updatedAtMillis", System.currentTimeMillis())
                )
            }
            _downloads.value = restored
        } catch (_: Exception) {
        }
    }
}
