package com.example

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Normalized crop rectangle in [0f..1f] coordinates relative to a Bitmap's dimensions.
 */
data class NormalizedCropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)

    fun isFullImage(epsilon: Float = 0.01f): Boolean {
        return left <= epsilon &&
            top <= epsilon &&
            right >= 1f - epsilon &&
            bottom >= 1f - epsilon
    }

    /**
     * Extracts a cropped [Bitmap] corresponding to this normalized region.
     * Returns [source] directly if the entire image is selected.
     */
    fun extractFromBitmap(source: Bitmap): Bitmap {
        if (isFullImage() || source.width <= 1 || source.height <= 1) {
            return source
        }
        val cropLeft = (left * source.width).roundToInt().coerceIn(0, source.width - 1)
        val cropTop = (top * source.height).roundToInt().coerceIn(0, source.height - 1)
        val cropRight = (right * source.width).roundToInt().coerceIn(cropLeft + 1, source.width)
        val cropBottom = (bottom * source.height).roundToInt().coerceIn(cropTop + 1, source.height)
        return Bitmap.createBitmap(
            source,
            cropLeft,
            cropTop,
            (cropRight - cropLeft).coerceAtLeast(1),
            (cropBottom - cropTop).coerceAtLeast(1)
        )
    }

    companion object {
        val FULL = NormalizedCropRect(0f, 0f, 1f, 1f)
    }
}

private enum class CropDragMode {
    NONE,
    CREATE_NEW,
    MOVE,
    RESIZE_TL,
    RESIZE_TR,
    RESIZE_BL,
    RESIZE_BR,
    RESIZE_TOP,
    RESIZE_BOTTOM,
    RESIZE_LEFT,
    RESIZE_RIGHT
}

/**
 * Computes aspect-ratio-preserving fitted bounds for an image inside a container.
 */
internal fun computeFittedImageBounds(
    containerWidth: Float,
    containerHeight: Float,
    imageWidth: Int,
    imageHeight: Int,
    paddingPx: Float = 0f
): RectF {
    val availW = (containerWidth - paddingPx * 2f).coerceAtLeast(1f)
    val availH = (containerHeight - paddingPx * 2f).coerceAtLeast(1f)
    if (imageWidth <= 0 || imageHeight <= 0) {
        return RectF(paddingPx, paddingPx, paddingPx + availW, paddingPx + availH)
    }
    val imgAspect = imageWidth.toFloat() / imageHeight.toFloat()
    val containerAspect = availW / availH

    val drawW: Float
    val drawH: Float
    if (imgAspect > containerAspect) {
        drawW = availW
        drawH = availW / imgAspect
    } else {
        drawH = availH
        drawW = availH * imgAspect
    }
    val left = (containerWidth - drawW) / 2f
    val top = (containerHeight - drawH) / 2f
    return RectF(left, top, left + drawW, top + drawH)
}

/**
 * Interactive aspect-ratio-accurate photo cropper for Camera captures.
 * Supports dragging corners, edges, moving sub-crops, or drawing a new crop region directly on the photo.
 */
@Composable
fun CameraImageCropper(
    bitmap: Bitmap,
    cropRect: NormalizedCropRect,
    onCropRectChange: (NormalizedCropRect) -> Unit,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val currentCrop by rememberUpdatedState(cropRect)
    val currentOnChange by rememberUpdatedState(onCropRectChange)
    var isDragging by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("camera_image_cropper")
            .pointerInput(bitmap) {
                val cornerRadiusPx = 30.dp.toPx()
                val edgeMarginPx = 22.dp.toPx()
                val padPx = 14.dp.toPx()
                val minNormSpan = 0.08f

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val bounds = computeFittedImageBounds(
                        size.width.toFloat(),
                        size.height.toFloat(),
                        bitmap.width,
                        bitmap.height,
                        padPx
                    )
                    if (bounds.width() <= 1f || bounds.height() <= 1f) return@awaitEachGesture

                    val startCrop = currentCrop
                    val selLeft = bounds.left + startCrop.left * bounds.width()
                    val selTop = bounds.top + startCrop.top * bounds.height()
                    val selRight = bounds.left + startCrop.right * bounds.width()
                    val selBottom = bounds.top + startCrop.bottom * bounds.height()

                    val x0 = down.position.x
                    val y0 = down.position.y

                    val mode = when {
                        hypot(x0 - selLeft, y0 - selTop) <= cornerRadiusPx -> CropDragMode.RESIZE_TL
                        hypot(x0 - selRight, y0 - selTop) <= cornerRadiusPx -> CropDragMode.RESIZE_TR
                        hypot(x0 - selLeft, y0 - selBottom) <= cornerRadiusPx -> CropDragMode.RESIZE_BL
                        hypot(x0 - selRight, y0 - selBottom) <= cornerRadiusPx -> CropDragMode.RESIZE_BR
                        abs(y0 - selTop) <= edgeMarginPx && x0 in (selLeft - edgeMarginPx)..(selRight + edgeMarginPx) ->
                            CropDragMode.RESIZE_TOP
                        abs(y0 - selBottom) <= edgeMarginPx && x0 in (selLeft - edgeMarginPx)..(selRight + edgeMarginPx) ->
                            CropDragMode.RESIZE_BOTTOM
                        abs(x0 - selLeft) <= edgeMarginPx && y0 in (selTop - edgeMarginPx)..(selBottom + edgeMarginPx) ->
                            CropDragMode.RESIZE_LEFT
                        abs(x0 - selRight) <= edgeMarginPx && y0 in (selTop - edgeMarginPx)..(selBottom + edgeMarginPx) ->
                            CropDragMode.RESIZE_RIGHT
                        !startCrop.isFullImage() && x0 in selLeft..selRight && y0 in selTop..selBottom ->
                            CropDragMode.MOVE
                        x0 in bounds.left..bounds.right && y0 in bounds.top..bounds.bottom ->
                            CropDragMode.CREATE_NEW
                        else -> CropDragMode.NONE
                    }

                    if (mode == CropDragMode.NONE) return@awaitEachGesture

                    val startNormX = ((x0 - bounds.left) / bounds.width()).coerceIn(0f, 1f)
                    val startNormY = ((y0 - bounds.top) / bounds.height()).coerceIn(0f, 1f)
                    var lastPos = down.position
                    val slop = viewConfiguration.touchSlop
                    var hasExceededSlop = mode != CropDragMode.CREATE_NEW
                    isDragging = true

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) {
                                // Ensure minimum crop dimensions if a new crop was drawn
                                val finalCrop = currentCrop
                                if (finalCrop.width < minNormSpan || finalCrop.height < minNormSpan) {
                                    currentOnChange(startCrop)
                                }
                                break
                            }

                            val pos = change.position
                            if (!hasExceededSlop && hypot(pos.x - x0, pos.y - y0) > slop) {
                                hasExceededSlop = true
                            }
                            if (!hasExceededSlop) continue

                            change.consume()

                            val curNormX = ((pos.x - bounds.left) / bounds.width()).coerceIn(0f, 1f)
                            val curNormY = ((pos.y - bounds.top) / bounds.height()).coerceIn(0f, 1f)
                            val active = currentCrop

                            val updated = when (mode) {
                                CropDragMode.CREATE_NEW -> NormalizedCropRect(
                                    left = min(startNormX, curNormX),
                                    top = min(startNormY, curNormY),
                                    right = max(startNormX, curNormX),
                                    bottom = max(startNormY, curNormY)
                                )
                                CropDragMode.MOVE -> {
                                    val dxNorm = (pos.x - lastPos.x) / bounds.width()
                                    val dyNorm = (pos.y - lastPos.y) / bounds.height()
                                    val w = active.width
                                    val h = active.height
                                    val newL = (active.left + dxNorm).coerceIn(0f, (1f - w).coerceAtLeast(0f))
                                    val newT = (active.top + dyNorm).coerceIn(0f, (1f - h).coerceAtLeast(0f))
                                    NormalizedCropRect(newL, newT, newL + w, newT + h)
                                }
                                CropDragMode.RESIZE_TL -> NormalizedCropRect(
                                    left = min(curNormX, active.right - minNormSpan).coerceAtLeast(0f),
                                    top = min(curNormY, active.bottom - minNormSpan).coerceAtLeast(0f),
                                    right = active.right,
                                    bottom = active.bottom
                                )
                                CropDragMode.RESIZE_TR -> NormalizedCropRect(
                                    left = active.left,
                                    top = min(curNormY, active.bottom - minNormSpan).coerceAtLeast(0f),
                                    right = max(curNormX, active.left + minNormSpan).coerceAtMost(1f),
                                    bottom = active.bottom
                                )
                                CropDragMode.RESIZE_BL -> NormalizedCropRect(
                                    left = min(curNormX, active.right - minNormSpan).coerceAtLeast(0f),
                                    top = active.top,
                                    right = active.right,
                                    bottom = max(curNormY, active.top + minNormSpan).coerceAtMost(1f)
                                )
                                CropDragMode.RESIZE_BR -> NormalizedCropRect(
                                    left = active.left,
                                    top = active.top,
                                    right = max(curNormX, active.left + minNormSpan).coerceAtMost(1f),
                                    bottom = max(curNormY, active.top + minNormSpan).coerceAtMost(1f)
                                )
                                CropDragMode.RESIZE_TOP -> NormalizedCropRect(
                                    left = active.left,
                                    top = min(curNormY, active.bottom - minNormSpan).coerceAtLeast(0f),
                                    right = active.right,
                                    bottom = active.bottom
                                )
                                CropDragMode.RESIZE_BOTTOM -> NormalizedCropRect(
                                    left = active.left,
                                    top = active.top,
                                    right = active.right,
                                    bottom = max(curNormY, active.top + minNormSpan).coerceAtMost(1f)
                                )
                                CropDragMode.RESIZE_LEFT -> NormalizedCropRect(
                                    left = min(curNormX, active.right - minNormSpan).coerceAtLeast(0f),
                                    top = active.top,
                                    right = active.right,
                                    bottom = active.bottom
                                )
                                CropDragMode.RESIZE_RIGHT -> NormalizedCropRect(
                                    left = active.left,
                                    top = active.top,
                                    right = max(curNormX, active.left + minNormSpan).coerceAtMost(1f),
                                    bottom = active.bottom
                                )
                                CropDragMode.NONE -> active
                            }

                            lastPos = pos
                            currentOnChange(updated)
                        }
                    } finally {
                        isDragging = false
                    }
                }
            }
    ) {
        canvasSize = size
        val padPx = 14.dp.toPx()
        val imgBounds = computeFittedImageBounds(
            size.width,
            size.height,
            bitmap.width,
            bitmap.height,
            padPx
        )

        // 1. Draw the captured bitmap fitted without distortion
        drawImage(
            image = imageBitmap,
            dstOffset = IntOffset(imgBounds.left.roundToInt(), imgBounds.top.roundToInt()),
            dstSize = IntSize(
                imgBounds.width().roundToInt().coerceAtLeast(1),
                imgBounds.height().roundToInt().coerceAtLeast(1)
            )
        )

        // 2. Compute selection rectangle in canvas coordinates
        val selL = imgBounds.left + cropRect.left * imgBounds.width()
        val selT = imgBounds.top + cropRect.top * imgBounds.height()
        val selR = imgBounds.left + cropRect.right * imgBounds.width()
        val selB = imgBounds.top + cropRect.bottom * imgBounds.height()
        val selW = (selR - selL).coerceAtLeast(1f)
        val selH = (selB - selT).coerceAtLeast(1f)

        // 3. Dim regions outside the crop box within the image bounds
        val dimColor = Color(0x99060911)
        if (selT > imgBounds.top) {
            drawRect(dimColor, Offset(imgBounds.left, imgBounds.top), Size(imgBounds.width(), selT - imgBounds.top))
        }
        if (selB < imgBounds.bottom) {
            drawRect(dimColor, Offset(imgBounds.left, selB), Size(imgBounds.width(), imgBounds.bottom - selB))
        }
        if (selL > imgBounds.left) {
            drawRect(dimColor, Offset(imgBounds.left, selT), Size(selL - imgBounds.left, selH))
        }
        if (selR < imgBounds.right) {
            drawRect(dimColor, Offset(selR, selT), Size(imgBounds.right - selR, selH))
        }

        // 4. Rule-of-thirds grid lines while actively cropping or when sub-cropped
        if (isDragging || !cropRect.isFullImage()) {
            val gridColor = Color(0x55FFFFFF)
            val gridStroke = 1.dp.toPx()
            for (i in 1..2) {
                val gx = selL + selW * (i / 3f)
                val gy = selT + selH * (i / 3f)
                drawLine(gridColor, Offset(gx, selT), Offset(gx, selB), strokeWidth = gridStroke)
                drawLine(gridColor, Offset(selL, gy), Offset(selR, gy), strokeWidth = gridStroke)
            }
        }

        // 5. Outer cyan glow + crisp white border
        drawRect(
            color = Color(0x6600E5FF),
            topLeft = Offset(selL, selT),
            size = Size(selW, selH),
            style = Stroke(width = 3.dp.toPx())
        )
        drawRect(
            color = Color.White,
            topLeft = Offset(selL, selT),
            size = Size(selW, selH),
            style = Stroke(width = 1.5.dp.toPx())
        )

        // 6. Corner L-handles + edge pills for clear crop affordance
        val handleLen = min(20.dp.toPx(), min(selW, selH) * 0.35f)
        val handleStroke = 3.5.dp.toPx()
        val accentColor = Color(0xFF00E5FF)

        val cornersPath = Path().apply {
            // Top-Left
            moveTo(selL, selT + handleLen)
            lineTo(selL, selT)
            lineTo(selL + handleLen, selT)
            // Top-Right
            moveTo(selR - handleLen, selT)
            lineTo(selR, selT)
            lineTo(selR, selT + handleLen)
            // Bottom-Left
            moveTo(selL, selB - handleLen)
            lineTo(selL, selB)
            lineTo(selL + handleLen, selB)
            // Bottom-Right
            moveTo(selR - handleLen, selB)
            lineTo(selR, selB)
            lineTo(selR, selB - handleLen)
        }
        drawPath(
            path = cornersPath,
            color = accentColor,
            style = Stroke(width = handleStroke, cap = StrokeCap.Round)
        )

        // Edge center pills
        val pillLen = min(22.dp.toPx(), min(selW, selH) * 0.3f)
        val pillThick = 3.5.dp.toPx()
        val cr = CornerRadius(pillThick / 2f, pillThick / 2f)
        val cx = (selL + selR) / 2f
        val cy = (selT + selB) / 2f

        drawRoundRect(Color.White, Offset(cx - pillLen / 2f, selT - pillThick / 2f), Size(pillLen, pillThick), cr)
        drawRoundRect(Color.White, Offset(cx - pillLen / 2f, selB - pillThick / 2f), Size(pillLen, pillThick), cr)
        drawRoundRect(Color.White, Offset(selL - pillThick / 2f, cy - pillLen / 2f), Size(pillThick, pillLen), cr)
        drawRoundRect(Color.White, Offset(selR - pillThick / 2f, cy - pillLen / 2f), Size(pillThick, pillLen), cr)
    }
}
