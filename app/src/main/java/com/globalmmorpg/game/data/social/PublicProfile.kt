package com.globalmmorpg.game.data.social

/**
 * The public identity a player is found by in Guild and Friends systems (GDD section 3).
 * Separate from CharacterProfile (Phase 2, appearance) and PlayerStats (Phase 3, combat) —
 * this is the one small piece of identity social features actually need: a searchable,
 * unique username, plus which guild (if any) the player currently belongs to.
 */
data class PublicProfile(
    val uid: String = "",
    val username: String = "",
    val guildId: String? = null
) {
    fun toMap(): Map<String, Any> = mapOf(
        "uid" to uid,
        "username" to username,
        "guildId" to (guildId ?: "")
    )

    companion object {
        fun fromMap(uid: String, map: Map<String, Any?>): PublicProfile = PublicProfile(
            uid = uid,
            username = (map["username"] as? String) ?: "",
            guildId = (map["guildId"] as? String)?.takeIf { it.isNotBlank() }
        )
    }
}

sealed class UsernameResult {
    data class Success(val profile: PublicProfile) : UsernameResult()
    data class Taken(val username: String) : UsernameResult()
    data class Invalid(val reason: String) : UsernameResult()
    data class Failure(val message: String) : UsernameResult()
}
