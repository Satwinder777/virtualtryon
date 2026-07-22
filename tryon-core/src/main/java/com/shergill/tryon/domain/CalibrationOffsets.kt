package com.shergill.tryon.domain

/**
 * Per-accessory calibration offsets applied on top of the procedural [Placement].
 * Persisted via DataStore in the UI layer.
 */
data class CalibrationOffsets(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val offsetZ: Float = 0f,
    val scale: Float = 1f,
    val rotationYawDeg: Float = 0f,
    val rotationPitchDeg: Float = 0f,
    val rotationRollDeg: Float = 0f,
) {
    companion object {
        val IDENTITY = CalibrationOffsets()
    }
}
