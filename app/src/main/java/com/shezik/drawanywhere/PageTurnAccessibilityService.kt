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
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Accessibility service for automatic slide pages.
 *
 * 1. In Google Slides and PowerPoint it looks for the slide counter the app shows
 *    ("Folie 3 von 12", "3 / 12"), so the annotation page follows the slide no matter
 *    how it was changed (swipe, tap, keyboard). Only these apps are looked at, and only
 *    the slide number is used.
 * 2. Fallback: it observes hardware key presses (Bluetooth keyboard, presenter clicker).
 *    Arrow keys and Page Up/Down are passed on unchanged to the presentation app and
 *    additionally switch the annotation page. This is skipped while the slide counter
 *    can be read, so a page is never turned twice.
 */
class PageTurnAccessibilityService : AccessibilityService() {

    companion object {
        private val _connected = MutableStateFlow(false)
        /** True while the user has enabled the service in the system settings. */
        val connected: StateFlow<Boolean> = _connected.asStateFlow()

        private val _pageTurns = MutableSharedFlow<Int>(extraBufferCapacity = 16)
        /** +1 = next slide, -1 = previous slide. */
        val pageTurns: SharedFlow<Int> = _pageTurns.asSharedFlow()

        private val _slideJumps = MutableSharedFlow<Int>(extraBufferCapacity = 16)
        /** 0-based slide read from the presentation app's slide counter. */
        val slideJumps: SharedFlow<Int> = _slideJumps.asSharedFlow()

        private const val SCAN_DELAY_MS = 250L
        private const val MAX_NODES = 600

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

    private val handler = Handler(Looper.getMainLooper())
    private val scanRunnable = Runnable { scanForSlideNumber() }
    /** True while the current presentation app shows a readable slide counter. */
    @Volatile private var counterReadable = false
    private var lastSlide = -1
    private var currentPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        _connected.value = true
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        _connected.value = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(scanRunnable)
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
            if (dir != 0) {
                if (counterReadable) scheduleScan()  // the counter decides, not the key
                else _pageTurns.tryEmit(dir)
            }
        }
        return false  // never consume: the presentation app still gets the key
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg in SlideNumber.PRESENTATION_PACKAGES) {
            if (pkg != currentPackage) {
                currentPackage = pkg
                counterReadable = false
                lastSlide = -1
            }
            if (listening) scheduleScan()
        } else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            pkg != packageName && !pkg.contains("inputmethod") && pkg != "com.android.systemui"
        ) {
            // Another app came to the front: keys count again until a counter is seen.
            currentPackage = null
            counterReadable = false
            lastSlide = -1
        }
    }

    private fun scheduleScan() {
        handler.removeCallbacks(scanRunnable)
        handler.postDelayed(scanRunnable, SCAN_DELAY_MS)
    }

    private fun scanForSlideNumber() {
        if (!listening) return
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return
        if (root.packageName?.toString() !in SlideNumber.PRESENTATION_PACKAGES) return
        val found = mutableListOf<Int>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            (SlideNumber.parse(node.text) ?: SlideNumber.parse(node.contentDescription))?.let { found += it }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        val slide = SlideNumber.pick(found) ?: return
        counterReadable = true
        if (slide != lastSlide) {
            lastSlide = slide
            _slideJumps.tryEmit(slide - 1)
        }
    }
    override fun onInterrupt() {}
}
