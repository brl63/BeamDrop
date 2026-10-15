package com.example.network

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.model.TransferSession
import com.example.model.TransferStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class EmbeddedFileServer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onProgressUpdate: (TransferSession) -> Unit
) {
    private val tag = "EmbeddedFileServer"
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    var isRunning = false
        private set

    private var currentClientSocket: Socket? = null
    private val receivedFilesList = java.util.Collections.synchronizedList(mutableListOf<String>())

    var sharedFilesProvider: (() -> List<com.example.model.WebSharedFile>)? = null
    var onClientLobbyHeartbeat: ((clientIp: String, deviceName: String, clientType: String) -> Unit)? = null
    var activeLobbyPeersProvider: (() -> List<com.example.model.DiscoveredPeer>)? = null
    var currentTopologyProvider: (() -> String)? = null

    private fun resolveClientDevice(userAgent: String, clientIp: String): Pair<String, String> {
        val cleanIp = clientIp.removePrefix("::ffff:")
        val lower = userAgent.lowercase()
        val isLocal = cleanIp == "127.0.0.1" || cleanIp == "localhost"
        val ipLabel = if (isLocal) "Localhost" else cleanIp
        return when {
            lower.contains("windows") -> "Windows PC" to "Windows PC ($ipLabel)"
            lower.contains("macintosh") || lower.contains("mac os") -> "Mac" to "MacBook ($ipLabel)"
            lower.contains("linux") && !lower.contains("android") -> "Linux PC" to "Linux PC ($ipLabel)"
            lower.contains("cros") -> "Chromebook" to "Chromebook ($ipLabel)"
            lower.contains("iphone") || lower.contains("ipad") -> "iOS" to "iPhone/iPad ($ipLabel)"
            lower.contains("android") -> "Android" to "Android ($ipLabel)"
            else -> "PC Web" to "PC Web ($ipLabel)"
        }
    }

    fun start(port: Int = NetworkUtils.DEFAULT_PORT) {
        if (isRunning) return
        receivedFilesList.clear()

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                isRunning = true
                Log.d(tag, "Embedded HTTP Server started on port $port")

                while (isActive && isRunning) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        handleClientConnection(client)
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.w(tag, "Socket accept error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to start server on port $port", e)
                withContext(Dispatchers.Main) {
                    onProgressUpdate(
                        TransferSession(
                            status = TransferStatus.ERROR,
                            isReceiver = true,
                            errorMessage = "Could not bind port $port: ${e.message}"
                        )
                    )
                }
            } finally {
                isRunning = false
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            currentClientSocket?.close()
        } catch (_: Exception) {}
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverJob?.cancel()
        serverSocket = null
        serverJob = null
        Log.d(tag, "Embedded HTTP Server stopped")
    }

    private suspend fun handleClientConnection(socket: Socket) = withContext(Dispatchers.IO) {
        currentClientSocket = socket
        try {
            socket.soTimeout = 30000 // 30s timeout for stream activity
            val inputStream = BufferedInputStream(socket.getInputStream(), 64 * 1024)
            val outputStream = BufferedOutputStream(socket.getOutputStream())

            // Read HTTP Request line & headers
            val headers = readHttpHeaders(inputStream)
            val requestLine = headers.firstOrNull() ?: ""
            Log.d(tag, "Request: $requestLine")

            val rawIp = socket.inetAddress?.hostAddress ?: "127.0.0.1"
            val clientIp = rawIp.removePrefix("::ffff:")
            val userAgent = headers.find { it.startsWith("User-Agent:", ignoreCase = true) }?.substringAfter(":")?.trim() ?: ""
            val (clientType, deviceName) = resolveClientDevice(userAgent, clientIp)

            // Automatically announce PC/Browser to BeamDrop Lobby
            onClientLobbyHeartbeat?.invoke(clientIp, deviceName, clientType)

            if (requestLine.startsWith("OPTIONS", ignoreCase = true)) {
                sendOptionsResponse(outputStream)
                return@withContext
            }

            if (requestLine.startsWith("GET /info", ignoreCase = true)) {
                sendJsonResponse(
                    outputStream,
                    200,
                    """{"name":"${NetworkUtils.getDeviceDisplayName()}","status":"ready"}"""
                )
                return@withContext
            }

            // Real-time Lobby Ping from PC Web Browser
            if (requestLine.startsWith("POST /api/lobby/ping", ignoreCase = true) ||
                requestLine.startsWith("GET /api/lobby/ping", ignoreCase = true)
            ) {
                val topology = currentTopologyProvider?.invoke() ?: "SIMPLE"
                val peers = activeLobbyPeersProvider?.invoke() ?: emptyList()
                val json = """{"status":"in_lobby","clientIp":"$clientIp","deviceName":"$deviceName","clientType":"$clientType","topology":"$topology","lobbyCount":${peers.size}}"""
                sendJsonResponse(outputStream, 200, json)
                return@withContext
            }

            // Web UI Fallback for PC users
            val rawPath = if (requestLine.startsWith("GET ", ignoreCase = true)) {
                requestLine.substringAfter("GET ", "").substringBefore(" HTTP").trim()
            } else ""

            if (rawPath == "/" || rawPath == "/index.html" || rawPath.isEmpty() && requestLine.startsWith("GET ", ignoreCase = true)) {
                val files = sharedFilesProvider?.invoke() ?: emptyList()
                val html = WebUiHtmlGenerator.getWebPageHtml(NetworkUtils.getDeviceDisplayName(), files)
                sendHtmlResponse(outputStream, 200, html)
                return@withContext
            }

            if (rawPath == "/api/files") {
                val files = sharedFilesProvider?.invoke() ?: emptyList()
                val json = WebUiHtmlGenerator.generateFilesJson(files, NetworkUtils.getDeviceDisplayName())
                sendJsonResponse(outputStream, 200, json)
                return@withContext
            }

            if (rawPath == "/download/apk") {
                handleApkDownload(outputStream)
                return@withContext
            }

            if (rawPath.startsWith("/download")) {
                handleFileDownload(rawPath, outputStream)
                return@withContext
            }

            if (requestLine.startsWith("POST /upload", ignoreCase = true)) {
                handleFileUpload(inputStream, outputStream, headers)
                return@withContext
            }

            // Fallback 404
            sendJsonResponse(outputStream, 404, """{"error":"Not Found"}""")
        } catch (e: Exception) {
            Log.e(tag, "Client processing failed", e)
            withContext(Dispatchers.Main) {
                onProgressUpdate(
                    TransferSession(
                        status = TransferStatus.ERROR,
                        isReceiver = true,
                        errorMessage = "Transfer interrupted: ${e.message}"
                    )
                )
            }
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}
            if (currentClientSocket == socket) {
                currentClientSocket = null
            }
        }
    }

    private fun readHttpHeaders(inputStream: InputStream): List<String> {
        val headers = mutableListOf<String>()
        val lineBuffer = StringBuilder()
        var prevChar = 0

        while (true) {
            val c = inputStream.read()
            if (c == -1) break
            if (c == '\n'.code && prevChar == '\r'.code) {
                val line = lineBuffer.toString().trim()
                if (line.isEmpty()) {
                    // Empty line marks end of headers
                    break
                }
                headers.add(line)
                lineBuffer.clear()
            } else if (c != '\r'.code) {
                lineBuffer.append(c.toChar())
            }
            prevChar = c
        }
        return headers
    }

    private suspend fun handleFileUpload(
        inputStream: InputStream,
        outputStream: OutputStream,
        headers: List<String>
    ) {
        var rawFileName = "received_file_${System.currentTimeMillis()}"
        var contentLength = -1L
        var isApk = false
        var senderName = "Nearby Peer"
        var resumeOffset = 0L

        for (header in headers) {
            val lower = header.lowercase()
            when {
                lower.startsWith("x-file-name:") -> {
                    val encoded = header.substringAfter(":").trim()
                    rawFileName = try {
                        URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
                    } catch (_: Exception) {
                        encoded
                    }
                }
                lower.startsWith("content-length:") -> {
                    contentLength = header.substringAfter(":").trim().toLongOrNull() ?: -1L
                }
                lower.startsWith("x-file-size:") -> {
                    if (contentLength <= 0) {
                        contentLength = header.substringAfter(":").trim().toLongOrNull() ?: -1L
                    }
                }
                lower.startsWith("x-is-apk:") -> {
                    isApk = header.substringAfter(":").trim().equals("true", ignoreCase = true)
                }
                lower.startsWith("x-sender-name:") -> {
                    senderName = header.substringAfter(":").trim()
                }
                lower.startsWith("x-resume-offset:") -> {
                    resumeOffset = header.substringAfter(":").trim().toLongOrNull() ?: 0L
                }
            }
        }

        if (rawFileName.endsWith(".apk", ignoreCase = true)) {
            isApk = true
        }

        // Prepare destination directory in Downloads/BeamDrop
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val beamDropDir = File(downloadsDir, "BeamDrop").apply {
            if (!exists()) mkdirs()
        }

        val destinationFile = if (resumeOffset > 0L) {
            File(beamDropDir, rawFileName)
        } else {
            getUniqueFile(beamDropDir, rawFileName)
        }

        val isAppend = resumeOffset > 0L && destinationFile.exists()
        val fileOutputStream = BufferedOutputStream(FileOutputStream(destinationFile, isAppend), 64 * 1024)

        var totalRead = if (isAppend) destinationFile.length() else 0L

        withContext(Dispatchers.Main) {
            onProgressUpdate(
                TransferSession(
                    status = TransferStatus.TRANSFERRING,
                    fileName = destinationFile.name,
                    totalBytes = contentLength,
                    transferredBytes = totalRead,
                    speedBytesPerSec = 0L,
                    isReceiver = true,
                    isApk = isApk,
                    remotePeerName = senderName
                )
            )
        }

        val buffer = ByteArray(64 * 1024)
        var lastUpdateTimestamp = System.currentTimeMillis()
        var bytesSinceLastUpdate = 0L

        try {
            while (isRunning) {
                val bytesToRead = if (contentLength > 0) {
                    val remaining = contentLength - totalRead
                    if (remaining <= 0) break
                    buffer.size.coerceAtMost(remaining.toInt())
                } else {
                    buffer.size
                }

                val read = inputStream.read(buffer, 0, bytesToRead)
                if (read == -1) break

                fileOutputStream.write(buffer, 0, read)
                totalRead += read
                bytesSinceLastUpdate += read

                val now = System.currentTimeMillis()
                val elapsed = now - lastUpdateTimestamp
                if (elapsed >= 150) {
                    val speed = if (elapsed > 0) (bytesSinceLastUpdate * 1000) / elapsed else 0L
                    withContext(Dispatchers.Main) {
                        onProgressUpdate(
                            TransferSession(
                                status = TransferStatus.TRANSFERRING,
                                fileName = destinationFile.name,
                                totalBytes = if (contentLength > 0) contentLength else totalRead,
                                transferredBytes = totalRead,
                                speedBytesPerSec = speed,
                                isReceiver = true,
                                isApk = isApk,
                                remotePeerName = senderName
                            )
                        )
                    }
                    lastUpdateTimestamp = now
                    bytesSinceLastUpdate = 0L
                }
            }

            fileOutputStream.flush()
            fileOutputStream.close()

            // Success response
            sendJsonResponse(outputStream, 200, """{"status":"ok","fileName":"${destinationFile.name}"}""")

            receivedFilesList.add(destinationFile.absolutePath)
            val currentList = synchronized(receivedFilesList) { receivedFilesList.toList() }

            withContext(Dispatchers.Main) {
                onProgressUpdate(
                    TransferSession(
                        status = TransferStatus.COMPLETED,
                        fileName = destinationFile.name,
                        totalBytes = totalRead,
                        transferredBytes = totalRead,
                        speedBytesPerSec = 0L,
                        isReceiver = true,
                        isApk = isApk,
                        savedFilePath = destinationFile.absolutePath,
                        remotePeerName = senderName,
                        completedFilePaths = currentList
                    )
                )
            }
        } catch (e: Exception) {
            try { fileOutputStream.close() } catch (_: Exception) {}
            // Keep partially downloaded file to enable seamless auto-resume on reconnection
            Log.w(tag, "Upload stream paused/interrupted at $totalRead bytes. Preserving file for resume.")
            throw e
        }
    }

    private fun getUniqueFile(dir: File, fileName: String): File {
        var file = File(dir, fileName)
        if (!file.exists()) return file

        val nameWithoutExt = fileName.substringBeforeLast('.', fileName)
        val ext = if (fileName.contains('.')) ".${fileName.substringAfterLast('.')}" else ""
        var count = 1
        while (file.exists()) {
            file = File(dir, "${nameWithoutExt}_$count$ext")
            count++
        }
        return file
    }

    private fun handleFileDownload(requestPath: String, outputStream: OutputStream) {
        val query = requestPath.substringAfter("?", "")
        val params = query.split("&").associate {
            val parts = it.split("=", limit = 2)
            val k = parts.getOrNull(0) ?: ""
            val v = try { URLDecoder.decode(parts.getOrNull(1) ?: "", StandardCharsets.UTF_8.name()) } catch (_: Exception) { parts.getOrNull(1) ?: "" }
            k to v
        }
        val fileId = params["id"] ?: params["name"] ?: ""
        val files = sharedFilesProvider?.invoke() ?: emptyList()
        val file = files.find { it.id == fileId || it.name == fileId }

        if (file == null) {
            sendJsonResponse(outputStream, 404, """{"error":"File not found"}""")
            return
        }

        try {
            val inStream = when {
                file.uri != null -> context.contentResolver.openInputStream(file.uri)
                file.localFilePath != null -> java.io.FileInputStream(File(file.localFilePath))
                else -> null
            }

            if (inStream == null) {
                sendJsonResponse(outputStream, 404, """{"error":"Cannot open file stream"}""")
                return
            }

            val safeName = file.name.replace("\"", "")
            val header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: ${file.mimeType.ifEmpty { "application/octet-stream" }}\r\n" +
                    "Content-Length: ${file.sizeBytes}\r\n" +
                    "Content-Disposition: attachment; filename=\"$safeName\"\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Connection: close\r\n" +
                    "\r\n"

            outputStream.write(header.toByteArray(StandardCharsets.US_ASCII))
            inStream.use { input ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }
            outputStream.flush()
        } catch (e: Exception) {
            Log.e(tag, "Failed streaming download file", e)
        }
    }

    private fun handleApkDownload(outputStream: OutputStream) {
        try {
            val apkFile = File(context.applicationInfo.sourceDir)
            if (!apkFile.exists()) {
                sendJsonResponse(outputStream, 404, """{"error":"APK not found"}""")
                return
            }

            val header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/vnd.android.package-archive\r\n" +
                    "Content-Length: ${apkFile.length()}\r\n" +
                    "Content-Disposition: attachment; filename=\"BeamDrop.apk\"\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Connection: close\r\n" +
                    "\r\n"

            outputStream.write(header.toByteArray(StandardCharsets.US_ASCII))
            java.io.FileInputStream(apkFile).use { input ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }
            outputStream.flush()
        } catch (e: Exception) {
            Log.e(tag, "Failed streaming APK", e)
        }
    }

    private fun sendHtmlResponse(outputStream: OutputStream, statusCode: Int, html: String) {
        val bytes = html.toByteArray(StandardCharsets.UTF_8)
        val response = "HTTP/1.1 $statusCode OK\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n" +
                "\r\n"

        outputStream.write(response.toByteArray(StandardCharsets.US_ASCII))
        outputStream.write(bytes)
        outputStream.flush()
    }

    private fun sendOptionsResponse(outputStream: OutputStream) {
        val response = "HTTP/1.1 200 OK\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: *\r\n" +
                "Content-Length: 0\r\n" +
                "Connection: close\r\n" +
                "\r\n"
        outputStream.write(response.toByteArray(StandardCharsets.US_ASCII))
        outputStream.flush()
    }

    private fun sendJsonResponse(outputStream: OutputStream, statusCode: Int, json: String) {
        val statusText = if (statusCode == 200) "OK" else "Error"
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        val response = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n" +
                "\r\n"

        outputStream.write(response.toByteArray(StandardCharsets.US_ASCII))
        outputStream.write(bytes)
        outputStream.flush()
    }
}
