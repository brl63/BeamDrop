package com.example.domain.usecase

import android.content.Context
import android.util.Log
import com.example.model.DiscoveredPeer
import com.example.network.NetworkUtils
import com.example.network.NsdDiscoveryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Domain coordinator responsible for unified peer discovery.
 * Aggregates local Android mDNS (NSD) peers and real-time PC/Browser Web client heartbeats
 * into reactive, stable StateFlows for the Lobby UI.
 */
class LobbyDiscoveryCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val nsdManager: NsdDiscoveryManager
) {
    private val tag = "LobbyCoordinator"

    private val _webClients = MutableStateFlow<Map<String, DiscoveredPeer>>(emptyMap())
    val webClients: StateFlow<Map<String, DiscoveredPeer>> = _webClients.asStateFlow()

    // Specifically expose active web clients (PC, Mac, Linux browsers) as a list
    val activeWebClients: StateFlow<List<DiscoveredPeer>> = _webClients.map { map ->
        map.values.toList().sortedByDescending { it.lastSeenTimestamp }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Unified discovered peers combining NSD and Web clients
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = combine(
        nsdManager.discoveredPeers,
        _webClients
    ) { nsdList, webMap ->
        val combined = ArrayList<DiscoveredPeer>()
        val seenIps = HashSet<String>()

        nsdList.forEach { peer ->
            combined.add(peer)
            seenIps.add(peer.ip)
        }

        webMap.values.forEach { webPeer ->
            if (!seenIps.contains(webPeer.ip)) {
                combined.add(webPeer)
            }
        }
        combined
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Periodic background prune for stale PC web client sessions (> 30s inactivity)
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(8000)
                val now = System.currentTimeMillis()
                val current = _webClients.value
                val active = current.filter { now - it.value.lastSeenTimestamp <= 30000L }
                if (active.size != current.size) {
                    Log.d(tag, "Pruned stale web clients: ${current.size - active.size} removed")
                    _webClients.value = active
                }
            }
        }
    }

    fun startDiscovery() {
        nsdManager.startDiscovery()
    }

    fun stopDiscovery() {
        nsdManager.stopDiscovery()
    }

    fun startBroadcasting(port: Int = NetworkUtils.DEFAULT_PORT) {
        nsdManager.startBroadcasting(port)
    }

    fun stopBroadcasting() {
        nsdManager.stopBroadcasting()
    }

    fun addManualPeer(ip: String, port: Int, name: String) {
        nsdManager.addManualPeer(ip, port, name)
    }

    fun registerWebClientHeartbeat(clientIp: String, deviceName: String, clientType: String) {
        val peerId = "web_$clientIp"
        val current = _webClients.value.toMutableMap()
        val existing = current[peerId]

        val updatedPeer = DiscoveredPeer(
            id = peerId,
            name = deviceName,
            ip = clientIp,
            port = NetworkUtils.DEFAULT_PORT,
            isWebClient = true,
            clientType = clientType,
            lastSeenTimestamp = System.currentTimeMillis()
        )

        // Only update map if changed or if heartbeat timestamp needs refresh
        if (existing == null || existing.name != deviceName || existing.clientType != clientType ||
            (System.currentTimeMillis() - existing.lastSeenTimestamp) > 3000L
        ) {
            current[peerId] = updatedPeer
            _webClients.value = current
            Log.d(tag, "Registered/Refreshed PC Web Client: $deviceName ($clientIp)")
        }
    }

    fun removeWebClient(clientIp: String) {
        val peerId = "web_$clientIp"
        val current = _webClients.value.toMutableMap()
        if (current.remove(peerId) != null) {
            _webClients.value = current
        }
    }
}
