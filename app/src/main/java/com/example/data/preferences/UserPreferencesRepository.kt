package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.LanguagePreference
import com.example.data.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_store_prefs")

data class UserSettingsState(
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val languagePreference: LanguagePreference = LanguagePreference.ENGLISH,
    val notifyAppUpdates: Boolean = true,
    val notifyReviewReplies: Boolean = true,
    val notifyAnnouncements: Boolean = true,
    val notifySecurityAlerts: Boolean = true,
    val recentSearches: List<String> = emptyList(),
    val recentlyViewedAppIds: List<String> = emptyList()
)

class UserPreferencesRepository(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("theme_pref")
        val LANGUAGE = stringPreferencesKey("language_pref")
        val NOTIFY_UPDATES = booleanPreferencesKey("notify_updates")
        val NOTIFY_REPLIES = booleanPreferencesKey("notify_replies")
        val NOTIFY_ANNOUNCEMENTS = booleanPreferencesKey("notify_announcements")
        val NOTIFY_SECURITY = booleanPreferencesKey("notify_security")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        val RECENTLY_VIEWED = stringPreferencesKey("recently_viewed")
    }

    val settingsFlow: Flow<UserSettingsState> = context.appDataStore.data.map { prefs ->
        val theme = ThemePreference.entries.firstOrNull { it.name == prefs[Keys.THEME] } ?: ThemePreference.SYSTEM
        val lang = LanguagePreference.entries.firstOrNull { it.name == prefs[Keys.LANGUAGE] } ?: LanguagePreference.ENGLISH
        val searches = prefs[Keys.RECENT_SEARCHES]?.split("||")?.filter { it.isNotBlank() } ?: emptyList()
        val viewed = prefs[Keys.RECENTLY_VIEWED]?.split("||")?.filter { it.isNotBlank() } ?: emptyList()
        UserSettingsState(
            themePreference = theme,
            languagePreference = lang,
            notifyAppUpdates = prefs[Keys.NOTIFY_UPDATES] ?: true,
            notifyReviewReplies = prefs[Keys.NOTIFY_REPLIES] ?: true,
            notifyAnnouncements = prefs[Keys.NOTIFY_ANNOUNCEMENTS] ?: true,
            notifySecurityAlerts = prefs[Keys.NOTIFY_SECURITY] ?: true,
            recentSearches = searches,
            recentlyViewedAppIds = viewed
        )
    }

    suspend fun setTheme(theme: ThemePreference) {
        context.appDataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setLanguage(language: LanguagePreference) {
        context.appDataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun updateNotificationSetting(
        appUpdates: Boolean? = null,
        reviewReplies: Boolean? = null,
        announcements: Boolean? = null,
        securityAlerts: Boolean? = null
    ) {
        context.appDataStore.edit { prefs ->
            appUpdates?.let { prefs[Keys.NOTIFY_UPDATES] = it }
            reviewReplies?.let { prefs[Keys.NOTIFY_REPLIES] = it }
            announcements?.let { prefs[Keys.NOTIFY_ANNOUNCEMENTS] = it }
            securityAlerts?.let { prefs[Keys.NOTIFY_SECURITY] = it }
        }
    }

    suspend fun addSearchQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        context.appDataStore.edit { prefs ->
            val current = prefs[Keys.RECENT_SEARCHES]?.split("||")?.filter { it.isNotBlank() } ?: emptyList()
            val updated = (listOf(trimmed) + current.filterNot { it.equals(trimmed, ignoreCase = true) }).take(10)
            prefs[Keys.RECENT_SEARCHES] = updated.joinToString("||")
        }
    }

    suspend fun clearSearchHistory() {
        context.appDataStore.edit { it.remove(Keys.RECENT_SEARCHES) }
    }

    suspend fun addRecentlyViewed(appId: String) {
        if (appId.isBlank()) return
        context.appDataStore.edit { prefs ->
            val current = prefs[Keys.RECENTLY_VIEWED]?.split("||")?.filter { it.isNotBlank() } ?: emptyList()
            val updated = (listOf(appId) + current.filterNot { it == appId }).take(15)
            prefs[Keys.RECENTLY_VIEWED] = updated.joinToString("||")
        }
    }
}
