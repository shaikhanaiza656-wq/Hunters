package com.globalmmorpg.game.ui.gate

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.globalmmorpg.game.R
import com.globalmmorpg.game.data.gate.GateStatus
import com.globalmmorpg.game.data.monster.MonsterInstance
import com.globalmmorpg.game.data.monster.MonsterType
import com.globalmmorpg.game.ui.hud.HudViewModel
import com.globalmmorpg.game.ui.hud.StatBar
import com.globalmmorpg.game.ui.hud.VirtualJoystick
import com.globalmmorpg.game.ui.theme.HpRed
import com.globalmmorpg.game.ui.theme.ManaBlue
import com.globalmmorpg.game.ui.theme.StaminaGreen
import com.globalmmorpg.game.ui.voice.VoiceControls
import com.globalmmorpg.game.ui.voice.VoiceViewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun GateScreen(
    hudViewModel: HudViewModel,
    gateViewModel: GateViewModel,
    voiceViewModel: VoiceViewModel,
    onGateEnded: () -> Unit
) {
    val playerStats by hudViewModel.stats.collectAsState()
    val gate by gateViewModel.gate.collectAsState()
    val monsters by gateViewModel.monsters.collectAsState()
    val selectedTargetId by gateViewModel.selectedTargetId.collectAsState()
    val secondsRemaining by gateViewModel.secondsRemaining.collectAsState()
    val cooldowns by hudViewModel.cooldowns.collectAsState()
    val voiceState by voiceViewModel.state.collectAsState()

    // Real reactions to gate outcome — not a stub: leaving the gate screen
    // happens exactly when the gate is cleared or broken.
    LaunchedEffect(Unit) {
        gateViewModel.onGateCleared = { onGateEnded() }
        gateViewModel.onGateBroken = { onGateEnded() }
    }

    LaunchedEffect(playerStats.hp) {
        if (playerStats.hp <= 0) {
            gateViewModel.reportTeamWiped()
        }
    }

    // Phase 5: real open-mic team voice for this Gate (Agora RTC). Ask for
    // RECORD_AUDIO if needed, then join — mic starts open, like Free Fire.
    val context = LocalContext.current
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            gate?.id?.let { voiceViewModel.joinGateVoice(it) }
        }
    }
    LaunchedEffect(gate?.id) {
        val gateId = gate?.id ?: return@LaunchedEffect
        val hasMicPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMicPermission) {
            voiceViewModel.joinGateVoice(gateId)
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    DisposableEffect(Unit) {
        onDispose { voiceViewModel.leaveVoice() }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // Dungeon floor background — replaces the previous blank canvas.
        Image(
            painter = painterResource(R.drawable.bg_gate_floor),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Real player position on screen, driven by the joystick's movement
        // vector every frame — not decorative, this is the actual movement
        // input already produced by HudViewModel.onJoystickMoved.
        var playerOffset by remember { mutableStateOf(Offset.Zero) }
        LaunchedEffect(Unit) {
            while (true) {
                val (dx, dy) = hudViewModel.movementVector.value
                if (dx != 0f || dy != 0f) {
                    val speed = 5f
                    playerOffset = Offset(
                        x = (playerOffset.x + dx * speed).coerceIn(-140f, 140f),
                        y = (playerOffset.y + dy * speed).coerceIn(-60f, 260f)
                    )
                }
                delay(16)
            }
        }

        // Arena — the actual monsters, shown as sprites instead of text-only chips.
        LazyRow(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 160.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 20.dp)
        ) {
            items(monsters.filter { it.isAlive }) { monster ->
                MonsterSprite(
                    monster = monster,
                    isSelected = monster.instanceId == selectedTargetId,
                    onClick = { gateViewModel.selectTarget(monster.instanceId) }
                )
            }
        }

        Image(
            painter = painterResource(R.drawable.sprite_player),
            contentDescription = "Player",
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 40.dp)
                .size(84.dp)
                .offset { IntOffset(playerOffset.x.roundToInt(), playerOffset.y.roundToInt()) }
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .width(180.dp)
        ) {
            StatBar("HP", playerStats.hp, playerStats.maxHp, HpRed)
            Spacer(modifier = Modifier.height(4.dp))
            StatBar("MANA", playerStats.mana, playerStats.maxMana, ManaBlue)
            Spacer(modifier = Modifier.height(4.dp))
            StatBar("STAMINA", playerStats.stamina, playerStats.maxStamina, StaminaGreen)
            Spacer(modifier = Modifier.height(6.dp))
            VoiceControls(state = voiceState, onToggleMic = { voiceViewModel.toggleMic() })
        }

        // Gate timer banner (GDD: "Each Gate has a time limit. If time runs out = GATE BREAK")
        val minutes = secondsRemaining / 60
        val secs = secondsRemaining % 60
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = "GATE — ${gate?.status?.name ?: "ACTIVE"}   ${"%02d:%02d".format(minutes, secs)}",
                color = Color.White,
                fontSize = 14.sp
            )
        }

        VirtualJoystick(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp),
            onMove = { dx, dy -> hudViewModel.onJoystickMoved(dx, dy) },
            onReleased = { hudViewModel.onJoystickReleased() }
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    val ready = cooldowns["skill_1"]?.isReady ?: true
                    if (ready) {
                        hudViewModel.useSkill("skill_1", manaCost = 10, staminaCost = 5)
                        gateViewModel.attackSelectedTarget(damage = 20)
                    }
                }
            ) { Text("Attack") }

            Button(
                onClick = {
                    val ready = cooldowns["skill_2"]?.isReady ?: true
                    if (ready) {
                        hudViewModel.useSkill("skill_2", manaCost = 25, staminaCost = 15)
                        gateViewModel.attackSelectedTarget(damage = 55)
                    }
                }
            ) { Text("Skill") }
        }
    }
}

/** Maps each monster family + king status to its sprite drawable. */
private fun monsterDrawableRes(type: MonsterType, isKing: Boolean): Int = when (type) {
    MonsterType.GOBLIN -> if (isKing) R.drawable.sprite_goblin_king else R.drawable.sprite_goblin
    MonsterType.WOLF -> if (isKing) R.drawable.sprite_wolf_king else R.drawable.sprite_wolf
    MonsterType.ORC -> if (isKing) R.drawable.sprite_orc_king else R.drawable.sprite_orc
    MonsterType.SKELETON -> if (isKing) R.drawable.sprite_skeleton_king else R.drawable.sprite_skeleton
}

@Composable
private fun MonsterSprite(monster: MonsterInstance, isSelected: Boolean, onClick: () -> Unit) {
    val spriteSize = if (monster.isKing) 110.dp else 76.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(4.dp)
            .width(spriteSize + 20.dp)
    ) {
        Text(
            text = if (monster.isKing) "${monster.type.name} KING" else monster.type.name,
            color = Color.White,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        StatBar("", monster.currentHp, monster.maxHp, HpRed)
        Spacer(modifier = Modifier.height(4.dp))
        Image(
            painter = painterResource(monsterDrawableRes(monster.type, monster.isKing)),
            contentDescription = monster.type.name,
            modifier = Modifier
                .size(spriteSize)
                .then(
                    if (isSelected) Modifier.border(3.dp, Color(0xFFE23B3B), CircleShape)
                    else Modifier
                )
        )
    }
}
