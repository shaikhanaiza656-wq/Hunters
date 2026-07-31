package com.globalmmorpg.game.data.social

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

sealed class GuildActionResult {
    data class Success(val guild: Guild) : GuildActionResult()
    data object AlreadyInGuild : GuildActionResult()
    data object NotInGuild : GuildActionResult()
    data object NotLeader : GuildActionResult()
    data object GuildFull : GuildActionResult()
    data object GuildNotFound : GuildActionResult()
    data class Invalid(val reason: String) : GuildActionResult()
    data class Failure(val message: String) : GuildActionResult()
}

/**
 * Real Firestore-backed Guild system (GDD section 3).
 *
 * guilds/{guildId}        -> Guild (name, tag, leader, memberUids)
 * guildInvites/{inviteId} -> GuildInvite (PENDING/ACCEPTED/DECLINED)
 * users/{uid}/profile/public.guildId -> kept in sync via ProfileRepository.setGuildId
 *
 * Membership changes (create/join/leave/kick/disband) run inside Firestore transactions so
 * the guild's memberUids array and the member's profile.guildId can never disagree, even
 * under concurrent requests.
 */
class GuildRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val profileRepository: ProfileRepository = ProfileRepository(firestore)
) {
    companion object {
        const val MAX_MEMBERS = 30
    }

    private fun guildsCollection() = firestore.collection("guilds")
    private fun invitesCollection() = firestore.collection("guildInvites")
    private fun profileDoc(uid: String) =
        firestore.collection("users").document(uid).collection("profile").document("public")

    suspend fun fetchGuild(guildId: String): Guild? {
        val snapshot = guildsCollection().document(guildId).get().await()
        if (!snapshot.exists()) return null
        return Guild.fromMap(guildId, snapshot.data ?: return null)
    }

    suspend fun fetchMemberProfiles(guild: Guild): List<PublicProfile> =
        guild.memberUids.mapNotNull { uid -> profileRepository.loadProfile(uid) }

    suspend fun createGuild(leader: PublicProfile, name: String, tag: String): GuildActionResult {
        val trimmedName = name.trim()
        val trimmedTag = tag.trim().uppercase()
        if (trimmedName.length !in 3..24) return GuildActionResult.Invalid("Guild name must be 3-24 characters")
        if (trimmedTag.length !in 2..5 || !trimmedTag.all { it.isLetterOrDigit() }) {
            return GuildActionResult.Invalid("Tag must be 2-5 letters/digits")
        }
        if (leader.guildId != null) return GuildActionResult.AlreadyInGuild

        return try {
            val guildRef = guildsCollection().document()
            val guild = firestore.runTransaction { txn ->
                val currentProfile = txn.get(profileDoc(leader.uid))
                val existingGuildId = currentProfile.getString("guildId")?.takeIf { it.isNotBlank() }
                if (existingGuildId != null) throw AlreadyInGuildException()

                val newGuild = Guild(
                    id = guildRef.id,
                    name = trimmedName,
                    tag = trimmedTag,
                    leaderUid = leader.uid,
                    memberUids = listOf(leader.uid),
                    createdAtEpochMs = System.currentTimeMillis()
                )
                txn.set(guildRef, newGuild.toMap())
                txn.set(profileDoc(leader.uid), mapOf("guildId" to guildRef.id), com.google.firebase.firestore.SetOptions.merge())
                newGuild
            }.await()
            GuildActionResult.Success(guild)
        } catch (e: AlreadyInGuildException) {
            GuildActionResult.AlreadyInGuild
        } catch (e: Exception) {
            GuildActionResult.Failure(e.message ?: "Failed to create guild")
        }
    }

    suspend fun invite(fromProfile: PublicProfile, toUsername: String): GuildActionResult {
        val guildId = fromProfile.guildId ?: return GuildActionResult.NotInGuild
        val guild = fetchGuild(guildId) ?: return GuildActionResult.GuildNotFound
        if (guild.memberUids.size >= MAX_MEMBERS) return GuildActionResult.GuildFull

        val toProfile = profileRepository.findByUsername(toUsername)
            ?: return GuildActionResult.Invalid("No player with that username")
        if (toProfile.guildId != null) return GuildActionResult.Invalid("${toProfile.username} is already in a guild")

        return try {
            val ref = invitesCollection().document()
            val invite = GuildInvite(
                id = ref.id,
                guildId = guild.id,
                guildName = guild.name,
                fromUid = fromProfile.uid,
                fromUsername = fromProfile.username,
                toUid = toProfile.uid,
                status = GuildInviteStatus.PENDING,
                createdAtEpochMs = System.currentTimeMillis()
            )
            ref.set(invite.toMap()).await()
            GuildActionResult.Success(guild)
        } catch (e: Exception) {
            GuildActionResult.Failure(e.message ?: "Failed to send guild invite")
        }
    }

    suspend fun incomingPendingInvites(uid: String): List<GuildInvite> {
        val snapshot = invitesCollection()
            .whereEqualTo("toUid", uid)
            .whereEqualTo("status", GuildInviteStatus.PENDING.name)
            .get().await()
        return snapshot.documents.mapNotNull { doc -> doc.data?.let { GuildInvite.fromMap(doc.id, it) } }
    }

    suspend fun acceptInvite(invite: GuildInvite, joiningUid: String): GuildActionResult {
        val guildRef = guildsCollection().document(invite.guildId)
        return try {
            val guild = firestore.runTransaction { txn ->
                val currentProfile = txn.get(profileDoc(joiningUid))
                if ((currentProfile.getString("guildId") ?: "").isNotBlank()) throw AlreadyInGuildException()

                val guildSnap = txn.get(guildRef)
                if (!guildSnap.exists()) throw GuildNotFoundException()
                @Suppress("UNCHECKED_CAST")
                val members = (guildSnap.get("memberUids") as? List<String>) ?: emptyList()
                if (members.size >= MAX_MEMBERS) throw GuildFullException()

                txn.update(guildRef, "memberUids", FieldValue.arrayUnion(joiningUid))
                txn.set(profileDoc(joiningUid), mapOf("guildId" to invite.guildId), com.google.firebase.firestore.SetOptions.merge())
                txn.update(invitesCollection().document(invite.id), "status", GuildInviteStatus.ACCEPTED.name)

                Guild.fromMap(invite.guildId, guildSnap.data ?: emptyMap())
                    .copy(memberUids = members + joiningUid)
            }.await()
            GuildActionResult.Success(guild)
        } catch (e: AlreadyInGuildException) {
            GuildActionResult.AlreadyInGuild
        } catch (e: GuildNotFoundException) {
            GuildActionResult.GuildNotFound
        } catch (e: GuildFullException) {
            GuildActionResult.GuildFull
        } catch (e: Exception) {
            GuildActionResult.Failure(e.message ?: "Failed to accept guild invite")
        }
    }

    suspend fun declineInvite(invite: GuildInvite) {
        invitesCollection().document(invite.id).update("status", GuildInviteStatus.DECLINED.name).await()
    }

    /** Leader leaving with other members present is rejected — must promote or disband first. */
    suspend fun leaveGuild(profile: PublicProfile): GuildActionResult {
        val guildId = profile.guildId ?: return GuildActionResult.NotInGuild
        val guildRef = guildsCollection().document(guildId)
        return try {
            firestore.runTransaction { txn ->
                val guildSnap = txn.get(guildRef)
                if (!guildSnap.exists()) throw GuildNotFoundException()
                val leaderUid = guildSnap.getString("leaderUid")
                @Suppress("UNCHECKED_CAST")
                val members = (guildSnap.get("memberUids") as? List<String>) ?: emptyList()

                if (leaderUid == profile.uid && members.size > 1) {
                    throw LeaderMustTransferException()
                }

                if (leaderUid == profile.uid && members.size <= 1) {
                    txn.delete(guildRef)
                } else {
                    txn.update(guildRef, "memberUids", FieldValue.arrayRemove(profile.uid))
                }
                txn.set(profileDoc(profile.uid), mapOf("guildId" to ""), com.google.firebase.firestore.SetOptions.merge())
            }.await()
            GuildActionResult.Success(Guild(id = guildId))
        } catch (e: LeaderMustTransferException) {
            GuildActionResult.Invalid("Transfer leadership or disband the guild before leaving")
        } catch (e: GuildNotFoundException) {
            GuildActionResult.GuildNotFound
        } catch (e: Exception) {
            GuildActionResult.Failure(e.message ?: "Failed to leave guild")
        }
    }

    suspend fun kickMember(leaderProfile: PublicProfile, targetUid: String): GuildActionResult {
        val guildId = leaderProfile.guildId ?: return GuildActionResult.NotInGuild
        val guildRef = guildsCollection().document(guildId)
        return try {
            firestore.runTransaction { txn ->
                val guildSnap = txn.get(guildRef)
                if (!guildSnap.exists()) throw GuildNotFoundException()
                if (guildSnap.getString("leaderUid") != leaderProfile.uid) throw NotLeaderException()

                txn.update(guildRef, "memberUids", FieldValue.arrayRemove(targetUid))
                txn.set(profileDoc(targetUid), mapOf("guildId" to ""), com.google.firebase.firestore.SetOptions.merge())
            }.await()
            GuildActionResult.Success(Guild(id = guildId))
        } catch (e: NotLeaderException) {
            GuildActionResult.NotLeader
        } catch (e: GuildNotFoundException) {
            GuildActionResult.GuildNotFound
        } catch (e: Exception) {
            GuildActionResult.Failure(e.message ?: "Failed to kick member")
        }
    }

    /** Leader-only: disbands the guild entirely and clears every member's guildId. */
    suspend fun disbandGuild(leaderProfile: PublicProfile): GuildActionResult {
        val guildId = leaderProfile.guildId ?: return GuildActionResult.NotInGuild
        val guild = fetchGuild(guildId) ?: return GuildActionResult.GuildNotFound
        if (guild.leaderUid != leaderProfile.uid) return GuildActionResult.NotLeader

        return try {
            guild.memberUids.forEach { uid ->
                profileDoc(uid).set(mapOf("guildId" to ""), com.google.firebase.firestore.SetOptions.merge()).await()
            }
            guildsCollection().document(guildId).delete().await()
            GuildActionResult.Success(guild)
        } catch (e: Exception) {
            GuildActionResult.Failure(e.message ?: "Failed to disband guild")
        }
    }

    private class AlreadyInGuildException : Exception()
    private class GuildNotFoundException : Exception()
    private class GuildFullException : Exception()
    private class LeaderMustTransferException : Exception()
    private class NotLeaderException : Exception()
}
