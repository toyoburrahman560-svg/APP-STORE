package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.AppReview
import com.example.data.model.DownloadRecord
import com.example.data.model.LanguagePreference
import com.example.data.model.ThemePreference
import com.example.data.model.UserProfile
import com.example.data.preferences.UserSettingsState
import com.example.ui.components.AppIconBox
import com.example.ui.components.AuthGateCard
import com.example.ui.components.PoliciesModalDialog
import com.example.ui.components.StatusChip
import com.example.ui.components.formatBytes
import com.example.ui.components.formatTimestamp
import com.google.firebase.auth.FirebaseUser

@Composable
fun ProfileScreen(
    currentUser: FirebaseUser?,
    userProfile: UserProfile?,
    settings: UserSettingsState,
    wishlistCount: Int,
    downloadsCount: Int,
    activeDownloadsCount: Int,
    downloadHistory: List<DownloadRecord>,
    unreadNotifications: Int,
    myReviews: List<AppReview>,
    authLoading: Boolean,
    authError: String?,
    onGoogleSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onSetTheme: (ThemePreference) -> Unit,
    onSetLanguage: (LanguagePreference) -> Unit,
    onUpdateNotificationPref: (Boolean?, Boolean?, Boolean?, Boolean?) -> Unit,
    onOpenMyDownloads: () -> Unit,
    onOpenApp: (String) -> Unit,
    onOpenMyApps: () -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenWishlistLibrary: () -> Unit,
    onOpenNotifications: () -> Unit
) {
    var showPolicies by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMyReviewsDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (currentUser == null) {
            item {
                AuthGateCard(
                    title = "Sign in to APP STORE",
                    body = "Access your wishlist, download history, reviews, developer tools, and personalized notifications.",
                    isLoading = authLoading,
                    errorMessage = authError,
                    onSignInClick = onGoogleSignIn
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    ProfileNavRow(
                        icon = Icons.Default.DownloadForOffline,
                        title = stringResource(R.string.my_downloads),
                        subtitle = if (activeDownloadsCount > 0) "$activeDownloadsCount downloading • $downloadsCount records"
                        else "Manage APK downloads & installations",
                        onClick = onOpenMyDownloads
                    )
                }
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        val photo = userProfile?.photoURL?.takeIf { it.isNotBlank() } ?: currentUser.photoUrl?.toString()
                        if (!photo.isNullOrBlank()) {
                            AsyncImage(
                                model = photo,
                                contentDescription = "Profile photo",
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = userProfile?.displayName?.ifBlank { currentUser.displayName } ?: "App Store User",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = userProfile?.email?.ifBlank { currentUser.email } ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusChip(status = "dev: ${userProfile?.developerStatus ?: "none"}")
                                StatusChip(status = userProfile?.accountStatus ?: "active")
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column {
                        ProfileNavRow(
                            icon = Icons.Default.DownloadForOffline,
                            title = stringResource(R.string.my_downloads),
                            subtitle = if (activeDownloadsCount > 0) "$activeDownloadsCount active • ${downloadHistory.size} history"
                            else "${downloadHistory.size} downloaded • pause, resume & install",
                            onClick = onOpenMyDownloads
                        )
                        HorizontalDivider()
                        ProfileNavRow(Icons.Default.Bookmark, stringResource(R.string.wishlist) + " & " + stringResource(R.string.library), "$wishlistCount saved • $downloadsCount downloads", onOpenWishlistLibrary)
                        HorizontalDivider()
                        ProfileNavRow(Icons.Default.Apps, stringResource(R.string.my_apps), "Manage submitted & published APKs", onOpenMyApps)
                        HorizontalDivider()
                        ProfileNavRow(Icons.Default.Analytics, stringResource(R.string.developer_dashboard), "Real downloads, views & rating trends", onOpenDashboard)
                        HorizontalDivider()
                        ProfileNavRow(Icons.Default.RateReview, "My Reviews", "${myReviews.size} submitted reviews") { showMyReviewsDialog = true }
                        HorizontalDivider()
                        ProfileNavRow(Icons.Default.Notifications, stringResource(R.string.notifications), "$unreadNotifications unread", onOpenNotifications)
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Download History", style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = onOpenMyDownloads) {
                                Text("Manage All")
                            }
                        }
                        if (downloadHistory.isEmpty()) {
                            Text(
                                text = "Your completed and in-progress APK downloads appear here in real time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            downloadHistory.take(4).forEachIndexed { idx, rec ->
                                if (idx > 0) HorizontalDivider()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenApp(rec.appId) }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    AppIconBox(iconUrl = rec.iconUrl, appName = rec.appName, size = 40.dp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = rec.appName,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val sizeInfo = if (rec.fileSizeBytes > 0L) " • ${formatBytes(rec.fileSizeBytes)}" else ""
                                        Text(
                                            text = "v${rec.versionName}$sizeInfo • ${formatTimestamp(rec.updatedAt ?: rec.createdAt)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    StatusChip(status = rec.status)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Theme & Language (available for both guest & signed-in)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleSmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemePreference.entries.forEach { pref ->
                            FilterChip(
                                selected = settings.themePreference == pref,
                                onClick = { onSetTheme(pref) },
                                label = { Text(pref.name.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }

                    HorizontalDivider()

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.language), style = MaterialTheme.typography.titleSmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LanguagePreference.entries.forEach { lang ->
                            FilterChip(
                                selected = settings.languagePreference == lang,
                                onClick = { onSetLanguage(lang) },
                                label = { Text(lang.displayName) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Notification Preferences", style = MaterialTheme.typography.titleSmall)
                    TogglePrefRow("App approvals & updates", settings.notifyAppUpdates) {
                        onUpdateNotificationPref(it, null, null, null)
                    }
                    TogglePrefRow("Developer review replies", settings.notifyReviewReplies) {
                        onUpdateNotificationPref(null, it, null, null)
                    }
                    TogglePrefRow("Marketplace announcements", settings.notifyAnnouncements) {
                        onUpdateNotificationPref(null, null, it, null)
                    }
                    TogglePrefRow("Account security events", settings.notifySecurityAlerts) {
                        onUpdateNotificationPref(null, null, null, it)
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                ProfileNavRow(
                    Icons.Default.Policy,
                    stringResource(R.string.policies),
                    "Privacy, Terms, Malware, Copyright & Appeals"
                ) { showPolicies = true }
            }
        }

        if (currentUser != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onSignOut,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("sign_out_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.sign_out))
                    }
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.delete_account))
                    }
                }
            }
        }
    }

    if (showPolicies) {
        PoliciesModalDialog(onDismiss = { showPolicies = false })
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Account?") },
            text = { Text("This permanently deletes your APP STORE account profile and signs you out. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteAccount()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete Account") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showMyReviewsDialog) {
        AlertDialog(
            onDismissRequest = { showMyReviewsDialog = false },
            title = { Text("My Reviews (${myReviews.size})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (myReviews.isEmpty()) {
                        Text("You haven't written any reviews yet.")
                    } else {
                        myReviews.forEach { r ->
                            Text("• ${r.appId} (${r.rating}★): ${r.comment}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMyReviewsDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun ProfileNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
private fun TogglePrefRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
