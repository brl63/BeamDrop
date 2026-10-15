package com.example.helper

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.FileExplorerItem
import com.example.model.MediaCategory
import com.example.model.SharedItem
import com.example.ui.theme.CyanPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ThumbnailHelper {
    // 32MB cache for video and custom thumbnails
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8
    private val thumbnailCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun getCached(uri: Uri?, localPath: String?): Bitmap? {
        val cacheKey = uri?.toString() ?: localPath ?: return null
        return thumbnailCache.get(cacheKey)
    }

    suspend fun loadVideoThumbnail(context: Context, uri: Uri?, localPath: String?): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = uri?.toString() ?: localPath ?: return@withContext null
        thumbnailCache.get(cacheKey)?.let { return@withContext it }

        var bitmap: Bitmap? = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri != null) {
                bitmap = context.contentResolver.loadThumbnail(uri, Size(128, 128), null)
            }
        } catch (_: Exception) { }

        if (bitmap == null && !localPath.isNullOrEmpty()) {
            try {
                @Suppress("DEPRECATION")
                bitmap = ThumbnailUtils.createVideoThumbnail(
                    localPath,
                    MediaStore.Images.Thumbnails.MINI_KIND
                )
            } catch (_: Exception) { }
        }

        if (bitmap == null) {
            try {
                val retriever = MediaMetadataRetriever()
                if (uri != null) {
                    retriever.setDataSource(context, uri)
                } else if (localPath != null) {
                    retriever.setDataSource(localPath)
                }
                bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 128, 128)
                } else {
                    retriever.frameAtTime
                }
                retriever.release()
            } catch (_: Exception) { }
        }

        if (bitmap != null) {
            thumbnailCache.put(cacheKey, bitmap)
        }
        bitmap
    }
}

@Composable
fun ItemThumbnailView(
    category: MediaCategory,
    displayName: String,
    uri: Uri?,
    localPath: String?,
    isDirectory: Boolean = false,
    itemCount: Int = 0,
    modifier: Modifier = Modifier.size(52.dp)
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        when {
            isDirectory -> {
                // Classic ZArchiver folder icon with folder accent
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Folder",
                        tint = Color(0xFFFFB300), // Classic folder amber/gold
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            category == MediaCategory.IMAGES -> {
                // Optimized AsyncImage with Coil: downsampled to 140x140 to prevent OOM & frame drops
                val imageModel = uri ?: (if (!localPath.isNullOrEmpty()) File(localPath) else null)
                val imageRequest = remember(imageModel) {
                    ImageRequest.Builder(context)
                        .data(imageModel)
                        .size(140, 140)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            category == MediaCategory.VIDEOS -> {
                // Fast video thumbnail extraction with instant in-memory cache check
                val cached = remember(uri, localPath) { ThumbnailHelper.getCached(uri, localPath) }
                val videoThumbnail = remember(uri, localPath) { mutableStateOf<Bitmap?>(cached) }

                if (cached == null) {
                    LaunchedEffect(uri, localPath) {
                        videoThumbnail.value = ThumbnailHelper.loadVideoThumbnail(context, uri, localPath)
                    }
                }

                val bmp = videoThumbnail.value
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Play icon overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = "Video",
                        tint = CyanPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            displayName.endsWith(".apk", ignoreCase = true) -> {
                Icon(
                    imageVector = Icons.Default.Android,
                    contentDescription = "APK",
                    tint = Color(0xFF3DDC84), // Android green
                    modifier = Modifier.size(28.dp)
                )
            }

            displayName.endsWith(".zip", ignoreCase = true) ||
            displayName.endsWith(".rar", ignoreCase = true) ||
            displayName.endsWith(".7z", ignoreCase = true) ||
            displayName.endsWith(".tar", ignoreCase = true) ||
            displayName.endsWith(".gz", ignoreCase = true) -> {
                Icon(
                    imageVector = Icons.Default.FolderZip,
                    contentDescription = "Archive",
                    tint = Color(0xFFFF9800),
                    modifier = Modifier.size(28.dp)
                )
            }

            category == MediaCategory.AUDIO -> {
                Icon(
                    imageVector = Icons.Default.AudioFile,
                    contentDescription = "Audio",
                    tint = Color(0xFFB388FF),
                    modifier = Modifier.size(26.dp)
                )
            }

            else -> {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = "Document",
                    tint = CyanPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}
