package com.shergill.tryon.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GlassesPlacementBasisTest {

    private fun faceWithPose(
        left: Vec3,
        right: Vec3,
        forehead: Vec3 = Vec3(0f, 0.35f, 0f),
        chin: Vec3 = Vec3(0f, -0.25f, 0f),
        bridge: Vec3? = null,
        tip: Vec3? = null,
    ): FaceFrame {
        val list = MutableList(FaceLandmarks.LANDMARK_COUNT) { Vec3.ZERO }
        list[FaceLandmarks.LEFT_EYE_INNER] = left
        list[FaceLandmarks.RIGHT_EYE_INNER] = right
        list[FaceLandmarks.LEFT_EYE_OUTER] = left
        list[FaceLandmarks.RIGHT_EYE_OUTER] = right
        list[FaceLandmarks.FOREHEAD_TOP] = forehead
        list[FaceLandmarks.CHIN] = chin
        if (bridge != null) list[FaceLandmarks.NOSE_BRIDGE] = bridge
        if (tip != null) list[FaceLandmarks.NOSE_TIP] = tip
        list[FaceLandmarks.JAW_LEFT] = Vec3(left.x - 0.05f, left.y - 0.15f, left.z)
        list[FaceLandmarks.JAW_RIGHT] = Vec3(right.x + 0.05f, right.y - 0.15f, right.z)
        return FaceFrame(list, Mat4.identity(), faceDetected = true)
    }

    @Test
    fun position_tracksFaceOnBothXAndY() {
        val center = GlassesPlacementStrategy().computeTransform(
            faceWithPose(left = Vec3(-0.2f, 0.1f, 0f), right = Vec3(0.2f, 0.1f, 0f)),
        )!!
        val movedRight = GlassesPlacementStrategy().computeTransform(
            faceWithPose(left = Vec3(0.1f, 0.1f, 0f), right = Vec3(0.5f, 0.1f, 0f)),
        )!!
        val movedUp = GlassesPlacementStrategy().computeTransform(
            faceWithPose(left = Vec3(-0.2f, 0.35f, 0f), right = Vec3(0.2f, 0.35f, 0f)),
        )!!

        assertTrue(
            "Expected X to follow face rightward, dx=${movedRight.position.x - center.position.x}",
            movedRight.position.x - center.position.x > 0.2f,
        )
        assertTrue(
            "Expected Y to follow face upward, dy=${movedUp.position.y - center.position.y}",
            movedUp.position.y - center.position.y > 0.15f,
        )
    }

    @Test
    fun scale_usesXySpan_notInflatedByLandmarkZ() {
        val placement = GlassesPlacementStrategy(baseScaleFactor = 1f).computeTransform(
            faceWithPose(
                left = Vec3(-0.2f, 0.1f, 0.9f),
                right = Vec3(0.2f, 0.1f, -0.9f),
            ),
        )!!
        assertEquals(0.4f, placement.scaleMultiplier, 1e-4f)
    }

    @Test
    fun pitchChange_updatesOrientation() {
        val frontal = GlassesPlacementStrategy().computeTransform(
            faceWithPose(
                left = Vec3(-0.2f, 0.1f, 0f),
                right = Vec3(0.2f, 0.1f, 0f),
                forehead = Vec3(0f, 0.35f, 0f),
                chin = Vec3(0f, -0.25f, 0f),
            ),
        )!!
        val pitched = GlassesPlacementStrategy().computeTransform(
            faceWithPose(
                left = Vec3(-0.2f, 0.12f, 0.05f),
                right = Vec3(0.2f, 0.12f, 0.05f),
                forehead = Vec3(0f, 0.30f, 0.15f),
                chin = Vec3(0f, -0.20f, -0.05f),
            ),
        )!!
        val dq = abs(frontal.rotation.x - pitched.rotation.x) +
            abs(frontal.rotation.y - pitched.rotation.y) +
            abs(frontal.rotation.z - pitched.rotation.z)
        assertTrue("Expected pitch to change orientation, dq=$dq", dq > 0.02f)
    }
}
