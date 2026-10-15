package com.example.helper

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.camera.core.ImageProxy
import com.example.model.DiscoveredPeer
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import java.util.EnumMap

object QrCodeHelper {

    private val reader = MultiFormatReader().apply {
        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java)
        hints[DecodeHintType.POSSIBLE_FORMATS] = listOf(BarcodeFormat.QR_CODE)
        setHints(hints)
    }

    /**
     * Generates a high-contrast QR code bitmap from text.
     */
    fun generateQrCodeBitmap(content: String, sizePx: Int = 512): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    val color = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                    bitmap.setPixel(x, y, color)
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Encodes this device's connection details (and optional local hotspot credentials) into a handshake URI.
     */
    fun buildHandshakePayload(
        ip: String,
        port: Int,
        deviceName: String,
        wifiSsid: String? = null,
        wifiPassword: String? = null
    ): String {
        val encodedName = Uri.encode(deviceName)
        val sb = StringBuilder("beamdrop://$ip:$port?name=$encodedName")
        if (!wifiSsid.isNullOrEmpty()) {
            sb.append("&ssid=").append(Uri.encode(wifiSsid))
        }
        if (!wifiPassword.isNullOrEmpty()) {
            sb.append("&pass=").append(Uri.encode(wifiPassword))
        }
        return sb.toString()
    }

    /**
     * Parses scanned QR code text into a DiscoveredPeer.
     * Supports formats:
     * - beamdrop://192.168.1.50:8080?name=Pixel&ssid=...&pass=...
     * - {"ip":"192.168.1.50","port":8080,"name":"Pixel","ssid":"...","pass":"..."}
     * - http://192.168.1.50:8080
     * - 192.168.1.50:8080
     */
    fun parseHandshakeContent(qrText: String): DiscoveredPeer? {
        val trimmed = qrText.trim()
        if (trimmed.isEmpty()) return null

        try {
            // 1. Check URI format: beamdrop:// or http://
            if (trimmed.startsWith("beamdrop://", ignoreCase = true) ||
                trimmed.startsWith("http://", ignoreCase = true)
            ) {
                val uri = Uri.parse(trimmed)
                val host = uri.host ?: uri.authority?.substringBefore(':')
                val port = if (uri.port != -1) uri.port else 8080
                val name = uri.getQueryParameter("name") ?: "Direct Peer ($host)"
                val ssid = uri.getQueryParameter("ssid")
                val pass = uri.getQueryParameter("pass")

                if (!host.isNullOrEmpty() && isValidIp(host)) {
                    return DiscoveredPeer(
                        id = "qr_${host}_$port",
                        name = name,
                        ip = host,
                        port = port,
                        wifiSsid = ssid,
                        wifiPassword = pass
                    )
                }
            }

            // 2. Check JSON format
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                val json = JSONObject(trimmed)
                val ip = json.optString("ip", "")
                val port = json.optInt("port", 8080)
                val name = json.optString("name", "Direct Peer ($ip)")
                val ssid = json.optString("ssid", "").ifEmpty { null }
                val pass = json.optString("pass", "").ifEmpty { null }

                if (ip.isNotEmpty() && isValidIp(ip)) {
                    return DiscoveredPeer(
                        id = "qr_${ip}_$port",
                        name = name,
                        ip = ip,
                        port = port,
                        wifiSsid = ssid,
                        wifiPassword = pass
                    )
                }
            }

            // 3. Check plain IP or IP:PORT format
            val hostPart = trimmed.substringBefore(':').trim()
            val portPart = if (trimmed.contains(':')) trimmed.substringAfter(':').trim() else "8080"
            val port = portPart.toIntOrNull() ?: 8080

            if (isValidIp(hostPart)) {
                return DiscoveredPeer(
                    id = "qr_${hostPart}_$port",
                    name = "Direct Peer ($hostPart)",
                    ip = hostPart,
                    port = port
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun isValidIp(ip: String): Boolean {
        val parts = ip.split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            part.toIntOrNull()?.let { it in 0..255 } == true
        }
    }

    /**
     * Decodes a QR code directly from CameraX ImageProxy YUV stream.
     */
    fun decodeQrFromImageProxy(imageProxy: ImageProxy): String? {
        return try {
            val plane = imageProxy.planes[0]
            val buffer = plane.buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            val width = imageProxy.width
            val height = imageProxy.height
            val source = PlanarYUVLuminanceSource(
                bytes,
                width,
                height,
                0,
                0,
                width,
                height,
                false
            )
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val result = reader.decodeWithState(binaryBitmap)
            reader.reset()
            result.text
        } catch (_: Exception) {
            reader.reset()
            null
        }
    }
}
