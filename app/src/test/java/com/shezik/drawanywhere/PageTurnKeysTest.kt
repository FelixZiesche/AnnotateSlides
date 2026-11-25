package com.shezik.drawanywhere

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class PageTurnKeysTest {
    @Test
    fun forwardAndBackKeys() {
        assertEquals(1, PageTurnAccessibilityService.directionFor(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(1, PageTurnAccessibilityService.directionFor(KeyEvent.KEYCODE_PAGE_DOWN))
        assertEquals(-1, PageTurnAccessibilityService.directionFor(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(-1, PageTurnAccessibilityService.directionFor(KeyEvent.KEYCODE_PAGE_UP))
        assertEquals(0, PageTurnAccessibilityService.directionFor(KeyEvent.KEYCODE_A))
        assertEquals(0, PageTurnAccessibilityService.directionFor(KeyEvent.KEYCODE_SPACE))
    }
}
