package org.proxydroid.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UpdateUiState(
    val visible: Boolean = false,
    val channel: UpdateChannel = UpdateChannel.STABLE,
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val installing: Boolean = false,
    val pendingInstall: Boolean = false,
    val awaitingPermission: Boolean = false,
    val progress: Float = 0f,
    val downloadBytes: Long = 0,
    val downloadTotal: Long = 0,
    val bytesPerSecond: Long = 0,
    val downloadAttempt: Int = 1,
    val downloadStatus: String? = null,
    val signatureReport: SignatureReport? = null,
    val build: UpdateBuild? = null,
    val file: File? = null,
    val message: String? = null,
    val error: String? = null,
)

class UpdateViewModel(app: Application) : AndroidViewModel(app) {
    private val updater = GitHubUpdater(app)
    private val mutable = MutableStateFlow(UpdateUiState())
    val state = mutable.asStateFlow()
    private var operation: Job? = null
    @Volatile private var request = 0L

    fun open() { mutable.update { it.copy(visible = true) }
        viewModelScope.launch {
            val installed = withContext(Dispatchers.IO) { runCatching { updater.installedSignatures() }.getOrDefault(emptySet()) }
            mutable.update { it.copy(signatureReport = it.signatureReport ?: SignatureReport(installed)) }
        }
    }
    fun dismiss() {
        operation?.cancel(); request++
        mutable.update { it.copy(visible = false, checking = false, downloading = false, installing = false, pendingInstall = false, awaitingPermission = false) }
    }
    fun selectChannel(channel: UpdateChannel) {
        operation?.cancel(); request++
        mutable.value = UpdateUiState(visible = true, channel = channel, signatureReport = mutable.value.signatureReport?.let { SignatureReport(it.installed) })
    }
    fun check() {
        operation?.cancel()
        val id = ++request
        val channel = mutable.value.channel
        mutable.update { it.copy(checking = true, build = null, file = null, error = null, message = null, signatureReport = it.signatureReport?.let { report -> SignatureReport(report.installed) }, downloadBytes = 0, downloadTotal = 0, progress = 0f) }
        operation = viewModelScope.launch {
            try {
                val build = withContext(Dispatchers.IO) { updater.check(channel) }
                if (id == request) mutable.update { it.copy(build = build, checking = false,
                    downloadBytes = updater.partialFile(build).length(), downloadTotal = build.offer.apk.size,
                    progress = updater.partialFile(build).length().toFloat() / build.offer.apk.size) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (id == request) mutable.update { it.copy(checking = false, error = failure.message ?: "Cannot check GitHub releases") }
            }
        }
    }
    fun download() {
        val build = mutable.value.build ?: return
        operation?.cancel()
        val id = ++request
        mutable.update { it.copy(downloading = true, file = null, error = null, message = null, pendingInstall = false, signatureReport = it.signatureReport?.let { report -> SignatureReport(report.installed) }) }
        operation = viewModelScope.launch {
            try {
                val downloaded = withContext(Dispatchers.IO) {
                    updater.download(build) { progress ->
                        if (id == request) mutable.update { it.copy(progress = progress.bytes.toFloat() / progress.total,
                            downloadBytes = progress.bytes, downloadTotal = progress.total,
                            bytesPerSecond = progress.bytesPerSecond, downloadAttempt = progress.attempt, downloadStatus = progress.status) }
                    }
                }
                if (id == request) mutable.update { it.copy(file = downloaded.file, signatureReport = downloaded.signatures, downloading = false, pendingInstall = true, message = "APK verified. Opening Android installer; confirm installation there. You can also select the full path below to retry.") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (id == request) mutable.update { it.copy(downloading = false, pendingInstall = false,
                    signatureReport = (failure as? SignatureValidationException)?.report ?: it.signatureReport,
                    downloadBytes = updater.partialFile(build).length(), error = failure.message ?: "Cannot download the update; saved bytes can be resumed") }
            }
        }
    }
    fun install(activity: android.app.Activity) {
        val file = mutable.value.file ?: return
        val build = mutable.value.build ?: return
        if (mutable.value.installing || activity.isFinishing || activity.isDestroyed) return
        val id = request
        mutable.update { it.copy(installing = true, pendingInstall = false, awaitingPermission = false, error = null) }
        operation = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { updater.verifyReady(file, build) }
                if (id != request || !mutable.value.visible || activity.isFinishing || activity.isDestroyed) return@launch
                if (activity is androidx.lifecycle.LifecycleOwner && !activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                    mutable.update { it.copy(installing = false, pendingInstall = true) }
                    return@launch
                }
                val opened = updater.install(activity, file)
                mutable.update { it.copy(installing = false, awaitingPermission = !opened,
                    message = if (opened) "Android installer opened. Confirm installation there; select the APK path to retry if needed."
                        else "Allow installs from LocProxyTV in Android settings. Installation resumes on return; the APK path also retries without downloading.") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (id == request) mutable.update { it.copy(installing = false, signatureReport = (failure as? SignatureValidationException)?.report ?: it.signatureReport, error = failure.message ?: "Android installer is unavailable. The verified APK is retained; select its path to retry.") }
            }
        }
    }

    fun onResume(activity: android.app.Activity) {
        if (mutable.value.visible && mutable.value.awaitingPermission && updater.canInstall()) install(activity)
    }
}
