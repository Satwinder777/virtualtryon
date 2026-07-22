package com.shergill.tryon.domain

/**
 * Cap / hat placement.
 *
 * Anchor landmark(s): [FaceLandmarks.FOREHEAD_TOP] (index 10) — top of the face oval.
 * Offset upward along face-up (forehead − chin), proportional to inter-eye distance.
 */
class CapPlacementStrategy(
    private val upwardOffsetFactor: Float = 0.55f,
    private val baseScaleFactor: Float = 2.4f,
) : PlacementStrategy {

    override fun computeTransform(face: FaceFrame): Placement? {
        if (!face.faceDetected) return null
        val forehead = face.landmarkOrNull(FaceLandmarks.FOREHEAD_TOP) ?: return null
        val chin = face.landmarkOrNull(FaceLandmarks.CHIN)
        val eyeSpan = face.interEyeDistance()
        val up = if (chin != null) {
            (forehead - chin).normalized()
        } else {
            Vec3.UP
        }
        val position = forehead + up * (eyeSpan * upwardOffsetFactor)
        return Placement(
            position = position,
            rotation = face.overlayRollFromEyes(),
            scaleMultiplier = eyeSpan * baseScaleFactor,
        )
    }
}
