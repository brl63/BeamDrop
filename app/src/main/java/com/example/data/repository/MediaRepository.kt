package com.example.data.repository

import android.content.Context
import com.example.helper.MediaStoreHelper
import com.example.model.DateFilterOption
import com.example.model.FileSortOption
import com.example.model.MediaCategory
import com.example.model.SharedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository responsible for querying, filtering, and sorting local media files
 * from Android MediaStore.
 */
class MediaRepository(private val context: Context) {

    fun hasStoragePermissions(): Boolean {
        return MediaStoreHelper.hasStoragePermissions(context)
    }

    suspend fun getMediaFiles(
        category: MediaCategory,
        sortOption: FileSortOption = FileSortOption.DATE_DESC
    ): List<SharedItem.MediaFile> = withContext(Dispatchers.IO) {
        val rawList = MediaStoreHelper.queryMediaFiles(context, category)
        sortMediaList(rawList, sortOption)
    }

    fun sortMediaList(
        files: List<SharedItem.MediaFile>,
        sort: FileSortOption
    ): List<SharedItem.MediaFile> {
        return when (sort) {
            FileSortOption.DATE_DESC -> files.sortedByDescending { it.dateModified }
            FileSortOption.DATE_ASC -> files.sortedBy { it.dateModified }
            FileSortOption.NAME_ASC -> files.sortedBy { it.displayName.lowercase() }
            FileSortOption.NAME_DESC -> files.sortedByDescending { it.displayName.lowercase() }
            FileSortOption.SIZE_DESC -> files.sortedByDescending { it.sizeBytes }
            FileSortOption.SIZE_ASC -> files.sortedBy { it.sizeBytes }
        }
    }
}
