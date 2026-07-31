package com.globalmmorpg.game.data.gate

enum class GateType {
    NORMAL,
    /** GDD marks Red Gates as "FUTURE" — modeled now, locked out of spawn selection until enabled. */
    RED
}

enum class GateStatus { ACTIVE, CLEARED, BROKEN }

data class Gate(
    val id: String = "",
    val type: GateType = GateType.NORMAL,
    val createdAtEpochMs: Long = 0L,
    val timeLimitSeconds: Int = 600,
    val status: GateStatus = GateStatus.ACTIVE,
    val bossDefeated: Boolean = false,
    val monsterInstanceIds: List<String> = emptyList()
) {
    fun secondsRemaining(nowEpochMs: Long): Int {
        val elapsedSeconds = ((nowEpochMs - createdAtEpochMs) / 1000L).toInt()
        return (timeLimitSeconds - elapsedSeconds).coerceAtLeast(0)
    }

    /** Normal Gates: exit anytime while active. Red Gates: no exit once entered (GDD rule). */
    fun canExit(): Boolean = type == GateType.NORMAL && status == GateStatus.ACTIVE

    fun toMap(): Map<String, Any> = mapOf(
        "type" to type.name,
        "createdAtEpochMs" to createdAtEpochMs,
        "timeLimitSeconds" to timeLimitSeconds,
        "status" to status.name,
        "bossDefeated" to bossDefeated,
        "monsterInstanceIds" to monsterInstanceIds
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): Gate = Gate(
            id = id,
            type = (map["type"] as? String)?.let { GateType.valueOf(it) } ?: GateType.NORMAL,
            createdAtEpochMs = (map["createdAtEpochMs"] as? Long) ?: 0L,
            timeLimitSeconds = (map["timeLimitSeconds"] as? Long)?.toInt() ?: 600,
            status = (map["status"] as? String)?.let { GateStatus.valueOf(it) } ?: GateStatus.ACTIVE,
            bossDefeated = map["bossDefeated"] as? Boolean ?: false,
            monsterInstanceIds = (map["monsterInstanceIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        )
    }
}
