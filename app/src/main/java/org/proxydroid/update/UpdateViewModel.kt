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
        mutable.update { it.copy(visible = false, checking = false, downloading = false) }
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
                if (id == request) mutable.update { it.copy(file = file, downloading = false, message = "APK checksum, package and certificate verified. Installation needs your confirmation.") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (id == request) mutable.update { it.copy(downloading = false, error = failure.message ?: "Cannot download the update") }
            }
        }
    }
    fun install() {
        val file = mutable.value.file ?: return
        try {
            if (!updater.install(file)) mutable.update { it.copy(message = "Allow installs from ProxyDroid in Android settings, return here and press Install again.") }
        } catch (failure: Exception) {
            mutable.update { it.copy(error = failure.message ?: "Android package installer is unavailable on this device") }
        }
    }
}
