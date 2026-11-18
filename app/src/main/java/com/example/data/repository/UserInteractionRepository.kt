package com.example.data.repository

import com.example.data.firestore.OperationType
import com.example.data.firestore.handleFirestoreError
import com.example.data.model.AppListing
import com.example.data.model.AppReview
import com.example.data.model.DeveloperProfile
import com.example.data.model.DownloadRecord
import com.example.data.model.NotificationItem
import com.example.data.model.WishlistEntry
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

class UserInteractionRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeWishlist(userId: String): Flow<List<WishlistEntry>> {
        if (userId.isBlank()) return flowOf(emptyList())
        return db.collection("wishlists")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(WishlistEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
    }

    suspend fun toggleWishlist(app: AppListing, currentlySaved: Boolean): Result<Boolean> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Sign in with Google to manage wishlist."))
        val docId = "${uid}_${app.appId}"
        val docRef = db.collection("wishlists").document(docId)
        return try {
            if (currentlySaved) {
                docRef.delete().await()
                Result.success(false)
            } else {
                val payload = mapOf(
                    "userId" to uid,
                    "appId" to app.appId,
                    "appName" to app.appName.take(80),
                    "developerName" to app.developerName.take(100),
                    "iconUrl" to app.iconUrl.take(1000),
                    "categoryName" to app.categoryName.take(80),
                    "averageRating" to app.averageRating,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                Result.success(true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeDownloadHistory(userId: String): Flow<List<DownloadRecord>> {
        if (userId.isBlank()) return flowOf(emptyList())
        return db.collection("downloads")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(DownloadRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
            }
    }

    suspend fun recordDownloadEvent(
        app: AppListing,
        versionId: String,
        versionName: String,
        versionCode: Long,
        sha256: String,
        status: String,
        downloadedBytes: Long = 0L,
        fileSizeBytes: Long = 0L
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.success("")
        val docId = "${uid}_${app.appId}_v${versionCode}"
        val docRef = db.collection("downloads").document(docId)
        return try {
            val existing = docRef.get().await()
            val prevStatus = existing.getString("status").orEmpty()
            val completedBefore = prevStatus in setOf("verified", "completed", "installed")
            val becomingCompleted = status in setOf("verified", "completed")

            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "appId" to app.appId,
                    "versionId" to versionId,
                    "appName" to app.appName.take(80),
                    "packageName" to app.packageName.take(160),
                    "versionName" to versionName.take(60),
                    "versionCode" to versionCode,
                    "fileSizeBytes" to fileSizeBytes.coerceAtLeast(0L),
                    "downloadedBytes" to downloadedBytes.coerceAtLeast(0L),
                    "iconUrl" to app.iconUrl.take(1000),
                    "sha256" to sha256.take(128),
                    "status" to status,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                docRef.update(
                    mapOf(
                        "status" to status,
                        "downloadedBytes" to downloadedBytes.coerceAtLeast(0L),
                        "fileSizeBytes" to fileSizeBytes.coerceAtLeast(0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            if (becomingCompleted && !completedBefore && app.status.equals("approved", ignoreCase = true)) {
                runCatching {
                    db.collection("apps").document(app.appId)
                        .update("downloadCount", FieldValue.increment(1L))
                        .await()
                }
            }
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteDownloadRecord(downloadId: String): Result<Unit> {
        requireUserId()
        val docRef = db.collection("downloads").document(downloadId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun submitOrUpdateReview(
        appId: String,
        rating: Int,
        comment: String
    ): Result<Unit> {
        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("Sign in with Google to submit a review."))
        val reviewId = "${appId}_${user.uid}"
        val docRef = db.collection("reviews").document(reviewId)
        return try {
            require(rating in 1..5) { "Rating must be between 1 and 5" }
            val existing = docRef.get().await()
            val userName = user.displayName?.takeIf { it.isNotBlank() } ?: "App Store User"
            val userPhoto = user.photoUrl?.toString().orEmpty()
            if (!existing.exists()) {
                val payload = mapOf(
                    "appId" to appId,
                    "userId" to user.uid,
                    "userName" to userName.take(100),
                    "userPhotoUrl" to userPhoto.take(1000),
                    "rating" to rating.toLong(),
                    "comment" to comment.trim().take(2000),
                    "helpfulCount" to 0L,
                    "moderationStatus" to "visible",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                docRef.update(
                    mapOf(
                        "rating" to rating.toLong(),
                        "comment" to comment.trim().take(2000),
                        "userName" to userName.take(100),
                        "userPhotoUrl" to userPhoto.take(1000),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteOwnReview(appId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val reviewId = "${appId}_${uid}"
        val docRef = db.collection("reviews").document(reviewId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun voteReviewHelpful(reviewId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sign in to vote"))
        val voteId = "${reviewId}_${uid}"
        val voteRef = db.collection("reviewVotes").document(voteId)
        val reviewRef = db.collection("reviews").document(reviewId)
        return try {
            val existing = voteRef.get().await()
            if (existing.exists()) {
                return Result.success(Unit)
            }
            voteRef.set(
                mapOf(
                    "reviewId" to reviewId,
                    "userId" to uid,
                    "helpful" to true,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            reviewRef.update("helpfulCount", FieldValue.increment(1L)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, reviewRef.path)
            Result.failure(e)
        }
    }

    suspend fun replyToReview(reviewId: String, replyText: String): Result<Unit> {
        requireUserId()
        val reviewRef = db.collection("reviews").document(reviewId)
        return try {
            reviewRef.update(
                mapOf(
                    "developerReply" to replyText.trim().take(2000),
                    "developerReplyAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, reviewRef.path)
            Result.failure(e)
        }
    }

    suspend fun submitReport(targetType: String, targetId: String, reason: String, details: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Sign in with Google to submit a report."))
        val docRef = db.collection("reports").document()
        return try {
            val payload = mapOf(
                "reporterUid" to uid,
                "targetType" to targetType,
                "targetId" to targetId.take(200),
                "reason" to reason,
                "details" to details.trim().take(2000),
                "status" to "open",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeNotifications(userId: String): Flow<List<NotificationItem>> {
        if (userId.isBlank()) return flowOf(emptyList())
        return db.collection("notifications")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(NotificationItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
    }

    suspend fun markNotificationRead(notificationId: String): Result<Unit> {
        requireUserId()
        val docRef = db.collection("notifications").document(notificationId)
        return try {
            docRef.update("read", true).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeDeveloperProfile(uid: String): Flow<DeveloperProfile?> {
        if (uid.isBlank()) return flowOf(null)
        return db.collection("developerProfiles").document(uid)
            .snapshots()
            .map { snap ->
                if (!snap.exists()) null
                else snap.toObject(DeveloperProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
    }

    suspend fun registerOrUpdateDeveloperProfile(
        displayName: String,
        description: String,
        contactEmail: String,
        websiteUrl: String,
        privacyPolicyUrl: String,
        avatarUrl: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Sign in with Google first."))
        val devRef = db.collection("developerProfiles").document(uid)
        val userRef = db.collection("users").document(uid)
        return try {
            val existing = devRef.get().await()
            if (!existing.exists()) {
                devRef.set(
                    mapOf(
                        "uid" to uid,
                        "displayName" to displayName.trim().take(80),
                        "description" to description.trim().take(2000),
                        "avatarUrl" to avatarUrl.trim().take(1000),
                        "contactEmail" to contactEmail.trim().take(200),
                        "websiteUrl" to websiteUrl.trim().take(500),
                        "privacyPolicyUrl" to privacyPolicyUrl.trim().take(500),
                        "status" to "pending",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                userRef.update(
                    mapOf(
                        "developerStatus" to "pending",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                devRef.update(
                    mapOf(
                        "displayName" to displayName.trim().take(80),
                        "description" to description.trim().take(2000),
                        "avatarUrl" to avatarUrl.trim().take(1000),
                        "contactEmail" to contactEmail.trim().take(200),
                        "websiteUrl" to websiteUrl.trim().take(500),
                        "privacyPolicyUrl" to privacyPolicyUrl.trim().take(500),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, devRef.path)
            Result.failure(e)
        }
    }

    fun observeMyReviews(userId: String): Flow<List<AppReview>> {
        if (userId.isBlank()) return flowOf(emptyList())
        return db.collection("reviews")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(AppReview::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
            }
    }
}
