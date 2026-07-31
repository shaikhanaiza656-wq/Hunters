package com.globalmmorpg.game.ui.login

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.globalmmorpg.game.R
import com.globalmmorpg.game.data.auth.AuthRepository
import com.globalmmorpg.game.data.auth.AuthResult

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    authRepository: AuthRepository,
    onLoggedIn: (String?) -> Unit
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.LoggedIn) {
            onLoggedIn((uiState as LoginUiState.LoggedIn).displayName)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "LOGIN",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    viewModel.onGoogleLoginClicked(context.getString(R.string.google_web_client_id))
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Text("Continue with Google", color = Color.Black)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    viewModel.setLoading()
                    authRepository.signInWithFacebook(activity) { result ->
                        viewModel.onFacebookAuthResult(result)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2))
            ) {
                Text("Continue with Facebook", color = Color.White)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { viewModel.onGuestLoginClicked() },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Continue as Guest")
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (val state = uiState) {
                is LoginUiState.Loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                is LoginUiState.Error -> Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
                else -> {}
            }
        }
    }
}
