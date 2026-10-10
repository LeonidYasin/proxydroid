package org.proxydroid.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MainScreenTvTest {
    @get:Rule val compose = createComposeRule()
    @Test fun initialRemoteFocusIsOnConnection() {
        compose.setContent {
            MaterialTheme {
                MainScreen(MainUiState(), {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithTag("connection-toggle").assertIsFocused()
    }
    @Test fun gatewayErrorIsVisibleWhileDisconnected() {
        val error = "No usable IPv4 default gateway on this network. Host was not changed."
        compose.setContent {
            MaterialTheme {
                MainScreen(MainUiState(lastError = error), {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText(error).assertIsDisplayed()
    }
    @Test fun scrollingKeepsHeaderFixedAndReturnsFirstCardBelowIt() {
        compose.setContent { MaterialTheme { MainScreen(MainUiState(), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) } }
        val bar = compose.onNodeWithTag("app-top-bar").fetchSemanticsNode().boundsInRoot
        val first = compose.onNodeWithTag("connection-card").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("main-scroll").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10000f) }
        compose.waitForIdle()
        assertEquals(bar, compose.onNodeWithTag("app-top-bar").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("main-scroll").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -10000f) }
        compose.waitForIdle()
        compose.onNodeWithTag("connection-card").assertIsDisplayed()
        val restored = compose.onNodeWithTag("connection-card").fetchSemanticsNode().boundsInRoot
        assertTrue("First row under header", restored.top >= bar.bottom)
        assertEquals(first.top, restored.top, 1f)
    }
}
