package com.shergill.tryon.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shergill.tryon.data.GlbDownloadState
import com.shergill.tryon.data.GlbRepository
import com.shergill.tryon.domain.AccessoryType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class TryOnFormUiState(
    val selectedModel: SampleGlbModel = SampleGlbModels.DEFAULT,
    val accessoryType: AccessoryType = SampleGlbModels.DEFAULT.suggestedType,
    val downloadState: GlbDownloadState = GlbDownloadState.Idle,
    val navigatedFile: File? = null,
) {
    val url: String get() = selectedModel.url

    val isUrlValid: Boolean
        get() = GlbRepository.isHttpUrl(url.trim())

    val canSubmit: Boolean
        get() = isUrlValid && downloadState !is GlbDownloadState.Downloading
}

class TryOnFormViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val repository = GlbRepository(application)

    private val _uiState = MutableStateFlow(TryOnFormUiState())
    val uiState: StateFlow<TryOnFormUiState> = _uiState.asStateFlow()

    fun onSampleModelSelected(model: SampleGlbModel) {
        _uiState.update {
            it.copy(
                selectedModel = model,
                accessoryType = model.suggestedType,
                downloadState = GlbDownloadState.Idle,
                navigatedFile = null,
            )
        }
    }

    fun onAccessoryTypeChange(type: AccessoryType) {
        _uiState.update { it.copy(accessoryType = type) }
    }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        viewModelScope.launch {
            repository.downloadAndValidate(state.url).collect { download ->
                _uiState.update { current ->
                    when (download) {
                        is GlbDownloadState.Success -> current.copy(
                            downloadState = download,
                            navigatedFile = download.file,
                        )
                        else -> current.copy(downloadState = download, navigatedFile = null)
                    }
                }
            }
        }
    }

    fun retry() = submit()

    fun consumeNavigation() {
        _uiState.update { it.copy(navigatedFile = null, downloadState = GlbDownloadState.Idle) }
    }
}
