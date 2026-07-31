package com.globalmmorpg.game.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StatBar(
    label: String,
    current: Int,
    max: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val fraction = if (max > 0) (current.toFloat() / max.toFloat()).coerceIn(0f, 1f) else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp)
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .background(color, RoundedCornerShape(4.dp))
        )
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "$label  $current/$max",
                color = Color.White,
                fontSize = 10.sp
            )
        }
    }
}
