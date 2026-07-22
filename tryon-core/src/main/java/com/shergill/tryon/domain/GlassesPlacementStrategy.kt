package com.shergill.tryon.domain

import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Glasses placement — screen overlay on the eyes.
 *
 * Uses nose-bridge / mid-eye anchor + **roll only** (atan2 of the eye line).
 * Landmark Z is ignored so depth cannot shear the frames into "broken" temples.
 *
 * Model face-camera correction (XZ → XY) is handled at load by
 * [com.shergill.tryon.render.ModelBoundsNormalizer].
 */
class GlassesPlacementStrategy(
    /** Fits unit-normalized frame width (~1) across outer-eye span. */
    private val baseScaleFactor: Float = 1.35f,
) : PlacementStrategy {

    override fun computeTransform(face: FaceFrame): Placement? {
        if (!face.faceDetected) return null

        val left = face.screenLeftEye() ?: return null
        val right = face.screenRightEye() ?: return null

        val dx = right.x - left.x
        val dy = right.y - left.y
        val eyeSpan = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)

        // Prefer nose bridge; fall back to mid-pupil line nudged slightly down.
        val bridge = face.landmarkOrNull(FaceLandmarks.NOSE_BRIDGE)
            ?.takeUnless { it == Vec3.ZERO }
        val midX = (left.x + right.x) * 0.5f
        val midY = (left.y + right.y) * 0.5f
        val position = if (bridge != null) {
            Vec3(bridge.x, bridge.y, 0f)
        } else {
            val down = eyeSpan * 0.08f
            Vec3(midX, midY - down, 0f)
        }

        // Pure Z-roll: keeps frames facing the camera; matches head tilt in-plane.
        val rollRad = atan2(dy, dx)
        val rotation = Quaternion.fromAxisAngleDegrees(Vec3(0f, 0f, 1f), Math.toDegrees(rollRad.toDouble()).toFloat())

        return Placement(
            position = position,
            rotation = rotation,
            scaleMultiplier = eyeSpan * baseScaleFactor,
        )
    }
}

fun FaceFrame.screenLeftEye(): Vec3? {
    val a = landmarkOrNull(FaceLandmarks.LEFT_EYE_OUTER)?.takeUnless { it == Vec3.ZERO }
        ?: landmarkOrNull(FaceLandmarks.LEFT_EYE_INNER)
    val b = landmarkOrNull(FaceLandmarks.RIGHT_EYE_OUTER)?.takeUnless { it == Vec3.ZERO }
        ?: landmarkOrNull(FaceLandmarks.RIGHT_EYE_INNER)
    if (a == null || b == null) return null
    return if (a.x <= b.x) a else b
}

fun FaceFrame.screenRightEye(): Vec3? {
    val a = landmarkOrNull(FaceLandmarks.LEFT_EYE_OUTER)?.takeUnless { it == Vec3.ZERO }
        ?: landmarkOrNull(FaceLandmarks.LEFT_EYE_INNER)
    val b = landmarkOrNull(FaceLandmarks.RIGHT_EYE_OUTER)?.takeUnless { it == Vec3.ZERO }
        ?: landmarkOrNull(FaceLandmarks.RIGHT_EYE_INNER)
    if (a == null || b == null) return null
    return if (a.x <= b.x) b else a
}

fun FaceFrame.overlayRollFromEyes(): Quaternion {
    val left = screenLeftEye() ?: return Quaternion.IDENTITY
    val right = screenRightEye() ?: return Quaternion.IDENTITY
    val rollRad = atan2(right.y - left.y, right.x - left.x)
    return Quaternion.fromAxisAngleDegrees(
        Vec3(0f, 0f, 1f),
        Math.toDegrees(rollRad.toDouble()).toFloat(),
    )
}
