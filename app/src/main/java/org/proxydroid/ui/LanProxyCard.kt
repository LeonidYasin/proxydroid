package org.proxydroid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.proxydroid.utils.LanProxy

@Composable
fun LanProxyCard(state: MainUiState, onAuto: (Boolean) -> Unit, onScan: () -> Unit,
                 onCancel: () -> Unit, onSelect: (LanProxy) -> Unit) {
    var selected by remember { mutableStateOf<LanProxy?>(null) }
    LaunchedEffect(state.lanProxies) { if (selected !in state.lanProxies) selected = null }
    Card(Modifier.fillMaxWidth().testTag("lan-proxy-card")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Прокси в локальной сети", style = MaterialTheme.typography.titleMedium)
            Text("Поиск SOCKS5 и HTTP CONNECT в текущей Wi-Fi/Ethernet-сети. Найденный протокол ещё не доказывает выход в интернет.",
                style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).tvRemoteFocus().testTag("lan-auto")
                .toggleable(state.lanAutoScan, role = Role.Switch, onValueChange = onAuto).padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Автопоиск при открытии и смене сети", Modifier.weight(1f))
                Switch(state.lanAutoScan, onCheckedChange = null)
            }
            Text("Только частные IPv4-адреса, до 256 узлов, популярные порты и текущий Port. Без отправки сохранённых паролей. Поиск останавливается при выходе из приложения.",
                style = MaterialTheme.typography.bodySmall)
            state.lanScope?.let { Text("Область: $it", style = MaterialTheme.typography.bodySmall) }
            Text(state.lanStatus, style = MaterialTheme.typography.bodyMedium)
            if (state.lanScanning) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                OutlinedButton(onClick = onCancel, modifier = Modifier.tvRemoteFocus().testTag("lan-cancel")) { Text("Остановить поиск") }
            } else OutlinedButton(onClick = onScan, modifier = Modifier.tvRemoteFocus().testTag("lan-scan")) { Text("Найти прокси") }
            state.lanProxies.forEach { proxy ->
                OutlinedButton(onClick = { selected = proxy }, enabled = !state.isConnecting,
                    modifier = Modifier.fillMaxWidth().tvRemoteFocus().testTag("lan-${proxy.key}")) {
                    Column(Modifier.fillMaxWidth()) {
                        Text("${proxy.type.uppercase()} · ${proxy.host}:${proxy.port}")
                        Text(if (proxy.requiresAuth) "Нужна авторизация · настроить" else "Без авторизации · предложить подключение",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text("Если ничего не найдено: проверьте порт источника и изоляцию Wi-Fi клиентов. Для большой подсети проверяется только текущий /24 и шлюз/Host. SOCKS4, HTTPS-прокси и другие порты задаются вручную.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
    selected?.let { proxy ->
        ConfirmDialog(title = if (proxy.requiresAuth)
            "Создать профиль ${proxy.host}:${proxy.port}? Затем укажите авторизацию и подключитесь вручную. Прежний профиль сохранится."
        else "Подключиться к ${proxy.type}://${proxy.host}:${proxy.port}? Будет создан новый профиль без переноса паролей. Текущий VPN сначала остановится; выход в интернет проверяется отдельно.",
            confirmText = if (proxy.requiresAuth) "Настроить" else "Подключиться",
            onDismiss = { selected = null }, onConfirm = { selected = null; onSelect(proxy) })
    }
}
