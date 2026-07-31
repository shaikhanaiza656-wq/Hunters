package com.globalmmorpg.game.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.globalmmorpg.game.data.auth.AuthRepository
import com.globalmmorpg.game.data.auth.AuthResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class LoginUiState {
    data object Idle : LoginUiState()
    data object Loading : LoginUiState()
    data class LoggedIn(val displayName: String?) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState

    fun onGuestLoginClicked() {
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            when (val result = authRepository.signInAsGuest()) {
                is AuthResult.Success -> _uiState.value = LoginUiState.LoggedIn(result.user.displayName ?: "Guest")
                is AuthResult.Failure -> _uiState.value = LoginUiState.Error(result.message)
            }
        }
    }

    fun onGoogleLoginClicked(webClientId: String) {
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            when (val result = authRepository.signInWithGoogle(webClientId)) {
                is AuthResult.Success -> _uiState.value = LoginUiState.LoggedIn(result.user.displayName)
                is AuthResult.Failure -> _uiState.value = LoginUiState.Error(result.message)
            }
        }
    }

    fun onFacebookAuthResult(result: AuthResult) {
        _uiState.value = when (result) {
            is AuthResult.Success -> LoginUiState.LoggedIn(result.user.displayName)
            is AuthResult.Failure -> LoginUiState.Error(result.message)
        }
    }

    fun setLoading() {
        _uiState.value = LoginUiState.Loading
    }
}
