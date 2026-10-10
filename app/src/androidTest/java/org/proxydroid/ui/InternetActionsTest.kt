package org.proxydroid.ui
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
class InternetActionsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun offersYouTubeOnlyAfterVerifiedConnectedState() {
        var clicked = false
        compose.setContent { MaterialTheme { InternetActions(MainUiState(isWorking = true, internetAvailable = true), { clicked = true }, {}) } }
        compose.onNodeWithTag("open-youtube").assertIsDisplayed().performClick()
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
}
