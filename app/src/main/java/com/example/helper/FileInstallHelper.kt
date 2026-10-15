package com.example.helper

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object FileInstallHelper {

    fun installApk(context: Context, apkFilePath: String) {
        val file = File(apkFilePath)
        if (!file.exists()) {
            Toast.makeText(context, "APK file not found: $apkFilePath", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open package installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun openFile(context: Context, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(context, "File not found: $filePath", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val mimeType = context.contentResolver.getType(contentUri) ?: "*/*"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }

            context.startActivity(Intent.createChooser(intent, "Open with").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (e: Exception) {
            Toast.makeText(context, "No app available to open file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Shares the app's own base.apk file via Bluetooth, Quick Share, or other installed apps.
     */
    fun shareAppApk(context: Context) {
        try {
            val appInfo = context.applicationInfo
            val sourceApk = File(appInfo.sourceDir)
            if (!sourceApk.exists()) {
                Toast.makeText(context, "BeamDrop APK source not found", Toast.LENGTH_SHORT).show()
                return
            }

            val shareDir = File(context.cacheDir, "apk_share").apply { mkdirs() }
            val targetApk = File(shareDir, "BeamDrop.apk")

            if (!targetApk.exists() || targetApk.length() != sourceApk.length()) {
                sourceApk.copyTo(targetApk, overwrite = true)
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                targetApk
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, apkUri)
                putExtra(Intent.EXTRA_SUBJECT, "BeamDrop APK")
                putExtra(Intent.EXTRA_TEXT, "Install BeamDrop to transfer files & apps offline over local Wi-Fi / Hotspot!")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(intent, "Share BeamDrop APK (Bluetooth / Apps)").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to share APK: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
