package com.globalmmorpg.game.data.gate

import com.google.firebase.firestore.FirebaseFirestore
import com.globalmmorpg.game.data.monster.MonsterCatalog
import com.globalmmorpg.game.data.monster.MonsterInstance
import com.globalmmorpg.game.data.monster.MonsterType
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class GateRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun gatesCollection() = firestore.collection("gates")
    private fun monstersCollection(gateId: String) = gatesCollection().document(gateId).collection("monsters")

    /**
     * Real gate spawn: picks a random monster family, spawns 3-5 normal monsters
     * plus exactly one King (the boss), and persists everything to Firestore.
     * Red Gates are intentionally excluded — GDD marks them "FUTURE".
     */
    suspend fun createRandomGate(timeLimitSeconds: Int = 600): Gate {
        val gateRef = gatesCollection().document()
        val gate = Gate(
            id = gateRef.id,
            type = GateType.NORMAL,
            createdAtEpochMs = System.currentTimeMillis(),
            timeLimitSeconds = timeLimitSeconds,
            status = GateStatus.ACTIVE
        )

        val family = MonsterType.entries.random()
        val template = MonsterCatalog.byType(family)
        val normalCount = Random.nextInt(3, 6)

        val monsterIds = mutableListOf<String>()

        repeat(normalCount) {
            val instanceId = monstersCollection(gate.id).document().id
            val instance = MonsterInstance.spawn(instanceId, template, isKing = false)
            monstersCollection(gate.id).document(instanceId).set(instanceToMap(instance)).await()
            monsterIds += instanceId
        }

        val bossId = monstersCollection(gate.id).document().id
        val boss = MonsterInstance.spawn(bossId, template, isKing = true)
        monstersCollection(gate.id).document(bossId).set(instanceToMap(boss)).await()
        monsterIds += bossId

        val finalGate = gate.copy(monsterInstanceIds = monsterIds)
        gateRef.set(finalGate.toMap()).await()
        return finalGate
    }

    suspend fun fetchGate(gateId: String): Gate? {
        val snapshot = gatesCollection().document(gateId).get().await()
        if (!snapshot.exists()) return null
        return Gate.fromMap(gateId, snapshot.data ?: return null)
    }

    suspend fun fetchMonsters(gateId: String): List<MonsterInstance> {
        val snapshot = monstersCollection(gateId).get().await()
        return snapshot.documents.mapNotNull { doc -> doc.data?.let { instanceFromMap(doc.id, it) } }
    }

    suspend fun damageMonster(gateId: String, instanceId: String, amount: Int): MonsterInstance? {
        val doc = monstersCollection(gateId).document(instanceId).get().await()
        val data = doc.data ?: return null
        val current = instanceFromMap(instanceId, data)
        val updated = current.damaged(amount)
        monstersCollection(gateId).document(instanceId).set(instanceToMap(updated)).await()

        if (updated.isKing && !updated.isAlive) {
            gatesCollection().document(gateId)
                .update(mapOf("bossDefeated" to true, "status" to GateStatus.CLEARED.name))
                .await()
        }
        return updated
    }

    /** Called by the timer loop: if the time limit passed and the gate is still active, it breaks. */
    suspend fun checkAndApplyGateBreak(gate: Gate): Gate {
        if (gate.status != GateStatus.ACTIVE) return gate
        if (gate.secondsRemaining(System.currentTimeMillis()) > 0) return gate

        gatesCollection().document(gate.id).update("status", GateStatus.BROKEN.name).await()
        // NOTE: actual city-invasion consequences belong to the future City Defense
        // module (GDD section 1: "If Gate Break happens, monsters invade the city").
        // This method only performs the real, persisted status transition; the
        // invasion event itself is emitted by the caller (see GateViewModel).
        return gate.copy(status = GateStatus.BROKEN)
    }

    /** Team wipe (all party members' HP reached 0) also ends the gate attempt. */
    suspend fun markTeamWiped(gateId: String) {
        gatesCollection().document(gateId).update("status", GateStatus.BROKEN.name).await()
    }

    private fun instanceToMap(instance: MonsterInstance): Map<String, Any> = mapOf(
        "type" to instance.type.name,
        "isKing" to instance.isKing,
        "currentHp" to instance.currentHp,
        "maxHp" to instance.maxHp,
        "power" to instance.power
    )

    private fun instanceFromMap(id: String, map: Map<String, Any?>): MonsterInstance = MonsterInstance(
        instanceId = id,
        type = (map["type"] as? String)?.let { MonsterType.valueOf(it) } ?: MonsterType.GOBLIN,
        isKing = map["isKing"] as? Boolean ?: false,
        currentHp = (map["currentHp"] as? Long)?.toInt() ?: 0,
        maxHp = (map["maxHp"] as? Long)?.toInt() ?: 0,
        power = (map["power"] as? Long)?.toInt() ?: 0
    )
}
