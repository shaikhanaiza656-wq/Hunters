package com.globalmmorpg.game.ui.social

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.globalmmorpg.game.data.social.Friend
import com.globalmmorpg.game.data.social.FriendRequest
import com.globalmmorpg.game.data.social.GuildInvite
import com.globalmmorpg.game.data.social.GuildMessage
import com.globalmmorpg.game.data.voice.VoiceConnectionState
import com.globalmmorpg.game.ui.voice.VoiceControls
import com.globalmmorpg.game.ui.voice.VoiceViewModel

private enum class SocialTab { FRIENDS, GUILD }

@Composable
fun SocialScreen(
    viewModel: SocialViewModel,
    voiceViewModel: VoiceViewModel,
    onBack: () -> Unit
) {
    val profile by viewModel.profile.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()
    var tab by remember { mutableStateOf(SocialTab.FRIENDS) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Guild & Friends") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (statusMessage != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(statusMessage ?: "", modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.clearStatusMessage() }) { Text("Dismiss") }
                    }
                }
            }

            if (profile == null || profile?.username.isNullOrBlank()) {
                UsernameSetupPanel(isBusy = isBusy, onSubmit = { viewModel.claimUsername(it) })
                return@Column
            }

            TabRow(selectedTabIndex = tab.ordinal) {
                Tab(selected = tab == SocialTab.FRIENDS, onClick = { tab = SocialTab.FRIENDS }, text = { Text("Friends") })
                Tab(selected = tab == SocialTab.GUILD, onClick = { tab = SocialTab.GUILD }, text = { Text("Guild") })
            }

            if (isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when (tab) {
                SocialTab.FRIENDS -> FriendsTab(viewModel)
                SocialTab.GUILD -> GuildTab(viewModel, voiceViewModel)
            }
        }
    }
}

@Composable
private fun UsernameSetupPanel(isBusy: Boolean, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Choose a username", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Friends and guildmates will find you by this name (3-16 letters, digits, or _).",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Username") }, singleLine = true)
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = { onSubmit(text) }, enabled = !isBusy && text.isNotBlank()) {
            Text("Save username")
        }
    }
}

@Composable
private fun FriendsTab(viewModel: SocialViewModel) {
    val friends by viewModel.friends.collectAsState()
    val incoming by viewModel.incomingFriendRequests.collectAsState()
    var addUsername by remember { mutableStateOf("") }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = addUsername,
                    onValueChange = { addUsername = it },
                    label = { Text("Add friend by username") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        viewModel.sendFriendRequest(addUsername)
                        addUsername = ""
                    },
                    enabled = addUsername.isNotBlank()
                ) { Text("Add") }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (incoming.isNotEmpty()) {
            item { Text("Pending requests", style = MaterialTheme.typography.titleSmall) }
            items(incoming, key = { it.id }) { request -> IncomingFriendRequestRow(request, viewModel) }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        item { Text("Friends (${friends.size})", style = MaterialTheme.typography.titleSmall) }
        items(friends, key = { it.uid }) { friend -> FriendRow(friend, viewModel) }
    }
}

@Composable
private fun IncomingFriendRequestRow(request: FriendRequest, viewModel: SocialViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(request.fromUsername)
        Row {
            TextButton(onClick = { viewModel.acceptFriendRequest(request) }) { Text("Accept") }
            TextButton(onClick = { viewModel.declineFriendRequest(request) }) { Text("Decline") }
        }
    }
}

@Composable
private fun FriendRow(friend: Friend, viewModel: SocialViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(friend.username)
        TextButton(onClick = { viewModel.removeFriend(friend) }) { Text("Remove") }
    }
}

@Composable
private fun GuildTab(viewModel: SocialViewModel, voiceViewModel: VoiceViewModel) {
    val guild by viewModel.guild.collectAsState()
    val members by viewModel.guildMembers.collectAsState()
    val invites by viewModel.incomingGuildInvites.collectAsState()
    val profile by viewModel.profile.collectAsState()

    if (guild == null) {
        NoGuildPanel(invites = invites, viewModel = viewModel)
        return
    }

    val isLeader = guild?.leaderUid == profile?.uid
    var inviteUsername by remember { mutableStateOf("") }

    // Guild voice is joined on demand (unlike Gate voice, which auto-joins) —
    // players might sit in the Guild tab just to chat or manage the roster.
    // Leaving this tab also leaves the voice channel, same lifecycle pattern
    // GateScreen uses for Gate voice.
    val context = LocalContext.current
    val voiceState by voiceViewModel.state.collectAsState()
    val guildId = guild?.id
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && guildId != null) voiceViewModel.joinChannel("guild_$guildId")
    }
    DisposableEffect(guildId) {
        onDispose { voiceViewModel.leaveVoice() }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        item {
            Text("[${guild?.tag}] ${guild?.name}", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text("${members.size} / ${com.globalmmorpg.game.data.social.GuildRepository.MAX_MEMBERS} members")
            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                VoiceControls(state = voiceState, onToggleMic = { voiceViewModel.toggleMic() })
                Spacer(modifier = Modifier.width(12.dp))
                if (voiceState.connectionState == VoiceConnectionState.DISCONNECTED ||
                    voiceState.connectionState == VoiceConnectionState.FAILED
                ) {
                    Button(onClick = {
                        val hasMic = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasMic) {
                            guildId?.let { voiceViewModel.joinChannel("guild_$it") }
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }) { Text("Join Voice") }
                } else {
                    OutlinedButton(onClick = { voiceViewModel.leaveVoice() }) { Text("Leave Voice") }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (isLeader) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = inviteUsername,
                        onValueChange = { inviteUsername = it },
                        label = { Text("Invite by username") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.inviteToGuild(inviteUsername)
                            inviteUsername = ""
                        },
                        enabled = inviteUsername.isNotBlank()
                    ) { Text("Invite") }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        items(members, key = { it.uid }) { member ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(member.username + if (member.uid == guild?.leaderUid) "  (Leader)" else "")
                if (isLeader && member.uid != profile?.uid) {
                    TextButton(onClick = { viewModel.kickMember(member.uid) }) { Text("Kick") }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Guild Chat", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            GuildChatPanel(viewModel = viewModel, myUid = profile?.uid)
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            if (isLeader) {
                OutlinedButton(onClick = { viewModel.disbandGuild() }) { Text("Disband guild") }
            } else {
                OutlinedButton(onClick = { viewModel.leaveGuild() }) { Text("Leave guild") }
            }
        }
    }
}

/** Live, bounded-height chat panel — nested LazyColumn is fine since its height is fixed. */
@Composable
private fun GuildChatPanel(viewModel: SocialViewModel, myUid: String?) {
    val messages by viewModel.guildMessages.collectAsState()
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column {
        Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth().height(220.dp)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(8.dp)) {
                items(messages, key = { it.id }) { message -> ChatMessageRow(message, myUid) }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Message") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    viewModel.sendGuildMessage(draft)
                    draft = ""
                },
                enabled = draft.isNotBlank()
            ) { Text("Send") }
        }
    }
}

@Composable
private fun ChatMessageRow(message: GuildMessage, myUid: String?) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = if (message.senderUid == myUid) "You: " else "${message.senderUsername}: ",
            style = MaterialTheme.typography.labelMedium
        )
        Text(text = message.text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NoGuildPanel(invites: List<GuildInvite>, viewModel: SocialViewModel) {
    var guildName by remember { mutableStateOf("") }
    var guildTag by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (invites.isNotEmpty()) {
            Text("Guild invites", style = MaterialTheme.typography.titleSmall)
            invites.forEach { invite ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("[${invite.guildName}] from ${invite.fromUsername}")
                    Row {
                        TextButton(onClick = { viewModel.acceptGuildInvite(invite) }) { Text("Accept") }
                        TextButton(onClick = { viewModel.declineGuildInvite(invite) }) { Text("Decline") }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        Text("Create a guild", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = guildName, onValueChange = { guildName = it }, label = { Text("Guild name") }, singleLine = true)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = guildTag, onValueChange = { guildTag = it }, label = { Text("Tag (2-5 chars)") }, singleLine = true)
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { viewModel.createGuild(guildName, guildTag) },
            enabled = guildName.isNotBlank() && guildTag.isNotBlank()
        ) { Text("Create") }
    }
}
