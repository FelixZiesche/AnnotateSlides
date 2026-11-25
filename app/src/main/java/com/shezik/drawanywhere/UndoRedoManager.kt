package com.shezik.drawanywhere

import com.shezik.drawanywhere.model.DrawAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UndoRedoManager(private val maxDepth: Int = 50) {

    private val undoStack = mutableListOf<DrawAction>()
    private val redoStack = mutableListOf<DrawAction>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    internal fun push(action: DrawAction, clearRedo: Boolean = true) {
        val filtered = action.withoutEphemeral() ?: return
        if (clearRedo) redoStack.clear()
        undoStack.add(filtered)
        if (undoStack.size > maxDepth) {
            undoStack.removeAt(0)
        }
        updateUndoRedoState()
    }

    /** Snapshot of both stacks (used when switching slide pages). */
    internal fun exportStacks(): Pair<List<DrawAction>, List<DrawAction>> =
        undoStack.toList() to redoStack.toList()

    /** Replace both stacks, e.g. with a page's saved history (empty for a new page). */
    internal fun importStacks(stacks: Pair<List<DrawAction>, List<DrawAction>>) {
        undoStack.clear(); undoStack.addAll(stacks.first)
        redoStack.clear(); redoStack.addAll(stacks.second)
        updateUndoRedoState()
    }

    internal fun popUndo(): DrawAction? {
        if (undoStack.isEmpty()) return null
        val action = undoStack.removeAt(undoStack.lastIndex)
        updateUndoRedoState()
        return action
    }

    internal fun pushRedo(action: DrawAction) {
        redoStack.add(action)
        updateUndoRedoState()
    }

    internal fun popRedo(): DrawAction? {
        if (redoStack.isEmpty()) return null
        val action = redoStack.removeAt(redoStack.lastIndex)
        updateUndoRedoState()
        return action
    }

    private fun updateUndoRedoState() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }
}
