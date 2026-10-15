package com.example.helper

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.example.model.MediaCategory
import com.example.model.SharedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaStoreHelper {

    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun hasStoragePermissions(context: Context): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    suspend fun queryMediaFiles(
        context: Context,
        category: MediaCategory = MediaCategory.ALL
    ): List<SharedItem.MediaFile> = withContext(Dispatchers.IO) {
        val items = mutableListOf<SharedItem.MediaFile>()

        try {
            when (category) {
                MediaCategory.ALL -> {
                    items.addAll(queryImages(context))
                    items.addAll(queryVideos(context))
                    items.addAll(queryAudio(context))
                    items.addAll(queryDocuments(context))
                }
                MediaCategory.IMAGES -> items.addAll(queryImages(context))
                MediaCategory.VIDEOS -> items.addAll(queryVideos(context))
                MediaCategory.AUDIO -> items.addAll(queryAudio(context))
                MediaCategory.DOCUMENTS -> items.addAll(queryDocuments(context))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        items.sortedByDescending { it.dateModified }
    }

    private fun queryImages(context: Context): List<SharedItem.MediaFile> {
        val list = mutableListOf<SharedItem.MediaFile>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_MODIFIED
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "Image_$id"
                val size = cursor.getLong(sizeColumn)
                val mime = cursor.getString(mimeColumn) ?: "image/*"
                val date = cursor.getLong(dateColumn)
                val contentUri: Uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                if (size > 0) {
                    list.add(
                        SharedItem.MediaFile(
                            id = "img_$id",
                            displayName = name,
                            uri = contentUri,
                            mimeType = mime,
                            category = MediaCategory.IMAGES,
                            dateModified = date,
                            sizeBytes = size
                        )
                    )
                }
            }
        }
        return list
    }

    private fun queryVideos(context: Context): List<SharedItem.MediaFile> {
        val list = mutableListOf<SharedItem.MediaFile>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_MODIFIED
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"

        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "Video_$id"
                val size = cursor.getLong(sizeColumn)
                val mime = cursor.getString(mimeColumn) ?: "video/*"
                val date = cursor.getLong(dateColumn)
                val contentUri: Uri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                if (size > 0) {
                    list.add(
                        SharedItem.MediaFile(
                            id = "vid_$id",
                            displayName = name,
                            uri = contentUri,
                            mimeType = mime,
                            category = MediaCategory.VIDEOS,
                            dateModified = date,
                            sizeBytes = size
                        )
                    )
                }
            }
        }
        return list
    }

    private fun queryAudio(context: Context): List<SharedItem.MediaFile> {
        val list = mutableListOf<SharedItem.MediaFile>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_MODIFIED
        )

        val sortOrder = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "Audio_$id"
                val size = cursor.getLong(sizeColumn)
                val mime = cursor.getString(mimeColumn) ?: "audio/*"
                val date = cursor.getLong(dateColumn)
                val contentUri: Uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                if (size > 0) {
                    list.add(
                        SharedItem.MediaFile(
                            id = "aud_$id",
                            displayName = name,
                            uri = contentUri,
                            mimeType = mime,
                            category = MediaCategory.AUDIO,
                            dateModified = date,
                            sizeBytes = size
                        )
                    )
                }
            }
        }
        return list
    }

    private fun queryDocuments(context: Context): List<SharedItem.MediaFile> {
        val list = mutableListOf<SharedItem.MediaFile>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )

        // Filter out photos, video, audio to focus on documents, archives, APKs, pdfs
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE} AND ${MediaStore.Files.FileColumns.SIZE} > 0"
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "Document_$id"
                    val size = cursor.getLong(sizeColumn)
                    val mime = cursor.getString(mimeColumn) ?: "application/octet-stream"
                    val date = cursor.getLong(dateColumn)
                    val contentUri: Uri = ContentUris.withAppendedId(
                        MediaStore.Files.getContentUri("external"),
                        id
                    )

                    if (size > 0) {
                        list.add(
                            SharedItem.MediaFile(
                                id = "doc_$id",
                                displayName = name,
                                uri = contentUri,
                                mimeType = mime,
                                category = MediaCategory.DOCUMENTS,
                                dateModified = date,
                                sizeBytes = size
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
