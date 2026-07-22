package com.shergill.tryon.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shergill.tryon.data.CalibrationRepository
import com.shergill.tryon.domain.AccessoryType
import com.shergill.tryon.domain.CalibrationOffsets
import com.shergill.tryon.domain.PlacementStrategies
import com.shergill.tryon.domain.PlacementStrategy
import com.shergill.tryon.render.FilamentAccessoryRenderer
import com.shergill.tryon.tracking.FaceTracker
import com.shergill.tryon.tracking.MediaPipeFaceTracker
import com.shergill.tryon.tracking.NoFrontCameraException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class TryOnUiState(
    val faceDetected: Boolean = false,
    val landmarkCount: Int = 0,
    val showAlignHint: Boolean = false,
    val noFrontCamera: Boolean = false,
    val errorMessage: String? = null,
    val calibration: CalibrationOffsets = CalibrationOffsets.IDENTITY,
    val reducedQuality: Boolean = false,
    val modelLoaded: Boolean = false,
)

class TryOnViewModel(
    application: Application,
    private val modelFile: File,
    private val accessoryType: AccessoryType,
) : AndroidViewModel(application) {

    private val calibrationRepository = CalibrationRepository(application)
    private val strategy: PlacementStrategy = PlacementStrategies.forType(accessoryType)

    private var faceTracker: FaceTracker? = null
    private var renderer: FilamentAccessoryRenderer? = null

    private val _uiState = MutableStateFlow(TryOnUiState())
    val uiState: StateFlow<TryOnUiState> = _uiState.asStateFlow()

    private var lastFaceTimestampMs: Long = System.currentTimeMillis()

    init {
        viewModelScope.launch {
            calibrationRepository.observe(accessoryType).collect { offsets ->
                _uiState.update { it.copy(calibration = offsets) }
            }
        }
    }

    fun attachRenderer(renderer: FilamentAccessoryRenderer) {
        this.renderer = renderer
        try {
            renderer.loadModel(modelFile, accessoryType)
            _uiState.update { it.copy(modelLoaded = true, errorMessage = null) }
        } catch (t: Throwable) {
            _uiState.update {
                it.copy(modelLoaded = false, errorMessage = t.message ?: "Failed to load model")
            }
        }
    }

    fun createTracker(): FaceTracker {
        val tracker = MediaPipeFaceTracker(getApplication())
        faceTracker = tracker
        return tracker
    }

    fun onTrackerStarted() {
        // no-op hook
    }

    fun onNoFrontCamera() {
        _uiState.update { it.copy(noFrontCamera = true) }
    }

    fun onFrame(face: com.shergill.tryon.domain.FaceFrame?) {
        val now = System.currentTimeMillis()
        if (face == null || !face.faceDetected) {
            val showHint = now - lastFaceTimestampMs > 2_000L
            _uiState.update {
                it.copy(
                    faceDetected = false,
                    landmarkCount = 0,
                    showAlignHint = showHint,
                )
            }
            renderer?.updateTransform(null, _uiState.value.calibration)
            return
        }
        lastFaceTimestampMs = now
        // Tracker already emits overlay-space landmarks (PreviewView FILL_CENTER mapped).
        val placement = strategy.computeTransform(face)
        _uiState.update {
            it.copy(
                faceDetected = true,
                landmarkCount = face.landmarks.size,
                showAlignHint = false,
                noFrontCamera = false,
            )
        }
        renderer?.updateTransform(placement, _uiState.value.calibration)
    }

    fun updateCalibration(offsets: CalibrationOffsets) {
        _uiState.update { it.copy(calibration = offsets) }
        viewModelScope.launch {
            calibrationRepository.save(accessoryType, offsets)
        }
    }

    fun resetCalibration() {
        updateCalibration(CalibrationOffsets.IDENTITY)
    }

    fun setReducedQuality(enabled: Boolean) {
        _uiState.update { it.copy(reducedQuality = enabled) }
        renderer?.setReducedQuality(enabled)
    }

    fun handleTrackerStartError(t: Throwable) {
        if (t is NoFrontCameraException || t.message?.contains("front", ignoreCase = true) == true) {
            onNoFrontCamera()
        } else {
            _uiState.update { it.copy(errorMessage = t.message) }
        }
    }

    override fun onCleared() {
        faceTracker?.stop()
        (faceTracker as? MediaPipeFaceTracker)?.release()
        faceTracker = null
        // Renderer lifecycle is owned by TryOnScreen's DisposableEffect.
        renderer = null
        super.onCleared()
    }
}

class TryOnViewModelFactory(
    private val application: Application,
    private val modelFile: File,
    private val accessoryType: AccessoryType,
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return TryOnViewModel(application, modelFile, accessoryType) as T
    }
}
