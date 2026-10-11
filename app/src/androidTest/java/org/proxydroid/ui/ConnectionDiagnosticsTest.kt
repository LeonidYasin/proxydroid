package org.proxydroid.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.input.key.Key

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
    @Test fun busyButtonsDoNotLaunchDuplicateChecks() {
        compose.setContent { MaterialTheme { ConnectionDiagnostics(MainUiState(diagnosticRunning = true), {}, {}, {}) } }
        compose.onNodeWithTag("check-proxy-internet").assertIsNotEnabled()
        compose.onNodeWithTag("check-youtube-proxy").assertIsNotEnabled()
    }
}
