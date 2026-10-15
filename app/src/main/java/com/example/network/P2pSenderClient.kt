package com.example.network

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.model.TransferSession
import com.example.model.TransferStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class P2pSenderClient(private val context: Context) {
    private val tag = "P2pSenderClient"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS) // No timeout for streaming large transfers
        .build()

    private var activeCall: Call? = null

    suspend fun streamFile(
        targetIp: String,
        targetPort: Int,
        fileUri: Uri? = null,
        localFilePath: String? = null,
        overrideFileName: String? = null,
        isApk: Boolean = false,
        remotePeerName: String = "",
        onProgressUpdate: (TransferSession) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val fileName: String
            val fileSize: Long
            val streamProvider: () -> InputStream

            if (fileUri != null) {
                fileName = overrideFileName ?: resolveFileNameFromUri(fileUri)
                fileSize = resolveFileSizeFromUri(fileUri)
                streamProvider = {
                    context.contentResolver.openInputStream(fileUri)
                        ?: throw IllegalStateException("Could not open input stream for Uri: $fileUri")
                }
            } else if (!localFilePath.isNullOrEmpty()) {
                val file = File(localFilePath)
                if (!file.exists()) {
                    throw IllegalArgumentException("File does not exist: $localFilePath")
                }
                fileName = overrideFileName ?: file.name
                fileSize = file.length()
                streamProvider = { FileInputStream(file) }
            } else {
                throw IllegalArgumentException("Neither fileUri nor localFilePath provided")
            }

            withContext(Dispatchers.Main) {
                onProgressUpdate(
                    TransferSession(
                        status = TransferStatus.CONNECTING,
                        fileName = fileName,
                        totalBytes = fileSize,
                        transferredBytes = 0L,
                        isReceiver = false,
                        isApk = isApk,
                        remotePeerName = remotePeerName
                    )
                )
            }

            val encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name())
            val myDeviceName = NetworkUtils.getDeviceDisplayName()

            var attempts = 0
            val maxAttempts = 3
            var confirmedBytesWritten = 0L
            var transferSuccess = false

            while (attempts < maxAttempts && !transferSuccess && activeCall?.isCanceled() != true) {
                attempts++
                try {
                    if (attempts > 1) {
                        Log.i(tag, "Reconnection attempt $attempts/$maxAttempts starting from offset $confirmedBytesWritten bytes")
                        withContext(Dispatchers.Main) {
                            onProgressUpdate(
                                TransferSession(
                                    status = TransferStatus.CONNECTING,
                                    fileName = fileName,
                                    totalBytes = fileSize,
                                    transferredBytes = confirmedBytesWritten,
                                    isReceiver = false,
                                    isApk = isApk,
                                    remotePeerName = "$remotePeerName (Reconnecting $attempts/$maxAttempts)",
                                    errorMessage = "Network interrupted. Auto-reconnecting from last byte..."
                                )
                            )
                        }
                    }

                    val currentOffset = confirmedBytesWritten
                    val requestBody = object : RequestBody() {
                        override fun contentType(): MediaType? = "application/octet-stream".toMediaTypeOrNull()

                        override fun contentLength(): Long = if (fileSize > 0) fileSize - currentOffset else -1L

                        override fun writeTo(sink: BufferedSink) {
                            val inStream = streamProvider()
                            val buffer = ByteArray(64 * 1024)
                            var bytesWrittenThisAttempt = 0L
                            var lastUpdateTimestamp = System.currentTimeMillis()
                            var bytesSinceLastUpdate = 0L

                            try {
                                inStream.use { stream ->
                                    // Skip already transmitted bytes for resumable stream
                                    if (currentOffset > 0) {
                                        var remainingToSkip = currentOffset
                                        while (remainingToSkip > 0) {
                                            val skipped = stream.skip(remainingToSkip)
                                            if (skipped <= 0) {
                                                // Read and discard if skip returns 0
                                                val discarded = stream.read(buffer, 0, minOf(buffer.size.toLong(), remainingToSkip).toInt())
                                                if (discarded == -1) break
                                                remainingToSkip -= discarded
                                            } else {
                                                remainingToSkip -= skipped
                                            }
                                        }
                                        Log.d(tag, "Successfully skipped $currentOffset bytes to resume stream")
                                    }

                                    while (true) {
                                        val read = stream.read(buffer)
                                        if (read == -1) break
                                        sink.write(buffer, 0, read)
                                        bytesWrittenThisAttempt += read
                                        bytesSinceLastUpdate += read
                                        confirmedBytesWritten = currentOffset + bytesWrittenThisAttempt

                                        val now = System.currentTimeMillis()
                                        val elapsed = now - lastUpdateTimestamp
                                        if (elapsed >= 150) {
                                            val speed = if (elapsed > 0) (bytesSinceLastUpdate * 1000) / elapsed else 0L
                                            val totalCurrent = confirmedBytesWritten
                                            scopeDispatchProgress(
                                                TransferSession(
                                                    status = TransferStatus.TRANSFERRING,
                                                    fileName = fileName,
                                                    totalBytes = fileSize,
                                                    transferredBytes = totalCurrent,
                                                    speedBytesPerSec = speed,
                                                    isReceiver = false,
                                                    isApk = isApk,
                                                    remotePeerName = remotePeerName
                                                ),
                                                onProgressUpdate
                                            )
                                            lastUpdateTimestamp = now
                                            bytesSinceLastUpdate = 0L
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(tag, "Attempt $attempts write interrupted at $confirmedBytesWritten bytes: ${e.message}")
                                throw e
                            }
                        }
                    }

                    val requestBuilder = Request.Builder()
                        .url("http://$targetIp:$targetPort/upload")
                        .header("X-File-Name", encodedFileName)
                        .header("X-File-Size", fileSize.toString())
                        .header("X-Is-Apk", isApk.toString())
                        .header("X-Sender-Name", myDeviceName)

                    if (currentOffset > 0) {
                        requestBuilder.header("X-Resume-Offset", currentOffset.toString())
                    }

                    activeCall = client.newCall(requestBuilder.build())
                    val response = activeCall?.execute() ?: throw IllegalStateException("Call execution failed")

                    if (response.isSuccessful) {
                        transferSuccess = true
                        withContext(Dispatchers.Main) {
                            onProgressUpdate(
                                TransferSession(
                                    status = TransferStatus.COMPLETED,
                                    fileName = fileName,
                                    totalBytes = fileSize,
                                    transferredBytes = fileSize,
                                    speedBytesPerSec = 0L,
                                    isReceiver = false,
                                    isApk = isApk,
                                    remotePeerName = remotePeerName
                                )
                            )
                        }
                    } else {
                        throw IllegalStateException("Receiver returned HTTP ${response.code}: ${response.message}")
                    }
                } catch (e: Exception) {
                    if (activeCall?.isCanceled() == true) {
                        Log.d(tag, "Transfer cancelled by user")
                        break
                    }
                    if (attempts >= maxAttempts) {
                        Log.e(tag, "All $maxAttempts transfer attempts exhausted", e)
                        throw e
                    } else {
                        Log.w(tag, "Connection dropped. Waiting before retry $attempts...", e)
                        kotlinx.coroutines.delay(1200L * attempts)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Upload error", e)
            val isCancelled = activeCall?.isCanceled() == true
            withContext(Dispatchers.Main) {
                onProgressUpdate(
                    TransferSession(
                        status = if (isCancelled) TransferStatus.CANCELLED else TransferStatus.ERROR,
                        isReceiver = false,
                        errorMessage = if (isCancelled) "Transfer cancelled" else "Transfer failed: ${e.message}"
                    )
                )
            }
        } finally {
            activeCall = null
        }
    }

    private fun scopeDispatchProgress(
        session: TransferSession,
        onProgressUpdate: (TransferSession) -> Unit
    ) {
        kotlinx.coroutines.runBlocking(Dispatchers.Main) {
            onProgressUpdate(session)
        }
    }

    fun cancel() {
        try {
            activeCall?.cancel()
        } catch (_: Exception) {}
        activeCall = null
    }

    private fun resolveFileNameFromUri(uri: Uri): String {
        var name = "file_${System.currentTimeMillis()}"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (_: Exception) {}
        return name
    }

    private fun resolveFileSizeFromUri(uri: Uri): Long {
        var size = -1L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        } catch (_: Exception) {}
        return size
    }
}
