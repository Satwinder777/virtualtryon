package com.shergill.tryon.domain

import kotlin.math.abs

/**
 * Corrects MediaPipe landmarks that are still in a CameraX buffer rotated 90° relative
 * to the portrait preview. Symptom: outer eye corners share X and span Y → glasses
 * [GlassesPlacementStrategy] roll ≈ ±90° on an upright face.
 */
object LandmarkOrientation {

    /**
     * If outer canthi (33 ↔ 263) are more vertical than horizontal, rotate every
     * landmark ±90° in normalized image space so the eye line becomes horizontal.
     * Depth (z) is preserved.
     */
    fun ensureEyeLineHorizontal(landmarks: List<Vec3>): List<Vec3> {
        if (landmarks.size <= FaceLandmarks.LEFT_EYE_OUTER) return landmarks
        val a = landmarks[FaceLandmarks.RIGHT_EYE_OUTER]
        val b = landmarks[FaceLandmarks.LEFT_EYE_OUTER]
        val dx = abs(b.x - a.x)
        val dy = abs(b.y - a.y)
        // Already horizontal enough (small slack for natural head tilt).
        if (dy <= dx * 1.15f) return landmarks

        val cw = landmarks.map { p -> Vec3(p.y, 1f - p.x, p.z) }
        val ccw = landmarks.map { p -> Vec3(1f - p.y, p.x, p.z) }
        return if (eyeSpanX(cw) >= eyeSpanX(ccw)) cw else ccw
    }

    private fun eyeSpanX(landmarks: List<Vec3>): Float {
        val a = landmarks[FaceLandmarks.RIGHT_EYE_OUTER]
        val b = landmarks[FaceLandmarks.LEFT_EYE_OUTER]
        return abs(b.x - a.x)
    }

    /**
     * Degrees to rotate a CameraX analysis bitmap so it matches a portrait/landscape preview.
     * Avoids both missed rotates (degrees=0 but buffer still landscape) and double rotates
     * (degrees=90 but buffer already portrait).
     */
    fun effectiveBitmapRotationDegrees(
        bufferWidth: Int,
        bufferHeight: Int,
        rotationDegrees: Int,
        previewWidth: Int,
        previewHeight: Int,
    ): Int {
        if (bufferWidth <= 0 || bufferHeight <= 0 || previewWidth <= 0 || previewHeight <= 0) {
            return ((rotationDegrees % 360) + 360) % 360
        }
        val previewPortrait = previewHeight > previewWidth
        val bufferLandscape = bufferWidth > bufferHeight
        val bufferPortrait = bufferHeight > bufferWidth
        val deg = ((rotationDegrees % 360) + 360) % 360

        // Portrait UI + landscape buffer + no metadata rotation → force 90°.
        if (previewPortrait && bufferLandscape && deg == 0) return 90
        // Portrait UI + portrait buffer + metadata says 90/270 → already upright; skip.
        if (previewPortrait && bufferPortrait && (deg == 90 || deg == 270)) return 0
        // Landscape UI + portrait buffer + deg 0 → force 90° (rare for this app).
        if (!previewPortrait && bufferPortrait && deg == 0) return 90
        if (!previewPortrait && bufferLandscape && (deg == 90 || deg == 270)) return 0

        return deg
    }
}
