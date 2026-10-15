package com.example.ui.lobby.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.WifiTethering
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

@Composable
fun DiscoveredPeerCard(
    peer: DiscoveredPeer,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isHotspotPeer = !peer.wifiSsid.isNullOrEmpty()
    val isWeb = peer.isWebClient

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("discovered_peer_card_${peer.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            when {
                isHotspotPeer -> Color(0xFF00E5FF).copy(alpha = 0.4f)
                isWeb -> Color(0xFF10B981).copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            }
        )
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
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Device Avatar Icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            when {
                                isHotspotPeer -> Color(0xFF00E5FF).copy(alpha = 0.15f)
                                isWeb -> Color(0xFF10B981).copy(alpha = 0.15f)
                                else -> MaterialTheme.colorScheme.primaryContainer
                            },
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            isHotspotPeer -> Icons.Default.WifiTethering
                            peer.name.contains("Windows", ignoreCase = true) -> Icons.Default.Computer
                            peer.name.contains("Mac", ignoreCase = true) -> Icons.Default.Laptop
                            isWeb -> Icons.Default.Laptop
                            else -> Icons.Default.PhoneAndroid
                        },
                        contentDescription = "Peer Device",
                        tint = when {
                            isHotspotPeer -> Color(0xFF00E5FF)
                            isWeb -> Color(0xFF10B981)
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = peer.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )

                        // Client Type Tag
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when {
                                isHotspotPeer -> Color(0xFF00E5FF).copy(alpha = 0.12f)
                                isWeb -> Color(0xFF10B981).copy(alpha = 0.12f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = when {
                                    isHotspotPeer -> "Hotspot Peer"
                                    isWeb -> peer.clientType
                                    else -> "Android"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = when {
                                    isHotspotPeer -> Color(0xFF00E5FF)
                                    isWeb -> Color(0xFF10B981)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = if (isHotspotPeer) "Direct Hotspot • ${peer.ip}" else "${peer.ip}:${peer.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = onSend,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(38.dp)
                    .testTag("peer_send_button_${peer.id}"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Beam",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
