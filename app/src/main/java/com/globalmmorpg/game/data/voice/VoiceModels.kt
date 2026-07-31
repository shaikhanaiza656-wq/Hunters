package com.globalmmorpg.game.data.voice

/** A real remote party member currently in the voice channel (Agora uid, not a local mock). */
data class VoiceParticipant(
    val remoteUid: Int,
    val isSpeaking: Boolean = false
)

enum class VoiceConnectionState { DISCONNECTED, CONNECTING, CONNECTED, FAILED }

data class VoiceState(
    val connectionState: VoiceConnectionState = VoiceConnectionState.DISCONNECTED,
    /** Mic starts unmuted (open call) the moment a channel join succeeds — see VoiceChannelManager.join(). */
    val isMicMuted: Boolean = false,
    val participants: List<VoiceParticipant> = emptyList(),
    val error: String? = null
)
