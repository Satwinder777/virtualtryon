package com.shergill.tryon.domain

/**
 * MediaPipe Face Landmarker (468-point Face Mesh) landmark indices.
 * Left/right are anatomical (subject's left/right).
 */
object FaceLandmarks {
    const val NOSE_BRIDGE = 168
    const val FOREHEAD_TOP = 10
    const val CHIN = 152
    const val JAW_LEFT = 234
    const val JAW_RIGHT = 454

    const val RIGHT_EYE_OUTER = 33
    const val RIGHT_EYE_INNER = 133
    const val LEFT_EYE_OUTER = 263
    const val LEFT_EYE_INNER = 362

    const val LANDMARK_COUNT = 468

    /** Full Face Landmarker output with iris refinement (468 + 10 iris). */
    const val FULL_MESH_WITH_IRIS = 478
}
