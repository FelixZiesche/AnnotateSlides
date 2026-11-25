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

package com.shezik.drawanywhere.view.canvas

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.compose.ui.graphics.toArgb
import com.shezik.drawanywhere.DrawViewModel
import com.shezik.drawanywhere.R
import com.shezik.drawanywhere.TextEditState
import com.shezik.drawanywhere.model.TextItem

/**
 * The live edit box for the text tool: a real [EditText] placed over the canvas at the
 * position of the text box being edited, plus small "done" and "delete" buttons.
 *
 * Lives inside the canvas overlay window. The window only becomes focusable while a
 * box is being edited (see MainService), so the hardware keyboard and the on-screen
 * keyboard both reach this EditText. Enter inserts a new line, Esc or Back finishes.
 */
class TextEditLayer(
    private val context: Context,
    private val container: FrameLayout,
    private val viewModel: DrawViewModel,
) {
    private val density = context.resources.displayMetrics.density
    private val imm = context.getSystemService(InputMethodManager::class.java)

    private var boundId: Long? = null
    private var ignoreTextChanges = false
    private var wantKeyboard = false

    private val boxBackground = GradientDrawable().apply {
        setStroke((1.5f * density).toInt(), 0x99_808080.toInt(), 6f * density, 4f * density)
    }

    val editText: EditText = object : EditText(context) {
        override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                if (event.action == KeyEvent.ACTION_UP) viewModel.finishTextEditing()
                return true
            }
            return super.onKeyPreIme(keyCode, event)
        }

        override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
            super.onWindowFocusChanged(hasWindowFocus)
            if (hasWindowFocus && wantKeyboard) showKeyboardNow()
        }
    }.apply {
        visibility = View.GONE
        typeface = TextBoxRenderer.TYPEFACE
        includeFontPadding = false
        setLineSpacing(0f, 1f)
        gravity = Gravity.TOP or Gravity.START
        inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        setHorizontallyScrolling(false)
        isFocusableInTouchMode = true
        background = boxBackground
        setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
                if (event.action == KeyEvent.ACTION_UP) viewModel.finishTextEditing()
                true
            } else false
        }
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!ignoreTextChanges) viewModel.updateEditingText(s?.toString().orEmpty())
            }
        })
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> positionButtons() }
    }

    private val buttonSize = (40 * density).toInt()

    private fun makeButton(iconRes: Int, description: Int, onClick: () -> Unit): ImageButton {
        val label = context.getString(description)
        return ImageButton(context).apply {
            setImageResource(iconRes)
            contentDescription = label
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xF2_FFFFFF.toInt())
                setStroke((1 * density).toInt(), 0x33_000000)
            }
            elevation = 4 * density
            scaleType = android.widget.ImageView.ScaleType.CENTER
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(buttonSize, buttonSize).apply {
                marginEnd = (8 * density).toInt()
            }
        }
    }

    private val buttons = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        visibility = View.GONE
        addView(makeButton(R.drawable.ic_text_done, R.string.text_done) { viewModel.finishTextEditing() })
        addView(makeButton(R.drawable.ic_text_delete, R.string.text_delete) { viewModel.deleteEditingText() })
    }

    private var current: TextEditState? = null
    private var viewport = CanvasViewport()

    init {
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        container.addView(editText, FrameLayout.LayoutParams(wrap, wrap, Gravity.TOP or Gravity.START))
        container.addView(buttons, FrameLayout.LayoutParams(wrap, wrap, Gravity.TOP or Gravity.START))
        container.viewTreeObserver.addOnWindowFocusChangeListener(
            ViewTreeObserver.OnWindowFocusChangeListener { focused ->
                if (focused && wantKeyboard) showKeyboardNow()
            }
        )
    }

    val isEditing: Boolean get() = current != null

    /** Show/hide/restyle the edit box for the current edit state and viewport. */
    fun bind(state: TextEditState?, vp: CanvasViewport) {
        current = state
        viewport = vp
        if (state == null) {
            if (boundId != null) {
                boundId = null
                hideKeyboard()
                editText.clearFocus()
                editText.visibility = View.GONE
                buttons.visibility = View.GONE
            }
            return
        }
        val draft = state.draft
        if (boundId != draft.id) {
            boundId = draft.id
            ignoreTextChanges = true
            editText.setText(draft.text)
            editText.setSelection(draft.text.length)
            ignoreTextChanges = false
            editText.visibility = View.VISIBLE
            buttons.visibility = View.VISIBLE
        }
        applyStyle(draft, vp)
    }

    private fun applyStyle(item: TextItem, vp: CanvasViewport) {
        val zoom = vp.zoom
        val sizePx = item.textSize * zoom
        val pad = (item.padding * zoom).toInt()
        editText.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx)
        val argb = item.color.copy(alpha = (item.color.alpha * item.alpha).coerceIn(0f, 1f)).toArgb()
        editText.setTextColor(argb)
        editText.setPadding(pad, pad, pad, pad)
        editText.minWidth = (sizePx * 2 + 2 * pad).toInt()
        boxBackground.setColor(if (item.background) TextBoxRenderer.BACKGROUND_COLOR else 0x00_000000)
        boxBackground.cornerRadius = pad * TextBoxRenderer.CORNER_FACTOR
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            editText.textCursorDrawable = GradientDrawable().apply {
                setColor(argb or 0xFF000000.toInt())
                setSize((2 * density).toInt().coerceAtLeast(2), sizePx.toInt())
            }
        }

        val left = (item.x - vp.panX) * zoom
        val top = (item.y - vp.panY) * zoom
        editText.translationX = left
        editText.translationY = top
        val available = (container.width - left).toInt()
        editText.maxWidth = available.coerceAtLeast(editText.minWidth)
        positionButtons()
    }

    private fun positionButtons() {
        if (current == null) return
        val gap = 6 * density
        val left = editText.translationX
        val top = editText.translationY
        buttons.translationX = left.coerceIn(0f, (container.width - 2 * buttonSize).toFloat().coerceAtLeast(0f))
        val above = top - buttonSize - gap
        buttons.translationY = if (above >= 0f) above else top + editText.height + gap
    }

    /** Called by the service once the overlay window has become focusable. */
    fun requestKeyboard() {
        wantKeyboard = true
        editText.requestFocus()
        showKeyboardNow()
        // Window focus may arrive a moment later; onWindowFocusChanged retries then.
        editText.postDelayed({ if (wantKeyboard) showKeyboardNow() }, 150)
    }

    private fun showKeyboardNow() {
        if (current == null) return
        if (!editText.hasFocus()) editText.requestFocus()
        imm?.showSoftInput(editText, 0)
    }

    private fun hideKeyboard() {
        wantKeyboard = false
        imm?.hideSoftInputFromWindow(editText.windowToken, 0)
    }
}
