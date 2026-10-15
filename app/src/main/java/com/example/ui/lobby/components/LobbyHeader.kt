package com.example.ui.lobby.components

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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LobbyHeader(
    localIp: String?,
    hotspotSsid: String?,
    onRefreshNetwork: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenScanner: () -> Unit,
    onShowMyQr: () -> Unit,
    onShareBluetooth: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("lobby_header")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // App Branding
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF0284C7), Color(0xFF10B981))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NearMe,
                        contentDescription = "BeamDrop Logo",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Beam",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Drop",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Clean P2P & PC Web Mesh",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Action Icons Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Interactive Guide & Documentation Button
                FilledTonalIconButton(
                    onClick = onOpenGuide,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("docs_guide_button"),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = "App Guide & Documentation",
                        modifier = Modifier.size(20.dp)
                    )
                }

                // QR Scanner
                FilledTonalIconButton(
                    onClick = onOpenScanner,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("scan_qr_header_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan Peer QR",
                        modifier = Modifier.size(20.dp)
                    )
                }

                // My QR
                FilledTonalIconButton(
                    onClick = onShowMyQr,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("show_qr_header_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = "Show Pairing QR",
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Bluetooth APK Share
                FilledTonalIconButton(
                    onClick = onShareBluetooth,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("bluetooth_share_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = "Share APK via Bluetooth",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Network Status Pill
        NetworkStatusPill(
            localIp = localIp,
            hotspotSsid = hotspotSsid,
            onRefresh = onRefreshNetwork,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
