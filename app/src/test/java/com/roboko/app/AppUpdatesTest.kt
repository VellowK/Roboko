package com.roboko.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AppUpdatesTest {
    private fun release(tag: String, draft: Boolean = false, prerelease: Boolean = false): String = JSONObject()
        .put("tag_name", tag).put("draft", draft).put("prerelease", prerelease).put("body", "更新说明")
        .put("assets", JSONArray().put(JSONObject().put("name", "Roboko.apk").put("browser_download_url", "https://example.com/Roboko.apk")))
        .toString()

    @Test fun sameVersionDoesNotOfferDownload() {
        val result = AppUpdates.evaluate(release("v2.0.1"), "2.0.1")
        assertNull(result.version)
        assertNull(result.downloadUrl)
        assertEquals("", result.notes)
        assertEquals("当前已是最新版本", result.status)
    }

    @Test fun olderRemoteDoesNotOfferDowngrade() {
        assertNull(AppUpdates.evaluate(release("v2.0.0"), "2.0.1").version)
        assertNull(AppUpdates.evaluate(release("v2.0.1"), "2.1.0").downloadUrl)
    }

    @Test fun newerPatchOffersReleaseAndAsset() {
        val result = AppUpdates.evaluate(release("v2.0.2"), "2.0.1")
        assertEquals("2.0.2", result.version)
        assertEquals("更新说明", result.notes)
        assertEquals("https://example.com/Roboko.apk", result.downloadUrl)
        assertNull(result.status)
    }

    @Test fun newerMinorAndMajorAreDetected() {
        assertTrue(AppUpdates.isNewer("2.1.0", "2.0.9"))
        assertTrue(AppUpdates.isNewer("3.0.0", "2.99.99"))
        assertFalse(AppUpdates.isNewer("1.99.99", "2.0.1"))
    }

    @Test fun comparesNumbersInsteadOfStrings() {
        assertTrue(AppUpdates.isNewer("2.0.10", "2.0.9"))
        assertFalse(AppUpdates.isNewer("2.0.9", "2.0.10"))
    }

    @Test fun handlesTagPrefixWhitespaceAndBuildMetadata() {
        assertFalse(AppUpdates.isNewer(" V2.0.1+github ", "2.0.1+local"))
        assertFalse(AppUpdates.isNewer("2.0.1", "v2.0.1"))
    }

    @Test fun prereleaseIdentifiersFollowSemanticOrder() {
        assertTrue(AppUpdates.isNewer("2.0.1", "2.0.1-rc.1"))
        assertFalse(AppUpdates.isNewer("2.0.1-rc.1", "2.0.1"))
        assertTrue(AppUpdates.isNewer("2.0.1-rc.10", "2.0.1-rc.9"))
        assertTrue(AppUpdates.isNewer("2.0.1-rc", "2.0.1-beta"))
        assertTrue(AppUpdates.isNewer("2.0.1-rc.1", "2.0.1-rc"))
    }

    @Test fun excludesDraftsAndPrereleasesFromStableUpdates() {
        assertNull(AppUpdates.evaluate(release("2.1.0", draft = true), "2.0.1").version)
        assertNull(AppUpdates.evaluate(release("2.1.0", prerelease = true), "2.0.1").version)
        assertNull(AppUpdates.evaluate(release("2.1.0-beta"), "2.0.1").version)
    }

    @Test(expected = IllegalArgumentException::class) fun malformedRemoteFailsRatherThanReportingLatest() {
        AppUpdates.evaluate(release("not-a-version"), "2.0.1")
    }

    @Test(expected = IllegalArgumentException::class) fun missingTagFailsRatherThanReportingLatest() {
        AppUpdates.evaluate("{}", "2.0.1")
    }

    @Test(expected = IllegalArgumentException::class) fun unknownInstalledVersionFailsRatherThanOfferingDownload() {
        AppUpdates.evaluate(release("2.0.2"), "未知")
    }

    @Test fun absentApkDoesNotCreateDownloadUrl() {
        val payload = JSONObject(release("2.0.2")).put("assets", JSONArray()).toString()
        val result = AppUpdates.evaluate(payload, "2.0.1")
        assertEquals("2.0.2", result.version)
        assertNull(result.downloadUrl)
    }
}
