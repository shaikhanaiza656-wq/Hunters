package com.globalmmorpg.game.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.globalmmorpg.game.ui.theme.HpRed
import com.globalmmorpg.game.ui.theme.ManaBlue
import com.globalmmorpg.game.ui.theme.StaminaGreen

@Composable
fun HudScreen(
    viewModel: HudViewModel,
    onFindGateClicked: () -> Unit = {},
    onSocialClicked: () -> Unit = {}
) {
    val stats by viewModel.stats.collectAsState()
    val entities by viewModel.nearbyEntities.collectAsState()
    val target by viewModel.target.collectAsState()
    val cooldowns by viewModel.cooldowns.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {

        // --- Top-left: HP / Mana / Stamina + Rank/Level ---
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .width(180.dp)
        ) {
            StatBar("HP", stats.hp, stats.maxHp, HpRed)
            Spacer(modifier = Modifier.height(4.dp))
            StatBar("MANA", stats.mana, stats.maxMana, ManaBlue)
            Spacer(modifier = Modifier.height(4.dp))
            StatBar("STAMINA", stats.stamina, stats.maxStamina, StaminaGreen)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "RANK: ${stats.rank}   Lv. ${stats.level}",
                color = Color.White,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            TextButton(onClick = onSocialClicked, contentPadding = PaddingValues(0.dp)) {
                Text("Guild / Friends", fontSize = 12.sp)
            }
        }

        // --- Top-right: Minimap + current target ---
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            horizontalAlignment = Alignment.End
        ) {
            Minimap(entities = entities)
            if (target != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                        .width(140.dp)
                ) {
                    Text(
                        text = "${target!!.name} Lv.${target!!.level}",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    StatBar("", target!!.hp, target!!.maxHp, HpRed)
                }
            }
        }

        // --- Bottom-left: virtual joystick ---
        VirtualJoystick(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp),
            onMove = { dx, dy -> viewModel.onJoystickMoved(dx, dy) },
            onReleased = { viewModel.onJoystickReleased() }
        )

        // --- Bottom-center: enter the Gate system (GDD section 9/10) ---
        Button(
            onClick = onFindGateClicked,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            Text("Find Gate")
        }

        // --- Bottom-right: skill/attack buttons ---
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SkillButton(
                label = "S1",
                cooldown = cooldowns["skill_1"],
                onClick = { viewModel.useSkill("skill_1", manaCost = 10, staminaCost = 5) }
            )
            SkillButton(
                label = "S2",
                cooldown = cooldowns["skill_2"],
                onClick = { viewModel.useSkill("skill_2", manaCost = 25, staminaCost = 15) }
            )
        }
    }
}

@Composable
private fun SkillButton(label: String, cooldown: SkillCooldown?, onClick: () -> Unit) {
    val ready = cooldown?.isReady ?: true
    Box(
        modifier = Modifier
            .size(64.dp)
            .background(
                if (ready) Color(0xFF3A8DFF) else Color(0xFF3A8DFF).copy(alpha = 0.4f),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick, enabled = ready) {
            Text(
                text = if (ready) label else "${cooldown?.remainingSeconds}",
                color = Color.White,
                fontSize = 16.sp
            )
        }
    }
}
