package com.shergill.tryon.domain

import kotlin.math.sqrt

/**
 * Lenskart-style auto fit: glasses stay glued to the face every frame.
 *
 * - **Position**: mid outer-eyes every frame (X+Y track) + light nose-bridge blend
 * - **Rotation**: face basis with **damped landmark Z** (avoids vertical-stick yaw)
 * - **Scale**: XY eye span only
 * - Local nose offset is rotated in face space so it re-seats when the head turns
 */
class GlassesPlacementStrategy(
    private val baseScaleFactor: Float = 1.12f,
    /** Drop from rest point toward the nose pads, as a fraction of eye span. */
    private val noseDropFactor: Float = 0.08f,
    /** Push slightly into the face so frames sit on skin, not float in front. */
    private val depthInsetFactor: Float = 0.035f,
) : PlacementStrategy {

    override fun computeTransform(face: FaceFrame): Placement? {
        if (!face.faceDetected) return null

        val leftEye = face.screenLeftEye() ?: return null
        val rightEye = face.screenRightEye() ?: return null
        val forehead = face.landmarkOrNull(FaceLandmarks.FOREHEAD_TOP)?.takeUnless { it == Vec3.ZERO }
        val chin = face.landmarkOrNull(FaceLandmarks.CHIN)?.takeUnless { it == Vec3.ZERO }

        val dx = rightEye.x - leftEye.x
        val dy = rightEye.y - leftEye.y
        val eyeSpanXy = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)

        val midEyes = Vec3(
            (leftEye.x + rightEye.x) * 0.5f,
            (leftEye.y + rightEye.y) * 0.5f,
            (leftEye.z + rightEye.z) * 0.5f,
        )

        val rotation = glassesOrientation(
            leftEye = leftEye,
            rightEye = rightEye,
            forehead = forehead,
            chin = chin,
            leftJaw = face.landmarkOrNull(FaceLandmarks.JAW_LEFT),
            rightJaw = face.landmarkOrNull(FaceLandmarks.JAW_RIGHT),
        )

        val anchor = glassesFaceAnchor(face, midEyes)
        val localOffset = Vec3(
            0f,
            -noseDropFactor * eyeSpanXy,
            -depthInsetFactor * eyeSpanXy,
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
 * Mid-eyes drive tracking (so the frame follows the face immediately).
 * Nose bridge only fine-tunes the rest height — never overrides X/Y lock.
 */
internal fun glassesFaceAnchor(face: FaceFrame, midEyes: Vec3): Vec3 {
    val bridge = face.landmarkOrNull(FaceLandmarks.NOSE_BRIDGE)?.takeUnless { it == Vec3.ZERO }
    return if (bridge != null) {
        Vec3(
            midEyes.x * 0.85f + bridge.x * 0.15f,
            midEyes.y * 0.70f + bridge.y * 0.30f,
            midEyes.z * 0.70f + bridge.z * 0.30f,
        )
    } else {
        midEyes
    }
}

/**
 * Face orientation with landmark-Z damping.
 *
 * Full MediaPipe Z on the eye axis tipped frames into a vertical stick; we keep
 * XY-dominant roll, mild pitch from forehead→chin, and a small yaw from damped Z.
 */
internal fun glassesOrientation(
    leftEye: Vec3,
    rightEye: Vec3,
    forehead: Vec3?,
    chin: Vec3?,
    leftJaw: Vec3?,
    rightJaw: Vec3?,
    zDamp: Float = 0.22f,
): Quaternion {
    val eyeAxis = Vec3(
        rightEye.x - leftEye.x,
        rightEye.y - leftEye.y,
        (rightEye.z - leftEye.z) * zDamp,
    )
    val jawAxis = if (
        leftJaw != null && rightJaw != null &&
        leftJaw != Vec3.ZERO && rightJaw != Vec3.ZERO
    ) {
        val (jl, jr) = if (leftJaw.x <= rightJaw.x) leftJaw to rightJaw else rightJaw to leftJaw
        Vec3(jr.x - jl.x, jr.y - jl.y, (jr.z - jl.z) * zDamp)
    } else {
        eyeAxis
    }

    var xAxis = (eyeAxis * 0.75f + jawAxis * 0.25f).normalized()
    if (xAxis.length() < 1e-5f) {
        xAxis = Vec3(eyeAxis.x, eyeAxis.y, 0f).normalized()
    }

    val upApprox = if (forehead != null && chin != null) {
        Vec3(
            forehead.x - chin.x,
            forehead.y - chin.y,
            (forehead.z - chin.z) * zDamp,
        ).normalized()
    } else {
        Vec3.UP
    }

    var zAxis = xAxis.cross(upApprox).normalized()
    if (zAxis.length() < 1e-5f) zAxis = Vec3(0f, 0f, 1f)
    if (zAxis.z < 0f) {
        zAxis = zAxis * -1f
        xAxis = xAxis * -1f
    }
    val yAxis = zAxis.cross(xAxis).normalized()
    return rotationFromAxes(xAxis, yAxis, zAxis)
}

/**
 * Light EMA so jitter dies without trailing the face (Lenskart-like glue).
 * Defaults are intentionally high — double-smoothing lag is what made frames float.
 */
class PlacementSmoother(
    private val posAlpha: Float = 0.88f,
    private val scaleAlpha: Float = 0.80f,
    private val rotAlpha: Float = 0.85f,
) {
    private var previous: Placement? = null

    fun reset() {
        previous = null
    }

    fun smooth(next: Placement?): Placement? {
        if (next == null) {
            previous = null
            return null
        }
        val prev = previous
        if (prev == null) {
            previous = next
            return next
        }
        val t = posAlpha.coerceIn(0.05f, 1f)
        val st = scaleAlpha.coerceIn(0.05f, 1f)
        val rt = rotAlpha.coerceIn(0.05f, 1f)
        val smoothed = Placement(
            position = Vec3(
                prev.position.x + (next.position.x - prev.position.x) * t,
                prev.position.y + (next.position.y - prev.position.y) * t,
                prev.position.z + (next.position.z - prev.position.z) * t,
            ),
            rotation = slerp(prev.rotation, next.rotation, rt),
            scaleMultiplier = prev.scaleMultiplier + (next.scaleMultiplier - prev.scaleMultiplier) * st,
        )
        previous = smoothed
        return smoothed
    }

    private fun slerp(a: Quaternion, b: Quaternion, t: Float): Quaternion {
        var bx = b.x
        var by = b.y
        var bz = b.z
        var bw = b.w
        var dot = a.x * bx + a.y * by + a.z * bz + a.w * bw
        if (dot < 0f) {
            bx = -bx
            by = -by
            bz = -bz
            bw = -bw
            dot = -dot
        }
        if (dot > 0.9995f) {
            return Quaternion(
                a.x + (bx - a.x) * t,
                a.y + (by - a.y) * t,
                a.z + (bz - a.z) * t,
                a.w + (bw - a.w) * t,
            ).let {
                val n = sqrt(it.x * it.x + it.y * it.y + it.z * it.z + it.w * it.w).coerceAtLeast(1e-6f)
                Quaternion(it.x / n, it.y / n, it.z / n, it.w / n)
            }
        }
        val theta = kotlin.math.acos(dot.coerceIn(-1f, 1f))
        val s = kotlin.math.sin(theta).coerceAtLeast(1e-6f)
        val w1 = kotlin.math.sin((1f - t) * theta) / s
        val w2 = kotlin.math.sin(t * theta) / s
        return Quaternion(
            a.x * w1 + bx * w2,
            a.y * w1 + by * w2,
            a.z * w1 + bz * w2,
            a.w * w1 + bw * w2,
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
    return glassesOrientation(
        leftEye = left,
        rightEye = right,
        forehead = landmarkOrNull(FaceLandmarks.FOREHEAD_TOP),
        chin = landmarkOrNull(FaceLandmarks.CHIN),
        leftJaw = landmarkOrNull(FaceLandmarks.JAW_LEFT),
        rightJaw = landmarkOrNull(FaceLandmarks.JAW_RIGHT),
    )
}
