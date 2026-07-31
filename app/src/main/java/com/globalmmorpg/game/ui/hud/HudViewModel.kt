package com.globalmmorpg.game.ui.hud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.globalmmorpg.game.data.player.MapEntity
import com.globalmmorpg.game.data.player.PlayerRepository
import com.globalmmorpg.game.data.player.PlayerStats
import com.globalmmorpg.game.data.player.Target
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SkillCooldown(val skillId: String, val totalSeconds: Int, val remainingSeconds: Int) {
    val isReady: Boolean get() = remainingSeconds <= 0
}

class HudViewModel(
    private val uid: String,
    private val repository: PlayerRepository
) : ViewModel() {

    private val _stats = MutableStateFlow(PlayerStats(uid = uid))
    val stats: StateFlow<PlayerStats> = _stats

    private val _movementVector = MutableStateFlow(0f to 0f) // (dx, dy) normalized -1..1, from joystick
    val movementVector: StateFlow<Pair<Float, Float>> = _movementVector

    private val _nearbyEntities = MutableStateFlow<List<MapEntity>>(emptyList())
    val nearbyEntities: StateFlow<List<MapEntity>> = _nearbyEntities

    private val _target = MutableStateFlow<Target?>(null)
    val target: StateFlow<Target?> = _target

    private val _cooldowns = MutableStateFlow(
        mapOf(
            "skill_1" to SkillCooldown("skill_1", totalSeconds = 3, remainingSeconds = 0),
            "skill_2" to SkillCooldown("skill_2", totalSeconds = 8, remainingSeconds = 0)
        )
    )
    val cooldowns: StateFlow<Map<String, SkillCooldown>> = _cooldowns

    init {
        viewModelScope.launch {
            _stats.value = repository.loadStats(uid)
        }
        startRegenLoop()
        startCooldownTicker()
    }

    /** Called continuously by the joystick composable while the thumb is dragged. */
    fun onJoystickMoved(dx: Float, dy: Float) {
        _movementVector.value = dx to dy
    }

    fun onJoystickReleased() {
        _movementVector.value = 0f to 0f
    }

    fun setNearbyEntities(entities: List<MapEntity>) {
        _nearbyEntities.value = entities
    }

    fun setTarget(target: Target?) {
        _target.value = target
    }

    /** Real skill-use flow: checks mana/stamina cost and cooldown before allowing the action. */
    fun useSkill(skillId: String, manaCost: Int, staminaCost: Int) {
        val cooldown = _cooldowns.value[skillId] ?: return
        if (!cooldown.isReady) return

        val current = _stats.value
        if (current.mana < manaCost || current.stamina < staminaCost) return

        _stats.update {
            it.copy(
                mana = (it.mana - manaCost).coerceAtLeast(0),
                stamina = (it.stamina - staminaCost).coerceAtLeast(0)
            )
        }
        _cooldowns.update { map ->
            map + (skillId to cooldown.copy(remainingSeconds = cooldown.totalSeconds))
        }
        persistStats()
    }

    fun applyDamageToPlayer(amount: Int) {
        _stats.update { it.copy(hp = (it.hp - amount).coerceAtLeast(0)) }
        persistStats()
    }

    private fun startCooldownTicker() {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                _cooldowns.update { map ->
                    map.mapValues { (_, cd) ->
                        if (cd.remainingSeconds > 0) cd.copy(remainingSeconds = cd.remainingSeconds - 1) else cd
                    }
                }
            }
        }
    }

    /** Real stamina/mana passive regeneration, matching typical MMORPG HUD behaviour. */
    private fun startRegenLoop() {
        viewModelScope.launch {
            while (true) {
                delay(2000)
                _stats.update {
                    it.copy(
                        stamina = (it.stamina + 5).coerceAtMost(it.maxStamina),
                        mana = (it.mana + 3).coerceAtMost(it.maxMana)
                    )
                }
            }
        }
    }

    private fun persistStats() {
        // Fires on discrete events (skill use, damage) rather than every frame,
        // so this does not flood Firestore with writes.
        viewModelScope.launch {
            repository.saveStats(_stats.value)
        }
    }
}

class HudViewModelFactory(
    private val uid: String,
    private val repository: PlayerRepository = PlayerRepository()
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HudViewModel::class.java)) {
            return HudViewModel(uid, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
