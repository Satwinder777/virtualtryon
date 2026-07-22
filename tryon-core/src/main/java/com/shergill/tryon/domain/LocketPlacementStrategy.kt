package com.shergill.tryon.domain

/**
 * Locket / pendant placement — APPROXIMATION.
 *
 * Anchor landmark: [FaceLandmarks.CHIN] (index 152). MediaPipe's frontal face mesh
 * does not provide neck or clavicle landmarks, so the locket is procedurally placed
 * below the chin, offset down (+local −Y) and slightly back (local −Z) along head axes,
 * with magnitude proportional to face height (forehead → chin).
 *
 * Offset factors are named constants for later calibration wiring.
 */
class LocketPlacementStrategy(
    private val downOffsetFactor: Float = DOWN_OFFSET_FACTOR,
    private val backOffsetFactor: Float = BACK_OFFSET_FACTOR,
    private val baseScaleFactor: Float = 0.55f,
) : PlacementStrategy {

    override fun computeTransform(face: FaceFrame): Placement? {
        if (!face.faceDetected) return null
        val chin = face.landmarkOrNull(FaceLandmarks.CHIN) ?: return null
        val forehead = face.landmarkOrNull(FaceLandmarks.FOREHEAD_TOP)
        val height = face.faceHeight()
        val localY = if (forehead != null) {
            (forehead - chin).normalized()
        } else {
            Vec3.UP
        }
        // Toward camera is +Z in overlay; push locket slightly back (−Z).
        val localZ = Vec3(0f, 0f, 1f)
        val position = chin +
            localY * -(height * downOffsetFactor) +
            localZ * -(height * backOffsetFactor)
        return Placement(
            position = position,
            rotation = face.orientationFromLandmarks(),
            scaleMultiplier = height * baseScaleFactor,
        )
    }

    companion object {
        /** Downward offset from chin along local −Y, relative to face height. */
        const val DOWN_OFFSET_FACTOR = 0.55f

        /** Backward offset from chin along local −Z, relative to face height. */
        const val BACK_OFFSET_FACTOR = 0.12f
    }
}
