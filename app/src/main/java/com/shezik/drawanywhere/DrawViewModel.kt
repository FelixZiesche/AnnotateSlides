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
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shezik.drawanywhere.model.PenConfig
import com.shezik.drawanywhere.model.PenType
import com.shezik.drawanywhere.model.StrokeModifier
import com.shezik.drawanywhere.model.TextItem
import com.shezik.drawanywhere.view.canvas.CanvasViewport
import com.shezik.drawanywhere.view.canvas.LockMode
import com.shezik.drawanywhere.view.toolbar.ToolbarOrientation
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServiceState(
    val toolbarPosition: Offset = Offset(32f, 64f),
    val toolbarActive: Boolean = true
)

data class UiState(
    val canvasVisible: Boolean = true,
    val canvasPassthrough: Boolean = false,
    val autoClearCanvas: Boolean = false,
    val visibleOnStart: Boolean = true,
    val fingerDrawingEnabled: Boolean = true,
    val recentColors: List<Color> = emptyList(),
    /** Semi-transparent white box behind new/edited text boxes. */
    val textBackground: Boolean = true,

    val currentPenType: PenType = PenType.Pen,
    val penConfigs: Map<PenType, PenConfig> = defaultPenConfigs(),

    val toolbarOrientation: ToolbarOrientation = ToolbarOrientation.HORIZONTAL,
    val firstDrawerOpen: Boolean = canvasVisible,
    val secondDrawerOpen: Boolean = false,

    // Always visible (not in a drawer): passthrough, show/hide, setup hint.
    // Main row: what is needed while presenting. "More" drawer: rarely used.
    val firstDrawerButtons: Set<String> = setOf(
        "text_tool", "tool_controls", "color_picker", "undo"
    ),
    val secondDrawerButtons: Set<String> = setOf(
        "clear", "redo", "zoom_lock", "settings"
    ),
    val secondDrawerPinnedButtons: Set<String> = emptySet()
) {
    val currentPenConfig: PenConfig
        get() = penConfigs[currentPenType] ?: PenConfig()
}

/** Default text size in sp (readable on a projector). */
const val DEFAULT_TEXT_SIZE_SP = 28f
val TEXT_SIZE_RANGE_SP = 12f..96f

/** A text box currently being typed into. [original] is null for a new box. */
data class TextEditState(val original: TextItem?, val draft: TextItem)

fun defaultPenConfigs(): Map<PenType, PenConfig> = PenType.entries.associateWith { type ->
    if (type == PenType.Text) {
        PenConfig(penType = type, width = DEFAULT_TEXT_SIZE_SP)
    } else if (type.isEraser) {
        val w = if (type == PenType.StrokeEraser) 50f else 30f
        PenConfig(penType = type, width = w, color = Color.LightGray)
    } else {
        PenConfig(penType = type)
    }
}

@OptIn(FlowPreview::class)
class DrawViewModel(
    private val controller: DrawController,
    private val preferencesManager: PreferencesManager,
    initialUiState: UiState,
    initialServiceState: ServiceState,
    private val stopService: () -> Unit,
    private val containsDismissTarget: (Int, Int) -> Boolean,
) : ViewModel() {
    companion object {
        const val TOOLBAR_DIM_DELAY_MS = 3_000L
        const val TOOLBAR_DIM_ALPHA = 0.5f
        const val TOOLBAR_DIM_DURATION_MS = 300L
        const val TEXT_LONG_PRESS_MS = 600L
        private const val TEXT_DRAG_SLOP_DP = 8f
    }
    private val _uiState = MutableStateFlow(initialUiState)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _serviceState = MutableStateFlow(initialServiceState)
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

    private val _viewport = MutableStateFlow(CanvasViewport())
    val viewport: StateFlow<CanvasViewport> = _viewport.asStateFlow()

    private val _lockMode = MutableStateFlow(LockMode.NONE)
    val lockMode: StateFlow<LockMode> = _lockMode.asStateFlow()

    private val _dismissTarget = MutableStateFlow<DismissTarget>(DismissTarget.Hidden)
    val dismissTarget: StateFlow<DismissTarget> = _dismissTarget.asStateFlow()

    val canUndo: StateFlow<Boolean> = controller.canUndo
    val canRedo: StateFlow<Boolean> = controller.canRedo
    val canClearCanvas: StateFlow<Boolean> = controller.canClearStrokes
    val pageIndex: StateFlow<Int> = controller.pageIndex

    // ── Text tool state ──────────────────────────────────────────

    private val _textEdit = MutableStateFlow<TextEditState?>(null)
    /** Non-null while a text box is being edited; the overlay window must be focusable then. */
    val textEdit: StateFlow<TextEditState?> = _textEdit.asStateFlow()

    /** Returns the topmost text box at a world point; installed by the canvas view. */
    var textHitTest: (Offset) -> TextItem? = { null }

    /** Pixels per sp (and per dp) on this screen; installed by the service. */
    var textScale: Float = 1f

    /** Called after a text box was deleted by long press (for haptic feedback). */
    var onTextDeleted: (() -> Unit)? = null

    init {

        _uiState
            .debounce(300)
            .onEach { state -> preferencesManager.saveUiState(state) }
            .launchIn(viewModelScope)

        resetToolbarTimer()
    }

    override fun onCleared() {
        super.onCleared()
        dimmingJob?.cancel()
    }

    private var lastDrawingPenType: PenType = PenType.Pen

    fun switchToPen(type: PenType) {
        if (type != PenType.Text) {
            cancelTextGesture()
            finishTextEditing()
        }
        val current = uiState.value.currentPenType
        if (current != PenType.Text && type == PenType.Text) lastDrawingPenType = current
        _uiState.update { it.copy(currentPenType = type) }
        controller.setPenConfig(uiState.value.currentPenConfig)
    }

    fun resolvePenType(modifier: StrokeModifier) =
        when (modifier) {
            StrokeModifier.PrimaryButton   -> PenType.StrokeEraser
            StrokeModifier.SecondaryButton -> PenType.StrokeEraser
            StrokeModifier.Both            -> PenType.StrokeEraser
            StrokeModifier.None            -> uiState.value.currentPenType
        }

    private var previousPenType: PenType? = null
    private var isStrokeDown: Boolean = false

    /** "T" toolbar button: toggle between the text tool and the last drawing tool. */
    fun toggleTextTool() {
        if (uiState.value.currentPenType == PenType.Text) switchToPen(lastDrawingPenType)
        else switchToPen(PenType.Text)
    }

    fun startStroke(point: Offset, modifier: StrokeModifier) {
        finishStroke()

        if (modifier == StrokeModifier.None && uiState.value.currentPenType == PenType.Text) {
            beginTextGesture(point)
            return
        }

        val newPenType = resolvePenType(modifier)
        if (newPenType != uiState.value.currentPenType) {
            previousPenType = uiState.value.currentPenType
            switchToPen(newPenType)
        }

        controller.createStroke(point)
        isStrokeDown = true
    }

    fun updateStroke(point: Offset) {
        textGesture?.let { moveTextGesture(it, point); return }
        if (!isStrokeDown) return
        controller.updateLatestStroke(point)
    }

    fun finishStroke() {
        textGesture?.let { endTextGesture(it); return }
        if (!isStrokeDown) return

        controller.finishStroke()

        previousPenType?.let {
            switchToPen(it)
            previousPenType = null
        }
        isStrokeDown = false
    }

    fun toggleCanvasVisibility() =
        setCanvasVisibility(!uiState.value.canvasVisible)

    fun setCanvasVisibility(visible: Boolean) {
        cancelTextGesture()
        finishTextEditing()
        var currentPassthrough = uiState.value.canvasPassthrough
        var currentPinned = uiState.value.secondDrawerPinnedButtons

        if (uiState.value.autoClearCanvas && !visible) {
            clearCanvas()
            currentPassthrough = false
            currentPinned = getPinSecondDrawerButtonResult("passthrough", false)
        }

        val currentFirstOpen = uiState.value.firstDrawerOpen
        _uiState.update { it.copy(
            canvasVisible = visible,
            canvasPassthrough = currentPassthrough,
            firstDrawerOpen = !currentFirstOpen,
            secondDrawerPinnedButtons = currentPinned
        ) }
    }

    fun toggleCanvasPassthrough() =
        setCanvasPassthrough(!uiState.value.canvasPassthrough)

    fun setCanvasPassthrough(passthrough: Boolean) {
        if (passthrough) {
            cancelTextGesture()
            finishTextEditing()
        }
        val newPinned = getPinSecondDrawerButtonResult("passthrough", passthrough)
        _uiState.update { it.copy(canvasPassthrough = passthrough, secondDrawerPinnedButtons = newPinned) }
    }

    fun setPenColor(color: Color, trackRecent: Boolean = true) {
        updateCurrentPenConfig { copy(color = color) }
        if (trackRecent) addRecentColor(color)
    }

    fun setPresetColor(color: Color) = setPenColor(color, trackRecent = false)

    private fun addRecentColor(color: Color) {
        val current = uiState.value.recentColors
        if (current.contains(color)) return
        val updated = listOf(color) + current
        _uiState.update { it.copy(recentColors = updated.take(6)) }
    }

    fun setStrokeWidth(width: Float) = updateCurrentPenConfig { copy(width = width) }

    fun setStrokeAlpha(alpha: Float) = updateCurrentPenConfig { copy(alpha = alpha) }

    private fun updateCurrentPenConfig(transform: PenConfig.() -> PenConfig) {
        _uiState.update { state ->
            val configs = state.penConfigs.toMutableMap()
            val current = configs[state.currentPenType] ?: PenConfig(penType = state.currentPenType)
            configs[state.currentPenType] = current.transform()
            state.copy(penConfigs = configs)
        }
        controller.setPenConfig(uiState.value.currentPenConfig)
        if (uiState.value.currentPenType == PenType.Text) applyTextStyleToDraft()
    }

    fun setTextBackground(enabled: Boolean) {
        _uiState.update { it.copy(textBackground = enabled) }
        _textEdit.update { st -> st?.copy(draft = st.draft.copy(background = enabled)) }
    }

    fun setToolbarPosition(position: Offset) =
        _serviceState.update { it.copy(toolbarPosition = position) }

    fun updateToolbarPosition(offset: Offset) =
        setToolbarPosition(serviceState.value.toolbarPosition + offset)

    fun saveToolbarPosition() = viewModelScope.launch {
        preferencesManager.saveServiceState(serviceState.value)
    }

    fun clearCanvas() {
        cancelTextGesture()
        _textEdit.value = null  // discard the box being typed, everything is cleared anyway
        controller.clearStrokes()
    }

    fun undo() {
        cancelTextGesture()
        val wasEditing = textEdit.value != null
        // Commit the typing session first so that undo reverts exactly it.
        // If nothing was typed, the press only leaves typing mode.
        if (!finishTextEditing() && wasEditing) return
        controller.undo()
    }

    fun redo() {
        cancelTextGesture()
        finishTextEditing()
        controller.redo()
    }

    // ── Slide pages ──────────────────────────────────────────────

    fun nextPage() = goToPage(pageIndex.value + 1)
    fun previousPage() = goToPage(pageIndex.value - 1)

    /** Jump to the page of a slide number read from the presentation app (0-based). */
    fun goToPage(index: Int) {
        if (index == pageIndex.value) return
        if (index < 0) return
        cancelTextGesture()
        finishStroke()
        finishTextEditing()
        controller.goToPage(index)
    }

    // ── Text tool ────────────────────────────────────────────────

    private class TextGesture(val down: Offset, val hit: TextItem?) {
        var moved = false
        var consumed = false
        var last: Offset = down
    }

    private var textGesture: TextGesture? = null
    private var longPressJob: Job? = null

    private fun currentZoom(): Float = viewport.value.zoom.takeIf { it > 0f } ?: 1f

    private fun textSizeWorld(sp: Float): Float = sp * textScale / currentZoom()

    private fun beginTextGesture(point: Offset) {
        val editingId = textEdit.value?.draft?.id
        val hit = textHitTest(point)?.takeIf { it.id != editingId }
        val gesture = TextGesture(point, hit)
        textGesture = gesture
        longPressJob?.cancel()
        if (hit != null) {
            longPressJob = viewModelScope.launch {
                delay(TEXT_LONG_PRESS_MS)
                if (textGesture === gesture && !gesture.moved) {
                    gesture.consumed = true
                    controller.commitTextChange(hit, null)
                    onTextDeleted?.invoke()
                }
            }
        }
    }

    private fun moveTextGesture(g: TextGesture, point: Offset) {
        if (g.consumed) return
        g.last = point
        if (!g.moved) {
            val slop = TEXT_DRAG_SLOP_DP * textScale / currentZoom()
            if ((point - g.down).getDistance() <= slop) return
            g.moved = true
            longPressJob?.cancel()
        }
        g.hit?.let { hit ->
            controller.previewText(hit.copy(x = hit.x + point.x - g.down.x, y = hit.y + point.y - g.down.y))
        }
    }

    private fun endTextGesture(g: TextGesture) {
        longPressJob?.cancel()
        textGesture = null
        if (g.consumed) return
        val hit = g.hit
        if (g.moved) {
            if (hit != null) controller.commitTextChange(hit, controller.findText(hit.id))
            return
        }
        // Tap
        when {
            hit != null -> {
                finishTextEditing()
                // The hit box may have been replaced meanwhile; edit its latest version
                val latest = controller.findText(hit.id) ?: return
                _textEdit.value = TextEditState(original = latest, draft = latest)
            }
            textEdit.value != null -> finishTextEditing()
            else -> startNewText(g.down)
        }
    }

    /** Abort a running touch gesture on a text box (e.g. a second finger came down). */
    fun cancelTextGesture() {
        val g = textGesture ?: return
        longPressJob?.cancel()
        textGesture = null
        if (g.moved && !g.consumed) g.hit?.let { controller.previewText(it) }
    }

    private fun startNewText(point: Offset) {
        val cfg = uiState.value.penConfigs[PenType.Text] ?: PenConfig(PenType.Text, width = DEFAULT_TEXT_SIZE_SP)
        val size = textSizeWorld(cfg.width)
        val pad = size * TextItem.PADDING_FACTOR
        val draft = TextItem(
            id = TextItem.newId(),
            text = "",
            x = point.x - pad,
            y = point.y - pad - size * 0.6f,
            textSize = size,
            color = cfg.color,
            alpha = cfg.alpha,
            background = uiState.value.textBackground,
        )
        _textEdit.value = TextEditState(original = null, draft = draft)
    }

    private fun applyTextStyleToDraft() {
        val cfg = uiState.value.currentPenConfig
        _textEdit.update { st ->
            st?.copy(draft = st.draft.copy(
                textSize = textSizeWorld(cfg.width),
                color = cfg.color,
                alpha = cfg.alpha,
            ))
        }
    }

    /** Text typed into the edit box changed. */
    fun updateEditingText(text: String) {
        _textEdit.update { st -> if (st == null || st.draft.text == text) st else st.copy(draft = st.draft.copy(text = text)) }
    }

    /**
     * Commit the box being edited (one undo step) and leave typing mode.
     * An empty box is discarded (or deleted, if it existed before).
     * @return true if this recorded an undo step.
     */
    fun finishTextEditing(): Boolean {
        val st = _textEdit.value ?: return false
        _textEdit.value = null
        val draft = st.draft.copy(text = st.draft.text.trimEnd())
        return if (draft.text.isBlank()) {
            st.original != null && controller.commitTextChange(st.original, null)
        } else {
            controller.commitTextChange(st.original, draft)
        }
    }

    /** Trash button next to the edit box. */
    fun deleteEditingText() {
        val st = _textEdit.value ?: return
        _textEdit.value = null
        if (st.original != null) controller.commitTextChange(st.original, null)
    }

    private var dimmingJob: Job? = null

    fun resetToolbarTimer() {
        dimmingJob?.cancel()
        setToolbarActive(true)
        dimmingJob = viewModelScope.launch {
            delay(TOOLBAR_DIM_DELAY_MS)
            setToolbarActive(false)
        }
    }

    fun setToolbarActive(state: Boolean) =
        _serviceState.update { it.copy(toolbarActive = state) }

    fun toggleToolbarOrientation() =
        setToolbarOrientation(
            when (uiState.value.toolbarOrientation) {
                ToolbarOrientation.VERTICAL -> ToolbarOrientation.HORIZONTAL
                ToolbarOrientation.HORIZONTAL -> ToolbarOrientation.VERTICAL
            }
        )

    fun setToolbarOrientation(orientation: ToolbarOrientation) =
        _uiState.update { it.copy(toolbarOrientation = orientation) }

    fun toggleFirstDrawer() =
        setFirstDrawerOpen(!uiState.value.firstDrawerOpen)

    fun setFirstDrawerOpen(state: Boolean) =
        _uiState.update { it.copy(firstDrawerOpen = state) }

    fun toggleSecondDrawer() =
        setSecondDrawerOpen(!uiState.value.secondDrawerOpen)

    fun setSecondDrawerOpen(state: Boolean) =
        _uiState.update { it.copy(secondDrawerOpen = state) }

    fun toggleSecondDrawerPinned(id: String) {
        val currentPinned = uiState.value.secondDrawerPinnedButtons
        pinSecondDrawerButton(id, !currentPinned.contains(id))
    }

    fun pinSecondDrawerButton(id: String, pinned: Boolean) =
        _uiState.update { it.copy(secondDrawerPinnedButtons = getPinSecondDrawerButtonResult(id, pinned)) }

    private fun getPinSecondDrawerButtonResult(id: String, pinned: Boolean): Set<String> {
        val currentPinned = uiState.value.secondDrawerPinnedButtons
        if (currentPinned.contains(id) == pinned) return currentPinned
        return if (pinned) currentPinned + id else currentPinned - id
    }

    fun setAutoClearCanvas(state: Boolean) =
        _uiState.update { it.copy(autoClearCanvas = state) }

    fun setVisibleOnStart(state: Boolean) =
        _uiState.update { it.copy(visibleOnStart = state) }

    fun setFingerDrawingEnabled(state: Boolean) =
        _uiState.update { it.copy(fingerDrawingEnabled = state) }

    // --- Viewport ---

    fun setViewport(viewport: CanvasViewport) {
        _viewport.value = viewport
    }

    fun cycleLockMode() {
        _lockMode.update { current ->
            when (current) {
                LockMode.NONE -> LockMode.ZOOM
                LockMode.ZOOM -> LockMode.ALL
                LockMode.ALL -> LockMode.NONE
            }
        }
    }

    fun setLockMode(mode: LockMode) {
        _lockMode.value = mode
    }

    fun resetViewport(screenCenter: Offset) {
        _viewport.value = _viewport.value.resetAt(screenCenter)
    }

    fun onDismissDragStart() { _dismissTarget.value = DismissTarget.Visible(false) }

    fun onDismissDragMove(fingerPosInToolbar: Offset) {
        val pos = serviceState.value.toolbarPosition
        val active = containsDismissTarget(
            (pos.x + fingerPosInToolbar.x).toInt(),
            (pos.y + fingerPosInToolbar.y).toInt()
        )
        _dismissTarget.value = DismissTarget.Visible(active)
    }

    /** @return false if the drag ended in a dismiss — caller should skip saving position. */
    fun onDismissDragEnd(): Boolean {
        val target = _dismissTarget.value
        if (target is DismissTarget.Visible && target.active) {
            quitApplication(savePosition = false)
            return false
        }
        _dismissTarget.value = DismissTarget.Hidden
        return true
    }

    fun quitApplication(savePosition: Boolean = true) {
        finishTextEditing()
        viewModelScope.launch {
            preferencesManager.saveUiState(uiState.value)
            if (savePosition) preferencesManager.saveServiceState(serviceState.value)
            stopService()
        }
    }
}

sealed class DismissTarget {
    object Hidden : DismissTarget()
    data class Visible(val active: Boolean) : DismissTarget()
}
