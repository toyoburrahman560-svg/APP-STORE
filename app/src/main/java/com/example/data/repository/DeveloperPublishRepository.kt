package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.firestore.OperationType
import com.example.data.firestore.handleFirestoreError
import com.example.data.model.AppListing
import com.example.data.model.AppSubmission
import com.example.data.model.DeveloperAnalytics
import com.example.data.model.ParsedApkMetadata
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.storage
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.File

data class PublishSubmissionDraft(
    val appName: String,
    val categoryId: String,
    val categoryName: String,
    val categoryGroup: String,
    val shortDescription: String,
    val fullDescription: String,
    val whatsNew: String,
    val developerName: String,
    val supportContact: String,
    val websiteUrl: String,
    val privacyPolicyUrl: String,
    val iconUrl: String,
    val featureGraphicUrl: String,
    val screenshotUrls: List<String>,
    val previewVideoUrl: String,
    val apkMetadata: ParsedApkMetadata,
    val apkExternalUrl: String,
    val ageRating: String,
    val contentDescription: String,
    val dataCollectionDeclared: String,
    val copyrightDeclared: Boolean,
    val agreementAccepted: Boolean,
    val iconUri: Uri? = null,
    val featureGraphicUri: Uri? = null,
    val screenshotUris: List<Uri> = emptyList()
)

class DeveloperPublishRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val context: Context? = null
) {
    private val _uploadProgress = MutableStateFlow<Float?>(null)
    val uploadProgress: StateFlow<Float?> = _uploadProgress.asStateFlow()

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before publishing.")
    }

    fun observeDeveloperApps(ownerUid: String): Flow<List<AppListing>> {
        if (ownerUid.isBlank()) return flowOf(emptyList())
        return db.collection("apps")
            .whereEqualTo("ownerUid", ownerUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(AppListing::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt?.seconds ?: 0L }
            }
    }

    fun observeDeveloperSubmissions(ownerUid: String): Flow<List<AppSubmission>> {
        if (ownerUid.isBlank()) return flowOf(emptyList())
        return db.collection("appSubmissions")
            .whereEqualTo("ownerUid", ownerUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(AppSubmission::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt?.seconds ?: 0L }
            }
    }

    fun observeDeveloperAnalytics(ownerUid: String): Flow<List<DeveloperAnalytics>> {
        if (ownerUid.isBlank()) return flowOf(emptyList())
        return db.collection("developerAnalytics")
            .whereEqualTo("ownerUid", ownerUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(DeveloperAnalytics::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
    }

    suspend fun submitApplicationForReview(draft: PublishSubmissionDraft): Result<String> {
        val uid = requireUserId()
        val appId = "app_${draft.apkMetadata.packageName.replace(Regex("[^a-zA-Z0-9_]"), "_").take(40)}_${System.currentTimeMillis().toString().takeLast(6)}"
        val versionId = "${appId}_v${draft.apkMetadata.versionCode}"
        val submissionId = "sub_${System.currentTimeMillis()}"

        val appRef = db.collection("apps").document(appId)
        val versionRef = db.collection("appVersions").document(versionId)
        val submissionRef = db.collection("appSubmissions").document(submissionId)

        return try {
            val safeFileName = sanitizeApkFileName(draft.apkMetadata.fileName, draft.apkMetadata.packageName)
            val stagingApkPath = "staging/$uid/$appId/apk/$safeFileName"

            val normalizedExternalUrl = normalizeDirectApkUrl(draft.apkExternalUrl)
            val resolvedApkDownloadUrl: String = if (normalizedExternalUrl.isNotBlank()) {
                normalizedExternalUrl
            } else {
                uploadApkFileToStorage(
                    localPath = draft.apkMetadata.localCachePath,
                    storagePath = stagingApkPath
                )
            }

            // Best-effort upload for icon / feature graphic / screenshots selected via Photo Picker
            val resolvedIconUrl = draft.iconUrl.trim().ifBlank {
                draft.iconUri?.let { uri ->
                    tryUploadMediaUri(uri, "staging/$uid/$appId/media/icon.png", "image/png")
                }.orEmpty()
            }
            val resolvedFeatureUrl = draft.featureGraphicUrl.trim().ifBlank {
                draft.featureGraphicUri?.let { uri ->
                    tryUploadMediaUri(uri, "staging/$uid/$appId/media/feature.jpg", "image/jpeg")
                }.orEmpty()
            }
            val uploadedScreenshotPairs = mutableListOf<Pair<String, String>>()
            draft.screenshotUrls.forEachIndexed { idx, url ->
                if (url.isNotBlank()) {
                    uploadedScreenshotPairs.add(url.trim() to "staging/$uid/$appId/media/ss_$idx.jpg")
                }
            }
            draft.screenshotUris.forEachIndexed { idx, uri ->
                val storagePath = "staging/$uid/$appId/media/ss_upload_$idx.jpg"
                val uploadedUrl = tryUploadMediaUri(uri, storagePath, "image/jpeg")
                if (!uploadedUrl.isNullOrBlank()) {
                    uploadedScreenshotPairs.add(uploadedUrl to storagePath)
                }
            }

            val batch = db.batch()

            val appPayload = mapOf(
                "appId" to appId,
                "ownerUid" to uid,
                "appName" to draft.appName.trim().take(60),
                "packageName" to draft.apkMetadata.packageName.take(160),
                "categoryId" to draft.categoryId,
                "categoryName" to draft.categoryName.take(80),
                "categoryGroup" to draft.categoryGroup,
                "iconStoragePath" to "staging/$uid/$appId/media/icon.png",
                "iconUrl" to resolvedIconUrl.take(1000),
                "featureGraphicUrl" to resolvedFeatureUrl.take(1000),
                "shortDescription" to draft.shortDescription.trim().take(200),
                "description" to draft.fullDescription.trim().take(5000),
                "whatsNew" to draft.whatsNew.trim().take(2000),
                "developerName" to draft.developerName.trim().take(100),
                "websiteUrl" to draft.websiteUrl.trim().take(500),
                "privacyPolicyUrl" to draft.privacyPolicyUrl.trim().take(500),
                "supportContact" to draft.supportContact.trim().take(200),
                "versionName" to draft.apkMetadata.versionName.take(60),
                "versionCode" to draft.apkMetadata.versionCode,
                "apkSizeBytes" to draft.apkMetadata.fileSizeBytes,
                "minSdk" to draft.apkMetadata.minSdk.toLong(),
                "targetSdk" to draft.apkMetadata.targetSdk.toLong(),
                "ageRating" to draft.ageRating.take(40),
                "status" to "pending",
                "featured" to false,
                "editorsChoice" to false,
                "trendingScore" to 0L,
                "averageRating" to 0.0,
                "totalReviews" to 0L,
                "downloadCount" to 0L,
                "viewCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(appRef, appPayload)

            val versionPayload = mapOf(
                "versionId" to versionId,
                "appId" to appId,
                "ownerUid" to uid,
                "packageName" to draft.apkMetadata.packageName.take(160),
                "versionName" to draft.apkMetadata.versionName.take(60),
                "versionCode" to draft.apkMetadata.versionCode,
                "apkStoragePath" to stagingApkPath,
                "apkDownloadUrl" to resolvedApkDownloadUrl.take(1000),
                "apkSize" to draft.apkMetadata.fileSizeBytes,
                "sha256" to draft.apkMetadata.sha256,
                "signingCertificateFingerprint" to draft.apkMetadata.signingCertificateFingerprint,
                "minSdk" to draft.apkMetadata.minSdk.toLong(),
                "targetSdk" to draft.apkMetadata.targetSdk.toLong(),
                "permissions" to draft.apkMetadata.permissions.take(100),
                "validationStatus" to "pending",
                "scanStatus" to "pending",
                "approvalStatus" to "pending",
                "createdAt" to FieldValue.serverTimestamp()
            )
            batch.set(versionRef, versionPayload)

            val submissionPayload = mapOf(
                "appId" to appId,
                "versionId" to versionId,
                "ownerUid" to uid,
                "appName" to draft.appName.trim().take(60),
                "packageName" to draft.apkMetadata.packageName.take(160),
                "versionName" to draft.apkMetadata.versionName.take(60),
                "versionCode" to draft.apkMetadata.versionCode,
                "categoryId" to draft.categoryId,
                "status" to "pending",
                "ageRating" to draft.ageRating.take(40),
                "contentDescription" to draft.contentDescription.trim().take(1000),
                "dataCollectionDeclared" to draft.dataCollectionDeclared.trim().take(1000),
                "copyrightDeclared" to draft.copyrightDeclared,
                "agreementAccepted" to draft.agreementAccepted,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(submissionRef, submissionPayload)

            uploadedScreenshotPairs.forEachIndexed { idx, (url, path) ->
                val mediaRef = db.collection("appMedia").document("${appId}_ss_$idx")
                batch.set(
                    mediaRef,
                    mapOf(
                        "appId" to appId,
                        "ownerUid" to uid,
                        "mediaType" to "screenshot",
                        "url" to url.take(1000),
                        "storagePath" to path.take(500),
                        "sortOrder" to idx.toLong(),
                        "publicMedia" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            if (draft.previewVideoUrl.isNotBlank()) {
                val vidRef = db.collection("appMedia").document("${appId}_video")
                batch.set(
                    vidRef,
                    mapOf(
                        "appId" to appId,
                        "ownerUid" to uid,
                        "mediaType" to "video",
                        "url" to draft.previewVideoUrl.trim().take(1000),
                        "storagePath" to "staging/$uid/$appId/media/preview.mp4",
                        "sortOrder" to 99L,
                        "publicMedia" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.commit().await()
            Result.success(appId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, submissionRef.path)
            Result.failure(e)
        } finally {
            _uploadProgress.value = null
        }
    }

    suspend fun updateAppMetadata(
        appId: String,
        appName: String,
        shortDescription: String,
        fullDescription: String,
        whatsNew: String,
        websiteUrl: String,
        privacyPolicyUrl: String,
        resubmitForReview: Boolean = false
    ): Result<Unit> {
        requireUserId()
        val appRef = db.collection("apps").document(appId)
        return try {
            val updates = mutableMapOf<String, Any>(
                "appName" to appName.trim().take(60),
                "shortDescription" to shortDescription.trim().take(200),
                "description" to fullDescription.trim().take(5000),
                "whatsNew" to whatsNew.trim().take(2000),
                "websiteUrl" to websiteUrl.trim().take(500),
                "privacyPolicyUrl" to privacyPolicyUrl.trim().take(500),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (resubmitForReview) {
                updates["status"] = "pending"
            }
            appRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, appRef.path)
            Result.failure(e)
        }
    }

    suspend fun submitNewApkVersion(
        existingApp: AppListing,
        apkMetadata: ParsedApkMetadata,
        apkDownloadUrl: String,
        whatsNew: String
    ): Result<Unit> {
        val uid = requireUserId()
        val versionId = "${existingApp.appId}_v${apkMetadata.versionCode}_${System.currentTimeMillis().toString().takeLast(4)}"
        val submissionId = "sub_${System.currentTimeMillis()}"
        val appRef = db.collection("apps").document(existingApp.appId)
        val versionRef = db.collection("appVersions").document(versionId)
        val submissionRef = db.collection("appSubmissions").document(submissionId)

        return try {
            require(apkMetadata.packageName == existingApp.packageName) {
                "Update APK packageName (${apkMetadata.packageName}) must match ${existingApp.packageName}"
            }
            require(apkMetadata.versionCode > existingApp.versionCode) {
                "Update versionCode (${apkMetadata.versionCode}) must be higher than current (${existingApp.versionCode})"
            }

            val safeFileName = sanitizeApkFileName(apkMetadata.fileName, apkMetadata.packageName)
            val stagingApkPath = "staging/$uid/${existingApp.appId}/apk/$safeFileName"
            val normalizedExternalUrl = normalizeDirectApkUrl(apkDownloadUrl)
            val resolvedUrl = if (normalizedExternalUrl.isNotBlank()) {
                normalizedExternalUrl
            } else {
                uploadApkFileToStorage(
                    localPath = apkMetadata.localCachePath,
                    storagePath = stagingApkPath
                )
            }

            val batch = db.batch()
            batch.set(
                versionRef,
                mapOf(
                    "versionId" to versionId,
                    "appId" to existingApp.appId,
                    "ownerUid" to uid,
                    "packageName" to apkMetadata.packageName,
                    "versionName" to apkMetadata.versionName,
                    "versionCode" to apkMetadata.versionCode,
                    "apkStoragePath" to stagingApkPath,
                    "apkDownloadUrl" to resolvedUrl.take(1000),
                    "apkSize" to apkMetadata.fileSizeBytes,
                    "sha256" to apkMetadata.sha256,
                    "signingCertificateFingerprint" to apkMetadata.signingCertificateFingerprint,
                    "minSdk" to apkMetadata.minSdk.toLong(),
                    "targetSdk" to apkMetadata.targetSdk.toLong(),
                    "permissions" to apkMetadata.permissions.take(100),
                    "validationStatus" to "pending",
                    "scanStatus" to "pending",
                    "approvalStatus" to "pending",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                submissionRef,
                mapOf(
                    "appId" to existingApp.appId,
                    "versionId" to versionId,
                    "ownerUid" to uid,
                    "appName" to existingApp.appName,
                    "packageName" to existingApp.packageName,
                    "versionName" to apkMetadata.versionName,
                    "versionCode" to apkMetadata.versionCode,
                    "categoryId" to existingApp.categoryId,
                    "status" to "pending",
                    "ageRating" to existingApp.ageRating,
                    "copyrightDeclared" to true,
                    "agreementAccepted" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                appRef,
                mapOf(
                    "whatsNew" to whatsNew.trim().take(2000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, versionRef.path)
            Result.failure(e)
        } finally {
            _uploadProgress.value = null
        }
    }

    suspend fun archiveApp(appId: String): Result<Unit> {
        requireUserId()
        val appRef = db.collection("apps").document(appId)
        return try {
            appRef.update(
                mapOf(
                    "status" to "archived",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, appRef.path)
            Result.failure(e)
        }
    }

    private suspend fun uploadApkFileToStorage(localPath: String, storagePath: String): String {
        val file = File(localPath)
        if (!file.exists() || file.length() <= 0L) {
            throw IllegalStateException("Cached APK file is missing. Re-select the .apk file or enter an HTTPS APK link.")
        }
        return try {
            _uploadProgress.value = 0.01f
            val ref = Firebase.storage.reference.child(storagePath)
            val metadata = storageMetadata {
                contentType = "application/vnd.android.package-archive"
            }
            val task = ref.putFile(Uri.fromFile(file), metadata)
            task.addOnProgressListener { snap ->
                if (snap.totalByteCount > 0L) {
                    _uploadProgress.value = (snap.bytesTransferred.toFloat() / snap.totalByteCount.toFloat())
                        .coerceIn(0.01f, 0.99f)
                }
            }
            task.await()
            val url = ref.downloadUrl.await().toString()
            _uploadProgress.value = 1f
            url
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val detail = when (e) {
                is StorageException -> "${e.localizedMessage} (Code ${e.errorCode})"
                else -> e.localizedMessage ?: "Storage error"
            }
            throw IllegalStateException(
                "Could not upload APK to Firebase Storage ($detail). Enable Firebase Storage in Firebase Console OR paste a direct HTTPS APK download link (e.g. GitHub Releases / Google Drive) in Step 2.",
                e
            )
        }
    }

    private suspend fun tryUploadMediaUri(uri: Uri, storagePath: String, mimeType: String): String? {
        return try {
            val ref = Firebase.storage.reference.child(storagePath)
            val metadata = storageMetadata { contentType = mimeType }
            val bytes = context?.contentResolver?.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null && bytes.isNotEmpty()) {
                ref.putBytes(bytes, metadata).await()
            } else {
                ref.putFile(uri, metadata).await()
            }
            ref.downloadUrl.await().toString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("DeveloperPublishRepo", "Optional media upload skipped: ${e.message}")
            null
        }
    }

    companion object {
        fun sanitizeApkFileName(fileName: String, packageName: String): String {
            val base = fileName.trim().ifBlank { "${packageName}.apk" }
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            return if (base.endsWith(".apk", ignoreCase = true)) base else "$base.apk"
        }

        fun normalizeDirectApkUrl(rawUrl: String): String {
            val trimmed = rawUrl.trim()
            if (trimmed.isBlank()) return ""
            val driveMatch = Regex("https?://drive\\.google\\.com/file/d/([a-zA-Z0-9_-]+)").find(trimmed)
                ?: Regex("https?://drive\\.google\\.com/open\\?id=([a-zA-Z0-9_-]+)").find(trimmed)
            if (driveMatch != null) {
                val id = driveMatch.groupValues[1]
                return "https://drive.google.com/uc?export=download&confirm=t&id=$id"
            }
            return trimmed
        }
    }
}
