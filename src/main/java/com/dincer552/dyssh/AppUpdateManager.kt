package com.dincer552.dyssh

import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class ReleaseUpdate(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String? = null
)
data class DownloadSnapshot(val status: Int, val progressPercent: Int)

/** GitHub Release updater. Never installs an APK before checking package, version and signer. */
object AppUpdateManager {
    private const val RELEASE_API = "https://api.github.com/repos/dincer552/dyssh/releases/latest"
    private const val APK_ASSET_NAME = "dyssh.apk"
    private const val MAX_APK_BYTES = 150L * 1024L * 1024L

    internal fun parseReleaseVersionCode(tagName: String): Int? {
        val match = Regex("^v?\\d+\\.\\d+\\.(\\d+)$").matchEntire(tagName.trim()) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    internal fun isReleaseApkAsset(name: String): Boolean = name == APK_ASSET_NAME

    suspend fun checkForUpdate(): ReleaseUpdate? = withContext(Dispatchers.IO) {
        val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "DYSSH-Android")
        }
        try {
            when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_NOT_FOUND -> return@withContext null
                in 200..299 -> Unit
                HttpURLConnection.HTTP_FORBIDDEN, HttpURLConnection.HTTP_TOO_MANY_REQUESTS ->
                    throw IOException("GitHub güncelleme sınırına ulaşıldı (HTTP $status). Daha sonra tekrar deneyin.")
                else -> throw IOException("GitHub güncelleme sunucusu HTTP $status döndürdü.")
            }
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val tag = release.optString("tag_name")
            val versionCode = parseReleaseVersionCode(tag)
                ?: throw IOException("Sürüm etiketi okunamadı: $tag")
            if (versionCode <= BuildConfig.VERSION_CODE) return@withContext null

            val assets = release.optJSONArray("assets") ?: throw IOException("Release APK dosyası eksik.")
            val asset = (0 until assets.length()).asSequence()
                .map { assets.getJSONObject(it) }
                .firstOrNull { isReleaseApkAsset(it.optString("name")) }
                ?: throw IOException("Release içinde dyssh.apk bulunamadı.")
            val assetUrl = asset.optString("browser_download_url")
                .takeIf { it.startsWith("https://") }
                ?: throw IOException("Release APK indirme adresi geçersiz.")
            val digestValue = asset.optString("digest").takeIf { it.startsWith("sha256:") }
            val digest = digestValue?.removePrefix("sha256:")?.lowercase()
                ?.takeIf { it.matches(Regex("[a-f0-9]{64}")) }
            ReleaseUpdate(versionCode, tag.removePrefix("v"), assetUrl, digest)
        } finally {
            connection.disconnect()
        }
    }

    fun enqueueDownload(context: Context, update: ReleaseUpdate): Long {
        val request = DownloadManager.Request(Uri.parse(update.apkUrl))
            .setTitle("DYSSH güncellemesi")
            .setDescription("${update.versionName} sürümü indiriliyor")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(
                context, Environment.DIRECTORY_DOWNLOADS, "dyssh-${update.versionCode}.apk"
            )
        return (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }

    fun cancelDownload(context: Context, id: Long) {
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(id)
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
     * Copies the provider URI into a bounded cache file and validates content digest (when
     * provided), exact Android package/version and the signing certificate before installer UI.
     */
    fun verifyDownloadedApk(context: Context, uri: Uri, update: ReleaseUpdate): Result<Unit> = runCatching {
        val temp = File.createTempFile("dyssh-update-", ".apk", context.cacheDir)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("İndirilen APK dosyası açılamadı.")
            input.use { source ->
                temp.outputStream().buffered().use { target ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        total += count
                        if (total > MAX_APK_BYTES) throw IOException("APK izin verilen boyuttan büyük.")
                        digest.update(buffer, 0, count)
                        target.write(buffer, 0, count)
                    }
                }
            }
            if (total <= 0) throw IOException("İndirilen APK boş.")
            val actualDigest = digest.digest().joinToString("") { "%02x".format(it) }
            if (update.sha256 != null && !actualDigest.equals(update.sha256, ignoreCase = true)) {
                throw IOException("APK SHA-256 doğrulaması başarısız.")
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val archive = context.packageManager.getPackageArchiveInfo(temp.absolutePath, flags)
                ?: throw IOException("İndirilen dosya geçerli bir Android APK değil.")
            if (archive.packageName != context.packageName) {
                throw IOException("APK paket kimliği uyuşmuyor: ${archive.packageName}")
            }
            val archiveVersion = packageVersionCode(archive)
            if (archiveVersion != update.versionCode.toLong()) {
                throw IOException("APK sürümü beklenen sürümle eşleşmiyor.")
            }

            val installed = context.packageManager.getPackageInfo(context.packageName, flags)
            val archiveSigners = signerFingerprints(archive)
            val installedSigners = signerFingerprints(installed)
            if (archiveSigners.isEmpty() || installedSigners.isEmpty() || archiveSigners != installedSigners) {
                throw IOException("APK imza sertifikası kurulu DYSSH sürümüyle eşleşmiyor.")
            }
        } finally {
            temp.delete()
        }
    }

    private fun packageVersionCode(info: PackageInfo): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    private fun signerFingerprints(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        } ?: return emptySet()
        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }.toSet()
    }
}
