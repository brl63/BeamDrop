package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AppRepository
import com.example.data.repository.MediaRepository
import com.example.domain.usecase.LobbyDiscoveryCoordinator
import com.example.helper.Breadcrumb
import com.example.helper.FileExplorerHelper
import com.example.helper.FileInstallHelper
import com.example.model.DateFilterOption
import com.example.model.DiscoveredPeer
import com.example.model.FileExplorerItem
import com.example.model.FileSortOption
import com.example.model.InstalledAppInfo
import com.example.model.MediaCategory
import com.example.model.SharedItem
import com.example.model.TransferSession
import com.example.model.TransferStatus
import com.example.model.TransferTopology
import com.example.model.formatBytes
import com.example.network.EmbeddedFileServer
import com.example.network.HotspotManager
import com.example.network.NetworkUtils
import com.example.network.NsdDiscoveryManager
import com.example.network.P2pSenderClient
import com.example.network.swarm.HybridTransferCoordinator
import com.example.service.TransferForegroundService
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppScreen {
    MAIN,
    SELECTION,
    GUIDE
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    // Repositories & Domain Coordinators (Clean Architecture)
    private val mediaRepository = MediaRepository(context)
    private val appRepository = AppRepository(context)
    private val nsdManager = NsdDiscoveryManager(context, viewModelScope)
    val lobbyCoordinator = LobbyDiscoveryCoordinator(context, viewModelScope, nsdManager)

    private val senderClient = P2pSenderClient(context)
    private lateinit var embeddedServer: EmbeddedFileServer

    val hybridCoordinator = HybridTransferCoordinator(
        context = application,
        scope = viewModelScope,
        onSessionUpdate = { session ->
            updateTransferSession(session)
        }
    )

    // Navigation
    private val _currentScreen = MutableStateFlow(AppScreen.MAIN)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Network State
    private val _localIp = MutableStateFlow<String?>("Detecting...")
    val localIp: StateFlow<String?> = _localIp.asStateFlow()

    private val _isReceiverActive = MutableStateFlow(false)
    val isReceiverActive: StateFlow<Boolean> = _isReceiverActive.asStateFlow()
    val currentTopology: StateFlow<TransferTopology> = hybridCoordinator.currentTopology

    // Peers & Real-Time PC Web Sessions
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = lobbyCoordinator.discoveredPeers
    val activeWebClients: StateFlow<List<DiscoveredPeer>> = lobbyCoordinator.activeWebClients

    // Apps Tab
    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    // Files Tab (MediaStore)
    private val _selectedCategory = MutableStateFlow(MediaCategory.ALL)
    val selectedCategory: StateFlow<MediaCategory> = _selectedCategory.asStateFlow()

    private val _mediaFiles = MutableStateFlow<List<SharedItem.MediaFile>>(emptyList())
    val mediaFiles: StateFlow<List<SharedItem.MediaFile>> = _mediaFiles.asStateFlow()

    private val _isLoadingMedia = MutableStateFlow(false)
    val isLoadingMedia: StateFlow<Boolean> = _isLoadingMedia.asStateFlow()

    private val _hasStoragePermissions = MutableStateFlow(mediaRepository.hasStoragePermissions())
    val hasStoragePermissions: StateFlow<Boolean> = _hasStoragePermissions.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Sorting & Filters
    private val _fileSortOption = MutableStateFlow(FileSortOption.DATE_DESC)
    val fileSortOption: StateFlow<FileSortOption> = _fileSortOption.asStateFlow()

    private val _dateFilterOption = MutableStateFlow(DateFilterOption.ALL)
    val dateFilterOption: StateFlow<DateFilterOption> = _dateFilterOption.asStateFlow()

    private val _isGridView = MutableStateFlow(false)
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    // File Explorer
    private val rootDirectory = FileExplorerHelper.getRootDirectory()
    private val _currentDirectory = MutableStateFlow(rootDirectory)
    val currentDirectory: StateFlow<File> = _currentDirectory.asStateFlow()

    private val _fileExplorerItems = MutableStateFlow<List<FileExplorerItem>>(emptyList())
    val fileExplorerItems: StateFlow<List<FileExplorerItem>> = _fileExplorerItems.asStateFlow()

    private val _isLoadingExplorer = MutableStateFlow(false)
    val isLoadingExplorer: StateFlow<Boolean> = _isLoadingExplorer.asStateFlow()

    val breadcrumbs: StateFlow<List<Breadcrumb>> = _currentDirectory.map { dir ->
        FileExplorerHelper.getBreadcrumbs(dir, rootDirectory)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val canNavigateUp: StateFlow<Boolean> = _currentDirectory.map { dir ->
        dir.canonicalPath != rootDirectory.canonicalPath && dir.parentFile != null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Multi-Selection Queue
    private val _selectedItems = MutableStateFlow<Set<SharedItem>>(emptySet())
    val selectedItems: StateFlow<Set<SharedItem>> = _selectedItems.asStateFlow()

    val totalSelectedCount: StateFlow<Int> = _selectedItems.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalSelectedBytes: StateFlow<Long> = _selectedItems.map { set ->
        set.sumOf { it.sizeBytes }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val formattedTotalSelectedSize: StateFlow<String> = totalSelectedBytes.map { formatBytes(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0 B")

    // Active Transfer State
    private val _transferSession = MutableStateFlow(TransferSession())
    val transferSession: StateFlow<TransferSession> = _transferSession.asStateFlow()

    private val _showTransferModal = MutableStateFlow(false)
    val showTransferModal: StateFlow<Boolean> = _showTransferModal.asStateFlow()

    private val _showPeerPickerSheet = MutableStateFlow(false)
    val showPeerPickerSheet: StateFlow<Boolean> = _showPeerPickerSheet.asStateFlow()

    private val _showManualIpDialog = MutableStateFlow(false)
    val showManualIpDialog: StateFlow<Boolean> = _showManualIpDialog.asStateFlow()

    private val _showQrScanner = MutableStateFlow(false)
    val showQrScanner: StateFlow<Boolean> = _showQrScanner.asStateFlow()

    private val _showMyQrCode = MutableStateFlow(false)
    val showMyQrCode: StateFlow<Boolean> = _showMyQrCode.asStateFlow()

    private val _showWebShareDialog = MutableStateFlow(false)
    val showWebShareDialog: StateFlow<Boolean> = _showWebShareDialog.asStateFlow()

    private val _pairedPeerMessage = MutableStateFlow<String?>(null)
    val pairedPeerMessage: StateFlow<String?> = _pairedPeerMessage.asStateFlow()

    private val _hotspotSsid = MutableStateFlow<String?>(null)
    val hotspotSsid: StateFlow<String?> = _hotspotSsid.asStateFlow()

    private val _hotspotPassword = MutableStateFlow<String?>(null)
    val hotspotPassword: StateFlow<String?> = _hotspotPassword.asStateFlow()

    private var activeBatchJob: Job? = null

    init {
        TransferForegroundService.onCancelRequested = {
            cancelActiveTransfer()
        }

        embeddedServer = EmbeddedFileServer(
            context = context,
            scope = viewModelScope,
            onProgressUpdate = { session ->
                updateTransferSession(session)
            }
        ).apply {
            sharedFilesProvider = { getWebSharedFiles() }
            activeLobbyPeersProvider = { discoveredPeers.value }
            currentTopologyProvider = { hybridCoordinator.currentTopology.value.name }
            onClientLobbyHeartbeat = { clientIp, deviceName, clientType ->
                lobbyCoordinator.registerWebClientHeartbeat(clientIp, deviceName, clientType)
            }
        }

        refreshNetworkInfo()
        lobbyCoordinator.startDiscovery()

        // Auto-start embedded server in lightweight standby mode so any PC on LAN or Hotspot
        // connects seamlessly without needing manual toggles!
        embeddedServer.start(NetworkUtils.DEFAULT_PORT)

        loadInstalledApps()
        checkPermissionsAndLoadMedia()
        loadExplorerDirectory()

        viewModelScope.launch {
            discoveredPeers.collect { peers ->
                hybridCoordinator.onLobbyPeersChanged(peers)
            }
        }
    }

    fun updateTransferSession(session: TransferSession) {
        _transferSession.value = session
        if (session.status != TransferStatus.IDLE) {
            _showTransferModal.value = true
        }

        when (session.status) {
            TransferStatus.CONNECTING -> {
                TransferForegroundService.startService(
                    context = context,
                    fileName = session.fileName.ifEmpty { "File Transfer" },
                    isReceiver = session.isReceiver,
                    totalBytes = session.totalBytes
                )
            }
            TransferStatus.TRANSFERRING -> {
                TransferForegroundService.updateProgress(
                    context = context,
                    fileName = session.fileName.ifEmpty { "File Transfer" },
                    transferredBytes = session.transferredBytes,
                    totalBytes = session.totalBytes,
                    speedBps = session.speedBytesPerSec,
                    isReceiver = session.isReceiver
                )
            }
            TransferStatus.COMPLETED,
            TransferStatus.ERROR,
            TransferStatus.CANCELLED -> {
                TransferForegroundService.stopService(context)
            }
            TransferStatus.IDLE -> {}
        }
    }

    // Navigation Actions
    fun navigateToSelection() {
        _currentScreen.value = AppScreen.SELECTION
        checkPermissionsAndLoadMedia()
    }

    fun navigateToGuide() {
        _currentScreen.value = AppScreen.GUIDE
    }

    fun navigateBackToMain() {
        _currentScreen.value = AppScreen.MAIN
        _searchQuery.value = ""
    }

    fun refreshNetworkInfo() {
        val ip = NetworkUtils.getLocalIpAddress()
        _localIp.value = ip ?: "Offline / No Wi-Fi"
    }

    fun toggleReceiverMode() {
        val nextState = !_isReceiverActive.value
        _isReceiverActive.value = nextState

        if (nextState) {
            refreshNetworkInfo()
            // Immediately open QR code so the sender can scan and beam files
            _showMyQrCode.value = true

            val currentIp = _localIp.value
            val isOffline = currentIp == null || currentIp.contains("Offline")

            if (isOffline) {
                HotspotManager.startLocalOnlyHotspot(
                    context = context,
                    onReady = { ssid, pass, ip ->
                        _hotspotSsid.value = ssid
                        _hotspotPassword.value = pass
                        _localIp.value = ip
                        embeddedServer.start(NetworkUtils.DEFAULT_PORT)
                    },
                    onFailed = { _ ->
                        _localIp.value = "192.168.43.1"
                        embeddedServer.start(NetworkUtils.DEFAULT_PORT)
                    }
                )
            } else {
                embeddedServer.start(NetworkUtils.DEFAULT_PORT)
                lobbyCoordinator.startBroadcasting(NetworkUtils.DEFAULT_PORT)
            }
        } else {
            _showMyQrCode.value = false
            lobbyCoordinator.stopBroadcasting()
            HotspotManager.stopLocalHotspot()
            _hotspotSsid.value = null
            _hotspotPassword.value = null
            refreshNetworkInfo()
        }
    }

    // Media & Apps
    fun checkPermissionsAndLoadMedia() {
        val granted = mediaRepository.hasStoragePermissions()
        _hasStoragePermissions.value = granted
        if (granted) {
            loadMediaFiles()
        }
    }

    fun onPermissionsResult(allGranted: Boolean) {
        _hasStoragePermissions.value = allGranted
        if (allGranted) {
            loadMediaFiles()
            loadExplorerDirectory()
        }
    }

    fun setMediaCategory(category: MediaCategory) {
        _selectedCategory.value = category
        if (_hasStoragePermissions.value) {
            loadMediaFiles()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        loadExplorerDirectory()
    }

    fun setSortOption(option: FileSortOption) {
        _fileSortOption.value = option
        loadExplorerDirectory()
        sortMediaFiles()
    }

    fun setDateFilterOption(option: DateFilterOption) {
        _dateFilterOption.value = option
        loadExplorerDirectory()
        sortMediaFiles()
    }

    fun toggleViewLayout() {
        _isGridView.value = !_isGridView.value
    }

    fun navigateToDirectory(dir: File) {
        _currentDirectory.value = dir
        loadExplorerDirectory(dir)
    }

    fun navigateUpDirectory() {
        val parent = _currentDirectory.value.parentFile
        if (parent != null && _currentDirectory.value.canonicalPath != rootDirectory.canonicalPath) {
            navigateToDirectory(parent)
        }
    }

    fun loadExplorerDirectory(dir: File = _currentDirectory.value) {
        viewModelScope.launch {
            _isLoadingExplorer.value = true
            val items = FileExplorerHelper.listDirectory(
                dir = dir,
                sortOption = _fileSortOption.value,
                dateFilter = _dateFilterOption.value,
                searchQuery = _searchQuery.value
            )
            _fileExplorerItems.value = items
            _isLoadingExplorer.value = false
        }
    }

    fun toggleExplorerItemSelection(item: FileExplorerItem) {
        if (item.isDirectory) {
            navigateToDirectory(File(item.path))
        } else {
            toggleSelection(item.toSharedItem())
        }
    }

    fun isExplorerItemSelected(item: FileExplorerItem): Boolean {
        return _selectedItems.value.any { it.id == item.id }
    }

    fun loadMediaFiles() {
        if (_isLoadingMedia.value) return
        viewModelScope.launch {
            _isLoadingMedia.value = true
            _mediaFiles.value = mediaRepository.getMediaFiles(_selectedCategory.value, _fileSortOption.value)
            _isLoadingMedia.value = false
        }
    }

    private fun sortMediaFiles() {
        _mediaFiles.value = mediaRepository.sortMediaList(_mediaFiles.value, _fileSortOption.value)
    }

    fun loadInstalledApps() {
        if (_isLoadingApps.value) return
        viewModelScope.launch {
            _isLoadingApps.value = true
            _installedApps.value = appRepository.getInstalledApps()
            _isLoadingApps.value = false
        }
    }

    fun toggleSelection(item: SharedItem) {
        val current = _selectedItems.value.toMutableSet()
        val existing = current.find { it.id == item.id }
        if (existing != null) {
            current.remove(existing)
        } else {
            current.add(item)
        }
        _selectedItems.value = current
    }

    fun isItemSelected(item: SharedItem): Boolean {
        return _selectedItems.value.any { it.id == item.id }
    }

    fun clearSelection() {
        _selectedItems.value = emptySet()
    }

    fun selectAllInView(items: List<SharedItem>) {
        val current = _selectedItems.value.toMutableSet()
        val allContained = items.all { item -> current.any { it.id == item.id } }
        if (allContained) {
            items.forEach { item -> current.removeAll { it.id == item.id } }
        } else {
            items.forEach { item ->
                if (current.none { it.id == item.id }) {
                    current.add(item)
                }
            }
        }
        _selectedItems.value = current
    }

    private val _lastPairedPeer = MutableStateFlow<DiscoveredPeer?>(null)
    val lastPairedPeer: StateFlow<DiscoveredPeer?> = _lastPairedPeer.asStateFlow()

    fun onSendQueueClicked() {
        if (_selectedItems.value.isEmpty()) return

        val peers = discoveredPeers.value
        val paired = _lastPairedPeer.value
        if (paired != null) {
            startBatchTransfer(paired)
        } else if (peers.size == 1) {
            startBatchTransfer(peers.first())
        } else if (peers.isEmpty()) {
            _showQrScanner.value = true
        } else {
            _showPeerPickerSheet.value = true
        }
    }

    fun sendToDiscoveredPeer(peer: DiscoveredPeer) {
        _showPeerPickerSheet.value = false
        startBatchTransfer(peer)
    }

    fun addManualPeerAndSend(ip: String, portStr: String) {
        val port = portStr.trim().toIntOrNull() ?: NetworkUtils.DEFAULT_PORT
        val cleanIp = ip.trim()
        if (cleanIp.isNotEmpty()) {
            lobbyCoordinator.addManualPeer(cleanIp, port, "Device ($cleanIp)")
            _showManualIpDialog.value = false
            val peer = DiscoveredPeer(
                id = "manual_$cleanIp:$port",
                name = "Direct ($cleanIp)",
                ip = cleanIp,
                port = port
            )
            startBatchTransfer(peer)
        }
    }

    private fun startBatchTransfer(peer: DiscoveredPeer) {
        val itemsToSend = _selectedItems.value.toList()
        if (itemsToSend.isEmpty()) return

        _showPeerPickerSheet.value = false
        _showTransferModal.value = true

        val totalBytes = itemsToSend.sumOf { it.sizeBytes }
        var cumulativeTransferredBytes = 0L

        activeBatchJob?.cancel()
        activeBatchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                for ((index, item) in itemsToSend.withIndex()) {
                    val currentIndex = index + 1

                    when (item) {
                        is SharedItem.MediaFile -> {
                            senderClient.streamFile(
                                targetIp = peer.ip,
                                targetPort = peer.port,
                                fileUri = item.uri,
                                overrideFileName = item.displayName,
                                isApk = item.isApk,
                                remotePeerName = peer.name,
                                onProgressUpdate = { session ->
                                    val sessionTransferred = cumulativeTransferredBytes + session.transferredBytes
                                    updateTransferSession(
                                        session.copy(
                                            batchCurrentIndex = currentIndex,
                                            batchTotalCount = itemsToSend.size,
                                            batchTotalBytes = totalBytes,
                                            batchTransferredBytes = sessionTransferred
                                        )
                                    )
                                }
                            )
                        }
                        is SharedItem.AppFile -> {
                            val cleanApkName = appRepository.getCleanApkFileName(item.appInfo)
                            senderClient.streamFile(
                                targetIp = peer.ip,
                                targetPort = peer.port,
                                localFilePath = item.appInfo.apkPath,
                                overrideFileName = cleanApkName,
                                isApk = true,
                                remotePeerName = peer.name,
                                onProgressUpdate = { session ->
                                    val sessionTransferred = cumulativeTransferredBytes + session.transferredBytes
                                    updateTransferSession(
                                        session.copy(
                                            batchCurrentIndex = currentIndex,
                                            batchTotalCount = itemsToSend.size,
                                            batchTotalBytes = totalBytes,
                                            batchTransferredBytes = sessionTransferred
                                        )
                                    )
                                }
                            )
                        }
                    }

                    if (_transferSession.value.status == TransferStatus.ERROR ||
                        _transferSession.value.status == TransferStatus.CANCELLED
                    ) {
                        break
                    }

                    cumulativeTransferredBytes += item.sizeBytes
                }

                if (_transferSession.value.status != TransferStatus.ERROR &&
                    _transferSession.value.status != TransferStatus.CANCELLED
                ) {
                    updateTransferSession(
                        _transferSession.value.copy(
                            status = TransferStatus.COMPLETED,
                            fileName = "Batch Transfer (${itemsToSend.size} items)",
                            totalBytes = totalBytes,
                            transferredBytes = totalBytes,
                            batchCurrentIndex = itemsToSend.size,
                            batchTotalCount = itemsToSend.size,
                            batchTotalBytes = totalBytes,
                            batchTransferredBytes = totalBytes
                        )
                    )
                    _selectedItems.value = emptySet()
                }
            } catch (e: Exception) {
                updateTransferSession(
                    _transferSession.value.copy(
                        status = TransferStatus.ERROR,
                        errorMessage = "Batch transfer interrupted: ${e.message}"
                    )
                )
            }
        }
    }

    fun cancelActiveTransfer() {
        activeBatchJob?.cancel()
        activeBatchJob = null
        hybridCoordinator.stop()
        if (!_transferSession.value.isReceiver) {
            senderClient.cancel()
        }
        TransferForegroundService.stopService(context)
        updateTransferSession(
            _transferSession.value.copy(
                status = TransferStatus.CANCELLED,
                errorMessage = "Transfer cancelled"
            )
        )
    }

    fun startSwarmTransfer(peers: List<DiscoveredPeer>) {
        val itemsToSend = _selectedItems.value.toList()
        if (itemsToSend.isEmpty() || peers.isEmpty()) return
        _showPeerPickerSheet.value = false
        _showTransferModal.value = true

        val firstItem = itemsToSend.first()
        when (firstItem) {
            is SharedItem.MediaFile -> {
                hybridCoordinator.startSession(
                    fileName = firstItem.displayName,
                    fileSize = firstItem.sizeBytes,
                    fileUri = firstItem.uri,
                    localPath = firstItem.localPath,
                    isReceiverMode = false,
                    receiverPeers = peers
                )
            }
            is SharedItem.AppFile -> {
                val cleanApkName = appRepository.getCleanApkFileName(firstItem.appInfo)
                hybridCoordinator.startSession(
                    fileName = cleanApkName,
                    fileSize = firstItem.sizeBytes,
                    localPath = firstItem.appInfo.apkPath,
                    isReceiverMode = false,
                    receiverPeers = peers
                )
            }
        }
    }

    fun dismissTransferModal() {
        _showTransferModal.value = false
        TransferForegroundService.stopService(context)
        _transferSession.value = TransferSession()
    }

    fun openManualIpDialog() {
        _showManualIpDialog.value = true
    }

    fun closeManualIpDialog() {
        _showManualIpDialog.value = false
    }

    fun closePeerPicker() {
        _showPeerPickerSheet.value = false
    }

    fun installReceivedApk(apkPath: String) {
        FileInstallHelper.installApk(context, apkPath)
    }

    fun openReceivedFile(filePath: String) {
        FileInstallHelper.openFile(context, filePath)
    }

    fun openQrScanner() {
        _showQrScanner.value = true
    }

    fun closeQrScanner() {
        _showQrScanner.value = false
    }

    fun openMyQrCode() {
        _showMyQrCode.value = true
    }

    fun closeMyQrCode() {
        _showMyQrCode.value = false
    }

    fun clearPairedMessage() {
        _pairedPeerMessage.value = null
    }

    fun openWebShareModal() {
        refreshNetworkInfo()
        val currentIp = _localIp.value
        val isOffline = currentIp == null || currentIp.contains("Offline")

        if (!_isReceiverActive.value) {
            _isReceiverActive.value = true
            if (isOffline) {
                HotspotManager.startLocalOnlyHotspot(
                    context = context,
                    onReady = { ssid, pass, ip ->
                        _hotspotSsid.value = ssid
                        _hotspotPassword.value = pass
                        _localIp.value = ip
                        embeddedServer.start(NetworkUtils.DEFAULT_PORT)
                    },
                    onFailed = { _ ->
                        _localIp.value = "192.168.43.1"
                        embeddedServer.start(NetworkUtils.DEFAULT_PORT)
                    }
                )
            } else {
                embeddedServer.start(NetworkUtils.DEFAULT_PORT)
                lobbyCoordinator.startBroadcasting(NetworkUtils.DEFAULT_PORT)
            }
        }
        _showWebShareDialog.value = true
    }

    fun closeWebShareModal() {
        _showWebShareDialog.value = false
    }

    fun shareAppApkViaBluetooth() {
        FileInstallHelper.shareAppApk(context)
    }

    fun getWebSharedFiles(): List<com.example.model.WebSharedFile> {
        val selected = _selectedItems.value
        if (selected.isEmpty()) {
            val appInfo = context.applicationInfo
            val sourceApk = java.io.File(appInfo.sourceDir)
            return if (sourceApk.exists()) {
                listOf(
                    com.example.model.WebSharedFile(
                        id = "beamdrop_app_apk",
                        name = "BeamDrop.apk",
                        sizeBytes = sourceApk.length(),
                        isApk = true,
                        localFilePath = appInfo.sourceDir,
                        mimeType = "application/vnd.android.package-archive"
                    )
                )
            } else emptyList()
        }

        return selected.map { item ->
            when (item) {
                is SharedItem.MediaFile -> com.example.model.WebSharedFile(
                    id = item.id.toString(),
                    name = item.displayName,
                    sizeBytes = item.sizeBytes,
                    isApk = item.isApk,
                    uri = item.uri,
                    mimeType = item.mimeType
                )
                is SharedItem.AppFile -> com.example.model.WebSharedFile(
                    id = "app_${item.appInfo.packageName}",
                    name = appRepository.getCleanApkFileName(item.appInfo),
                    sizeBytes = item.appInfo.sizeBytes,
                    isApk = true,
                    localFilePath = item.appInfo.apkPath,
                    mimeType = "application/vnd.android.package-archive"
                )
            }
        }
    }

    fun onPeerScanned(peer: DiscoveredPeer) {
        lobbyCoordinator.addManualPeer(peer.ip, peer.port, peer.name)
        _lastPairedPeer.value = peer
        _pairedPeerMessage.value = "Paired with ${peer.name} (${peer.ip}:${peer.port})"
        _showQrScanner.value = false

        if (_selectedItems.value.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                if (!peer.wifiSsid.isNullOrEmpty()) {
                    _transferSession.value = TransferSession(
                        status = TransferStatus.CONNECTING,
                        fileName = "Connecting to ${peer.name}'s Hotspot...",
                        isReceiver = false,
                        remotePeerName = peer.name
                    )
                    _showTransferModal.value = true
                    HotspotManager.connectToHotspot(
                        context,
                        peer.wifiSsid,
                        peer.wifiPassword ?: ""
                    )
                }
                startBatchTransfer(peer)
            }
        } else {
            navigateToSelection()
        }
    }

    override fun onCleared() {
        super.onCleared()
        TransferForegroundService.stopService(context)
        lobbyCoordinator.stopDiscovery()
        lobbyCoordinator.stopBroadcasting()
        embeddedServer.stop()
        HotspotManager.stopLocalHotspot()
        activeBatchJob?.cancel()
    }
}
