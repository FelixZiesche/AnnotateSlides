/*
DrawAnywhere: An Android application that lets you draw on top of other apps.
Copyright (C) 2025-2026 shezik

This program is free software: you can redistribute it and/or modify it under the
terms of the GNU Affero General Public License as published by the Free Software
Foundation, either version 3 of the License, or any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License along
with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package com.shezik.drawanywhere

import androidx.compose.ui.geometry.Offset
import com.shezik.drawanywhere.drawing.StrokeTool
import com.shezik.drawanywhere.drawing.ToolContext
import com.shezik.drawanywhere.model.DrawAction
import com.shezik.drawanywhere.model.Stroke
import com.shezik.drawanywhere.model.PenConfig
import com.shezik.drawanywhere.model.TextItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the stroke list and undo/redo stack. Drawing logic is delegated to
 * [StrokeTool] implementations via [PenType.createTool].
 */
class DrawController(initialConfig: PenConfig) {
    var penConfig: PenConfig = initialConfig
        private set

    fun setPenConfig(config: PenConfig) {
        penConfig = config
    }

    var onStrokesChanged: (() -> Unit)? = null

    private val _strokeList = mutableListOf<Stroke>()
    val strokeList: List<Stroke>
        get() = _strokeList

    private val _textList = mutableListOf<TextItem>()
    /** Text boxes, drawn on top of strokes in list order. */
    val textList: List<TextItem>
        get() = _textList

    private val undoRedo = UndoRedoManager()
    val canUndo: StateFlow<Boolean> = undoRedo.canUndo
    val canRedo: StateFlow<Boolean> = undoRedo.canRedo

    private val _canClear = MutableStateFlow(false)
    val canClearStrokes: StateFlow<Boolean> = _canClear.asStateFlow()

    private var activeTool: StrokeTool? = null

    // ── Slide pages ───────────────────────────────────────────────
    // Every slide gets its own strokes, texts and undo history. Pages that are not shown
    // are kept in memory (until the app is closed) so going back restores them.

    private data class PageState(
        val strokes: List<Stroke>,
        val texts: List<TextItem>,
        val history: Pair<List<DrawAction>, List<DrawAction>>,
    )

    private val savedPages = mutableMapOf<Int, PageState>()

    private val _pageIndex = MutableStateFlow(0)
    /** 0-based index of the slide page currently shown. */
    val pageIndex: StateFlow<Int> = _pageIndex.asStateFlow()

    /** Show page [index]: the current page is stored, the target restored (or empty). */
    fun goToPage(index: Int) {
        val current = _pageIndex.value
        if (index < 0 || index == current) return
        finishStroke()
        val keep = _strokeList.filter { !it.penType.isEphemeral }
        if (keep.isEmpty() && _textList.isEmpty() && !undoRedo.canUndo.value && !undoRedo.canRedo.value) {
            savedPages.remove(current)
        } else {
            savedPages[current] = PageState(keep, _textList.toList(), undoRedo.exportStacks())
        }
        val target = savedPages.remove(index)
        _strokeList.clear()
        _textList.clear()
        if (target != null) {
            _strokeList.addAll(target.strokes)
            _textList.addAll(target.texts)
        }
        undoRedo.importStacks(target?.history ?: (emptyList<DrawAction>() to emptyList()))
        _pageIndex.value = index
        notifyChanged()
    }

    /** True if page [index] has saved content (used for the page indicator). */
    fun pageHasContent(index: Int): Boolean =
        if (index == _pageIndex.value) _strokeList.isNotEmpty() || _textList.isNotEmpty()
        else savedPages[index]?.let { it.strokes.isNotEmpty() || it.texts.isNotEmpty() } == true

    private val toolContext get() = ToolContext(
        strokes = _strokeList,
        penConfig = penConfig,
        onUndoPush = undoRedo::push,
        onChanged = ::notifyChanged,
    )

    private fun notifyChanged() {
        _canClear.value = _strokeList.isNotEmpty() || _textList.isNotEmpty()
        onStrokesChanged?.invoke()
    }

    fun createStroke(newPoint: Offset) {
        val tool = penConfig.penType.createTool(toolContext)
        activeTool = tool
        tool.onStart(newPoint)
    }

    fun updateLatestStroke(newPoint: Offset) {
        activeTool?.onMove(newPoint)
    }

    fun finishStroke() {
        activeTool?.onFinish()
        activeTool = null
    }

    fun clearStrokes() {
        if (_strokeList.isEmpty() && _textList.isEmpty()) return
        undoRedo.push(DrawAction.ClearStrokes(_strokeList.toList(), _textList.toList()))
        _strokeList.clear()
        _textList.clear()
        notifyChanged()
    }

    // ── Text boxes ────────────────────────────────────────────────

    fun findText(id: Long): TextItem? = _textList.firstOrNull { it.id == id }

    /** Replace [before] with [after] in the text list (either may be null). */
    private fun applyTextChange(before: TextItem?, after: TextItem?) {
        val idx = before?.let { b -> _textList.indexOfFirst { it.id == b.id } } ?: -1
        when {
            after == null -> if (idx >= 0) _textList.removeAt(idx)
            idx >= 0 -> _textList[idx] = after
            else -> {
                // Also replace a stale entry with the same id (e.g. live preview)
                val existing = _textList.indexOfFirst { it.id == after.id }
                if (existing >= 0) _textList[existing] = after else _textList.add(after)
            }
        }
    }

    /**
     * Show [item] without recording an undo step (used while dragging).
     * Finish with [commitTextChange] using the original as `before`.
     */
    fun previewText(item: TextItem) {
        val idx = _textList.indexOfFirst { it.id == item.id }
        if (idx >= 0) _textList[idx] = item else _textList.add(item)
        notifyChanged()
    }

    /**
     * Apply a text change and record it as one undo step.
     * @return true if an undo step was recorded (false if nothing changed).
     */
    fun commitTextChange(before: TextItem?, after: TextItem?): Boolean {
        if (before == null && after == null) return false
        applyTextChange(before, after)
        val changed = before != after
        if (changed) undoRedo.push(DrawAction.TextChange(before, after))
        notifyChanged()
        return changed
    }

    fun removeExpiredStrokes(now: Long) {
        if (
            _strokeList.removeAll { stroke ->
                val ttl = stroke.penType.ttlMs ?: return@removeAll false
                now - stroke.modifiedAt > ttl
            }
        ) notifyChanged()
    }

    fun undo() {
        val action = undoRedo.popUndo() ?: return
        when (action) {
            is DrawAction.AddStroke -> {
                if (_strokeList.remove(action.stroke)) undoRedo.pushRedo(action)
            }
            is DrawAction.EraseStroke -> {
                _strokeList.add(action.stroke)
                undoRedo.pushRedo(action)
            }
            is DrawAction.ClearStrokes -> {
                _strokeList.addAll(action.strokes)
                _textList.addAll(action.texts)
                undoRedo.pushRedo(action)
            }
            is DrawAction.TextChange -> {
                applyTextChange(action.after, action.before)
                undoRedo.pushRedo(action)
            }
            is DrawAction.CanvasSnapshot -> {
                _strokeList.clear()
                _strokeList.addAll(action.before)
                undoRedo.pushRedo(action)
            }
        }
        notifyChanged()
    }

    fun redo() {
        val action = undoRedo.popRedo() ?: return
        when (action) {
            is DrawAction.AddStroke -> {
                _strokeList.add(action.stroke)
                undoRedo.push(action, clearRedo = false)
            }
            is DrawAction.EraseStroke -> {
                if (_strokeList.remove(action.stroke)) undoRedo.push(action, clearRedo = false)
            }
            is DrawAction.ClearStrokes -> {
                _strokeList.removeAll(action.strokes)
                _textList.removeAll(action.texts)
                undoRedo.push(action, clearRedo = false)
            }
            is DrawAction.TextChange -> {
                applyTextChange(action.before, action.after)
                undoRedo.push(action, clearRedo = false)
            }
            is DrawAction.CanvasSnapshot -> {
                _strokeList.clear()
                _strokeList.addAll(action.after)
                undoRedo.push(action, clearRedo = false)
            }
        }
        notifyChanged()
    }
}
