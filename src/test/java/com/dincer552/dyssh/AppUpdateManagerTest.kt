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

        // Test edge cases - function returns last part after last dot as int
        assertEquals(0, parseVersionCodeFromTag("v1.0")) // Returns 0 (last part is "0")
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
            releaseNotes = "Bug fixes"
        )
        assertEquals(5, update.versionCode)
        assertEquals("1.0.5", update.versionName)
        assertEquals("https://github.com/dincer552/dyssh/releases/download/v1.0.5/dyssh.apk", update.apkUrl)
        assertEquals("Bug fixes", update.releaseNotes)
        assertNull(update.assetSha256)
        assertNull(update.publishedAt)
    }
}