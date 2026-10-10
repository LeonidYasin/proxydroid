package org.proxydroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
            TextButton(onClick = onInternet, enabled = !state.diagnosticRunning, modifier = Modifier.testTag("check-proxy-internet")) {
                Text("Проверить прокси / интернет")
            }
            TextButton(onClick = onYouTube, enabled = !state.diagnosticRunning, modifier = Modifier.testTag("check-youtube-proxy")) {
                Text("Проверить YouTube через прокси")
            }
            if (state.diagnosticRunning) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(state.diagnosticProgress ?: "Проверка…", style = MaterialTheme.typography.bodySmall) }
            state.diagnosticReport?.let { report ->
                val proxy = report.results.filter { !it.target.direct }
                Text("${if (report.stale) "Устаревший результат — повторите проверку. " else ""}Через прокси: ${proxy.count { it.success }} / ${proxy.size} целей прошли", style = MaterialTheme.typography.bodySmall)
            }
            if (state.diagnosticReport != null || state.diagnosticRunning || state.diagnosticError != null) {
                TextButton(onClick = onShowReport, modifier = Modifier.testTag("show-diagnostic-report")) { Text("Этапы, причины и что проверить дальше") }
            }
            if (!state.isWorking) Text("VPN выключен: проверяем настроенный прокси, не TUN. Подключение автоматически не включается.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionDiagnosticDialog(state: MainUiState, onCancel: () -> Unit, onDismiss: () -> Unit) {
    if (!state.diagnosticVisible) return
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Подробная диагностика") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("diagnostic-report"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.diagnosticRunning) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(state.diagnosticProgress ?: "Проверка…")
                    Text("До 65 секунд для сетевых этапов; системный DNS может завершаться позже. Закрытие/отмена закрывает сокеты. Прямые контрольные запросы явно отделены от прокси.", style = MaterialTheme.typography.bodySmall)
                }
                state.diagnosticError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.diagnosticReport?.let { report -> SelectionContainer { Text(report.text(), style = MaterialTheme.typography.bodySmall) } }
                if (!state.diagnosticRunning && state.diagnosticReport == null) Text(state.diagnosticProgress ?: "Результата ещё нет.")
            }
        },
        confirmButton = {
            if (state.diagnosticRunning) TextButton(onClick = onCancel) { Text("Отменить проверку") }
            else state.diagnosticReport?.let { report ->
                TextButton(onClick = { clipboard.setText(AnnotatedString(report.text())) }, modifier = Modifier.testTag("copy-diagnostic-report")) { Text("Скопировать отчёт") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}
