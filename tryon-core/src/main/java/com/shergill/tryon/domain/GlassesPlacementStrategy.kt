package com.shergill.tryon.domain

import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Live glasses overlay — position / scale from eyes + nose; orientation tracks head turn.
 *
 * Every call uses the **current** [FaceFrame] landmarks (already in overlay space):
 * - Left eye center = midpoint(33, 133)
 * - Right eye center = midpoint(362, 263)
 * - Anchor = midpoint(left, right), Y blended with nose bridge 168
 * - Scale = distance(33, 263) / glassesReferenceWidth × [framePadding],
 *   divided by |cos(yaw)| so frames do not shrink when the head turns (XY foreshortening).
 *   Default padding ≈1.7 (face-fitting; 2.2 was oversized).
 * - Rotation:
 *   - **Yaw** = nose-tip X vs mid-eyes (2D) — NOT raw MediaPipe Z (Z bias pinned yaw≈−34°)
 *   - **Roll** = eye-line atan2, **damped when |yaw| is large** — profile foreshortening
 *     makes dy/dx explode (~50° false roll) and tips temples onto the face
 *   - **Pitch** = light forehead↔chin depth, heavily damped (default off)
 *
 * No iris, no previous-frame pose, no screen-corner defaults inside this strategy.
 * Renderer apply order: `T * R * (s * Normalize)`.
 *
 * Optional [onDebug] receives per-frame landmark/anchor/scale/angles for on-device checks
 * (keep domain Android-free — log from the UI layer).
 */
class GlassesPlacementStrategy(
    private val glassesReferenceWidth: Float = 1f,
    /** Multiplier on outer-eye span → on-face frame width (2.2 looked oversized). */
    private val framePadding: Float = 1.7f,
    private val noseBridgeBlendY: Float = 0.35f,
    /** Degrees of yaw when nose tip sits one full eye-span off the eye midpoint. */
    private val yawScaleDeg: Float = 42f,
    private val yawGain: Float = 1f,
    /** Keep pitch near 0 — landmark-Z pitch tipped temples into the face plane. */
    private val pitchGain: Float = 0f,
    /** Allow near-profile turns; old 32° clamp left temples drawn across the cheek. */
    private val maxYawDeg: Float = 58f,
    private val maxPitchDeg: Float = 18f,
    /**
     * |yaw| at which eye-line roll is fully suppressed. Below this, roll fades with
     * `(1 - |yaw|/rollYawFadeDeg)²`.
     */
    private val rollYawFadeDeg: Float = 48f,
    private val onDebug: ((GlassesPlacementDebug) -> Unit)? = null,
) : PlacementStrategy {

    override fun computeTransform(face: FaceFrame): Placement? {
        if (!face.faceDetected) return null

        // Require live eye corners — never invent coordinates.
        val leftOuter = face.landmarkOrNull(FaceLandmarks.RIGHT_EYE_OUTER) ?: return null // 33
        val leftInner = face.landmarkOrNull(FaceLandmarks.RIGHT_EYE_INNER) ?: return null // 133
        val rightInner = face.landmarkOrNull(FaceLandmarks.LEFT_EYE_INNER) ?: return null // 362
        val rightOuter = face.landmarkOrNull(FaceLandmarks.LEFT_EYE_OUTER) ?: return null // 263

        val leftCenter = midpoint(leftOuter, leftInner)
        val rightCenter = midpoint(rightInner, rightOuter)

        // Order by overlay X so roll follows the on-screen eye line.
        val (screenLeft, screenRight) = if (leftCenter.x <= rightCenter.x) {
            leftCenter to rightCenter
        } else {
            rightCenter to leftCenter
        }

        val midX = (screenLeft.x + screenRight.x) * 0.5f
        val midY = (screenLeft.y + screenRight.y) * 0.5f

        val bridge = face.landmarkOrNull(FaceLandmarks.NOSE_BRIDGE) // 168
        val anchorX = midX
        val anchorY = if (bridge != null) {
            midY * (1f - noseBridgeBlendY) + bridge.y * noseBridgeBlendY
        } else {
            midY
        }

        val dx = screenRight.x - screenLeft.x
        val dy = screenRight.y - screenLeft.y
        val rawRollDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

        val eyeSpanXy = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)

        // Yaw from nose tip (or bridge) offset in X — MediaPipe Z left/right bias was ~−34° always.
        val yawLandmark = face.landmarkOrNull(FaceLandmarks.NOSE_TIP) ?: bridge
        var yawDeg = 0f
        if (yawLandmark != null) {
            yawDeg = ((yawLandmark.x - midX) / eyeSpanXy) * yawScaleDeg * yawGain
            yawDeg = yawDeg.coerceIn(-maxYawDeg, maxYawDeg)
        }

        // Side face: foreshortened eyes make atan2(dy,dx) huge → temples tip onto the face.
        // Keep full roll when frontal; fade it out as |yaw| grows.
        val yawAbs = kotlin.math.abs(yawDeg)
        val fade = rollYawFadeDeg.coerceAtLeast(1f)
        val rollWeight = (1f - (yawAbs / fade).coerceIn(0f, 1f)).let { w -> w * w }
        val rollDeg = rawRollDeg * rollWeight

        // Pitch: small depth cue only (heavily damped — Z scale ≠ XY).
        val forehead = face.landmarkOrNull(FaceLandmarks.FOREHEAD_TOP)
        val chin = face.landmarkOrNull(FaceLandmarks.CHIN)
        var pitchDeg = 0f
        if (forehead != null && chin != null) {
            val upY = (forehead.y - chin.y).coerceAtLeast(1e-4f)
            val upZ = forehead.z - chin.z
            pitchDeg = Math.toDegrees(atan2((-upZ).toDouble(), upY.toDouble())).toFloat() * pitchGain
            pitchDeg = pitchDeg.coerceIn(-maxPitchDeg, maxPitchDeg)
        }

        // Model: +Y up, +Z toward camera. yaw (Y) → pitch (X) → roll (Z).
        val rotation =
            Quaternion.fromAxisAngleDegrees(Vec3(0f, 1f, 0f), yawDeg) *
                Quaternion.fromAxisAngleDegrees(Vec3(1f, 0f, 0f), pitchDeg) *
                Quaternion.fromAxisAngleDegrees(Vec3(0f, 0f, 1f), rollDeg)

        // Outer-corner span 33 → 263 in overlay XY (near/far + head width).
        val spanDx = rightOuter.x - leftOuter.x
        val spanDy = rightOuter.y - leftOuter.y
        val eyeSpan = sqrt(spanDx * spanDx + spanDy * spanDy).coerceAtLeast(1e-4f)
        // Undo XY foreshortening so frames stay face-width when the head turns.
        val yawCos = kotlin.math.cos(Math.toRadians(yawDeg.toDouble())).toFloat().coerceAtLeast(0.45f)
        val scale = (eyeSpan / glassesReferenceWidth.coerceAtLeast(1e-4f)) * framePadding / yawCos

        onDebug?.invoke(
            GlassesPlacementDebug(
                landmark33 = leftOuter,
                landmark133 = leftInner,
                landmark362 = rightInner,
                landmark263 = rightOuter,
                landmark168 = bridge,
                anchor = Vec3(anchorX, anchorY, 0f),
                scale = scale,
                rollDeg = rollDeg,
                yawDeg = yawDeg,
                pitchDeg = pitchDeg,
            ),
        )

        return Placement(
            position = Vec3(anchorX, anchorY, 0f),
            rotation = rotation,
            scaleMultiplier = scale,
        )
    }
}

/** Debug payload for on-device verification. */
data class GlassesPlacementDebug(
    val landmark33: Vec3,
    val landmark133: Vec3,
    val landmark362: Vec3,
    val landmark263: Vec3,
    val landmark168: Vec3?,
    val anchor: Vec3,
    val scale: Float,
    val rollDeg: Float,
    val yawDeg: Float = 0f,
    val pitchDeg: Float = 0f,
)

private fun midpoint(a: Vec3, b: Vec3): Vec3 = Vec3(
    (a.x + b.x) * 0.5f,
    (a.y + b.y) * 0.5f,
    (a.z + b.z) * 0.5f,
)

/** Optional EMA helper for tests / other accessories. Glasses path does not use this. */
class PlacementSmoother(
    private val posAlpha: Float = 1f,
    private val scaleAlpha: Float = 1f,
    private val rotAlpha: Float = 1f,
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
        if (prev == null || posAlpha >= 0.999f && scaleAlpha >= 0.999f && rotAlpha >= 0.999f) {
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

fun FaceFrame.leftEyeCenterForGlasses(): Vec3? {
    val outer = landmarkOrNull(FaceLandmarks.RIGHT_EYE_OUTER) ?: return null
    val inner = landmarkOrNull(FaceLandmarks.RIGHT_EYE_INNER) ?: return null
    return midpoint(outer, inner)
}

fun FaceFrame.rightEyeCenterForGlasses(): Vec3? {
    val outer = landmarkOrNull(FaceLandmarks.LEFT_EYE_OUTER) ?: return null
    val inner = landmarkOrNull(FaceLandmarks.LEFT_EYE_INNER) ?: return null
    return midpoint(outer, inner)
}

fun FaceFrame.outerEyeCornerDistance(): Float? {
    val a = landmarkOrNull(FaceLandmarks.RIGHT_EYE_OUTER) ?: return null
    val b = landmarkOrNull(FaceLandmarks.LEFT_EYE_OUTER) ?: return null
    val dx = b.x - a.x
    val dy = b.y - a.y
    return sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)
}

fun FaceFrame.glassesEyeCenters(): GlassesEyeCenters? {
    val left = leftEyeCenterForGlasses() ?: return null
    val right = rightEyeCenterForGlasses() ?: return null
    return if (left.x <= right.x) {
        GlassesEyeCenters(screenLeft = left, screenRight = right)
    } else {
        GlassesEyeCenters(screenLeft = right, screenRight = left)
    }
}

data class GlassesEyeCenters(
    val screenLeft: Vec3,
    val screenRight: Vec3,
)

fun FaceFrame.screenLeftEye(): Vec3? = glassesEyeCenters()?.screenLeft

fun FaceFrame.screenRightEye(): Vec3? = glassesEyeCenters()?.screenRight

fun FaceFrame.overlayRollFromEyes(): Quaternion {
    val eyes = glassesEyeCenters() ?: return Quaternion.IDENTITY
    val dx = eyes.screenRight.x - eyes.screenLeft.x
    val dy = eyes.screenRight.y - eyes.screenLeft.y
    val rollDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    return Quaternion.fromAxisAngleDegrees(Vec3(0f, 0f, 1f), rollDeg)
}
