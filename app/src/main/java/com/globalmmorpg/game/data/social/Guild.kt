package com.globalmmorpg.game.data.social

data class Guild(
    val id: String = "",
    val name: String = "",
    val tag: String = "",
    val leaderUid: String = "",
    val memberUids: List<String> = emptyList(),
    val createdAtEpochMs: Long = 0L
) {
    fun toMap(): Map<String, Any> = mapOf(
        "name" to name,
        "tag" to tag,
        "leaderUid" to leaderUid,
        "memberUids" to memberUids,
        "createdAtEpochMs" to createdAtEpochMs
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): Guild = Guild(
            id = id,
            name = (map["name"] as? String) ?: "",
            tag = (map["tag"] as? String) ?: "",
            leaderUid = (map["leaderUid"] as? String) ?: "",
            @Suppress("UNCHECKED_CAST")
            memberUids = (map["memberUids"] as? List<String>) ?: emptyList(),
            createdAtEpochMs = (map["createdAtEpochMs"] as? Long) ?: 0L
        )
    }
}

enum class GuildInviteStatus { PENDING, ACCEPTED, DECLINED }

data class GuildInvite(
    val id: String = "",
    val guildId: String = "",
    val guildName: String = "",
    val fromUid: String = "",
    val fromUsername: String = "",
    val toUid: String = "",
    val status: GuildInviteStatus = GuildInviteStatus.PENDING,
    val createdAtEpochMs: Long = 0L
) {
    fun toMap(): Map<String, Any> = mapOf(
        "guildId" to guildId,
        "guildName" to guildName,
        "fromUid" to fromUid,
        "fromUsername" to fromUsername,
        "toUid" to toUid,
        "status" to status.name,
        "createdAtEpochMs" to createdAtEpochMs
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): GuildInvite = GuildInvite(
            id = id,
            guildId = (map["guildId"] as? String) ?: "",
            guildName = (map["guildName"] as? String) ?: "",
            fromUid = (map["fromUid"] as? String) ?: "",
            fromUsername = (map["fromUsername"] as? String) ?: "",
            toUid = (map["toUid"] as? String) ?: "",
            status = (map["status"] as? String)?.let { GuildInviteStatus.valueOf(it) }
                ?: GuildInviteStatus.PENDING,
            createdAtEpochMs = (map["createdAtEpochMs"] as? Long) ?: 0L
        )
    }
}
