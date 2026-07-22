package com.shergill.tryon.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class GlassesPlacementBasisTest {

    private fun faceWithPose(
        left: Vec3,
        right: Vec3,
        forehead: Vec3,
        chin: Vec3,
        bridge: Vec3? = null,
    ): FaceFrame {
        val list = MutableList(FaceLandmarks.LANDMARK_COUNT) { Vec3.ZERO }
        list[FaceLandmarks.LEFT_EYE_INNER] = left
        list[FaceLandmarks.RIGHT_EYE_INNER] = right
        list[FaceLandmarks.LEFT_EYE_OUTER] = left
        list[FaceLandmarks.RIGHT_EYE_OUTER] = right
        list[FaceLandmarks.FOREHEAD_TOP] = forehead
        list[FaceLandmarks.CHIN] = chin
        if (bridge != null) list[FaceLandmarks.NOSE_BRIDGE] = bridge
        return FaceFrame(list, Mat4.identity(), faceDetected = true)
    }

    @Test
    fun scale_usesXySpan_notInflatedByLandmarkZ() {
        val face = faceWithPose(
            left = Vec3(-0.2f, 0.1f, 0.9f),
            right = Vec3(0.2f, 0.1f, -0.9f),
            forehead = Vec3(0f, 0.35f, 0f),
            chin = Vec3(0f, -0.25f, 0f),
        )
        val placement = GlassesPlacementStrategy(baseScaleFactor = 1f).computeTransform(face)!!
        // XY span = 0.4 even though 3D length is huge.
        assertEquals(0.4f, placement.scaleMultiplier, 1e-4f)
    }

    @Test
    fun frontal_quaternionIsUnit() {
        val placement = GlassesPlacementStrategy().computeTransform(
            faceWithPose(
                left = Vec3(-0.2f, 0.1f, 0f),
                right = Vec3(0.2f, 0.1f, 0f),
                forehead = Vec3(0f, 0.35f, 0f),
                chin = Vec3(0f, -0.25f, 0f),
                bridge = Vec3(0f, 0.08f, 0f),
            ),
        )!!
        val q = placement.rotation
        val n = sqrt(q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w)
        assertEquals(1f, n, 1e-3f)
        assertTrue(abs(placement.position.x) < 0.05f)
    }
}
