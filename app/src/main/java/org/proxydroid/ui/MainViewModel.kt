package org.proxydroid.ui

import android.app.Application
import android.content.SharedPreferences
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CancellationException
import org.proxydroid.utils.NetworkDiagnostics
import org.proxydroid.utils.DeviceDiagnostics
import org.proxydroid.utils.DiagnosticKind
import org.proxydroid.utils.DiagnosticReport
import org.proxydroid.utils.ProxyEndpoint
import org.proxydroid.utils.BootStatus
import android.preference.PreferenceManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.proxydroid.Profile
import org.proxydroid.utils.NetworkUtils
import org.proxydroid.utils.Utils

data class ProfileEntry(val id: String, val name: String)

data class MainUiState(
    val profile: Profile = Profile().apply { init() },
    val profiles: List<ProfileEntry> = emptyList(),
    val currentProfileId: String = "1",
    val isWorking: Boolean = false,
    val isConnecting: Boolean = false,
    val advancedExpanded: Boolean = false,
    /**
     * Result of the last upstream-proxy liveness probe.
     *   true  -> proxy port responded (alive)
     *   false -> probe failed (dead / unreachable)
     *   null  -> probe has not run yet (or reset)
     */
    val isAlive: Boolean? = null,
    /**
     * Human-readable description of the last connection failure, or null
     * when there is nothing to report. Set by [reportConnectionError] and
     * cleared by [clearError]. Surfaced in the UI so a failing connect
     * never dies silently.
     */
    val lastError: String? = null,
    val gatewayIp: String? = null,
    val internetChecking: Boolean = false,
    val internetAvailable: Boolean? = null,
    val bootStatus: String? = null,
    val diagnosticRunning: Boolean = false,
    val diagnosticVisible: Boolean = false,
    val diagnosticKind: DiagnosticKind = DiagnosticKind.INTERNET,
    val diagnosticProgress: String? = null,
    val diagnosticReport: DiagnosticReport? = null,
    val diagnosticError: String? = null,

)

class MainViewModel @JvmOverloads constructor(app: Application,
    private val gatewayLookup: (Context) -> String? = NetworkUtils::getGatewayIp,
    private val observeNetwork: Boolean = true,
) : AndroidViewModel(app) {

    private val settings: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(app)

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == BootStatus.KEY) viewModelScope.launch {
            _state.value = _state.value.copy(bootStatus = settings.getString(BootStatus.KEY, null))
        }
    }
    private val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private var networkRegistered = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshOnNetworkChange()
        override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) = refreshOnNetworkChange()
        override fun onLost(network: Network) = refreshOnNetworkChange()
    }
    private var networkChangeJob: Job? = null
    private fun refreshOnNetworkChange() { viewModelScope.launch {
        invalidateDiagnostics()
        networkChangeJob?.cancel()
        networkChangeJob = viewModelScope.launch {
            delay(750)
            refreshGateway()
            if (Utils.isWorking()) checkDiagnostics(showReport = _state.value.diagnosticVisible)
        }
    } }


    /** Job of the periodic liveness probe loop; null when not running. */
    private var pollingJob: Job? = null
    private var gatewayJob: Job? = null
    private var gatewayRequest = 0L
    private var internetJob: Job? = null
    @Volatile private var diagnosticRequest = 0L
    private var lastAutoEndpoint: ProxyEndpoint? = null
    private var aliveJob: Job? = null

    init {
        settings.registerOnSharedPreferenceChangeListener(prefListener)
        // Mirror service-side flags into UI state reactively.
        viewModelScope.launch {
            combine(Utils.working, Utils.connecting, Utils.activeUpstream) { w, c, e -> Triple(w, c, e) }
                .collect { (w, c, endpoint) ->
                    _state.value = _state.value.copy(isWorking = w, isConnecting = c)
                    // Auto-poll the proxy liveness while the tunnel is active.
                    if (w) {
                        startAlivePolling()
                        if (endpoint != null && lastAutoEndpoint !== endpoint) {
                            lastAutoEndpoint = endpoint
                            checkInternet()
                        }
                    } else {
                        stopAlivePolling()
                        if (lastAutoEndpoint != null) invalidateDiagnostics()
                        lastAutoEndpoint = null
                        _state.value = _state.value.copy(internetChecking = false, internetAvailable = null, isAlive = null)
                    }
                }
        }
        // Mirror errors reported by the VPN service (e.g. "no gateway on
        // cellular", "failed to start tun2socks") into the UI state so they
        // are shown instead of dying silently in the service.
        viewModelScope.launch {
            Utils.lastError.collect { err ->
                _state.value = _state.value.copy(lastError = err)
            }
        }
        reload()
        if (observeNetwork) runCatching {
            cm?.registerNetworkCallback(NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), networkCallback)
            networkRegistered = cm != null
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        gatewayJob?.cancel(); internetJob?.cancel(); aliveJob?.cancel(); networkChangeJob?.cancel()
        if (networkRegistered) runCatching { cm?.unregisterNetworkCallback(networkCallback) }
        settings.unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    fun reload() {
        // SharedPreferences are in-memory; avoid an old IO snapshot overwriting
        // a newly edited/refreshed Host after onResume or initial startup.
        val current = settings.getString("profile", "1") ?: "1"
        val profile = Profile().also { it.getProfile(settings) }
        if (profile.proxyType.isEmpty()) profile.proxyType = "socks5"
        _state.value = _state.value.copy(profile = profile, profiles = loadProfileList(),
            currentProfileId = current, isWorking = Utils.isWorking(), isConnecting = Utils.isConnecting(),
            bootStatus = settings.getString(BootStatus.KEY, null))
        refreshGateway()
    }

    fun updateProfile(transform: Profile.() -> Unit) {
        val before = _state.value.profile
        val next = Profile().also { it.copyFrom(before) }.apply(transform)
        if (before.host != next.host || before.port != next.port || before.proxyType != next.proxyType || before.user != next.user || before.password != next.password || before.proxyApps != next.proxyApps || before.isBypassApps != next.isBypassApps) invalidateDiagnostics()
        _state.value = _state.value.copy(profile = next)
        // apply() changes memory synchronously and writes disk asynchronously.
        // Serialize UI edits here: an older IO coroutine must not overwrite a
        // just-refreshed gateway or persist an edit under a different profile ID.
        val id = _state.value.currentProfileId
        next.setProfile(settings)
        settings.edit().putString(id, next.toString()).apply()
    }

    fun toggleAdvanced() {
        _state.value = _state.value.copy(advancedExpanded = !_state.value.advancedExpanded)
    }

    fun toggleUseGatewayAsHost() = setUseGatewayAsHost(!_state.value.profile.useGatewayAsHost)

    fun setUseGatewayAsHost(enabled: Boolean) {
        updateProfile { useGatewayAsHost = enabled }
        refreshGateway(showMissingError = enabled)
    }

    /** Detect on startup/resume, network changes and toggle; write Host only in auto mode. */
    fun refreshGateway(showMissingError: Boolean = false) {
        gatewayJob?.cancel()
        val request = ++gatewayRequest
        val profileId = _state.value.currentProfileId
        gatewayJob = viewModelScope.launch {
            val gateway = withContext(Dispatchers.IO) { runCatching { gatewayLookup(getApplication()) }.getOrNull() }
            if (request != gatewayRequest || _state.value.currentProfileId != profileId) return@launch
            _state.value = _state.value.copy(gatewayIp = gateway)
            if (!_state.value.profile.useGatewayAsHost) return@launch
            if (gateway.isNullOrBlank()) {
                if (showMissingError) _state.value = _state.value.copy(lastError = "No usable IPv4 default gateway on this network. Host was not changed.")
            } else {
                updateProfile { host = gateway }
                clearError()
            }
        }
    }

    fun checkInternet() {
        if (Utils.isWorking()) checkDiagnostics(showReport = false)
    }

    fun showDiagnostics() { _state.value = _state.value.copy(diagnosticVisible = true) }
    fun dismissDiagnostics() { cancelDiagnostics(); _state.value = _state.value.copy(diagnosticVisible = false) }
    fun cancelDiagnostics() {
        diagnosticRequest++; internetJob?.cancel(); internetJob = null
        _state.value = _state.value.copy(diagnosticRunning = false, internetChecking = false,
            diagnosticProgress = "Check cancelled; no background probe continues", diagnosticReport = _state.value.diagnosticReport?.copy(stale = true))
    }
    private fun invalidateDiagnostics() {
        diagnosticRequest++; internetJob?.cancel(); internetJob = null
        _state.value = _state.value.copy(diagnosticRunning = false, internetChecking = false,
            internetAvailable = null, diagnosticProgress = null, diagnosticReport = _state.value.diagnosticReport?.copy(stale = true))
    }
    fun checkYouTube() = checkDiagnostics(DiagnosticKind.YOUTUBE)
    fun checkDiagnostics(kind: DiagnosticKind = DiagnosticKind.INTERNET, showReport: Boolean = true) {
        internetJob?.cancel()
        val request = ++diagnosticRequest
        val active = Utils.activeUpstream.value.takeIf { Utils.isWorking() }
        val profile = Profile().also { it.copyFrom(_state.value.profile) }
        val profileId = _state.value.currentProfileId
        val endpoint = active ?: ProxyEndpoint(profile.host, profile.port, profile.proxyType,
            profile.user.takeIf { it.isNotEmpty() }, profile.password.takeIf { it.isNotEmpty() })
        _state.value = _state.value.copy(diagnosticRunning = true, diagnosticVisible = showReport || _state.value.diagnosticVisible,
            diagnosticKind = kind, diagnosticProgress = "Checking actual upstream / selected physical network…",
            diagnosticError = null, diagnosticReport = _state.value.diagnosticReport?.copy(stale = true),
            internetChecking = kind == DiagnosticKind.INTERNET && active != null,
            internetAvailable = if (kind == DiagnosticKind.INTERNET) null else _state.value.internetAvailable)
        internetJob = viewModelScope.launch {
            try {
                val device = withContext(Dispatchers.IO) { DeviceDiagnostics.capture(getApplication(), profile, endpoint, active != null) }
                val report = NetworkDiagnostics.run(kind, endpoint, device.lines, device.route) { done, total ->
                    viewModelScope.launch {
                        if (request == diagnosticRequest && _state.value.diagnosticRunning) _state.value = _state.value.copy(diagnosticProgress = "$done / $total destinations completed; HTTPS/DoH stages are bounded")
                    }
                }
                if (request != diagnosticRequest || _state.value.currentProfileId != profileId) return@launch
                val stale = if (active != null) !Utils.isWorking() || Utils.activeUpstream.value !== active else Utils.isWorking()
                val changedNetwork = withContext(Dispatchers.IO) { DeviceDiagnostics.physicalNetwork(getApplication()) } != device.network
                if (request != diagnosticRequest || _state.value.currentProfileId != profileId) return@launch
                _state.value = _state.value.copy(diagnosticRunning = false, internetChecking = false,
                    diagnosticReport = report.copy(stale = stale || changedNetwork), diagnosticProgress = "Completed",
                    internetAvailable = if (stale || changedNetwork) null else if (active != null && kind == DiagnosticKind.INTERNET) report.internetOk
                        else if (active != null && report.internetOk) true else _state.value.internetAvailable)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (request == diagnosticRequest) _state.value = _state.value.copy(diagnosticRunning = false, internetChecking = false,
                    diagnosticError = "Diagnostic setup failed (${failure.javaClass.simpleName}); check physical network and settings. Raw error text omitted.")
            }
        }
    }

    fun toggleAutoStartOnBoot() {
        updateProfile { autoStartOnBoot = !autoStartOnBoot }
    }

    /**
     * Publishes a human-readable connection error into [MainUiState.lastError].
     * Called by UI/service when a connect attempt fails, so the failure is
     * always visible instead of dying silently.
     */
    fun reportConnectionError(message: String) {
        _state.value = _state.value.copy(lastError = message, isConnecting = false)
    }

    /** Clears [MainUiState.lastError] (e.g. after the user dismisses it). */
    fun clearError() {
        _state.value = _state.value.copy(lastError = null)
    }

    /** TCP reachability is not internet verification; use the running service snapshot. */
    fun checkAlive() {
        aliveJob?.cancel()
        val profile = _state.value.profile
        val id = _state.value.currentProfileId
        val endpoint = Utils.activeUpstream.value
        aliveJob = viewModelScope.launch {
            val alive = withContext(Dispatchers.IO) {
                val host = endpoint?.host ?: if (profile.useGatewayAsHost) gatewayLookup(getApplication()) else profile.host
                !host.isNullOrBlank() && NetworkUtils.isProxyAlive(host, endpoint?.port ?: profile.port)
            }
            if (_state.value.currentProfileId == id && Utils.activeUpstream.value === endpoint) {
                _state.value = _state.value.copy(isAlive = alive)
            }
        }
    }

    /**
     * Starts a periodic liveness probe (every 15 seconds) while the tunnel
     * is active. Idempotent: calling it while the loop is already running
     * is a no-op.
     */
    private fun startAlivePolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                checkAlive()
                delay(ALIVE_POLL_INTERVAL_MS)
            }
        }
    }

    /** Stops the periodic liveness probe (when the tunnel goes down). */
    private fun stopAlivePolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun selectProfile(id: String) {
        if (id == _state.value.currentProfileId) return
        invalidateDiagnostics()
        gatewayJob?.cancel(); gatewayRequest++
        viewModelScope.launch {
            // Save current profile JSON under its ID, then load the new one.
            persistCurrentProfileJson(_state.value.profile)
            settings.edit().putString("profile", id).apply()
            val raw = settings.getString(id, "").orEmpty()
            val next = Profile().apply {
                if (raw.isEmpty()) {
                    init()
                    name = profileNameFor(id)
                } else {
                    decodeJson(raw)
                }
                if (proxyType.isEmpty()) proxyType = "socks5"
            }
            next.setProfile(settings)
            _state.value = _state.value.copy(
                profile = next,
                currentProfileId = id,
                profiles = loadProfileList(),
            )
            refreshGateway()
        }
    }

    fun newProfile(name: String) {
        invalidateDiagnostics()
        gatewayJob?.cancel(); gatewayRequest++
        viewModelScope.launch {
            persistCurrentProfileJson(_state.value.profile)
            val nextId = nextProfileId()
            val p = Profile().apply {
                init()
                this.name = if (name.isBlank()) profileNameFor(nextId) else name
                proxyType = "socks5"
            }
            settings.edit().putString("profile", nextId).apply()
            p.setProfile(settings)
            settings.edit().putString(nextId, p.toString()).apply()
            _state.value = _state.value.copy(
                profile = p,
                currentProfileId = nextId,
                profiles = loadProfileList(),
            )
        }
    }

    fun renameCurrent(newName: String) {
        if (newName.isBlank()) return
        updateProfile { name = newName }
        viewModelScope.launch {
            _state.value = _state.value.copy(profiles = loadProfileList())
        }
    }

    fun deleteCurrent() {
        gatewayJob?.cancel(); gatewayRequest++
        viewModelScope.launch {
            val cur = _state.value.currentProfileId
            settings.edit().remove(cur).apply()
            // Pick the first remaining profile (or create a fresh one).
            val remaining = loadProfileList().filter { it.id != cur }
            val next = remaining.firstOrNull()?.id ?: "1"
            val raw = settings.getString(next, "").orEmpty()
            val p = Profile().apply {
                if (raw.isEmpty()) {
                    init(); name = profileNameFor(next); proxyType = "socks5"
                } else decodeJson(raw)
            }
            settings.edit().putString("profile", next).apply()
            p.setProfile(settings)
            _state.value = _state.value.copy(
                profile = p,
                currentProfileId = next,
                profiles = remaining,
            )
            refreshGateway()
        }
    }

    private fun persistCurrentProfileJson(p: Profile) {
        val cur = _state.value.currentProfileId
        settings.edit().putString(cur, p.toString()).apply()
    }

    private fun nextProfileId(): String {
        val raw = settings.getString("profileValues", "") ?: ""
        val ids = raw.split("|").mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() }?.toIntOrNull() }
        val next = ((ids.maxOrNull() ?: 0) + 1).toString()
        // Maintain the legacy profileEntries / profileValues bookkeeping.
        val values = (ids + next.toInt()).joinToString("|") + "|"
        val entries = ids.joinToString("|") { profileNameFor(it.toString()) } +
            (if (ids.isNotEmpty()) "|" else "") + profileNameFor(next) + "|"
        settings.edit()
            .putString("profileValues", values)
            .putString("profileEntries", entries)
            .apply()
        return next
    }

    private fun profileNameFor(id: String): String =
        settings.getString("profile$id", null) ?: "Profile $id"

    private fun loadProfileList(): List<ProfileEntry> {
        val raw = settings.getString("profileValues", null)
        val ids = if (raw.isNullOrEmpty()) {
            // Bootstrap a single default profile.
            listOf("1").also {
                settings.edit().putString("profileValues", "1|").apply()
                settings.edit().putString("profileEntries", "Profile 1|").apply()
            }
        } else {
            raw.split("|").mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }
        }
        return ids.map { ProfileEntry(it, profileNameFor(it)) }
    }

    companion object {
        /** Interval between automatic liveness probes while the tunnel is up. */
        private const val ALIVE_POLL_INTERVAL_MS = 15_000L
    }
}
