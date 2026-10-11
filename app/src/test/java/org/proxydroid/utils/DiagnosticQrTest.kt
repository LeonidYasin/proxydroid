package org.proxydroid.utils
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.*
import org.junit.Test
class DiagnosticQrTest {
    @Test fun cameraCanDecodeTheExactTemporaryUrl() {
        val url = "http://192.168.43.123:54321/" + "ab".repeat(24) + "/diagnostics.txt"
        val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 512, 512, mapOf(EncodeHintType.MARGIN to 4))
        val pixels = IntArray(512 * 512) { i -> if (matrix[i % 512, i / 512]) 0xff000000.toInt() else 0xffffffff.toInt() }
        val decoded = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(512, 512, pixels))))
        assertEquals(url, decoded.text)
    }
}
