package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.AuthRepository
import com.example.data.model.AppReview
import com.example.data.model.DeveloperProfile
import com.example.data.model.DownloadRecord
import com.example.data.model.LanguagePreference
import com.example.data.model.NotificationItem
import com.example.data.model.ThemePreference
import com.example.data.model.UserProfile
import com.example.data.model.WishlistEntry
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.preferences.UserSettingsState
import com.example.data.repository.UserInteractionRepository
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AuthSessionViewModel(
    private val authRepository: AuthRepository,
    private val interactionRepository: UserInteractionRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val currentUser: StateFlow<FirebaseUser?> = authRepository.authStateFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = authRepository.currentUser()
        )

    val settings: StateFlow<UserSettingsState> = preferencesRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UserSettingsState()
        )

    val userProfile: StateFlow<UserProfile?> = currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(null)
            else authRepository.observeUserProfile(user.uid).catch { emit(null) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val developerProfile: StateFlow<DeveloperProfile?> = currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(null)
            else interactionRepository.observeDeveloperProfile(user.uid).catch { emit(null) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val wishlist: StateFlow<List<WishlistEntry>> = currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else interactionRepository.observeWishlist(user.uid).catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val downloadHistory: StateFlow<List<DownloadRecord>> = currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else interactionRepository.observeDownloadHistory(user.uid).catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val notifications: StateFlow<List<NotificationItem>> = currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else interactionRepository.observeNotifications(user.uid).catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val myReviews: StateFlow<List<AppReview>> = currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else interactionRepository.observeMyReviews(user.uid).catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _splashDone = MutableStateFlow(false)
    val splashDone: StateFlow<Boolean> = _splashDone.asStateFlow()

    private val _guestMode = MutableStateFlow(false)
    val guestMode: StateFlow<Boolean> = _guestMode.asStateFlow()

    fun initializeSession(context: Context) {
        if (_splashDone.value) return
        viewModelScope.launch {
            authRepository.attemptAutoSignIn(context)
            _splashDone.value = true
        }
    }

    fun enterGuestMode() {
        _guestMode.value = true
    }

    fun signInWithGoogle(context: Context, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            val res = authRepository.signInWithGoogleInteractive(context)
            _authLoading.value = false
            res.onSuccess {
                _guestMode.value = false
                onSuccess()
            }.onFailure { err ->
                if (err !is androidx.credentials.exceptions.GetCredentialCancellationException) {
                    _authError.value = err.localizedMessage ?: "Google Sign-In failed"
                }
            }
        }
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun signOut(context: Context) {
        authRepository.signOut(context, viewModelScope) {
            _guestMode.value = false
        }
    }

    fun deleteAccount(context: Context, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val res = authRepository.deleteAccount(context)
            onResult(res)
        }
    }

    fun setTheme(theme: ThemePreference) {
        viewModelScope.launch { preferencesRepository.setTheme(theme) }
    }

    fun setLanguage(language: LanguagePreference) {
        viewModelScope.launch { preferencesRepository.setLanguage(language) }
    }

    fun updateNotificationPrefs(
        appUpdates: Boolean? = null,
        reviewReplies: Boolean? = null,
        announcements: Boolean? = null,
        securityAlerts: Boolean? = null
    ) {
        viewModelScope.launch {
            preferencesRepository.updateNotificationSetting(appUpdates, reviewReplies, announcements, securityAlerts)
        }
    }

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch { interactionRepository.markNotificationRead(notificationId) }
    }

    fun recordRecentlyViewed(appId: String) {
        viewModelScope.launch { preferencesRepository.addRecentlyViewed(appId) }
    }

    fun registerDeveloper(
        displayName: String,
        description: String,
        contactEmail: String,
        websiteUrl: String,
        privacyPolicyUrl: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val avatar = currentUser.value?.photoUrl?.toString().orEmpty()
            val res = interactionRepository.registerOrUpdateDeveloperProfile(
                displayName = displayName,
                description = description,
                contactEmail = contactEmail,
                websiteUrl = websiteUrl,
                privacyPolicyUrl = privacyPolicyUrl,
                avatarUrl = avatar
            )
            onResult(res)
        }
    }
}
