package com.globalmmorpg.game.ui.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.globalmmorpg.game.data.character.CharacterRepository

class CharacterCreationViewModelFactory(
    private val uid: String,
    private val repository: CharacterRepository = CharacterRepository()
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CharacterCreationViewModel::class.java)) {
            return CharacterCreationViewModel(uid, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
