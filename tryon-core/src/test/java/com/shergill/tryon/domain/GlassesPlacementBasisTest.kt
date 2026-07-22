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
        if (bridge != null) {
            list[FaceLandmarks.NOSE_BRIDGE] = bridge
        }
        return FaceFrame(list, Mat4.identity(), faceDetected = true)
    }

    @Test
    fun placement_ignoresLandmarkZ_andUsesRollOnly() {
        val face = faceWithEyes(
            left = Vec3(-0.2f, 0.1f, 0.8f),
            right = Vec3(0.2f, 0.1f, -0.8f),
            bridge = Vec3(0f, 0.05f, 0.3f),
        )
        val placement = GlassesPlacementStrategy().computeTransform(face)!!
        val q = placement.rotation
        // Horizontal eyes → near-identity roll (z≈0).
        assertTrue("Expected near-zero roll, got $q", abs(q.x) < 0.05f && abs(q.y) < 0.05f && abs(q.z) < 0.05f)
        assertTrue(abs(q.w) > 0.99f)
        assertEquals(0f, placement.position.z, 1e-5f)
        assertEquals(0f, placement.position.x, 1e-5f)
        assertEquals(0.05f, placement.position.y, 1e-5f)
    }
}
