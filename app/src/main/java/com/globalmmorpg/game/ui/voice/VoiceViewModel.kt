package com.globalmmorpg.game.ui.voice

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.globalmmorpg.game.data.voice.VoiceChannelManager
import com.globalmmorpg.game.data.voice.VoiceRepository
import com.globalmmorpg.game.data.voice.VoiceState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class VoiceViewModel(
    context: Context,
    private val channelManager: VoiceChannelManager = VoiceChannelManager(context),
    private val voiceRepository: VoiceRepository = VoiceRepository()
) : ViewModel() {

    val state: StateFlow<VoiceState> = channelManager.state

    /**
     * Joins the real voice channel for this Gate. Requires RECORD_AUDIO to
     * already be granted — the caller (GateScreen) handles that permission
     * request before calling this.
     */
    fun joinGateVoice(gateId: String) = joinChannel(gateId)

    /**
     * Generic real Agora channel join — same underlying engine/token flow as
     * joinGateVoice, just parameterized by channel name so it can also be used
     * for a Guild-wide voice channel (channelName = "guild_" + guildId).
     * Requires RECORD_AUDIO to already be granted — callers handle that
     * permission request before calling this.
     */
    fun joinChannel(channelName: String) {
        viewModelScope.launch {
            runCatching { voiceRepository.fetchToken(channelName = channelName) }
                .onSuccess { result ->
                    channelManager.join(result.appId, result.token, channelName, result.uid)
                }
                .onFailure { e ->
                    Log.e("VoiceViewModel", "Failed to join voice channel $channelName", e)
                }
        }
    }

    fun toggleMic() {
        val muted = state.value.isMicMuted
        channelManager.setMicMuted(!muted)
    }

    fun leaveVoice() {
        channelManager.leave()
    }

    override fun onCleared() {
        channelManager.release()
        super.onCleared()
    }
}
