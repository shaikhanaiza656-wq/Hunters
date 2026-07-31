package com.globalmmorpg.game.data.player

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class PlayerRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun statsDoc(uid: String) =
        firestore.collection("users").document(uid).collection("player").document("stats")

    suspend fun loadStats(uid: String): PlayerStats {
        val snapshot = statsDoc(uid).get().await()
        return if (snapshot.exists()) {
            PlayerStats.fromMap(uid, snapshot.data ?: emptyMap())
        } else {
            val fresh = PlayerStats(uid = uid)
            statsDoc(uid).set(fresh.toMap()).await()
            fresh
        }
    }

    suspend fun saveStats(stats: PlayerStats) {
        statsDoc(stats.uid).set(stats.toMap()).await()
    }
}
