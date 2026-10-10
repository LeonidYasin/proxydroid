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

    fun open() { mutable.update { it.copy(visible = true) } }
    fun dismiss() {
        operation?.cancel(); request++
        mutable.update { it.copy(visible = false, checking = false, downloading = false, installing = false, pendingInstall = false, awaitingPermission = false) }
    }
    fun selectChannel(channel: UpdateChannel) {
        operation?.cancel(); request++
        mutable.value = UpdateUiState(visible = true, channel = channel)
    }
    fun check() {
        operation?.cancel()
        val id = ++request
        val channel = mutable.value.channel
        mutable.update { it.copy(checking = true, build = null, file = null, error = null, message = null) }
        operation = viewModelScope.launch {
            try {
                val build = withContext(Dispatchers.IO) { updater.check(channel) }
                if (id == request) mutable.update { it.copy(build = build, checking = false) }
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
        mutable.update { it.copy(downloading = true, progress = 0f, file = null, error = null, message = null) }
        operation = viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    updater.download(build) { progress ->
                        if (id == request) mutable.update { it.copy(progress = progress) }
                    }
                }
                if (id == request) mutable.update { it.copy(file = file, downloading = false, pendingInstall = true, message = "APK verified. Opening Android installer; confirm installation there. You can also select the full path below to retry.") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (id == request) mutable.update { it.copy(downloading = false, error = failure.message ?: "Cannot download the update") }
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
                        else "Allow installs from ProxyDroid in Android settings. Installation resumes on return; the APK path also retries without downloading.") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (id == request) mutable.update { it.copy(installing = false, error = failure.message ?: "Android installer is unavailable. The verified APK is retained; select its path to retry.") }
            }
        }
    }

    fun onResume(activity: android.app.Activity) {
        if (mutable.value.visible && mutable.value.awaitingPermission && updater.canInstall()) install(activity)
    }
}
