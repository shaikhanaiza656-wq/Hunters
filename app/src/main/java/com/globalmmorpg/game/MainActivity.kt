package com.globalmmorpg.game

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.globalmmorpg.game.data.auth.AuthRepository
import com.globalmmorpg.game.data.character.CharacterRepository
import com.globalmmorpg.game.data.gate.GateRepository
import com.globalmmorpg.game.data.player.PlayerRepository
import com.globalmmorpg.game.ui.character.CharacterCreationScreen
import com.globalmmorpg.game.ui.character.CharacterCreationViewModelFactory
import com.globalmmorpg.game.ui.gate.GateBrowserScreen
import com.globalmmorpg.game.ui.gate.GateScreen
import com.globalmmorpg.game.ui.gate.GateViewModelFactory
import com.globalmmorpg.game.ui.hud.HudScreen
import com.globalmmorpg.game.ui.hud.HudViewModelFactory
import com.globalmmorpg.game.ui.login.LoginScreen
import com.globalmmorpg.game.ui.login.LoginViewModel
import com.globalmmorpg.game.ui.social.SocialScreen
import com.globalmmorpg.game.ui.social.SocialViewModelFactory
import com.globalmmorpg.game.ui.theme.GlobalMMORPGTheme
import com.globalmmorpg.game.ui.voice.VoiceViewModelFactory
import kotlinx.coroutines.launch

private sealed class Screen {
    data object Login : Screen()
    data class CharacterCreation(val uid: String) : Screen()
    data class InGame(val uid: String) : Screen()
    data class GateBrowser(val uid: String) : Screen()
    data class InsideGate(val uid: String, val gateId: String) : Screen()
    data class Social(val uid: String) : Screen()
}

class MainActivity : ComponentActivity() {

    private lateinit var authRepository: AuthRepository
    private lateinit var loginViewModel: LoginViewModel
    private val characterRepository = CharacterRepository()
    private val gateRepository = GateRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authRepository = AuthRepository(applicationContext)
        loginViewModel = LoginViewModel(authRepository)

        setContent {
            GlobalMMORPGTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }

    @Composable
    private fun AppRoot() {
        var screen by remember { mutableStateOf<Screen>(Screen.Login) }
        val scope = rememberCoroutineScope()

        when (val current = screen) {
            is Screen.Login -> LoginScreen(
                viewModel = loginViewModel,
                authRepository = authRepository,
                onLoggedIn = { _ ->
                    val uid = authRepository.currentUser()?.uid
                    if (uid == null) return@LoginScreen
                    // Real check: has this account already created a character before?
                    scope.launch {
                        val hasCharacter = characterRepository.hasCharacter(uid)
                        screen = if (hasCharacter) Screen.InGame(uid) else Screen.CharacterCreation(uid)
                    }
                }
            )

            is Screen.CharacterCreation -> {
                val factory = remember(current.uid) {
                    CharacterCreationViewModelFactory(current.uid, characterRepository)
                }
                val vm = viewModel(factory = factory)
                CharacterCreationScreen(
                    viewModel = vm,
                    onCharacterCreated = { screen = Screen.InGame(current.uid) }
                )
            }

            is Screen.InGame -> {
                val hudFactory = remember(current.uid) {
                    HudViewModelFactory(current.uid, PlayerRepository())
                }
                val hudViewModel = viewModel(factory = hudFactory)
                HudScreen(
                    viewModel = hudViewModel,
                    onFindGateClicked = { screen = Screen.GateBrowser(current.uid) },
                    onSocialClicked = { screen = Screen.Social(current.uid) }
                )
            }

            is Screen.Social -> {
                val socialFactory = remember(current.uid) {
                    SocialViewModelFactory(current.uid)
                }
                val socialViewModel = viewModel(factory = socialFactory)
                // Keyed separately from the Gate voice VoiceViewModel so guild voice
                // and gate voice never share the same ViewModel instance/state.
                val voiceFactory = remember(current.uid) {
                    VoiceViewModelFactory(applicationContext)
                }
                val voiceViewModel = viewModel(factory = voiceFactory, key = "guildVoice_${current.uid}")
                SocialScreen(
                    viewModel = socialViewModel,
                    voiceViewModel = voiceViewModel,
                    onBack = { screen = Screen.InGame(current.uid) }
                )
            }

            is Screen.GateBrowser -> GateBrowserScreen(
                gateRepository = gateRepository,
                onGateEntered = { gateId -> screen = Screen.InsideGate(current.uid, gateId) }
            )

            is Screen.InsideGate -> {
                val hudFactory = remember(current.uid) {
                    HudViewModelFactory(current.uid, PlayerRepository())
                }
                val hudViewModel = viewModel(factory = hudFactory)
                val gateFactory = remember(current.gateId) {
                    GateViewModelFactory(current.gateId, gateRepository)
                }
                val gateViewModel = viewModel(factory = gateFactory)
                val voiceFactory = remember(current.gateId) {
                    VoiceViewModelFactory(applicationContext)
                }
                val voiceViewModel = viewModel(factory = voiceFactory)
                GateScreen(
                    hudViewModel = hudViewModel,
                    gateViewModel = gateViewModel,
                    voiceViewModel = voiceViewModel,
                    onGateEnded = { screen = Screen.InGame(current.uid) }
                )
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        authRepository.getCallbackManager().onActivityResult(requestCode, resultCode, data)
    }
}
