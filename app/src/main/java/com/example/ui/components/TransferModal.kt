package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.TransferSession
import com.example.model.TransferStatus
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.AccentRed
import com.example.ui.theme.CyanPrimary
import java.io.File

@Composable
fun TransferModal(
    session: TransferSession,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onInstallApk: (String) -> Unit,
    onOpenFile: (String) -> Unit
) {
    Dialog(
        onDismissRequest = {
            if (session.status == TransferStatus.COMPLETED ||
                session.status == TransferStatus.ERROR ||
                session.status == TransferStatus.CANCELLED
            ) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = session.status != TransferStatus.TRANSFERRING,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .testTag("transfer_progress_dialog"),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header Row with status badge & close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusText = when (session.status) {
                        TransferStatus.CONNECTING -> "Connecting..."
                        TransferStatus.TRANSFERRING -> if (session.isReceiver) "Receiving Stream" else "Sending Stream"
                        TransferStatus.COMPLETED -> "Transfer Complete!"
                        TransferStatus.ERROR -> "Transfer Failed"
                        TransferStatus.CANCELLED -> "Cancelled"
                        TransferStatus.IDLE -> "Ready"
                    }

                    val badgeColor = when (session.status) {
                        TransferStatus.COMPLETED -> AccentGreen
                        TransferStatus.ERROR, TransferStatus.CANCELLED -> AccentRed
                        TransferStatus.TRANSFERRING -> CyanPrimary
                        else -> AccentOrange
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(badgeColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = badgeColor
                            )
                        )
                    }

                    if (session.status != TransferStatus.TRANSFERRING && session.status != TransferStatus.CONNECTING) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("transfer_dismiss_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Topology Badge (Simple vs Swarm Mesh)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (session.topology == com.example.model.TransferTopology.SWARM)
                                CyanPrimary.copy(alpha = 0.18f)
                            else
                                AccentGreen.copy(alpha = 0.18f)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (session.topology == com.example.model.TransferTopology.SWARM)
                            Icons.Default.Send
                        else
                            Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (session.topology == com.example.model.TransferTopology.SWARM) CyanPrimary else AccentGreen
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (session.topology == com.example.model.TransferTopology.SWARM)
                            "Swarm Mode (P2P Mesh)"
                        else
                            "Simple Mode (1-to-1 Stream)",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (session.topology == com.example.model.TransferTopology.SWARM) CyanPrimary else AccentGreen
                        )
                    )
                }

                if (session.isSwarmTransitioning) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AccentOrange.copy(alpha = 0.15f))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "New peer joined! Upgrading stream to Swarm Mesh...",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = AccentOrange
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Batch Progress Pill (e.g. "Item 2 of 5")
                if (session.isBatchTransfer) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(CyanPrimary.copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Item ${session.batchCurrentIndex} of ${session.batchTotalCount}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = CyanPrimary
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Center Icon Avatar
                val avatarBg = when (session.status) {
                    TransferStatus.COMPLETED -> AccentGreen.copy(alpha = 0.15f)
                    TransferStatus.ERROR -> AccentRed.copy(alpha = 0.15f)
                    else -> CyanPrimary.copy(alpha = 0.15f)
                }

                val avatarIcon = when (session.status) {
                    TransferStatus.COMPLETED -> Icons.Default.CheckCircle
                    TransferStatus.ERROR -> Icons.Default.Error
                    else -> if (session.isApk) Icons.Default.Android
                    else if (session.isReceiver) Icons.Default.Download
                    else Icons.Default.Upload
                }

                val avatarTint = when (session.status) {
                    TransferStatus.COMPLETED -> AccentGreen
                    TransferStatus.ERROR -> AccentRed
                    else -> CyanPrimary
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(avatarBg)
                ) {
                    Icon(
                        imageVector = avatarIcon,
                        contentDescription = "Transfer Status",
                        tint = avatarTint,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // File Name
                Text(
                    text = session.fileName.ifEmpty { "Transfer Queue" },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                if (session.remotePeerName.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (session.isReceiver) "From: ${session.remotePeerName}" else "To: ${session.remotePeerName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Progress Bar & Speed Section
                if (session.status == TransferStatus.TRANSFERRING || session.status == TransferStatus.CONNECTING) {
                    val animatedProgress by animateFloatAsState(
                        targetValue = session.progressFraction,
                        label = "progress"
                    )

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = CyanPrimary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = session.formattedTransferred,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "${session.progressPercent}%",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Transfer Speed Readout
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Speed: ${session.formattedSpeed}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = CyanPrimary
                            )
                        )
                    }

                    // Swarm Mode Chunk & Peer Details
                    if (session.topology == com.example.model.TransferTopology.SWARM) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Mesh Chunks",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${session.completedChunksCount} / ${session.totalChunksCount} (1 MB ea)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyanPrimary
                                )
                            }

                            if (session.swarmPeers.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Connected Swarm Peers (${session.swarmPeers.size}):",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                session.swarmPeers.forEach { peer ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(if (peer.isSeeder) AccentGreen else CyanPrimary)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = peer.name,
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (peer.isSeeder) AccentGreen.copy(alpha = 0.15f)
                                                    else CyanPrimary.copy(alpha = 0.15f)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (peer.isSeeder) "Seeding" else "Leeching (${peer.progressPercent}%)",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = if (peer.isSeeder) AccentGreen else CyanPrimary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("transfer_cancel_button")
                    ) {
                        Text("Cancel Transfer")
                    }
                } else if (session.status == TransferStatus.COMPLETED) {
                    Text(
                        text = "Transferred ${session.formattedTransferred}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Received Files Action List (for Receiver mode)
                    if (session.isReceiver && session.completedFilePaths.isNotEmpty()) {
                        Text(
                            text = "Received Files (${session.completedFilePaths.size})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(session.completedFilePaths) { filePath ->
                                val file = File(filePath)
                                val isFileApk = file.name.endsWith(".apk", ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isFileApk) Icons.Default.Android else Icons.Default.InsertDriveFile,
                                            contentDescription = null,
                                            tint = if (isFileApk) CyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = file.name,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                         if (isFileApk) {
                                            Button(
                                                onClick = { onInstallApk(filePath) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = Color.White
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text("Install", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        } else {
                                            OutlinedButton(
                                                onClick = { onOpenFile(filePath) },
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text("Open", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    } else if (session.isReceiver && session.savedFilePath != null) {
                        // Single file fallback
                        if (session.isApk) {
                            Button(
                                onClick = { onInstallApk(session.savedFilePath) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("install_apk_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(imageVector = Icons.Default.Android, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Install APK", fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Button(
                            onClick = { onOpenFile(session.savedFilePath) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_file_button")
                        ) {
                            Icon(imageVector = Icons.Default.InsertDriveFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open File", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("transfer_done_button")
                    ) {
                        Text("Done")
                    }
                } else if (session.status == TransferStatus.ERROR || session.status == TransferStatus.CANCELLED) {
                    if (session.errorMessage != null) {
                        Text(
                            text = session.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AccentRed,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("transfer_error_dismiss_button")
                    ) {
                        Text("Dismiss")
                    }
                }
            }
        }
    }
}
