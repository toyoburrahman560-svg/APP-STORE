package com.example.data.repository

import com.example.data.firestore.OperationType
import com.example.data.firestore.handleFirestoreError
import com.example.data.model.Announcement
import com.example.data.model.AppListing
import com.example.data.model.AppMedia
import com.example.data.model.AppReview
import com.example.data.model.AppVersion
import com.example.data.model.MarketplaceCategory
import com.example.data.model.PlatformSettings
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class MarketplaceRepository(private val db: FirebaseFirestore) {

    fun observeApprovedApps(): Flow<List<AppListing>> =
        db.collection("apps")
            .whereEqualTo("status", "approved")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(AppListing::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val fsError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsError == null && error is CancellationException) throw error
                handleFirestoreError(fsError ?: error, OperationType.LIST, "apps")
                throw error
            }

    fun observeCategories(): Flow<List<MarketplaceCategory>> =
        db.collection("categories")
            .whereEqualTo("enabled", true)
            .snapshots()
            .map { snapshot ->
                val list = snapshot.toObjects(MarketplaceCategory::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                val sorted = list.sortedBy { it.sortOrder }
                sorted.ifEmpty { DefaultCategories }
            }
            .catch { error ->
                val fsError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsError == null && error is CancellationException) throw error
                handleFirestoreError(fsError ?: error, OperationType.LIST, "categories")
                emit(DefaultCategories)
            }

    fun observeAppById(appId: String): Flow<AppListing?> =
        db.collection("apps").document(appId)
            .snapshots()
            .map { snap ->
                if (!snap.exists()) null
                else snap.toObject(AppListing::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }

    fun observeApprovedVersions(appId: String): Flow<List<AppVersion>> =
        db.collection("appVersions")
            .whereEqualTo("appId", appId)
            .whereEqualTo("approvalStatus", "approved")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(AppVersion::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.versionCode }
            }

    fun observePublicMedia(appId: String): Flow<List<AppMedia>> =
        db.collection("appMedia")
            .whereEqualTo("appId", appId)
            .whereEqualTo("publicMedia", true)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(AppMedia::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.sortOrder }
            }

    fun observeReviews(appId: String): Flow<List<AppReview>> =
        db.collection("reviews")
            .whereEqualTo("appId", appId)
            .whereEqualTo("moderationStatus", "visible")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(AppReview::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }

    fun observeAnnouncements(): Flow<List<Announcement>> =
        db.collection("announcements")
            .whereEqualTo("active", true)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Announcement::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { emit(emptyList()) }

    suspend fun getPlatformSettings(): PlatformSettings {
        return try {
            val doc = db.collection("platformSettings").document("global").get().await()
            if (doc.exists()) {
                doc.toObject(PlatformSettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: PlatformSettings()
            } else {
                PlatformSettings()
            }
        } catch (_: Exception) {
            PlatformSettings()
        }
    }

    companion object {
        val DefaultCategories: List<MarketplaceCategory> = listOf(
            // Games
            MarketplaceCategory(id = "action", name = "Action", nameBn = "অ্যাকশন", group = "games", iconName = "sports_esports", sortOrder = 1),
            MarketplaceCategory(id = "adventure", name = "Adventure", nameBn = "অ্যাডভেঞ্চার", group = "games", iconName = "explore", sortOrder = 2),
            MarketplaceCategory(id = "racing", name = "Racing", nameBn = "রেসিং", group = "games", iconName = "directions_car", sortOrder = 3),
            MarketplaceCategory(id = "puzzle", name = "Puzzle", nameBn = "পাজল", group = "games", iconName = "extension", sortOrder = 4),
            MarketplaceCategory(id = "strategy", name = "Strategy", nameBn = "স্ট্র্যাটেজি", group = "games", iconName = "psychology", sortOrder = 5),
            MarketplaceCategory(id = "arcade", name = "Arcade", nameBn = "আর্কেড", group = "games", iconName = "videogame_asset", sortOrder = 6),
            MarketplaceCategory(id = "sports", name = "Sports", nameBn = "স্পোর্টস", group = "games", iconName = "sports_soccer", sortOrder = 7),
            // Applications
            MarketplaceCategory(id = "education", name = "Education", nameBn = "শিক্ষা", group = "apps", iconName = "school", sortOrder = 8),
            MarketplaceCategory(id = "entertainment", name = "Entertainment", nameBn = "বিনোদন", group = "apps", iconName = "theaters", sortOrder = 9),
            MarketplaceCategory(id = "photography", name = "Photography", nameBn = "ফটোগ্রাফি", group = "apps", iconName = "photo_camera", sortOrder = 10),
            MarketplaceCategory(id = "video_players", name = "Video Players", nameBn = "ভিডিও প্লেয়ার", group = "apps", iconName = "smart_display", sortOrder = 11),
            MarketplaceCategory(id = "music", name = "Music", nameBn = "সংগীত", group = "apps", iconName = "music_note", sortOrder = 12),
            MarketplaceCategory(id = "business", name = "Business", nameBn = "ব্যবসা", group = "apps", iconName = "business_center", sortOrder = 13),
            MarketplaceCategory(id = "productivity", name = "Productivity", nameBn = "উৎপাদনশীলতা", group = "apps", iconName = "task_alt", sortOrder = 14),
            MarketplaceCategory(id = "tools", name = "Tools", nameBn = "টুলস", group = "apps", iconName = "build", sortOrder = 15),
            MarketplaceCategory(id = "lifestyle", name = "Lifestyle", nameBn = "লাইফস্টাইল", group = "apps", iconName = "spa", sortOrder = 16),
            MarketplaceCategory(id = "finance", name = "Finance", nameBn = "অর্থনীতি", group = "apps", iconName = "account_balance", sortOrder = 17),
            MarketplaceCategory(id = "health_fitness", name = "Health & Fitness", nameBn = "স্বাস্থ্য ও ফিটনেস", group = "apps", iconName = "fitness_center", sortOrder = 18),
            MarketplaceCategory(id = "communication", name = "Communication", nameBn = "যোগাযোগ", group = "apps", iconName = "forum", sortOrder = 19),
            MarketplaceCategory(id = "shopping", name = "Shopping", nameBn = "শপিং", group = "apps", iconName = "shopping_bag", sortOrder = 20),
            MarketplaceCategory(id = "news", name = "News", nameBn = "সংবাদ", group = "apps", iconName = "newspaper", sortOrder = 21),
            MarketplaceCategory(id = "travel", name = "Travel", nameBn = "ভ্রমণ", group = "apps", iconName = "flight", sortOrder = 22),
            MarketplaceCategory(id = "personalization", name = "Personalization", nameBn = "পার্সোনালাইজেশন", group = "apps", iconName = "palette", sortOrder = 23),
            MarketplaceCategory(id = "other", name = "Other", nameBn = "অন্যান্য", group = "apps", iconName = "apps", sortOrder = 24)
        )
    }
}
