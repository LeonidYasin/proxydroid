package org.proxydroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

@Composable
internal fun ConnectionDiagnostics(state: MainUiState, onInternet: () -> Unit, onYouTube: () -> Unit, onShowReport: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("connection-diagnostics")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Диагностика соединения", style = MaterialTheme.typography.titleMedium)
            Text("TCP-порт ≠ рабочий прокси ≠ интернет ≠ видео YouTube", style = MaterialTheme.typography.bodySmall)
            Text("Есть отдельные прямые HTTPS-контрольные запросы без прокси; их успех не засчитывается прокси.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onInternet, enabled = !state.diagnosticRunning, modifier = Modifier.tvRemoteFocus().testTag("check-proxy-internet")) {
                Text("Проверить прокси / интернет")
            }
            TextButton(onClick = onYouTube, enabled = !state.diagnosticRunning, modifier = Modifier.tvRemoteFocus().testTag("check-youtube-proxy")) {
                Text("Проверить YouTube через прокси")
            }
            if (state.diagnosticRunning) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(state.diagnosticProgress ?: "Проверка…", style = MaterialTheme.typography.bodySmall) }
            state.diagnosticReport?.let { report ->
                val proxy = report.results.filter { !it.target.direct }
                Text("${if (report.stale) "Устаревший результат — повторите проверку. " else ""}Через прокси: ${proxy.count { it.success }} / ${proxy.size} целей прошли", style = MaterialTheme.typography.bodySmall)
            }
            if (state.diagnosticReport != null || state.diagnosticRunning || state.diagnosticError != null) {
                TextButton(onClick = onShowReport, modifier = Modifier.tvRemoteFocus().testTag("show-diagnostic-report")) { Text("Этапы, причины и что проверить дальше") }
            }
            if (!state.isWorking) Text("VPN выключен: проверяем настроенный прокси, не TUN. Подключение автоматически не включается.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Fixed action panel: report text never competes with buttons for DPAD focus. */
@Composable
fun ConnectionDiagnosticDialog(state: MainUiState, onCancel: () -> Unit, onDismiss: () -> Unit) {
    if (!state.diagnosticVisible) return
    var sharedReport by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(state.diagnosticRunning) { if (state.diagnosticRunning) sharedReport = null }
    sharedReport?.let { DiagnosticShareDialog(it) { sharedReport = null } }
    val clipboard = LocalClipboardManager.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focus = androidx.compose.runtime.remember { List(6) { androidx.compose.ui.focus.FocusRequester() } }
    val placed = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateListOf(false, false, false, false, false, false) }
    val hasReport = state.diagnosticReport != null && !state.diagnosticRunning
    val bottomLeft = if (state.diagnosticRunning) 5 else if (hasReport) 3 else 0
    val bottomRight = if (state.diagnosticRunning) 5 else if (hasReport) 4 else 1
    fun remote(index: Int): Modifier = Modifier.tvRemoteFocus().focusRequester(focus[index]).onGloballyPositioned { placed[index] = true }.focusProperties {
        when (index) {
            0 -> { left = focus[2]; right = focus[1]; down = focus[bottomLeft]; up = focus[bottomLeft] }
            1 -> { left = focus[0]; right = focus[2]; down = focus[bottomRight]; up = focus[bottomRight] }
            2 -> { left = focus[1]; right = focus[0]; down = focus[bottomRight]; up = focus[bottomRight] }
            3 -> { left = focus[4]; right = focus[4]; up = focus[0]; down = focus[0] }
            4 -> { left = focus[3]; right = focus[3]; up = focus[1]; down = focus[1] }
            5 -> { left = focus[5]; right = focus[5]; up = focus[1]; down = focus[1] }
        }
    }
    val initial = if (state.diagnosticRunning) 5 else if (hasReport) 4 else 2
    val maxHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp - 48.dp).coerceAtLeast(200.dp)
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.runtime.LaunchedEffect(initial, placed[initial], sharedReport == null) {
            if (placed[initial] && sharedReport == null) { androidx.compose.runtime.withFrameNanos { }; focus[initial].requestFocus() }
        }
        androidx.compose.material3.Surface(
            modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth(0.94f).heightIn(max = maxHeight).testTag("diagnostic-dialog"),
            shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Подробная диагностика", style = MaterialTheme.typography.titleLarge)
                Text("Установлено: v${org.proxydroid.BuildConfig.VERSION_NAME} · build ${org.proxydroid.BuildConfig.BUILD_RUN_ID}", style = MaterialTheme.typography.bodySmall)
                Column(Modifier.weight(1f).fillMaxWidth().focusProperties { canFocus = false }
                    .verticalScroll(scroll).testTag("diagnostic-report"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.diagnosticRunning) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(state.diagnosticProgress ?: "Проверка…")
                        Text("До 65 секунд для сетевых этапов; системный DNS может завершаться позже. Отмена закрывает сокеты. Прямые контрольные запросы явно отделены от прокси.", style = MaterialTheme.typography.bodySmall)
                    }
                    state.diagnosticError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    state.diagnosticReport?.let { Text(it.text(), style = MaterialTheme.typography.bodySmall) }
                    if (!state.diagnosticRunning && state.diagnosticReport == null) Text(state.diagnosticProgress ?: "Результата ещё нет.")
                }
                Column(Modifier.fillMaxWidth().testTag("diagnostic-actions"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { scope.launch { scroll.animateScrollTo((scroll.value - 400).coerceAtLeast(0)) } }, modifier = Modifier.weight(1f).then(remote(0)).testTag("scroll-diagnostic-up")) { Text("↑ Вверх") }
                        TextButton(onClick = { scope.launch { scroll.animateScrollTo((scroll.value + 400).coerceAtMost(scroll.maxValue)) } }, modifier = Modifier.weight(1f).then(remote(1)).testTag("scroll-diagnostic-down")) { Text("↓ Вниз") }
                        TextButton(onClick = onDismiss, modifier = Modifier.weight(1f).then(remote(2)).testTag("close-diagnostic-report")) { Text("Закрыть") }
                    }
                    if (state.diagnosticRunning) TextButton(onClick = onCancel, modifier = remote(5).fillMaxWidth().testTag("cancel-diagnostic-report")) { Text("Отменить проверку") }
                    else state.diagnosticReport?.let { report ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { clipboard.setText(AnnotatedString(report.text())) }, modifier = Modifier.weight(1f).then(remote(3)).testTag("copy-diagnostic-report")) { Text("Скопировать отчёт") }
                            TextButton(onClick = { sharedReport = report.text() }, modifier = Modifier.weight(1f).then(remote(4)).testTag("share-diagnostic-qr")) { Text("На смартфон · QR") }
                        }
                    }
                }
            }
        }
    }
}
