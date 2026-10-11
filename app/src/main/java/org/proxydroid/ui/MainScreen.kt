package org.proxydroid.ui

import android.content.Intent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Divider
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.proxydroid.AppManager
import org.proxydroid.BuildConfig
import org.proxydroid.BypassListActivity
import org.proxydroid.Profile
import org.proxydroid.R

private val PROXY_TYPES = listOf("socks5", "socks4", "http", "https")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    state: MainUiState,
    onToggle: (Boolean) -> Unit,
    onProfileEdit: (Profile.() -> Unit) -> Unit,
    onSelectProfile: (String) -> Unit,
    onNewProfile: () -> Unit,
    onRenameProfile: (String) -> Unit,
    onDeleteProfile: () -> Unit,
    onToggleAdvanced: () -> Unit,
    onCheckAlive: () -> Unit,
    onGatewayChange: (Boolean) -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenYouTube: () -> Unit = {},
    onRunDiagnostics: () -> Unit = {},
    onCheckYouTube: () -> Unit = {},
    onShowDiagnostics: () -> Unit = {},
    onRecovery: () -> Unit = {},
    onLanAutoScan: (Boolean) -> Unit = {},
    onLanScan: () -> Unit = {},
    onLanCancel: () -> Unit = {},
    onLanSelect: (org.proxydroid.utils.LanProxy) -> Unit = {},
) {
    val scroll = rememberScrollState()
    var menuOpen by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val connectionFocus = remember { FocusRequester() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                modifier = Modifier.testTag("app-top-bar"),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Column {
                        Text(stringRes(R.string.app_name), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = buildIdentityText(),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.tvRemoteFocus()) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename profile") },
                            onClick = { menuOpen = false; showRename = true },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete profile") },
                            onClick = { menuOpen = false; showDeleteConfirm = true },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                        )
                        DropdownMenuItem(text = { Text("Восстановление / перезапуск") },
                            onClick = { menuOpen = false; onRecovery() })
                        Divider()
                        DropdownMenuItem(
                            text = { Text("Update app") },
                            onClick = { menuOpen = false; onOpenUpdates() },
                        )
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("About")
                                    Text(
                                        text = buildIdentityText(),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            },
                            onClick = { menuOpen = false },
                            leadingIcon = { Icon(Icons.Default.Info, null) },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .fillMaxSize()
                .clipToBounds()
                .testTag("main-scroll")
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ConnectionCard(state = state, onToggle = onToggle, onCheckAlive = onCheckAlive, focusRequester = connectionFocus, onOpenYouTube = onOpenYouTube, onDiagnostics = onRunDiagnostics)
            ConnectionDiagnostics(state, onRunDiagnostics, onCheckYouTube, onShowDiagnostics)
            AutoGatewayCard(
                enabled = state.profile.useGatewayAsHost,
                onChange = onGatewayChange,
                gatewayIp = state.gatewayIp,
            )
            ProxyEndpointCard(profile = state.profile, edit = onProfileEdit)
            ProfileCard(
                state = state,
                onSelectProfile = onSelectProfile,
                onNewProfile = onNewProfile,
            )
            ProxyForm(profile = state.profile, edit = onProfileEdit)
            AutoStartOnBootCard(
                enabled = state.profile.autoStartOnBoot,
                status = state.bootStatus,
                onChange = { onProfileEdit { autoStartOnBoot = it } },
            )
            LanProxyCard(state, onLanAutoScan, onLanScan, onLanCancel, onLanSelect)
            AdvancedSection(
                profile = state.profile,
                expanded = state.advancedExpanded,
                onToggle = onToggleAdvanced,
                edit = onProfileEdit,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showRename) {
        TextEditDialog(
            title = "Rename profile",
            initial = state.profile.name,
            onDismiss = { showRename = false },
            onConfirm = { showRename = false; onRenameProfile(it) },
        )
    }
    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "Delete this profile?",
            confirmText = "Delete",
            onDismiss = { showDeleteConfirm = false },
            onConfirm = { showDeleteConfirm = false; onDeleteProfile() },
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ConnectionCard(
    state: MainUiState,
    onToggle: (Boolean) -> Unit,
    onCheckAlive: () -> Unit,
    focusRequester: FocusRequester,
    onOpenYouTube: () -> Unit,
    onDiagnostics: () -> Unit,
) {
    // Request inside Scaffold's subcomposition, after the target exists.
    val inputMode = LocalInputModeManager.current
    var initialFocusSet by remember { mutableStateOf(false) }
    LaunchedEffect(state.isConnecting) {
        if (!initialFocusSet && !(state.isWorking && state.internetAvailable == true && !state.internetChecking)) {
            // Foundation switches/buttons reject focus in touch mode. Enter
            // DPAD/keyboard mode before assigning the initial remote focus.
            inputMode.requestInputMode(InputMode.Keyboard)
            focusRequester.requestFocus()
            initialFocusSet = true
        }
    }
    val on = state.isWorking
    val tone = if (on) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val onTone = if (on) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        colors = CardDefaults.cardColors(containerColor = tone, contentColor = onTone),
        modifier = Modifier.fillMaxWidth().testTag("connection-card"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp)
                .focusRequester(focusRequester)
                .tvRemoteFocus().testTag("connection-row")
                .toggleable(value = on || state.isConnecting,
                    role = Role.Switch, onValueChange = onToggle)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(14.dp).background(
                if (on) Color(0xFF2BAA63) else MaterialTheme.colorScheme.outline,
                CircleShape))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (state.isConnecting) "Connecting / waiting — OK to stop" else if (on) "Connected" else "Disconnected",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold)
                Text(secondaryStatus(state), style = MaterialTheme.typography.bodyMedium)
                if (!on) state.lastError?.let { error ->
                    Text(error, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
            if (state.isConnecting) CircularProgressIndicator(
                modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            // Passive visual indicator: the entire row is the only toggle/focus
            // target. This prevents clipped labels when returning with DPAD.
            Switch(checked = on || state.isConnecting, onCheckedChange = null,
                modifier = Modifier.testTag("connection-toggle"))
        }
        if (on) Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            LivenessIndicator(state, onCheckAlive)
            InternetActions(state, onOpenYouTube, onDiagnostics)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun InternetActions(state: MainUiState, onOpenYouTube: () -> Unit, onRetry: () -> Unit) {
    val youtubeFocus = remember { FocusRequester() }
    val youtubeView = remember { BringIntoViewRequester() }
    val inputMode = LocalInputModeManager.current
    var focusedThisConnection by remember { mutableStateOf(false) }
    LaunchedEffect(state.isWorking) {
        if (!state.isWorking) focusedThisConnection = false
    }
    if (!state.isWorking) return
    when {
        state.internetChecking -> Text("Checking internet through proxy…", style = MaterialTheme.typography.bodySmall)
        state.internetAvailable == true -> {
            Text("HTTPS test succeeded through proxy — not a full VPN or video test", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onOpenYouTube, modifier = Modifier
                .tvRemoteFocus().bringIntoViewRequester(youtubeView)
                .focusRequester(youtubeFocus).testTag("open-youtube")) { Text("Open YouTube") }
            // First verified success per connection: show the action and hand off
            // the remote focus. Retry/poll updates must not repeatedly steal it.
            LaunchedEffect(Unit) {
                if (!focusedThisConnection) {
                    inputMode.requestInputMode(InputMode.Keyboard)
                    withFrameNanos { } // Wait for the inserted action's layout.
                    youtubeView.bringIntoView()
                    youtubeFocus.requestFocus()
                    focusedThisConnection = true
                }
            }
        }
        state.internetAvailable == false -> {
            Text("Internet through proxy could not be verified", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onRetry, modifier = Modifier.tvRemoteFocus()) { Text("Retry internet check") }
        }
    }
}

/**
 * Small dot + label showing the result of the last liveness probe of the
 * upstream proxy. Tapping the label re-runs the probe.
 *
 * Colors:
 *   green  -> isAlive == true  (proxy port responded)
 *   red    -> isAlive == false (probe failed)
 *   gray   -> isAlive == null  (probe has not run yet)
 */
@Composable
private fun LivenessIndicator(state: MainUiState, onCheckAlive: () -> Unit) {
    val (dotColor, label) = when (state.isAlive) {
        true -> Color(0xFF2BAA63) to "TCP port reachable — forwarding/internet not proven"
        false -> MaterialTheme.colorScheme.error to "TCP port unreachable — run detailed diagnostics"
        null -> Color(0xFF9E9E9E) to "Proxy not checked yet"
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(dotColor, CircleShape),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = onCheckAlive,
                modifier = Modifier.tvRemoteFocus(),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            ) {
                Text("TCP check", style = MaterialTheme.typography.bodySmall)
            }
        }
        // Show the concrete reason of the last failure instead of dying
        // silently: e.g. "no gateway on cellular" or "connection refused".
        val err = state.lastError
        if (err != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = err,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun secondaryStatus(state: MainUiState): String {
    val p = state.profile
    val host = p.host.ifBlank { "—" }
    val port = if (p.port > 0) ":${p.port}" else ""
    return "${p.proxyType.uppercase()} • $host$port"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileCard(
    state: MainUiState,
    onSelectProfile: (String) -> Unit,
    onNewProfile: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = state.profiles.firstOrNull { it.id == state.currentProfileId }
        ?: ProfileEntry(state.currentProfileId, "Profile ${state.currentProfileId}")

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Profile", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth().tvRemoteFocus().testTag("select-profile")) {
                        Text(current.name, modifier = Modifier.weight(1f), maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Default.ExpandMore, contentDescription = "Select profile")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        state.profiles.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.name) }, modifier = Modifier.tvRemoteFocus(),
                                onClick = { expanded = false; onSelectProfile(p.id) })
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onNewProfile, modifier = Modifier.tvRemoteFocus()) {
                    Icon(Icons.Default.Add, contentDescription = "New profile")
                }
            }
        }
    }
}

@Composable
private fun AutoGatewayCard(enabled: Boolean, onChange: (Boolean) -> Unit, gatewayIp: String?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Auto gateway", style = MaterialTheme.typography.labelLarge)
            Text("Detected gateway: ${gatewayIp ?: "not available yet"}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            ToggleRow(
                title = "Use default gateway as proxy host",
                subtitle = "Automatically use the current network's default gateway " +
                    "(e.g. the phone's hotspot address 192.168.43.1) as the upstream " +
                    "proxy host, instead of the manually configured Host. " +
                    "После потери сети активный auto-VPN ждёт новый шлюз и пересоздаётся даже при закрытом интерфейсе.",
                checked = enabled,
                onChange = onChange,
            )
        }
    }
}

@Composable
private fun AutoStartOnBootCard(enabled: Boolean, onChange: (Boolean) -> Unit, status: String?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Startup", style = MaterialTheme.typography.labelLarge)
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(8.dp))
            ToggleRow(
                title = "Start on boot",
                subtitle = "Connect once to grant VPN consent. After boot/update, wait up to " +
                    "120 seconds for network/proxy. OEM autostart restrictions may apply.",
                checked = enabled,
                onChange = onChange,
            )
        }
    }
}

@Composable
private fun ProxyEndpointCard(profile: Profile, edit: (Profile.() -> Unit) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().testTag("proxy-endpoint")) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EditableSettingField(
                    value = profile.host,
                    onValueChange = { v -> edit { host = v.trim() } },
                    label = "Host",
                    singleLine = true,
                    enabled = !profile.useGatewayAsHost,
                    modifier = Modifier.weight(2f),
                )
                EditableSettingField(
                    value = if (profile.port == 0) "" else profile.port.toString(),
                    onValueChange = { v -> edit { port = v.toInt() } },
                    validate = { v ->
                        if (org.proxydroid.utils.isValidProxyPort(v)) null
                        else "Enter a port from 1 to 65535"
                    },
                    label = "Port",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }

        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProxyForm(profile: Profile, edit: (Profile.() -> Unit) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Proxy", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PROXY_TYPES.forEach { t ->
                    FilterChip(
                        selected = profile.proxyType == t,
                        onClick = { edit { proxyType = t } },
                        label = { Text(t.uppercase()) },
                        modifier = Modifier.weight(1f).tvRemoteFocus(),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))


            Spacer(Modifier.height(8.dp))
            ToggleRow(
                title = "Authentication",
                checked = profile.isAuth,
                onChange = { edit { isAuth = it } },
            )
            AnimatedVisibility(visible = profile.isAuth) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditableSettingField(
                        value = profile.user,
                        onValueChange = { v -> edit { user = v } },
                        label = "Username",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    EditableSettingField(
                        value = profile.password,
                        onValueChange = { v -> edit { password = v } },
                        label = "Password",
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ToggleRow(
                        title = "NTLM",
                        checked = profile.isNTLM,
                        onChange = { edit { isNTLM = it } },
                    )
                    AnimatedVisibility(visible = profile.isNTLM) {
                        EditableSettingField(
                            value = profile.domain,
                            onValueChange = { v -> edit { domain = v } },
                            label = "NTLM Domain",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdvancedSection(
    profile: Profile,
    expanded: Boolean,
    onToggle: () -> Unit,
    edit: (Profile.() -> Unit) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Advanced", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onToggle, modifier = Modifier.tvRemoteFocus()) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Divider()
                    ToggleRow(
                        title = "PAC (Auto Setting)",
                        subtitle = "Treat host as a PAC URL",
                        checked = profile.isPAC,
                        onChange = { edit { isPAC = it } },
                    )
                    ToggleRow(
                        title = "DNS proxy",
                        subtitle = "Forward DNS queries through the proxy",
                        checked = profile.isDNSProxy,
                        onChange = { edit { isDNSProxy = it } },
                    )
                    ToggleRow(
                        title = "Auto-connect on selected SSIDs",
                        checked = profile.isAutoConnect,
                        onChange = { edit { isAutoConnect = it } },
                    )
                    AnimatedVisibility(visible = profile.isAutoConnect) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            EditableSettingField(
                                value = profile.ssid,
                                onValueChange = { v -> edit { ssid = v } },
                                label = "Bind SSID (comma-separated)",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            EditableSettingField(
                                value = profile.excludedSsid,
                                onValueChange = { v -> edit { excludedSsid = v } },
                                label = "Exclude SSID (comma-separated)",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Divider()
                    LinkRow(
                        icon = Icons.Default.Block,
                        title = "Bypass addresses",
                        targetActivity = BypassListActivity::class.java,
                    )
                    LinkRow(
                        icon = Icons.Default.Apps,
                        title = "Per-app routing",
                        subtitle = if (profile.isBypassApps) "Bypass selected apps" else "Proxy only selected apps",
                        targetActivity = AppManager::class.java,
                    )
                    ToggleRow(
                        title = "Bypass selected apps",
                        subtitle = "Off = proxy only the selected apps",
                        checked = profile.isBypassApps,
                        onChange = { edit { isBypassApps = it } },
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .tvRemoteFocus()
            .toggleable(checked, role = Role.Switch, onValueChange = onChange)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun LinkRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    targetActivity: Class<*>,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { ctx.startActivity(Intent(ctx, targetActivity)) }, modifier = Modifier.tvRemoteFocus()) {
            Text("Open")
        }
    }
}

@Composable
private fun TextEditDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }, modifier = Modifier.tvRemoteFocus()) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.tvRemoteFocus()) { Text("Cancel") } },
    )
}

@Composable
internal fun ConfirmDialog(
    title: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = {
            // Focus inside the dialog window, after the button has been placed.
            val confirmFocus = remember { FocusRequester() }
            var placed by remember { mutableStateOf(false) }
            LaunchedEffect(placed) {
                if (placed) { withFrameNanos { }; confirmFocus.requestFocus() }
            }
            TextButton(onClick = onConfirm, modifier = Modifier.focusRequester(confirmFocus)
                .onGloballyPositioned { placed = true }.tvRemoteFocus()) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.tvRemoteFocus()) { Text("Cancel") } },
    )
}

@Composable
private fun stringRes(id: Int): String =
    androidx.compose.ui.res.stringResource(id = id)


private fun buildIdentityText(): String =
    "v${BuildConfig.VERSION_NAME} · ${BuildConfig.BUILD_TYPE} · " +
        "build ${BuildConfig.BUILD_RUN_ID}/a${BuildConfig.BUILD_RUN_ATTEMPT} · " +
        "g${BuildConfig.BUILD_SHA.take(12)}"
