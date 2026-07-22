package com.shergill.tryon.domain

import kotlin.math.sqrt

/**
 * Glasses follow the face in **both X and Y** (and rotate with yaw/pitch/roll).
 *
 * Position XY is taken directly from the mid-point of the outer eyes every frame so
 * left/right head translation is never dropped. Orientation uses the full 3D
 * forehead→chin vector so pitch (nod) is applied, not only yaw.
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

        val midX = (leftEye.x + rightEye.x) * 0.5f
        val midY = (leftEye.y + rightEye.y) * 0.5f
        val midZ = (leftEye.z + rightEye.z) * 0.5f

        val dx = rightEye.x - leftEye.x
        val dy = rightEye.y - leftEye.y
        val eyeSpanXy = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)

        val rotation = glassesOrientation(
            leftEye = leftEye,
            rightEye = rightEye,
            forehead = forehead,
            chin = chin,
            leftJaw = face.landmarkOrNull(FaceLandmarks.JAW_LEFT),
            rightJaw = face.landmarkOrNull(FaceLandmarks.JAW_RIGHT),
        )

        // Primary track: mid-eyes in X and Y every frame (left/right + up/down).
        // Nose tip only fine-tunes Y slightly so pads sit on the bridge.
        val tip = face.landmarkOrNull(FaceLandmarks.NOSE_TIP)?.takeUnless { it == Vec3.ZERO }
        val bridge = face.landmarkOrNull(FaceLandmarks.NOSE_BRIDGE)?.takeUnless { it == Vec3.ZERO }
        val noseY = when {
            tip != null && bridge != null -> tip.y * 0.35f + bridge.y * 0.65f
            bridge != null -> bridge.y
            tip != null -> tip.y
            else -> midY
        }
        val noseX = when {
            tip != null && bridge != null -> tip.x * 0.35f + bridge.x * 0.65f
            bridge != null -> bridge.x
            tip != null -> tip.x
            else -> midX
        }
        // Blend mid-eyes (stable track) with nose (rest point) on BOTH axes.
        val anchor = Vec3(
            midX * 0.55f + noseX * 0.45f,
            midY * 0.45f + noseY * 0.55f,
            midZ * 0.35f,
        )

        // Face-local offset so the frame sits on the nose while still tracking X/Y.
        val localOffset = Vec3(0f, -0.10f * eyeSpanXy, -0.03f * eyeSpanXy)
        val position = anchor + rotation.rotate(localOffset)

        return Placement(
            position = position,
            rotation = rotation,
            scaleMultiplier = eyeSpanXy * baseScaleFactor,
        )
    }
}

/**
 * Full 3D head orientation: eyes (+ jaw) for X, forehead/chin for pitch, Z toward camera.
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
        if (leftJaw.x <= rightJaw.x) rightJaw - leftJaw else leftJaw - rightJaw
    } else {
        eyeAxis
    }

    var xAxis = (eyeAxis * 0.7f + jawAxis * 0.3f).normalized()
    if (xAxis.length() < 1e-5f) xAxis = eyeAxis.normalized()

    // Full 3D up — required for pitch (nod). Do not flatten Z.
    val upApprox = (forehead - chin).normalized()
    var zAxis = xAxis.cross(upApprox).normalized()
    if (zAxis.length() < 1e-5f) zAxis = Vec3(0f, 0f, 1f)
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
