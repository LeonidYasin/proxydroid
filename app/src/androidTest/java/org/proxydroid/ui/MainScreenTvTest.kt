package org.proxydroid.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
}
