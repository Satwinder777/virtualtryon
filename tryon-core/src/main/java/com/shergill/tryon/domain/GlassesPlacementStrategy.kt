package com.shergill.tryon.domain

import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Glasses stay **horizontal on the face** (frame along the eye line / screen X),
 * and the whole model tracks the face in X and Y.
 *
 * Orientation is screen-plane roll only (around Z). That keeps the frames lying
 * across the eyes instead of standing upright like a vertical stick. Position is
 * the mid-point of the outer eyes every frame so left/right motion follows on X.
 */
class GlassesPlacementStrategy(
    private val baseScaleFactor: Float = 1.2f,
) : PlacementStrategy {

    override fun computeTransform(face: FaceFrame): Placement? {
        if (!face.faceDetected) return null

        val leftEye = face.screenLeftEye() ?: return null
        val rightEye = face.screenRightEye() ?: return null

        // Screen axes — do not use landmark Z for orientation (it tips frames vertical).
        val dx = rightEye.x - leftEye.x
        val dy = rightEye.y - leftEye.y
        val eyeSpanXy = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)

        val midX = (leftEye.x + rightEye.x) * 0.5f
        val midY = (leftEye.y + rightEye.y) * 0.5f

        // Roll in the screen plane so the frame stays horizontal along the eyes.
        val rollDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        val rotation = Quaternion.fromAxisAngleDegrees(Vec3(0f, 0f, 1f), rollDeg)

        // Optional nose fine-tune on Y only — X always follows mid-eyes.
        val tip = face.landmarkOrNull(FaceLandmarks.NOSE_TIP)?.takeUnless { it == Vec3.ZERO }
        val bridge = face.landmarkOrNull(FaceLandmarks.NOSE_BRIDGE)?.takeUnless { it == Vec3.ZERO }
        val noseY = when {
            tip != null && bridge != null -> bridge.y * 0.7f + tip.y * 0.3f
            bridge != null -> bridge.y
            tip != null -> tip.y
            else -> midY
        }

        // Down along the rolled frame (local −Y), so pads sit on the nose while
        // the long axis of the glasses stays along the eye line (local +X).
        val localDown = rotation.rotate(Vec3(0f, -0.10f * eyeSpanXy, 0f))
        val position = Vec3(
            midX + localDown.x,
            (midY * 0.4f + noseY * 0.6f) + localDown.y,
            0f,
        )

        return Placement(
            position = position,
            rotation = rotation,
            scaleMultiplier = eyeSpanXy * baseScaleFactor,
        )
    }
}

internal fun rotationFromAxes(xAxis: Vec3, yAxis: Vec3, zAxis: Vec3): Quaternion {
    val m = floatArrayOf(
        xAxis.x, xAxis.y, xAxis.z, 0f,
        yAxis.x, yAxis.y, yAxis.z, 0f,
        zAxis.x, zAxis.y, zAxis.z, 0f,
        0f, 0f, 0f, 1f,
    )
    return Quaternion.fromRotationMatrix(m)
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
    val dx = right.x - left.x
    val dy = right.y - left.y
    val rollDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    return Quaternion.fromAxisAngleDegrees(Vec3(0f, 0f, 1f), rollDeg)
}
