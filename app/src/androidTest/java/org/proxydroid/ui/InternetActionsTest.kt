package org.proxydroid.ui
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
class InternetActionsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun offersYouTubeOnlyAfterVerifiedConnectedState() {
        var clicked = false
        compose.setContent { MaterialTheme { InternetActions(MainUiState(isWorking = true, internetAvailable = true), { clicked = true }, {}) } }
        compose.onNodeWithTag("open-youtube").assertIsDisplayed().assertIsFocused().performClick()
        compose.runOnIdle { assertTrue(clicked) }
    }
    @Test fun noYouTubeForUnverifiedInternet() {
        compose.setContent { MaterialTheme { InternetActions(MainUiState(isWorking = true, internetAvailable = false), {}, {}) } }
        compose.onNodeWithTag("open-youtube").assertDoesNotExist()
    }
    @Test fun noYouTubeAfterDisconnectEvenIfOldCheckWasSuccessful() {
        compose.setContent { MaterialTheme { InternetActions(MainUiState(isWorking = false, internetAvailable = true), {}, {}) } }
        compose.onNodeWithTag("open-youtube").assertDoesNotExist()
    }
    @Test fun verifiedSuccessMovesRemoteFocusFromConnectionToYouTube() {
        val state = mutableStateOf(MainUiState())
        compose.setContent { MaterialTheme { MainScreen(state.value, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithTag("connection-toggle").assertIsFocused()
        compose.runOnIdle { state.value = MainUiState(isWorking = true, internetChecking = true) }
        compose.onNodeWithTag("open-youtube").assertDoesNotExist()
        compose.runOnIdle { state.value = MainUiState(isWorking = true, internetAvailable = true) }
        compose.onNodeWithTag("open-youtube").assertIsDisplayed().assertIsFocused()
    }
    @Test fun repeatVerificationDoesNotStealFocusAgain() {
        val state = mutableStateOf(MainUiState(isWorking = true, internetAvailable = true))
        compose.setContent { MaterialTheme { MainScreen(state.value, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithTag("open-youtube").assertIsFocused()
        compose.onNodeWithTag("connection-toggle").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("connection-toggle").assertIsFocused()
        compose.runOnIdle { state.value = state.value.copy(internetChecking = true, internetAvailable = null) }
        compose.runOnIdle { state.value = state.value.copy(internetChecking = false, internetAvailable = true) }
        compose.onNodeWithTag("connection-toggle").assertIsFocused()
    }
    @Test fun reconnectOffersFocusAgain() {
        val state = mutableStateOf(MainUiState(isWorking = true, internetAvailable = true))
        compose.setContent { MaterialTheme { MainScreen(state.value, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithTag("open-youtube").assertIsFocused()
        compose.runOnIdle { state.value = MainUiState() }
        compose.onNodeWithTag("open-youtube").assertDoesNotExist()
        compose.runOnIdle { state.value = MainUiState(isWorking = true, internetAvailable = true) }
        compose.onNodeWithTag("open-youtube").assertIsFocused()
    }
}
