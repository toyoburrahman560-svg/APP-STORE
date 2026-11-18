package com.example.data.repository

import com.example.data.firestore.OperationType
import com.example.data.firestore.handleFirestoreError
import com.example.data.model.AppListing
import com.example.data.model.AppSubmission
import com.example.data.model.DeveloperAnalytics
import com.example.data.model.ParsedApkMetadata
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

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
    val agreementAccepted: Boolean
)

class DeveloperPublishRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
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
            val batch = db.batch()
            val stagingApkPath = "staging/$uid/$appId/apk/${draft.apkMetadata.fileName}"

            val appPayload = mapOf(
                "appId" to appId,
                "ownerUid" to uid,
                "appName" to draft.appName.trim().take(60),
                "packageName" to draft.apkMetadata.packageName.take(160),
                "categoryId" to draft.categoryId,
                "categoryName" to draft.categoryName.take(80),
                "categoryGroup" to draft.categoryGroup,
                "iconStoragePath" to "staging/$uid/$appId/media/icon.png",
                "iconUrl" to draft.iconUrl.trim().take(1000),
                "featureGraphicUrl" to draft.featureGraphicUrl.trim().take(1000),
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
                "apkDownloadUrl" to draft.apkExternalUrl.trim().take(1000),
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

            draft.screenshotUrls.forEachIndexed { idx, url ->
                if (url.isNotBlank()) {
                    val mediaRef = db.collection("appMedia").document("${appId}_ss_$idx")
                    batch.set(
                        mediaRef,
                        mapOf(
                            "appId" to appId,
                            "ownerUid" to uid,
                            "mediaType" to "screenshot",
                            "url" to url.trim().take(1000),
                            "storagePath" to "staging/$uid/$appId/media/ss_$idx.jpg",
                            "sortOrder" to idx.toLong(),
                            "publicMedia" to false,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
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
                    "apkStoragePath" to "staging/$uid/${existingApp.appId}/apk/${apkMetadata.fileName}",
                    "apkDownloadUrl" to apkDownloadUrl.trim().take(1000),
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
}
