package com.shezik.drawanywhere

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SlideNumberTest {
    @Test
    fun readsSlideCounters() {
        assertEquals(3, SlideNumber.parse("Folie 3 von 12"))
        assertEquals(3, SlideNumber.parse("Slide 3 of 12"))
        assertEquals(7, SlideNumber.parse("7 / 20"))
        assertEquals(7, SlideNumber.parse("7/20"))
        assertEquals(1, SlideNumber.parse("Seite 1 von 1"))
    }

    @Test
    fun ignoresOtherTexts() {
        assertNull(SlideNumber.parse(null))
        assertNull(SlideNumber.parse(""))
        assertNull(SlideNumber.parse("Folie 13 von 12"))
        assertNull(SlideNumber.parse("Am 3/12 ist Abgabe"))
        assertNull(SlideNumber.parse("Präsentation"))
    }

    @Test
    fun onlyUnambiguousNumberCounts() {
        assertEquals(4, SlideNumber.pick(listOf(4, 4)))
        assertNull(SlideNumber.pick(listOf(1, 2, 3)))
        assertNull(SlideNumber.pick(emptyList()))
    }
}
