package com.globalmmorpg.game.data.player

enum class HunterRank { E, D, C, B, A, S }

data class PlayerStats(
    val uid: String = "",
    val hp: Int = 100,
    val maxHp: Int = 100,
    val mana: Int = 100,
    val maxMana: Int = 100,
    val stamina: Int = 100,
    val maxStamina: Int = 100,
    val rank: HunterRank = HunterRank.E,
    val level: Int = 1
) {
    fun toMap(): Map<String, Any> = mapOf(
        "uid" to uid,
        "hp" to hp, "maxHp" to maxHp,
        "mana" to mana, "maxMana" to maxMana,
        "stamina" to stamina, "maxStamina" to maxStamina,
        "rank" to rank.name,
        "level" to level
    )

    companion object {
        fun fromMap(uid: String, map: Map<String, Any?>): PlayerStats = PlayerStats(
            uid = uid,
            hp = (map["hp"] as? Long)?.toInt() ?: 100,
            maxHp = (map["maxHp"] as? Long)?.toInt() ?: 100,
            mana = (map["mana"] as? Long)?.toInt() ?: 100,
            maxMana = (map["maxMana"] as? Long)?.toInt() ?: 100,
            stamina = (map["stamina"] as? Long)?.toInt() ?: 100,
            maxStamina = (map["maxStamina"] as? Long)?.toInt() ?: 100,
            rank = (map["rank"] as? String)?.let { HunterRank.valueOf(it) } ?: HunterRank.E,
            level = (map["level"] as? Long)?.toInt() ?: 1
        )
    }
}

/** A nearby entity shown as a dot on the minimap / as the current target. */
data class MapEntity(
    val id: String,
    val name: String,
    val relativeX: Float, // -1f..1f, player-centered
    val relativeY: Float, // -1f..1f, player-centered
    val isHostile: Boolean = true
)

data class Target(
    val name: String,
    val level: Int,
    val hp: Int,
    val maxHp: Int
)
