package com.shergill.tryon.domain

/**
 * Glasses placement with full 3D head pose (yaw / pitch / roll).
 *
 * Orthonormal basis from screen-ordered eyes + forehead/chin. Model after normalize:
 * +X toward screen-right, +Y up, −Z into the head (temples → ears). Glasses assets get
 * an X-mirror at load so wearer's-left matches the mirrored selfie.
 */
class GlassesPlacementStrategy(
    private val baseScaleFactor: Float = 1.55f,
) : PlacementStrategy {

    override fun computeTransform(face: FaceFrame): Placement? {
        if (!face.faceDetected) return null

        val left = face.screenLeftEye() ?: return null
        val right = face.screenRightEye() ?: return null
        val forehead = face.landmarkOrNull(FaceLandmarks.FOREHEAD_TOP)?.takeUnless { it == Vec3.ZERO }
            ?: return null
        val chin = face.landmarkOrNull(FaceLandmarks.CHIN)?.takeUnless { it == Vec3.ZERO }
            ?: return null

        val eyeSpan = (right - left).length().coerceAtLeast(1e-4f)

        // +X = along eyes toward screen-right (matches mirrored model +X).
        var xAxis = (right - left).normalized()
        var yAxis = (forehead - chin).normalized()
        var zAxis = xAxis.cross(yAxis).normalized()
        if (zAxis.length() < 1e-5f) return null

        // Keep +Z toward the camera.
        if (zAxis.z < 0f) {
            zAxis = zAxis * -1f
            xAxis = xAxis * -1f
        }
        yAxis = zAxis.cross(xAxis).normalized()

        val rotation = rotationFromAxes(xAxis, yAxis, zAxis)

        val bridge = face.landmarkOrNull(FaceLandmarks.NOSE_BRIDGE)?.takeUnless { it == Vec3.ZERO }
        val mid = Vec3(
            (left.x + right.x) * 0.5f,
            (left.y + right.y) * 0.5f,
            (left.z + right.z) * 0.5f,
        )
        val anchor = bridge ?: mid
        // Nudge toward nose tip and slightly into the face.
        val position = Vec3(
            anchor.x - yAxis.x * (0.06f * eyeSpan) - zAxis.x * (0.05f * eyeSpan),
            anchor.y - yAxis.y * (0.06f * eyeSpan) - zAxis.y * (0.05f * eyeSpan),
            anchor.z - yAxis.z * (0.06f * eyeSpan) - zAxis.z * (0.05f * eyeSpan),
        )

        return Placement(
            position = position,
            rotation = rotation,
            scaleMultiplier = eyeSpan * baseScaleFactor,
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
    val forehead = landmarkOrNull(FaceLandmarks.FOREHEAD_TOP) ?: return Quaternion.IDENTITY
    val chin = landmarkOrNull(FaceLandmarks.CHIN) ?: return Quaternion.IDENTITY
    var xAxis = (right - left).normalized()
    var yAxis = (forehead - chin).normalized()
    var zAxis = xAxis.cross(yAxis).normalized()
    if (zAxis.length() < 1e-5f) return Quaternion.IDENTITY
    if (zAxis.z < 0f) {
        zAxis = zAxis * -1f
        xAxis = xAxis * -1f
    }
    yAxis = zAxis.cross(xAxis).normalized()
    return rotationFromAxes(xAxis, yAxis, zAxis)
}
