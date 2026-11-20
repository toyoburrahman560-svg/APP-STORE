package com.example.data.repository

import android.net.Uri
import android.util.Log
import com.example.data.firestore.OperationType
import com.example.data.firestore.handleFirestoreError
import com.example.data.model.AdminLog
import com.example.data.model.AppListing
import com.example.data.model.AppReport
import com.example.data.model.AppReview
import com.example.data.model.AppSubmission
import com.example.data.model.AppVersion
import com.example.data.model.DeveloperProfile
import com.example.data.model.MarketplaceCategory
import com.example.data.model.ParsedApkMetadata
import com.example.data.model.PlatformSettings
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.storage
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.io.File

data class AdminDashboardSnapshot(
    val apps: List<AppListing> = emptyList(),
    val versions: List<AppVersion> = emptyList(),
    val submissions: List<AppSubmission> = emptyList(),
    val categories: List<MarketplaceCategory> = emptyList(),
    val users: List<UserProfile> = emptyList(),
    val developers: List<DeveloperProfile> = emptyList(),
    val reports: List<AppReport> = emptyList(),
    val reviews: List<AppReview> = emptyList(),
    val logs: List<AdminLog> = emptyList(),
    val settings: PlatformSettings = PlatformSettings()
)

class AdminRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    companion object {
        const val BOOTSTRAPPED_ADMIN_EMAIL = "monicaeg6wbestewart@gmail.com"
    }

    suspend fun verifyAdminAccess(user: FirebaseUser?): Boolean {
        if (user == null) return false
        if (user.email.equals(BOOTSTRAPPED_ADMIN_EMAIL, ignoreCase = true) && user.isEmailVerified) {
            return true
        }
        return try {
            val tokenResult = user.getIdToken(false).await()
            if (tokenResult.claims["admin"] == true) return true
            val adminDoc = db.collection("admins").document(user.uid).get().await()
            adminDoc.exists()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("AdminRepository", "Admin verification check failed: ${e.message}")
            false
        }
    }

    suspend fun loadDashboardSnapshot(): Result<AdminDashboardSnapshot> {
        val currentUser = auth.currentUser
            ?: return Result.failure(IllegalStateException("Sign in required."))
        if (!verifyAdminAccess(currentUser)) {
            return Result.failure(SecurityException("Administrator privileges required."))
        }
        return try {
            coroutineScope {
                val appsDef = async { db.collection("apps").get().await() }
                val versionsDef = async { db.collection("appVersions").get().await() }
                val subsDef = async { db.collection("appSubmissions").get().await() }
                val catsDef = async { db.collection("categories").get().await() }
                val usersDef = async { db.collection("users").get().await() }
                val devsDef = async { db.collection("developerProfiles").get().await() }
                val reportsDef = async { db.collection("reports").get().await() }
                val reviewsDef = async { db.collection("reviews").get().await() }
                val logsDef = async {
                    db.collection("adminLogs")
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .limit(30)
                        .get()
                        .await()
                }
                val settingsDef = async {
                    db.collection("platformSettings").document("global").get().await()
                }

                val apps = appsDef.await().toObjects(AppListing::class.java)
                    .sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
                val versions = versionsDef.await().toObjects(AppVersion::class.java)
                val subs = subsDef.await().toObjects(AppSubmission::class.java)
                val cats = catsDef.await().toObjects(MarketplaceCategory::class.java)
                    .sortedBy { it.sortOrder }
                val users = usersDef.await().toObjects(UserProfile::class.java)
                    .sortedByDescending { it.lastLoginAt?.seconds ?: 0L }
                val devs = devsDef.await().toObjects(DeveloperProfile::class.java)
                val reports = reportsDef.await().toObjects(AppReport::class.java)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
                val reviews = reviewsDef.await().toObjects(AppReview::class.java)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
                val logs = logsDef.await().toObjects(AdminLog::class.java)
                val settings = settingsDef.await().toObject(PlatformSettings::class.java) ?: PlatformSettings()

                Result.success(
                    AdminDashboardSnapshot(
                        apps = apps,
                        versions = versions,
                        submissions = subs,
                        categories = cats,
                        users = users,
                        developers = devs,
                        reports = reports,
                        reviews = reviews,
                        logs = logs,
                        settings = settings
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "admin_dashboard")
            Result.failure(e)
        }
    }

    suspend fun approveApp(
        app: AppListing,
        version: AppVersion?,
        submission: AppSubmission?
    ): Result<String?> {
        return try {
            var resolvedUrl = version?.apkDownloadUrl?.trim().orEmpty()
                .takeIf { it.startsWith("https://") || it.startsWith("http://") }
            if (resolvedUrl == null && version != null) {
                resolvedUrl = tryResolveStorageUrl(app, version)
            }

            val batch = db.batch()
            val appRef = db.collection("apps").document(app.appId.ifBlank { app.id })
            batch.update(
                appRef,
                mapOf(
                    "status" to "approved",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val versionId = submission?.versionId?.takeIf { it.isNotBlank() }
                ?: version?.versionId?.takeIf { it.isNotBlank() }
                ?: version?.id.orEmpty()
            if (versionId.isNotBlank()) {
                val versionUpdates = mutableMapOf<String, Any>(
                    "approvalStatus" to "approved",
                    "validationStatus" to "passed"
                )
                if (!resolvedUrl.isNullOrBlank() && resolvedUrl.length <= 1000) {
                    versionUpdates["apkDownloadUrl"] = resolvedUrl
                }
                batch.update(db.collection("appVersions").document(versionId), versionUpdates)
            }
            val subId = submission?.id.orEmpty()
            if (subId.isNotBlank()) {
                batch.update(
                    db.collection("appSubmissions").document(subId),
                    mapOf(
                        "status" to "approved",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()

            val appId = app.appId.ifBlank { app.id }
            runCatching {
                val mediaDocs = db.collection("appMedia")
                    .whereEqualTo("appId", appId)
                    .get()
                    .await()
                if (!mediaDocs.isEmpty) {
                    val mediaBatch = db.batch()
                    mediaDocs.documents.forEach { doc ->
                        mediaBatch.update(doc.reference, "publicMedia", true)
                    }
                    mediaBatch.commit().await()
                }
            }

            if (app.ownerUid.isNotBlank()) {
                db.collection("notifications").add(
                    mapOf(
                        "userId" to app.ownerUid,
                        "type" to "app_approved",
                        "title" to "${app.appName} Approved",
                        "body" to "Your application is now live on APP STORE.",
                        "targetAppId" to appId,
                        "read" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            logAction("APPROVE_APP", "apps", appId)
            Result.success(resolvedUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "apps/${app.appId}")
            Result.failure(e)
        }
    }

    suspend fun rejectApp(
        app: AppListing,
        version: AppVersion?,
        submission: AppSubmission?,
        reason: String
    ): Result<Unit> {
        val cleanReason = reason.trim().ifBlank { "Does not meet APP STORE guidelines." }.take(1000)
        return try {
            val batch = db.batch()
            val appId = app.appId.ifBlank { app.id }
            batch.update(
                db.collection("apps").document(appId),
                mapOf("status" to "rejected", "updatedAt" to FieldValue.serverTimestamp())
            )
            val versionId = submission?.versionId?.takeIf { it.isNotBlank() }
                ?: version?.versionId?.takeIf { it.isNotBlank() }
                ?: version?.id.orEmpty()
            if (versionId.isNotBlank()) {
                batch.update(
                    db.collection("appVersions").document(versionId),
                    mapOf("approvalStatus" to "rejected")
                )
            }
            val subId = submission?.id.orEmpty()
            if (subId.isNotBlank()) {
                batch.update(
                    db.collection("appSubmissions").document(subId),
                    mapOf(
                        "status" to "rejected",
                        "rejectionReason" to cleanReason,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()

            if (app.ownerUid.isNotBlank()) {
                db.collection("notifications").add(
                    mapOf(
                        "userId" to app.ownerUid,
                        "type" to "app_rejected",
                        "title" to "${app.appName} Needs Changes",
                        "body" to "Reason: $cleanReason",
                        "targetAppId" to appId,
                        "read" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            logAction("REJECT_APP", "apps", appId, cleanReason)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "apps/${app.appId}")
            Result.failure(e)
        }
    }

    suspend fun setAppStatus(appId: String, status: String, notes: String = ""): Result<Unit> {
        return try {
            db.collection("apps").document(appId).update(
                mapOf(
                    "status" to status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAction("SET_APP_STATUS_${status.uppercase()}", "apps", appId, notes)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "apps/$appId")
            Result.failure(e)
        }
    }

    suspend fun toggleAppFlag(appId: String, field: String, newValue: Boolean): Result<Unit> {
        return try {
            db.collection("apps").document(appId).update(
                mapOf(
                    field to newValue,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAction("TOGGLE_${field.uppercase()}", "apps", appId, newValue.toString())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "apps/$appId")
            Result.failure(e)
        }
    }

    suspend fun resolveAndPersistApkLink(app: AppListing, version: AppVersion): Result<String> {
        return try {
            val direct = version.apkDownloadUrl.trim()
            if (direct.startsWith("https://") || direct.startsWith("http://")) {
                return Result.success(direct)
            }
            val resolved = tryResolveStorageUrl(app, version)
                ?: return Result.failure(IllegalStateException("No downloadable APK found in Firebase Storage. Use 'Attach APK / URL' to upload an APK file or set a direct download link."))
            val verId = version.versionId.ifBlank { version.id }
            if (verId.isNotBlank() && resolved.length <= 1000) {
                db.collection("appVersions").document(verId)
                    .update("apkDownloadUrl", resolved)
                    .await()
            }
            Result.success(resolved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setVersionApkUrl(
        app: AppListing,
        version: AppVersion?,
        rawUrl: String
    ): Result<String> {
        val normalized = DeveloperPublishRepository.normalizeDirectApkUrl(rawUrl)
        if (!normalized.startsWith("https://") && !normalized.startsWith("http://")) {
            return Result.failure(IllegalArgumentException("Please enter a valid http:// or https:// APK download link."))
        }
        if (normalized.length > 1000) {
            return Result.failure(IllegalArgumentException("APK URL must be 1000 characters or shorter."))
        }
        return try {
            val appId = app.appId.ifBlank { app.id }
            val existingVerId = version?.let { it.versionId.ifBlank { it.id } }
                ?.takeIf { it.isNotBlank() }
                ?: db.collection("appVersions")
                    .whereEqualTo("appId", appId)
                    .get()
                    .await()
                    .documents
                    .firstOrNull()
                    ?.id

            if (!existingVerId.isNullOrBlank()) {
                val updates = mutableMapOf<String, Any>("apkDownloadUrl" to normalized)
                if (app.status.equals("approved", ignoreCase = true)) {
                    updates["approvalStatus"] = "approved"
                    updates["validationStatus"] = "passed"
                }
                db.collection("appVersions").document(existingVerId).update(updates).await()
            } else {
                val newVerId = "${appId}_v${app.versionCode.coerceAtLeast(1L)}"
                val owner = app.ownerUid.ifBlank { auth.currentUser?.uid.orEmpty() }
                db.collection("appVersions").document(newVerId).set(
                    mapOf(
                        "versionId" to newVerId,
                        "appId" to appId,
                        "ownerUid" to owner,
                        "packageName" to app.packageName,
                        "versionName" to app.versionName.ifBlank { "1.0" },
                        "versionCode" to app.versionCode.coerceAtLeast(1L),
                        "apkStoragePath" to "public/apks/$appId/$newVerId/${app.packageName}.apk",
                        "apkDownloadUrl" to normalized,
                        "apkSize" to app.apkSizeBytes.coerceAtLeast(0L),
                        "sha256" to "",
                        "signingCertificateFingerprint" to "",
                        "minSdk" to app.minSdk.coerceAtLeast(21L),
                        "targetSdk" to app.targetSdk.coerceAtLeast(21L),
                        "permissions" to emptyList<String>(),
                        "validationStatus" to "pending",
                        "scanStatus" to "pending",
                        "approvalStatus" to "pending",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                if (app.status.equals("approved", ignoreCase = true)) {
                    db.collection("appVersions").document(newVerId).update(
                        mapOf("approvalStatus" to "approved", "validationStatus" to "passed")
                    ).await()
                }
            }
            logAction("SET_APK_URL", "apps", appId, normalized.take(200))
            Result.success(normalized)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "appVersions/${app.appId}")
            Result.failure(e)
        }
    }

    suspend fun uploadAndAttachApkForVersion(
        app: AppListing,
        version: AppVersion?,
        parsedApk: ParsedApkMetadata
    ): Result<String> {
        val file = File(parsedApk.localCachePath)
        if (!file.exists() || file.length() <= 0L) {
            return Result.failure(IllegalStateException("Selected APK file is missing from cache. Pick it again."))
        }
        return try {
            val appId = app.appId.ifBlank { app.id }
            val uploaderUid = auth.currentUser?.uid ?: app.ownerUid
            val safeFileName = DeveloperPublishRepository.sanitizeApkFileName(parsedApk.fileName, parsedApk.packageName)
            val storagePath = "staging/$uploaderUid/$appId/apk/$safeFileName"
            val ref = Firebase.storage.reference.child(storagePath)
            val metadata = storageMetadata { contentType = "application/vnd.android.package-archive" }
            ref.putFile(Uri.fromFile(file), metadata).await()
            val downloadUrl = ref.downloadUrl.await().toString()

            val existingVerId = version?.let { it.versionId.ifBlank { it.id } }?.takeIf { it.isNotBlank() }
            if (!existingVerId.isNullOrBlank()) {
                val updates = mutableMapOf<String, Any>(
                    "apkStoragePath" to storagePath,
                    "apkDownloadUrl" to downloadUrl.take(1000),
                    "apkSize" to parsedApk.fileSizeBytes,
                    "sha256" to parsedApk.sha256,
                    "signingCertificateFingerprint" to parsedApk.signingCertificateFingerprint,
                    "versionName" to parsedApk.versionName.take(60),
                    "versionCode" to parsedApk.versionCode,
                    "minSdk" to parsedApk.minSdk.toLong(),
                    "targetSdk" to parsedApk.targetSdk.toLong(),
                    "permissions" to parsedApk.permissions.take(100)
                )
                if (app.status.equals("approved", ignoreCase = true)) {
                    updates["approvalStatus"] = "approved"
                    updates["validationStatus"] = "passed"
                }
                db.collection("appVersions").document(existingVerId).update(updates).await()
            } else {
                val newVerId = "${appId}_v${parsedApk.versionCode}"
                db.collection("appVersions").document(newVerId).set(
                    mapOf(
                        "versionId" to newVerId,
                        "appId" to appId,
                        "ownerUid" to app.ownerUid.ifBlank { uploaderUid },
                        "packageName" to parsedApk.packageName.take(160),
                        "versionName" to parsedApk.versionName.take(60),
                        "versionCode" to parsedApk.versionCode,
                        "apkStoragePath" to storagePath,
                        "apkDownloadUrl" to downloadUrl.take(1000),
                        "apkSize" to parsedApk.fileSizeBytes,
                        "sha256" to parsedApk.sha256,
                        "signingCertificateFingerprint" to parsedApk.signingCertificateFingerprint,
                        "minSdk" to parsedApk.minSdk.toLong(),
                        "targetSdk" to parsedApk.targetSdk.toLong(),
                        "permissions" to parsedApk.permissions.take(100),
                        "validationStatus" to "pending",
                        "scanStatus" to "pending",
                        "approvalStatus" to "pending",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                if (app.status.equals("approved", ignoreCase = true)) {
                    db.collection("appVersions").document(newVerId).update(
                        mapOf("approvalStatus" to "approved", "validationStatus" to "passed")
                    ).await()
                }
            }

            db.collection("apps").document(appId).update(
                mapOf(
                    "versionName" to parsedApk.versionName.take(60),
                    "versionCode" to parsedApk.versionCode,
                    "apkSizeBytes" to parsedApk.fileSizeBytes,
                    "minSdk" to parsedApk.minSdk.toLong(),
                    "targetSdk" to parsedApk.targetSdk.toLong(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            logAction("UPLOAD_ATTACH_APK", "apps", appId, safeFileName)
            Result.success(downloadUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "appVersions/${app.appId}")
            Result.failure(e)
        }
    }

    private suspend fun tryResolveStorageUrl(app: AppListing, version: AppVersion): String? {
        val candidates = buildList {
            val rawUrl = version.apkDownloadUrl.trim()
            if (rawUrl.startsWith("gs://")) add(rawUrl)
            val storagePath = version.apkStoragePath.trim()
            if (storagePath.isNotBlank()) {
                if (app.status == "approved") {
                    val fileName = storagePath.substringAfterLast('/').ifBlank { "${app.packageName}.apk" }
                    val versionId = version.versionId.ifBlank { version.id }
                    val appId = app.appId.ifBlank { app.id }
                    if (appId.isNotBlank() && versionId.isNotBlank()) {
                        add("public/apks/$appId/$versionId/$fileName")
                    }
                }
                add(storagePath)
            }
        }.distinct()

        for (candidate in candidates) {
            try {
                val ref = if (candidate.startsWith("gs://")) {
                    Firebase.storage.getReferenceFromUrl(candidate)
                } else {
                    Firebase.storage.reference.child(candidate)
                }
                val url = ref.downloadUrl.await().toString()
                if (url.startsWith("https://") || url.startsWith("http://")) return url
            } catch (_: Exception) {
            }
        }
        return null
    }

    suspend fun seedDefaultCategories(): Result<Int> {
        val defaults = listOf(
            CategorySeed("action", "Action", "অ্যাকশন", "games", "sports_esports", 1L),
            CategorySeed("adventure", "Adventure", "অ্যাডভেঞ্চার", "games", "explore", 2L),
            CategorySeed("racing", "Racing", "রেসিং", "games", "directions_car", 3L),
            CategorySeed("puzzle", "Puzzle", "পাজল", "games", "extension", 4L),
            CategorySeed("strategy", "Strategy", "স্ট্র্যাটেজি", "games", "psychology", 5L),
            CategorySeed("arcade", "Arcade", "আর্কেড", "games", "videogame_asset", 6L),
            CategorySeed("sports", "Sports", "স্পোর্টস", "games", "sports_soccer", 7L),
            CategorySeed("education", "Education", "শিক্ষা", "apps", "school", 8L),
            CategorySeed("entertainment", "Entertainment", "বিনোদন", "apps", "theaters", 9L),
            CategorySeed("photography", "Photography", "ফটোগ্রাফি", "apps", "photo_camera", 10L),
            CategorySeed("video_players", "Video Players", "ভিডিও প্লেয়ার", "apps", "smart_display", 11L),
            CategorySeed("music", "Music", "সংগীত", "apps", "music_note", 12L),
            CategorySeed("business", "Business", "ব্যবসা", "apps", "business_center", 13L),
            CategorySeed("productivity", "Productivity", "উৎপাদনশীলতা", "apps", "task_alt", 14L),
            CategorySeed("tools", "Tools", "টুলস", "apps", "build", 15L),
            CategorySeed("lifestyle", "Lifestyle", "লাইফস্টাইল", "apps", "spa", 16L),
            CategorySeed("finance", "Finance", "অর্থনীতি", "apps", "account_balance", 17L),
            CategorySeed("health_fitness", "Health & Fitness", "স্বাস্থ্য ও ফিটনেস", "apps", "fitness_center", 18L),
            CategorySeed("communication", "Communication", "যোগাযোগ", "apps", "forum", 19L),
            CategorySeed("shopping", "Shopping", "শপিং", "apps", "shopping_bag", 20L),
            CategorySeed("news", "News", "সংবাদ", "apps", "newspaper", 21L),
            CategorySeed("travel", "Travel", "ভ্রমণ", "apps", "flight", 22L),
            CategorySeed("personalization", "Personalization", "পার্সোনালাইজেশন", "apps", "palette", 23L),
            CategorySeed("other", "Other", "অন্যান্য", "apps", "apps", 24L)
        )
        return try {
            val batch = db.batch()
            defaults.forEach { cat ->
                batch.set(
                    db.collection("categories").document(cat.id),
                    mapOf(
                        "name" to cat.name,
                        "nameBn" to cat.nameBn,
                        "group" to cat.group,
                        "iconName" to cat.iconName,
                        "sortOrder" to cat.sortOrder,
                        "enabled" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            logAction("SEED_CATEGORIES", "categories", "all")
            Result.success(defaults.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "categories")
            Result.failure(e)
        }
    }

    suspend fun addCategory(name: String, nameBn: String, group: String, sortOrder: Long): Result<Unit> {
        val cleanName = name.trim().take(60)
        require(cleanName.isNotBlank()) { "Category name cannot be empty" }
        val id = cleanName.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "cat_${System.currentTimeMillis()}" }
        return try {
            db.collection("categories").document(id).set(
                mapOf(
                    "name" to cleanName,
                    "nameBn" to nameBn.trim().take(80),
                    "group" to if (group == "games") "games" else "apps",
                    "iconName" to if (group == "games") "sports_esports" else "apps",
                    "sortOrder" to sortOrder.coerceIn(0L, 1000L),
                    "enabled" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAction("CREATE_CATEGORY", "categories", id)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "categories/$id")
            Result.failure(e)
        }
    }

    suspend fun toggleCategory(categoryId: String, enabled: Boolean): Result<Unit> {
        return try {
            db.collection("categories").document(categoryId).update(
                mapOf("enabled" to enabled, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            logAction("TOGGLE_CATEGORY", "categories", categoryId, enabled.toString())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "categories/$categoryId")
            Result.failure(e)
        }
    }

    suspend fun deleteCategory(categoryId: String): Result<Unit> {
        return try {
            db.collection("categories").document(categoryId).delete().await()
            logAction("DELETE_CATEGORY", "categories", categoryId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "categories/$categoryId")
            Result.failure(e)
        }
    }

    suspend fun setUserStatus(uid: String, status: String): Result<Unit> {
        return try {
            db.collection("users").document(uid).update(
                mapOf("accountStatus" to status, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            logAction("USER_${status.uppercase()}", "users", uid)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun setDeveloperStatus(uid: String, status: String): Result<Unit> {
        return try {
            val batch = db.batch()
            batch.update(
                db.collection("developerProfiles").document(uid),
                mapOf("status" to status, "updatedAt" to FieldValue.serverTimestamp())
            )
            batch.update(
                db.collection("users").document(uid),
                mapOf("developerStatus" to status, "updatedAt" to FieldValue.serverTimestamp())
            )
            batch.commit().await()
            logAction("DEV_${status.uppercase()}", "developerProfiles", uid)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "developerProfiles/$uid")
            Result.failure(e)
        }
    }

    suspend fun resolveReport(reportId: String, status: String): Result<Unit> {
        return try {
            db.collection("reports").document(reportId).update(
                mapOf("status" to status, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            logAction("REPORT_${status.uppercase()}", "reports", reportId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "reports/$reportId")
            Result.failure(e)
        }
    }

    suspend fun removeReview(reviewId: String): Result<Unit> {
        return try {
            db.collection("reviews").document(reviewId).update(
                mapOf("moderationStatus" to "removed", "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            logAction("REMOVE_REVIEW", "reviews", reviewId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "reviews/$reviewId")
            Result.failure(e)
        }
    }

    suspend fun sendAnnouncement(title: String, body: String, targetUid: String = ""): Result<Unit> {
        val cleanTitle = title.trim().take(120)
        val cleanBody = body.trim().take(500)
        require(cleanTitle.isNotBlank() && cleanBody.isNotBlank()) { "Title and message are required." }
        return try {
            val target = targetUid.trim()
            if (target.isNotBlank()) {
                db.collection("notifications").add(
                    mapOf(
                        "userId" to target,
                        "type" to "announcement",
                        "title" to cleanTitle,
                        "body" to cleanBody,
                        "read" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                logAction("SEND_NOTIFICATION", "notifications", target)
            } else {
                db.collection("announcements").add(
                    mapOf(
                        "title" to cleanTitle,
                        "body" to cleanBody,
                        "active" to true,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                logAction("SEND_ANNOUNCEMENT", "announcements", "global")
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "announcements")
            Result.failure(e)
        }
    }

    suspend fun savePlatformSettings(
        platformName: String,
        supportEmail: String,
        maxUploadMb: Long,
        maintenanceMode: Boolean
    ): Result<Unit> {
        return try {
            db.collection("platformSettings").document("global").set(
                mapOf(
                    "platformName" to platformName.trim().ifBlank { "APP STORE" }.take(60),
                    "supportEmail" to supportEmail.trim().take(200),
                    "maxUploadSizeMb" to maxUploadMb.coerceIn(1L, 2048L),
                    "allowedExtensions" to listOf("apk"),
                    "maintenanceMode" to maintenanceMode,
                    "monetizationEnabled" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
            logAction("UPDATE_SETTINGS", "platformSettings", "global")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "platformSettings/global")
            Result.failure(e)
        }
    }

    private suspend fun logAction(action: String, targetType: String, targetId: String, notes: String = "") {
        val adminUid = auth.currentUser?.uid ?: "unknown"
        runCatching {
            db.collection("adminLogs").add(
                mapOf(
                    "adminUid" to adminUid,
                    "action" to action,
                    "targetType" to targetType,
                    "targetId" to targetId,
                    "result" to "success",
                    "notes" to notes.take(500),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    private data class CategorySeed(
        val id: String,
        val name: String,
        val nameBn: String,
        val group: String,
        val iconName: String,
        val sortOrder: Long
    )
}
