package com.globalmmorpg.game.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.globalmmorpg.game.data.social.Friend
import com.globalmmorpg.game.data.social.FriendRepository
import com.globalmmorpg.game.data.social.FriendRequest
import com.globalmmorpg.game.data.social.FriendRequestOutcome
import com.globalmmorpg.game.data.social.Guild
import com.globalmmorpg.game.data.social.GuildActionResult
import com.globalmmorpg.game.data.social.GuildChatRepository
import com.globalmmorpg.game.data.social.GuildInvite
import com.globalmmorpg.game.data.social.GuildMessage
import com.globalmmorpg.game.data.social.GuildRepository
import com.globalmmorpg.game.data.social.ProfileRepository
import com.globalmmorpg.game.data.social.PublicProfile
import com.globalmmorpg.game.data.social.UsernameResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SocialViewModel(
    private val uid: String,
    private val profileRepository: ProfileRepository,
    private val friendRepository: FriendRepository,
    private val guildRepository: GuildRepository,
    private val guildChatRepository: GuildChatRepository = GuildChatRepository()
) : ViewModel() {

    private val _profile = MutableStateFlow<PublicProfile?>(null)
    val profile: StateFlow<PublicProfile?> = _profile

    private val _friends = MutableStateFlow<List<Friend>>(emptyList())
    val friends: StateFlow<List<Friend>> = _friends

    private val _incomingFriendRequests = MutableStateFlow<List<FriendRequest>>(emptyList())
    val incomingFriendRequests: StateFlow<List<FriendRequest>> = _incomingFriendRequests

    private val _guild = MutableStateFlow<Guild?>(null)
    val guild: StateFlow<Guild?> = _guild

    private val _guildMembers = MutableStateFlow<List<PublicProfile>>(emptyList())
    val guildMembers: StateFlow<List<PublicProfile>> = _guildMembers

    private val _incomingGuildInvites = MutableStateFlow<List<GuildInvite>>(emptyList())
    val incomingGuildInvites: StateFlow<List<GuildInvite>> = _incomingGuildInvites

    private val _guildMessages = MutableStateFlow<List<GuildMessage>>(emptyList())
    val guildMessages: StateFlow<List<GuildMessage>> = _guildMessages

    private var chatListenerJob: Job? = null
    private var chatListenerGuildId: String? = null

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy

    init {
        refreshAll()
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun refreshAll() {
        viewModelScope.launch {
            _isBusy.value = true
            val loadedProfile = profileRepository.loadProfile(uid)
            _profile.value = loadedProfile
            _friends.value = friendRepository.listFriends(uid)
            _incomingFriendRequests.value = friendRepository.incomingPendingRequests(uid)
            _incomingGuildInvites.value = guildRepository.incomingPendingInvites(uid)

            val guildId = loadedProfile?.guildId
            if (guildId != null) {
                val fetchedGuild = guildRepository.fetchGuild(guildId)
                _guild.value = fetchedGuild
                _guildMembers.value = fetchedGuild?.let { guildRepository.fetchMemberProfiles(it) } ?: emptyList()
            } else {
                _guild.value = null
                _guildMembers.value = emptyList()
            }
            startOrUpdateChatListener(guildId)
            _isBusy.value = false
        }
    }

    /** Real-time guild chat: (re)subscribes only when the player's guildId actually changes. */
    private fun startOrUpdateChatListener(guildId: String?) {
        if (guildId == chatListenerGuildId) return
        chatListenerJob?.cancel()
        chatListenerGuildId = guildId
        _guildMessages.value = emptyList()
        if (guildId == null) return
        chatListenerJob = viewModelScope.launch {
            guildChatRepository.listenMessages(guildId).collect { messages ->
                _guildMessages.value = messages
            }
        }
    }

    fun sendGuildMessage(text: String) {
        val myProfile = _profile.value ?: return
        val guildId = myProfile.guildId ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            guildChatRepository.sendMessage(guildId, myProfile, text)
        }
    }

    fun claimUsername(desired: String) {
        viewModelScope.launch {
            _isBusy.value = true
            when (val result = profileRepository.claimUsername(uid, desired)) {
                is UsernameResult.Success -> {
                    _profile.value = result.profile
                    _statusMessage.value = "Username set to ${result.profile.username}"
                }
                is UsernameResult.Taken -> _statusMessage.value = "\"${result.username}\" is already taken"
                is UsernameResult.Invalid -> _statusMessage.value = result.reason
                is UsernameResult.Failure -> _statusMessage.value = result.message
            }
            _isBusy.value = false
        }
    }

    fun sendFriendRequest(toUsername: String) {
        val myProfile = _profile.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            when (val outcome = friendRepository.sendRequest(myProfile, toUsername, profileRepository)) {
                FriendRequestOutcome.Sent -> _statusMessage.value = "Friend request sent to $toUsername"
                FriendRequestOutcome.AlreadyFriends -> _statusMessage.value = "You're already friends with $toUsername"
                FriendRequestOutcome.RequestAlreadyPending -> _statusMessage.value = "Request already pending"
                FriendRequestOutcome.UserNotFound -> _statusMessage.value = "No player named $toUsername"
                FriendRequestOutcome.CannotFriendSelf -> _statusMessage.value = "You can't friend yourself"
                is FriendRequestOutcome.Failure -> _statusMessage.value = outcome.message
            }
            _isBusy.value = false
        }
    }

    fun acceptFriendRequest(request: FriendRequest) {
        viewModelScope.launch {
            _isBusy.value = true
            friendRepository.acceptRequest(request)
            _statusMessage.value = "You are now friends with ${request.fromUsername}"
            refreshAll()
        }
    }

    fun declineFriendRequest(request: FriendRequest) {
        viewModelScope.launch {
            friendRepository.declineRequest(request)
            refreshAll()
        }
    }

    fun removeFriend(friend: Friend) {
        viewModelScope.launch {
            friendRepository.removeFriend(uid, friend.uid)
            refreshAll()
        }
    }

    fun createGuild(name: String, tag: String) {
        val myProfile = _profile.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            handleGuildResult(guildRepository.createGuild(myProfile, name, tag), "Guild created!")
            _isBusy.value = false
        }
    }

    fun inviteToGuild(username: String) {
        val myProfile = _profile.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            handleGuildResult(guildRepository.invite(myProfile, username), "Invite sent to $username")
            _isBusy.value = false
        }
    }

    fun acceptGuildInvite(invite: GuildInvite) {
        viewModelScope.launch {
            _isBusy.value = true
            handleGuildResult(guildRepository.acceptInvite(invite, uid), "Joined ${invite.guildName}!")
            _isBusy.value = false
        }
    }

    fun declineGuildInvite(invite: GuildInvite) {
        viewModelScope.launch {
            guildRepository.declineInvite(invite)
            refreshAll()
        }
    }

    fun leaveGuild() {
        val myProfile = _profile.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            handleGuildResult(guildRepository.leaveGuild(myProfile), "You left the guild")
            _isBusy.value = false
        }
    }

    fun kickMember(targetUid: String) {
        val myProfile = _profile.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            handleGuildResult(guildRepository.kickMember(myProfile, targetUid), "Member removed")
            _isBusy.value = false
        }
    }

    fun disbandGuild() {
        val myProfile = _profile.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            handleGuildResult(guildRepository.disbandGuild(myProfile), "Guild disbanded")
            _isBusy.value = false
        }
    }

    private suspend fun handleGuildResult(result: GuildActionResult, successMessage: String) {
        when (result) {
            is GuildActionResult.Success -> {
                _statusMessage.value = successMessage
                refreshAll()
            }
            GuildActionResult.AlreadyInGuild -> _statusMessage.value = "You're already in a guild"
            GuildActionResult.NotInGuild -> _statusMessage.value = "You're not in a guild"
            GuildActionResult.NotLeader -> _statusMessage.value = "Only the guild leader can do that"
            GuildActionResult.GuildFull -> _statusMessage.value = "That guild is full"
            GuildActionResult.GuildNotFound -> _statusMessage.value = "Guild not found"
            is GuildActionResult.Invalid -> _statusMessage.value = result.reason
            is GuildActionResult.Failure -> _statusMessage.value = result.message
        }
    }
}

class SocialViewModelFactory(
    private val uid: String,
    private val profileRepository: ProfileRepository = ProfileRepository(),
    private val friendRepository: FriendRepository = FriendRepository(),
    private val guildRepository: GuildRepository = GuildRepository()
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SocialViewModel::class.java)) {
            return SocialViewModel(uid, profileRepository, friendRepository, guildRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
