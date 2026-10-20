package com.example

import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateDarkBg
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Temporary App Storage Section
 *
 * Displays items saved to app temporary storage with a 24-hour retention policy.
 * Provides batch/multi-select actions to:
 * - Share selected items
 * - Save selected items to device gallery
 * - Delete items
 */
@OptIn(ExperimentalLayoutApi::class)
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
        // Clean selection of any removed files
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
                // Batch Selection Bar & Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (selectedFiles.isEmpty()) "Tap items to select" else "${selectedFiles.size} of ${items.size} selected",
                            color = if (selectedFiles.isNotEmpty()) Color(0xFF00E5FF) else TextMuted,
                            fontSize = 13.sp,
                            fontWeight = if (selectedFiles.isNotEmpty()) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = {
                                if (selectedFiles.size == items.size) {
                                    selectedFiles.clear()
                                } else {
                                    selectedFiles.clear()
                                    selectedFiles.addAll(items.map { it.file })
                                }
                            },
                            modifier = Modifier.testTag("btn_select_all_saved")
                        ) {
                            Text(
                                text = if (selectedFiles.size == items.size) stringResource(R.string.btn_deselect_all) else stringResource(R.string.btn_select_all),
                                color = CyanPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Batch Action Buttons: Share & Save to Device
                AnimatedVisibility(
                    visible = selectedFiles.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0x3300E5FF),
                        border = BorderStroke(1.dp, Color(0x6600E5FF))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. Share
                            Button(
                                onClick = {
                                    val chooser = BatchCropManager.createShareIntentForFiles(context, selectedFiles.toList())
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
                                    .height(38.dp)
                                    .testTag("btn_batch_share"),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = stringResource(R.string.btn_share_selected),
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // 2. Save
                            Button(
                                onClick = {
                                    val count = BatchCropManager.saveMultipleFilesToGallery(context, selectedFiles.toList())
                                    Toast.makeText(context, "Saved $count screenshots to Device Gallery", Toast.LENGTH_SHORT).show()
                                    selectedFiles.clear()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                shape = RoundedCornerShape(percent = 50),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("btn_batch_save_device"),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = stringResource(R.string.crop_btn_save),
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // 3. Batch Delete
                            IconButton(
                                onClick = {
                                    val deletedCount = BatchCropManager.deleteBatchFiles(context, selectedFiles.toList())
                                    Toast.makeText(context, "Removed $deletedCount item(s)", Toast.LENGTH_SHORT).show()
                                    selectedFiles.clear()
                                    refreshList()
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x33EF4444))
                                    .testTag("btn_batch_delete")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete",
                                    tint = Color(0xFFF87171),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // 4. Close / Deselect Selection
                            IconButton(
                                onClick = {
                                    selectedFiles.clear()
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x28FFFFFF))
                                    .testTag("btn_batch_close_selection")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear selection",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
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
                                    // Image Thumbnail
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

                                    // Checkbox Overlay Top End
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

    // Fullscreen image preview modal
    if (previewFile != null) {
        val previewBmp = remember(previewFile) {
            try {
                BitmapFactory.decodeFile(previewFile?.absolutePath)
            } catch (e: Exception) {
                null
            }
        }

        Dialog(onDismissRequest = { previewFile = null }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF0F172A)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
                            // Save to Device Button
                            IconButton(
                                onClick = {
                                    previewFile?.let { f ->
                                        val count = BatchCropManager.saveMultipleFilesToGallery(context, listOf(f))
                                        if (count > 0) {
                                            Toast.makeText(context, "Saved screenshot to Device Gallery", Toast.LENGTH_SHORT).show()
                                        }
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
                                    contentDescription = "Save to Device",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Close Button
                            IconButton(
                                onClick = { previewFile = null },
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
                                .height(320.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }
}
