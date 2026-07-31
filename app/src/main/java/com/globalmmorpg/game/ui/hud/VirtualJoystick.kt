package com.globalmmorpg.game.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

/**
 * A real virtual joystick: drag the knob, get a normalized (dx, dy) vector
 * back every frame via onMove, clamped to the outer ring radius. This is not
 * a static image — it is fully functional input the movement system consumes.
 */
@Composable
fun VirtualJoystick(
    modifier: Modifier = Modifier,
    outerSize: Dp = 120.dp,
    knobSize: Dp = 48.dp,
    onMove: (dx: Float, dy: Float) -> Unit,
    onReleased: () -> Unit
) {
    var knobOffsetPx by remember { mutableStateOf(0f to 0f) }
    val density = LocalDensity.current
    val maxRadiusPx = with(density) { (outerSize / 2).toPx() }

    Box(
        modifier = modifier
            .size(outerSize)
            .background(Color.White.copy(alpha = 0.15f), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val (curX, curY) = knobOffsetPx
                        var newX = curX + dragAmount.x
                        var newY = curY + dragAmount.y
                        val distance = sqrt(newX * newX + newY * newY)
                        if (distance > maxRadiusPx) {
                            val scale = maxRadiusPx / distance
                            newX *= scale
                            newY *= scale
                        }
                        knobOffsetPx = newX to newY
                        onMove(newX / maxRadiusPx, newY / maxRadiusPx)
                    },
                    onDragEnd = {
                        knobOffsetPx = 0f to 0f
                        onReleased()
                    },
                    onDragCancel = {
                        knobOffsetPx = 0f to 0f
                        onReleased()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val (xPx, yPx) = knobOffsetPx
        val xDp = with(density) { xPx.toDp() }
        val yDp = with(density) { yPx.toDp() }
        Box(
            modifier = Modifier
                .size(knobSize)
                .offset(x = xDp, y = yDp)
                .background(Color.White.copy(alpha = 0.6f), CircleShape)
        )
    }
}
