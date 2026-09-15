package com.example

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * AiModelPreferenceManager
 *
 * Persists the user's preferred AI model for sharing cropped screenshots.
 * Default is GEMINI. Supports Google Search, ChatGPT and Claude when installed.
 */
object AiModelPreferenceManager {
    private const val PREFS_NAME = "snapcrop_ai_prefs"
    private const val KEY_SELECTED_AI = "selected_ai_model"

    enum class AiModel(
        val id: String,
        val displayName: String,
        val description: String,
        val packageName: String,
        val fallbackUrl: String
    ) {
        GEMINI(
            id = "gemini",
            displayName = "Google Gemini",
            description = "Default • Explain, summarize, or solve anything",
            packageName = "com.google.android.apps.bard",
            fallbackUrl = "https://gemini.google.com/"
        ),
        GOOGLE_SEARCH(
            id = "google_search",
            displayName = "Google Search",
            description = "Visual search, Lens & identify anything on screen",
            packageName = "com.google.android.googlequicksearchbox",
            fallbackUrl = "https://lens.google.com/"
        ),
        CHATGPT(
            id = "chatgpt",
            displayName = "ChatGPT",
            description = "Ask questions, analyze images & chat with GPT",
            packageName = "com.openai.chatgpt",
            fallbackUrl = "https://chatgpt.com/"
        ),
        CLAUDE(
            id = "claude",
            displayName = "Claude",
            description = "Analyze code, documents & visual reasoning with Claude",
            packageName = "com.anthropic.claude",
            fallbackUrl = "https://claude.ai/"
        );

        companion object {
            fun fromId(id: String?): AiModel {
                return entries.find { it.id.equals(id, ignoreCase = true) } ?: GEMINI
            }
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSelectedModel(context: Context): AiModel {
        val id = getPrefs(context).getString(KEY_SELECTED_AI, AiModel.GEMINI.id)
        return AiModel.fromId(id)
    }

    fun setSelectedModel(context: Context, model: AiModel) {
        getPrefs(context).edit().putString(KEY_SELECTED_AI, model.id).apply()
    }

    fun isModelInstalled(context: Context, model: AiModel): Boolean {
        return when (model) {
            AiModel.GEMINI -> {
                isPackageInstalled(context, model.packageName) ||
                        isPackageInstalled(context, "com.google.android.googlequicksearchbox")
            }
            AiModel.GOOGLE_SEARCH -> {
                isPackageInstalled(context, "com.google.android.googlequicksearchbox") ||
                        isPackageInstalled(context, "com.google.ar.lens")
            }
            AiModel.CHATGPT, AiModel.CLAUDE -> {
                isPackageInstalled(context, model.packageName)
            }
        }
    }

    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (e: Exception) {
            try {
                context.packageManager.getLaunchIntentForPackage(packageName) != null
            } catch (ex: Exception) {
                false
            }
        }
    }

    fun openPlayStoreOrWeb(context: Context, model: AiModel) {
        try {
            val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${model.packageName}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(playStoreIntent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(model.fallbackUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }
}

/**
 * Custom vector canvas for ChatGPT icon.
 */
@Composable
fun ChatGptIcon(
    modifier: Modifier = Modifier.size(22.dp),
    tint: Color = Color(0xFF10A37F)
) {
    ComposeCanvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.width * 0.40f

        // Center hub circle
        drawCircle(
            color = tint,
            radius = size.width * 0.14f,
            center = Offset(cx, cy)
        )

        // 6 symmetric looping petals
        val petalStroke = 1.8f * density
        for (i in 0 until 6) {
            val angle = Math.toRadians(i * 60.0)
            val px = cx + (r * 0.52f * Math.cos(angle)).toFloat()
            val py = cy + (r * 0.52f * Math.sin(angle)).toFloat()
            drawCircle(
                color = tint,
                radius = r * 0.42f,
                center = Offset(px, py),
                style = Stroke(width = petalStroke)
            )
        }
    }
}

/**
 * Custom vector canvas for Claude icon (Anthropic sunburst).
 */
@Composable
fun ClaudeIcon(
    modifier: Modifier = Modifier.size(22.dp),
    tint: Color = Color(0xFFD97706)
) {
    ComposeCanvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val rOuter = size.width * 0.44f
        val rInner = size.width * 0.16f
        val strokeWidth = 2.4f * density

        // Draw 8 radial spokes of Claude's sunburst asterisk
        for (i in 0 until 8) {
            val angle = Math.toRadians(i * 45.0)
            val startX = cx + (rInner * Math.cos(angle)).toFloat()
            val startY = cy + (rInner * Math.sin(angle)).toFloat()
            val endX = cx + (rOuter * Math.cos(angle)).toFloat()
            val endY = cy + (rOuter * Math.sin(angle)).toFloat()
            drawLine(
                color = tint,
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
        drawCircle(
            color = tint,
            radius = rInner * 0.8f,
            center = Offset(cx, cy)
        )
    }
}

/**
 * Custom vector canvas for Google Search / Lens icon with Google's iconic 4-color palette.
 */
@Composable
fun GoogleSearchIcon(
    modifier: Modifier = Modifier.size(22.dp)
) {
    ComposeCanvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeW = 2.2f * density
        val pad = w * 0.12f
        val cornerLen = w * 0.26f

        val blue = Color(0xFF4285F4)
        val red = Color(0xFFEA4335)
        val yellow = Color(0xFFFBBC05)
        val green = Color(0xFF34A853)

        // Top-Left corner bracket (Blue)
        val tlPath = Path().apply {
            moveTo(pad, pad + cornerLen)
            lineTo(pad, pad + 4f * density)
            quadraticTo(pad, pad, pad + 4f * density, pad)
            lineTo(pad + cornerLen, pad)
        }
        drawPath(tlPath, blue, style = Stroke(strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Top-Right corner bracket (Red)
        val trPath = Path().apply {
            moveTo(w - pad - cornerLen, pad)
            lineTo(w - pad - 4f * density, pad)
            quadraticTo(w - pad, pad, w - pad, pad + 4f * density)
            lineTo(w - pad, pad + cornerLen)
        }
        drawPath(trPath, red, style = Stroke(strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Bottom-Right corner bracket (Green)
        val brPath = Path().apply {
            moveTo(w - pad, h - pad - cornerLen)
            lineTo(w - pad, h - pad - 4f * density)
            quadraticTo(w - pad, h - pad, w - pad - 4f * density, h - pad)
            lineTo(w - pad - cornerLen, h - pad)
        }
        drawPath(brPath, green, style = Stroke(strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Bottom-Left corner bracket (Yellow)
        val blPath = Path().apply {
            moveTo(pad + cornerLen, h - pad)
            lineTo(pad + 4f * density, h - pad)
            quadraticTo(pad, h - pad, pad, h - pad - 4f * density)
            lineTo(pad, h - pad - cornerLen)
        }
        drawPath(blPath, yellow, style = Stroke(strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Center Lens pupil
        drawCircle(
            color = blue,
            radius = w * 0.18f,
            center = Offset(w / 2f, h / 2f),
            style = Stroke(strokeW)
        )
        // Red dot in lens
        drawCircle(
            color = red,
            radius = w * 0.08f,
            center = Offset(w / 2f, h / 2f)
        )
    }
}

