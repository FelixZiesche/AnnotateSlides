package com.shezik.drawanywhere

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.shezik.drawanywhere.model.PenConfig
import com.shezik.drawanywhere.model.TextItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TextItemControllerTest {

    private fun text(id: Long = 1L, s: String = "Hallo Ä", x: Float = 10f) =
        TextItem(id = id, text = s, x = x, y = 20f, textSize = 50f, color = Color.Red)

    @Test
    fun addText_undo_redo() = runTest {
        val c = DrawController(PenConfig())
        val t = text()
        assertTrue(c.commitTextChange(null, t))
        assertEquals(listOf(t), c.textList)
        assertTrue(c.canClearStrokes.first())

        c.undo()
        assertTrue(c.textList.isEmpty())
        assertFalse(c.canClearStrokes.first())

        c.redo()
        assertEquals(listOf(t), c.textList)
    }

    @Test
    fun editAndMove_areSeparateUndoSteps() {
        val c = DrawController(PenConfig())
        val t = text()
        c.commitTextChange(null, t)
        val edited = t.copy(text = "Hallo Welt\nZeile 2")
        c.commitTextChange(t, edited)
        val moved = edited.copy(x = 300f)
        c.previewText(moved.copy(x = 150f))
        c.previewText(moved)
        c.commitTextChange(edited, moved)

        assertEquals(listOf(moved), c.textList)
        c.undo()
        assertEquals(listOf(edited), c.textList)
        c.undo()
        assertEquals(listOf(t), c.textList)
        c.undo()
        assertTrue(c.textList.isEmpty())
    }

    @Test
    fun deleteText_undoRestores() {
        val c = DrawController(PenConfig())
        val t = text()
        c.commitTextChange(null, t)
        c.commitTextChange(t, null)
        assertTrue(c.textList.isEmpty())
        c.undo()
        assertEquals(listOf(t), c.textList)
    }

    @Test
    fun unchangedCommit_recordsNoUndoStep() = runTest {
        val c = DrawController(PenConfig())
        val t = text()
        c.commitTextChange(null, t)
        assertFalse(c.commitTextChange(t, t))
        c.undo()
        assertTrue(c.textList.isEmpty())
        assertFalse(c.canUndo.first())
    }

    @Test
    fun clear_removesTextsAndStrokes_undoRestoresBoth() {
        val c = DrawController(PenConfig())
        c.createStroke(Offset(1f, 1f)); c.updateLatestStroke(Offset(5f, 5f)); c.finishStroke()
        val a = text(1L); val b = text(2L, "B")
        c.commitTextChange(null, a)
        c.commitTextChange(null, b)

        c.clearStrokes()
        assertTrue(c.textList.isEmpty())
        assertTrue(c.strokeList.isEmpty())

        c.undo()
        assertEquals(listOf(a, b), c.textList)
        assertEquals(1, c.strokeList.size)

        c.redo()
        assertTrue(c.textList.isEmpty())
    }

    @Test
    fun clear_withOnlyTexts_works() {
        val c = DrawController(PenConfig())
        c.commitTextChange(null, text())
        c.clearStrokes()
        assertTrue(c.textList.isEmpty())
        c.undo()
        assertEquals(1, c.textList.size)
    }
}
