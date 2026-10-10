package org.proxydroid.ui
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class DownloadedApkLinkTest {
    @get:Rule val compose = createComposeRule()
    private val path = "/data/user/0/org.proxydroid/cache/updates/proxydroid-v3.8.1-android-tv-phone-armeabi-v7a-release-signed-prerelease-build-12345-a1-g123456789abc.apk"
    @Test fun fullPathIsClickableAndPreservedWithoutTruncation() {
        var clicked = false
        compose.setContent { MaterialTheme { DownloadedApkLink(path, true) { clicked = true } } }
        compose.onNodeWithText(path).assertExists()
        compose.onNodeWithTag("downloaded-apk-path").performClick()
        compose.runOnIdle { assertTrue(clicked) }
    }
    @Test fun busyPathDoesNotTriggerInstallation() {
        var clicked = false
        compose.setContent { MaterialTheme { DownloadedApkLink(path, false) { clicked = true } } }
        compose.onNodeWithTag("downloaded-apk-path").performClick()
        compose.runOnIdle { assertFalse(clicked) }
    }
}
