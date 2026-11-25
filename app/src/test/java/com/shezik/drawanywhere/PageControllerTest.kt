package com.shezik.drawanywhere

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.shezik.drawanywhere.model.PenConfig
import com.shezik.drawanywhere.model.TextItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PageControllerTest {

    private fun DrawController.drawLine() {
        createStroke(Offset(1f, 1f)); updateLatestStroke(Offset(9f, 9f)); finishStroke()
    }

    private fun text(id: Long) =
        TextItem(id = id, text = "Folie $id", x = 0f, y = 0f, textSize = 40f, color = Color.Red)

    @Test
    fun nextPage_isEmpty_previousPageRestored() = runTest {
        val c = DrawController(PenConfig())
        c.drawLine()
        c.commitTextChange(null, text(1))

        c.goToPage(1)
        assertEquals(1, c.pageIndex.first())
        assertTrue(c.strokeList.isEmpty())
        assertTrue(c.textList.isEmpty())
        assertFalse(c.canUndo.first())
        assertFalse(c.canClearStrokes.first())

        c.commitTextChange(null, text(2))
        c.goToPage(0)
        assertEquals(1, c.strokeList.size)
        assertEquals(listOf(text(1)), c.textList)

        c.goToPage(1)
        assertEquals(listOf(text(2)), c.textList)
    }

    @Test
    fun undoHistory_isPerPage() = runTest {
        val c = DrawController(PenConfig())
        c.drawLine()
        c.goToPage(1)
        c.commitTextChange(null, text(2))
        c.goToPage(0)
        assertTrue(c.canUndo.first())
        c.undo()
        assertTrue(c.strokeList.isEmpty())
        c.goToPage(1)
        assertEquals(listOf(text(2)), c.textList)
        c.undo()
        assertTrue(c.textList.isEmpty())
        c.redo()
        assertEquals(listOf(text(2)), c.textList)
    }

    @Test
    fun clear_onlyAffectsCurrentPage() {
        val c = DrawController(PenConfig())
        c.commitTextChange(null, text(1))
        c.goToPage(1)
        c.commitTextChange(null, text(2))
        c.clearStrokes()
        assertTrue(c.textList.isEmpty())
        c.goToPage(0)
        assertEquals(listOf(text(1)), c.textList)
    }

    @Test
    fun negativePage_ignored() = runTest {
        val c = DrawController(PenConfig())
        c.goToPage(-1)
        assertEquals(0, c.pageIndex.first())
    }
}
