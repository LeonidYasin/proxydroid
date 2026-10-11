package org.proxydroid.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class MainScreenTvTest {
    @get:Rule val compose = createComposeRule()
    @Test fun initialRemoteFocusIsOnConnection() {
        compose.setContent {
            MaterialTheme {
                MainScreen(MainUiState(), {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithTag("connection-row").assertIsFocused()
        assertFalse(compose.onNodeWithTag("connection-toggle", useUnmergedTree = true)
            .fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false })
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
    @Test fun dpadReturnRevealsEntireConnectionRowAndHeaderStaysFixed() {
        compose.setContent { MaterialTheme { MainScreen(MainUiState(), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) } }
        val header = compose.onNodeWithTag("app-top-bar").getUnclippedBoundsInRoot()
        val initial = compose.onNodeWithTag("connection-row").getUnclippedBoundsInRoot()
        repeat(14) { compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) } }
        assertEquals(header, compose.onNodeWithTag("app-top-bar").getUnclippedBoundsInRoot())
        assertTrue("DPAD really scrolled connection row offscreen",
            compose.onNodeWithTag("connection-row").getUnclippedBoundsInRoot().top < header.bottom)
        var returned = false
        for (i in 0 until 40) {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
            val focus = compose.onNodeWithTag("connection-row").fetchSemanticsNode().config
            if (focus.getOrElse(SemanticsProperties.Focused) { false }) { returned = true; break }
        }
        assertTrue("DPAD returns focus to whole connection row", returned)
        compose.onNodeWithTag("connection-row").assertIsFocused().assertIsDisplayed()
        val row = compose.onNodeWithTag("connection-row").getUnclippedBoundsInRoot()
        val viewport = compose.onNodeWithTag("main-scroll").getUnclippedBoundsInRoot()
        assertTrue("Full title/row below pinned header", row.top >= header.bottom)
        assertTrue("Row bottom within scroll viewport", row.bottom <= viewport.bottom)
        assertEquals("Full row height retained", (initial.bottom-initial.top).value, (row.bottom-row.top).value, 1f)
    }
    @Test fun dpadOkOnConnectionRowTogglesExactlyOnce() {
        var calls = 0
        var requested = false
        compose.setContent { MaterialTheme { MainScreen(MainUiState(), { calls++; requested = it }, {}, {}, {}, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithTag("connection-row").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, calls); assertTrue(requested) }
    }
    @Test fun dpadCanStopIndefiniteNetworkWaiting() {
        var calls = 0; var requested = true
        compose.setContent { MaterialTheme {
            MainScreen(MainUiState(isConnecting = true), { calls++; requested = it }, {}, {}, {}, {}, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithTag("connection-row").assertIsFocused().assertIsDisplayed()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, calls); assertFalse(requested) }
    }

}
