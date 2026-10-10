package org.proxydroid.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.proxydroid.utils.isValidProxyPort

@OptIn(ExperimentalTestApi::class)
class EditableSettingFieldTest {
    @get:Rule val compose = createComposeRule()

    @Test fun focusDoesNotEditButDpadOkDoes() {
        val focus = FocusRequester()
        compose.setContent {
            MaterialTheme {
                EditableSettingField("10808", {}, "Port", Modifier.focusRequester(focus))
            }
        }
        compose.runOnIdle { focus.requestFocus() }
        compose.onNodeWithTag("setting-Port").assertIsFocused()
        compose.onNodeWithTag("editor-Port").assertDoesNotExist()
        compose.onNodeWithTag("setting-Port").performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        compose.onNodeWithTag("editor-Port").assertIsDisplayed()
    }

    @Test fun cancelDiscardsChangesAndInvalidPortCannotSave() {
        var saved = "10808"
        compose.setContent {
            MaterialTheme {
                EditableSettingField(saved, { saved = it }, "Port", validate = {
                    if (isValidProxyPort(it)) null else "Invalid port"
                })
            }
        }
        compose.onNodeWithTag("setting-Port").performClick()
        compose.onNodeWithTag("editor-Port").performTextReplacement("65536")
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("Invalid port").assertIsDisplayed()
        compose.runOnIdle { assertEquals("10808", saved) }
        compose.onNodeWithTag("editor-Port").performTextReplacement("8080")
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals("10808", saved) }
        compose.onNodeWithTag("setting-Port").performClick()
        compose.onNodeWithTag("editor-Port").performTextReplacement("10809")
        compose.onNodeWithText("OK").performClick()
        compose.runOnIdle { assertEquals("10809", saved) }
    }
}
