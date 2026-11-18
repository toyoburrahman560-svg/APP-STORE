package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AppStatus(val raw: String) {
    DRAFT("draft"),
    UPLOADING("uploading"),
    PENDING("pending"),
    VALIDATING("validating"),
    UNDER_REVIEW("under_review"),
    APPROVED("approved"),
    REJECTED("rejected"),
    SUSPENDED("suspended"),
    ARCHIVED("archived");

    companion object {
        fun fromRaw(value: String?): AppStatus =
            entries.firstOrNull { it.raw.equals(value, ignoreCase = true) } ?: PENDING
    }
}

enum class DownloadPhase {
    IDLE, QUEUED, DOWNLOADING, PAUSED, VERIFYING, READY_TO_INSTALL, INSTALLED, FAILED, CANCELLED
}

enum class ThemePreference { SYSTEM, LIGHT, DARK }
enum class LanguagePreference(val tag: String, val displayName: String) {
    ENGLISH("en", "English"),
    BENGALI("bn", "বাংলা")
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoURL: String = "",
    val accountType: String = "user",
    val developerStatus: String = "none",
    val accountStatus: String = "active",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val lastLoginAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DeveloperProfile(
    @DocumentId val id: String = "",
    val uid: String = "",
    val displayName: String = "",
    val description: String = "",
    val avatarUrl: String = "",
    val contactEmail: String = "",
    val websiteUrl: String = "",
    val privacyPolicyUrl: String = "",
    val status: String = "pending",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MarketplaceCategory(
    @DocumentId val id: String = "",
    val name: String = "",
    val nameBn: String = "",
    val group: String = "apps",
    val iconName: String = "apps",
    val sortOrder: Long = 0L,
    val enabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppListing(
    @DocumentId val id: String = "",
    val appId: String = "",
    val ownerUid: String = "",
    val appName: String = "",
    val packageName: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val categoryGroup: String = "apps",
    val iconStoragePath: String = "",
    val iconUrl: String = "",
    val featureGraphicUrl: String = "",
    val shortDescription: String = "",
    val description: String = "",
    val whatsNew: String = "",
    val developerName: String = "",
    val websiteUrl: String = "",
    val privacyPolicyUrl: String = "",
    val supportContact: String = "",
    val versionName: String = "1.0.0",
    val versionCode: Long = 1L,
    val apkSizeBytes: Long = 0L,
    val minSdk: Long = 24L,
    val targetSdk: Long = 35L,
    val ageRating: String = "Everyone",
    val status: String = "pending",
    val featured: Boolean = false,
    val editorsChoice: Boolean = false,
    val trendingScore: Long = 0L,
    val averageRating: Double = 0.0,
    val totalReviews: Long = 0L,
    val downloadCount: Long = 0L,
    val viewCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppVersion(
    @DocumentId val id: String = "",
    val versionId: String = "",
    val appId: String = "",
    val ownerUid: String = "",
    val packageName: String = "",
    val versionName: String = "",
    val versionCode: Long = 1L,
    val apkStoragePath: String = "",
    val apkDownloadUrl: String = "",
    val apkSize: Long = 0L,
    val sha256: String = "",
    val signingCertificateFingerprint: String = "",
    val minSdk: Long = 24L,
    val targetSdk: Long = 35L,
    val permissions: List<String> = emptyList(),
    val validationStatus: String = "pending",
    val scanStatus: String = "pending",
    val approvalStatus: String = "pending",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppSubmission(
    @DocumentId val id: String = "",
    val appId: String = "",
    val versionId: String = "",
    val ownerUid: String = "",
    val appName: String = "",
    val packageName: String = "",
    val versionName: String = "",
    val versionCode: Long = 1L,
    val categoryId: String = "",
    val status: String = "pending",
    val rejectionReason: String = "",
    val moderationNotes: String = "",
    val ageRating: String = "Everyone",
    val contentDescription: String = "",
    val dataCollectionDeclared: String = "",
    val copyrightDeclared: Boolean = false,
    val agreementAccepted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppMedia(
    @DocumentId val id: String = "",
    val appId: String = "",
    val ownerUid: String = "",
    val mediaType: String = "screenshot",
    val url: String = "",
    val storagePath: String = "",
    val sortOrder: Long = 0L,
    val publicMedia: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppReview(
    @DocumentId val id: String = "",
    val appId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhotoUrl: String = "",
    val rating: Long = 5L,
    val comment: String = "",
    val helpfulCount: Long = 0L,
    val developerReply: String = "",
    val developerReplyAt: Timestamp? = null,
    val moderationStatus: String = "visible",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class WishlistEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val appId: String = "",
    val appName: String = "",
    val developerName: String = "",
    val iconUrl: String = "",
    val categoryName: String = "",
    val averageRating: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DownloadRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val appId: String = "",
    val versionId: String = "",
    val appName: String = "",
    val packageName: String = "",
    val versionName: String = "",
    val versionCode: Long = 1L,
    val fileSizeBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val iconUrl: String = "",
    val sha256: String = "",
    val status: String = "queued",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class NotificationItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val type: String = "announcement",
    val title: String = "",
    val body: String = "",
    val targetAppId: String = "",
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Announcement(
    @DocumentId val id: String = "",
    val title: String = "",
    val body: String = "",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DeveloperAnalytics(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val appId: String = "",
    val periodKey: String = "30d",
    val totalDownloads: Long = 0L,
    val totalViews: Long = 0L,
    val dailyDownloads: List<Long> = emptyList(),
    val weeklyDownloads: Long = 0L,
    val monthlyDownloads: Long = 0L,
    val averageRating: Double = 0.0,
    val totalReviews: Long = 0L,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PlatformSettings(
    @DocumentId val id: String = "",
    val platformName: String = "APP STORE",
    val supportEmail: String = "",
    val maxUploadSizeMb: Long = 250L,
    val allowedExtensions: List<String> = listOf("apk"),
    val maintenanceMode: Boolean = false,
    val monetizationEnabled: Boolean = false,
    val privacyPolicyUrl: String = "",
    val termsUrl: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class ParsedApkMetadata(
    val fileName: String,
    val localCachePath: String,
    val fileSizeBytes: Long,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val permissions: List<String>,
    val sha256: String,
    val signingCertificateFingerprint: String,
    val validStructure: Boolean,
    val validationErrors: List<String> = emptyList()
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
