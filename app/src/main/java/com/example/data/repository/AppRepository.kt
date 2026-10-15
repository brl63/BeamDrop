package com.example.data.repository

import android.content.Context
import com.example.helper.AppExtractorHelper
import com.example.model.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository responsible for querying installed applications and resolving APK metadata.
 */
class AppRepository(private val context: Context) {

    suspend fun getInstalledApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        AppExtractorHelper.getInstalledApps(context)
    }

    fun getCleanApkFileName(appInfo: InstalledAppInfo): String {
        return AppExtractorHelper.getCleanApkFileName(appInfo)
    }
}
