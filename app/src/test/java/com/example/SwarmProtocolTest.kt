package com.example

import com.example.network.swarm.ChunkManager
import com.example.network.swarm.SwarmProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SwarmProtocolTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testHandshakeSerialization() {
        val original = SwarmProtocol.SwarmFrame.Handshake(
            peerId = "peer123",
            deviceName = "Pixel 8 Pro",
            sessionId = "session-xyz",
            listeningPort = 8082
        )

        val bos = ByteArrayOutputStream()
        val dos = DataOutputStream(bos)
        SwarmProtocol.writeFrame(dos, original)

        val dis = DataInputStream(ByteArrayInputStream(bos.toByteArray()))
        val deserialized = SwarmProtocol.readFrame(dis) as SwarmProtocol.SwarmFrame.Handshake

        assertEquals(original.peerId, deserialized.peerId)
        assertEquals(original.deviceName, deserialized.deviceName)
        assertEquals(original.sessionId, deserialized.sessionId)
        assertEquals(original.listeningPort, deserialized.listeningPort)
    }

    @Test
    fun testChunkDataFrameRoundtrip() {
        val testData = "Hello Swarm Network P2P Mesh!".toByteArray(Charsets.UTF_8)
        val original = SwarmProtocol.SwarmFrame.ChunkData(
            chunkIndex = 42,
            data = testData
        )

        val bos = ByteArrayOutputStream()
        val dos = DataOutputStream(bos)
        SwarmProtocol.writeFrame(dos, original)

        val dis = DataInputStream(ByteArrayInputStream(bos.toByteArray()))
        val deserialized = SwarmProtocol.readFrame(dis) as SwarmProtocol.SwarmFrame.ChunkData

        assertEquals(42, deserialized.chunkIndex)
        assertArrayEquals(testData, deserialized.data)
    }

    @Test
    fun testBitfieldOperations() {
        val bits = ByteArray(2) // 16 bits
        bits[0] = 0b00000101.toByte() // bits 0 and 2 set

        val bitfield = SwarmProtocol.SwarmFrame.Bitfield(totalChunks = 16, bits = bits)
        assertTrue(bitfield.hasChunk(0))
        assertFalse(bitfield.hasChunk(1))
        assertTrue(bitfield.hasChunk(2))
        assertFalse(bitfield.hasChunk(3))
        assertFalse(bitfield.hasChunk(8))
    }

    @Test
    fun testChunkManagerSha256Verification() {
        val file = tempFolder.newFile("test_chunk_file.bin")
        val sampleChunk = "BeamDrop P2P Chunk Data with Verified SHA-256".toByteArray(Charsets.UTF_8)
        val expectedHash = ChunkManager.sha256Hex(sampleChunk)

        val chunkManager = ChunkManager(
            sourceFile = file,
            fileSize = sampleChunk.size.toLong(),
            chunkSize = sampleChunk.size.toLong(),
            chunkHashes = listOf(expectedHash)
        )

        // Write corrupted data -> should fail
        val corruptedData = "Corrupted chunk data!".toByteArray(Charsets.UTF_8)
        val badWrite = chunkManager.writeChunk(0, corruptedData)
        assertFalse(badWrite)
        assertFalse(chunkManager.isChunkComplete(0))

        // Write valid data -> should succeed
        val goodWrite = chunkManager.writeChunk(0, sampleChunk)
        assertTrue(goodWrite)
        assertTrue(chunkManager.isChunkComplete(0))
        assertTrue(chunkManager.isAllComplete())

        // Read chunk back
        val readBack = chunkManager.readChunk(0)
        assertArrayEquals(sampleChunk, readBack)

        chunkManager.close()
    }
}
