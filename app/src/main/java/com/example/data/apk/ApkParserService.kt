package com.example.data.apk

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.example.data.model.ParsedApkMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

class ApkParserService(private val context: Context) {

    suspend fun parseAndValidateApk(uri: Uri): Result<ParsedApkMetadata> = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri).orEmpty()
            val fileName = resolveFileName(uri) ?: "app_package.apk"

            if (!fileName.endsWith(".apk", ignoreCase = true)) {
                errors.add("File extension must be .apk (found $fileName)")
            }
            if (mimeType.isNotBlank() &&
                mimeType != "application/vnd.android.package-archive" &&
                mimeType != "application/octet-stream" &&
                mimeType != "application/zip"
            ) {
                errors.add("Unsupported MIME type: $mimeType")
            }

            val cacheDir = File(context.cacheDir, "apk_staging").apply { mkdirs() }
            val tempFile = File(cacheDir, "inspect_${System.currentTimeMillis()}.apk")

            val shaDigest = MessageDigest.getInstance("SHA-256")
            var totalBytes = 0L

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        shaDigest.update(buffer, 0, read)
                        totalBytes += read
                    }
                }
            } ?: return@withContext Result.failure(IllegalStateException("Unable to read selected APK file."))

            if (totalBytes <= 0L) {
                errors.add("APK file is empty (0 bytes)")
            }
            if (totalBytes > 250L * 1024L * 1024L) {
                errors.add("APK exceeds maximum 250 MB upload size")
            }

            val sha256Hex = shaDigest.digest().joinToString("") { "%02x".format(it) }

            var hasManifest = false
            var hasDexOrResources = false
            try {
                ZipFile(tempFile).use { zip ->
                    hasManifest = zip.getEntry("AndroidManifest.xml") != null
                    hasDexOrResources = zip.getEntry("classes.dex") != null || zip.getEntry("resources.arsc") != null
                }
            } catch (e: Exception) {
                errors.add("Invalid APK archive structure: ${e.localizedMessage ?: "corrupt zip"}")
            }

            if (!hasManifest) errors.add("Missing AndroidManifest.xml inside APK")
            if (!hasDexOrResources) errors.add("Missing classes.dex or resources.arsc inside APK")

            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_PERMISSIONS
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES or PackageManager.GET_PERMISSIONS
            }

            val pkgInfo: PackageInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(tempFile.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(tempFile.absolutePath, flags)
            }

            if (pkgInfo == null) {
                errors.add("Android PackageManager could not parse package archive metadata")
            }

            val packageName = pkgInfo?.packageName?.takeIf { it.isNotBlank() } ?: run {
                errors.add("Missing Android package name")
                "unknown.package"
            }
            val versionName = pkgInfo?.versionName?.takeIf { it.isNotBlank() } ?: "1.0.0"
            val versionCode = if (pkgInfo != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkgInfo.longVersionCode
                else @Suppress("DEPRECATION") pkgInfo.versionCode.toLong()
            } else 1L

            val appInfo = pkgInfo?.applicationInfo
            val minSdk = if (appInfo != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                appInfo.minSdkVersion
            } else 24
            val targetSdk = appInfo?.targetSdkVersion ?: 35

            val permissions = pkgInfo?.requestedPermissions?.toList() ?: emptyList()

            val certFingerprint = extractSigningFingerprint(pkgInfo)
            if (certFingerprint.isBlank()) {
                errors.add("APK has no readable signing certificate (unsigned APKs cannot be distributed)")
            }

            Result.success(
                ParsedApkMetadata(
                    fileName = fileName,
                    localCachePath = tempFile.absolutePath,
                    fileSizeBytes = totalBytes,
                    packageName = packageName,
                    versionName = versionName,
                    versionCode = versionCode.coerceAtLeast(1L),
                    minSdk = minSdk.coerceAtLeast(1),
                    targetSdk = targetSdk.coerceAtLeast(1),
                    permissions = permissions,
                    sha256 = sha256Hex,
                    signingCertificateFingerprint = certFingerprint,
                    validStructure = errors.isEmpty(),
                    validationErrors = errors
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractSigningFingerprint(pkgInfo: PackageInfo?): String {
        if (pkgInfo == null) return ""
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.signingInfo?.apkContentsSigners ?: pkgInfo.signingInfo?.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures
        }
        val firstSig = signatures?.firstOrNull() ?: return ""
        val digest = MessageDigest.getInstance("SHA-256").digest(firstSig.toByteArray())
        return digest.joinToString(":") { "%02X".format(it) }
    }

    private fun resolveFileName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }
}
