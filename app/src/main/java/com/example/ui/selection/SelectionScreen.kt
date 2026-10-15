package com.example.ui.selection

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SharedItem
import com.example.network.NetworkUtils
import com.example.ui.components.ManualIpDialog
import com.example.ui.components.PeerPickerSheet
import com.example.ui.components.TransferModal
import com.example.ui.components.WebShareDialog
import com.example.ui.theme.CyanPrimary
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var isSearchExpanded by remember { mutableStateOf(false) }

    val canNavigateUp by viewModel.canNavigateUp.collectAsState()

    BackHandler {
        if (selectedTabIndex == 0 && canNavigateUp) {
            viewModel.navigateUpDirectory()
        } else {
            viewModel.navigateBackToMain()
        }
    }

    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedItems by viewModel.selectedItems.collectAsState()
    val totalSelectedCount by viewModel.totalSelectedCount.collectAsState()
    val formattedTotalSize by viewModel.formattedTotalSelectedSize.collectAsState()

    // Explorer States
    val currentDirectory by viewModel.currentDirectory.collectAsState()
    val breadcrumbs by viewModel.breadcrumbs.collectAsState()
    val fileExplorerItems by viewModel.fileExplorerItems.collectAsState()
    val isLoadingExplorer by viewModel.isLoadingExplorer.collectAsState()

    // Sorting & Layout States
    val fileSortOption by viewModel.fileSortOption.collectAsState()
    val dateFilterOption by viewModel.dateFilterOption.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()

    // Media States
    val mediaFiles by viewModel.mediaFiles.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val isLoadingMedia by viewModel.isLoadingMedia.collectAsState()
    val hasStoragePermissions by viewModel.hasStoragePermissions.collectAsState()

    val installedApps by viewModel.installedApps.collectAsState()
    val isLoadingApps by viewModel.isLoadingApps.collectAsState()

    val discoveredPeers by viewModel.discoveredPeers.collectAsState()
    val showPeerPickerSheet by viewModel.showPeerPickerSheet.collectAsState()
    val showManualIpDialog by viewModel.showManualIpDialog.collectAsState()
    val showTransferModal by viewModel.showTransferModal.collectAsState()
    val transferSession by viewModel.transferSession.collectAsState()

    val selectedFilesCount = remember(selectedItems) {
        selectedItems.count { it is SharedItem.MediaFile }
    }
    val selectedAppsCount = remember(selectedItems) {
        selectedItems.count { it is SharedItem.AppFile }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Select Items to Send",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        )
                        if (totalSelectedCount > 0) {
                            Text(
                                text = "$totalSelectedCount selected • $formattedTotalSize",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanPrimary
                            )
                        } else {
                            Text(
                                text = "Choose files or installed apps",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBackToMain() },
                        modifier = Modifier.testTag("selection_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.openWebShareModal() },
                        modifier = Modifier.testTag("selection_web_share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = "Send to PC / Web Browser",
                            tint = CyanPrimary
                        )
                    }

                    IconButton(
                        onClick = {
                            isSearchExpanded = !isSearchExpanded
                            if (!isSearchExpanded) viewModel.setSearchQuery("")
                        },
                        modifier = Modifier.testTag("toggle_search_button")
                    ) {
                        Icon(
                            imageVector = if (isSearchExpanded) Icons.Default.Clear else Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (totalSelectedCount > 0) {
                        TextButton(
                            onClick = { viewModel.clearSelection() },
                            modifier = Modifier.testTag("clear_selection_button")
                        ) {
                            Text("Clear", color = CyanPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
            ) {
                // Top Tab Row (Explorer, Media Categories, Apps)
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = CyanPrimary,
                            height = 3.dp
                        )
                    },
                    modifier = Modifier.testTag("selection_tab_row")
                ) {
                    // Tab 0: Explorer (ZArchiver Directory Tree)
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Explorer",
                                    fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        },
                        modifier = Modifier.testTag("explorer_tab")
                    )

                    // Tab 1: Media (Images, Videos, Audio, Documents)
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.PermMedia, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Media",
                                    fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Medium
                                )
                                if (selectedFilesCount > 0) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "$selectedFilesCount",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier.testTag("media_tab")
                    )

                    // Tab 2: Apps
                    Tab(
                        selected = selectedTabIndex == 2,
                        onClick = { selectedTabIndex = 2 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Android, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Apps",
                                    fontWeight = if (selectedTabIndex == 2) FontWeight.Bold else FontWeight.Medium
                                )
                                if (selectedAppsCount > 0) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "$selectedAppsCount",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier.testTag("apps_tab")
                    )
                }

                // Search Bar (if expanded)
                AnimatedVisibility(visible = isSearchExpanded) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = {
                            Text(
                                when (selectedTabIndex) {
                                    0 -> "Search folder..."
                                    1 -> "Search photos, videos, files..."
                                    else -> "Search installed apps..."
                                }
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("selection_search_field")
                    )
                }

                // Tab Content
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTabIndex) {
                        0 -> StorageExplorerTab(
                            currentDirectory = currentDirectory,
                            breadcrumbs = breadcrumbs,
                            canNavigateUp = canNavigateUp,
                            items = fileExplorerItems,
                            isLoading = isLoadingExplorer,
                            hasPermissions = hasStoragePermissions,
                            selectedItems = selectedItems,
                            searchQuery = searchQuery,
                            sortOption = fileSortOption,
                            dateFilterOption = dateFilterOption,
                            isGridView = isGridView,
                            onNavigateToDir = { viewModel.navigateToDirectory(it) },
                            onNavigateUp = { viewModel.navigateUpDirectory() },
                            onToggleItemSelection = { viewModel.toggleExplorerItemSelection(it) },
                            onSetSortOption = { viewModel.setSortOption(it) },
                            onSetDateFilterOption = { viewModel.setDateFilterOption(it) },
                            onToggleViewLayout = { viewModel.toggleViewLayout() },
                            onPermissionsResult = { viewModel.onPermissionsResult(it) }
                        )
                        1 -> FilesTab(
                            mediaFiles = mediaFiles,
                            selectedCategory = selectedCategory,
                            isLoading = isLoadingMedia,
                            hasPermissions = hasStoragePermissions,
                            selectedItems = selectedItems,
                            searchQuery = searchQuery,
                            sortOption = fileSortOption,
                            dateFilterOption = dateFilterOption,
                            isGridView = isGridView,
                            onCategorySelected = { viewModel.setMediaCategory(it) },
                            onToggleSelection = { viewModel.toggleSelection(it) },
                            onSetSortOption = { viewModel.setSortOption(it) },
                            onSetDateFilterOption = { viewModel.setDateFilterOption(it) },
                            onToggleViewLayout = { viewModel.toggleViewLayout() },
                            onPermissionsResult = { viewModel.onPermissionsResult(it) }
                        )
                        2 -> AppsTab(
                            installedApps = installedApps,
                            isLoading = isLoadingApps,
                            selectedItems = selectedItems,
                            searchQuery = searchQuery,
                            onToggleSelection = { viewModel.toggleSelection(it) }
                        )
                    }
                }
            }

            // Bottom Sticky Dock / FAB: "Send X items • XX MB"
            AnimatedVisibility(
                visible = totalSelectedCount > 0,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 12.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 500.dp)
                        .testTag("send_queue_dock")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Selected $totalSelectedCount items",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = formattedTotalSize,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = CyanPrimary
                            )
                        }

                        Button(
                            onClick = { viewModel.onSendQueueClicked() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.testTag("send_queue_fab_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Send ($totalSelectedCount)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }

    val showQrScanner by viewModel.showQrScanner.collectAsState()
    val showWebShareDialog by viewModel.showWebShareDialog.collectAsState()
    val localIp by viewModel.localIp.collectAsState()
    val hotspotSsid by viewModel.hotspotSsid.collectAsState()
    val hotspotPassword by viewModel.hotspotPassword.collectAsState()

    if (showPeerPickerSheet) {
        PeerPickerSheet(
            peers = discoveredPeers,
            onPeerSelected = { peer -> viewModel.sendToDiscoveredPeer(peer) },
            onManualIpClick = {
                viewModel.closePeerPicker()
                viewModel.openManualIpDialog()
            },
            onScanQrClick = {
                viewModel.closePeerPicker()
                viewModel.openQrScanner()
            },
            onWebShareClick = {
                viewModel.closePeerPicker()
                viewModel.openWebShareModal()
            },
            onStartSwarmAllClick = { peers ->
                viewModel.startSwarmTransfer(peers)
            },
            onDismiss = { viewModel.closePeerPicker() }
        )
    }

    if (showQrScanner) {
        com.example.ui.components.QrScannerDialog(
            onPeerScanned = { peer -> viewModel.onPeerScanned(peer) },
            onDismiss = { viewModel.closeQrScanner() }
        )
    }

    if (showManualIpDialog) {
        ManualIpDialog(
            onConfirm = { ip, port -> viewModel.addManualPeerAndSend(ip, port) },
            onDismiss = { viewModel.closeManualIpDialog() }
        )
    }

    if (showTransferModal) {
        TransferModal(
            session = transferSession,
            onCancel = { viewModel.cancelActiveTransfer() },
            onDismiss = { viewModel.dismissTransferModal() },
            onInstallApk = { apkPath -> viewModel.installReceivedApk(apkPath) },
            onOpenFile = { path -> viewModel.openReceivedFile(path) }
        )
    }

    if (showWebShareDialog) {
        val currentIp = localIp?.takeIf { !it.contains("Offline") } ?: "192.168.43.1"
        WebShareDialog(
            localIp = currentIp,
            port = NetworkUtils.DEFAULT_PORT,
            hotspotSsid = hotspotSsid,
            hotspotPassword = hotspotPassword,
            sharedFiles = viewModel.getWebSharedFiles(),
            onDismiss = { viewModel.closeWebShareModal() }
        )
    }
}
