package com.globalmmorpg.game.data.voice

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/**
 * Calls the real `generateAgoraToken` Cloud Function (functions/index.js).
 * The Agora App Certificate never leaves the server — the client only ever
 * receives a short-lived signed token, exactly like a production voice-chat
 * backend. No token is hardcoded or faked client-side.
 */
class VoiceRepository(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) {
    data class TokenResult(val token: String, val appId: String, val uid: Int)

    suspend fun fetchToken(channelName: String): TokenResult {
        val result = functions
            .getHttpsCallable("generateAgoraToken")
            .call(mapOf("channelName" to channelName))
            .await()

        @Suppress("UNCHECKED_CAST")
        val data = result.data as? Map<String, Any?>
            ?: error("Unexpected response from generateAgoraToken")

        val token = data["token"] as? String ?: error("Agora token missing from server response")
        val appId = data["appId"] as? String ?: error("Agora App ID missing from server response")
        val uid = (data["uid"] as? Long)?.toInt() ?: 0
        return TokenResult(token, appId, uid)
    }
}
