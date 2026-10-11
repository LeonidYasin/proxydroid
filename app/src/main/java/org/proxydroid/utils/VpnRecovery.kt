package org.proxydroid.utils

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.proxydroid.ProxyDroidVpnService
import kotlin.coroutines.resume

object VpnRecovery {
    /** Completion comes from the service AFTER native tasks and TUN have closed. */
    suspend fun stopAndAwait(context: Context): Boolean = withTimeoutOrNull(10_000L) {
        suspendCancellableCoroutine<Boolean> { continuation ->
            val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(resultCode: Int, data: android.os.Bundle?) {
                    if (continuation.isActive) continuation.resume(resultCode == 0)
                }
            }
            try {
                context.startService(Intent(context, ProxyDroidVpnService::class.java)
                    .setAction(ProxyDroidVpnService.ACTION_STOP)
                    .putExtra(ProxyDroidVpnService.EXTRA_STOP_ACK, receiver))
            } catch (failure: Exception) {
                Utils.setLastError("Android refused VPN stop: ${failure.javaClass.simpleName}")
                if (continuation.isActive) continuation.resume(false)
            }
        }
    } ?: false
}
