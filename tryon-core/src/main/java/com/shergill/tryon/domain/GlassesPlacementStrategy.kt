package com.shergill.tryon.domain

import kotlin.math.sqrt

/**
 * Glasses placement with nose-bridge anchor and full head follow (yaw / pitch / roll).
 *
 * Critical rules that keep the model glued during rotation:
 * 1. **Position is always the nose rest point** in overlay space, plus a **local** offset
 *    rotated by the face quaternion — never a screen-space "down" nudge (that drifts when yawing).
 * 2. **Rotation** is a right-handed orthonormal basis from eyes + jaw/ears + forehead/chin.
 *    No Z-damping and no opportunistic Y-flip (those turned yaw into vertical shift).
 * 3. **Scale** from screen-XY outer-eye distance so MediaPipe Z cannot inflate size.
 */
class GlassesPlacementStrategy(
    private val baseScaleFactor: Float = 1.15f,
) : PlacementStrategy {

    override fun computeTransform(face: FaceFrame): Placement? {
        if (!face.faceDetected) return null

        val leftEye = face.screenLeftEye() ?: return null
        val rightEye = face.screenRightEye() ?: return null
        val forehead = face.landmarkOrNull(FaceLandmarks.FOREHEAD_TOP)?.takeUnless { it == Vec3.ZERO }
            ?: return null
        val chin = face.landmarkOrNull(FaceLandmarks.CHIN)?.takeUnless { it == Vec3.ZERO }
            ?: return null

        val dx = rightEye.x - leftEye.x
        val dy = rightEye.y - leftEye.y
        val eyeSpanXy = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)

        val leftJaw = face.landmarkOrNull(FaceLandmarks.JAW_LEFT)
        val rightJaw = face.landmarkOrNull(FaceLandmarks.JAW_RIGHT)
        val rotation = glassesOrientation(
            leftEye = leftEye,
            rightEye = rightEye,
            forehead = forehead,
            chin = chin,
            leftJaw = leftJaw,
            rightJaw = rightJaw,
        )

        val anchor = glassesNoseAnchor(face, leftEye, rightEye)
        // Local model offset: slightly down the nose and a touch into the face so pads sit on skin.
        // Applied in face space so it tracks yaw/pitch/roll instead of floating in screen space.
        val localOffset = Vec3(
            0f,
            -0.12f * eyeSpanXy,
            -0.04f * eyeSpanXy,
        )
        val position = anchor + rotation.rotate(localOffset)

        return Placement(
            position = position,
            rotation = rotation,
            scaleMultiplier = eyeSpanXy * baseScaleFactor,
        )
    }
}

/**
 * Rest point between the eyes on the nose — prefer landmark 168, blend toward nose tip.
 */
internal fun glassesNoseAnchor(face: FaceFrame, leftEye: Vec3, rightEye: Vec3): Vec3 {
    val midEyes = Vec3(
        (leftEye.x + rightEye.x) * 0.5f,
        (leftEye.y + rightEye.y) * 0.5f,
        (leftEye.z + rightEye.z) * 0.5f,
    )
    val bridge = face.landmarkOrNull(FaceLandmarks.NOSE_BRIDGE)?.takeUnless { it == Vec3.ZERO }
    val tip = face.landmarkOrNull(FaceLandmarks.NOSE_TIP)?.takeUnless { it == Vec3.ZERO }

    val base = bridge ?: midEyes
    return if (tip != null) {
        // 75% bridge / mid-eyes, 25% toward tip — sits on the nose, not the forehead.
        Vec3(
            base.x * 0.75f + tip.x * 0.25f,
            base.y * 0.75f + tip.y * 0.25f,
            base.z * 0.75f + tip.z * 0.25f,
        )
    } else {
        base
    }
}

/**
 * Right-handed basis in overlay space:
 * - X: along the face toward screen-right (eyes, blended with jaw/ears for yaw stability)
 * - Y: up the face (chin → forehead), re-orthogonalized
 * - Z: toward the camera (X × Y), forced to keep +Z facing the viewer
 *
 * Intentionally does **not** flip when Y points slightly down — that heuristic converted
 * yaw into a vertical jump during head turns.
 */
internal fun glassesOrientation(
    leftEye: Vec3,
    rightEye: Vec3,
    forehead: Vec3,
    chin: Vec3,
    leftJaw: Vec3?,
    rightJaw: Vec3?,
): Quaternion {
    val eyeAxis = rightEye - leftEye
    val jawAxis = if (
        leftJaw != null && rightJaw != null &&
        leftJaw != Vec3.ZERO && rightJaw != Vec3.ZERO
    ) {
        // Screen-order the jaw points the same way as eyes.
        if (leftJaw.x <= rightJaw.x) rightJaw - leftJaw else leftJaw - rightJaw
    } else {
        eyeAxis
    }

    // Blend eyes (precise roll) with jaw/ears (stable yaw when foreshortened).
    var xAxis = (eyeAxis * 0.65f + jawAxis * 0.35f).normalized()
    if (xAxis.length() < 1e-5f) xAxis = eyeAxis.normalized()

    val upApprox = (forehead - chin).normalized()
    var zAxis = xAxis.cross(upApprox).normalized()
    if (zAxis.length() < 1e-5f) {
        zAxis = Vec3(0f, 0f, 1f)
    }
    // Only enforce camera-facing hemisphere — do not touch Y-up heuristics here.
    if (zAxis.z < 0f) {
        zAxis = zAxis * -1f
        xAxis = xAxis * -1f
    }
    val yAxis = zAxis.cross(xAxis).normalized()
    return rotationFromAxes(xAxis, yAxis, zAxis)
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
    val forehead = landmarkOrNull(FaceLandmarks.FOREHEAD_TOP) ?: return Quaternion.IDENTITY
    val chin = landmarkOrNull(FaceLandmarks.CHIN) ?: return Quaternion.IDENTITY
    return glassesOrientation(
        leftEye = left,
        rightEye = right,
        forehead = forehead,
        chin = chin,
        leftJaw = landmarkOrNull(FaceLandmarks.JAW_LEFT),
        rightJaw = landmarkOrNull(FaceLandmarks.JAW_RIGHT),
    )
}
