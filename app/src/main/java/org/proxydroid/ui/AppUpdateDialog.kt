package org.proxydroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import org.proxydroid.BuildConfig
import org.proxydroid.update.UpdateChannel
import org.proxydroid.update.UpdateUiState

@OptIn(ExperimentalMaterial3Api::class)
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
    val busy = state.checking || state.downloading || state.installing
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update from GitHub") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Installed: v${BuildConfig.VERSION_NAME} · build ${BuildConfig.BUILD_RUN_ID} · ${if (BuildConfig.DEBUG) "DEBUG" else "RELEASE"}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UpdateChannel.values().forEach { channel ->
                        FilterChip(selected = state.channel == channel,
                            onClick = { onChannel(channel) }, enabled = !busy,
                            label = { Text(channel.label) }, modifier = Modifier.tvRemoteFocus())
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
                    Text("${state.downloadStatus ?: "Downloading"}: ${(state.progress * 100).toInt()}% · attempt ${state.downloadAttempt}/5")
                    Text("${state.downloadBytes / 1024} / ${state.downloadTotal / 1024} KiB · ${state.bytesPerSecond / 1024} KiB/s", style = MaterialTheme.typography.bodySmall)
                    if (state.bytesPerSecond > 0 && state.downloadBytes < state.downloadTotal) {
                        Text("Remaining ~${(state.downloadTotal - state.downloadBytes) / state.bytesPerSecond} seconds", style = MaterialTheme.typography.bodySmall)
                    }
                    LinearProgressIndicator(progress = state.progress, modifier = Modifier.fillMaxWidth())
                }
                if (state.installing) { Text("Verifying cached APK / opening installer…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                state.file?.let { file ->
                    DownloadedApkLink(file.absolutePath, enabled = !busy, onInstall = onInstall)
                }
                if (!busy && state.file == null && state.downloadBytes > 0) {
                    Text("Saved ${state.downloadBytes / 1024} / ${state.downloadTotal / 1024} KiB. Resume keeps saved bytes; only a fully verified APK can be installed.", style = MaterialTheme.typography.bodySmall)
                }
                state.signatureReport?.let { report ->
                    Text("Installed signer SHA-256:", style = MaterialTheme.typography.bodySmall)
                    Text(report.installed.sorted().joinToString("\n").ifEmpty { "Unavailable — not verified" }, style = MaterialTheme.typography.bodySmall)
                    if (report.downloaded.isNotEmpty()) {
                        Text("Downloaded APK signer SHA-256:", style = MaterialTheme.typography.bodySmall)
                        Text(report.downloaded.sorted().joinToString("\n"), style = MaterialTheme.typography.bodySmall)
                    }
                }
                state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (!busy) {
                when {
                    state.file != null -> TextButton(onClick = onInstall, modifier = Modifier.tvRemoteFocus()) { Text("Install") }
                    state.build != null -> TextButton(onClick = onDownload, modifier = Modifier.tvRemoteFocus()) { Text(if (state.downloadBytes > 0) "Resume download / verify" else "Download APK") }
                    else -> TextButton(onClick = onCheck, modifier = Modifier.tvRemoteFocus()) { Text("Check latest") }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.tvRemoteFocus()) { Text(if (state.downloading) "Pause / close" else if (busy) "Cancel" else "Close") } },
    )
}

/** Untruncated path is an explicit, DPAD-focusable installer action. */
@Composable
internal fun DownloadedApkLink(path: String, enabled: Boolean, onInstall: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Downloaded APK — select the path to install", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onInstall, enabled = enabled,
            modifier = Modifier.fillMaxWidth().tvRemoteFocus().testTag("downloaded-apk-path")) {
            Text(path, style = MaterialTheme.typography.bodySmall, softWrap = true)
        }
        Text("Private app cache; other file managers cannot access this path. Installation uses a secure content URI.", style = MaterialTheme.typography.bodySmall)
    }
}
