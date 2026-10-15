package com.example.helper

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.model.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AppExtractorHelper {

    suspend fun getInstalledApps(context: Context): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val appsList = mutableListOf<InstalledAppInfo>()

        try {
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

            for (appInfo in installedApps) {
                // Filter out non-launchable background system processes
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val launchIntent = packageManager.getLaunchIntentForPackage(appInfo.packageName)

                // Include all user-installed apps and any launchable app, skip system services
                if (!isSystem || isUpdatedSystem || launchIntent != null) {
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    val apkFile = File(appInfo.sourceDir)
                    val size = if (apkFile.exists()) apkFile.length() else 0L

                    val versionName = try {
                        val pkgInfo = packageManager.getPackageInfo(appInfo.packageName, 0)
                        pkgInfo.versionName ?: "1.0"
                    } catch (_: Exception) {
                        "1.0"
                    }

                    val icon = try {
                        packageManager.getApplicationIcon(appInfo)
                    } catch (_: Exception) {
                        null
                    }

                    val iconBitmap = icon?.let { drawableToImageBitmap(it) }

                    appsList.add(
                        InstalledAppInfo(
                            appName = appName,
                            packageName = appInfo.packageName,
                            versionName = versionName,
                            apkPath = appInfo.sourceDir,
                            sizeBytes = size,
                            iconBitmap = iconBitmap,
                            rawIcon = icon
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Sort alphabetically by app name
        appsList.sortedBy { it.appName.lowercase() }
    }

    private fun drawableToImageBitmap(drawable: Drawable): ImageBitmap? {
        return try {
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                return drawable.bitmap.asImageBitmap()
            }
            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    fun getCleanApkFileName(app: InstalledAppInfo): String {
        val sanitized = app.appName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return "${sanitized}_v${app.versionName}.apk"
    }
}
