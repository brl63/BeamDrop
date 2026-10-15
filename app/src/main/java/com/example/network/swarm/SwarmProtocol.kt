package com.example.network.swarm

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.nio.charset.StandardCharsets

object SwarmProtocol {
    const val MAGIC_BYTE_1: Byte = 0xBE.toByte()
    const val MAGIC_BYTE_2: Byte = 0xAD.toByte()
    const val HEADER_SIZE = 9
    const val DEFAULT_SWARM_PORT = 8082
    // Dynamic Chunk Sizing Constants
    const val CHUNK_SIZE_SMALL: Long = 256 * 1024L // 256 KB for files < 50MB (more pieces for high swarm concurrency)
    const val CHUNK_SIZE_LARGE: Long = 1024 * 1024L // 1 MB for files >= 50MB (reduced tracking overhead)
    const val FILE_SIZE_THRESHOLD_50MB: Long = 50L * 1024 * 1024
    const val CHUNK_SIZE: Long = CHUNK_SIZE_LARGE // Default fallback

    /**
     * Calculates chunk size dynamically based on file size:
     * - < 50MB (images, audio, PDFs): 256 KB chunks
     * - >= 50MB (videos, big APKs, archives): 1 MB chunks
     */
    fun calculateChunkSize(fileSize: Long): Long {
        return if (fileSize < FILE_SIZE_THRESHOLD_50MB) {
            CHUNK_SIZE_SMALL
        } else {
            CHUNK_SIZE_LARGE
        }
    }

    // Frame Types
    const val TYPE_HANDSHAKE: Byte = 0x01
    const val TYPE_SWARM_UPGRADE: Byte = 0x02
    const val TYPE_PEER_LIST: Byte = 0x03
    const val TYPE_BITFIELD: Byte = 0x04
    const val TYPE_HAVE: Byte = 0x05
    const val TYPE_REQUEST_CHUNK: Byte = 0x06
    const val TYPE_CHUNK_DATA: Byte = 0x07
    const val TYPE_PAUSE_SYNC: Byte = 0x08
    const val TYPE_RESUME_SWARM: Byte = 0x09
    const val TYPE_KEEP_ALIVE: Byte = 0x0A

    sealed class SwarmFrame(val type: Byte) {
        data class Handshake(
            val peerId: String,
            val deviceName: String,
            val sessionId: String,
            val listeningPort: Int
        ) : SwarmFrame(TYPE_HANDSHAKE)

        data class SwarmUpgrade(
            val sessionId: String,
            val fileName: String,
            val fileSize: Long,
            val chunkSize: Long,
            val totalChunks: Int,
            val chunkHashes: List<String>,
            val currentStreamOffset: Long
        ) : SwarmFrame(TYPE_SWARM_UPGRADE)

        data class PeerEntry(val peerId: String, val name: String, val ip: String, val port: Int)

        data class PeerList(
            val peers: List<PeerEntry>
        ) : SwarmFrame(TYPE_PEER_LIST)

        data class Bitfield(
            val totalChunks: Int,
            val bits: ByteArray
        ) : SwarmFrame(TYPE_BITFIELD) {
            fun hasChunk(index: Int): Boolean {
                val byteIndex = index / 8
                val bitIndex = index % 8
                if (byteIndex >= bits.size) return false
                return (bits[byteIndex].toInt() and (1 shl bitIndex)) != 0
            }

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false
                other as Bitfield
                if (totalChunks != other.totalChunks) return false
                return bits.contentEquals(other.bits)
            }

            override fun hashCode(): Int {
                var result = totalChunks
                result = 31 * result + bits.contentHashCode()
                return result
            }
        }

        data class Have(
            val chunkIndex: Int
        ) : SwarmFrame(TYPE_HAVE)

        data class RequestChunk(
            val chunkIndex: Int
        ) : SwarmFrame(TYPE_REQUEST_CHUNK)

        data class ChunkData(
            val chunkIndex: Int,
            val data: ByteArray
        ) : SwarmFrame(TYPE_CHUNK_DATA) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false
                other as ChunkData
                if (chunkIndex != other.chunkIndex) return false
                return data.contentEquals(other.data)
            }

            override fun hashCode(): Int {
                var result = chunkIndex
                result = 31 * result + data.contentHashCode()
                return result
            }
        }

        data class PauseSync(
            val targetOffset: Long
        ) : SwarmFrame(TYPE_PAUSE_SYNC)

        data class ResumeSwarm(
            val startChunkIndex: Int
        ) : SwarmFrame(TYPE_RESUME_SWARM)

        object KeepAlive : SwarmFrame(TYPE_KEEP_ALIVE)
    }

    /**
     * Writes a framed packet to the given DataOutputStream.
     */
    @Synchronized
    fun writeFrame(out: DataOutputStream, frame: SwarmFrame) {
        val payload = serializePayload(frame)
        out.writeByte(MAGIC_BYTE_1.toInt())
        out.writeByte(MAGIC_BYTE_2.toInt())
        out.writeByte(frame.type.toInt())
        out.writeInt(payload.size)
        out.writeShort(0) // reserved
        if (payload.isNotEmpty()) {
            out.write(payload)
        }
        out.flush()
    }

    /**
     * Reads a framed packet from the DataInputStream.
     */
    fun readFrame(input: DataInputStream): SwarmFrame {
        val b1 = input.readByte()
        val b2 = input.readByte()
        if (b1 != MAGIC_BYTE_1 || b2 != MAGIC_BYTE_2) {
            throw IOException("Invalid Swarm Magic Bytes: ${b1.toUByte()} ${b2.toUByte()}")
        }
        val type = input.readByte()
        val length = input.readInt()
        input.readShort() // skip reserved

        if (length < 0 || length > 16 * 1024 * 1024) {
            throw IOException("Payload length out of reasonable range: $length")
        }

        val payload = ByteArray(length)
        input.readFully(payload)

        return deserializePayload(type, payload)
    }

    private fun serializePayload(frame: SwarmFrame): ByteArray {
        val bos = java.io.ByteArrayOutputStream()
        val dos = DataOutputStream(bos)
        when (frame) {
            is SwarmFrame.Handshake -> {
                dos.writeUTF(frame.peerId)
                dos.writeUTF(frame.deviceName)
                dos.writeUTF(frame.sessionId)
                dos.writeInt(frame.listeningPort)
            }
            is SwarmFrame.SwarmUpgrade -> {
                dos.writeUTF(frame.sessionId)
                dos.writeUTF(frame.fileName)
                dos.writeLong(frame.fileSize)
                dos.writeLong(frame.chunkSize)
                dos.writeInt(frame.totalChunks)
                dos.writeLong(frame.currentStreamOffset)
                dos.writeInt(frame.chunkHashes.size)
                for (h in frame.chunkHashes) {
                    dos.writeUTF(h)
                }
            }
            is SwarmFrame.PeerList -> {
                dos.writeInt(frame.peers.size)
                for (p in frame.peers) {
                    dos.writeUTF(p.peerId)
                    dos.writeUTF(p.name)
                    dos.writeUTF(p.ip)
                    dos.writeInt(p.port)
                }
            }
            is SwarmFrame.Bitfield -> {
                dos.writeInt(frame.totalChunks)
                dos.writeInt(frame.bits.size)
                dos.write(frame.bits)
            }
            is SwarmFrame.Have -> {
                dos.writeInt(frame.chunkIndex)
            }
            is SwarmFrame.RequestChunk -> {
                dos.writeInt(frame.chunkIndex)
            }
            is SwarmFrame.ChunkData -> {
                dos.writeInt(frame.chunkIndex)
                dos.writeInt(frame.data.size)
                dos.write(frame.data)
            }
            is SwarmFrame.PauseSync -> {
                dos.writeLong(frame.targetOffset)
            }
            is SwarmFrame.ResumeSwarm -> {
                dos.writeInt(frame.startChunkIndex)
            }
            is SwarmFrame.KeepAlive -> {}
        }
        dos.flush()
        return bos.toByteArray()
    }

    private fun deserializePayload(type: Byte, payload: ByteArray): SwarmFrame {
        val dis = DataInputStream(java.io.ByteArrayInputStream(payload))
        return when (type) {
            TYPE_HANDSHAKE -> {
                SwarmFrame.Handshake(
                    peerId = dis.readUTF(),
                    deviceName = dis.readUTF(),
                    sessionId = dis.readUTF(),
                    listeningPort = dis.readInt()
                )
            }
            TYPE_SWARM_UPGRADE -> {
                val sid = dis.readUTF()
                val fname = dis.readUTF()
                val fsize = dis.readLong()
                val csize = dis.readLong()
                val totalC = dis.readInt()
                val streamOffset = dis.readLong()
                val hashCount = dis.readInt()
                val hashes = mutableListOf<String>()
                for (i in 0 until hashCount) {
                    hashes.add(dis.readUTF())
                }
                SwarmFrame.SwarmUpgrade(
                    sessionId = sid,
                    fileName = fname,
                    fileSize = fsize,
                    chunkSize = csize,
                    totalChunks = totalC,
                    chunkHashes = hashes,
                    currentStreamOffset = streamOffset
                )
            }
            TYPE_PEER_LIST -> {
                val count = dis.readInt()
                val peers = mutableListOf<SwarmFrame.PeerEntry>()
                for (i in 0 until count) {
                    peers.add(
                        SwarmFrame.PeerEntry(
                            peerId = dis.readUTF(),
                            name = dis.readUTF(),
                            ip = dis.readUTF(),
                            port = dis.readInt()
                        )
                    )
                }
                SwarmFrame.PeerList(peers)
            }
            TYPE_BITFIELD -> {
                val totalChunks = dis.readInt()
                val bitLen = dis.readInt()
                val bits = ByteArray(bitLen)
                dis.readFully(bits)
                SwarmFrame.Bitfield(totalChunks, bits)
            }
            TYPE_HAVE -> {
                SwarmFrame.Have(dis.readInt())
            }
            TYPE_REQUEST_CHUNK -> {
                SwarmFrame.RequestChunk(dis.readInt())
            }
            TYPE_CHUNK_DATA -> {
                val index = dis.readInt()
                val dataLen = dis.readInt()
                val data = ByteArray(dataLen)
                dis.readFully(data)
                SwarmFrame.ChunkData(index, data)
            }
            TYPE_PAUSE_SYNC -> {
                SwarmFrame.PauseSync(dis.readLong())
            }
            TYPE_RESUME_SWARM -> {
                SwarmFrame.ResumeSwarm(dis.readInt())
            }
            TYPE_KEEP_ALIVE -> SwarmFrame.KeepAlive
            else -> throw IOException("Unknown Swarm Frame Type: $type")
        }
    }
}
