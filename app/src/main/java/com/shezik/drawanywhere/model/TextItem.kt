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

package com.shezik.drawanywhere.model

import androidx.compose.ui.graphics.Color

/**
 * A text box placed on the canvas by the text tool.
 *
 * Immutable: every edit, move or style change produces a new instance with the same [id],
 * so undo/redo can store before/after snapshots.
 *
 * @property x left edge of the box in world (canvas) coordinates
 * @property y top edge of the box in world (canvas) coordinates
 * @property textSize font size in world pixels
 * @property background draw a semi-transparent white box behind the text
 */
data class TextItem(
    val id: Long,
    val text: String,
    val x: Float,
    val y: Float,
    val textSize: Float,
    val color: Color,
    val alpha: Float = 1f,
    val background: Boolean = false,
) {
    /** Inner padding between box edge and text, in world pixels. */
    val padding: Float get() = textSize * PADDING_FACTOR

    companion object {
        const val PADDING_FACTOR = 0.25f
        private var nextId = System.currentTimeMillis()
        fun newId(): Long = nextId++
    }
}
