package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.network.NetworkUtils
import com.example.ui.components.ManualIpDialog
import com.example.ui.components.PeerPickerSheet
import com.example.ui.components.QrCodeDisplayDialog
import com.example.ui.components.QrScannerDialog
import com.example.ui.components.TransferModal
import com.example.ui.components.WebShareDialog
import com.example.ui.guide.GuideScreen
import com.example.ui.lobby.LobbyScreen
import com.example.ui.selection.SelectionScreen
import com.example.viewmodel.AppScreen
import com.example.viewmodel.MainViewModel

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val currentScreen by viewModel.currentScreen.collectAsState()

    val localIp by viewModel.localIp.collectAsState()
    val isReceiverActive by viewModel.isReceiverActive.collectAsState()
    val discoveredPeers by viewModel.discoveredPeers.collectAsState()
    val activeWebClients by viewModel.activeWebClients.collectAsState()
    val hotspotSsid by viewModel.hotspotSsid.collectAsState()
    val hotspotPassword by viewModel.hotspotPassword.collectAsState()

    val showTransferModal by viewModel.showTransferModal.collectAsState()
    val showPeerPickerSheet by viewModel.showPeerPickerSheet.collectAsState()
    val showManualIpDialog by viewModel.showManualIpDialog.collectAsState()
    val showQrScanner by viewModel.showQrScanner.collectAsState()
    val showMyQrCode by viewModel.showMyQrCode.collectAsState()
    val showWebShareDialog by viewModel.showWebShareDialog.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        // Screen Navigation Flow
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                when {
                    targetState == AppScreen.SELECTION -> {
                        (slideInHorizontally { it } + fadeIn()).togetherWith(
                            slideOutHorizontally { -it } + fadeOut()
                        )
                    }
                    targetState == AppScreen.GUIDE -> {
                        (slideInHorizontally { it } + fadeIn()).togetherWith(
                            slideOutHorizontally { -it } + fadeOut()
                        )
                    }
                    else -> {
                        (slideInHorizontally { -it } + fadeIn()).togetherWith(
                            slideOutHorizontally { it } + fadeOut()
                        )
                    }
                }
            },
            label = "ScreenTransition"
        ) { screen ->
            when (screen) {
                AppScreen.MAIN -> {
                    LobbyScreen(
                        localIp = localIp,
                        hotspotSsid = hotspotSsid,
                        isReceiverActive = isReceiverActive,
                        discoveredPeers = discoveredPeers,
                        activeWebClients = activeWebClients,
                        onSendClick = { viewModel.navigateToSelection() },
                        onReceiveToggle = { viewModel.toggleReceiverMode() },
                        onWebShareClick = { viewModel.openWebShareModal() },
                        onManualIpClick = { viewModel.openManualIpDialog() },
                        onRefreshNetwork = { viewModel.refreshNetworkInfo() },
                        onOpenGuide = { viewModel.navigateToGuide() },
                        onOpenScanner = { viewModel.openQrScanner() },
                        onShowMyQr = { viewModel.openMyQrCode() },
                        onShareBluetooth = { viewModel.shareAppApkViaBluetooth() },
                        onSendToPeer = { peer ->
                            viewModel.sendToDiscoveredPeer(peer)
                        },
                        onStartSwarm = { peers ->
                            viewModel.startSwarmTransfer(peers)
                        }
                    )
                }
                AppScreen.SELECTION -> {
                    SelectionScreen(viewModel = viewModel)
                }
                AppScreen.GUIDE -> {
                    GuideScreen(
                        onNavigateBack = { viewModel.navigateBackToMain() }
                    )
                }
            }
        }

        // Isolated Transfer Modal Overlay (Decoupled to eliminate background frame drops)
        TransferModalOverlay(
            viewModel = viewModel,
            show = showTransferModal
        )

        // Peer Picker Sheet
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
                    viewModel.closePeerPicker()
                    viewModel.startSwarmTransfer(peers)
                },
                onDismiss = { viewModel.closePeerPicker() }
            )
        }

        // QR Scanner Camera Dialog
        if (showQrScanner) {
            QrScannerDialog(
                onPeerScanned = { peer -> viewModel.onPeerScanned(peer) },
                onDismiss = { viewModel.closeQrScanner() }
            )
        }

        // Pairing / Receiver QR Code Dialog
        if (showMyQrCode) {
            val currentIp = localIp?.takeIf { !it.contains("Offline") && !it.contains("Detecting") } ?: "192.168.43.1"
            QrCodeDisplayDialog(
                ip = currentIp,
                port = NetworkUtils.DEFAULT_PORT,
                deviceName = NetworkUtils.getDeviceDisplayName(),
                wifiSsid = hotspotSsid,
                wifiPassword = hotspotPassword,
                title = if (isReceiverActive) "Receiver Standby • Scan QR" else "Pairing QR Code",
                subtitle = if (isReceiverActive)
                    "Scan with sender phone or open URL on PC browser to beam files here"
                    else "Scan from another phone or PC camera to pair instantly",
                onDismiss = { viewModel.closeMyQrCode() }
            )
        }

        // Manual IP Dialog
        if (showManualIpDialog) {
            ManualIpDialog(
                onConfirm = { ip, port -> viewModel.addManualPeerAndSend(ip, port) },
                onDismiss = { viewModel.closeManualIpDialog() }
            )
        }

        // Web Share Dialog
        if (showWebShareDialog) {
            val currentIp = localIp?.takeIf { !it.contains("Offline") && !it.contains("Detecting") } ?: "192.168.43.1"
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
}

/**
 * Isolated Composable wrapper for TransferModal.
 * Only recomposes when transferSession ticks change, preventing the entire screen from redraw lag.
 */
@Composable
private fun TransferModalOverlay(
    viewModel: MainViewModel,
    show: Boolean
) {
    if (show) {
        val transferSession by viewModel.transferSession.collectAsState()
        TransferModal(
            session = transferSession,
            onCancel = { viewModel.cancelActiveTransfer() },
            onDismiss = { viewModel.dismissTransferModal() },
            onInstallApk = { apkPath -> viewModel.installReceivedApk(apkPath) },
            onOpenFile = { path -> viewModel.openReceivedFile(path) }
        )
    }
}
