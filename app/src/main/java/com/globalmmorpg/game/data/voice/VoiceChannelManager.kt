package com.globalmmorpg.game.data.voice

import android.content.Context
import android.util.Log
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Real Agora RTC voice channel wrapper (Phase 5 — GDD voice requirement:
 * "like Free Fire" — an always-open mic during the call, not push-to-talk
 * beeps). This drives the actual io.agora.rtc2.RtcEngine; there is no
 * fake/local audio simulation anywhere in this class.
 *
 * Channel scope: one Agora channel per Gate (channelName = gate id), so
 * everyone fighting the same Gate shares one live voice call. World/guild-wide
 * voice channels are future scope, same as Red Gates in the GDD.
 */
class VoiceChannelManager(private val context: Context) {

    private var engine: RtcEngine? = null

    private val _state = MutableStateFlow(VoiceState())
    val state: StateFlow<VoiceState> = _state

    private val eventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            _state.update { it.copy(connectionState = VoiceConnectionState.CONNECTED, error = null) }
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            _state.update { current ->
                if (current.participants.any { it.remoteUid == uid }) return@update current
                current.copy(participants = current.participants + VoiceParticipant(uid))
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            _state.update { current ->
                current.copy(participants = current.participants.filterNot { it.remoteUid == uid })
            }
        }

        override fun onAudioVolumeIndication(speakers: Array<AudioVolumeInfo>?, totalVolume: Int) {
            val speakingUids = speakers?.filter { it.volume > 5 }?.map { it.uid }?.toSet() ?: emptySet()
            _state.update { current ->
                current.copy(
                    participants = current.participants.map { p ->
                        p.copy(isSpeaking = speakingUids.contains(p.remoteUid))
                    }
                )
            }
        }

        override fun onError(err: Int) {
            Log.e("VoiceChannelManager", "Agora error code: $err")
            _state.update { it.copy(error = "Voice error code $err", connectionState = VoiceConnectionState.FAILED) }
        }
    }

    /** Creates the real Agora engine once (lazy singleton per manager instance). */
    private fun ensureEngine(appId: String): RtcEngine {
        engine?.let { return it }
        val config = RtcEngineConfig().apply {
            mContext = context.applicationContext
            mAppId = appId
            mEventHandler = eventHandler
        }
        val created = RtcEngine.create(config)
        created.enableAudio()
        // Volume indication callback every 300ms, smoothed over 3 frames —
        // drives the real per-participant "who's talking" indicator.
        created.enableAudioVolumeIndication(300, 3, true)
        engine = created
        return created
    }

    /**
     * Joins the given Gate's real voice channel using a server-signed Agora
     * token (see VoiceRepository — the token is never generated on-device).
     * Mic is unmuted immediately on join: an open call, like Free Fire.
     */
    fun join(appId: String, token: String, channelName: String, localUid: Int) {
        _state.update { it.copy(connectionState = VoiceConnectionState.CONNECTING, error = null) }
        val rtc = ensureEngine(appId)
        rtc.muteLocalAudioStream(false)
        val options = ChannelMediaOptions().apply {
            channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            publishMicrophoneTrack = true
            autoSubscribeAudio = true
        }
        rtc.joinChannel(token, channelName, localUid, options)
        _state.update { it.copy(isMicMuted = false) }
    }

    fun setMicMuted(muted: Boolean) {
        engine?.muteLocalAudioStream(muted)
        _state.update { it.copy(isMicMuted = muted) }
    }

    /** Leaves the current channel but keeps the engine alive for the next Gate. */
    fun leave() {
        engine?.leaveChannel()
        _state.update { VoiceState() }
    }

    /** Fully tears down the engine — call from onCleared() of the owning ViewModel. */
    fun release() {
        engine?.leaveChannel()
        RtcEngine.destroy()
        engine = null
        _state.update { VoiceState() }
    }
}
