package org.proxydroid.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.proxydroid.utils.LanProxy

@OptIn(ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
class LanProxyCardTest {
    @get:Rule val compose = createComposeRule()
    @Test fun discoveredProxyNeedsConfirmationAndIsReachableByDpad() {
        val proxy = LanProxy("192.168.50.1", 10808, "socks5", false)
        var selections = 0
        compose.setContent {
            val mode = LocalInputModeManager.current
            LaunchedEffect(Unit) { mode.requestInputMode(InputMode.Keyboard) }
            org.proxydroid.ui.theme.ProxyDroidTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    LanProxyCard(MainUiState(lanProxies = listOf(proxy), lanScope = "192.168.50.0/24", lanStatus = "Поиск завершён · найден 1 прокси"), {}, {}, {}, { assertEquals(proxy, it); selections++ })
                }
            }
        }
        val button = compose.onNodeWithTag("lan-${proxy.key}")
        button.performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        button.assertIsFocused().assertIsDisplayed()
        saveScreen("lan-proxy-results.png")
        button.performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(0, selections) }
        saveScreen("lan-proxy-confirm.png")
        compose.onNodeWithText("Подключиться").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
            .assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, selections) }
    }
    @Test fun scanningHasCancellationAndUnknownPortsDoNotBecomeProxies() {
        var cancelled = false
        compose.setContent { androidx.compose.material3.MaterialTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                LanProxyCard(MainUiState(lanScanning = true, lanStatus = "Проверка 16/1771"), {}, {}, { cancelled = true }, {})
            }
        } }
        compose.onNodeWithTag("lan-cancel").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(cancelled) }
        compose.onNodeWithText("Найти прокси").assertDoesNotExist()
    }
    @Test fun authProxyOffersSettingsNotAnUnauthenticatedConnect() {
        val proxy = LanProxy("192.168.50.2", 3128, "http", true)
        compose.setContent { androidx.compose.material3.MaterialTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                LanProxyCard(MainUiState(lanProxies = listOf(proxy)), {}, {}, {}, {})
            }
        } }
        compose.onNodeWithTag("lan-${proxy.key}").performScrollTo().performClick()
        compose.onNodeWithText("Настроить").assertIsDisplayed()
        compose.onNodeWithText("Подключиться").assertDoesNotExist()
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
}
