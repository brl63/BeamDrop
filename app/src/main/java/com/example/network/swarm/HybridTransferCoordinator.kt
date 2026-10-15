package com.example.network.swarm

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.model.DiscoveredPeer
import com.example.model.SwarmPeerInfo
import com.example.model.TransferSession
import com.example.model.TransferStatus
import com.example.model.TransferTopology
import com.example.network.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

enum class TopologyState {
    IDLE,
    SIMPLE_STREAMING,         // 1-to-1 lightweight direct streaming (Zero swarm overhead)
    TRANSITION_PAUSING,       // SWARM_UPGRADE sent; waiting for PAUSE_SYNC ack with 3-5s timeout
    TRANSITION_METADATA_SYNC, // Distributing dynamic SHA-256 chunk hashes and peer endpoints
    SWARM_MODE,               // Swarm Mesh actively running (Peer B seeds to Peer C)
    COMPLETED,
    ERROR
}

/**
 * State Machine and Orchestrator that dynamically monitors lobby size
 * and manages seamless transition between Simple 1-to-1 Mode and Swarm Mode.
 *
 * Resilience & Production features:
 * - Transition Timeout & Graceful Fallback: Aborts and reverts to SIMPLE_STREAMING if PAUSE_SYNC not received within 4s.
 * - Dynamic Chunk Sizing: 256KB (<50MB) vs 1MB (>=50MB) to optimize piece distribution and metadata.
 * - Android 11+ Scoped Storage: Uses MediaStore ParcelFileDescriptor and sparse NIO FileChannel.
 */
class HybridTransferCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onSessionUpdate: (TransferSession) -> Unit
) {
    private val tag = "HybridCoordinator"

    private val _currentState = MutableStateFlow(TopologyState.IDLE)
    val currentState: StateFlow<TopologyState> = _currentState.asStateFlow()

    private val _currentTopology = MutableStateFlow(TransferTopology.SIMPLE)
    val currentTopology: StateFlow<TransferTopology> = _currentTopology.asStateFlow()

    private var swarmNode: SwarmNode? = null
    private var chunkManager: ChunkManager? = null

    // Lobby tracking
    private val connectedPeers = mutableListOf<DiscoveredPeer>()
    private var activeSessionId = UUID.randomUUID().toString()

    // Active transfer metadata
    private var currentFileName = ""
    private var currentFileSize = 0L
    private var currentFileUri: Uri? = null
    private var currentLocalPath: String? = null
    private var isReceiver = false

    // Progress accumulator
    private var transferredBytes = 0L
    private var currentSpeedBps = 0L

    // Transition Timeout (3 to 5 seconds per requirement)
    private val transitionTimeoutMs = 4000L

    /**
     * Called whenever a peer joins or leaves the lobby.
     * Triggers dynamic topology switch if lobby size exceeds threshold.
     */
    fun onLobbyPeersChanged(peers: List<DiscoveredPeer>) {
        connectedPeers.clear()
        connectedPeers.addAll(peers)
        val receiverCount = connectedPeers.size
        Log.d(tag, "Lobby count updated: $receiverCount receivers connected")

        when (_currentState.value) {
            TopologyState.IDLE -> {
                if (receiverCount > 1) {
                    _currentTopology.value = TransferTopology.SWARM
                } else {
                    _currentTopology.value = TransferTopology.SIMPLE
                }
            }
            TopologyState.SIMPLE_STREAMING -> {
                // 3rd device joined mid-transfer! (1 Sender + 2 Receivers = 3 devices)
                if (receiverCount >= 2) {
                    Log.i(tag, "Triggering mid-transfer dynamic escalation: Simple -> Swarm Mesh!")
                    scope.launch(Dispatchers.IO) {
                        escalateToSwarmMode()
                    }
                }
            }
            TopologyState.SWARM_MODE -> {
                // Inform swarm node of any newly joined peers
                peers.forEach { peer ->
                    swarmNode?.connectToPeer(peer.ip, SwarmProtocol.DEFAULT_SWARM_PORT, peer.name)
                }
            }
            else -> {}
        }
    }

    /**
     * Initializes a transfer session in appropriate mode.
     */
    fun startSession(
        fileName: String,
        fileSize: Long,
        fileUri: Uri? = null,
        localPath: String? = null,
        isReceiverMode: Boolean = false,
        receiverPeers: List<DiscoveredPeer> = emptyList()
    ) {
        currentFileName = fileName
        currentFileSize = fileSize
        currentFileUri = fileUri
        currentLocalPath = localPath
        isReceiver = isReceiverMode
        connectedPeers.clear()
        connectedPeers.addAll(receiverPeers)
        activeSessionId = UUID.randomUUID().toString()

        if (connectedPeers.size >= 2) {
            // Start directly in Swarm Mode
            _currentTopology.value = TransferTopology.SWARM
            _currentState.value = TopologyState.SWARM_MODE
            launchSwarmMode(initialOffset = 0L)
        } else {
            // Start lightweight 1-to-1 Simple Mode
            _currentTopology.value = TransferTopology.SIMPLE
            _currentState.value = TopologyState.SIMPLE_STREAMING
            Log.d(tag, "Starting in Simple 1-to-1 Mode (Zero overhead)")
        }
    }

    /**
     * Upgrades an active 1-to-1 transfer to Swarm Mesh with Coroutine Timeout and Fallback.
     */
    private suspend fun escalateToSwarmMode() = withContext(Dispatchers.IO) {
        _currentState.value = TopologyState.TRANSITION_PAUSING
        _currentTopology.value = TransferTopology.SWARM

        // Notify UI of transition in progress
        updateSession(
            isTransitioning = true,
            status = TransferStatus.TRANSFERRING
        )

        // Calculate dynamic chunk size
        val dynamicChunkSize = SwarmProtocol.calculateChunkSize(currentFileSize)

        // 1. Calculate nearest chunk boundary from current progress
        val alignedOffset = (transferredBytes / dynamicChunkSize) * dynamicChunkSize
        Log.i(tag, "Aligned stream boundary to offset $alignedOffset bytes (chunk ${alignedOffset / dynamicChunkSize}, chunk size: ${dynamicChunkSize / 1024} KB)")

        // 2. Prepare SWARM_UPGRADE frame
        val streamProvider: () -> InputStream = {
            if (currentFileUri != null) {
                context.contentResolver.openInputStream(currentFileUri!!)
                    ?: throw IllegalStateException("Cannot open uri: $currentFileUri")
            } else if (currentLocalPath != null) {
                File(currentLocalPath!!).inputStream()
            } else {
                resolveTargetFile().inputStream()
            }
        }

        val hashes = ChunkManager.computeChunkHashes(
            inputStreamProvider = streamProvider,
            fileSize = currentFileSize,
            chunkSize = dynamicChunkSize
        )

        val upgradeFrame = SwarmProtocol.SwarmFrame.SwarmUpgrade(
            sessionId = activeSessionId,
            fileName = currentFileName,
            fileSize = currentFileSize,
            chunkSize = dynamicChunkSize,
            totalChunks = hashes.size,
            chunkHashes = hashes,
            currentStreamOffset = alignedOffset
        )

        // 3. Send SWARM_UPGRADE and wait for PAUSE_SYNC acknowledgment within 3-5 seconds
        val firstReceiver = connectedPeers.firstOrNull()
        val pauseSyncAck: SwarmProtocol.SwarmFrame.PauseSync? = if (firstReceiver != null) {
            withTimeoutOrNull(transitionTimeoutMs) {
                sendUpgradeAndWaitForAck(firstReceiver.ip, upgradeFrame)
            }
        } else {
            null
        }

        // 4. TIMEOUT & FALLBACK CHECK:
        if (pauseSyncAck == null && firstReceiver != null) {
            Log.w(tag, "TRANSITION TIMEOUT: Did not receive PAUSE_SYNC from Receiver within ${transitionTimeoutMs}ms! Aborting escalation.")
            // Gracefully fallback to SIMPLE_STREAMING to ensure file transfer never hangs indefinitely:
            _currentState.value = TopologyState.SIMPLE_STREAMING
            _currentTopology.value = TransferTopology.SIMPLE
            updateSession(
                isTransitioning = false,
                status = TransferStatus.TRANSFERRING
            )
            Log.i(tag, "Gracefully reverted State Machine to SIMPLE_STREAMING. Transfer continues without disruption.")
            return@withContext
        }

        // 5. PAUSE_SYNC Confirmed -> Advance to METADATA_SYNC and launch SwarmNode
        val effectiveOffset = pauseSyncAck?.targetOffset ?: alignedOffset
        Log.i(tag, "PAUSE_SYNC confirmed at offset $effectiveOffset! Escalating to Swarm Mesh.")
        _currentState.value = TopologyState.TRANSITION_METADATA_SYNC

        launchSwarmMode(initialOffset = effectiveOffset, precomputedHashes = hashes, dynamicChunkSize = dynamicChunkSize)
    }

    /**
     * Sends SWARM_UPGRADE over TCP control socket and awaits PAUSE_SYNC frame.
     */
    private suspend fun sendUpgradeAndWaitForAck(
        receiverIp: String,
        upgradeFrame: SwarmProtocol.SwarmFrame.SwarmUpgrade
    ): SwarmProtocol.SwarmFrame.PauseSync? = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(receiverIp, SwarmProtocol.DEFAULT_SWARM_PORT), 3000)

            val dos = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), 16 * 1024))
            val dis = DataInputStream(BufferedInputStream(socket.getInputStream(), 16 * 1024))

            // 1. Write SWARM_UPGRADE frame
            SwarmProtocol.writeFrame(dos, upgradeFrame)

            // 2. Read response
            val response = SwarmProtocol.readFrame(dis)
            if (response is SwarmProtocol.SwarmFrame.PauseSync) {
                response
            } else {
                Log.w(tag, "Expected PauseSync but received: ${response.javaClass.simpleName}")
                null
            }
        } catch (e: Exception) {
            Log.w(tag, "Upgrade handshake failed with $receiverIp: ${e.message}")
            null
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private fun launchSwarmMode(
        initialOffset: Long,
        precomputedHashes: List<String>? = null,
        dynamicChunkSize: Long = SwarmProtocol.calculateChunkSize(currentFileSize)
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                val streamProvider: () -> InputStream = {
                    if (currentFileUri != null) {
                        context.contentResolver.openInputStream(currentFileUri!!)
                            ?: throw IllegalStateException("Cannot open uri: $currentFileUri")
                    } else if (currentLocalPath != null) {
                        File(currentLocalPath!!).inputStream()
                    } else {
                        resolveTargetFile().inputStream()
                    }
                }

                val hashes = precomputedHashes ?: ChunkManager.computeChunkHashes(
                    inputStreamProvider = streamProvider,
                    fileSize = currentFileSize,
                    chunkSize = dynamicChunkSize
                )

                // Android 11+ Scoped Storage initialization:
                if (isReceiver) {
                    val (destUri, destPfd) = ChunkManager.createDestinationPfd(
                        context = context,
                        fileName = currentFileName.ifBlank { "received_file" },
                        fileSize = currentFileSize
                    )
                    currentFileUri = destUri

                    chunkManager = ChunkManager(
                        context = context,
                        targetUri = destUri,
                        fileSize = currentFileSize,
                        chunkSize = dynamicChunkSize,
                        chunkHashes = hashes,
                        providedPfd = destPfd
                    ).apply {
                        if (initialOffset > 0) {
                            val chunksPossessed = (initialOffset / dynamicChunkSize).toInt()
                            markChunksPossessed(0, chunksPossessed)
                        }
                    }
                } else {
                    // Sender mode (seeder):
                    val sourceFile = if (currentLocalPath != null) File(currentLocalPath!!) else null
                    chunkManager = ChunkManager(
                        context = context,
                        targetUri = currentFileUri,
                        sourceFile = sourceFile,
                        fileSize = currentFileSize,
                        chunkSize = dynamicChunkSize,
                        chunkHashes = hashes
                    ).apply {
                        // Sender has all chunks
                        markChunksPossessed(0, totalChunks)
                    }
                }

                val myId = UUID.randomUUID().toString().take(8)
                val myName = NetworkUtils.getDeviceDisplayName()

                swarmNode = SwarmNode(
                    myPeerId = myId,
                    myDeviceName = myName,
                    sessionId = activeSessionId,
                    listeningPort = SwarmProtocol.DEFAULT_SWARM_PORT,
                    chunkManager = chunkManager!!,
                    scope = scope,
                    onProgress = { transferred, total, speed, peers ->
                        transferredBytes = transferred
                        currentSpeedBps = speed
                        updateSession(
                            isTransitioning = false,
                            status = TransferStatus.TRANSFERRING,
                            swarmPeers = peers,
                            completedChunks = chunkManager?.getCompletedCount() ?: 0,
                            totalChunks = chunkManager?.totalChunks ?: 0
                        )
                    },
                    onComplete = {
                        _currentState.value = TopologyState.COMPLETED
                        updateSession(
                            isTransitioning = false,
                            status = TransferStatus.COMPLETED
                        )
                    }
                ).apply {
                    start()
                }

                // Connect to all peers in the lobby
                connectedPeers.forEach { peer ->
                    swarmNode?.connectToPeer(peer.ip, SwarmProtocol.DEFAULT_SWARM_PORT, peer.name)
                }

                _currentState.value = TopologyState.SWARM_MODE
                Log.i(tag, "Swarm Mesh is now fully active with dynamic chunk size ${dynamicChunkSize / 1024} KB!")
            } catch (e: Exception) {
                Log.e(tag, "Failed to launch Swarm Mode: ${e.message}", e)
                _currentState.value = TopologyState.ERROR
                updateSession(
                    isTransitioning = false,
                    status = TransferStatus.ERROR,
                    error = e.message
                )
            }
        }
    }

    private fun resolveTargetFile(): File {
        if (!isReceiver && currentLocalPath != null) {
            return File(currentLocalPath!!)
        }
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        val beamDropDir = File(downloadsDir, "BeamDrop")
        if (!beamDropDir.exists()) beamDropDir.mkdirs()
        return File(beamDropDir, currentFileName.ifBlank { "received_file" })
    }

    fun updateSimpleProgress(transferred: Long, total: Long, speedBps: Long) {
        if (_currentState.value == TopologyState.SIMPLE_STREAMING) {
            transferredBytes = transferred
            currentFileSize = total
            currentSpeedBps = speedBps
            updateSession(
                isTransitioning = false,
                status = TransferStatus.TRANSFERRING
            )
        }
    }

    private fun updateSession(
        isTransitioning: Boolean,
        status: TransferStatus,
        error: String? = null,
        swarmPeers: List<SwarmPeerInfo> = emptyList(),
        completedChunks: Int = 0,
        totalChunks: Int = 0
    ) {
        val session = TransferSession(
            status = status,
            fileName = currentFileName,
            totalBytes = currentFileSize,
            transferredBytes = transferredBytes,
            speedBytesPerSec = currentSpeedBps,
            isReceiver = isReceiver,
            errorMessage = error,
            topology = _currentTopology.value,
            swarmPeers = swarmPeers,
            completedChunksCount = completedChunks,
            totalChunksCount = totalChunks,
            isSwarmTransitioning = isTransitioning
        )
        scope.launch(Dispatchers.Main) {
            onSessionUpdate(session)
        }
    }

    fun stop() {
        swarmNode?.stop()
        chunkManager?.close()
        swarmNode = null
        chunkManager = null
        _currentState.value = TopologyState.IDLE
    }
}
