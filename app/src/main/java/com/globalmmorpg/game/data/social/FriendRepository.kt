package com.globalmmorpg.game.data.social

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

sealed class FriendRequestOutcome {
    data object Sent : FriendRequestOutcome()
    data object AlreadyFriends : FriendRequestOutcome()
    data object RequestAlreadyPending : FriendRequestOutcome()
    data object UserNotFound : FriendRequestOutcome()
    data object CannotFriendSelf : FriendRequestOutcome()
    data class Failure(val message: String) : FriendRequestOutcome()
}

/**
 * Real friend-request + friend-list system, backed by Firestore.
 *
 * friendRequests/{id}          -> FriendRequest (status PENDING/ACCEPTED/DECLINED)
 * users/{uid}/friends/{peerUid} -> Friend  (written on both sides when a request is accepted)
 */
class FriendRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun requestsCollection() = firestore.collection("friendRequests")
    private fun friendsCollection(uid: String) =
        firestore.collection("users").document(uid).collection("friends")

    suspend fun sendRequest(fromProfile: PublicProfile, toUsername: String, profileRepository: ProfileRepository): FriendRequestOutcome {
        val toProfile = profileRepository.findByUsername(toUsername)
            ?: return FriendRequestOutcome.UserNotFound
        if (toProfile.uid == fromProfile.uid) return FriendRequestOutcome.CannotFriendSelf

        return try {
            val alreadyFriends = friendsCollection(fromProfile.uid).document(toProfile.uid).get().await().exists()
            if (alreadyFriends) return FriendRequestOutcome.AlreadyFriends

            val pending = requestsCollection()
                .whereEqualTo("fromUid", fromProfile.uid)
                .whereEqualTo("toUid", toProfile.uid)
                .whereEqualTo("status", FriendRequestStatus.PENDING.name)
                .get().await()
            if (!pending.isEmpty) return FriendRequestOutcome.RequestAlreadyPending

            val ref = requestsCollection().document()
            val request = FriendRequest(
                id = ref.id,
                fromUid = fromProfile.uid,
                fromUsername = fromProfile.username,
                toUid = toProfile.uid,
                toUsername = toProfile.username,
                status = FriendRequestStatus.PENDING,
                createdAtEpochMs = System.currentTimeMillis()
            )
            ref.set(request.toMap()).await()
            FriendRequestOutcome.Sent
        } catch (e: Exception) {
            FriendRequestOutcome.Failure(e.message ?: "Failed to send friend request")
        }
    }

    suspend fun incomingPendingRequests(uid: String): List<FriendRequest> {
        val snapshot = requestsCollection()
            .whereEqualTo("toUid", uid)
            .whereEqualTo("status", FriendRequestStatus.PENDING.name)
            .get().await()
        return snapshot.documents.mapNotNull { doc -> doc.data?.let { FriendRequest.fromMap(doc.id, it) } }
    }

    /** Accepting writes the Friend relation to both users' friend lists — real mutual state. */
    suspend fun acceptRequest(request: FriendRequest) {
        val now = System.currentTimeMillis()
        requestsCollection().document(request.id)
            .update("status", FriendRequestStatus.ACCEPTED.name).await()
        friendsCollection(request.toUid).document(request.fromUid)
            .set(Friend(request.fromUid, request.fromUsername, now).toMap()).await()
        friendsCollection(request.fromUid).document(request.toUid)
            .set(Friend(request.toUid, request.toUsername, now).toMap()).await()
    }

    suspend fun declineRequest(request: FriendRequest) {
        requestsCollection().document(request.id)
            .update("status", FriendRequestStatus.DECLINED.name).await()
    }

    suspend fun listFriends(uid: String): List<Friend> {
        val snapshot = friendsCollection(uid).get().await()
        return snapshot.documents.mapNotNull { doc -> doc.data?.let { Friend.fromMap(it) } }
    }

    /** Removing is mutual — both sides' friend-list entries are deleted. */
    suspend fun removeFriend(uid: String, friendUid: String) {
        friendsCollection(uid).document(friendUid).delete().await()
        friendsCollection(friendUid).document(uid).delete().await()
    }
}
