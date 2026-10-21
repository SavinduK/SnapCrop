package com.example

import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateDarkBg
import com.example.ui.theme.SlateSurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Entry Card shown on the Main Screen.
 * Displays temporary screenshots count and navigates to the dedicated Saved Images screen.
 */
@Composable
fun SavedImagesNavCard(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_nav_saved_images"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        border = BorderStroke(1.2.dp, SlateBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x33FBBF24)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Collections,
                        contentDescription = null,
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = stringResource(R.string.section_saved_storage_title),
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.section_saved_storage_policy),
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Open",
                    color = CyanPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = "Open Saved Screenshots",
                    tint = CyanPrimary,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

/**
 * Dedicated Fullscreen Screen for Saved Images.
 * Includes all Share and Save buttons, single unified button row, individual quick actions,
 * and high-resolution preview modal.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavedImagesScreen(
    onBack: () -> Unit,
    refreshTrigger: Long = 0L,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<BatchCropManager.TemporaryScreenshotItem>>(emptyList()) }
    val selectedFiles = remember { mutableStateListOf<File>() }
    var previewFile by remember { mutableStateOf<File?>(null) }

    fun refreshList() {
        items = BatchCropManager.getTemporaryItems(context)
        selectedFiles.retainAll(items.map { it.file })
    }

    LaunchedEffect(refreshTrigger) {
        refreshList()
    }

    val isSelectionMode = selectedFiles.isNotEmpty()

    BackHandler {
        if (selectedFiles.isNotEmpty()) {
            selectedFiles.clear()
        } else {
            onBack()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("saved_images_screen"),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = SlateDarkBg,
        topBar = {
            Surface(
                color = SlateSurfaceCard,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(44.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    // Normal mode top bar
                    AnimatedVisibility(
                        visible = !isSelectionMode,
                        enter = fadeIn(animationSpec = tween(180)),
                        exit = fadeOut(animationSpec = tween(150))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                IconButton(
                                    onClick = onBack,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x28FFFFFF))
                                        .testTag("btn_back_saved_images")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back to Home",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Text(
                                    text = stringResource(R.string.section_saved_storage_title),
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            IconButton(
                                onClick = { refreshList() },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x18FFFFFF))
                                    .testTag("btn_refresh_saved_images")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }

                    // Selection mode top bar
                    AnimatedVisibility(
                        visible = isSelectionMode,
                        enter = fadeIn(animationSpec = tween(180)),
                        exit = fadeOut(animationSpec = tween(150))
                    ) {
                        val allSelected = items.isNotEmpty() && selectedFiles.size == items.size
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Top left: a rounded dark translucent pill containing a circular select-all checkbox and the selected count
                            Surface(
                                shape = RoundedCornerShape(percent = 50),
                                color = Color(0xDD1E293B),
                                border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                                modifier = Modifier.testTag("pill_selection_count")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(if (allSelected) CyanPrimary else Color(0x33FFFFFF))
                                            .border(
                                                width = 1.5.dp,
                                                color = if (allSelected) CyanPrimary else Color.White,
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                if (allSelected) {
                                                    selectedFiles.clear()
                                                } else {
                                                    selectedFiles.clear()
                                                    selectedFiles.addAll(items.map { it.file })
                                                }
                                            }
                                            .testTag("btn_select_all_saved"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (allSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Select All",
                                                tint = Color(0xFF090D16),
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = "${selectedFiles.size}",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.testTag("text_selected_count")
                                    )
                                }
                            }

                            // Top right: a rounded dark translucent "Cancel" pill that exits selection mode and clears the selection
                            Surface(
                                onClick = { selectedFiles.clear() },
                                shape = RoundedCornerShape(percent = 50),
                                color = Color(0xDD1E293B),
                                border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                                modifier = Modifier.testTag("btn_cancel_selection")
                            ) {
                                Text(
                                    text = "Cancel",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0x22FBBF24)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Collections,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.saved_storage_empty_title),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.saved_storage_empty_desc),
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                            shape = RoundedCornerShape(percent = 50)
                        ) {
                            Text("Back to Home", color = Color(0xFF090D16), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Grid of items
                val rows = items.chunked(2)
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 12.dp,
                        end = 16.dp,
                        bottom = if (isSelectionMode) 100.dp else 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(rows) { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            for (item in rowItems) {
                                val isSelected = selectedFiles.contains(item.file)
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .testTag("saved_card_${item.file.name}"),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) CyanPrimary else Color(0x33FFFFFF)
                                    )
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        // Image thumbnail with selection toggle & expiration badge
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(1.15f)
                                                .background(Color(0xFF0F172A))
                                                .combinedClickable(
                                                    onClick = {
                                                        if (isSelectionMode) {
                                                            if (isSelected) {
                                                                selectedFiles.remove(item.file)
                                                            } else {
                                                                selectedFiles.add(item.file)
                                                            }
                                                        } else {
                                                            previewFile = item.file
                                                        }
                                                    },
                                                    onLongClick = {
                                                        if (!isSelectionMode) {
                                                            selectedFiles.add(item.file)
                                                        } else {
                                                            if (isSelected) {
                                                                selectedFiles.remove(item.file)
                                                            } else {
                                                                selectedFiles.add(item.file)
                                                            }
                                                        }
                                                    }
                                                )
                                        ) {
                                            val bitmap = remember(item.file.lastModified()) {
                                                try {
                                                    BitmapFactory.decodeFile(item.file.absolutePath)
                                                } catch (e: Exception) {
                                                    null
                                                }
                                            }

                                            if (bitmap != null) {
                                                Image(
                                                    bitmap = bitmap.asImageBitmap(),
                                                    contentDescription = "Saved screenshot",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }

                                            // Selection circle: visible ONLY in selection mode
                                            if (isSelectionMode) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(6.dp)
                                                        .clickable {
                                                            if (isSelected) {
                                                                selectedFiles.remove(item.file)
                                                            } else {
                                                                selectedFiles.add(item.file)
                                                            }
                                                        }
                                                        .testTag("checkbox_select_${item.file.name}")
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(24.dp)
                                                            .clip(CircleShape)
                                                            .background(if (isSelected) CyanPrimary else Color(0x99000000))
                                                            .border(
                                                                width = 1.5.dp,
                                                                color = if (isSelected) CyanPrimary else Color.White,
                                                                shape = CircleShape
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (isSelected) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = "Selected",
                                                                tint = Color(0xFF090D16),
                                                                modifier = Modifier.size(15.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            // Remaining Expiration Time Badge at Bottom
                                            val remainingHours = TimeUnit.MILLISECONDS.toHours(item.remainingTimeMs)
                                            val remainingMins = TimeUnit.MILLISECONDS.toMinutes(item.remainingTimeMs) % 60
                                            val timeLabel = when {
                                                remainingHours > 0 -> "${remainingHours}h ${remainingMins}m left"
                                                remainingMins > 0 -> "${remainingMins}m left"
                                                else -> "Expiring soon"
                                            }

                                            Surface(
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .fillMaxWidth(),
                                                color = Color(0xD9090D16)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.AccessTime,
                                                        contentDescription = null,
                                                        tint = Color(0xFFFBBF24),
                                                        modifier = Modifier.size(11.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = timeLabel,
                                                        color = Color(0xFFFDE68A),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }

                                        // Individual Quick Actions Bar directly on each card: Share, Save, Delete
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF172033))
                                                .padding(horizontal = 6.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            // Quick Share
                                            IconButton(
                                                onClick = {
                                                    val chooser = BatchCropManager.createShareIntentForFiles(context, listOf(item.file))
                                                    if (chooser != null) {
                                                        context.startActivity(chooser)
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Share,
                                                    contentDescription = "Share",
                                                    tint = Color(0xFF38BDF8),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            // Quick Save
                                            IconButton(
                                                onClick = {
                                                    val count = BatchCropManager.saveMultipleFilesToGallery(context, listOf(item.file))
                                                    if (count > 0) {
                                                        Toast.makeText(context, "Saved to Device Gallery", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Download,
                                                    contentDescription = "Save",
                                                    tint = Color(0xFF34D399),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            // Quick Delete
                                            IconButton(
                                                onClick = {
                                                    BatchCropManager.deleteBatchFiles(context, listOf(item.file))
                                                    refreshList()
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "Delete",
                                                    tint = Color(0xFFF87171),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // Floating rounded pill-shaped bar, centered and inset from screen edges
                AnimatedVisibility(
                    visible = isSelectionMode,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(durationMillis = 200)),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(durationMillis = 180)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = Color(0xF21E293B),
                        shadowElevation = 10.dp,
                        tonalElevation = 6.dp,
                        border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 420.dp)
                            .testTag("floating_selection_bar")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // 1. Share
                            FloatingBarActionItem(
                                icon = Icons.Default.Share,
                                label = "Share",
                                testTag = "btn_batch_share",
                                onClick = {
                                    val targetFiles = selectedFiles.toList()
                                    val chooser = BatchCropManager.createShareIntentForFiles(context, targetFiles)
                                    if (chooser != null) {
                                        context.startActivity(chooser)
                                    } else {
                                        Toast.makeText(context, "Failed to share items", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )

                            // 2. Save
                            FloatingBarActionItem(
                                icon = Icons.Default.Download,
                                label = "Save",
                                testTag = "btn_batch_save_device",
                                onClick = {
                                    val targetFiles = selectedFiles.toList()
                                    val count = BatchCropManager.saveMultipleFilesToGallery(context, targetFiles)
                                    Toast.makeText(context, "Saved $count screenshots to Gallery", Toast.LENGTH_SHORT).show()
                                    selectedFiles.clear()
                                }
                            )

                            // 3. Delete
                            FloatingBarActionItem(
                                icon = Icons.Default.DeleteOutline,
                                label = "Delete",
                                testTag = "btn_batch_delete",
                                tint = Color(0xFFF87171),
                                onClick = {
                                    val targetFiles = selectedFiles.toList()
                                    val deletedCount = BatchCropManager.deleteBatchFiles(context, targetFiles)
                                    Toast.makeText(context, "Removed $deletedCount item(s)", Toast.LENGTH_SHORT).show()
                                    selectedFiles.clear()
                                    refreshList()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // High resolution preview modal with Share, Save, and Close buttons cleanly separated
    if (previewFile != null) {
        SavedImagePreviewDialog(
            file = previewFile!!,
            onDismiss = { previewFile = null },
            onDeleted = {
                previewFile = null
                refreshList()
            }
        )
    }
}

@Composable
private fun FloatingBarActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    testTag: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * High-resolution preview dialog with distinct, non-overlapping Share, Save, and Close action buttons.
 */
@Composable
fun SavedImagePreviewDialog(
    file: File,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val previewBmp = remember(file) {
        try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            null
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F172A),
            border = BorderStroke(1.dp, Color(0x33FFFFFF))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top row with title and action buttons cleanly spaced
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Screenshot Preview",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Share Button
                        IconButton(
                            onClick = {
                                val chooser = BatchCropManager.createShareIntentForFiles(context, listOf(file))
                                if (chooser != null) {
                                    context.startActivity(chooser)
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0x330284C7))
                                .testTag("btn_preview_share")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // 2. Save Button
                        IconButton(
                            onClick = {
                                val count = BatchCropManager.saveMultipleFilesToGallery(context, listOf(file))
                                if (count > 0) {
                                    Toast.makeText(context, "Saved screenshot to Device Gallery", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0x3310B981))
                                .testTag("btn_preview_save_device")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Save",
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // 3. Close Button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0x28FFFFFF))
                                .testTag("btn_preview_close")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (previewBmp != null) {
                    Image(
                        bitmap = previewBmp.asImageBitmap(),
                        contentDescription = "Preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                }

                // Delete action button at the bottom of preview
                Button(
                    onClick = {
                        BatchCropManager.deleteBatchFiles(context, listOf(file))
                        Toast.makeText(context, "Screenshot deleted", Toast.LENGTH_SHORT).show()
                        onDeleted()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33EF4444)),
                    shape = RoundedCornerShape(percent = 50),
                    modifier = Modifier.fillMaxWidth().height(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = Color(0xFFF87171),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete Screenshot", color = Color(0xFFF87171), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Inline SavedStorageSection kept for backward compatibility.
 * Updated to use the single button row with "Save" and no count text.
 */
@Composable
fun SavedStorageSection(
    modifier: Modifier = Modifier,
    refreshTrigger: Long = 0L
) {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<BatchCropManager.TemporaryScreenshotItem>>(emptyList()) }
    val selectedFiles = remember { mutableStateListOf<File>() }
    var previewFile by remember { mutableStateOf<File?>(null) }

    fun refreshList() {
        items = BatchCropManager.getTemporaryItems(context)
        selectedFiles.retainAll(items.map { it.file })
    }

    LaunchedEffect(refreshTrigger) {
        refreshList()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("section_saved_storage"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurfaceCard),
        border = BorderStroke(1.2.dp, SlateBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row: Section Title & 24h Policy Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x33FBBF24)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Collections,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.section_saved_storage_title),
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (items.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(percent = 50),
                                    color = Color(0x33FBBF24)
                                ) {
                                    Text(
                                        text = "${items.size}",
                                        color = Color(0xFFFDE68A),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = stringResource(R.string.section_saved_storage_policy),
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Empty State
            if (items.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0x18FFFFFF),
                    border = BorderStroke(1.dp, Color(0x22FFFFFF))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = stringResource(R.string.saved_storage_empty_title),
                            color = TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.saved_storage_empty_desc),
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                // Single Button Row without selection count
                val allSelected = selectedFiles.isNotEmpty() && selectedFiles.size == items.size
                val targetFiles = if (selectedFiles.isNotEmpty()) selectedFiles.toList() else items.map { it.file }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 1. Select / Deselect All
                        Surface(
                            onClick = {
                                if (allSelected) {
                                    selectedFiles.clear()
                                } else {
                                    selectedFiles.clear()
                                    selectedFiles.addAll(items.map { it.file })
                                }
                            },
                            shape = RoundedCornerShape(percent = 50),
                            color = if (allSelected) Color(0x3300E5FF) else Color(0x22FFFFFF),
                            border = BorderStroke(1.dp, if (allSelected) CyanPrimary else Color(0x33FFFFFF)),
                            modifier = Modifier.height(36.dp).testTag("btn_select_all_saved")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (allSelected) Icons.Default.Check else Icons.Default.Collections,
                                    contentDescription = null,
                                    tint = if (allSelected) CyanPrimary else Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (allSelected) "Deselect" else "Select All",
                                    color = if (allSelected) CyanPrimary else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // 2. Share
                        Button(
                            onClick = {
                                val chooser = BatchCropManager.createShareIntentForFiles(context, targetFiles)
                                if (chooser != null) {
                                    context.startActivity(chooser)
                                } else {
                                    Toast.makeText(context, "Failed to share selected items", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(percent = 50),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .testTag("btn_batch_share"),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Share",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // 3. Save (renamed from "Save to Device" to "Save")
                        Button(
                            onClick = {
                                val count = BatchCropManager.saveMultipleFilesToGallery(context, targetFiles)
                                Toast.makeText(context, "Saved $count screenshots to Gallery", Toast.LENGTH_SHORT).show()
                                selectedFiles.clear()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(percent = 50),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .testTag("btn_batch_save_device"),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Save",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // 4. Batch Delete
                        IconButton(
                            onClick = {
                                val deletedCount = BatchCropManager.deleteBatchFiles(context, targetFiles)
                                Toast.makeText(context, "Removed $deletedCount item(s)", Toast.LENGTH_SHORT).show()
                                selectedFiles.clear()
                                refreshList()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0x33EF4444))
                                .testTag("btn_batch_delete")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete",
                                tint = Color(0xFFF87171),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }

                // Grid of Temporary Screenshots (2 items per row)
                val rows = items.chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (rowItems in rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            for (item in rowItems) {
                                val isSelected = selectedFiles.contains(item.file)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF1E293B))
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) CyanPrimary else Color(0x33FFFFFF),
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable {
                                            if (selectedFiles.isNotEmpty()) {
                                                if (isSelected) {
                                                    selectedFiles.remove(item.file)
                                                } else {
                                                    selectedFiles.add(item.file)
                                                }
                                            } else {
                                                previewFile = item.file
                                            }
                                        }
                                        .testTag("saved_item_${item.file.name}")
                                ) {
                                    val bitmap = remember(item.file.lastModified()) {
                                        try {
                                            BitmapFactory.decodeFile(item.file.absolutePath)
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }

                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "Saved screenshot",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .clickable {
                                                if (isSelected) {
                                                    selectedFiles.remove(item.file)
                                                } else {
                                                    selectedFiles.add(item.file)
                                                }
                                            }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) CyanPrimary else Color(0x99000000))
                                                .border(1.dp, if (isSelected) CyanPrimary else Color.White, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = Color(0xFF090D16),
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                    }

                                    val remainingHours = TimeUnit.MILLISECONDS.toHours(item.remainingTimeMs)
                                    val remainingMins = TimeUnit.MILLISECONDS.toMinutes(item.remainingTimeMs) % 60
                                    val timeLabel = when {
                                        remainingHours > 0 -> "${remainingHours}h ${remainingMins}m left"
                                        remainingMins > 0 -> "${remainingMins}m left"
                                        else -> "Expiring soon"
                                    }

                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth(),
                                        color = Color(0xCC090D16)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AccessTime,
                                                contentDescription = null,
                                                tint = Color(0xFFFBBF24),
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = timeLabel,
                                                color = Color(0xFFFDE68A),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    if (previewFile != null) {
        SavedImagePreviewDialog(
            file = previewFile!!,
            onDismiss = { previewFile = null },
            onDeleted = {
                previewFile = null
                refreshList()
            }
        )
    }
}
