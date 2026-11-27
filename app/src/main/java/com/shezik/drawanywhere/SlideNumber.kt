/*
Annotate Slides: a fork of DrawAnywhere with a keyboard text tool.
Copyright (C) 2026 FelixZiesche

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

/**
 * Reads the current slide number from texts the presentation app shows
 * (e.g. "Folie 3 von 12", "Slide 3 of 12", "3 / 12").
 */
object SlideNumber {

    /** Presentation apps whose screen is checked for a slide counter. */
    val PRESENTATION_PACKAGES = setOf(
        "com.google.android.apps.docs.editors.slides",   // Google Slides
        "com.microsoft.office.powerpoint",               // PowerPoint
        "com.microsoft.office.officehubrow",              // Microsoft 365 (Office)
    )

    private val WORDED = Regex(
        """(?i)\b(?:folie|slide|seite|page)\s*(\d{1,4})\s*(?:von|of|/)\s*(\d{1,4})\b"""
    )
    private val SLASH = Regex("""^\s*(\d{1,4})\s*/\s*(\d{1,4})\s*$""")

    /** 1-based slide number in [text], or null if it is no slide counter. */
    fun parse(text: CharSequence?): Int? {
        if (text.isNullOrBlank() || text.length > 80) return null
        val m = WORDED.find(text) ?: SLASH.find(text) ?: return null
        val n = m.groupValues[1].toIntOrNull() ?: return null
        val total = m.groupValues[2].toIntOrNull() ?: return null
        return if (n in 1..total) n else null
    }

    /**
     * Picks the slide number from all counters found on screen. Only a single,
     * unambiguous number counts: an editor's thumbnail list ("Slide 1 of 12",
     * "Slide 2 of 12", ...) is ignored.
     */
    fun pick(found: Collection<Int>): Int? = found.toSet().singleOrNull()
}
