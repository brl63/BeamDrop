package com.example.ui.selection

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.helper.ItemThumbnailView
import com.example.helper.MediaStoreHelper
import com.example.model.DateFilterOption
import com.example.model.FileSortOption
import com.example.model.MediaCategory
import com.example.model.SharedItem
import com.example.model.getDateGroupLabel
import com.example.ui.theme.CyanPrimary
import java.util.Calendar

@Composable
fun FilesTab(
    mediaFiles: List<SharedItem.MediaFile>,
    selectedCategory: MediaCategory,
    isLoading: Boolean,
    hasPermissions: Boolean,
    selectedItems: Set<SharedItem>,
    searchQuery: String,
    sortOption: FileSortOption,
    dateFilterOption: DateFilterOption,
    isGridView: Boolean,
    onCategorySelected: (MediaCategory) -> Unit,
    onToggleSelection: (SharedItem) -> Unit,
    onSetSortOption: (FileSortOption) -> Unit,
    onSetDateFilterOption: (DateFilterOption) -> Unit,
    onToggleViewLayout: () -> Unit,
    onPermissionsResult: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        onPermissionsResult(allGranted)
    }

    var showSortMenu by remember { mutableStateOf(false) }

    val filteredFiles = remember(mediaFiles, searchQuery, dateFilterOption) {
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val sevenDaysAgo = startOfToday - (7L * 24 * 60 * 60 * 1000)
        val thirtyDaysAgo = startOfToday - (30L * 24 * 60 * 60 * 1000)

        var list = mediaFiles
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter { it.displayName.lowercase().contains(q) }
        }

        when (dateFilterOption) {
            DateFilterOption.ALL -> list
            DateFilterOption.TODAY -> list.filter {
                val ms = if (it.dateModified < 100_000_000_000L) it.dateModified * 1000L else it.dateModified
                ms >= startOfToday
            }
            DateFilterOption.LAST_7_DAYS -> list.filter {
                val ms = if (it.dateModified < 100_000_000_000L) it.dateModified * 1000L else it.dateModified
                ms >= sevenDaysAgo
            }
            DateFilterOption.LAST_30_DAYS -> list.filter {
                val ms = if (it.dateModified < 100_000_000_000L) it.dateModified * 1000L else it.dateModified
                ms >= thirtyDaysAgo
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Horizontal Category Filter Chips (All, Images, Videos, Audio, Documents)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MediaCategory.values().forEach { category ->
                val isSelected = selectedCategory == category
                FilterChip(
                    selected = isSelected,
                    onClick = { onCategorySelected(category) },
                    label = {
                        Text(
                            text = category.label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("filter_chip_${category.name.lowercase()}")
                )
            }
        }

        // Sorting & Date Filter Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Sort Dropdown Button
            Box {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.clickable { showSortMenu = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "Sort",
                            tint = CyanPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = sortOption.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    FileSortOption.values().forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.label,
                                    fontWeight = if (option == sortOption) FontWeight.Bold else FontWeight.Normal,
                                    color = if (option == sortOption) CyanPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                onSetSortOption(option)
                                showSortMenu = false
                            }
                        )
                    }
                }
            }

            // Date Quick Filter Chips
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DateFilterOption.values().forEach { filter ->
                    val isSelected = filter == dateFilterOption
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSetDateFilterOption(filter) },
                        label = {
                            Text(
                                text = filter.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            // Grid / List View Toggle
            IconButton(
                onClick = onToggleViewLayout,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                    contentDescription = "Toggle view",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Inline Permission Request Banner (if permission is not granted)
        if (!hasPermissions) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("permission_banner")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Storage access is needed to show photos and videos.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { permissionLauncher.launch(MediaStoreHelper.getRequiredPermissions()) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("grant_permission_button")
                    ) {
                        Text("Allow", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Content
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CyanPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Loading ${selectedCategory.label}…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (filteredFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotEmpty()) "No files match \"$searchQuery\""
                    else if (!hasPermissions) "Grant storage access above to view files"
                    else "No ${selectedCategory.label.lowercase()} found on this device",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (isGridView) {
            // Grid Layout (Gallery style with big thumbnails)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredFiles, key = { it.id }) { file ->
                    val isSelected = selectedItems.any { it.id == file.id }
                    MediaGridItem(
                        file = file,
                        isSelected = isSelected,
                        onToggle = { onToggleSelection(file) }
                    )
                }
            }
        } else {
            // Detailed List Layout with real thumbnails and date info
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredFiles, key = { it.id }) { file ->
                    val isSelected = selectedItems.any { it.id == file.id }
                    MediaFileRow(
                        file = file,
                        isSelected = isSelected,
                        onToggle = { onToggleSelection(file) }
                    )
                }
            }
        }
    }
}

@Composable
fun MediaFileRow(
    file: SharedItem.MediaFile,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .testTag("file_row_${file.id}"),
        color = if (isSelected) CyanPrimary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) CyanPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Real photo/video thumbnail!
            ItemThumbnailView(
                category = file.category,
                displayName = file.displayName,
                uri = file.uri,
                localPath = file.localPath,
                modifier = Modifier.size(46.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = file.formattedSize,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = CyanPrimary
                    )
                    Text(
                        text = " • ${file.formattedDate}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Checkbox
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Selected" else "Unselected",
                tint = if (isSelected) CyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun MediaGridItem(
    file: SharedItem.MediaFile,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .testTag("media_grid_item_${file.id}"),
        color = if (isSelected) CyanPrimary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) CyanPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                ItemThumbnailView(
                    category = file.category,
                    displayName = file.displayName,
                    uri = file.uri,
                    localPath = file.localPath,
                    modifier = Modifier.fillMaxSize()
                )

                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) CyanPrimary else Color.White.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = file.displayName,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = file.formattedSize,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = CyanPrimary
            )
        }
    }
}
