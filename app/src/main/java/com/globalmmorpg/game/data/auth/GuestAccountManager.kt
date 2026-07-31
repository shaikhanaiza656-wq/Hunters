package com.globalmmorpg.game.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Tracks guest-account activity in Firestore so the server-side scheduled
 * Cloud Function (see /functions/index.js) can delete guest accounts that
 * have been inactive for 30+ days, matching GDD rule 4.
 */
class GuestAccountManager(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    /** Call this on every successful guest sign-in and periodically during play. */
    suspend fun touchLastActive(uid: String) {
        val data = mapOf(
            "uid" to uid,
            "isGuest" to true,
            "lastActiveAt" to com.google.firebase.Timestamp.now()
        )
        firestore.collection("guestAccounts")
            .document(uid)
            .set(data, SetOptions.merge())
            .await()
    }

    suspend fun markActiveNow() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        touchLastActive(uid)
    }
}
