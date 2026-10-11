/* proxydroid - Global / Individual Proxy App for Android
 * Copyright (C) 2011 Max Lv <max.c.lv@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.proxydroid

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.proxydroid.ui.ConnectionDiagnosticDialog
import org.proxydroid.ui.AppUpdateDialog
import org.proxydroid.update.UpdateViewModel
import org.proxydroid.ui.MainScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.proxydroid.ui.MainViewModel
import org.proxydroid.ui.theme.ProxyDroidTheme
import org.proxydroid.utils.ProxyController
import org.proxydroid.utils.Utils

class ProxyDroid : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val updates: UpdateViewModel by viewModels()
    private var recoveryVisible by androidx.compose.runtime.mutableStateOf(false)
    private var recoveryBusy by androidx.compose.runtime.mutableStateOf(false)

    private val vpnConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val extras = ProxyController.buildExtras(viewModel.state.value.profile)
            Utils.setLastError(null)
            androidx.core.content.ContextCompat.startForegroundService(this,
                Intent(this, ProxyDroidVpnService::class.java).putExtras(extras)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ProxyDroidTheme {
                val state by viewModel.state.collectAsState()
                val updateState by updates.state.collectAsState()
                MainScreen(
                    state = state,
                    onToggle = { wantOn ->
                        if (wantOn) startVpn() else stopVpn()
                    },
                    onProfileEdit = { viewModel.updateProfile(it) },
                    onSelectProfile = viewModel::selectProfile,
                    onNewProfile = { viewModel.newProfile("") },
                    onRenameProfile = viewModel::renameCurrent,
                    onDeleteProfile = viewModel::deleteCurrent,
                    onToggleAdvanced = viewModel::toggleAdvanced,
                    onCheckAlive = viewModel::checkAlive,
                    onGatewayChange = viewModel::setUseGatewayAsHost,
                    onOpenUpdates = updates::open,
                    onOpenYouTube = { if (!org.proxydroid.utils.YouTubeLauncher.open(this)) viewModel.reportConnectionError("YouTube/browser is not installed") },
                    onRunDiagnostics = { viewModel.checkDiagnostics() },
                    onCheckYouTube = viewModel::checkYouTube,
                    onShowDiagnostics = viewModel::showDiagnostics,
                    onRecovery = { recoveryVisible = true },
                    onLanAutoScan = viewModel::setLanAutoScan,
                    onLanScan = viewModel::scanLan,
                    onLanCancel = viewModel::cancelLanScan,
                    onLanSelect = ::connectLanProxy,
                )
                if (recoveryVisible) org.proxydroid.ui.RecoveryDialog(recoveryBusy, ::recover,
                    { openSystemSettings(android.provider.Settings.ACTION_VPN_SETTINGS) },
                    { openSystemSettings(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS) },
                    { if (!recoveryBusy) recoveryVisible = false })
                ConnectionDiagnosticDialog(state, viewModel::cancelDiagnostics, viewModel::dismissDiagnostics)
                AppUpdateDialog(updateState, updates::selectChannel, updates::check,
                    updates::download, { updates.install(this) }, updates::dismiss)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                updates.state.collect { update ->
                    if (update.visible && update.pendingInstall && !update.installing) updates.install(this@ProxyDroid)
                }
            }
        }

        // MainViewModel observes Utils.working / Utils.connecting StateFlows
        // directly, so the Compose UI auto-refreshes without polling.

        if (intent?.getBooleanExtra(ProxyController.EXTRA_AUTO_START, false) == true) {
            startVpn()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(ProxyController.EXTRA_AUTO_START, false)) {
            startVpn()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reload()
        viewModel.setForeground(true)
        updates.onResume(this)
    }

    override fun onStop() {
        viewModel.setForeground(false)
        super.onStop()
    }

    private fun connectLanProxy(proxy: org.proxydroid.utils.LanProxy) {
        if (recoveryBusy || proxy !in viewModel.state.value.lanProxies) return
        recoveryBusy = true
        viewModel.prepareRecovery()
        lifecycleScope.launch {
            try {
                if (Utils.isWorking() || Utils.isConnecting()) {
                    if (!org.proxydroid.utils.VpnRecovery.stopAndAwait(this@ProxyDroid)) {
                        viewModel.reportConnectionError("VPN не подтвердил остановку. Новый прокси не выбран.")
                        return@launch
                    }
                }
                viewModel.finishRecovery()
                if (!viewModel.selectLanProxy(proxy)) return@launch
                if (proxy.requiresAuth) viewModel.reportConnectionError("Прокси требует авторизацию. Укажите её в новом профиле; старые пароли не перенесены. HTTP-прокси может требовать неподдерживаемый NTLM/Digest.")
                else if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) startVpn()
            } finally { recoveryBusy = false; viewModel.finishRecovery() }
        }
    }

    private fun startVpn() {
        val consent = VpnService.prepare(this)
        if (consent != null) {
            vpnConsentLauncher.launch(consent)
        } else {
            val extras = ProxyController.buildExtras(viewModel.state.value.profile)
            Utils.setLastError(null)
            androidx.core.content.ContextCompat.startForegroundService(this,
                Intent(this, ProxyDroidVpnService::class.java).putExtras(extras)
            )
        }
    }

    private fun openSystemSettings(action: String) {
        val intent = Intent(action)
        if (action == android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.parse("package:$packageName")
        try { startActivity(intent) }
        catch (_: Exception) { viewModel.reportConnectionError("Системный экран недоступен на этой прошивке. Откройте настройки приставки вручную.") }
    }

    private fun recover(action: org.proxydroid.ui.RecoveryAction) {
        if (recoveryBusy) return
        recoveryBusy = true
        viewModel.prepareRecovery(); updates.dismiss()
        @Suppress("DEPRECATION")
        android.preference.PreferenceManager.getDefaultSharedPreferences(this).edit().commit()
        lifecycleScope.launch {
            val stopped = org.proxydroid.utils.VpnRecovery.stopAndAwait(this@ProxyDroid)
            recoveryBusy = false; recoveryVisible = false
            viewModel.finishRecovery()
            if (!stopped) {
                viewModel.reportConnectionError("Остановка не подтверждена: перезапуск/переподключение не выполнены. Повторите Stop или используйте системный Force stop.")
                return@launch
            }
            when (action) {
                org.proxydroid.ui.RecoveryAction.RECONNECT -> startVpn()
                org.proxydroid.ui.RecoveryAction.STOP_VPN -> Unit
                org.proxydroid.ui.RecoveryAction.RESTART_APP -> {
                    startActivity(Intent(this@ProxyDroid, RestartActivity::class.java)
                        .putExtra("old_pid", android.os.Process.myPid())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                }
                org.proxydroid.ui.RecoveryAction.EXIT_APP -> {
                    finishAffinity(); android.os.Process.killProcess(android.os.Process.myPid())
                }
            }
        }
    }

    private fun stopVpn() {
        ProxyController.stop(this)
    }
}
