package com.shergill.tryon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shergill.tryon.data.GlbDownloadState
import com.shergill.tryon.domain.AccessoryType
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TryOnFormScreen(
    viewModel: TryOnFormViewModel,
    onNavigateToTryOn: (file: File, type: AccessoryType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var typeMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.navigatedFile) {
        val file = state.navigatedFile ?: return@LaunchedEffect
        onNavigateToTryOn(file, state.accessoryType)
        viewModel.consumeNavigation()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Virtual Try-On",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Pick a verified sample GLB, choose accessory type, then submit.",
            style = MaterialTheme.typography.bodyMedium,
        )

        ExposedDropdownMenuBox(
            expanded = modelMenuExpanded,
            onExpandedChange = { modelMenuExpanded = it },
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = state.selectedModel.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("Sample GLB model") },
                supportingText = {
                    Text(
                        text = state.selectedModel.url,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenuExpanded)
                },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = modelMenuExpanded,
                onDismissRequest = { modelMenuExpanded = false },
            ) {
                SampleGlbModels.ALL.forEach { model ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(model.label)
                                Text(
                                    text = model.suggestedType.displayName(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            viewModel.onSampleModelSelected(model)
                            modelMenuExpanded = false
                        },
                    )
                }
            }
        }

        ExposedDropdownMenuBox(
            expanded = typeMenuExpanded,
            onExpandedChange = { typeMenuExpanded = it },
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = state.accessoryType.displayName(),
                onValueChange = {},
                readOnly = true,
                label = { Text("Accessory type") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded)
                },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = typeMenuExpanded,
                onDismissRequest = { typeMenuExpanded = false },
            ) {
                AccessoryType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.displayName()) },
                        onClick = {
                            viewModel.onAccessoryTypeChange(type)
                            typeMenuExpanded = false
                        },
                    )
                }
            }
        }

        when (val download = state.downloadState) {
            is GlbDownloadState.Downloading -> {
                LinearProgressIndicator(
                    progress = { download.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Downloading… ${(download.progress * 100).toInt()}%")
            }
            is GlbDownloadState.Error -> {
                Text(
                    text = download.message,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = viewModel::retry) {
                    Text("Retry")
                }
            }
            is GlbDownloadState.Success -> {
                Text("Model ready.")
            }
            GlbDownloadState.Idle -> Unit
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = viewModel::submit,
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.downloadState is GlbDownloadState.Downloading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Submit")
            }
        }
    }
}

fun AccessoryType.displayName(): String = when (this) {
    AccessoryType.CAP -> "Cap"
    AccessoryType.GLASSES -> "Glasses"
    AccessoryType.EARRINGS -> "Earrings"
    AccessoryType.LOCKET -> "Locket"
}
