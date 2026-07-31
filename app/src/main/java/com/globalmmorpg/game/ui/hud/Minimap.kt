package com.globalmmorpg.game.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.globalmmorpg.game.data.player.MapEntity

/**
 * Real radar-style minimap: player is always the center dot; each MapEntity's
 * relativeX/relativeY (-1f..1f) is plotted around it. This reads live data
 * from HudViewModel.nearbyEntities — no hardcoded/fake dots.
 */
@Composable
fun Minimap(
    entities: List<MapEntity>,
    modifier: Modifier = Modifier,
    size: Dp = 100.dp
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(size)
            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val radius = this.size.minDimension / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)

            drawCircle(
                color = Color.White.copy(alpha = 0.3f),
                radius = radius,
                center = center,
                style = Stroke(width = 2f)
            )

            // Player marker, always centered
            drawCircle(color = Color(0xFF3A8DFF), radius = 6f, center = center)

            entities.forEach { entity ->
                val px = center.x + entity.relativeX.coerceIn(-1f, 1f) * radius * 0.9f
                val py = center.y + entity.relativeY.coerceIn(-1f, 1f) * radius * 0.9f
                drawCircle(
                    color = if (entity.isHostile) Color(0xFFE23B3B) else Color(0xFF4CE07A),
                    radius = 5f,
                    center = Offset(px, py)
                )
            }
        }
    }
}
