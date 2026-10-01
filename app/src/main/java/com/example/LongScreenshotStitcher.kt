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
import kotlin.math.max

/**
 * LongScreenshotStitcher
 *
 * Provides high-performance vertical image stitching for multi-page scroll capture.
 * Stitches consecutive screenshot viewports seamlessly by trimming status bar and navigation
 * bar overlays from intermediate frames, preserving top header and bottom footer intact.
 * Also generates realistic multi-section mock long screenshots for direct in-app testing.
 */
object LongScreenshotStitcher {

    private const val TAG = "LongScreenshotStitcher"

    /**
     * Stitches an ordered list of screenshot bitmaps into a single continuous long bitmap.
     *
     * @param bitmaps Sequential frames captured while scrolling down.
     * @param statusBarHeightPx Height of system status bar to trim from intermediate frames.
     * @param navBarHeightPx Height of navigation bar to trim from intermediate frames.
     * @return Stitched continuous bitmap.
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
        val safeStatusBar = statusBarHeightPx.coerceAtLeast(0)
        val safeNavBar = navBarHeightPx.coerceAtLeast(0)

        // Calculate slice heights for each frame:
        // Frame 0: keep top (0 to height - navBar)
        // Middle frames (1 .. n-2): keep center (statusBar to height - navBar)
        // Last frame (n-1): keep bottom (statusBar to height)
        val sliceRects = mutableListOf<Rect>()
        var totalHeight = 0

        for (i in bitmaps.indices) {
            val bmp = bitmaps[i]
            val top = when (i) {
                0 -> 0
                else -> safeStatusBar.coerceAtMost(bmp.height / 3)
            }
            val bottom = when (i) {
                bitmaps.lastIndex -> bmp.height
                else -> (bmp.height - safeNavBar).coerceAtLeast(top + 10)
            }
            val sliceHeight = max(1, bottom - top)
            sliceRects.add(Rect(0, top, width, bottom))
            totalHeight += sliceHeight
        }

        // Limit total height to safe max texture dimension (e.g. 16384px) to prevent OOM
        val clampedHeight = totalHeight.coerceIn(1, 16384)
        val stitched = Bitmap.createBitmap(width, clampedHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(stitched)

        var currentY = 0
        val dstRect = Rect()

        for (i in bitmaps.indices) {
            val src = sliceRects[i]
            val sliceH = src.height()
            dstRect.set(0, currentY, width, (currentY + sliceH).coerceAtMost(clampedHeight))
            canvas.drawBitmap(bitmaps[i], src, dstRect, null)
            currentY += sliceH
            if (currentY >= clampedHeight) break
        }

        return stitched
    }

    /**
     * Appends a newly scrolled page to the bottom of an existing long screenshot.
     */
    fun appendSegment(
        existingBitmap: Bitmap,
        newSegment: Bitmap,
        statusBarHeightPx: Int = 0,
        navBarHeightPx: Int = 0
    ): Bitmap {
        val width = existingBitmap.width
        val safeStatusBar = statusBarHeightPx.coerceAtLeast(0).coerceAtMost(newSegment.height / 3)
        val newSliceHeight = max(1, newSegment.height - safeStatusBar)

        val totalHeight = (existingBitmap.height + newSliceHeight).coerceAtMost(16384)
        val result = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // Draw existing bitmap
        canvas.drawBitmap(existingBitmap, 0f, 0f, null)

        // Draw new segment below existing
        val src = Rect(0, safeStatusBar, newSegment.width, newSegment.height)
        val dst = Rect(0, existingBitmap.height, width, totalHeight)
        canvas.drawBitmap(newSegment, src, dst, null)

        return result
    }

    /**
     * Generates a realistic high-resolution 3-page continuous mock long screenshot (1080 × 3600px)
     * for direct in-app testing and previews.
     */
    fun generateSampleLongScreenshot(context: Context): Bitmap {
        val dm = context.resources.displayMetrics
        val width = if (dm.widthPixels > 0) dm.widthPixels else 1080
        val pageHeight = if (dm.heightPixels > 0) dm.heightPixels else 2400
        val totalHeight = pageHeight * 2 // 2 full scrollable viewports (e.g. 4800px)

        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        val bgPaint = Paint().apply {
            color = Color.rgb(15, 23, 42) // Slate 900
        }
        canvas.drawRect(0f, 0f, width.toFloat(), totalHeight.toFloat(), bgPaint)

        // Paints
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

        // 1. Top App / Browser Bar
        val barHeight = 60f * density
        val topBarPaint = Paint().apply {
            color = Color.rgb(24, 32, 47)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), barHeight, topBarPaint)
        canvas.drawText("TechDaily • Long Article Reader", padding, 38f * density, metaPaint)
        canvas.drawLine(0f, barHeight, width.toFloat(), barHeight, cardBorderPaint)

        y = barHeight + 32f * density

        // Category Tag
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

        // Article Title
        canvas.drawText("Comprehensive Guide to Modern", padding, y, headerPaint)
        y += 32f * density
        canvas.drawText("Android Architecture & AI", padding, y, headerPaint)

        y += 18f * density
        canvas.drawText("By Senior Engineering Team • 8 min read • Updated today", padding, y, metaPaint)

        y += 24f * density

        // Hero Illustration Card
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

        // Section 1: Introduction
        canvas.drawText("1. Background & Architecture Overview", padding, y, sectionTitlePaint)
        y += 26f * density
        canvas.drawText("Modern mobile applications require seamless UI responsiveness paired with", padding, y, bodyPaint)
        y += 22f * density
        canvas.drawText("instantaneous on-device capture capabilities. SnapCrop leverages high-speed", padding, y, bodyPaint)
        y += 22f * density
        canvas.drawText("zero-latency memory buffers and ML Kit OCR for continuous visual intelligence.", padding, y, bodyPaint)

        y += 38f * density

        // Section 2: Code Snippet Card
        canvas.drawText("2. Programmatic Scroll Stitching Pipeline", padding, y, sectionTitlePaint)
        y += 20f * density

        val codeCardHeight = 130f * density
        val codeRect = RectF(padding, y, width - padding, y + codeCardHeight)
        canvas.drawRoundRect(codeRect, 14f * density, 14f * density, cardPaint)
        canvas.drawRoundRect(codeRect, 14f * density, 14f * density, cardBorderPaint)

        val codePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(167, 243, 208) // Mint
            textSize = 12f * density
            typeface = Typeface.MONOSPACE
        }
        canvas.drawText("// High performance bitmap stitching", padding + 16f * density, y + 28f * density, metaPaint)
        canvas.drawText("val longBmp = LongScreenshotStitcher.stitch(", padding + 16f * density, y + 54f * density, codePaint)
        canvas.drawText("    bitmaps = capturedFrames,", padding + 16f * density, y + 76f * density, codePaint)
        canvas.drawText("    statusBarHeightPx = insets.top", padding + 16f * density, y + 98f * density, codePaint)
        canvas.drawText(")", padding + 16f * density, y + 118f * density, codePaint)

        y += codeCardHeight + 38f * density

        // Section 3: Performance Metrics
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

        // Section 4: Deep Insights & Continuous Page 2 Content
        canvas.drawText("4. Full Page Scroll Capture Capabilities", padding, y, sectionTitlePaint)
        y += 26f * density
        canvas.drawText("Scrolling screenshots allow saving entire multi-page documents, chat logs,", padding, y, bodyPaint)
        y += 22f * density
        canvas.drawText("recipes, and articles without needing to take repeated separate captures.", padding, y, bodyPaint)
        y += 22f * density
        canvas.drawText("Each page is aligned and stitched into a single pristine high-resolution PNG.", padding, y, bodyPaint)

        y += 38f * density

        // Discussion Card
        val discCardH = 140f * density
        val discRect = RectF(padding, y, width - padding, y + discCardH)
        canvas.drawRoundRect(discRect, 14f * density, 14f * density, cardPaint)
        canvas.drawRoundRect(discRect, 14f * density, 14f * density, cardBorderPaint)

        canvas.drawText("💬 Community Discussion (24 comments)", padding + 16f * density, y + 30f * density, headerPaint.apply { textSize = 14f * density })
        canvas.drawText("• \"The long screenshot feature works seamlessly on web articles!\"", padding + 16f * density, y + 62f * density, bodyPaint)
        canvas.drawText("• \"Extremely handy for sharing full receipts and long chats.\"", padding + 16f * density, y + 90f * density, bodyPaint)
        canvas.drawText("• \"Instant crop and AI integration make this a daily essential.\"", padding + 16f * density, y + 118f * density, bodyPaint)

        y += discCardH + 40f * density

        // Footer
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 12f * density
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("— End of Long Screenshot Document —", width / 2f, y, footerPaint)

        return bitmap
    }
}
