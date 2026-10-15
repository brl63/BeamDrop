package com.example.model

import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class MediaCategory(val label: String) {
    ALL("All"),
    IMAGES("Images"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    DOCUMENTS("Documents")
}

enum class FileSortOption(val label: String) {
    DATE_DESC("Date (Newest)"),
    DATE_ASC("Date (Oldest)"),
    NAME_ASC("Name (A - Z)"),
    NAME_DESC("Name (Z - A)"),
    SIZE_DESC("Size (Largest)"),
    SIZE_ASC("Size (Smallest)")
}

enum class DateFilterOption(val label: String) {
    ALL("All Dates"),
    TODAY("Today"),
    LAST_7_DAYS("Last 7 Days"),
    LAST_30_DAYS("Last 30 Days")
}

data class FileExplorerItem(
    val id: String,
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val dateModified: Long, // in millis
    val itemCount: Int = 0,
    val mimeType: String = "*/*",
    val uri: Uri? = null,
    val category: MediaCategory = MediaCategory.DOCUMENTS
) {
    val formattedSize: String get() = if (isDirectory) "$itemCount items" else formatBytes(sizeBytes)
    val formattedDate: String get() = formatTimestamp(dateModified)
    val isApk: Boolean get() = mimeType == "application/vnd.android.package-archive" || name.endsWith(".apk", ignoreCase = true)
    val isZipOrArchive: Boolean get() = name.endsWith(".zip", ignoreCase = true) ||
            name.endsWith(".rar", ignoreCase = true) ||
            name.endsWith(".7z", ignoreCase = true) ||
            name.endsWith(".tar", ignoreCase = true) ||
            name.endsWith(".gz", ignoreCase = true)

    fun toSharedItem(): SharedItem.MediaFile {
        return SharedItem.MediaFile(
            id = id,
            displayName = name,
            uri = uri ?: Uri.fromFile(java.io.File(path)),
            mimeType = mimeType,
            category = category,
            dateModified = if (dateModified > 1_000_000_000_000L) dateModified / 1000L else dateModified,
            sizeBytes = sizeBytes,
            localPath = path
        )
    }
}

sealed class SharedItem {
    abstract val id: String
    abstract val displayName: String
    abstract val sizeBytes: Long
    abstract val isApk: Boolean

    val formattedSize: String
        get() = formatBytes(sizeBytes)

    data class MediaFile(
        override val id: String,
        override val displayName: String,
        val uri: Uri,
        val mimeType: String,
        val category: MediaCategory,
        val dateModified: Long, // seconds or millis
        override val sizeBytes: Long,
        val localPath: String? = null
    ) : SharedItem() {
        override val isApk: Boolean =
            mimeType == "application/vnd.android.package-archive" ||
                    displayName.endsWith(".apk", ignoreCase = true)

        val formattedDate: String
            get() = formatTimestamp(if (dateModified < 100_000_000_000L) dateModified * 1000L else dateModified)
    }

    data class AppFile(
        val appInfo: InstalledAppInfo
    ) : SharedItem() {
        override val id: String = appInfo.packageName
        override val displayName: String = appInfo.appName
        override val sizeBytes: Long = appInfo.sizeBytes
        override val isApk: Boolean = true
    }
}

fun formatTimestamp(millis: Long): String {
    if (millis <= 0) return ""
    val adjustedMillis = if (millis < 100_000_000_000L) millis * 1000L else millis
    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    return sdf.format(Date(adjustedMillis))
}

fun getDateGroupLabel(timestamp: Long): String {
    if (timestamp <= 0) return "Older"
    val millis = if (timestamp < 100_000_000_000L) timestamp * 1000L else timestamp

    val now = Calendar.getInstance()
    val itemCal = Calendar.getInstance().apply { timeInMillis = millis }

    // Start of today
    val startOfToday = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    // Start of yesterday
    val startOfYesterday = (startOfToday.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, -1)
    }

    // 7 days ago
    val startOf7DaysAgo = (startOfToday.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, -7)
    }

    // 30 days ago
    val startOf30DaysAgo = (startOfToday.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, -30)
    }

    return when {
        itemCal.after(startOfToday) -> "Today"
        itemCal.after(startOfYesterday) -> "Yesterday"
        itemCal.after(startOf7DaysAgo) -> "Last 7 Days"
        itemCal.after(startOf30DaysAgo) -> "Last 30 Days"
        itemCal.get(Calendar.YEAR) == now.get(Calendar.YEAR) -> {
            SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(millis))
        }
        else -> {
            SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(millis))
        }
    }
}
