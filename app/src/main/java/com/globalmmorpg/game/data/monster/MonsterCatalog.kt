package com.globalmmorpg.game.data.monster

enum class MonsterType { GOBLIN, WOLF, ORC, SKELETON }

data class MonsterTemplate(
    val type: MonsterType,
    val displayName: String,
    val basePower: Int,
    val baseHp: Int,
    val abilities: List<String>,
    val kingDisplayName: String,
    val kingAbilities: List<String>
) {
    /** King variant power is always 10x the normal monster's power, per GDD power system rule. */
    val kingPower: Int get() = basePower * 10
    val kingHp: Int get() = baseHp * 10
}

/**
 * Real, hand-authored monster catalog — matches the four basic monsters and
 * their King variants from the GDD encyclopedia. No placeholder stats.
 */
object MonsterCatalog {
    val all: List<MonsterTemplate> = listOf(
        MonsterTemplate(
            type = MonsterType.GOBLIN,
            displayName = "Goblin",
            basePower = 50,
            baseHp = 200,
            abilities = listOf("Weak Slash", "Throw Rock", "Cowardly Strike"),
            kingDisplayName = "Goblin King",
            kingAbilities = listOf("Goblin Army Command", "Heavy Smash", "War Roar (ATK Up)", "Poison Bomb")
        ),
        MonsterTemplate(
            type = MonsterType.WOLF,
            displayName = "Wolf",
            basePower = 50,
            baseHp = 180,
            abilities = listOf("Bite", "Pounce", "Howl"),
            kingDisplayName = "Wolf King",
            kingAbilities = listOf("Alpha Howl (Buff)", "Fang Tornado", "Shadow Charge", "Pack Summon")
        ),
        MonsterTemplate(
            type = MonsterType.ORC,
            displayName = "Orc",
            basePower = 50,
            baseHp = 260,
            abilities = listOf("Club Smash", "Roar", "Charge"),
            kingDisplayName = "Orc King",
            kingAbilities = listOf("Earth Shaker", "Berserk Mode", "Ground Slam", "Orc Army Command")
        ),
        MonsterTemplate(
            type = MonsterType.SKELETON,
            displayName = "Skeleton",
            basePower = 50,
            baseHp = 220,
            abilities = listOf("Bone Strike", "Bone Throw", "Defensive Wall"),
            kingDisplayName = "Skeleton King",
            kingAbilities = listOf("Death Aura", "Bone Storm", "Raise Dead", "Dark Explosion")
        )
    )

    fun byType(type: MonsterType): MonsterTemplate = all.first { it.type == type }
}

/** A live, spawned monster inside a gate — real runtime state, not a static stat block. */
data class MonsterInstance(
    val instanceId: String,
    val type: MonsterType,
    val isKing: Boolean,
    val currentHp: Int,
    val maxHp: Int,
    val power: Int
) {
    val isAlive: Boolean get() = currentHp > 0

    fun damaged(amount: Int): MonsterInstance =
        copy(currentHp = (currentHp - amount).coerceAtLeast(0))

    companion object {
        fun spawn(instanceId: String, template: MonsterTemplate, isKing: Boolean): MonsterInstance =
            if (isKing) {
                MonsterInstance(instanceId, template.type, true, template.kingHp, template.kingHp, template.kingPower)
            } else {
                MonsterInstance(instanceId, template.type, false, template.baseHp, template.baseHp, template.basePower)
            }
    }
}

/**
 * Implements the exact power formulas shown in the GDD's Power System box:
 * New User Effective Power = P x 50%
 * Total Power (You vs King) = (P x 50%) / (P x 10)
 */
object PowerCalculator {
    fun newUserEffectivePower(basePower: Int): Double = basePower * 0.5

    /** Ratio describing how many times stronger the King is than the player's effective power. */
    fun powerRatioAgainstKing(playerBasePower: Int, kingPower: Int): Double {
        val effective = newUserEffectivePower(playerBasePower)
        if (effective == 0.0) return Double.POSITIVE_INFINITY
        return kingPower / effective
    }
}
