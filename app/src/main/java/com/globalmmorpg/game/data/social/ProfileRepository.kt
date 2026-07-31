package com.globalmmorpg.game.data.social

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Real Firestore-backed username directory.
 *
 * users/{uid}/profile/public       -> PublicProfile for that uid
 * usernames/{usernameLower}        -> { "uid": "<owner uid>" }  (uniqueness index + lookup)
 *
 * Username claiming runs inside a real Firestore transaction so two players racing to
 * claim the same name can't both win — same pattern real production social features use.
 * There is no fuzzy/prefix search here (Firestore doesn't support that natively without a
 * third-party search index like Algolia); lookup is by exact username, same limitation the
 * GDD leaves for a future search-index integration.
 */
class ProfileRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun profileDoc(uid: String) =
        firestore.collection("users").document(uid).collection("profile").document("public")

    private fun usernameDoc(usernameLower: String) =
        firestore.collection("usernames").document(usernameLower)

    suspend fun loadProfile(uid: String): PublicProfile? {
        val snapshot = profileDoc(uid).get().await()
        if (!snapshot.exists()) return null
        return PublicProfile.fromMap(uid, snapshot.data ?: return null)
    }

    /** Case-insensitive exact lookup, used to add friends / invite to a guild by name. */
    suspend fun findByUsername(username: String): PublicProfile? {
        val key = username.trim().lowercase()
        val indexSnapshot = usernameDoc(key).get().await()
        if (!indexSnapshot.exists()) return null
        val ownerUid = indexSnapshot.getString("uid") ?: return null
        return loadProfile(ownerUid)
    }

    /**
     * Claims a username for uid. Validates length/characters, then atomically checks the
     * usernames/{lower} index and either reserves it or reports it's taken — a real
     * uniqueness guarantee, not a client-side "looks free" check.
     */
    suspend fun claimUsername(uid: String, requested: String): UsernameResult {
        val trimmed = requested.trim()
        if (trimmed.length !in 3..16 || !trimmed.all { it.isLetterOrDigit() || it == '_' }) {
            return UsernameResult.Invalid("Username must be 3-16 letters, digits, or underscores")
        }
        val key = trimmed.lowercase()

        return try {
            val profile = firestore.runTransaction { txn ->
                val existingIndex = txn.get(usernameDoc(key))
                if (existingIndex.exists() && existingIndex.getString("uid") != uid) {
                    throw UsernameTakenException(trimmed)
                }

                val existingProfileSnap = txn.get(profileDoc(uid))
                val previousUsername = existingProfileSnap.getString("username")

                // Release the old reservation if this account is renaming.
                if (previousUsername != null && previousUsername.lowercase() != key) {
                    txn.delete(usernameDoc(previousUsername.lowercase()))
                }

                val guildId = existingProfileSnap.getString("guildId")?.takeIf { it.isNotBlank() }
                val newProfile = PublicProfile(uid = uid, username = trimmed, guildId = guildId)

                txn.set(usernameDoc(key), mapOf("uid" to uid))
                txn.set(profileDoc(uid), newProfile.toMap())
                newProfile
            }.await()
            UsernameResult.Success(profile)
        } catch (e: UsernameTakenException) {
            UsernameResult.Taken(e.username)
        } catch (e: Exception) {
            UsernameResult.Failure(e.message ?: "Failed to claim username")
        }
    }

    suspend fun setGuildId(uid: String, guildId: String?) {
        profileDoc(uid).set(
            mapOf("guildId" to (guildId ?: "")),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }

    private class UsernameTakenException(val username: String) : Exception("Username taken: $username")
}
