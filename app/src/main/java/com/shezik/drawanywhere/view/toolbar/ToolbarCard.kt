package com.shezik.drawanywhere.view.toolbar

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.shezik.drawanywhere.R
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun DraggableToolbarCard(
    modifier: Modifier = Modifier,
    haptics: HapticFeedback,
    onPositionChange: (Offset) -> Unit,
    onPositionSaved: () -> Unit,
    onToolbarInteracted: () -> Unit,
    onDragStart: (() -> Unit)? = null,
    onDragPosition: ((Offset) -> Unit)? = null,
    onDragEnd: (() -> Boolean)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .testTag("toolbar_card")
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                        onToolbarInteracted()
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDragStart?.invoke()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onPositionChange(dragAmount)
                        onDragPosition?.invoke(change.position)
                    },
                    onDragEnd = {
                        val shouldSave = onDragEnd?.invoke() ?: true
                        if (shouldSave) onPositionSaved()
                    },
                    onDragCancel = {
                        onDragEnd?.invoke()
                    }
                )
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = CircleShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f)
        )
    ) {
        content()
    }
}

/**
 * Grip at the start of the toolbar: drag it to move the toolbar right away
 * (no long press needed). Dropping it on the dismiss target quits, like the long-press drag.
 */
@Composable
fun ToolbarDragHandle(
    haptics: HapticFeedback,
    onPositionChange: (Offset) -> Unit,
    onPositionSaved: () -> Unit,
    onDragStart: (() -> Unit)? = null,
    onDragPosition: ((Offset) -> Unit)? = null,
    onDragEnd: (() -> Boolean)? = null,
) {
    Box(
        modifier = Modifier
            .size(width = 32.dp, height = 40.dp)
            .testTag("toolbar_drag_handle")
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDragStart?.invoke()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onPositionChange(dragAmount)
                        onDragPosition?.invoke(change.position)
                    },
                    onDragEnd = {
                        val shouldSave = onDragEnd?.invoke() ?: true
                        if (shouldSave) onPositionSaved()
                    },
                    onDragCancel = { onDragEnd?.invoke() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.DragIndicator,
            contentDescription = stringResource(R.string.move_toolbar),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
