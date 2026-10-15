package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object HotspotManager {
    private const val TAG = "HotspotManager"

    private var localHotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null
    var activeHotspotSsid: String? = null
        private set
    var activeHotspotPassword: String? = null
        private set

    val isHotspotRunning: Boolean
        get() = localHotspotReservation != null

    /**
     * Automatically creates an offline Local-Only Wi-Fi Hotspot on Android 8.0+
     * Requires ZERO cellular data or internet connection.
     */
    fun startLocalOnlyHotspot(
        context: Context,
        onReady: (ssid: String, pass: String, ip: String) -> Unit,
        onFailed: (String) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            onFailed("Local-only hotspot requires Android 8.0 or newer")
            return
        }

        if (localHotspotReservation != null) {
            val ssid = activeHotspotSsid ?: "BeamDrop-Hotspot"
            val pass = activeHotspotPassword ?: ""
            onReady(ssid, pass, "192.168.43.1")
            return
        }

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiManager == null) {
            onFailed("WifiManager not available")
            return
        }

        try {
            wifiManager.startLocalOnlyHotspot(
                object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                        super.onStarted(reservation)
                        localHotspotReservation = reservation

                        var ssid = "BeamDrop-Hotspot"
                        var pass = ""

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val config = reservation.softApConfiguration
                            ssid = config?.ssid ?: ssid
                            pass = config?.passphrase ?: ""
                        } else {
                            @Suppress("DEPRECATION")
                            val config = reservation.wifiConfiguration
                            if (config != null) {
                                ssid = config.SSID?.replace("\"", "") ?: ssid
                                pass = config.preSharedKey?.replace("\"", "") ?: ""
                            }
                        }

                        activeHotspotSsid = ssid
                        activeHotspotPassword = pass
                        Log.d(TAG, "Local hotspot started: SSID=$ssid, Password=$pass")

                        // Gateway IP for Android local hotspot is universally 192.168.43.1
                        val ip = NetworkUtils.getLocalIpAddress() ?: "192.168.43.1"
                        onReady(ssid, pass, ip)
                    }

                    override fun onStopped() {
                        super.onStopped()
                        Log.d(TAG, "Local hotspot stopped")
                        localHotspotReservation = null
                        activeHotspotSsid = null
                        activeHotspotPassword = null
                    }

                    override fun onFailed(reason: Int) {
                        super.onFailed(reason)
                        Log.e(TAG, "Local hotspot failed with reason: $reason")
                        localHotspotReservation = null
                        activeHotspotSsid = null
                        activeHotspotPassword = null
                        onFailed("Failed to create hotspot (Code $reason)")
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting local hotspot", e)
            onFailed("Cannot create hotspot: ${e.message}")
        }
    }

    fun stopLocalHotspot() {
        try {
            localHotspotReservation?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing hotspot", e)
        }
        localHotspotReservation = null
        activeHotspotSsid = null
        activeHotspotPassword = null
    }

    /**
     * Connects sender's device to receiver's hotspot automatically.
     */
    suspend fun connectToHotspot(
        context: Context,
        ssid: String,
        password: String
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

        if (connectivityManager == null) {
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .apply {
                    if (password.isNotEmpty()) {
                        setWpa2Passphrase(password)
                    }
                }
                .build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()

            var resumed = false

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (!resumed) {
                        resumed = true
                        connectivityManager.bindProcessToNetwork(network)
                        Log.d(TAG, "Successfully bound process to hotspot network: $ssid")
                        continuation.resume(true)
                    }
                }

                override fun onUnavailable() {
                    if (!resumed) {
                        resumed = true
                        Log.w(TAG, "Hotspot connection unavailable: $ssid")
                        continuation.resume(false)
                    }
                }
            }

            try {
                connectivityManager.requestNetwork(request, callback, 15000)
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting network for hotspot", e)
                if (!resumed) {
                    continuation.resume(false)
                }
            }

            continuation.invokeOnCancellation {
                try {
                    connectivityManager.unregisterNetworkCallback(callback)
                } catch (_: Exception) {}
            }
        } else {
            // Android 9 and below
            try {
                if (wifiManager != null) {
                    @Suppress("DEPRECATION")
                    val wifiConfig = WifiConfiguration().apply {
                        SSID = "\"$ssid\""
                        if (password.isNotEmpty()) {
                            preSharedKey = "\"$password\""
                        } else {
                            allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                        }
                    }
                    @Suppress("DEPRECATION")
                    val netId = wifiManager.addNetwork(wifiConfig)
                    @Suppress("DEPRECATION")
                    wifiManager.disconnect()
                    @Suppress("DEPRECATION")
                    wifiManager.enableNetwork(netId, true)
                    @Suppress("DEPRECATION")
                    wifiManager.reconnect()
                    continuation.resume(true)
                } else {
                    continuation.resume(false)
                }
            } catch (e: Exception) {
                continuation.resume(false)
            }
        }
    }
}
