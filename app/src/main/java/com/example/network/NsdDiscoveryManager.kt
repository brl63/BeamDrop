package com.example.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.example.model.DiscoveredPeer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class NsdDiscoveryManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val tag = "NsdDiscoveryManager"
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private var multicastLock: WifiManager.MulticastLock? = null

    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = _discoveredPeers.asStateFlow()

    private val peersMap = ConcurrentHashMap<String, DiscoveredPeer>()
    private val resolveQueue = ConcurrentLinkedQueue<NsdServiceInfo>()
    private var isResolving = false

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registeredServiceName: String? = null

    private var isRegistered = false
    private var isDiscovering = false

    /**
     * Broadcasts this device as a receiver on the local network.
     */
    fun startBroadcasting(port: Int = NetworkUtils.DEFAULT_PORT) {
        if (nsdManager == null || isRegistered) return

        val myServiceName = "${NetworkUtils.SERVICE_NAME_PREFIX}${NetworkUtils.getDeviceDisplayName()}"
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = myServiceName
            serviceType = "_beamdrop._tcp."
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(service: NsdServiceInfo) {
                registeredServiceName = service.serviceName
                isRegistered = true
                Log.d(tag, "Service registered successfully: ${service.serviceName}")
            }

            override fun onRegistrationFailed(service: NsdServiceInfo, errorCode: Int) {
                Log.e(tag, "Service registration failed: $errorCode")
                isRegistered = false
            }

            override fun onServiceUnregistered(service: NsdServiceInfo) {
                isRegistered = false
                Log.d(tag, "Service unregistered: ${service.serviceName}")
            }

            override fun onUnregistrationFailed(service: NsdServiceInfo, errorCode: Int) {
                Log.e(tag, "Service unregistration failed: $errorCode")
            }
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(tag, "Error registering NSD service", e)
        }
    }

    fun stopBroadcasting() {
        if (nsdManager != null && isRegistered && registrationListener != null) {
            try {
                nsdManager.unregisterService(registrationListener)
            } catch (e: Exception) {
                Log.e(tag, "Error unregistering service", e)
            }
            isRegistered = false
            registrationListener = null
            registeredServiceName = null
        }
    }

    /**
     * Scans the local network for other BeamDrop devices.
     */
    fun startDiscovery() {
        if (nsdManager == null || isDiscovering) return

        // Acquire Multicast lock for network discovery packet reception
        try {
            if (multicastLock == null) {
                multicastLock = wifiManager?.createMulticastLock("BeamDropDiscoveryLock")?.apply {
                    setReferenceCounted(true)
                    acquire()
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Could not acquire MulticastLock", e)
        }

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                isDiscovering = true
                Log.d(tag, "NSD Discovery started for $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(tag, "Service found: ${service.serviceName}")
                // Ignore self
                val localRegName = registeredServiceName
                if (localRegName != null && service.serviceName == localRegName) {
                    return
                }

                enqueueResolve(service)
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(tag, "Service lost: ${service.serviceName}")
                peersMap.remove(service.serviceName)
                updatePeersFlow()
            }

            override fun onDiscoveryStopped(serviceType: String) {
                isDiscovering = false
                Log.d(tag, "Discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(tag, "Start discovery failed: $errorCode")
                isDiscovering = false
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(tag, "Stop discovery failed: $errorCode")
                isDiscovering = false
            }
        }

        try {
            nsdManager.discoverServices("_beamdrop._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(tag, "Error discovering NSD services", e)
        }
    }

    fun stopDiscovery() {
        if (nsdManager != null && isDiscovering && discoveryListener != null) {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (e: Exception) {
                Log.e(tag, "Error stopping discovery", e)
            }
            isDiscovering = false
            discoveryListener = null
        }

        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
            multicastLock = null
        } catch (_: Exception) {}
    }

    private fun enqueueResolve(service: NsdServiceInfo) {
        resolveQueue.offer(service)
        processNextResolve()
    }

    private fun processNextResolve() {
        val nextService: NsdServiceInfo
        synchronized(this) {
            if (isResolving || resolveQueue.isEmpty() || nsdManager == null) return
            nextService = resolveQueue.poll() ?: return
            isResolving = true
        }

        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(tag, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
                synchronized(this@NsdDiscoveryManager) {
                    isResolving = false
                    processNextResolve()
                }
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host: InetAddress? = serviceInfo.host
                val ip = host?.hostAddress
                val port = serviceInfo.port
                val name = serviceInfo.serviceName.removePrefix(NetworkUtils.SERVICE_NAME_PREFIX)

                if (!ip.isNullOrEmpty()) {
                    val peer = DiscoveredPeer(
                        id = serviceInfo.serviceName,
                        name = name.ifEmpty { "Nearby Friend" },
                        ip = ip,
                        port = if (port > 0) port else NetworkUtils.DEFAULT_PORT
                    )
                    peersMap[serviceInfo.serviceName] = peer
                    updatePeersFlow()
                }

                synchronized(this@NsdDiscoveryManager) {
                    isResolving = false
                    processNextResolve()
                }
            }
        }

        try {
            nsdManager?.resolveService(nextService, resolveListener)
        } catch (e: Exception) {
            Log.e(tag, "Exception during resolveService", e)
            isResolving = false
            processNextResolve()
        }
    }

    /**
     * Manually adds a peer (e.g. from user input for direct hotspot IP).
     */
    fun addManualPeer(ip: String, port: Int = NetworkUtils.DEFAULT_PORT, name: String = "Direct Peer") {
        val peer = DiscoveredPeer(
            id = "manual_$ip:$port",
            name = name,
            ip = ip,
            port = port
        )
        peersMap[peer.id] = peer
        updatePeersFlow()
    }

    private fun updatePeersFlow() {
        scope.launch(Dispatchers.Main) {
            _discoveredPeers.value = peersMap.values.toList()
        }
    }
}
