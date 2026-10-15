package com.example.network

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {

    const val DEFAULT_PORT = 8080
    const val SERVICE_TYPE = "_beamdrop._tcp."
    const val SERVICE_NAME_PREFIX = "BeamDrop-"

    /**
     * Resolves the current active IPv4 Wi-Fi or Hotspot local address.
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            // First pass: look specifically for wlan / ap interfaces
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val name = intf.name.lowercase()
                if (name.contains("wlan") || name.contains("ap") || name.contains("eth")) {
                    for (addr in Collections.list(intf.inetAddresses)) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            val host = addr.hostAddress
                            if (!host.isNullOrEmpty() && !host.startsWith("127.")) {
                                return host
                            }
                        }
                    }
                }
            }

            // Second pass: any valid non-loopback IPv4
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in Collections.list(intf.inetAddresses)) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (!host.isNullOrEmpty() && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore socket enumeration failures
        }
        return null
    }

    /**
     * Returns a human-friendly name for this device (e.g. "Samsung Galaxy S22" or "Pixel 8").
     */
    fun getDeviceDisplayName(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model"
        }
    }
}
