package com.shezik.drawanywhere.drawing

import androidx.compose.ui.geometry.Offset

/**
 * Lifecycle of a single stroke gesture: start → move (zero or more) → finish.
 *
 * Tools are instantiated per-gesture by [com.shezik.drawanywhere.model.PenType.createTool].
 * The same [ToolContext] is shared across all tools.
 */
interface StrokeTool {
    fun onStart(point: Offset)
    fun onMove(point: Offset)
    fun onFinish()
}

/** Tool that does nothing; used by pen types that do not draw strokes (e.g. text). */
object NoOpTool : StrokeTool {
    override fun onStart(point: Offset) {}
    override fun onMove(point: Offset) {}
    override fun onFinish() {}
}
