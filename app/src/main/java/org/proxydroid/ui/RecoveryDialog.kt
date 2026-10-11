package org.proxydroid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class RecoveryAction(val label: String) {
    RECONNECT("Переподключить VPN"), STOP_VPN("Остановить VPN"),
    RESTART_APP("Полный перезапуск приложения"), EXIT_APP("Остановить и закрыть приложение")
}
@Composable
fun RecoveryDialog(busy: Boolean, onAction: (RecoveryAction) -> Unit, onVpnSettings: () -> Unit,
                   onAppSettings: () -> Unit, onDismiss: () -> Unit) {
    var confirmation by remember { mutableStateOf<RecoveryAction?>(null) }
    val selected = confirmation
    if (selected != null) {
        AlertDialog(onDismissRequest = { if (!busy) confirmation = null }, title = { Text(selected.label) },
            text = { Text("VPN отключится. Настройки, профили и пароли сохранятся. Перезапуск очистит только процесс LocProxyTV, не кэш YouTube и не источник прокси. Закрытие не является системным Force stop: always-on/автозапуск могут снова запустить VPN.") },
            confirmButton = { TextButton(onClick = { onAction(selected) }, enabled = !busy, modifier = Modifier.tvRemoteFocus()) { Text(if (busy) "Остановка…" else "Продолжить") } },
            dismissButton = { TextButton(onClick = { confirmation = null }, enabled = !busy, modifier = Modifier.tvRemoteFocus()) { Text("Отмена") } })
        return
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Восстановление соединения") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Android допускает один VPN. Мы не выключаем чужие приложения принудительно: переключение выполняет Android после вашего согласия. Чужой always-on / блокировка без VPN могут помешать.", style = MaterialTheme.typography.bodySmall)
            RecoveryAction.values().forEach { action ->
                TextButton(onClick = { confirmation = action }, enabled = !busy, modifier = Modifier.tvRemoteFocus()) { Text(action.label) }
            }
            TextButton(onClick = onVpnSettings, modifier = Modifier.tvRemoteFocus()) { Text("Системные настройки VPN") }
            TextButton(onClick = onAppSettings, modifier = Modifier.tvRemoteFocus()) { Text("Настройки приложения · системный Force stop") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.tvRemoteFocus()) { Text("Закрыть") } })
}
