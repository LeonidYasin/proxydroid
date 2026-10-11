package org.proxydroid.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.graphics.asAndroidBitmap

@OptIn(ExperimentalTestApi::class)
class ConnectionDiagnosticsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun bothChecksAreSeparateAndAvailableWithoutStartingVpn() {
        var internet = 0; var youtube = 0
        compose.setContent { MaterialTheme { ConnectionDiagnostics(MainUiState(), { internet++ }, { youtube++ }, {}) } }
        compose.onNodeWithTag("check-proxy-internet").performClick()
        compose.onNodeWithTag("check-youtube-proxy").performClick()
        compose.runOnIdle { assertEquals(1, internet); assertEquals(1, youtube) }
    }
    @Test fun busyChecksAreDisabledAndCanBeCancelled() {
        var cancelled = false
        val state = MainUiState(diagnosticRunning = true, diagnosticVisible = true)
        compose.setContent { MaterialTheme {
            ConnectionDiagnostics(state, {}, {}, {})
            ConnectionDiagnosticDialog(state, { cancelled = true }, {})
        } }
        compose.onNodeWithText("Отменить проверку").performClick()
        compose.runOnIdle { assertTrue(cancelled) }
    }
    @Test fun reportHasExplicitTvScrollAndCopyControls() {
        val report = org.proxydroid.utils.DiagnosticReport(org.proxydroid.utils.DiagnosticKind.INTERNET, "test", emptyList(), emptyList())
        compose.setContent { MaterialTheme { ConnectionDiagnosticDialog(MainUiState(diagnosticVisible = true, diagnosticReport = report), {}, {}) } }
        compose.onNodeWithTag("scroll-diagnostic-down").assertIsDisplayed().performClick()
        compose.onNodeWithTag("scroll-diagnostic-up").assertIsDisplayed().performClick()
        compose.onNodeWithTag("copy-diagnostic-report").assertIsDisplayed()
        compose.onNodeWithTag("share-diagnostic-qr").assertIsDisplayed()
    }
    @Test fun localQrCanBeOpenedAndClosedFromTheReport() {
        val report = org.proxydroid.utils.DiagnosticReport(org.proxydroid.utils.DiagnosticKind.INTERNET, "test", emptyList(), emptyList())
        compose.setContent { MaterialTheme { ConnectionDiagnosticDialog(MainUiState(diagnosticVisible = true, diagnosticReport = report), {}, {}) } }
        compose.onNodeWithTag("share-diagnostic-qr").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("diagnostic-share-qr").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("diagnostic-share-qr").assertIsDisplayed()
        compose.onNodeWithText("Закрыть передачу").performClick()
        compose.onNodeWithTag("diagnostic-share-qr").assertDoesNotExist()
        compose.onNodeWithTag("copy-diagnostic-report").assertIsDisplayed()
    }
    @Test fun longReportAllFooterActionsReachableWithDpadOnly() {
        val report = org.proxydroid.utils.DiagnosticReport(org.proxydroid.utils.DiagnosticKind.INTERNET, "test", (1..250).map { "Long diagnostic line $it" }, emptyList())
        compose.setContent { MaterialTheme { ConnectionDiagnosticDialog(MainUiState(diagnosticVisible = true, diagnosticReport = report), {}, {}) } }
        compose.onNodeWithTag("share-diagnostic-qr").assertIsFocused().assertIsDisplayed()
        val panel = compose.onNodeWithTag("diagnostic-actions").getUnclippedBoundsInRoot()
        for (tag in listOf("scroll-diagnostic-up", "scroll-diagnostic-down", "close-diagnostic-report", "copy-diagnostic-report", "share-diagnostic-qr")) {
            val button = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue("Whole $tag within fixed panel", button.left >= panel.left && button.right <= panel.right && button.top >= panel.top && button.bottom <= panel.bottom)
        }
        saveScreen("diagnostic-dpad-report.png")
        compose.onNodeWithTag("share-diagnostic-qr").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("copy-diagnostic-report").assertIsFocused().assertIsDisplayed()
        compose.onNodeWithTag("copy-diagnostic-report").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("scroll-diagnostic-up").assertIsFocused().assertIsDisplayed()
        compose.onNodeWithTag("scroll-diagnostic-up").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("scroll-diagnostic-down").assertIsFocused().assertIsDisplayed()
        compose.onNodeWithTag("scroll-diagnostic-down").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("close-diagnostic-report").assertIsFocused().assertIsDisplayed()
        compose.onNodeWithTag("close-diagnostic-report").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("share-diagnostic-qr").assertIsFocused().assertIsDisplayed()
        compose.onNodeWithTag("share-diagnostic-qr").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("diagnostic-share-qr").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("close-diagnostic-share").assertIsFocused().assertIsDisplayed()
        val popup = compose.onNodeWithTag("diagnostic-share-dialog").getUnclippedBoundsInRoot()
        val qr = compose.onNodeWithTag("diagnostic-share-qr").getUnclippedBoundsInRoot()
        assertTrue("Whole QR within popup", qr.left >= popup.left && qr.right <= popup.right && qr.top >= popup.top && qr.bottom <= popup.bottom)
        compose.onNodeWithTag("diagnostic-share-qr").assertIsDisplayed()
        compose.waitForIdle()
        val rendered = compose.onNodeWithTag("diagnostic-share-qr").captureToImage().asAndroidBitmap()
        assertTrue("QR has useful nonzero rendered dimensions", rendered.width >= 120 && rendered.height >= 120)
        val pixels = IntArray(rendered.width * rendered.height)
        rendered.getPixels(pixels, 0, rendered.width, 0, 0, rendered.width, rendered.height)
        val scan = com.google.zxing.MultiFormatReader().decode(com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(com.google.zxing.RGBLuminanceSource(rendered.width, rendered.height, pixels))))
        assertTrue("Camera-readable actual rendered QR", scan.text.startsWith("http://") && scan.text.endsWith("/diagnostics.txt"))
        saveScreen("diagnostic-dpad-qr.png")
        compose.onNodeWithTag("close-diagnostic-share").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("diagnostic-share-qr").assertDoesNotExist()
        compose.onNodeWithTag("share-diagnostic-qr").assertIsFocused()
    }
    @Test fun footerNeverScrollsWithTheLongReport() {
        val report = org.proxydroid.utils.DiagnosticReport(org.proxydroid.utils.DiagnosticKind.INTERNET, "test", (1..250).map { "Long diagnostic line $it" }, emptyList())
        compose.setContent { MaterialTheme { ConnectionDiagnosticDialog(MainUiState(diagnosticVisible = true, diagnosticReport = report), {}, {}) } }
        val before = compose.onNodeWithTag("diagnostic-actions").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("share-diagnostic-qr").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("scroll-diagnostic-down").performKeyInput { repeat(4) { pressKey(Key.DirectionCenter) } }
        compose.waitForIdle()
        assertEquals(before, compose.onNodeWithTag("diagnostic-actions").getUnclippedBoundsInRoot())
        compose.onNodeWithTag("scroll-diagnostic-down").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("share-diagnostic-qr").assertIsFocused().assertIsDisplayed()
    }
    private fun saveScreen(name: String) {
        compose.waitForIdle()
        // Hardware screenshot can trail the semantics tree by a frame. This is
        // capture synchronization only; key/focus tests do not use this delay.
        Thread.sleep(300)
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        val bitmap = checkNotNull(automation.takeScreenshot())
        val bytes = java.io.ByteArrayOutputStream()
        assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bytes))
        val encoded = android.util.Base64.encodeToString(bytes.toByteArray(), android.util.Base64.NO_WRAP)
        val path = "/data/local/tmp/$name"
        val ifs = "$" + "{IFS}"
        fun shell(script: String) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("sh -c $script")).use { it.readBytes() }
        }
        shell(":>$path.b64")
        for (chunk in encoded.chunked(16_384)) shell("echo$ifs'$chunk'>>$path.b64")
        shell("base64$ifs-d<$path.b64>$path;rm$ifs$path.b64")
        bitmap.recycle()
    }
    @Test fun busyButtonsDoNotLaunchDuplicateChecks() {
        compose.setContent { MaterialTheme { ConnectionDiagnostics(MainUiState(diagnosticRunning = true), {}, {}, {}) } }
        compose.onNodeWithTag("check-proxy-internet").assertIsNotEnabled()
        compose.onNodeWithTag("check-youtube-proxy").assertIsNotEnabled()
    }
}
