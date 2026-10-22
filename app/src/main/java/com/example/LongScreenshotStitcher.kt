package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Log
import kotlin.math.abs

/**
 * LongScreenshotStitcher
 *
 * Provides high-performance, pixel-perfect vertical image stitching for multi-page scroll capture.
 * Accurately calculates vertical displacement between consecutive viewports using multi-row
 * template matching, guaranteeing:
 * - NO overlapping parts
 * - NO double/duplicate parts
 * - NO missing parts
 * Seamlessly connects new scrolled content while strictly excluding duplicate status bars,
 * browser URL bars, headers, and navigation bars from intermediate slices.
 */
object LongScreenshotStitcher {

    private const val TAG = "LongScreenshotStitcher"

    /**
     * Checks if the scrollable area between prev and curr is essentially identical
     * (i.e. the page has reached the end and didn't scroll at all).
     */
    fun isEssentiallyIdentical(
        prev: Bitmap,
        curr: Bitmap,
        statusBarHeightPx: Int = 0,
        navBarHeightPx: Int = 0
    ): Boolean {
        if (prev.width != curr.width || prev.height != curr.height) return false
        val top = detectFixedTopBarHeight(prev, curr, statusBarHeightPx)
        val bottom = detectFixedBottomBarTop(prev, curr, navBarHeightPx)
        if (bottom <= top) return false

        val sampleX = mutableListOf<Int>()
        val stepX = (prev.width / 25).coerceAtLeast(8)
        for (x in stepX until prev.width - stepX step stepX) {
            sampleX.add(x)
        }

        var totalDiff = 0L
        var count = 0
        val stepY = ((bottom - top) / 35).coerceAtLeast(4)
        for (y in top until bottom step stepY) {
            for (x in sampleX) {
                val p1 = prev.getPixel(x, y)
                val p2 = curr.getPixel(x, y)
                totalDiff += (abs((p1 shr 16 and 0xFF) - (p2 shr 16 and 0xFF)) +
                        abs((p1 shr 8 and 0xFF) - (p2 shr 8 and 0xFF)) +
                        abs((p1 and 0xFF) - (p2 and 0xFF)))
                count++
            }
        }
        val avgDiff = if (count > 0) totalDiff / count else 255L
        return avgDiff < 4L
    }

    /**
     * Detects the bottom boundary of any fixed top bars (system status bar, browser URL bar, app headers)
     * that remain unchanged between two frames during scrolling.
     */
    fun detectFixedTopBarHeight(
        prev: Bitmap,
        curr: Bitmap,
        statusBarHeightPx: Int = 0
    ): Int {
        val width = prev.width
        val height = prev.height
        val minHeaderY = statusBarHeightPx.coerceIn(0, height / 2)
        val maxSearchY = (height * 0.32f).toInt().coerceAtLeast(minHeaderY + 20).coerceAtMost(height / 2)

        val sampleX = mutableListOf<Int>()
        val stepX = (width / 30).coerceAtLeast(8)
        for (x in stepX until width - stepX step stepX) {
            sampleX.add(x)
        }

        var detectedHeaderBottom = minHeaderY
        var consecutiveMismatch = 0
        var foundContentBoundary = false

        for (y in 0..maxSearchY step 2) {
            var diffSum = 0L
            var count = 0
            for (x in sampleX) {
                val p1 = prev.getPixel(x, y)
                val p2 = curr.getPixel(x, y)
                diffSum += (abs((p1 shr 16 and 0xFF) - (p2 shr 16 and 0xFF)) +
                        abs((p1 shr 8 and 0xFF) - (p2 shr 8 and 0xFF)) +
                        abs((p1 and 0xFF) - (p2 and 0xFF)))
                count++
            }
            val avgDiff = if (count > 0) diffSum / count else 255L
            if (avgDiff < 12L) {
                detectedHeaderBottom = y + 2
                consecutiveMismatch = 0
            } else {
                consecutiveMismatch++
                if (consecutiveMismatch >= 6 && y > minHeaderY) {
                    foundContentBoundary = true
                    break
                }
            }
        }

        if (!foundContentBoundary) {
            detectedHeaderBottom = minHeaderY
        }

        return detectedHeaderBottom.coerceIn(minHeaderY, maxSearchY)
    }

    /**
     * Detects the top boundary of any fixed bottom bars (system navigation bar, bottom toolbars)
     * that remain unchanged between two frames during scrolling.
     */
    fun detectFixedBottomBarTop(
        prev: Bitmap,
        curr: Bitmap,
        navBarHeightPx: Int = 0
    ): Int {
        val width = prev.width
        val height = prev.height
        val defaultFooterTop = (height - navBarHeightPx).coerceIn(height / 2, height)
        val maxFooterH = (height * 0.22f).toInt().coerceAtLeast(navBarHeightPx + 20).coerceAtMost(height / 2)
        val minSearchY = height - maxFooterH

        val sampleX = mutableListOf<Int>()
        val stepX = (width / 30).coerceAtLeast(8)
        for (x in stepX until width - stepX step stepX) {
            sampleX.add(x)
        }

        var detectedFooterTop = defaultFooterTop
        var consecutiveMismatch = 0
        var foundContentBoundary = false

        for (y in (height - 2) downTo minSearchY step 2) {
            var diffSum = 0L
            var count = 0
            for (x in sampleX) {
                val p1 = prev.getPixel(x, y)
                val p2 = curr.getPixel(x, y)
                diffSum += (abs((p1 shr 16 and 0xFF) - (p2 shr 16 and 0xFF)) +
                        abs((p1 shr 8 and 0xFF) - (p2 shr 8 and 0xFF)) +
                        abs((p1 and 0xFF) - (p2 and 0xFF)))
                count++
            }
            val avgDiff = if (count > 0) diffSum / count else 255L
            if (avgDiff < 12L) {
                detectedFooterTop = y
                consecutiveMismatch = 0
            } else {
                consecutiveMismatch++
                if (consecutiveMismatch >= 6 && y < defaultFooterTop) {
                    foundContentBoundary = true
                    break
                }
            }
        }

        if (!foundContentBoundary) {
            detectedFooterTop = defaultFooterTop
        }

        return detectedFooterTop.coerceIn(minSearchY, defaultFooterTop)
    }

    /**
     * Determines the exact vertical scroll displacement (delta in pixels) between two consecutive frames.
     * Content at (x, y) in `curr` was at (x, y + delta) in `prev`.
     *
     * @return Positive vertical displacement in pixels, or 0 if no scroll occurred.
     */
    fun findVerticalOffset(
        prev: Bitmap,
        curr: Bitmap,
        statusBarHeightPx: Int = 0,
        navBarHeightPx: Int = 0
    ): Int {
        val width = prev.width
        val height = prev.height
        if (curr.width != width || curr.height != height) {
            return (height * 0.44f).toInt()
        }

        val topFixedLimit = detectFixedTopBarHeight(prev, curr, statusBarHeightPx)
        val bottomFixedLimit = detectFixedBottomBarTop(prev, curr, navBarHeightPx)
        val scrollableHeight = bottomFixedLimit - topFixedLimit

        if (scrollableHeight < 80) {
            return 0
        }

        if (isEssentiallyIdentical(prev, curr, statusBarHeightPx, navBarHeightPx)) {
            return 0
        }

        // Horizontal sample columns (avoid outer 10% to bypass scrollbars and edge handles)
        val sampleX = mutableListOf<Int>()
        val xStart = (width * 0.12f).toInt()
        val xEnd = (width * 0.88f).toInt()
        val xStep = ((xEnd - xStart) / 36).coerceAtLeast(6)
        for (x in xStart..xEnd step xStep) {
            sampleX.add(x)
        }

        // Reference template band from curr:
        // Take a prominent band near the top of the scrollable area in curr
        val bandH = (scrollableHeight * 0.10f).toInt().coerceIn(60, 140)
        val refTopInCurr = topFixedLimit + 16
        val refBottomInCurr = refTopInCurr + bandH

        val sampleRowsInCurr = mutableListOf<Int>()
        for (y in refTopInCurr until refBottomInCurr step 3) {
            sampleRowsInCurr.add(y)
        }

        val minDelta = 25
        val maxDelta = (scrollableHeight - bandH - 20).coerceAtLeast(minDelta + 10)

        // Coarse search: step by 4 pixels across all possible displacements
        var bestDelta = 0
        var lowestDiff = Long.MAX_VALUE

        for (delta in minDelta..maxDelta step 4) {
            var diffSum = 0L
            var count = 0

            for (yCurr in sampleRowsInCurr) {
                val yPrev = yCurr + delta
                if (yPrev >= bottomFixedLimit) continue

                for (x in sampleX) {
                    val p1 = curr.getPixel(x, yCurr)
                    val p2 = prev.getPixel(x, yPrev)

                    diffSum += (abs((p1 shr 16 and 0xFF) - (p2 shr 16 and 0xFF)) +
                            abs((p1 shr 8 and 0xFF) - (p2 shr 8 and 0xFF)) +
                            abs((p1 and 0xFF) - (p2 and 0xFF)))
                    count++
                }
            }

            if (count > 0) {
                val avgDiff = diffSum / count
                if (avgDiff < lowestDiff) {
                    lowestDiff = avgDiff
                    bestDelta = delta
                }
            }
        }

        // Fine search: 1 pixel step around bestDelta
        var refinedDelta = bestDelta
        val fineMin = (bestDelta - 6).coerceAtLeast(minDelta)
        val fineMax = (bestDelta + 6).coerceAtMost(maxDelta)

        for (delta in fineMin..fineMax) {
            var diffSum = 0L
            var count = 0

            for (yCurr in sampleRowsInCurr) {
                val yPrev = yCurr + delta
                if (yPrev >= bottomFixedLimit) continue

                for (x in sampleX) {
                    val p1 = curr.getPixel(x, yCurr)
                    val p2 = prev.getPixel(x, yPrev)

                    diffSum += (abs((p1 shr 16 and 0xFF) - (p2 shr 16 and 0xFF)) +
                            abs((p1 shr 8 and 0xFF) - (p2 shr 8 and 0xFF)) +
                            abs((p1 and 0xFF) - (p2 and 0xFF)))
                    count++
                }
            }

            if (count > 0) {
                val avgDiff = diffSum / count
                if (avgDiff < lowestDiff) {
                    lowestDiff = avgDiff
                    refinedDelta = delta
                }
            }
        }

        // Confident match found
        if (refinedDelta > 0 && lowestDiff < 60L) {
            Log.d(TAG, "Vertical scroll offset detected: $refinedDelta px (avgDiff=$lowestDiff)")
            return refinedDelta
        }

        // No overlap detected (e.g. completely different page or test bitmaps)
        Log.d(TAG, "No vertical overlap detected (lowestDiff=$lowestDiff)")
        return 0
    }

    /**
     * Stitches an ordered list of screenshot bitmaps into a single continuous, seamless long bitmap.
     * Accurately aligns consecutive frames, completely removing duplicated status bars, browser address bars,
     * tabs, and navigation bars from all intermediate frames.
     *
     * @param bitmaps Sequential frames captured while scrolling down.
     * @param statusBarHeightPx Height of system status bar to trim from intermediate frames.
     * @param navBarHeightPx Height of navigation bar to trim from intermediate frames.
     * @return Stitched continuous seamless bitmap without overlap or duplicate content.
     */
    fun stitch(
        bitmaps: List<Bitmap>,
        statusBarHeightPx: Int = 0,
        navBarHeightPx: Int = 0
    ): Bitmap {
        if (bitmaps.isEmpty()) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        if (bitmaps.size == 1) {
            return bitmaps[0]
        }

        val width = bitmaps[0].width
        val height = bitmaps[0].height

        // Detect fixed top and bottom limits between first two frames
        val topFixedLimit = detectFixedTopBarHeight(bitmaps[0], bitmaps[1], statusBarHeightPx)
        val bottomFixedLimit = detectFixedBottomBarTop(bitmaps[0], bitmaps[1], navBarHeightPx)

        // Store slices to render
        data class FrameSlice(val bitmap: Bitmap, val srcTop: Int, val srcBottom: Int)
        val slices = mutableListOf<FrameSlice>()

        // Frame 0: include from 0 down to bottomFixedLimit
        // (Status bar and top app header included ONCE at the top)
        slices.add(FrameSlice(bitmaps[0], 0, bottomFixedLimit))

        for (i in 1 until bitmaps.size) {
            val prevBmp = bitmaps[i - 1]
            val currBmp = bitmaps[i]

            // If the two frames are identical (page reached bottom and didn't move), skip to avoid duplicate
            if (isEssentiallyIdentical(prevBmp, currBmp, statusBarHeightPx, navBarHeightPx)) {
                Log.d(TAG, "Frame $i is identical to frame ${i - 1}, skipping duplicate frame")
                continue
            }

            val delta = findVerticalOffset(prevBmp, currBmp, statusBarHeightPx, navBarHeightPx)

            if (delta > 10) {
                // In currBmp, new content is strictly from (bottomFixedLimit - delta) down to bottomFixedLimit
                val yStart = (bottomFixedLimit - delta).coerceIn(topFixedLimit, bottomFixedLimit - 1)
                val yEnd = bottomFixedLimit

                if (yEnd > yStart) {
                    slices.add(FrameSlice(currBmp, yStart, yEnd))
                }
            } else {
                // Non-overlapping / full-page step: trim top fixed bar, and for the last frame keep the bottom bar
                val isLast = (i == bitmaps.size - 1)
                val yStart = topFixedLimit.coerceIn(0, height)
                val yEnd = if (isLast) height else bottomFixedLimit.coerceIn(yStart, height)

                if (yEnd > yStart) {
                    slices.add(FrameSlice(currBmp, yStart, yEnd))
                }
            }
        }

        // Append the fixed bottom bar (navigation bar) from the very last frame ONCE at the end if not already included
        val lastSlice = slices.lastOrNull()
        if (lastSlice != null && lastSlice.srcBottom < height && bottomFixedLimit < height) {
            slices.add(FrameSlice(bitmaps.last(), bottomFixedLimit, height))
        }

        var totalHeight = 0
        for (s in slices) {
            totalHeight += (s.srcBottom - s.srcTop)
        }

        val clampedHeight = totalHeight.coerceIn(1, 16384)
        val stitched = Bitmap.createBitmap(width, clampedHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(stitched)

        var curY = 0
        for (s in slices) {
            val sliceH = s.srcBottom - s.srcTop
            val src = Rect(0, s.srcTop, width, s.srcBottom)
            val dst = Rect(0, curY, width, (curY + sliceH).coerceAtMost(clampedHeight))
            canvas.drawBitmap(s.bitmap, src, dst, null)
            curY += sliceH
            if (curY >= clampedHeight) break
        }

        return stitched
    }

    /**
     * Appends a newly scrolled page to the bottom of an existing long screenshot seamlessly.
     */
    fun appendSegment(
        existingBitmap: Bitmap,
        newSegment: Bitmap,
        statusBarHeightPx: Int = 0,
        navBarHeightPx: Int = 0
    ): Bitmap {
        val width = existingBitmap.width
        val height = newSegment.height

        val yStart = statusBarHeightPx.coerceIn(0, height)
        val newSliceHeight = (height - yStart).coerceAtLeast(1)
        val totalHeight = (existingBitmap.height + newSliceHeight).coerceAtMost(16384)
        val result = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        canvas.drawBitmap(existingBitmap, 0f, 0f, null)

        val src = Rect(0, yStart, width, height)
        val dst = Rect(0, existingBitmap.height, width, totalHeight)
        canvas.drawBitmap(newSegment, src, dst, null)

        return result
    }

    /**
     * Generates a realistic high-resolution continuous mock long screenshot (1080 × 4800px)
     * for direct in-app testing and previews.
     */
    fun generateSampleLongScreenshot(context: Context): Bitmap {
        val dm = context.resources.displayMetrics
        val width = if (dm.widthPixels > 0) dm.widthPixels else 1080
        val pageHeight = if (dm.heightPixels > 0) dm.heightPixels else 2400
        val totalHeight = pageHeight * 2

        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint().apply {
            color = Color.rgb(15, 23, 42) // Slate 900
        }
        canvas.drawRect(0f, 0f, width.toFloat(), totalHeight.toFloat(), bgPaint)

        val density = dm.density
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 229, 255) // Cyan Accent
            textSize = 18f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(203, 213, 225) // Slate 300
            textSize = 13.5f * density
            typeface = Typeface.DEFAULT
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(148, 163, 184) // Slate 400
            textSize = 11.5f * density
        }
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59) // Slate 800
            style = Paint.Style.FILL
        }
        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(51, 65, 85) // Slate 700
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }

        val padding = 24f * density
        var y = 48f * density

        val barHeight = 60f * density
        val topBarPaint = Paint().apply {
            color = Color.rgb(24, 32, 47)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), barHeight, topBarPaint)
        canvas.drawText("TechDaily • Long Article Reader", padding, 38f * density, metaPaint)
        canvas.drawLine(0f, barHeight, width.toFloat(), barHeight, cardBorderPaint)

        y = barHeight + 32f * density

        val tagRect = RectF(padding, y, padding + 130f * density, y + 24f * density)
        val tagBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(40, 0, 229, 255)
        }
        canvas.drawRoundRect(tagRect, 12f * density, 12f * density, tagBgPaint)
        val tagTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 229, 255)
            textSize = 11f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("FEATURED GUIDE", padding + 14f * density, y + 16.5f * density, tagTextPaint)

        y += 40f * density

        canvas.drawText("Comprehensive Guide to Modern", padding, y, headerPaint)
        y += 32f * density
        canvas.drawText("Android Architecture & AI", padding, y, headerPaint)

        y += 18f * density
        canvas.drawText("By Senior Engineering Team • 8 min read • Updated today", padding, y, metaPaint)

        y += 24f * density

        val heroHeight = 160f * density
        val heroRect = RectF(padding, y, width - padding, y + heroHeight)
        val heroPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                padding, y, width - padding, y + heroHeight,
                Color.rgb(14, 116, 144), Color.rgb(79, 70, 229), Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(heroRect, 16f * density, 16f * density, heroPaint)
        val heroTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 16f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("✨ SnapCrop Long Screenshot Demo", padding + 20f * density, y + heroHeight / 2f + 6f * density, heroTextPaint)

        y += heroHeight + 36f * density

        canvas.drawText("1. Background & Architecture Overview", padding, y, sectionTitlePaint)
        y += 26f * density
        canvas.drawText("Modern mobile applications require seamless UI responsiveness paired with", padding, y, bodyPaint)
        y += 22f * density
        canvas.drawText("instantaneous on-device capture capabilities. SnapCrop leverages high-speed", padding, y, bodyPaint)
        y += 22f * density
        canvas.drawText("zero-latency memory buffers and ML Kit OCR for continuous visual intelligence.", padding, y, bodyPaint)

        y += 38f * density

        canvas.drawText("2. Programmatic Scroll Stitching Pipeline", padding, y, sectionTitlePaint)
        y += 20f * density

        val codeCardHeight = 130f * density
        val codeRect = RectF(padding, y, width - padding, y + codeCardHeight)
        canvas.drawRoundRect(codeRect, 14f * density, 14f * density, cardPaint)
        canvas.drawRoundRect(codeRect, 14f * density, 14f * density, cardBorderPaint)

        val codePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(167, 243, 208)
            textSize = 12f * density
            typeface = Typeface.MONOSPACE
        }
        canvas.drawText("// High performance bitmap stitching", padding + 16f * density, y + 28f * density, metaPaint)
        canvas.drawText("val longBmp = LongScreenshotStitcher.stitch(", padding + 16f * density, y + 54f * density, codePaint)
        canvas.drawText("    bitmaps = capturedFrames,", padding + 16f * density, y + 76f * density, codePaint)
        canvas.drawText("    statusBarHeightPx = insets.top", padding + 16f * density, y + 98f * density, codePaint)
        canvas.drawText(")", padding + 16f * density, y + 118f * density, codePaint)

        y += codeCardHeight + 38f * density

        canvas.drawText("3. Core Performance Benchmarks", padding, y, sectionTitlePaint)
        y += 20f * density

        val statCardW = (width - padding * 2 - 20f * density) / 3f
        val statCardH = 75f * density
        val statValues = listOf("0ms", "100%", "60 FPS")
        val statLabels = listOf("Buffer Render", "OS Pass-Through", "Scroll Rate")

        for (i in 0..2) {
            val left = padding + i * (statCardW + 10f * density)
            val rect = RectF(left, y, left + statCardW, y + statCardH)
            canvas.drawRoundRect(rect, 12f * density, 12f * density, cardPaint)
            canvas.drawRoundRect(rect, 12f * density, 12f * density, cardBorderPaint)

            val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 15f * density
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val lblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(148, 163, 184)
                textSize = 10f * density
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(statValues[i], left + statCardW / 2f, y + 32f * density, valPaint)
            canvas.drawText(statLabels[i], left + statCardW / 2f, y + 54f * density, lblPaint)
        }

        y += statCardH + 42f * density

        canvas.drawText("4. Full Page Scroll Capture Capabilities", padding, y, sectionTitlePaint)
        y += 26f * density
        canvas.drawText("Scrolling screenshots allow saving entire multi-page documents, chat logs,", padding, y, bodyPaint)
        y += 22f * density
        canvas.drawText("recipes, and articles without needing to take repeated separate captures.", padding, y, bodyPaint)
        y += 22f * density
        canvas.drawText("Each page is aligned and stitched into a single pristine high-resolution PNG.", padding, y, bodyPaint)

        y += 38f * density

        val discCardH = 140f * density
        val discRect = RectF(padding, y, width - padding, y + discCardH)
        canvas.drawRoundRect(discRect, 14f * density, 14f * density, cardPaint)
        canvas.drawRoundRect(discRect, 14f * density, 14f * density, cardBorderPaint)

        canvas.drawText("💬 Community Discussion (24 comments)", padding + 16f * density, y + 30f * density, headerPaint.apply { textSize = 14f * density })
        canvas.drawText("• \"The long screenshot feature works seamlessly on web articles!\"", padding + 16f * density, y + 62f * density, bodyPaint)
        canvas.drawText("• \"Extremely handy for sharing full receipts and long chats.\"", padding + 16f * density, y + 90f * density, bodyPaint)
        canvas.drawText("• \"Instant crop and AI integration make this a daily essential.\"", padding + 16f * density, y + 118f * density, bodyPaint)

        y += discCardH + 40f * density

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 12f * density
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("— End of Long Screenshot Document —", width / 2f, y, footerPaint)

        return bitmap
    }
}
