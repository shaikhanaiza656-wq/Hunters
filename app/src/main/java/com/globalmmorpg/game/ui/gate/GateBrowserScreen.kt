package com.globalmmorpg.game.ui.gate

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.globalmmorpg.game.data.gate.GateRepository
import kotlinx.coroutines.launch

@Composable
fun GateBrowserScreen(
    gateRepository: GateRepository,
    onGateEntered: (gateId: String) -> Unit
) {
    var isSearching by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No Gate nearby yet.", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    isSearching = true
                    errorMessage = null
                    scope.launch {
                        try {
                            val gate = gateRepository.createRandomGate()
                            onGateEntered(gate.id)
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Failed to create gate"
                        } finally {
                            isSearching = false
                        }
                    }
                },
                enabled = !isSearching
            ) {
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text("Search for Gate")
                }
            }
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
