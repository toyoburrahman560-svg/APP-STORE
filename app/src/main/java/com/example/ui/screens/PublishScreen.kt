package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.DeveloperProfile
import com.example.data.model.MarketplaceCategory
import com.example.data.model.ParsedApkMetadata
import com.example.data.model.UserProfile
import com.example.data.repository.PublishSubmissionDraft
import com.example.ui.components.AuthGateCard
import com.example.ui.components.StatusChip
import com.example.ui.components.formatBytes
import com.example.ui.theme.EmeraldTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishScreen(
    currentUserUid: String?,
    userEmail: String?,
    userDisplayName: String?,
    userProfile: UserProfile?,
    developerProfile: DeveloperProfile?,
    categories: List<MarketplaceCategory>,
    parsedApk: ParsedApkMetadata?,
    parsingApk: Boolean,
    submitting: Boolean,
    uploadProgress: Float? = null,
    actionMessage: String?,
    authLoading: Boolean,
    authError: String?,
    onGoogleSignIn: () -> Unit,
    onRegisterDeveloper: (String, String, String, String, String, (Result<Unit>) -> Unit) -> Unit,
    onInspectApk: (Uri) -> Unit,
    onSubmitPublish: (PublishSubmissionDraft, (String) -> Unit) -> Unit,
    onOpenMyApps: () -> Unit,
    onOpenDashboard: () -> Unit,
    onClearMessage: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionMessage) {
        if (!actionMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(actionMessage)
            onClearMessage()
        }
    }

    if (currentUserUid == null) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AuthGateCard(
                title = "Developer & Publishing Hub",
                body = "Sign in with Google to register a developer profile, upload APKs, and manage your published applications.",
                isLoading = authLoading,
                errorMessage = authError,
                onSignInClick = onGoogleSignIn
            )
        }
        return
    }

    var showWizard by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Developer Console", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Publish Android APKs, track admin verification, and view marketplace analytics.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Quick Actions Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenMyApps,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_my_apps_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("My Apps")
                    }
                    OutlinedButton(
                        onClick = onOpenDashboard,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_dev_dashboard_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Analytics")
                    }
                }
            }

            if (developerProfile == null) {
                item {
                    DeveloperRegistrationCard(
                        defaultName = userDisplayName.orEmpty(),
                        defaultEmail = userEmail.orEmpty(),
                        onSubmit = { name, desc, email, web, privacy ->
                            onRegisterDeveloper(name, desc, email, web, privacy) {}
                        }
                    )
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(developerProfile.displayName, style = MaterialTheme.typography.titleMedium)
                                    Text(developerProfile.contactEmail, style = MaterialTheme.typography.bodySmall)
                                }
                                StatusChip(status = developerProfile.status)
                            }
                            if (developerProfile.status == "pending") {
                                Text(
                                    "Your developer verification is pending admin approval. You can prepare and submit app releases for admin review right away.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else if (developerProfile.status == "suspended") {
                                Text(
                                    "Your developer account is currently suspended. Contact support to appeal.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            if (!showWizard && developerProfile.status != "suspended") {
                                Button(
                                    onClick = { showWizard = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("publish_your_app_button"),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.publish_your_app))
                                }
                            }
                        }
                    }
                }

                if (showWizard && developerProfile.status != "suspended") {
                    item {
                        PublishAppWizard(
                            categories = categories,
                            defaultDevName = developerProfile.displayName,
                            defaultEmail = developerProfile.contactEmail,
                            defaultWebsite = developerProfile.websiteUrl,
                            defaultPrivacy = developerProfile.privacyPolicyUrl,
                            parsedApk = parsedApk,
                            parsingApk = parsingApk,
                            submitting = submitting,
                            uploadProgress = uploadProgress,
                            onInspectApk = onInspectApk,
                            onSubmit = { draft ->
                                onSubmitPublish(draft) {
                                    showWizard = false
                                    onOpenMyApps()
                                }
                            },
                            onCancel = { showWizard = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeveloperRegistrationCard(
    defaultName: String,
    defaultEmail: String,
    onSubmit: (String, String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(defaultName) }
    var desc by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(defaultEmail) }
    var website by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Register Developer Account", style = MaterialTheme.typography.titleLarge)
            Text(
                "Create your publisher profile to submit Android applications to APP STORE.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Developer / Studio Name *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Support Email *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Developer Bio") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = website, onValueChange = { website = it }, label = { Text("Website URL") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = privacy, onValueChange = { privacy = it }, label = { Text("Privacy Policy URL") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = { onSubmit(name, desc, email, website, privacy) },
                enabled = name.trim().length >= 2 && email.contains("@"),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("register_developer_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Create Developer Profile")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PublishAppWizard(
    categories: List<MarketplaceCategory>,
    defaultDevName: String,
    defaultEmail: String,
    defaultWebsite: String,
    defaultPrivacy: String,
    parsedApk: ParsedApkMetadata?,
    parsingApk: Boolean,
    submitting: Boolean,
    uploadProgress: Float?,
    onInspectApk: (Uri) -> Unit,
    onSubmit: (PublishSubmissionDraft) -> Unit,
    onCancel: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) }

    // Step 1
    var appName by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf(categories.firstOrNull()) }
    var catMenuExpanded by remember { mutableStateOf(false) }
    var shortDesc by remember { mutableStateOf("") }
    var fullDesc by remember { mutableStateOf("") }
    var devName by remember { mutableStateOf(defaultDevName) }
    var contactEmail by remember { mutableStateOf(defaultEmail) }
    var websiteUrl by remember { mutableStateOf(defaultWebsite) }

    // Step 2
    var apkExternalUrl by remember { mutableStateOf("") }
    var whatsNew by remember { mutableStateOf("Initial release") }

    // Step 3
    var iconUri by remember { mutableStateOf<Uri?>(null) }
    var iconUrlInput by remember { mutableStateOf("") }
    var featureUri by remember { mutableStateOf<Uri?>(null) }
    var featureUrlInput by remember { mutableStateOf("") }
    val screenshotUris = remember { mutableStateListOf<Uri>() }
    var screenshotUrlsInput by remember { mutableStateOf("") }
    var previewVideoUrl by remember { mutableStateOf("") }

    // Step 4
    val ageRatings = listOf("Everyone", "Everyone 10+", "Teen", "Mature 17+")
    var ageRating by remember { mutableStateOf("Everyone") }
    var contentDesc by remember { mutableStateOf("No objectionable content.") }
    var dataCollection by remember { mutableStateOf("Minimal diagnostics and app preferences only.") }
    var privacyUrl by remember { mutableStateOf(defaultPrivacy.ifBlank { "https://example.com/privacy" }) }
    var copyrightDeclared by remember { mutableStateOf(false) }
    var agreementAccepted by remember { mutableStateOf(false) }

    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onInspectApk)
    }
    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) iconUri = uri
    }
    val featurePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) featureUri = uri
    }
    val screenshotsPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris ->
        screenshotUris.clear()
        screenshotUris.addAll(uris)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Step $step of 5", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    when (step) {
                        1 -> "Basic Info"
                        2 -> "APK File"
                        3 -> "Media"
                        4 -> "Content & Policy"
                        else -> "Review & Submit"
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }
            LinearProgressIndicator(
                progress = { step / 5f },
                modifier = Modifier.fillMaxWidth()
            )

            when (step) {
                1 -> {
                    OutlinedTextField(value = appName, onValueChange = { appName = it }, label = { Text("Application Name *") }, modifier = Modifier.fillMaxWidth())
                    ExposedDropdownMenuBox(expanded = catMenuExpanded, onExpandedChange = { catMenuExpanded = !catMenuExpanded }) {
                        OutlinedTextField(
                            value = selectedCat?.name ?: "Select Category",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catMenuExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(expanded = catMenuExpanded, onDismissRequest = { catMenuExpanded = false }) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text("${cat.name} (${cat.group})") },
                                    onClick = {
                                        selectedCat = cat
                                        catMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(value = shortDesc, onValueChange = { shortDesc = it }, label = { Text("Short Description (<=200 chars) *") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = fullDesc, onValueChange = { fullDesc = it }, label = { Text("Full Description (>=10 chars) *") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = devName, onValueChange = { devName = it }, label = { Text("Developer Name *") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = contactEmail, onValueChange = { contactEmail = it }, label = { Text("Contact Email *") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = websiteUrl, onValueChange = { websiteUrl = it }, label = { Text("Website URL") }, modifier = Modifier.fillMaxWidth())
                }

                2 -> {
                    Text("Select a signed Android .apk using the system file picker. APP STORE inspects the archive structure, package ID, SDK bounds, permissions, signing certificate, and SHA-256 hash.", style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = { apkPicker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.FolderZip, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (parsedApk == null) "Select APK File" else "Replace APK File")
                    }

                    if (parsingApk) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Inspecting APK manifest, certificate, and SHA-256…")
                        }
                    }

                    if (parsedApk != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        imageVector = if (parsedApk.validStructure) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (parsedApk.validStructure) EmeraldTertiary else MaterialTheme.colorScheme.error
                                    )
                                    Text(parsedApk.fileName, style = MaterialTheme.typography.titleSmall)
                                }
                                Text("Package: ${parsedApk.packageName}", style = MaterialTheme.typography.labelSmall)
                                Text("Version: ${parsedApk.versionName} (${parsedApk.versionCode})", style = MaterialTheme.typography.labelSmall)
                                Text("Size: ${formatBytes(parsedApk.fileSizeBytes)} | SDK ${parsedApk.minSdk}–${parsedApk.targetSdk}", style = MaterialTheme.typography.labelSmall)
                                Text("SHA-256: ${parsedApk.sha256.take(28)}…", style = MaterialTheme.typography.labelSmall)
                                Text("Signer: ${parsedApk.signingCertificateFingerprint.take(28)}…", style = MaterialTheme.typography.labelSmall)
                                Text("Permissions: ${parsedApk.permissions.size}", style = MaterialTheme.typography.labelSmall)
                                parsedApk.validationErrors.forEach { err ->
                                    Text("• $err", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = apkExternalUrl,
                        onValueChange = { apkExternalUrl = it },
                        label = { Text("Direct HTTPS APK URL (optional if uploading APK file)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = whatsNew,
                        onValueChange = { whatsNew = it },
                        label = { Text("Release Notes / What's New") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                3 -> {
                    Text("Select visual media via the zero-permission Android Photo Picker or supply hosted HTTPS asset URLs.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { iconPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (iconUri == null) "Pick Icon" else "Icon Ready")
                        }
                        OutlinedButton(
                            onClick = { featurePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (featureUri == null) "Feature Art" else "Feature Ready")
                        }
                    }
                    OutlinedButton(
                        onClick = { screenshotsPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Pick Screenshots (${screenshotUris.size} selected)")
                    }
                    if (screenshotUris.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(screenshotUris) { uri ->
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Selected screenshot",
                                    modifier = Modifier
                                        .size(width = 72.dp, height = 120.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                    OutlinedTextField(value = iconUrlInput, onValueChange = { iconUrlInput = it }, label = { Text("Public Icon HTTPS URL (optional)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = featureUrlInput, onValueChange = { featureUrlInput = it }, label = { Text("Feature Graphic HTTPS URL (optional)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = screenshotUrlsInput, onValueChange = { screenshotUrlsInput = it }, label = { Text("Screenshot HTTPS URLs (comma-separated)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = previewVideoUrl, onValueChange = { previewVideoUrl = it }, label = { Text("Optional Preview Video URL") }, modifier = Modifier.fillMaxWidth())
                }

                4 -> {
                    Text("Age Rating", style = MaterialTheme.typography.titleSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ageRatings) { rating ->
                            FilterChip(
                                selected = ageRating == rating,
                                onClick = { ageRating = rating },
                                label = { Text(rating) }
                            )
                        }
                    }
                    OutlinedTextField(value = contentDesc, onValueChange = { contentDesc = it }, label = { Text("Content Questionnaire Description") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = dataCollection, onValueChange = { dataCollection = it }, label = { Text("Data Collection Declaration") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = privacyUrl, onValueChange = { privacyUrl = it }, label = { Text("Privacy Policy URL *") }, modifier = Modifier.fillMaxWidth())
                    if (parsedApk != null) {
                        Text("Detected Permissions (${parsedApk.permissions.size}): ${parsedApk.permissions.take(5).joinToString()}", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = copyrightDeclared, onCheckedChange = { copyrightDeclared = it })
                        Text("I own or hold distribution rights for this APK and all media.", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = agreementAccepted, onCheckedChange = { agreementAccepted = it })
                        Text("I accept the APP STORE Developer Agreement and Malware Policy.", style = MaterialTheme.typography.bodySmall)
                    }
                }

                else -> {
                    Text("Review Submission Preview", style = MaterialTheme.typography.titleMedium)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("App: $appName", style = MaterialTheme.typography.titleSmall)
                            Text("Category: ${selectedCat?.name ?: "Other"} | Rating: $ageRating", style = MaterialTheme.typography.bodySmall)
                            Text("Package: ${parsedApk?.packageName ?: "Missing"}", style = MaterialTheme.typography.labelSmall)
                            Text("Version: ${parsedApk?.versionName} (${parsedApk?.versionCode})", style = MaterialTheme.typography.labelSmall)
                            Text("APK Size: ${formatBytes(parsedApk?.fileSizeBytes ?: 0L)}", style = MaterialTheme.typography.labelSmall)
                            Text("Screenshots: ${screenshotUris.size + screenshotUrlsInput.split(",").count { it.isNotBlank() }}", style = MaterialTheme.typography.bodySmall)
                            Text("Status on Submit: PENDING ADMIN REVIEW (never auto-published)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (submitting) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (uploadProgress != null) {
                                Text(
                                    "Uploading APK ${(uploadProgress * 100).toInt()}%…",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                LinearProgressIndicator(
                                    progress = { uploadProgress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text("Submitting release for review…", style = MaterialTheme.typography.labelMedium)
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = { if (step == 1) onCancel() else step -= 1 },
                    enabled = !submitting
                ) {
                    Text(if (step == 1) "Cancel" else "Back")
                }

                if (step < 5) {
                    val canAdvance = when (step) {
                        1 -> appName.isNotBlank() && fullDesc.trim().length >= 10 && devName.isNotBlank()
                        2 -> parsedApk != null && parsedApk.validStructure
                        4 -> privacyUrl.isNotBlank() && copyrightDeclared && agreementAccepted
                        else -> true
                    }
                    Button(
                        onClick = { step += 1 },
                        enabled = canAdvance
                    ) {
                        Text("Continue")
                    }
                } else {
                    Button(
                        onClick = {
                            val apk = parsedApk ?: return@Button
                            val cat = selectedCat ?: categories.first()
                            val ssUrls = screenshotUrlsInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                            onSubmit(
                                PublishSubmissionDraft(
                                    appName = appName,
                                    categoryId = cat.id,
                                    categoryName = cat.name,
                                    categoryGroup = cat.group,
                                    shortDescription = shortDesc,
                                    fullDescription = fullDesc,
                                    whatsNew = whatsNew,
                                    developerName = devName,
                                    supportContact = contactEmail,
                                    websiteUrl = websiteUrl,
                                    privacyPolicyUrl = privacyUrl,
                                    iconUrl = iconUrlInput,
                                    featureGraphicUrl = featureUrlInput,
                                    screenshotUrls = ssUrls,
                                    previewVideoUrl = previewVideoUrl,
                                    apkMetadata = apk,
                                    apkExternalUrl = apkExternalUrl,
                                    ageRating = ageRating,
                                    contentDescription = contentDesc,
                                    dataCollectionDeclared = dataCollection,
                                    copyrightDeclared = copyrightDeclared,
                                    agreementAccepted = agreementAccepted,
                                    iconUri = iconUri,
                                    featureGraphicUri = featureUri,
                                    screenshotUris = screenshotUris.toList()
                                )
                            )
                        },
                        enabled = !submitting && parsedApk != null,
                        modifier = Modifier.testTag("submit_for_review_button")
                    ) {
                        if (submitting) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(stringResource(R.string.submit_for_review))
                    }
                }
            }
        }
    }
}
