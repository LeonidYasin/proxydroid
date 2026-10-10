package org.proxydroid.update

import org.junit.Assert.*
import org.junit.Test

class ReleaseCatalogTest {
    private val name = "proxydroid-v3.7.3-android-tv-phone-universal-release-signed-prerelease-build-42-a1-g0123456789ab.apk"
    private fun asset(name: String, bytes: Int = 10): String = """{"name":"$name","browser_download_url":"https://github.com/LeonidYasin/proxydroid/releases/download/v3.7.3-rc/$name","size":$bytes,"state":"uploaded"}"""
    private fun release(pre: Boolean, draft: Boolean = false, assets: String = asset(if (pre) name else name.replace("-prerelease-", "-stable-")) + "," + asset((if (pre) name else name.replace("-prerelease-", "-stable-")).removeSuffix(".apk") + ".build-info.json")): String =
        """{"tag_name":"v3.7.3-rc","name":"release","draft":$draft,"prerelease":$pre,"published_at":"2026-10-10T00:00:00Z","assets":[$assets]}"""

    @Test fun channelsAreSeparateAndDraftsAreExcluded() {
        val json = "[${release(true)},${release(false)},${release(true, true)}]"
        assertEquals(1, ReleaseCatalog.offers(json, UpdateChannel.TEST, listOf("arm64-v8a")).size)
        assertEquals(1, ReleaseCatalog.offers(json, UpdateChannel.STABLE, listOf("arm64-v8a")).size)
        assertTrue(ReleaseCatalog.offers("[${release(false, assets = asset(name))}]", UpdateChannel.STABLE, listOf("arm64-v8a")).isEmpty())
    }
    @Test fun prefersArm32OnlyWhenDeviceSupportsIt() {
        val arm = name.replace("-universal-", "-armeabi-v7a-")
        val assets = listOf(asset(name), asset(name.removeSuffix(".apk") + ".build-info.json"),
            asset(arm, 5), asset(arm.removeSuffix(".apk") + ".build-info.json")).joinToString(",")
        val json = "[${release(true, assets = assets)}]"
        assertEquals(arm, ReleaseCatalog.offers(json, UpdateChannel.TEST, listOf("arm64-v8a", "armeabi-v7a")).single().apk.name)
        assertEquals(name, ReleaseCatalog.offers(json, UpdateChannel.TEST, listOf("arm64-v8a")).single().apk.name)
    }
    @Test fun refusesDebugMissingMetadataAndUntrustedUrls() {
        assertTrue(ReleaseCatalog.offers("[${release(true, assets = asset("app-debug.apk"))}]", UpdateChannel.TEST, listOf("armeabi-v7a")).isEmpty())
        assertTrue(ReleaseCatalog.offers("[${release(true, assets = asset(name))}]", UpdateChannel.TEST, listOf("armeabi-v7a")).isEmpty())
        assertFalse(ReleaseCatalog.trustedAssetUrl("http://github.com/LeonidYasin/proxydroid/releases/download/x/y"))
        assertFalse(ReleaseCatalog.trustedAssetUrl("https://github.com.evil.test/LeonidYasin/proxydroid/releases/download/x/y"))
        assertFalse(ReleaseCatalog.trustedAssetUrl("https://github.com/another/repo/releases/download/x/y"))
    }
    private fun metadata(code: Int = 30703, sdk: Int = 24, digest: String = "a".repeat(64)): String = """{
        "application_id":"org.proxydroid","apk_filename":"$name","variant":"release","signing":"release",
        "release_channel":"prerelease","version_code":$code,"min_sdk":$sdk,"abi_variant":"universal","verified_native_abis":["arm64-v8a","armeabi-v7a","x86","x86_64"],
        "apk_sha256":"$digest","apk_bytes":10,"commit":"0123456789abcdef0123456789abcdef01234567","version":"3.7.3","run_id":"42"} """
    @Test fun validatesActualMetadataAndRejectsDowngradeWrongSdkAndChecksum() {
        val offer = ReleaseCatalog.offers("[${release(true)}]", UpdateChannel.TEST, listOf("arm64-v8a")).single()
        assertEquals(30703L, ReleaseCatalog.readBuild(offer, metadata(), listOf("arm64-v8a"), 30, 30702).versionCode)
        assertThrows(IllegalArgumentException::class.java) { ReleaseCatalog.readBuild(offer, metadata(30701), listOf("arm64-v8a"), 30, 30702) }
        assertThrows(IllegalArgumentException::class.java) { ReleaseCatalog.readBuild(offer, metadata(sdk = 35), listOf("arm64-v8a"), 30, 30702) }
        assertThrows(IllegalArgumentException::class.java) { ReleaseCatalog.readBuild(offer, metadata(digest = "bad"), listOf("arm64-v8a"), 30, 30702) }
    }
}
