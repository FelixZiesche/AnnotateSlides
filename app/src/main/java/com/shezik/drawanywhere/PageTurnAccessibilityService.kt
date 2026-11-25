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

package com.shezik.drawanywhere

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Optional accessibility service for automatic slide pages.
 *
 * It only *observes* hardware key presses (Bluetooth keyboard, presenter clicker):
 * arrow keys and Page Up/Down are passed on unchanged to the presentation app, and
 * additionally switch the annotation page. Nothing is read from the screen.
 */
class PageTurnAccessibilityService : AccessibilityService() {

    companion object {
        private val _connected = MutableStateFlow(false)
        /** True while the user has enabled the service in the system settings. */
        val connected: StateFlow<Boolean> = _connected.asStateFlow()

        private val _pageTurns = MutableSharedFlow<Int>(extraBufferCapacity = 16)
        /** +1 = next slide, -1 = previous slide. */
        val pageTurns: SharedFlow<Int> = _pageTurns.asSharedFlow()

        /** Set by MainService: whether a key press should currently turn the page. */
        @Volatile var listening: Boolean = false

        fun directionFor(keyCode: Int): Int = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_MEDIA_NEXT -> 1
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> -1
            else -> 0
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        _connected.value = true
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        _connected.value = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        _connected.value = false
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (listening &&
            event.action == KeyEvent.ACTION_DOWN &&
            event.repeatCount == 0 &&
            !event.isShiftPressed && !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed
        ) {
            val dir = directionFor(event.keyCode)
            if (dir != 0) _pageTurns.tryEmit(dir)
        }
        return false  // never consume: the presentation app still gets the key
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
