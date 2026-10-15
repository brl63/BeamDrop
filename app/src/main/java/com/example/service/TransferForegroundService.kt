package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.model.formatBytes

/**
 * Production-ready Foreground Service for background transfer survivability.
 *
 * Keeps CPU and high-performance Wi-Fi hardware active during transfers when the user
 * navigates away or locks the device, with a persistent progress notification.
 */
class TransferForegroundService : Service() {

    private var powerManager: PowerManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiManager: WifiManager? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var notificationManager: NotificationManager? = null

    private var lastNotifyTimestamp = 0L

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        createNotificationChannel()
        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START -> {
                val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "File Transfer"
                val isReceiver = intent.getBooleanExtra(EXTRA_IS_RECEIVER, false)
                val totalBytes = intent.getLongExtra(EXTRA_TOTAL_BYTES, 0L)
                val notification = buildNotification(
                    fileName = fileName,
                    transferredBytes = 0L,
                    totalBytes = totalBytes,
                    speedBps = 0L,
                    isReceiver = isReceiver
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
            ACTION_UPDATE -> {
                val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "File Transfer"
                val transferredBytes = intent.getLongExtra(EXTRA_TRANSFERRED_BYTES, 0L)
                val totalBytes = intent.getLongExtra(EXTRA_TOTAL_BYTES, 0L)
                val speedBps = intent.getLongExtra(EXTRA_SPEED_BPS, 0L)
                val isReceiver = intent.getBooleanExtra(EXTRA_IS_RECEIVER, false)

                val now = System.currentTimeMillis()
                // Throttle notification updates to at most once per 400ms to preserve system resources
                if (now - lastNotifyTimestamp >= 400 || (totalBytes > 0 && transferredBytes >= totalBytes)) {
                    lastNotifyTimestamp = now
                    val notification = buildNotification(
                        fileName = fileName,
                        transferredBytes = transferredBytes,
                        totalBytes = totalBytes,
                        speedBps = speedBps,
                        isReceiver = isReceiver
                    )
                    notificationManager?.notify(NOTIFICATION_ID, notification)
                }
            }
            ACTION_CANCEL -> {
                Log.d(TAG, "Cancel requested from notification action")
                onCancelRequested?.invoke()
                stopForegroundSafely()
            }
            ACTION_STOP -> {
                stopForegroundSafely()
            }
        }

        return START_NOT_STICKY
    }

    private fun acquireLocks() {
        try {
            powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "BeamDrop:TransferWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(4 * 60 * 60 * 1000L) // Safety max limit: 4 hours
            }
            Log.d(TAG, "Acquired PARTIAL_WAKE_LOCK")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire WakeLock: ${e.message}")
        }

        try {
            wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wifiManager?.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "BeamDrop:TransferWifiLock"
            )?.apply {
                setReferenceCounted(false)
                acquire()
            }
            Log.d(TAG, "Acquired WIFI_MODE_FULL_HIGH_PERF lock")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire WifiLock: ${e.message}")
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "Released WakeLock")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing WakeLock: ${e.message}")
        }
        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
                Log.d(TAG, "Released WifiLock")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing WifiLock: ${e.message}")
        }
        wakeLock = null
        wifiLock = null
    }

    private fun stopForegroundSafely() {
        releaseLocks()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        releaseLocks()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "File Transfers",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live progress for active local file transfers"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        fileName: String,
        transferredBytes: Long,
        totalBytes: Long,
        speedBps: Long,
        isReceiver: Boolean
    ): Notification {
        val title = if (isReceiver) "Receiving: $fileName" else "Sending: $fileName"
        val percent = if (totalBytes > 0) ((transferredBytes.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 100) else 0

        val speedText = if (speedBps > 0) "${formatBytes(speedBps)}/s" else "Connecting..."
        val transferredText = "${formatBytes(transferredBytes)} / ${formatBytes(totalBytes)}"
        val contentText = "$transferredText • $speedText ($percent%)"

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, TransferForegroundService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val smallIcon = if (isReceiver) {
            android.R.drawable.stat_sys_download
        } else {
            android.R.drawable.stat_sys_upload
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSubText("$percent%")
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, percent, totalBytes <= 0L)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .build()
    }

    companion object {
        const val TAG = "TransferService"
        const val CHANNEL_ID = "beamdrop_transfers_channel"
        const val NOTIFICATION_ID = 2048

        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_UPDATE = "com.example.service.ACTION_UPDATE"
        const val ACTION_CANCEL = "com.example.service.ACTION_CANCEL"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"

        const val EXTRA_FILE_NAME = "extra_file_name"
        const val EXTRA_IS_RECEIVER = "extra_is_receiver"
        const val EXTRA_TOTAL_BYTES = "extra_total_bytes"
        const val EXTRA_TRANSFERRED_BYTES = "extra_transferred_bytes"
        const val EXTRA_SPEED_BPS = "extra_speed_bps"

        var onCancelRequested: (() -> Unit)? = null

        fun startService(
            context: Context,
            fileName: String,
            isReceiver: Boolean,
            totalBytes: Long
        ) {
            try {
                val intent = Intent(context, TransferForegroundService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_FILE_NAME, fileName)
                    putExtra(EXTRA_IS_RECEIVER, isReceiver)
                    putExtra(EXTRA_TOTAL_BYTES, totalBytes)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cannot start foreground service: ${e.message}")
            }
        }

        fun updateProgress(
            context: Context,
            fileName: String,
            transferredBytes: Long,
            totalBytes: Long,
            speedBps: Long,
            isReceiver: Boolean
        ) {
            try {
                val intent = Intent(context, TransferForegroundService::class.java).apply {
                    action = ACTION_UPDATE
                    putExtra(EXTRA_FILE_NAME, fileName)
                    putExtra(EXTRA_TRANSFERRED_BYTES, transferredBytes)
                    putExtra(EXTRA_TOTAL_BYTES, totalBytes)
                    putExtra(EXTRA_SPEED_BPS, speedBps)
                    putExtra(EXTRA_IS_RECEIVER, isReceiver)
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Cannot update foreground service: ${e.message}")
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, TransferForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Cannot stop foreground service: ${e.message}")
            }
        }
    }
}
