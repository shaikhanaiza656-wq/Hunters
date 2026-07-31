package com.globalmmorpg.game.ui.gate

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.globalmmorpg.game.data.gate.GateStatus
import com.globalmmorpg.game.data.monster.MonsterInstance
import com.globalmmorpg.game.ui.hud.HudViewModel
import com.globalmmorpg.game.ui.hud.StatBar
import com.globalmmorpg.game.ui.hud.VirtualJoystick
import com.globalmmorpg.game.ui.theme.HpRed
import com.globalmmorpg.game.ui.theme.ManaBlue
import com.globalmmorpg.game.ui.theme.StaminaGreen
import com.globalmmorpg.game.ui.voice.VoiceControls
import com.globalmmorpg.game.ui.voice.VoiceViewModel

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

        // Monster list — tap to target, matching how targeting works with the HUD's target panel
        LazyRow(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 60.dp, end = 12.dp)
                .width(220.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(monsters.filter { it.isAlive }) { monster ->
                MonsterChip(
                    monster = monster,
                    isSelected = monster.instanceId == selectedTargetId,
                    onClick = { gateViewModel.selectTarget(monster.instanceId) }
                )
            }
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

@Composable
private fun MonsterChip(monster: MonsterInstance, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .background(
                if (isSelected) Color(0xFFE23B3B).copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.5f),
                RoundedCornerShape(6.dp)
            )
            .clickable { onClick() }
            .padding(8.dp)
            .width(100.dp)
    ) {
        Text(
            text = if (monster.isKing) "${monster.type.name} KING" else monster.type.name,
            color = Color.White,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        StatBar("", monster.currentHp, monster.maxHp, HpRed)
    }
}
