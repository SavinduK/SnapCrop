package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

private val SamsungZoomGold = Color(0xFFFFD60A)
private val ZoomPillBackdrop = Color(0xBD090D16)
private val ZoomRulerBackdrop = Color(0xD9090D16)
private val ZoomPillBorder = Color(0x2EFFFFFF)

/**
 * Builds Samsung One UI-style camera zoom presets tailored to [minZoom]..[maxZoom].
 * For standard 1x..8x cameras, produces [1.0f, 2.0f, 4.0f, 8.0f].
 * When ultra-wide (< 1.0x) is available, prepends the ultra-wide stop (e.g., 0.5x / 0.6x).
 */
internal fun buildSamsungZoomPresets(minZoom: Float, maxZoom: Float): List<Float> {
    val safeMin = minZoom.coerceIn(0.4f, 1.0f)
    val safeMax = maxZoom.coerceAtLeast(2.0f)
    val presets = mutableListOf<Float>()

    if (safeMin < 0.92f) {
        presets.add((safeMin * 10f).roundToInt() / 10f)
    }
    presets.add(1.0f)
    if (safeMax >= 1.95f) {
        presets.add(2.0f)
    }
    if (safeMax >= 3.95f) {
        presets.add(4.0f)
    }
    when {
        safeMax >= 9.95f -> presets.add(10.0f)
        safeMax >= 7.95f -> presets.add(8.0f)
        safeMax > 4.3f -> presets.add((safeMax * 10f).roundToInt() / 10f)
    }

    return presets.distinct().sorted()
}

/**
 * Formats a zoom multiplier in Samsung Camera style:
 * - Selected / active badge includes "x" ("0.5x", "1x", "2x", "2.4x", "8x")
 * - Unselected preset buttons omit "x" (".5", "1", "2", "4", "8")
 */
internal fun formatSamsungZoomLabel(zoom: Float, includeSuffix: Boolean): String {
    val rounded = (zoom * 10f).roundToInt() / 10f
    val nearestInt = rounded.roundToInt()
    val isWholeNumber = abs(rounded - nearestInt) < 0.05f

    return when {
        isWholeNumber -> if (includeSuffix) "${nearestInt}x" else "$nearestInt"
        rounded < 1.0f -> {
            if (includeSuffix) {
                String.format(Locale.US, "%.1fx", rounded)
            } else {
                val tenths = (rounded * 10f).roundToInt().coerceIn(1, 9)
                ".$tenths"
            }
        }
        else -> {
            if (includeSuffix) {
                String.format(Locale.US, "%.1fx", rounded)
            } else {
                String.format(Locale.US, "%.1f", rounded)
            }
        }
    }
}

internal fun zoomPresetTagSuffix(preset: Float): String {
    val rounded = (preset * 10f).roundToInt() / 10f
    val nearestInt = rounded.roundToInt()
    return if (abs(rounded - nearestInt) < 0.05f) {
        "${nearestInt}x"
    } else {
        String.format(Locale.US, "%.1fx", rounded).replace('.', '_')
    }
}

internal fun zoomToNormalizedProgress(zoom: Float, minZoom: Float, maxZoom: Float): Float {
    val safeMin = minZoom.coerceAtLeast(0.1f)
    val safeMax = maxZoom.coerceAtLeast(safeMin + 0.1f)
    val clamped = zoom.coerceIn(safeMin, safeMax)
    val ratio = safeMax / safeMin
    if (ratio <= 1.0001f) return 0f
    return (ln(clamped / safeMin) / ln(ratio)).coerceIn(0f, 1f)
}

internal fun normalizedProgressToZoom(
    progress: Float,
    minZoom: Float,
    maxZoom: Float,
    presets: List<Float> = emptyList()
): Float {
    val safeMin = minZoom.coerceAtLeast(0.1f)
    val safeMax = maxZoom.coerceAtLeast(safeMin + 0.1f)
    val clampedProgress = progress.coerceIn(0f, 1f)
    val rawZoom = safeMin * (safeMax / safeMin).pow(clampedProgress)
    val rounded = ((rawZoom * 10f).roundToInt() / 10f).coerceIn(safeMin, safeMax)

    // Subtle magnetic snap when very close to a preset stop
    for (preset in presets) {
        if (abs(rounded - preset) <= 0.04f) {
            return preset
        }
    }
    return rounded
}

/**
 * Samsung One UI-inspired Camera Zoom Bar.
 *
 * Features:
 * - Floating frosted capsule with circular lens/zoom preset buttons (e.g., .5, 1x, 2, 4, 8).
 * - Active preset slot dynamically displays the exact live zoom multiplier in Samsung gold (e.g., "2.4x").
 * - Tapping any preset jumps to that zoom level and reveals the precision logarithmic tick-mark
 *   scrubber ruler; dragging horizontally across either the capsule or the ruler scrubs zoom smoothly.
 */
@Composable
fun SamsungCameraZoomBar(
    zoomRatio: Float,
    minZoom: Float,
    maxZoom: Float,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDraggingChange: (Boolean) -> Unit,
    onZoomChange: (Float) -> Unit,
    onInteractionTick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val presets = remember(minZoom, maxZoom) { buildSamsungZoomPresets(minZoom, maxZoom) }

    val currentZoomState by rememberUpdatedState(zoomRatio)
    val currentOnZoomChange by rememberUpdatedState(onZoomChange)
    val currentOnExpandedChange by rememberUpdatedState(onExpandedChange)
    val currentOnDraggingChange by rememberUpdatedState(onDraggingChange)
    val currentOnInteractionTick by rememberUpdatedState(onInteractionTick)

    // Determine which preset slot owns the current zoom value (largest preset <= zoomRatio + epsilon)
    val activePresetIndex = remember(zoomRatio, presets) {
        val exactIdx = presets.indexOfFirst { abs(it - zoomRatio) < 0.06f }
        if (exactIdx >= 0) {
            exactIdx
        } else {
            val bucketIdx = presets.indexOfLast { it <= zoomRatio + 0.05f }
            if (bucketIdx >= 0) bucketIdx else 0
        }
    }

    var pillWidthPx by remember { mutableFloatStateOf(1f) }
    var lastHapticStep by remember { mutableFloatStateOf((zoomRatio * 10f).roundToInt().toFloat()) }

    fun emitZoomWithTickHaptic(newZoom: Float) {
        val prevPresetHit = presets.firstOrNull { abs(it - currentZoomState) < 0.05f }
        val nextPresetHit = presets.firstOrNull { abs(it - newZoom) < 0.05f }
        val tenthStep = (newZoom * 10f).roundToInt().toFloat()
        if ((nextPresetHit != null && nextPresetHit != prevPresetHit) || abs(tenthStep - lastHapticStep) >= 5f) {
            lastHapticStep = tenthStep
            runCatching { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
        }
        currentOnZoomChange(newZoom)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .testTag("camera_zoom_bar"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Preset Lens / Zoom Capsule (supports tap on presets AND horizontal drag scrubbing)
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = ZoomPillBackdrop,
            border = BorderStroke(1.dp, ZoomPillBorder),
            modifier = Modifier
                .onSizeChanged { size ->
                    pillWidthPx = size.width.toFloat().coerceAtLeast(1f)
                }
                .pointerInput(minZoom, maxZoom, presets) {
                    var startProgress = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            startProgress = zoomToNormalizedProgress(currentZoomState, minZoom, maxZoom)
                            currentOnDraggingChange(true)
                            currentOnExpandedChange(true)
                            currentOnInteractionTick()
                        },
                        onDragEnd = {
                            currentOnDraggingChange(false)
                            currentOnInteractionTick()
                        },
                        onDragCancel = {
                            currentOnDraggingChange(false)
                            currentOnInteractionTick()
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val deltaProgress = dragAmount / pillWidthPx.coerceAtLeast(160f)
                            startProgress = (startProgress + deltaProgress).coerceIn(0f, 1f)
                            val updatedZoom = normalizedProgressToZoom(
                                progress = startProgress,
                                minZoom = minZoom,
                                maxZoom = maxZoom,
                                presets = presets
                            )
                            currentOnInteractionTick()
                            emitZoomWithTickHaptic(updatedZoom)
                        }
                    )
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                presets.forEachIndexed { index, preset ->
                    val isSelected = index == activePresetIndex
                    val displayLabel = if (isSelected) {
                        formatSamsungZoomLabel(zoomRatio, includeSuffix = true)
                    } else {
                        formatSamsungZoomLabel(preset, includeSuffix = false)
                    }
                    val tagSuffix = zoomPresetTagSuffix(preset)
                    val presetDesc = stringResource(
                        R.string.camera_zoom_preset_cd,
                        formatSamsungZoomLabel(preset, includeSuffix = true)
                    )

                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .clickable {
                                currentOnInteractionTick()
                                if (isSelected && abs(zoomRatio - preset) <= 0.06f) {
                                    currentOnExpandedChange(!isExpanded)
                                } else {
                                    emitZoomWithTickHaptic(preset)
                                    currentOnExpandedChange(true)
                                }
                            }
                            .semantics { contentDescription = presetDesc }
                            .testTag("btn_camera_zoom_$tagSuffix"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .widthIn(min = if (isSelected) 38.dp else 32.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(
                                    if (isSelected) Color(0xF01E293B) else Color(0x52FFFFFF).copy(alpha = 0.08f)
                                )
                                .then(
                                    if (isSelected) {
                                        Modifier.border(
                                            width = 1.4.dp,
                                            color = SamsungZoomGold,
                                            shape = RoundedCornerShape(percent = 50)
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .padding(horizontal = if (isSelected) 8.dp else 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = displayLabel,
                                color = if (isSelected) SamsungZoomGold else Color.White,
                                fontSize = if (isSelected) 12.sp else 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Expanded Samsung Tick-Mark Zoom Scrubber Bar
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(tween(160)) + expandVertically(tween(180)),
            exit = fadeOut(tween(160)) + shrinkVertically(tween(180))
        ) {
            SamsungTickMarkRuler(
                zoomRatio = zoomRatio,
                minZoom = minZoom,
                maxZoom = maxZoom,
                presets = presets,
                onZoomChange = { updated ->
                    currentOnInteractionTick()
                    emitZoomWithTickHaptic(updated)
                },
                onDraggingChange = { dragging ->
                    currentOnDraggingChange(dragging)
                    currentOnInteractionTick()
                }
            )
        }
    }
}

@Composable
private fun SamsungTickMarkRuler(
    zoomRatio: Float,
    minZoom: Float,
    maxZoom: Float,
    presets: List<Float>,
    onZoomChange: (Float) -> Unit,
    onDraggingChange: (Boolean) -> Unit
) {
    val sliderDesc = stringResource(
        R.string.camera_zoom_slider_cd,
        formatSamsungZoomLabel(zoomRatio, includeSuffix = true)
    )
    var rulerWidthPx by remember { mutableFloatStateOf(1f) }
    val currentOnZoomChange by rememberUpdatedState(onZoomChange)
    val currentOnDraggingChange by rememberUpdatedState(onDraggingChange)

    val presetProgressPositions = remember(presets, minZoom, maxZoom) {
        presets.map { preset -> zoomToNormalizedProgress(preset, minZoom, maxZoom) }
    }
    val activeProgress = zoomToNormalizedProgress(zoomRatio, minZoom, maxZoom)

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = ZoomRulerBackdrop,
        border = BorderStroke(1.dp, ZoomPillBorder),
        modifier = Modifier
            .widthIn(max = 340.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = formatSamsungZoomLabel(zoomRatio, includeSuffix = true),
                color = SamsungZoomGold,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("camera_zoom_value_label")
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .onSizeChanged { size ->
                        rulerWidthPx = size.width.toFloat().coerceAtLeast(1f)
                    }
                    .pointerInput(minZoom, maxZoom, presets) {
                        detectTapGestures { offset ->
                            val horizontalPad = 12.dp.toPx()
                            val usableWidth = (rulerWidthPx - horizontalPad * 2f).coerceAtLeast(1f)
                            val targetProgress = ((offset.x - horizontalPad) / usableWidth).coerceIn(0f, 1f)
                            val newZoom = normalizedProgressToZoom(
                                progress = targetProgress,
                                minZoom = minZoom,
                                maxZoom = maxZoom,
                                presets = presets
                            )
                            currentOnZoomChange(newZoom)
                        }
                    }
                    .pointerInput(minZoom, maxZoom, presets) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                currentOnDraggingChange(true)
                                val horizontalPad = 12.dp.toPx()
                                val usableWidth = (rulerWidthPx - horizontalPad * 2f).coerceAtLeast(1f)
                                val targetProgress = ((offset.x - horizontalPad) / usableWidth).coerceIn(0f, 1f)
                                currentOnZoomChange(
                                    normalizedProgressToZoom(
                                        progress = targetProgress,
                                        minZoom = minZoom,
                                        maxZoom = maxZoom,
                                        presets = presets
                                    )
                                )
                            },
                            onDragEnd = { currentOnDraggingChange(false) },
                            onDragCancel = { currentOnDraggingChange(false) },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                val horizontalPad = 12.dp.toPx()
                                val usableWidth = (rulerWidthPx - horizontalPad * 2f).coerceAtLeast(1f)
                                val targetProgress =
                                    ((change.position.x - horizontalPad) / usableWidth).coerceIn(0f, 1f)
                                currentOnZoomChange(
                                    normalizedProgressToZoom(
                                        progress = targetProgress,
                                        minZoom = minZoom,
                                        maxZoom = maxZoom,
                                        presets = presets
                                    )
                                )
                            }
                        )
                    }
                    .semantics { contentDescription = sliderDesc }
                    .testTag("camera_zoom_slider"),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxWidth().height(36.dp)) {
                    val horizontalPad = 12.dp.toPx()
                    val usableWidth = (size.width - horizontalPad * 2f).coerceAtLeast(1f)
                    val centerY = size.height / 2f
                    val totalTicks = 40

                    for (i in 0..totalTicks) {
                        val tickProgress = i.toFloat() / totalTicks.toFloat()
                        val x = horizontalPad + tickProgress * usableWidth
                        val isMajor = presetProgressPositions.any { abs(it - tickProgress) < 0.016f } ||
                            i % 10 == 0
                        val tickHalfHeight = if (isMajor) 9.dp.toPx() else 5.dp.toPx()
                        val isPassed = tickProgress <= activeProgress + 0.005f
                        val tickColor = when {
                            isMajor && isPassed -> Color.White
                            isMajor -> Color(0xB3FFFFFF)
                            isPassed -> SamsungZoomGold.copy(alpha = 0.65f)
                            else -> Color(0x52FFFFFF)
                        }

                        drawLine(
                            color = tickColor,
                            start = Offset(x, centerY - tickHalfHeight),
                            end = Offset(x, centerY + tickHalfHeight),
                            strokeWidth = if (isMajor) 2.dp.toPx() else 1.2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // Active Samsung-Gold Indicator Needle + Thumb Dot
                    val activeX = horizontalPad + activeProgress * usableWidth
                    drawLine(
                        color = SamsungZoomGold,
                        start = Offset(activeX, centerY - 14.dp.toPx()),
                        end = Offset(activeX, centerY + 14.dp.toPx()),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = SamsungZoomGold,
                        radius = 4.5.dp.toPx(),
                        center = Offset(activeX, centerY)
                    )
                }
            }
        }
    }
}
