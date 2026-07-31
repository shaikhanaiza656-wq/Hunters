package com.globalmmorpg.game.ui.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.globalmmorpg.game.data.voice.VoiceConnectionState
import com.globalmmorpg.game.data.voice.VoiceState

/**
 * Real open-mic voice indicator + mute toggle. The mic is unmuted the
 * instant the channel connects — a live call, not a push-to-talk beep —
 * matching the GDD's "like Free Fire" voice requirement.
 */
@Composable
fun VoiceControls(state: VoiceState, onToggleMic: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val micColor = if (state.isMicMuted) Color.DarkGray else Color(0xFF35D07F)
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(micColor, CircleShape)
                .clickable { onToggleMic() },
            contentAlignment = Alignment.Center
        ) {
            Text(text = if (state.isMicMuted) "\uD83D\uDD07" else "\uD83C\uDFA4", fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.width(6.dp))
        val label = when (state.connectionState) {
            VoiceConnectionState.CONNECTED -> "Voice: ${state.participants.size + 1} in party"
            VoiceConnectionState.CONNECTING -> "Connecting…"
            VoiceConnectionState.FAILED -> "Voice error"
            VoiceConnectionState.DISCONNECTED -> "Voice off"
        }
        Text(text = label, color = Color.White, fontSize = 11.sp, modifier = Modifier.width(130.dp))
    }
}
