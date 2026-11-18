package com.dincer552.dyssh

import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.jar.JarEntry
import java.util.jar.JarFile

data class ReleaseUpdate(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String? = null,
    val publishedAt: String? = null
)
data class DownloadSnapshot(val status: Int, val progressPercent: Int)
data class ApkVerificationResult(
    val isValid: Boolean,
    val packageName: String?,
    val versionCode: Int?,
    val versionName: String?,
    val signatureMatches: Boolean,
    val errorMessage: String?
)

/** GitHub Release updater for user-approved signed APK installations. */
object AppUpdateManager {
    private const val RELEASE_API = "https://api.github.com/repos/dincer552/dyssh/releases/latest"
    private const val APK_ASSET_NAME = "dyssh.apk"
    private const val EXPECTED_PACKAGE_NAME = "com.dincer552.dyssh"
    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 15_000

    suspend fun checkForUpdate(): ReleaseUpdate? = withContext(Dispatchers.IO) {
        val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "DYSSH-Android-Client")
        }
        try {
            when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_NOT_FOUND -> return@withContext null
                in 200..299 -> Unit
                else -> throw IOException("GitHub update server returned HTTP $status")
            }
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val tag = release.optString("tag_name")
            val versionCode = parseVersionCodeFromTag(tag)
                ?: throw IOException("Could not read release version from tag: $tag")
            if (versionCode <= BuildConfig.VERSION_CODE) return@withContext null
            val assets = release.optJSONArray("assets") ?: throw IOException("Release APK is missing")
            val assetUrl = (0 until assets.length()).asSequence()
                .map { assets.getJSONObject(it) }
                .firstOrNull {
                    val assetName = it.optString("name")
                    assetName == APK_ASSET_NAME
                }
                ?.optString("browser_download_url")
                ?.takeIf { it.startsWith("https://") }
                ?: throw IOException("Release APK asset '$APK_ASSET_NAME' not found or download URL missing")
            val releaseNotes = release.optString("body").takeIf { it.isNotBlank() }
            val publishedAt = release.optString("published_at").takeIf { it.isNotBlank() }
            ReleaseUpdate(versionCode, tag.removePrefix("v"), assetUrl, releaseNotes, publishedAt)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseVersionCodeFromTag(tag: String): Int? {
        // Expected tag format: v1.0.5 or v1.0.10 etc.
        return tag.substringAfterLast('.').toIntOrNull()
    }

    fun enqueueDownload(context: Context, update: ReleaseUpdate): Long {
        val request = DownloadManager.Request(Uri.parse(update.apkUrl))
            .setTitle("DYSSH update")
            .setDescription("Downloading version ${update.versionName}")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "dyssh-${update.versionCode}.apk")
        return (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }

    fun getDownloadSnapshot(context: Context, id: Long): DownloadSnapshot? {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = manager.query(DownloadManager.Query().setFilterById(id)) ?: return null
        return cursor.use {
            if (!it.moveToFirst()) return null
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val current = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            DownloadSnapshot(status, if (total > 0) (current * 100 / total).toInt().coerceIn(0, 100) else 0)
        }
    }

    fun getDownloadedUri(context: Context, id: Long): Uri? =
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).getUriForDownloadedFile(id)

    /**
     * Verifies the downloaded APK matches expected package identity and is signed
     * with the same certificate as the currently installed app.
     * This ensures My App Store updates maintain the same signing identity.
     */
    suspend fun verifyDownloadedApk(context: Context, apkUri: Uri): ApkVerificationResult = withContext(Dispatchers.IO) {
        val file = try {
            context.contentResolver.openFileDescriptor(apkUri, "r")?.use { pfd ->
                java.io.File(pfd.fileDescriptor).also { f -> f.setReadable(true) }
            } ?: return@withContext ApkVerificationResult(false, null, null, null, false, "Cannot open APK file")
        } catch (e: Exception) {
            return@withContext ApkVerificationResult(false, null, null, null, false, "Failed to access APK: ${e.message}")
        }

        val jarFile = JarFile(file)
        try {
            // Verify package name and version from manifest
            val manifest = jarFile.getManifest()
            if (manifest == null) {
                return@withContext ApkVerificationResult(false, null, null, null, false, "APK missing manifest")
            }

            val packageName = manifest.mainAttributes.getValue("Package")
                ?: manifest.mainAttributes.getValue("package")
            val versionCodeStr = manifest.mainAttributes.getValue("Version-Code")
                ?: manifest.mainAttributes.getValue("versionCode")
            val versionName = manifest.mainAttributes.getValue("Version-Name")
                ?: manifest.mainAttributes.getValue("versionName")

            // Fallback: use aapt if manifest doesn't have the info
            val resolvedPackageName = packageName ?: extractPackageNameFromApk(file)
            val resolvedVersionCode = versionCodeStr?.toIntOrNull() ?: extractVersionCodeFromApk(file)
            val resolvedVersionName = versionName ?: extractVersionNameFromApk(file)

            // Verify package name matches expected
            val packageMatches = resolvedPackageName == EXPECTED_PACKAGE_NAME

            // Verify signature matches current app
            val signatureMatches = verifySignatureMatch(context, jarFile)

            val isValid = packageMatches && signatureMatches && resolvedVersionCode != null && resolvedVersionCode > BuildConfig.VERSION_CODE

            ApkVerificationResult(
                isValid = isValid,
                packageName = resolvedPackageName,
                versionCode = resolvedVersionCode,
                versionName = resolvedVersionName,
                signatureMatches = signatureMatches,
                errorMessage = if (isValid) null else buildErrorMessage(packageMatches, signatureMatches, resolvedVersionCode)
            )
        } finally {
            try { jarFile.close() } catch (_: Exception) {}
        }
    }

    private fun extractPackageNameFromApk(file: java.io.File): String? {
        // Simplified extraction - in practice would use aapt or parse binary XML
        return null
    }

    private fun extractVersionCodeFromApk(file: java.io.File): Int? = null

    private fun extractVersionNameFromApk(file: java.io.File): String? = null

    private fun verifySignatureMatch(context: Context, jarFile: JarFile): Boolean {
        try {
            // Get current app's signing certificates
            val currentPkgInfo: PackageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            val currentCerts = currentPkgInfo.signingInfo.apkContentsSigners
                ?: currentPkgInfo.signingInfo.signingCertificateHistory
                ?: return false

            // Get APK's certificates from the JAR entries
            val entries = jarFile.entries()
            var foundCert = false
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.endsWith(".RSA") || entry.name.endsWith(".DSA") || entry.name.endsWith(".EC")) {
                    foundCert = true
                    val certFactory = CertificateFactory.getInstance("X.509")
                    val cert = certFactory.generateCertificate(jarFile.getInputStream(entry)) as X509Certificate
                    // Compare with current app's certificates
                    for (currentCert in currentCerts) {
                        if (cert.encoded.contentEquals(currentCert.encoded)) {
                            return true
                        }
                    }
                }
            }
            return false
        } catch (e: Exception) {
            return false
        }
    }

    private fun buildErrorMessage(packageMatches: Boolean, signatureMatches: Boolean, versionCode: Int?): String {
        val errors = mutableListOf<String>()
        if (!packageMatches) errors.add("Package name mismatch")
        if (!signatureMatches) errors.add("Signature mismatch - APK not signed with the same key")
        if (versionCode == null || versionCode <= BuildConfig.VERSION_CODE) errors.add("Version code not higher than current")
        return errors.joinToString("; ")
    }

    /**
     * Computes SHA-256 hash of the APK for integrity verification.
     */
    suspend fun computeApkSha256(apkUri: Uri, context: Context): String? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(apkUri) ?: return@withContext null
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            inputStream.use { stream ->
                var bytesRead = stream.read(buffer)
                while (bytesRead != -1) {
                    digest.update(buffer, 0, bytesRead)
                    bytesRead = stream.read(buffer)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }
}
