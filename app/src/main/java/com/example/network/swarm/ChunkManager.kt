package com.example.network.swarm

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.system.Os
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.BitSet
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.min

/**
 * Android 11+ Scoped Storage Compliant Chunk Manager for Swarm P2P Transfers.
 *
 * Features:
 * - Dynamic Chunk Sizing (256 KB for < 50MB files, 1 MB for >= 50MB files).
 * - Scoped Storage Compliance: Uses MediaStore / SAF ParcelFileDescriptor to avoid "Permission Denied"
 *   crashes on Android 11+ (API 30+) in public Downloads directory.
 * - High-performance NIO FileChannel with sparse random-access writing and Os.ftruncate preallocation.
 * - Thread-safe Bitfield tracking, SHA-256 chunk integrity verification, and rarest-first piece picking.
 */
class ChunkManager(
    val context: Context? = null,
    val targetUri: Uri? = null,
    val sourceFile: File? = null,
    val fileSize: Long,
    val chunkSize: Long = SwarmProtocol.calculateChunkSize(fileSize),
    val chunkHashes: List<String>,
    providedPfd: ParcelFileDescriptor? = null
) {
    private val tag = "ChunkManager"
    val totalChunks: Int = if (fileSize <= 0L) 1 else ceil(fileSize.toDouble() / chunkSize).toInt()

    private val bitset = BitSet(totalChunks)
    private val inFlightChunks = ConcurrentHashMap.newKeySet<Int>()

    // Scoped Storage File Descriptors and NIO Channels
    private var pfd: ParcelFileDescriptor? = providedPfd
    private var writeChannel: FileChannel? = null
    private var readChannel: FileChannel? = null
    private var writeStream: FileOutputStream? = null
    private var readStream: FileInputStream? = null
    private var randomAccessFile: RandomAccessFile? = null

    private fun logE(msg: String, tr: Throwable? = null) {
        try {
            if (tr != null) Log.e(tag, msg, tr) else Log.e(tag, msg)
        } catch (_: Exception) {
            System.err.println("[$tag] $msg")
        }
    }

    init {
        require(chunkHashes.size == totalChunks) {
            "Mismatch chunk hashes count: expected $totalChunks, got ${chunkHashes.size}"
        }
        initStorage()
    }

    @Synchronized
    private fun initStorage() {
        try {
            // Case 1: Provided or openable ParcelFileDescriptor via MediaStore / SAF Uri (Android 11+ Scoped Storage)
            if (pfd == null && targetUri != null && context != null) {
                pfd = context.contentResolver.openFileDescriptor(targetUri, "rw")
                    ?: context.contentResolver.openFileDescriptor(targetUri, "r")
            }

            if (pfd != null) {
                val fd = pfd!!.fileDescriptor
                writeStream = FileOutputStream(fd)
                writeChannel = writeStream?.channel
                readStream = FileInputStream(fd)
                readChannel = readStream?.channel

                // Sparse NIO pre-allocation via Linux ftruncate
                try {
                    if (fileSize > 0) {
                        Os.ftruncate(fd, fileSize)
                        Log.d(tag, "Sparse allocated $fileSize bytes via ftruncate on ParcelFileDescriptor")
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Sparse ftruncate note: ${e.message}")
                }
                return
            }

            // Case 2: Legacy File-based access (e.g. Sender seeding own local file or internal cache)
            if (sourceFile != null) {
                if (!sourceFile.parentFile?.exists()!!) {
                    sourceFile.parentFile?.mkdirs()
                }
                randomAccessFile = RandomAccessFile(sourceFile, "rw")
                writeChannel = randomAccessFile?.channel
                readChannel = randomAccessFile?.channel
                if (randomAccessFile?.length() != fileSize && fileSize > 0) {
                    randomAccessFile?.setLength(fileSize)
                }
            }
        } catch (e: Exception) {
            logE("Failed to initialize storage channel: ${e.message}", e)
        }
    }

    /**
     * Mark chunks from [fromIndex] until [untilIndex] as possessed (e.g. during stream-to-swarm upgrade)
     */
    @Synchronized
    fun markChunksPossessed(fromIndex: Int, untilIndex: Int) {
        val safeUntil = min(untilIndex, totalChunks)
        for (i in fromIndex until safeUntil) {
            bitset.set(i, true)
            inFlightChunks.remove(i)
        }
    }

    @Synchronized
    fun isChunkComplete(index: Int): Boolean {
        if (index < 0 || index >= totalChunks) return false
        return bitset.get(index)
    }

    @Synchronized
    fun getCompletedCount(): Int {
        return bitset.cardinality()
    }

    @Synchronized
    fun isAllComplete(): Boolean {
        return bitset.cardinality() >= totalChunks
    }

    @Synchronized
    fun getBitfield(): SwarmProtocol.SwarmFrame.Bitfield {
        val byteCount = ceil(totalChunks / 8.0).toInt()
        val bytes = ByteArray(byteCount)
        for (i in 0 until totalChunks) {
            if (bitset.get(i)) {
                val byteIndex = i / 8
                val bitIndex = i % 8
                bytes[byteIndex] = (bytes[byteIndex].toInt() or (1 shl bitIndex)).toByte()
            }
        }
        return SwarmProtocol.SwarmFrame.Bitfield(totalChunks, bytes)
    }

    /**
     * Writes chunk data to sparse NIO FileChannel at position [chunkIndex * chunkSize],
     * validating against pre-computed SHA-256 hash.
     */
    fun writeChunk(chunkIndex: Int, data: ByteArray): Boolean {
        if (chunkIndex < 0 || chunkIndex >= totalChunks) return false
        val expectedHash = chunkHashes[chunkIndex]
        val actualHash = sha256Hex(data)

        if (!actualHash.equals(expectedHash, ignoreCase = true)) {
            logE("Hash mismatch for chunk $chunkIndex! Expected $expectedHash, got $actualHash")
            inFlightChunks.remove(chunkIndex)
            return false
        }

        val offset = chunkIndex * chunkSize
        val channel = writeChannel ?: return false

        synchronized(this) {
            try {
                val buffer = ByteBuffer.wrap(data)
                channel.position(offset)
                while (buffer.hasRemaining()) {
                    channel.write(buffer)
                }
                bitset.set(chunkIndex, true)
                inFlightChunks.remove(chunkIndex)
                return true
            } catch (e: Exception) {
                logE("Failed to write chunk $chunkIndex to disk via FileChannel", e)
                inFlightChunks.remove(chunkIndex)
                return false
            }
        }
    }

    /**
     * Reads chunk data from sparse NIO FileChannel for seeding to peer.
     */
    fun readChunk(chunkIndex: Int): ByteArray? {
        if (!isChunkComplete(chunkIndex)) return null
        val offset = chunkIndex * chunkSize
        val expectedLength = if (chunkIndex == totalChunks - 1) {
            (fileSize - offset).toInt()
        } else {
            chunkSize.toInt()
        }

        val channel = readChannel ?: writeChannel ?: return null
        synchronized(this) {
            try {
                val buffer = ByteBuffer.allocate(expectedLength)
                channel.position(offset)
                var bytesRead = 0
                while (bytesRead < expectedLength) {
                    val read = channel.read(buffer)
                    if (read == -1) break
                    bytesRead += read
                }
                return buffer.array()
            } catch (e: Exception) {
                logE("Failed to read chunk $chunkIndex from disk via FileChannel", e)
                return null
            }
        }
    }

    /**
     * Rarest-first piece selection algorithm.
     */
    @Synchronized
    fun pickNextChunkToRequest(
        peerBitfield: SwarmProtocol.SwarmFrame.Bitfield,
        pieceAvailability: Map<Int, Int>? = null
    ): Int? {
        val candidates = mutableListOf<Int>()
        for (i in 0 until totalChunks) {
            if (!bitset.get(i) && !inFlightChunks.contains(i) && peerBitfield.hasChunk(i)) {
                candidates.add(i)
            }
        }
        if (candidates.isEmpty()) return null

        val selected = if (pieceAvailability != null) {
            candidates.minByOrNull { pieceAvailability[it] ?: 999 } ?: candidates.first()
        } else {
            candidates.first()
        }

        inFlightChunks.add(selected)
        return selected
    }

    fun releaseInFlight(chunkIndex: Int) {
        inFlightChunks.remove(chunkIndex)
    }

    @Synchronized
    fun close() {
        try {
            writeChannel?.force(true)
            writeChannel?.close()
            readChannel?.close()
            writeStream?.close()
            readStream?.close()
            pfd?.close()
            randomAccessFile?.close()
        } catch (_: Exception) {}

        writeChannel = null
        readChannel = null
        writeStream = null
        readStream = null
        pfd = null
        randomAccessFile = null

        // Scoped Storage: finalize file in MediaStore by clearing IS_PENDING
        targetUri?.let { uri ->
            context?.let { ctx ->
                completeMediaStoreFile(ctx, uri)
            }
        }
    }

    companion object {
        fun sha256Hex(data: ByteArray): String {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(data)
            return digest.joinToString("") { "%02x".format(it) }
        }

        /**
         * Calculates SHA-256 hashes per dynamically calculated chunk size
         */
        fun computeChunkHashes(
            inputStreamProvider: () -> InputStream,
            fileSize: Long,
            chunkSize: Long = SwarmProtocol.calculateChunkSize(fileSize)
        ): List<String> {
            val hashes = mutableListOf<String>()
            val buffer = ByteArray(chunkSize.toInt())
            val stream = inputStreamProvider()
            val md = MessageDigest.getInstance("SHA-256")

            try {
                var totalBytesRead = 0L
                while (totalBytesRead < fileSize) {
                    val bytesToRead = min(chunkSize, fileSize - totalBytesRead).toInt()
                    var bytesReadThisChunk = 0
                    md.reset()

                    while (bytesReadThisChunk < bytesToRead) {
                        val count = stream.read(buffer, bytesReadThisChunk, bytesToRead - bytesReadThisChunk)
                        if (count == -1) break
                        bytesReadThisChunk += count
                    }

                    if (bytesReadThisChunk > 0) {
                        md.update(buffer, 0, bytesReadThisChunk)
                        hashes.add(md.digest().joinToString("") { "%02x".format(it) })
                        totalBytesRead += bytesReadThisChunk
                    } else {
                        break
                    }
                }
            } finally {
                try { stream.close() } catch (_: Exception) {}
            }
            return hashes
        }

        /**
         * Creates a MediaStore.Downloads destination entry compliant with Android 11+ Scoped Storage.
         * Returns the Uri and open ParcelFileDescriptor ready for NIO FileChannel writing.
         */
        fun createDestinationPfd(
            context: Context,
            fileName: String,
            fileSize: Long
        ): Pair<Uri, ParcelFileDescriptor> {
            val resolver = context.contentResolver
            val mimeType = getMimeType(fileName)
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/BeamDrop")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val beamDropDir = File(downloadsDir, "BeamDrop").apply { if (!exists()) mkdirs() }
                val targetFile = File(beamDropDir, fileName)
                Uri.fromFile(targetFile)
            } ?: throw IOException("Failed to create MediaStore entry for $fileName")

            val pfd = resolver.openFileDescriptor(uri, "rw")
                ?: throw IOException("Failed to open ParcelFileDescriptor for $uri")

            return Pair(uri, pfd)
        }

        fun completeMediaStoreFile(context: Context, uri: Uri) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    context.contentResolver.update(uri, values, null, null)
                    Log.d("ChunkManager", "Cleared IS_PENDING flag for MediaStore file: $uri")
                } catch (e: Exception) {
                    Log.w("ChunkManager", "Failed to clear IS_PENDING on uri $uri: ${e.message}")
                }
            }
        }

        private fun getMimeType(fileName: String): String {
            val extension = fileName.substringAfterLast('.', "").lowercase()
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
        }
    }
}
