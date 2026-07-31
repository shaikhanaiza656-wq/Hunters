package com.globalmmorpg.game.data.social

data class GuildMessage(
    val id: String = "",
    val senderUid: String = "",
    val senderUsername: String = "",
    val text: String = "",
    val sentAtEpochMs: Long = 0L
) {
    fun toMap(): Map<String, Any> = mapOf(
        "senderUid" to senderUid,
        "senderUsername" to senderUsername,
        "text" to text,
        "sentAtEpochMs" to sentAtEpochMs
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): GuildMessage = GuildMessage(
            id = id,
            senderUid = (map["senderUid"] as? String) ?: "",
            senderUsername = (map["senderUsername"] as? String) ?: "",
            text = (map["text"] as? String) ?: "",
            sentAtEpochMs = (map["sentAtEpochMs"] as? Long) ?: 0L
        )
    }
}
