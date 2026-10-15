package com.example.helper

import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import com.example.model.DateFilterOption
import com.example.model.FileExplorerItem
import com.example.model.FileSortOption
import com.example.model.MediaCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

data class Breadcrumb(
    val name: String,
    val fullPath: String,
    val isCurrent: Boolean
)

data class StorageShortcut(
    val name: String,
    val path: String,
    val iconType: String // "DOWNLOAD", "CAMERA", "PICTURES", "DOCS", "MUSIC", "MOVIES"
)

object FileExplorerHelper {

    fun getRootDirectory(): File {
        val ext = Environment.getExternalStorageDirectory()
        return if (ext != null && ext.exists()) ext else File("/storage/emulated/0")
    }

    fun getStorageShortcuts(): List<StorageShortcut> {
        val root = getRootDirectory().absolutePath
        val list = mutableListOf<StorageShortcut>()

        fun addIfEx(name: String, subPath: String, icon: String) {
            val f = File(root, subPath)
            list.add(StorageShortcut(name, f.absolutePath, icon))
        }

        addIfEx("Downloads", "Download", "DOWNLOAD")
        addIfEx("Camera / DCIM", "DCIM", "CAMERA")
        addIfEx("Pictures", "Pictures", "PICTURES")
        addIfEx("Documents", "Documents", "DOCS")
        addIfEx("Music", "Music", "MUSIC")
        addIfEx("Movies", "Movies", "MOVIES")

        return list
    }

    fun getBreadcrumbs(currentDir: File, rootDir: File): List<Breadcrumb> {
        val crumbs = mutableListOf<Breadcrumb>()
        var curr: File? = currentDir
        val rootPath = rootDir.canonicalPath

        while (curr != null) {
            val currCanonical = curr.canonicalPath
            val isRoot = currCanonical == rootPath || curr.parentFile == null
            val displayName = if (isRoot) "Internal Storage" else curr.name

            crumbs.add(0, Breadcrumb(
                name = displayName,
                fullPath = curr.absolutePath,
                isCurrent = currCanonical == currentDir.canonicalPath
            ))

            if (isRoot) break
            curr = curr.parentFile
        }

        if (crumbs.isEmpty()) {
            crumbs.add(Breadcrumb("Internal Storage", rootDir.absolutePath, true))
        }

        return crumbs
    }

    suspend fun listDirectory(
        dir: File,
        sortOption: FileSortOption = FileSortOption.DATE_DESC,
        dateFilter: DateFilterOption = DateFilterOption.ALL,
        searchQuery: String = ""
    ): List<FileExplorerItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<FileExplorerItem>()

        val rawFiles = try {
            dir.listFiles() ?: emptyArray()
        } catch (e: Exception) {
            emptyArray<File>()
        }

        val now = System.currentTimeMillis()
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val sevenDaysAgo = startOfToday - (7L * 24 * 60 * 60 * 1000)
        val thirtyDaysAgo = startOfToday - (30L * 24 * 60 * 60 * 1000)

        for (file in rawFiles) {
            // Ignore hidden files by default unless queried
            if (file.name.startsWith(".") && searchQuery.isBlank()) continue

            if (searchQuery.isNotBlank() && !file.name.contains(searchQuery, ignoreCase = true)) {
                continue
            }

            val lastModified = file.lastModified()

            // Date filtering for files (folders always stay visible so navigation works)
            if (!file.isDirectory) {
                when (dateFilter) {
                    DateFilterOption.ALL -> { /* no filter */ }
                    DateFilterOption.TODAY -> if (lastModified < startOfToday) continue
                    DateFilterOption.LAST_7_DAYS -> if (lastModified < sevenDaysAgo) continue
                    DateFilterOption.LAST_30_DAYS -> if (lastModified < thirtyDaysAgo) continue
                }
            }

            val isDir = file.isDirectory
            val size = if (isDir) 0L else file.length()
            val itemCount = if (isDir) {
                try {
                    file.list()?.size ?: 0
                } catch (_: Exception) {
                    0
                }
            } else 0

            val extension = file.extension.lowercase()
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                ?: when (extension) {
                    "apk" -> "application/vnd.android.package-archive"
                    "zip" -> "application/zip"
                    "rar" -> "application/x-rar-compressed"
                    "7z" -> "application/x-7z-compressed"
                    "pdf" -> "application/pdf"
                    else -> "*/*"
                }

            val category = when {
                extension in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "svg") -> MediaCategory.IMAGES
                extension in setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "wmv", "m4v") -> MediaCategory.VIDEOS
                extension in setOf("mp3", "wav", "aac", "m4a", "flac", "ogg", "opus", "wma") -> MediaCategory.AUDIO
                else -> MediaCategory.DOCUMENTS
            }

            items.add(
                FileExplorerItem(
                    id = "file_${file.absolutePath.hashCode()}",
                    name = file.name,
                    path = file.absolutePath,
                    isDirectory = isDir,
                    sizeBytes = size,
                    dateModified = lastModified,
                    itemCount = itemCount,
                    mimeType = mimeType,
                    uri = Uri.fromFile(file),
                    category = category
                )
            )
        }

        // Partition into folders and files (ZArchiver shows folders first, then files)
        val (directories, regularFiles) = items.partition { it.isDirectory }

        val sortedDirs = when (sortOption) {
            FileSortOption.DATE_DESC -> directories.sortedByDescending { it.dateModified }
            FileSortOption.DATE_ASC -> directories.sortedBy { it.dateModified }
            FileSortOption.NAME_DESC -> directories.sortedByDescending { it.name.lowercase() }
            FileSortOption.SIZE_DESC -> directories.sortedByDescending { it.itemCount }
            FileSortOption.SIZE_ASC -> directories.sortedBy { it.itemCount }
            else -> directories.sortedBy { it.name.lowercase() }
        }

        val sortedFiles = when (sortOption) {
            FileSortOption.DATE_DESC -> regularFiles.sortedByDescending { it.dateModified }
            FileSortOption.DATE_ASC -> regularFiles.sortedBy { it.dateModified }
            FileSortOption.NAME_ASC -> regularFiles.sortedBy { it.name.lowercase() }
            FileSortOption.NAME_DESC -> regularFiles.sortedByDescending { it.name.lowercase() }
            FileSortOption.SIZE_DESC -> regularFiles.sortedByDescending { it.sizeBytes }
            FileSortOption.SIZE_ASC -> regularFiles.sortedBy { it.sizeBytes }
        }

        sortedDirs + sortedFiles
    }
}
