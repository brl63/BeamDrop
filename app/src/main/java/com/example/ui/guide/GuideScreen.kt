package com.example.ui.guide

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onNavigateBack()
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Quick Start", "PC Web Hub", "Swarm Mesh", "Architecture")

    Scaffold(
        modifier = modifier.testTag("guide_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "BeamDrop Documentation",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "User Guide & System Architecture",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("guide_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Lobby"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Color(0xFF00E5FF),
                modifier = Modifier.testTag("guide_tab_row")
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.testTag("guide_tab_$index")
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selectedTabIndex) {
                    0 -> quickStartContent()
                    1 -> pcWebHubContent()
                    2 -> swarmMeshContent()
                    3 -> architectureContent()
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.quickStartContent() {
    item {
        GuideHeroCard(
            title = "Phone-to-Phone Quickstart",
            subtitle = "Transfer apps, media, and folders without cables or cellular data.",
            icon = Icons.Default.PhoneAndroid,
            gradientColors = listOf(Color(0xFF00E5FF), Color(0xFF0284C7))
        )
    }

    item {
        GuideStepCard(
            stepNumber = "1",
            title = "Connect to Same Network or Start Hotspot",
            description = "Both phones must be on the same Wi-Fi. If you are outdoors without Wi-Fi, the receiver simply toggles 'Receive' to automatically launch an in-app Local Hotspot with zero cellular data needed.",
            icon = Icons.Default.Wifi
        )
    }

    item {
        GuideStepCard(
            stepNumber = "2",
            title = "Discover Nearby Peers in Lobby",
            description = "Phones running BeamDrop automatically discover each other in seconds using zero-config mDNS (NSD). They appear right in the Lobby screen with device names.",
            icon = Icons.Default.Hub
        )
    }

    item {
        GuideStepCard(
            stepNumber = "3",
            title = "Select Files & Beam Instantly",
            description = "Tap 'Send Files' to browse Apps (with automatic APK extraction), Photos, Videos, Audio, or full directories. Tap 'Send' to beam them at maximum Wi-Fi bandwidth (up to 40+ MB/s).",
            icon = Icons.Default.Speed
        )
    }

    item {
        GuideStepCard(
            stepNumber = "4",
            title = "Instant QR Code Pairing",
            description = "If you don't see a device immediately, tap the QR icon at the top of the screen on the receiving phone, and scan it with the sending phone. Instant direct pairing!",
            icon = Icons.Default.WifiTethering
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.pcWebHubContent() {
    item {
        GuideHeroCard(
            title = "Zero-Install PC Web Hub",
            subtitle = "Connect any Windows, Mac, or Linux desktop using standard browsers.",
            icon = Icons.Default.Laptop,
            gradientColors = listOf(Color(0xFF10B981), Color(0xFF059669))
        )
    }

    item {
        GuideStepCard(
            stepNumber = "1",
            title = "Ensure PC & Phone Are on Same Wi-Fi",
            description = "Connect your computer and phone to the same Wi-Fi router, or connect your PC's Wi-Fi directly to the phone's hotspot.",
            icon = Icons.Default.Wifi
        )
    }

    item {
        GuideStepCard(
            stepNumber = "2",
            title = "Open Browser & Navigate to Port 8080",
            description = "On your PC, open Chrome, Edge, Safari, or Firefox and navigate to the phone's IP address (shown in the Lobby):\n\nhttp://<phone-ip>:8080\n\ne.g., http://192.168.1.15:8080",
            icon = Icons.Default.Terminal
        )
    }

    item {
        GuideStepCard(
            stepNumber = "3",
            title = "Real-Time Lobby Detection",
            description = "The moment you open the page on your PC, the phone's Lobby automatically detects your computer (e.g. 'Windows PC' or 'MacBook') and displays an Active Web Session card! You can beam selected files straight to the PC.",
            icon = Icons.Default.CheckCircle
        )
    }

    item {
        GuideStepCard(
            stepNumber = "4",
            title = "Bidirectional Drag & Drop Uploads",
            description = "On the desktop browser, you can drag and drop any file or folder from your computer straight into the webpage. The phone will instantly receive and save it to Scoped Storage with real-time speed tracking.",
            icon = Icons.Default.Speed
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.swarmMeshContent() {
    item {
        GuideHeroCard(
            title = "Dynamic Swarm P2P Mode",
            subtitle = "BitTorrent-inspired chunked mesh transfer for multi-recipient sharing.",
            icon = Icons.Default.Hub,
            gradientColors = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))
        )
    }

    item {
        GuideStepCard(
            stepNumber = "1",
            title = "Automatic Swarm Upgrade",
            description = "When sending files to multiple peers simultaneously, BeamDrop automatically switches from 1-to-1 streaming to Swarm Mesh mode.",
            icon = Icons.Default.Speed
        )
    }

    item {
        GuideStepCard(
            stepNumber = "2",
            title = "1MB Cryptographic Chunking",
            description = "Files are segmented into 1MB chunks hashed with SHA-256. Peers download disparate chunks in parallel, eliminating the bottleneck of a single sender.",
            icon = Icons.Default.CheckCircle
        )
    }

    item {
        GuideStepCard(
            stepNumber = "3",
            title = "Peer-to-Peer Relay",
            description = "Peers immediately re-seed chunks they have finished downloading to other nearby peers, multiplying aggregate network bandwidth.",
            icon = Icons.Default.Hub
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.architectureContent() {
    item {
        GuideHeroCard(
            title = "Clean Architecture & Engine",
            subtitle = "Modular separation of UI, Domain, and Data/Network layers.",
            icon = Icons.Default.Architecture,
            gradientColors = listOf(Color(0xFFF59E0B), Color(0xFFD97706))
        )
    }

    item {
        TechStackLayerCard(
            layerName = "1. UI Presentation Layer",
            details = "• Jetpack Compose Material 3 design system.\n• Modular screens: LobbyScreen, SelectionScreen, GuideScreen.\n• Recomposition optimization: @Immutable state models prevent UI lag.\n• TransferModal decoupled from background screen redraws.",
            tagColor = Color(0xFF00E5FF)
        )
    }

    item {
        TechStackLayerCard(
            layerName = "2. Domain / Use-Case Layer",
            details = "• LobbyDiscoveryCoordinator: Combines NSD and PC Web heartbeat registries.\n• HybridTransferCoordinator: Manages topology state machine (Simple ↔ Swarm).\n• ChunkManager: Handles random access chunk verification and deduplication.",
            tagColor = Color(0xFF10B981)
        )
    }

    item {
        TechStackLayerCard(
            layerName = "3. Data & Networking Layer",
            details = "• EmbeddedFileServer: Standby HTTP 1.1 engine supporting Range requests and resumable streaming.\n• MediaRepository: MediaStore queries with background coroutine sorting.\n• AppRepository: Installed application metadata querying and APK extraction.\n• TransferForegroundService: Wakelock and persistent progress notification.",
            tagColor = Color(0xFF8B5CF6)
        )
    }

    item {
        TechStackLayerCard(
            layerName = "4. Scoped Storage & Auto-Resume",
            details = "• Android 11+ MediaStore NIO FileChannel random-access writing.\n• Incomplete files cached as .part until chunk validation passes.\n• Automatic byte-offset resumption if Wi-Fi disconnects mid-transfer.",
            tagColor = Color(0xFFEC4899)
        )
    }
}

@Composable
private fun GuideHeroCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradientColors: List<Color>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(gradientColors))
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color.White.copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideStepCard(
    stepNumber: String,
    title: String,
    description: String,
    icon: ImageVector
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFF00E5FF).copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stepNumber,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E5FF)
                    )
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TechStackLayerCard(
    layerName: String,
    details: String,
    tagColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, tagColor.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(tagColor, CircleShape)
                )
                Text(
                    text = layerName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = tagColor
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = details,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
