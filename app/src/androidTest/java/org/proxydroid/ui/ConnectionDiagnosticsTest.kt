package org.proxydroid.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

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
    @Test fun busyButtonsDoNotLaunchDuplicateChecks() {
        compose.setContent { MaterialTheme { ConnectionDiagnostics(MainUiState(diagnosticRunning = true), {}, {}, {}) } }
        compose.onNodeWithTag("check-proxy-internet").assertIsNotEnabled()
        compose.onNodeWithTag("check-youtube-proxy").assertIsNotEnabled()
    }
}
