/*
Annotate Slides: a fork of DrawAnywhere with a keyboard text tool.
Copyright (C) 2025-2026 shezik (DrawAnywhere)
Copyright (C) 2026 FelixZiesche (text tool and modifications)

This program is free software: you can redistribute it and/or modify it under the
terms of the GNU Affero General Public License as published by the Free Software
Foundation, either version 3 of the License, or any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License along
with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.shezik.drawanywhere.view.canvas

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import com.shezik.drawanywhere.model.TextItem
import kotlin.math.ceil

/**
 * Draws [TextItem]s onto the canvas (in world coordinates) and hit-tests them.
 * Uses the same typeface and metrics as the edit box in [TextEditLayer] so that
 * text does not jump when editing starts or ends.
 */
class TextBoxRenderer {
    companion object {
        val TYPEFACE: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        /** 70 % white behind the text for readability on busy slides. */
        const val BACKGROUND_COLOR = 0xB3_FFFFFF.toInt()
        const val CORNER_FACTOR = 0.6f
    }

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = TYPEFACE }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = BACKGROUND_COLOR
    }

    private val cache = HashMap<TextItem, StaticLayout>()

    private fun layoutFor(item: TextItem): StaticLayout = cache.getOrPut(item) {
        val paint = TextPaint(textPaint).apply { textSize = item.textSize }
        val width = ceil(Layout.getDesiredWidth(item.text, paint)).toInt().coerceAtLeast(1)
        StaticLayout.Builder.obtain(item.text, 0, item.text.length, paint, width)
            .setIncludePad(false)
            .setLineSpacing(0f, 1f)
            .build()
    }

    /** Box bounds (including padding) in world coordinates. */
    fun bounds(item: TextItem): RectF {
        val l = layoutFor(item)
        val pad = item.padding
        return RectF(item.x, item.y, item.x + l.width + 2 * pad, item.y + l.height + 2 * pad)
    }

    fun draw(canvas: Canvas, item: TextItem) {
        if (item.text.isEmpty()) return
        val l = layoutFor(item)
        val pad = item.padding
        if (item.background) {
            val r = pad * CORNER_FACTOR
            canvas.drawRoundRect(bounds(item), r, r, bgPaint)
        }
        val combinedAlpha = (item.color.alpha * item.alpha).coerceIn(0f, 1f)
        l.paint.color = item.color.copy(alpha = combinedAlpha).toArgb()
        canvas.save()
        canvas.translate(item.x + pad, item.y + pad)
        l.draw(canvas)
        canvas.restore()
    }

    /** Topmost item whose box contains [point] (grown by [tolerance] world px). */
    fun hitTest(items: List<TextItem>, point: Offset, tolerance: Float): TextItem? =
        items.lastOrNull { item ->
            if (item.text.isEmpty()) return@lastOrNull false
            val b = bounds(item)
            b.inset(-tolerance, -tolerance)
            b.contains(point.x, point.y)
        }

    /** Drop cached layouts for items that no longer exist in this exact form. */
    fun trim(items: List<TextItem>) {
        if (cache.size > items.size + 8) cache.keys.retainAll(items.toSet())
    }
}
