package com.shergill.tryon.domain

import kotlin.math.sqrt

/**
 * Glasses placement — stable 3D head follow for an orthographic selfie overlay.
 *
 * - Scale from **screen XY** eye span (MediaPipe Z must not inflate size).
 * - Basis uses dampened Z so yaw/pitch work without shearing temples under the chin.
 * - Anchor at mid outer-eyes / nose bridge, slightly down the face.
 *
 * Model after normalize: width≈1 along X, pivot at lenses, temples along −Z.
 */
class GlassesPlacementStrategy(
    /** Unit-width frames should slightly wider than outer-eye distance. */
    private val baseScaleFactor: Float = 1.12f,
) : PlacementStrategy {

    override fun computeTransform(face: FaceFrame): Placement? {
        if (!face.faceDetected) return null

        val left = face.screenLeftEye() ?: return null
        val right = face.screenRightEye() ?: return null
        val forehead = face.landmarkOrNull(FaceLandmarks.FOREHEAD_TOP)?.takeUnless { it == Vec3.ZERO }
            ?: return null
        val chin = face.landmarkOrNull(FaceLandmarks.CHIN)?.takeUnless { it == Vec3.ZERO }
            ?: return null

        val dx = right.x - left.x
        val dy = right.y - left.y
        val eyeSpanXy = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)

        val rotation = glassesBasis(left, right, forehead, chin)

        val bridge = face.landmarkOrNull(FaceLandmarks.NOSE_BRIDGE)?.takeUnless { it == Vec3.ZERO }
        val midX = (left.x + right.x) * 0.5f
        val midY = (left.y + right.y) * 0.5f
        val midZ = (left.z + right.z) * 0.5f
        val anchor = if (bridge != null) {
            Vec3(bridge.x, bridge.y, midZ)
        } else {
            Vec3(midX, midY, midZ)
        }

        // Local face "down" from forehead→chin projected to dampened up axis.
        val down = glassesDownAxis(forehead, chin)
        val position = Vec3(
            anchor.x + down.x * (0.10f * eyeSpanXy),
            anchor.y + down.y * (0.10f * eyeSpanXy),
            // Keep near the face plane; ortho ignores Z for projection but depth-tests use it.
            midZ * 0.25f,
        )

        return Placement(
            position = position,
            rotation = rotation,
            scaleMultiplier = eyeSpanXy * baseScaleFactor,
        )
    }
}

/**
 * Orthonormal glasses basis. Landmark Z is dampened so depth noise cannot tip temples
 * under the jaw; enough Z remains for natural yaw when the head turns.
 */
internal fun glassesBasis(
    left: Vec3,
    right: Vec3,
    forehead: Vec3,
    chin: Vec3,
    zDamp: Float = 0.4f,
): Quaternion {
    val eye = Vec3(right.x - left.x, right.y - left.y, (right.z - left.z) * zDamp)
    var xAxis = eye.normalized()
    val upRaw = Vec3(
        forehead.x - chin.x,
        forehead.y - chin.y,
        (forehead.z - chin.z) * zDamp,
    )
    var zAxis = xAxis.cross(upRaw).normalized()
    if (zAxis.length() < 1e-5f) {
        zAxis = Vec3(0f, 0f, 1f)
    }
    if (zAxis.z < 0f) {
        zAxis = zAxis * -1f
        xAxis = xAxis * -1f
    }
    var yAxis = zAxis.cross(xAxis).normalized()
    // Prefer +Y roughly screen-up.
    if (yAxis.y < 0f) {
        yAxis = yAxis * -1f
        zAxis = zAxis * -1f
    }
    return rotationFromAxes(xAxis, yAxis, zAxis)
}

internal fun glassesDownAxis(forehead: Vec3, chin: Vec3): Vec3 {
    val up = Vec3(forehead.x - chin.x, forehead.y - chin.y, 0f).normalized()
    return if (up.length() < 1e-5f) Vec3(0f, -1f, 0f) else up * -1f
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
    return glassesBasis(left, right, forehead, chin)
}
