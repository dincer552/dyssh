package com.dincer552.dyssh

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun testParseVersionCodeFromTag() {
        // Test standard version tags
        assertEquals(5, parseVersionCodeFromTag("v1.0.5"))
        assertEquals(10, parseVersionCodeFromTag("v1.0.10"))
        assertEquals(100, parseVersionCodeFromTag("v2.5.100"))
        assertEquals(1, parseVersionCodeFromTag("v0.0.1"))

        // Test edge cases
        assertNull(parseVersionCodeFromTag("v1.0")) // No patch version
        assertNull(parseVersionCodeFromTag("invalid-tag"))
        assertNull(parseVersionCodeFromTag(""))
        assertNull(parseVersionCodeFromTag("v1.0.abc")) // Non-numeric patch
    }

    @Test
    fun testReleaseUpdateDataClass() {
        val update = ReleaseUpdate(
            versionCode = 5,
            versionName = "1.0.5",
            apkUrl = "https://github.com/dincer552/dyssh/releases/download/v1.0.5/dyssh.apk",
            releaseNotes = "Bug fixes",
            publishedAt = "2024-01-15T10:00:00Z",
            assetSha256 = "abc123def456"
        )

        assertEquals(5, update.versionCode)
        assertEquals("1.0.5", update.versionName)
        assertTrue(update.apkUrl.startsWith("https://"))
        assertEquals("Bug fixes", update.releaseNotes)
        assertEquals("2024-01-15T10:00:00Z", update.publishedAt)
        assertEquals("abc123def456", update.assetSha256)
    }

    @Test
    fun testReleaseUpdateDataClassWithoutOptionalFields() {
        val update = ReleaseUpdate(
            versionCode = 5,
            versionName = "1.0.5",
            apkUrl = "https://github.com/dincer552/dyssh/releases/download/v1.0.5/dyssh.apk"
        )

        assertEquals(5, update.versionCode)
        assertEquals("1.0.5", update.versionName)
        assertNull(update.releaseNotes)
        assertNull(update.publishedAt)
        assertNull(update.assetSha256)
    }

    @Test
    fun testApkVerificationResult() {
        val validResult = ApkVerificationResult(
            isValid = true,
            packageName = "com.dincer552.dyssh",
            versionCode = 5,
            versionName = "1.0.5",
            signatureMatches = true,
            sha256Matches = true,
            errorMessage = null
        )

        assertTrue(validResult.isValid)
        assertEquals("com.dincer552.dyssh", validResult.packageName)
        assertEquals(5, validResult.versionCode)
        assertEquals("1.0.5", validResult.versionName)
        assertTrue(validResult.signatureMatches)
        assertTrue(validResult.sha256Matches)
        assertNull(validResult.errorMessage)

        val invalidResult = ApkVerificationResult(
            isValid = false,
            packageName = "com.other.app",
            versionCode = 5,
            versionName = "1.0.5",
            signatureMatches = false,
            sha256Matches = false,
            errorMessage = "Package name mismatch; Signature mismatch"
        )

        assertFalse(invalidResult.isValid)
        assertEquals("com.other.app", invalidResult.packageName)
        assertFalse(invalidResult.signatureMatches)
        assertFalse(invalidResult.sha256Matches)
        assertNotNull(invalidResult.errorMessage)
    }

    @Test
    fun testDownloadSnapshot() {
        val snapshot = DownloadSnapshot(DownloadManager.STATUS_SUCCESSFUL, 100)
        assertEquals(DownloadManager.STATUS_SUCCESSFUL, snapshot.status)
        assertEquals(100, snapshot.progressPercent)

        val partialSnapshot = DownloadSnapshot(DownloadManager.STATUS_RUNNING, 50)
        assertEquals(DownloadManager.STATUS_RUNNING, partialSnapshot.status)
        assertEquals(50, partialSnapshot.progressPercent)
    }

    // Need to make parseVersionCodeFromTag accessible for testing
    companion object {
        // This mirrors the private function in AppUpdateManager
        fun parseVersionCodeFromTag(tag: String): Int? {
            return tag.substringAfterLast('.').toIntOrNull()
        }
    }
}