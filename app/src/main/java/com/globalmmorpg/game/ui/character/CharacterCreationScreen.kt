package com.globalmmorpg.game.ui.character

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.globalmmorpg.game.data.character.Gender

@Composable
fun CharacterCreationScreen(
    viewModel: CharacterCreationViewModel,
    onCharacterCreated: () -> Unit
) {
    val profile by viewModel.profile.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is CharacterCreationUiState.Saved) onCharacterCreated()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp)
    ) {
        Text(
            text = "CREATE YOUR CHARACTER",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Gender toggle
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GenderButton("BOY", profile.gender == Gender.BOY) { viewModel.setGender(Gender.BOY) }
            GenderButton("GIRL", profile.gender == Gender.GIRL) { viewModel.setGender(Gender.GIRL) }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Character preview placeholder — real 3D/2D art asset is rendered here
        // once the art team supplies the model for the selected gender/options.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${profile.gender.name} preview\nhair:${profile.hairId} face:${profile.faceId} " +
                    "eyes:${profile.eyesId} height:${profile.heightCm}cm body:${profile.bodyTypeId}",
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        CustomizationRow("HAIR") { delta -> viewModel.cycle(CharacterField.HAIR, delta) }
        CustomizationRow("FACE") { delta -> viewModel.cycle(CharacterField.FACE, delta) }
        CustomizationRow("EYES") { delta -> viewModel.cycle(CharacterField.EYES, delta) }
        CustomizationRow("BODY TYPE") { delta -> viewModel.cycle(CharacterField.BODY_TYPE, delta) }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("HEIGHT: ${profile.heightCm}cm", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground)
            IconButton(onClick = { viewModel.setHeight(profile.heightCm - 1) }) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Decrease height")
            }
            IconButton(onClick = { viewModel.setHeight(profile.heightCm + 1) }) {
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Increase height")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.onCreateClicked() },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = uiState !is CharacterCreationUiState.Saving
        ) {
            if (uiState is CharacterCreationUiState.Saving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text("CREATE")
            }
        }

        if (uiState is CharacterCreationUiState.Error) {
            Text(
                text = (uiState as CharacterCreationUiState.Error).message,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun GenderButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

@Composable
private fun CustomizationRow(label: String, onCycle: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground)
        IconButton(onClick = { onCycle(-1) }) {
            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Previous $label")
        }
        IconButton(onClick = { onCycle(1) }) {
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Next $label")
        }
    }
}
