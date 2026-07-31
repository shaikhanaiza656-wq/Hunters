package com.globalmmorpg.game.ui.gate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.globalmmorpg.game.data.gate.Gate
import com.globalmmorpg.game.data.gate.GateRepository
import com.globalmmorpg.game.data.gate.GateStatus
import com.globalmmorpg.game.data.monster.MonsterInstance
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GateViewModel(
    private val gateId: String,
    private val repository: GateRepository
) : ViewModel() {

    private val _gate = MutableStateFlow<Gate?>(null)
    val gate: StateFlow<Gate?> = _gate

    private val _monsters = MutableStateFlow<List<MonsterInstance>>(emptyList())
    val monsters: StateFlow<List<MonsterInstance>> = _monsters

    private val _selectedTargetId = MutableStateFlow<String?>(null)
    val selectedTargetId: StateFlow<String?> = _selectedTargetId

    private val _secondsRemaining = MutableStateFlow(0)
    val secondsRemaining: StateFlow<Int> = _secondsRemaining

    var onGateBroken: (() -> Unit)? = null
    var onGateCleared: (() -> Unit)? = null

    init {
        viewModelScope.launch {
            _gate.value = repository.fetchGate(gateId)
            _monsters.value = repository.fetchMonsters(gateId)
            autoSelectFirstAliveTarget()
        }
        startCountdownLoop()
    }

    fun selectTarget(instanceId: String) {
        _selectedTargetId.value = instanceId
    }

    /** Real attack resolution: damages the selected monster in Firestore, refreshes state,
     *  and reacts if that kill triggers a gate clear. */
    fun attackSelectedTarget(damage: Int) {
        val targetId = _selectedTargetId.value ?: return
        viewModelScope.launch {
            val updated = repository.damageMonster(gateId, targetId, damage) ?: return@launch
            _monsters.value = _monsters.value.map { if (it.instanceId == updated.instanceId) updated else it }

            if (!updated.isAlive) {
                if (updated.isKing) {
                    _gate.value = _gate.value?.copy(status = GateStatus.CLEARED, bossDefeated = true)
                    onGateCleared?.invoke()
                } else {
                    autoSelectFirstAliveTarget()
                }
            }
        }
    }

    fun reportTeamWiped() {
        viewModelScope.launch {
            repository.markTeamWiped(gateId)
            _gate.value = _gate.value?.copy(status = GateStatus.BROKEN)
            onGateBroken?.invoke()
        }
    }

    private fun autoSelectFirstAliveTarget() {
        val firstAlive = _monsters.value.firstOrNull { it.isAlive && !it.isKing }
            ?: _monsters.value.firstOrNull { it.isAlive }
        _selectedTargetId.value = firstAlive?.instanceId
    }

    private fun startCountdownLoop() {
        viewModelScope.launch {
            while (true) {
                val current = _gate.value
                if (current != null) {
                    _secondsRemaining.value = current.secondsRemaining(System.currentTimeMillis())
                    if (current.status == GateStatus.ACTIVE && _secondsRemaining.value <= 0) {
                        val broken = repository.checkAndApplyGateBreak(current)
                        _gate.value = broken
                        if (broken.status == GateStatus.BROKEN) onGateBroken?.invoke()
                    }
                }
                delay(1000)
            }
        }
    }
}

class GateViewModelFactory(
    private val gateId: String,
    private val repository: GateRepository = GateRepository()
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GateViewModel::class.java)) {
            return GateViewModel(gateId, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
