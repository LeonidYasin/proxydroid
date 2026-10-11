package org.proxydroid.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.proxydroid.utils.DeviceDiagnostics
import org.proxydroid.utils.DiagnosticShareServer
import java.net.Inet4Address

@Composable
fun DiagnosticShareDialog(report: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; closeFocus.requestFocus() }
    var server by remember { mutableStateOf<DiagnosticShareServer?>(null) }
    var image by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { server?.close(); onDismiss() }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); server?.close() }
    }
    LaunchedEffect(report) {
        var local: DiagnosticShareServer? = null
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = DeviceDiagnostics.physicalNetwork(context) ?: error("Нет физической сети")
            val address = cm.getLinkProperties(network)?.linkAddresses?.map { it.address }?.filterIsInstance<Inet4Address>()?.firstOrNull { !it.isLoopbackAddress }
                ?: error("Нет IPv4-адреса локальной сети")
            val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.ROOT).format(java.util.Date())
            val filename = "LocProxyTV-v${org.proxydroid.BuildConfig.VERSION_NAME}-diagnostics-$stamp.txt"
            withContext(Dispatchers.IO) { local = DiagnosticShareServer(address, report, filename = filename) }
            val sharing = checkNotNull(local)
            server = local
            val url = sharing.url
            image = withContext(Dispatchers.Default) {
                val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 512, 512, mapOf(EncodeHintType.MARGIN to 4))
                val pixels = IntArray(512 * 512) { i -> if (matrix[i % 512, i / 512]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
                Bitmap.createBitmap(pixels, 512, 512, Bitmap.Config.ARGB_8888)
            }
            while (sharing.isOpen && DeviceDiagnostics.physicalNetwork(context) == network && cm.getLinkProperties(network)?.linkAddresses?.any { it.address == address } == true) delay(1000)
            sharing.close(); error = "Ссылка закрыта: истекли 5 минут или изменилась сеть. Создайте новую."
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "Не удалось открыть локальную передачу" }
        finally { local?.close() }
    }
    val height = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp - 48.dp).coerceAtLeast(200.dp)
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 900.dp).fillMaxWidth(0.94f).heightIn(max = height).testTag("diagnostic-share-dialog"),
            shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Отчёт на смартфон", style = MaterialTheme.typography.titleLarge)
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val qrSize = minOf(220.dp, maxHeight, if (maxWidth < 480.dp && maxHeight >= 360.dp) maxHeight / 2 else maxWidth / 2)
                    @Composable fun qr() {
                        if (error == null) image?.let { Image(it.asImageBitmap(), "QR для скачивания отчёта", Modifier.size(qrSize).testTag("diagnostic-share-qr")) }
                    }
                    @Composable fun information(modifier: Modifier) {
                        Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Телефон и приставка — в одной локальной сети. Камера телефона откроет скачивание TXT.", style = MaterialTheme.typography.bodySmall)
                            if (error == null) server?.let { Text(it.url, style = MaterialTheme.typography.bodySmall) }
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            Text("До 5 минут, закрытие окна отключает ссылку. Локальный HTTP без шифрования: только доверенная сеть. QR даёт доступ к адресам сети/модели, но не паролю прокси. Если не открывается: VPN телефона / изоляция Wi-Fi клиентов.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (maxWidth >= 480.dp || maxHeight < 360.dp) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            qr(); information(Modifier.weight(1f))
                        }
                    } else {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                            qr(); information(Modifier.weight(1f).fillMaxWidth())
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.tvRemoteFocus().focusRequester(closeFocus).fillMaxWidth().testTag("close-diagnostic-share")) { Text("Закрыть передачу") }
            }
        }
    }
}
