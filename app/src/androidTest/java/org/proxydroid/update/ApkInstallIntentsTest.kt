package org.proxydroid.update
import android.content.Intent
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
class ApkInstallIntentsTest {
    @Test fun installerAndViewFallbackHaveReadableContentUriWithoutNewTask() {
        val uri = Uri.parse("content://org.proxydroid.update-files/verified-updates/update.apk")
        for (action in listOf(Intent.ACTION_INSTALL_PACKAGE, Intent.ACTION_VIEW)) {
            val intent = ApkInstallIntents.create(uri, action)
            assertEquals(uri, intent.data)
            assertEquals("application/vnd.android.package-archive", intent.type)
            assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(0, intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsRawFileUri() {
        ApkInstallIntents.create(Uri.parse("file:///sdcard/update.apk"), Intent.ACTION_VIEW)
    }
}
