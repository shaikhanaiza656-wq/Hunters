package com.globalmmorpg.game.ui.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.globalmmorpg.game.data.character.CharacterOptions
import com.globalmmorpg.game.data.character.CharacterProfile
import com.globalmmorpg.game.data.character.CharacterRepository
import com.globalmmorpg.game.data.character.Gender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class CharacterCreationUiState {
    data object Idle : CharacterCreationUiState()
    data object Saving : CharacterCreationUiState()
    data object Saved : CharacterCreationUiState()
    data class Error(val message: String) : CharacterCreationUiState()
}

class CharacterCreationViewModel(
    private val uid: String,
    private val repository: CharacterRepository
) : ViewModel() {

    private val _profile = MutableStateFlow(CharacterProfile(uid = uid))
    val profile: StateFlow<CharacterProfile> = _profile

    private val _uiState = MutableStateFlow<CharacterCreationUiState>(CharacterCreationUiState.Idle)
    val uiState: StateFlow<CharacterCreationUiState> = _uiState

    fun setGender(gender: Gender) {
        _profile.value = _profile.value.copy(gender = gender)
    }

    fun setHeight(cm: Int) {
        val clamped = cm.coerceIn(CharacterOptions.MIN_HEIGHT_CM, CharacterOptions.MAX_HEIGHT_CM)
        _profile.value = _profile.value.copy(heightCm = clamped)
    }

    fun cycle(field: CharacterField, delta: Int) {
        val p = _profile.value
        _profile.value = when (field) {
            CharacterField.HAIR -> p.copy(hairId = wrap(p.hairId + delta, CharacterOptions.HAIR_COUNT))
            CharacterField.FACE -> p.copy(faceId = wrap(p.faceId + delta, CharacterOptions.FACE_COUNT))
            CharacterField.EYES -> p.copy(eyesId = wrap(p.eyesId + delta, CharacterOptions.EYES_COUNT))
            CharacterField.BODY_TYPE -> p.copy(bodyTypeId = wrap(p.bodyTypeId + delta, CharacterOptions.BODY_TYPE_COUNT))
        }
    }

    private fun wrap(value: Int, count: Int): Int = ((value % count) + count) % count

    fun onCreateClicked() {
        _uiState.value = CharacterCreationUiState.Saving
        viewModelScope.launch {
            try {
                repository.saveCharacter(_profile.value)
                _uiState.value = CharacterCreationUiState.Saved
            } catch (e: Exception) {
                _uiState.value = CharacterCreationUiState.Error(e.message ?: "Failed to save character")
            }
        }
    }
}

enum class CharacterField { HAIR, FACE, EYES, BODY_TYPE }
