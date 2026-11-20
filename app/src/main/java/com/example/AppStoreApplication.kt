package com.example

import android.app.Application
import com.example.data.apk.ApkParserService
import com.example.data.auth.AuthRepository
import com.example.data.download.ApkDownloadService
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.AdminRepository
import com.example.data.repository.DeveloperPublishRepository
import com.example.data.repository.MarketplaceRepository
import com.example.data.repository.UserInteractionRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings

class AppContainer(app: Application) {
    private val databaseId: String = app.getString(R.string.firestore_database_id)

    val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(databaseId).apply {
            firestoreSettings = firestoreSettings {
                setLocalCacheSettings(persistentCacheSettings {
                    setSizeBytes(50L * 1024L * 1024L)
                })
            }
        }
    }

    val preferencesRepository by lazy { UserPreferencesRepository(app) }
    val apkParserService by lazy { ApkParserService(app) }
    val apkDownloadService by lazy { ApkDownloadService(app) }
    val authRepository by lazy { AuthRepository(firestore, Firebase.auth) }
    val marketplaceRepository by lazy { MarketplaceRepository(firestore) }
    val interactionRepository by lazy { UserInteractionRepository(firestore, Firebase.auth) }
    val developerPublishRepository by lazy { DeveloperPublishRepository(firestore, Firebase.auth, app) }
    val adminRepository by lazy { AdminRepository(firestore, Firebase.auth) }
}

class AppStoreApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
