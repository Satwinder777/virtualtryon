package com.shergill.tryon.domain

/**
 * Earrings placement — APPROXIMATION.
 *
 * MediaPipe's frontal face mesh has NO true ear landmarks (the mesh is cropped
 * before the ears). We approximate each earring position from the nearest available
 * jaw-corner landmarks ([FaceLandmarks.JAW_LEFT] = 234, [FaceLandmarks.JAW_RIGHT] = 454),
 * then offset outward along local ±X and slightly down along local −Y.
 *
 * Offset distances are named constants so they can later be wired to calibration sliders.
 *
 * Returns the LEFT earring placement by default; use [computeLeft] / [computeRight]
 * when both sides are needed by the renderer.
 */
class EarringsPlacementStrategy(
    private val outwardOffsetFactor: Float = OUTWARD_OFFSET_FACTOR,
    private val downwardOffsetFactor: Float = DOWNWARD_OFFSET_FACTOR,
    private val baseScaleFactor: Float = 0.35f,
    private val side: Side = Side.LEFT,
) : PlacementStrategy {

    enum class Side { LEFT, RIGHT }

    override fun computeTransform(face: FaceFrame): Placement? = when (side) {
        Side.LEFT -> computeLeft(face)
        Side.RIGHT -> computeRight(face)
    }

    fun computeLeft(face: FaceFrame): Placement? = computeForSide(face, Side.LEFT)

    fun computeRight(face: FaceFrame): Placement? = computeForSide(face, Side.RIGHT)

    private fun computeForSide(face: FaceFrame, which: Side): Placement? {
        if (!face.faceDetected) return null
        val jawIndex = when (which) {
            Side.LEFT -> FaceLandmarks.JAW_LEFT
            Side.RIGHT -> FaceLandmarks.JAW_RIGHT
        }
        val jaw = face.landmarkOrNull(jawIndex) ?: return null
        val eyeSpan = face.interEyeDistance()
        val leftEye = face.landmarkOrNull(FaceLandmarks.LEFT_EYE_OUTER)
            ?.takeUnless { it == Vec3.ZERO }
            ?: face.landmarkOrNull(FaceLandmarks.LEFT_EYE_INNER)
        val rightEye = face.landmarkOrNull(FaceLandmarks.RIGHT_EYE_OUTER)
            ?.takeUnless { it == Vec3.ZERO }
            ?: face.landmarkOrNull(FaceLandmarks.RIGHT_EYE_INNER)
        val forehead = face.landmarkOrNull(FaceLandmarks.FOREHEAD_TOP)
        val chin = face.landmarkOrNull(FaceLandmarks.CHIN)
        val eyeAxis = if (leftEye != null && rightEye != null) leftEye - rightEye else Vec3.RIGHT
        val localX = if (eyeAxis.length() > 1e-5f) eyeAxis.normalized() else Vec3.RIGHT
        val upAxis = if (forehead != null && chin != null) forehead - chin else Vec3.UP
        val localY = if (upAxis.length() > 1e-5f) upAxis.normalized() else Vec3.UP
        val outward = when (which) {
            Side.LEFT -> localX
            Side.RIGHT -> localX * -1f
        }
        val position = jaw +
            outward * (eyeSpan * outwardOffsetFactor) +
            localY * -(eyeSpan * downwardOffsetFactor)
        return Placement(
            position = position,
            rotation = face.orientationFromLandmarks(),
            scaleMultiplier = eyeSpan * baseScaleFactor,
        )
    }

    companion object {
        /** Horizontal offset from jaw corner toward the ear, relative to inter-eye distance. */
        const val OUTWARD_OFFSET_FACTOR = 0.45f

        /** Vertical offset downward from jaw corner, relative to inter-eye distance. */
        const val DOWNWARD_OFFSET_FACTOR = 0.15f
    }
}
