package com.example.model

import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap

data class DiscoveredPeer(
    val id: String,
    val name: String,
    val ip: String,
    val port: Int = 8080,
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val wifiSsid: String? = null,
    val wifiPassword: String? = null,
    val isWebClient: Boolean = false,
    val clientType: String = "Mobile"
)

data class InstalledAppInfo(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val apkPath: String,
    val sizeBytes: Long,
    val iconBitmap: ImageBitmap? = null,
    val rawIcon: Drawable? = null
) {
    val formattedSize: String
        get() = formatBytes(sizeBytes)
}

enum class TransferStatus {
    IDLE,
    CONNECTING,
    TRANSFERRING,
    COMPLETED,
    ERROR,
    CANCELLED
}

enum class TransferTopology {
    SIMPLE,
    SWARM
}

data class SwarmPeerInfo(
    val id: String,
    val name: String,
    val ip: String,
    val port: Int,
    val completedChunks: Int,
    val totalChunks: Int,
    val isSeeder: Boolean,
    val speedBytesPerSec: Long = 0L
) {
    val progressPercent: Int
        get() = if (totalChunks > 0) ((completedChunks.toFloat() / totalChunks.toFloat()) * 100).toInt() else 0
}

data class TransferSession(
    val status: TransferStatus = TransferStatus.IDLE,
    val fileName: String = "",
    val totalBytes: Long = 0L,
    val transferredBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val isReceiver: Boolean = false,
    val isApk: Boolean = false,
    val savedFilePath: String? = null,
    val errorMessage: String? = null,
    val remotePeerName: String = "",
    val batchCurrentIndex: Int = 1,
    val batchTotalCount: Int = 1,
    val batchTotalBytes: Long = 0L,
    val batchTransferredBytes: Long = 0L,
    val completedFilePaths: List<String> = emptyList(),
    val topology: TransferTopology = TransferTopology.SIMPLE,
    val swarmPeers: List<SwarmPeerInfo> = emptyList(),
    val completedChunksCount: Int = 0,
    val totalChunksCount: Int = 0,
    val isSwarmTransitioning: Boolean = false
) {
    val progressFraction: Float
        get() = if (totalBytes > 0) (transferredBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val progressPercent: Int
        get() = (progressFraction * 100).toInt()

    val batchProgressFraction: Float
        get() {
            if (batchTotalBytes > 0) {
                return (batchTransferredBytes.toFloat() / batchTotalBytes.toFloat()).coerceIn(0f, 1f)
            }
            if (batchTotalCount > 0) {
                return ((batchCurrentIndex - 1).toFloat() / batchTotalCount.toFloat()).coerceIn(0f, 1f)
            }
            return 0f
        }

    val formattedSpeed: String
        get() {
            val speedMb = speedBytesPerSec.toDouble() / (1024.0 * 1024.0)
            return if (speedMb >= 1.0) {
                String.format("%.1f MB/s", speedMb)
            } else {
                val speedKb = speedBytesPerSec.toDouble() / 1024.0
                String.format("%.0f KB/s", speedKb)
            }
        }

    val formattedTransferred: String
        get() = "${formatBytes(transferredBytes)} / ${formatBytes(totalBytes)}"

    val isBatchTransfer: Boolean
        get() = batchTotalCount > 1
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        kb >= 1.0 -> String.format("%.1f KB", kb)
        else -> "$bytes B"
    }
}

data class WebSharedFile(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val isApk: Boolean = false,
    val uri: android.net.Uri? = null,
    val localFilePath: String? = null,
    val mimeType: String = "*/*"
) {
    val formattedSize: String
        get() = formatBytes(sizeBytes)
}
