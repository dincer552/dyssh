package com.dincer552.dyssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun parsesSupportedReleaseTags() {
        assertEquals(42, AppUpdateManager.parseReleaseVersionCode("v1.0.42"))
        assertEquals(42, AppUpdateManager.parseReleaseVersionCode("1.0.42"))
    }

    @Test
    fun rejectsMalformedOrOverflowingReleaseTags() {
        assertNull(AppUpdateManager.parseReleaseVersionCode(""))
        assertNull(AppUpdateManager.parseReleaseVersionCode("latest"))
        assertNull(AppUpdateManager.parseReleaseVersionCode("v1.42"))
        assertNull(AppUpdateManager.parseReleaseVersionCode("v1.0.beta"))
        assertNull(AppUpdateManager.parseReleaseVersionCode("v1.0.999999999999999999"))
    }

    @Test
    fun acceptsOnlyTheStoreExpectedReleaseAsset() {
        assertTrue(AppUpdateManager.isReleaseApkAsset("dyssh.apk"))
        assertFalse(AppUpdateManager.isReleaseApkAsset("DYSSH.apk"))
        assertFalse(AppUpdateManager.isReleaseApkAsset("debug.apk"))
        assertFalse(AppUpdateManager.isReleaseApkAsset("dyssh.apk.exe"))
    }
}
