package com.shergill.tryon.domain

import kotlin.math.cos
import kotlin.math.sin

data class Placement(
    val position: Vec3,
    val rotation: Quaternion,
    val scaleMultiplier: Float,
)

interface PlacementStrategy {
    fun computeTransform(face: FaceFrame): Placement?
}

fun FaceFrame.headRotation(): Quaternion =
    Quaternion.fromRotationMatrix(facialTransformationMatrix)

/**
 * Orientation from face landmarks in overlay space (more reliable than the facial
 * transformation matrix after PreviewView crop / front-camera mirroring).
 *
 * Basis:
 * - X: right eye → left eye (horizontal across the face)
 * - Y: chin → forehead (up the face)
 * - Z: completes a right-handed frame (toward the viewer for a front-facing face)
 */
fun FaceFrame.orientationFromLandmarks(): Quaternion {
    val leftEye = landmarkOrNull(FaceLandmarks.LEFT_EYE_OUTER)
        ?: landmarkOrNull(FaceLandmarks.LEFT_EYE_INNER)
        ?: return Quaternion.IDENTITY
    val rightEye = landmarkOrNull(FaceLandmarks.RIGHT_EYE_OUTER)
        ?: landmarkOrNull(FaceLandmarks.RIGHT_EYE_INNER)
        ?: return Quaternion.IDENTITY
    val forehead = landmarkOrNull(FaceLandmarks.FOREHEAD_TOP) ?: return Quaternion.IDENTITY
    val chin = landmarkOrNull(FaceLandmarks.CHIN) ?: return Quaternion.IDENTITY

    var xAxis = (leftEye - rightEye).normalized()
    var yAxis = (forehead - chin).normalized()
    var zAxis = xAxis.cross(yAxis).normalized()
    if (zAxis.length() < 1e-5f) return Quaternion.IDENTITY

    // Prefer Z toward the camera (+Z in our orthographic overlay).
    if (zAxis.z < 0f) {
        zAxis = zAxis * -1f
        xAxis = xAxis * -1f
    }
    yAxis = zAxis.cross(xAxis).normalized()
    return rotationFromBasis(xAxis, yAxis, zAxis)
}

private fun rotationFromBasis(xAxis: Vec3, yAxis: Vec3, zAxis: Vec3): Quaternion {
    val m = floatArrayOf(
        xAxis.x, xAxis.y, xAxis.z, 0f,
        yAxis.x, yAxis.y, yAxis.z, 0f,
        zAxis.x, zAxis.y, zAxis.z, 0f,
        0f, 0f, 0f, 1f,
    )
    return Quaternion.fromRotationMatrix(m)
}

operator fun Quaternion.times(other: Quaternion): Quaternion = Quaternion(
    x = w * other.x + x * other.w + y * other.z - z * other.y,
    y = w * other.y - x * other.z + y * other.w + z * other.x,
    z = w * other.z + x * other.y - y * other.x + z * other.w,
    w = w * other.w - x * other.x - y * other.y - z * other.z,
)

/** Axis-angle in degrees. */
fun Quaternion.Companion.fromAxisAngleDegrees(axis: Vec3, degrees: Float): Quaternion {
    val n = axis.normalized()
    val half = Math.toRadians(degrees.toDouble()).toFloat() * 0.5f
    val s = sin(half)
    return Quaternion(n.x * s, n.y * s, n.z * s, cos(half))
}

fun FaceFrame.interEyeDistance(): Float {
    val leftInner = landmarkOrNull(FaceLandmarks.LEFT_EYE_INNER) ?: return 1f
    val rightInner = landmarkOrNull(FaceLandmarks.RIGHT_EYE_INNER) ?: return 1f
    return (leftInner - rightInner).length().coerceAtLeast(1e-4f)
}

fun FaceFrame.faceHeight(): Float {
    val forehead = landmarkOrNull(FaceLandmarks.FOREHEAD_TOP) ?: return interEyeDistance() * 2f
    val chin = landmarkOrNull(FaceLandmarks.CHIN) ?: return interEyeDistance() * 2f
    return (forehead - chin).length().coerceAtLeast(1e-4f)
}
