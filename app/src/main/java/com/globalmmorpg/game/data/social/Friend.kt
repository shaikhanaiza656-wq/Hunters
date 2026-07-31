package com.globalmmorpg.game.data.social

enum class FriendRequestStatus { PENDING, ACCEPTED, DECLINED }

data class FriendRequest(
    val id: String = "",
    val fromUid: String = "",
    val fromUsername: String = "",
    val toUid: String = "",
    val toUsername: String = "",
    val status: FriendRequestStatus = FriendRequestStatus.PENDING,
    val createdAtEpochMs: Long = 0L
) {
    fun toMap(): Map<String, Any> = mapOf(
        "fromUid" to fromUid,
        "fromUsername" to fromUsername,
        "toUid" to toUid,
        "toUsername" to toUsername,
        "status" to status.name,
        "createdAtEpochMs" to createdAtEpochMs
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): FriendRequest = FriendRequest(
            id = id,
            fromUid = (map["fromUid"] as? String) ?: "",
            fromUsername = (map["fromUsername"] as? String) ?: "",
            toUid = (map["toUid"] as? String) ?: "",
            toUsername = (map["toUsername"] as? String) ?: "",
            status = (map["status"] as? String)?.let { FriendRequestStatus.valueOf(it) }
                ?: FriendRequestStatus.PENDING,
            createdAtEpochMs = (map["createdAtEpochMs"] as? Long) ?: 0L
        )
    }
}

/** A confirmed friend relation, denormalized onto the owning user for a cheap list read. */
data class Friend(
    val uid: String = "",
    val username: String = "",
    val sinceEpochMs: Long = 0L
) {
    fun toMap(): Map<String, Any> = mapOf(
        "uid" to uid,
        "username" to username,
        "sinceEpochMs" to sinceEpochMs
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): Friend = Friend(
            uid = (map["uid"] as? String) ?: "",
            username = (map["username"] as? String) ?: "",
            sinceEpochMs = (map["sinceEpochMs"] as? Long) ?: 0L
        )
    }
}
