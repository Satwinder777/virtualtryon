package com.shergill.tryon.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GlassesPlacementBasisTest {

    private fun faceWithEyes(left: Vec3, right: Vec3, bridge: Vec3? = null): FaceFrame {
        val list = MutableList(FaceLandmarks.LANDMARK_COUNT) { Vec3.ZERO }
        list[FaceLandmarks.LEFT_EYE_INNER] = left
        list[FaceLandmarks.RIGHT_EYE_INNER] = right
        list[FaceLandmarks.LEFT_EYE_OUTER] = left
        list[FaceLandmarks.RIGHT_EYE_OUTER] = right
        list[FaceLandmarks.FOREHEAD_TOP] = Vec3(0f, 0.35f, 0f)
        list[FaceLandmarks.CHIN] = Vec3(0f, -0.25f, 0f)
        if (bridge != null) list[FaceLandmarks.NOSE_BRIDGE] = bridge
        return FaceFrame(list, Mat4.identity(), faceDetected = true)
    }

    @Test
    fun horizontalEyes_useScreenPlaneRoll_notVerticalTip() {
        val placement = GlassesPlacementStrategy().computeTransform(
            faceWithEyes(left = Vec3(-0.2f, 0.1f, 0.5f), right = Vec3(0.2f, 0.1f, -0.5f)),
        )!!
        val q = placement.rotation
        // Pure Z-roll ≈ identity when eyes are level (large landmark Z must not tip).
        assertTrue(abs(q.x) < 0.05f && abs(q.y) < 0.05f)
        assertTrue(abs(q.w) > 0.95f)
    }

    @Test
    fun position_tracksHorizontalFaceMotionOnX() {
        val center = GlassesPlacementStrategy().computeTransform(
            faceWithEyes(left = Vec3(-0.2f, 0.1f, 0f), right = Vec3(0.2f, 0.1f, 0f)),
        )!!
        val right = GlassesPlacementStrategy().computeTransform(
            faceWithEyes(left = Vec3(0.1f, 0.1f, 0f), right = Vec3(0.5f, 0.1f, 0f)),
        )!!
        assertTrue(right.position.x - center.position.x > 0.25f)
        assertEquals(center.position.y, right.position.y, 0.08f)
    }

    @Test
    fun tiltedEyes_rollAroundZOnly() {
        val placement = GlassesPlacementStrategy().computeTransform(
            faceWithEyes(left = Vec3(-0.2f, 0.2f, 0f), right = Vec3(0.2f, 0.0f, 0f)),
        )!!
        val q = placement.rotation
        assertTrue("Expected Z roll, got $q", abs(q.z) > 0.05f || abs(q.w) < 0.999f)
        assertTrue(abs(q.x) < 0.05f && abs(q.y) < 0.05f)
    }
}
