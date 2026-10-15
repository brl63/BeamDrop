package com.example.network.swarm

import android.content.Context
import android.util.Log
import com.example.model.SwarmPeerInfo
import com.example.network.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class SwarmNode(
    val myPeerId: String,
    val myDeviceName: String,
    val sessionId: String,
    val listeningPort: Int = SwarmProtocol.DEFAULT_SWARM_PORT,
    val chunkManager: ChunkManager,
    private val scope: CoroutineScope,
    private val onProgress: (transferredBytes: Long, totalBytes: Long, speedBps: Long, swarmPeers: List<SwarmPeerInfo>) -> Unit,
    private val onComplete: () -> Unit
) {
    private val tag = "SwarmNode"
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val isRunning = AtomicBoolean(false)

    // Connected peers: peerId -> PeerChannel
    private val activePeers = ConcurrentHashMap<String, PeerChannel>()

    // Global piece availability counter: chunkIndex -> count of peers possessing it
    private val pieceAvailability = ConcurrentHashMap<Int, Int>()

    private val transferredBytesAccumulator = AtomicLong(0)
    private var lastSpeedCheckTimestamp = System.currentTimeMillis()
    private var lastSpeedTransferredBytes = 0L
    private var currentSpeedBps = 0L

    fun start() {
        if (!isRunning.compareAndSet(false, true)) return
        Log.d(tag, "Starting SwarmNode on port $listeningPort for session $sessionId")

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(listeningPort))
                }
                Log.d(tag, "Swarm ServerSocket bound to port $listeningPort")

                while (isActive && isRunning.get()) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        launch { handleIncomingSocket(client) }
                    } catch (e: Exception) {
                        if (isRunning.get()) {
                            Log.w(tag, "Swarm accept error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Swarm ServerSocket failed on port $listeningPort", e)
            }
        }

        // Periodic metrics reporter
        scope.launch(Dispatchers.IO) {
            while (isActive && isRunning.get()) {
                delay(500)
                updateMetrics()
                if (chunkManager.isAllComplete()) {
                    Log.d(tag, "All chunks complete in SwarmNode!")
                    withContext(Dispatchers.Main) {
                        onComplete()
                    }
                    break
                }
            }
        }
    }

    fun connectToPeer(ip: String, port: Int, peerName: String = "Peer") {
        if (!isRunning.get()) return
        scope.launch(Dispatchers.IO) {
            try {
                Log.d(tag, "Connecting to swarm peer at $ip:$port...")
                val socket = Socket()
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(ip, port), 6000)
                setupPeerConnection(socket, isIncoming = false, suggestedName = peerName)
            } catch (e: Exception) {
                Log.w(tag, "Failed to connect to swarm peer at $ip:$port: ${e.message}")
            }
        }
    }

    private suspend fun handleIncomingSocket(socket: Socket) = withContext(Dispatchers.IO) {
        socket.tcpNoDelay = true
        setupPeerConnection(socket, isIncoming = true)
    }

    private fun setupPeerConnection(socket: Socket, isIncoming: Boolean, suggestedName: String = "Peer") {
        val remoteAddress = socket.inetAddress?.hostAddress ?: "unknown"
        try {
            val dis = DataInputStream(BufferedInputStream(socket.getInputStream(), 64 * 1024))
            val dos = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), 64 * 1024))

            // 1. Send Handshake
            SwarmProtocol.writeFrame(
                dos,
                SwarmProtocol.SwarmFrame.Handshake(
                    peerId = myPeerId,
                    deviceName = myDeviceName,
                    sessionId = sessionId,
                    listeningPort = listeningPort
                )
            )

            // 2. Read Remote Handshake
            val remoteHandshake = SwarmProtocol.readFrame(dis) as? SwarmProtocol.SwarmFrame.Handshake
                ?: throw IOException("Expected Handshake frame")

            if (remoteHandshake.sessionId != sessionId) {
                Log.w(tag, "Session ID mismatch: ${remoteHandshake.sessionId} vs $sessionId. Closing socket.")
                socket.close()
                return
            }

            val peerId = remoteHandshake.peerId
            val peerName = remoteHandshake.deviceName.ifBlank { suggestedName }

            // 3. Send our current Bitfield
            SwarmProtocol.writeFrame(dos, chunkManager.getBitfield())

            // 4. Create PeerChannel
            val channel = PeerChannel(
                peerId = peerId,
                name = peerName,
                ip = remoteAddress,
                port = remoteHandshake.listeningPort,
                socket = socket,
                dis = dis,
                dos = dos
            )
            activePeers[peerId] = channel
            Log.d(tag, "Swarm Peer Connected: $peerName ($peerId) at $remoteAddress")

            // 5. Start Reader loop & Request loop for this peer
            startPeerWorker(channel)
        } catch (e: Exception) {
            Log.e(tag, "Error setting up peer connection with $remoteAddress: ${e.message}")
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun startPeerWorker(channel: PeerChannel) {
        // Reader loop
        channel.readerJob = scope.launch(Dispatchers.IO) {
            try {
                while (isActive && isRunning.get()) {
                    val frame = SwarmProtocol.readFrame(channel.dis)
                    handleFrame(channel, frame)
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    Log.d(tag, "Peer ${channel.name} disconnected: ${e.message}")
                }
            } finally {
                removePeer(channel.peerId)
            }
        }

        // Leeching / Request loop
        channel.requestJob = scope.launch(Dispatchers.IO) {
            while (isActive && isRunning.get() && !chunkManager.isAllComplete()) {
                val bitfield = channel.bitfield
                if (bitfield != null) {
                    val chunkIndex = chunkManager.pickNextChunkToRequest(bitfield, pieceAvailability)
                    if (chunkIndex != null) {
                        try {
                            SwarmProtocol.writeFrame(channel.dos, SwarmProtocol.SwarmFrame.RequestChunk(chunkIndex))
                        } catch (e: Exception) {
                            chunkManager.releaseInFlight(chunkIndex)
                            break
                        }
                    }
                }
                delay(30)
            }
        }
    }

    private fun handleFrame(channel: PeerChannel, frame: SwarmProtocol.SwarmFrame) {
        when (frame) {
            is SwarmProtocol.SwarmFrame.Bitfield -> {
                channel.bitfield = frame
                // Update availability
                for (i in 0 until frame.totalChunks) {
                    if (frame.hasChunk(i)) {
                        pieceAvailability[i] = (pieceAvailability[i] ?: 0) + 1
                    }
                }
                Log.d(tag, "Received Bitfield from ${channel.name}: has ${countBits(frame)} chunks")
            }
            is SwarmProtocol.SwarmFrame.Have -> {
                val idx = frame.chunkIndex
                channel.bitfield?.let {
                    val byteIdx = idx / 8
                    val bitIdx = idx % 8
                    if (byteIdx < it.bits.size) {
                        it.bits[byteIdx] = (it.bits[byteIdx].toInt() or (1 shl bitIdx)).toByte()
                    }
                }
                pieceAvailability[idx] = (pieceAvailability[idx] ?: 0) + 1
            }
            is SwarmProtocol.SwarmFrame.RequestChunk -> {
                // Seeding: serve chunk to peer
                val chunkData = chunkManager.readChunk(frame.chunkIndex)
                if (chunkData != null) {
                    synchronized(channel.dos) {
                        SwarmProtocol.writeFrame(
                            channel.dos,
                            SwarmProtocol.SwarmFrame.ChunkData(frame.chunkIndex, chunkData)
                        )
                    }
                    channel.uploadedBytes.addAndGet(chunkData.size.toLong())
                }
            }
            is SwarmProtocol.SwarmFrame.ChunkData -> {
                // Leeching: write chunk and verify hash
                val success = chunkManager.writeChunk(frame.chunkIndex, frame.data)
                if (success) {
                    channel.downloadedBytes.addAndGet(frame.data.size.toLong())
                    transferredBytesAccumulator.addAndGet(frame.data.size.toLong())
                    Log.d(tag, "Successfully verified & wrote chunk #${frame.chunkIndex} from ${channel.name}!")

                    // INSTANT SEEDER ACTIVATION: Broadcast HAVE to all connected peers!
                    broadcastHave(frame.chunkIndex)
                } else {
                    Log.w(tag, "Rejected corrupted chunk #${frame.chunkIndex} from ${channel.name}")
                }
            }
            is SwarmProtocol.SwarmFrame.KeepAlive -> {
                // No-op
            }
            else -> {
                Log.d(tag, "Unhandled frame type: ${frame.type}")
            }
        }
    }

    /**
     * Broadcasts HAVE frame to all peers as soon as a chunk is received
     */
    fun broadcastHave(chunkIndex: Int) {
        val haveFrame = SwarmProtocol.SwarmFrame.Have(chunkIndex)
        for (peer in activePeers.values) {
            scope.launch(Dispatchers.IO) {
                try {
                    synchronized(peer.dos) {
                        SwarmProtocol.writeFrame(peer.dos, haveFrame)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun removePeer(peerId: String) {
        val channel = activePeers.remove(peerId) ?: return
        channel.readerJob?.cancel()
        channel.requestJob?.cancel()
        try { channel.socket.close() } catch (_: Exception) {}
        Log.d(tag, "Cleaned up peer ${channel.name} ($peerId)")
    }

    private fun updateMetrics() {
        val now = System.currentTimeMillis()
        val dt = (now - lastSpeedCheckTimestamp).coerceAtLeast(1)
        val currentTransferred = chunkManager.getCompletedCount() * chunkManager.chunkSize
        val deltaBytes = (currentTransferred - lastSpeedTransferredBytes).coerceAtLeast(0)

        currentSpeedBps = (deltaBytes * 1000) / dt
        lastSpeedCheckTimestamp = now
        lastSpeedTransferredBytes = currentTransferred

        val peerList = activePeers.values.map { p ->
            val peerCompleted = p.bitfield?.let { countBits(it) } ?: 0
            SwarmPeerInfo(
                id = p.peerId,
                name = p.name,
                ip = p.ip,
                port = p.port,
                completedChunks = peerCompleted,
                totalChunks = chunkManager.totalChunks,
                isSeeder = peerCompleted >= chunkManager.totalChunks,
                speedBytesPerSec = p.downloadedBytes.get()
            )
        }

        val totalBytes = chunkManager.fileSize
        val transferred = (chunkManager.getCompletedCount().toLong() * chunkManager.chunkSize).coerceAtMost(totalBytes)

        scope.launch(Dispatchers.Main) {
            onProgress(transferred, totalBytes, currentSpeedBps, peerList)
        }
    }

    private fun countBits(bitfield: SwarmProtocol.SwarmFrame.Bitfield): Int {
        var count = 0
        for (i in 0 until bitfield.totalChunks) {
            if (bitfield.hasChunk(i)) count++
        }
        return count
    }

    fun stop() {
        if (!isRunning.compareAndSet(true, false)) return
        for (peer in activePeers.values) {
            peer.readerJob?.cancel()
            peer.requestJob?.cancel()
            try { peer.socket.close() } catch (_: Exception) {}
        }
        activePeers.clear()
        try { serverSocket?.close() } catch (_: Exception) {}
        serverJob?.cancel()
        serverSocket = null
        chunkManager.close()
        Log.d(tag, "SwarmNode stopped.")
    }

    private class PeerChannel(
        val peerId: String,
        val name: String,
        val ip: String,
        val port: Int,
        val socket: Socket,
        val dis: DataInputStream,
        val dos: DataOutputStream,
        var bitfield: SwarmProtocol.SwarmFrame.Bitfield? = null,
        val downloadedBytes: AtomicLong = AtomicLong(0),
        val uploadedBytes: AtomicLong = AtomicLong(0),
        var readerJob: Job? = null,
        var requestJob: Job? = null
    )
}
