package com.globalmmorpg.game.data.social

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Real-time guild text chat, backed by Firestore.
 *
 * guilds/{guildId}/messages/{messageId} -> GuildMessage
 *
 * `listenMessages` uses a real Firestore snapshot listener (not polling) so
 * new messages appear live for every guildmate. Only the most recent
 * [MESSAGE_LIMIT] messages are kept in view, matching how a live chat panel
 * (not a full searchable history) is actually meant to behave.
 *
 * Known scope gap, flagged honestly: no server-side profanity filter, rate
 * limiting, or report/mute tooling yet — a future Trust & Safety pass, same
 * category of "left for later" as Red Gates and City Defense elsewhere in
 * this project.
 */
class GuildChatRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        const val MESSAGE_LIMIT = 100L
        const val MAX_MESSAGE_LENGTH = 500
    }

    private fun messagesCollection(guildId: String) =
        firestore.collection("guilds").document(guildId).collection("messages")

    suspend fun sendMessage(guildId: String, sender: PublicProfile, text: String) {
        val trimmed = text.trim().take(MAX_MESSAGE_LENGTH)
        if (trimmed.isEmpty()) return
        val ref = messagesCollection(guildId).document()
        val message = GuildMessage(
            id = ref.id,
            senderUid = sender.uid,
            senderUsername = sender.username,
            text = trimmed,
            sentAtEpochMs = System.currentTimeMillis()
        )
        ref.set(message.toMap()).await()
    }

    /** Live-updating list of the most recent messages, oldest first (ready to render top-to-bottom). */
    fun listenMessages(guildId: String): Flow<List<GuildMessage>> = callbackFlow {
        val registration = messagesCollection(guildId)
            .orderBy("sentAtEpochMs", Query.Direction.DESCENDING)
            .limit(MESSAGE_LIMIT)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val messages = snapshot.documents
                    .mapNotNull { doc -> doc.data?.let { GuildMessage.fromMap(doc.id, it) } }
                    .reversed()
                trySend(messages)
            }
        awaitClose { registration.remove() }
    }
}
