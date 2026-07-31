package com.globalmmorpg.game.data.character

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Persists the created character to Firestore under users/{uid}/character/profile.
 * Works identically for guest, Google, and Facebook accounts since all three
 * produce a real Firebase uid.
 */
class CharacterRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun profileDoc(uid: String) =
        firestore.collection("users").document(uid)
            .collection("character").document("profile")

    suspend fun saveCharacter(profile: CharacterProfile) {
        profileDoc(profile.uid).set(profile.toMap()).await()
    }

    suspend fun loadCharacter(uid: String): CharacterProfile? {
        val snapshot = profileDoc(uid).get().await()
        if (!snapshot.exists()) return null
        return CharacterProfile.fromMap(uid, snapshot.data ?: return null)
    }

    suspend fun hasCharacter(uid: String): Boolean {
        return profileDoc(uid).get().await().exists()
    }
}
