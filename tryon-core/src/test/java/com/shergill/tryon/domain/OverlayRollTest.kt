package com.shergill.tryon.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OverlayRollTest {

    private fun faceWithEyes(left: Vec3, right: Vec3): FaceFrame {
        val list = MutableList(FaceLandmarks.LANDMARK_COUNT) { Vec3.ZERO }
        list[FaceLandmarks.LEFT_EYE_INNER] = left
        list[FaceLandmarks.RIGHT_EYE_INNER] = right
        list[FaceLandmarks.LEFT_EYE_OUTER] = left
        list[FaceLandmarks.RIGHT_EYE_OUTER] = right
        return FaceFrame(list, Mat4.identity(), faceDetected = true)
    }

    @Test
    fun roll_isNearZero_whenEyesHorizontal_afterMirrorOrdering() {
        // Mirrored selfie: anatomical left at screen-left (x=-0.2), right at x=+0.2
        val face = faceWithEyes(
            left = Vec3(-0.2f, 0.1f, 0f),
            right = Vec3(0.2f, 0.1f, 0f),
        )
        val q = face.overlayRollFromEyes()
        // Identity-ish: w≈1, z≈0 (no 180° flip)
        assertTrue("Expected near-identity roll, got $q", abs(q.z) < 0.1f && abs(q.w) > 0.9f)
    }

    @Test
    fun roll_followsSlightHeadTilt_withoutFlipping() {
        val face = faceWithEyes(
            left = Vec3(-0.2f, 0.15f, 0f),
            right = Vec3(0.2f, 0.05f, 0f),
        )
        val q = face.overlayRollFromEyes()
        // Should be a modest Z rotation, not ~180°
        assertTrue("Unexpected flip: $q", abs(q.w) > 0.7f)
    }
}
