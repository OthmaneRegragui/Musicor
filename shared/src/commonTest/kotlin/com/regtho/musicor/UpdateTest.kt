package com.regtho.musicor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateTest {

    @Test
    fun parseVersionHandlesPrefixAndSuffix() {
        assertEquals(listOf(1, 2, 3), parseVersion("v1.2.3"))
        assertEquals(listOf(1, 2), parseVersion("1.2"))
        assertEquals(listOf(1, 2), parseVersion("1.2-beta1"))
        assertEquals(listOf(2, 0, 0), parseVersion("2.0.0-rc1"))
    }

    @Test
    fun compareVersionsOrdersCorrectly() {
        assertTrue(compareVersions("1.0.0", "1.0.1") < 0)
        assertTrue(compareVersions("v1.0.1", "1.0.0") > 0)
        assertEquals(0, compareVersions("1.0", "1.0.0"))
        assertTrue(compareVersions("2.0", "1.9.9") > 0)
        assertEquals(0, compareVersions("1.2.3-beta1", "1.2.3"))
        assertEquals(0, compareVersions("1.2.3", "v1.2.3"))
    }

    @Test
    fun pickAssetReturnsMatchingExtensionInPriorityOrder() {
        val release = GithubRelease(
            tagName = "v1.1.0",
            assets = listOf(
                GithubAsset("musicor-android.apk", "https://example.com/apk", 100),
                GithubAsset("musicor-linux-amd64.AppImage", "https://example.com/appimage", 200),
                GithubAsset("com.regtho.musicor_1.1.0_amd64.deb", "https://example.com/deb", 300),
            ),
        )
        assertEquals("https://example.com/appimage", pickAsset(release, listOf("AppImage", "deb"))!!.browserDownloadUrl)
        assertEquals("https://example.com/deb", pickAsset(release, listOf("deb"))!!.browserDownloadUrl)
        assertNull(pickAsset(release, listOf("exe")))
        assertNull(pickAsset(release, emptyList()))
    }

    @Test
    fun checkerReturnsUpdateWhenNewerReleaseExists() = kotlinx.coroutines.test.runTest {
        val releaseJson = """
            {
                "url": "https://api.github.com/repos/OthmaneRegragui/Musicor/releases/123",
                "id": 123,
                "tag_name": "v1.1.0",
                "html_url": "https://github.com/OthmaneRegragui/Musicor/releases/tag/v1.1.0",
                "name": "Musicor 1.1.0",
                "body": "Bug fixes.",
                "assets": [
                    {"name": "musicor-linux-amd64.AppImage", "browser_download_url": "https://example.com/appimage", "size": 96000000},
                    {"name": "musicor-android.apk", "browser_download_url": "https://example.com/apk", "size": 9000000}
                ],
                "draft": false,
                "prerelease": false
            }
        """.trimIndent()
        val checker = UpdateChecker(fetch = { releaseJson })
        val info = checker.check(currentVersion = "1.0.0", extensions = listOf("AppImage", "deb"))
        assertNotNull(info)
        assertEquals("1.1.0", info.version)
        assertEquals("musicor-linux-amd64.AppImage", info.asset!!.fileName)
        assertEquals(96_000_000L, info.asset.sizeBytes)
        assertEquals("Bug fixes.", info.notes)
    }

    @Test
    fun checkerReturnsNullWhenUpToDate() = kotlinx.coroutines.test.runTest {
        val releaseJson = """{"tag_name":"v1.0.0","assets":[]}""".trimIndent()
        val checker = UpdateChecker(fetch = { releaseJson })
        assertNull(checker.check("1.0.0", listOf("AppImage")))
        assertNull(checker.check("v1.0.0", listOf("AppImage")))
        assertNull(checker.check("2.0.0", listOf("AppImage")))
    }

    @Test
    fun checkerReturnsNullOnFetchFailure() = kotlinx.coroutines.test.runTest {
        assertNull(UpdateChecker(fetch = { null }).check("1.0.0", listOf("AppImage")))
        assertNull(UpdateChecker(fetch = { "not json" }).check("1.0.0", listOf("AppImage")))
        assertNull(UpdateChecker(fetch = { """{"tag_name":"v1.1.0","assets":[]}""" }).check("", listOf("AppImage")))
    }
}