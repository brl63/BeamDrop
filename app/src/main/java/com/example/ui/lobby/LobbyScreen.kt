package com.example.ui.lobby

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DiscoveredPeer
import com.example.ui.lobby.components.ActiveWebSessionsCard
import com.example.ui.lobby.components.DiscoveredPeerCard
import com.example.ui.lobby.components.LobbyHeader
import com.example.ui.lobby.components.QuickActionCards

@Composable
fun LobbyScreen(
    localIp: String?,
    hotspotSsid: String?,
    isReceiverActive: Boolean,
    discoveredPeers: List<DiscoveredPeer>,
    activeWebClients: List<DiscoveredPeer>,
    onSendClick: () -> Unit,
    onReceiveToggle: () -> Unit,
    onWebShareClick: () -> Unit,
    onManualIpClick: () -> Unit,
    onRefreshNetwork: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenScanner: () -> Unit,
    onShowMyQr: () -> Unit,
    onShareBluetooth: () -> Unit,
    onSendToPeer: (DiscoveredPeer) -> Unit,
    onStartSwarm: (List<DiscoveredPeer>) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSwarmReady = discoveredPeers.size >= 2
    val isSinglePeer = discoveredPeers.size == 1

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("lobby_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header (Brand, Docs action, QR, Network Pill)
        item(key = "lobby_header_item") {
            LobbyHeader(
                localIp = localIp,
                hotspotSsid = hotspotSsid,
                onRefreshNetwork = onRefreshNetwork,
                onOpenGuide = onOpenGuide,
                onOpenScanner = onOpenScanner,
                onShowMyQr = onShowMyQr,
                onShareBluetooth = onShareBluetooth
            )
        }

        // 2. Primary Actions (Send Files, Receive, Web Portal, Direct IP)
        item(key = "quick_actions_item") {
            QuickActionCards(
                isReceiverActive = isReceiverActive,
                onSendClick = onSendClick,
                onReceiveToggle = onReceiveToggle,
                onShowMyQr = onShowMyQr,
                onWebShareClick = onWebShareClick,
                onManualIpClick = onManualIpClick
            )
        }

        // Active Receiver Notice Banner (if receiver active, give direct QR action)
        if (isReceiverActive) {
            item(key = "receiver_active_banner_item") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("receiver_active_banner"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF10B981).copy(alpha = 0.15f)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFF10B981).copy(alpha = 0.25f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Hub,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Ready to Receive Files",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF10B981)
                                    )
                                )
                                Text(
                                    text = if (!hotspotSsid.isNullOrEmpty()) "Hotspot: $hotspotSsid" else "Wi-Fi: ${localIp ?: "Standby"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = onShowMyQr,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(
                                text = "Show QR",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }

        // 3. Dedicated Real-Time PC Web Client Card
        item(key = "active_web_sessions_item") {
            ActiveWebSessionsCard(
                webClients = activeWebClients,
                localIp = localIp,
                onSendToPeer = onSendToPeer,
                onOpenWebHub = onWebShareClick,
                onShowQr = onShowMyQr
            )
        }

        // 4. P2P Mesh Lobby Header & Controls
        item(key = "p2p_mesh_header_item") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lobby_status_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                border = BorderStroke(
                    1.dp,
                    if (isSwarmReady) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(
                                        if (isSwarmReady) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                        else MaterialTheme.colorScheme.primaryContainer,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isSwarmReady) Icons.Default.Hub else Icons.Default.People,
                                    contentDescription = "P2P Lobby",
                                    tint = if (isSwarmReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "Nearby Devices Lobby",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if (isSwarmReady) "Swarm mesh active • BitTorrent mode"
                                    else if (isSinglePeer) "1 peer connected • 1-to-1 mode"
                                    else "Scanning local network & mDNS...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Status Tag
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = when {
                                isSwarmReady -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                isSinglePeer -> Color(0xFF10B981).copy(alpha = 0.15f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            border = BorderStroke(
                                1.dp,
                                when {
                                    isSwarmReady -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                    isSinglePeer -> Color(0xFF10B981).copy(alpha = 0.4f)
                                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                }
                            )
                        ) {
                            Text(
                                text = when {
                                    isSwarmReady -> "🔥 Swarm (${discoveredPeers.size})"
                                    isSinglePeer -> "⚡ 1-to-1 (1)"
                                    else -> "0 Peers"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = when {
                                    isSwarmReady -> MaterialTheme.colorScheme.primary
                                    isSinglePeer -> Color(0xFF10B981)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    // Multi-Peer Swarm Action Button
                    AnimatedVisibility(visible = isSwarmReady) {
                        Column {
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { onStartSwarm(discoveredPeers) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("start_swarm_broadcast_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Start Swarm Mesh Transfer (${discoveredPeers.size} Peers)",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Discovered Peers List
        if (discoveredPeers.isEmpty()) {
            item(key = "empty_peers_item") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("empty_peers_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.surface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "No other devices detected yet",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Open BeamDrop on another phone, or open the URL on your PC browser to enter the lobby.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            items(
                items = discoveredPeers,
                key = { it.id }
            ) { peer ->
                DiscoveredPeerCard(
                    peer = peer,
                    onSend = { onSendToPeer(peer) }
                )
            }
        }
    }
}
