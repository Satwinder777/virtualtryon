package com.shergill.tryon.domain

/**
 * MediaPipe Face Landmarker (468-point Face Mesh) landmark indices.
 * Left/right are anatomical (subject's left/right).
 */
object FaceLandmarks {
    const val NOSE_TIP = 4
    /** Between the eyes — used to seat glasses on the bridge. */
    const val NOSE_BRIDGE = 168
    const val FOREHEAD_TOP = 10
    const val CHIN = 152
    const val JAW_LEFT = 234
    const val JAW_RIGHT = 454

    /**
     * MediaPipe anatomical indices (subject's left/right).
     * On a mirrored selfie, anatomical RIGHT (33/133) often sits on screen-left.
     */
    const val RIGHT_EYE_OUTER = 33
    const val RIGHT_EYE_INNER = 133
    const val LEFT_EYE_OUTER = 263
    const val LEFT_EYE_INNER = 362

    /** Iris centers when Face Landmarker iris refinement is enabled (478-point mesh). */
    const val LEFT_IRIS_CENTER = 468
    const val RIGHT_IRIS_CENTER = 473

    const val LANDMARK_COUNT = 468

    /** Full Face Landmarker output with iris refinement (468 + 10 iris). */
    const val FULL_MESH_WITH_IRIS = 478
}
