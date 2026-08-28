package com.example

import android.content.Context
import android.graphics.RectF
import android.util.AttributeSet

/**
 * CropOverlayView
 *
 * Backward-compatibility wrapper extending SelectionView to ensure compatibility
 * with existing references.
 */
class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SelectionView(context, attrs, defStyleAttr) {

    var onSelectionCompleted: ((RectF) -> Unit)? = null
    var onCancelRequested: (() -> Unit)? = null

    init {
        onSelectionChanged = { rect, isInteracting ->
            if (!isInteracting && rect != null) {
                onSelectionCompleted?.invoke(rect)
            }
        }
    }
}
