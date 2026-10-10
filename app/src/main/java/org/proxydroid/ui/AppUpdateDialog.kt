package org.proxydroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.proxydroid.BuildConfig
import org.proxydroid.update.UpdateChannel
import org.proxydroid.update.UpdateUiState

@Composable
fun AppUpdateDialog(
    state: UpdateUiState,
    onChannel: (UpdateChannel) -> Unit,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!state.visible) return
    val busy = state.checking || state.downloading
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update from GitHub") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Installed: v${BuildConfig.VERSION_NAME} · build ${BuildConfig.BUILD_RUN_ID}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UpdateChannel.values().forEach { channel ->
                        FilterChip(selected = state.channel == channel,
                            onClick = { onChannel(channel) }, enabled = !busy,
                            label = { Text(channel.label) })
                    }
                }
                if (state.checking) { Text("Checking releases…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                state.build?.let { build ->
                    Text("v${build.version} · ${build.abi} · build ${build.runId}")
                    Text(build.offer.tag, style = MaterialTheme.typography.bodySmall)
                    Text(build.offer.apk.name, style = MaterialTheme.typography.bodySmall)
                    Text("Android confirms installation. No silent update or key changes.", style = MaterialTheme.typography.bodySmall)
                }
                if (state.downloading) {
                    Text("Downloading: ${(state.progress * 100).toInt()}%")
                    LinearProgressIndicator(progress = state.progress, modifier = Modifier.fillMaxWidth())
                }
                state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (!busy) {
                when {
                    state.file != null -> TextButton(onClick = onInstall) { Text("Install") }
                    state.build != null -> TextButton(onClick = onDownload) { Text("Download APK") }
                    else -> TextButton(onClick = onCheck) { Text("Check latest") }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (busy) "Cancel" else "Close") } },
    )
}
