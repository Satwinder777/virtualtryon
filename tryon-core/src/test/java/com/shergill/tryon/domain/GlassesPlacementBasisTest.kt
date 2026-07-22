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
        tip: Vec3? = null,
        leftJaw: Vec3? = null,
        rightJaw: Vec3? = null,
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
        if (leftJaw != null) list[FaceLandmarks.JAW_LEFT] = leftJaw
        if (rightJaw != null) list[FaceLandmarks.JAW_RIGHT] = rightJaw
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
        assertEquals(0.4f, placement.scaleMultiplier, 1e-4f)
    }

    @Test
    fun yawChange_doesNotFlipToOppositeHemisphere() {
        val frontal = GlassesPlacementStrategy().computeTransform(
            faceWithPose(
                left = Vec3(-0.2f, 0.1f, 0f),
                right = Vec3(0.2f, 0.1f, 0f),
                forehead = Vec3(0f, 0.35f, 0f),
                chin = Vec3(0f, -0.25f, 0f),
                leftJaw = Vec3(-0.25f, -0.05f, 0f),
                rightJaw = Vec3(0.25f, -0.05f, 0f),
            ),
        )!!
        val yawed = GlassesPlacementStrategy().computeTransform(
            faceWithPose(
                left = Vec3(-0.15f, 0.1f, 0.12f),
                right = Vec3(0.25f, 0.1f, -0.08f),
                forehead = Vec3(0.04f, 0.35f, 0.02f),
                chin = Vec3(0.02f, -0.25f, 0f),
                leftJaw = Vec3(-0.2f, -0.05f, 0.1f),
                rightJaw = Vec3(0.28f, -0.05f, -0.06f),
            ),
        )!!
        // Quaternions should stay in a continuous neighborhood (dot product high).
        val d = abs(
            frontal.rotation.x * yawed.rotation.x +
                frontal.rotation.y * yawed.rotation.y +
                frontal.rotation.z * yawed.rotation.z +
                frontal.rotation.w * yawed.rotation.w,
        )
        assertTrue("Yaw caused discontinuous flip (dot=$d)", d > 0.5f)
        // Anchor stays near face center X.
        assertTrue(abs(yawed.position.x) < 0.35f)
    }

    @Test
    fun noseAnchor_tracksBridgeNotForehead() {
        val bridge = Vec3(0f, 0.05f, 0.02f)
        val tip = Vec3(0f, -0.02f, 0.05f)
        val face = faceWithPose(
            left = Vec3(-0.2f, 0.1f, 0f),
            right = Vec3(0.2f, 0.1f, 0f),
            forehead = Vec3(0f, 0.4f, 0f),
            chin = Vec3(0f, -0.3f, 0f),
            bridge = bridge,
            tip = tip,
        )
        val placement = GlassesPlacementStrategy().computeTransform(face)!!
        // Should be near bridge/tip blend, far below forehead.
        assertTrue(placement.position.y < 0.2f)
        assertTrue(placement.position.y > -0.15f)
        val n = sqrt(
            placement.rotation.x * placement.rotation.x +
                placement.rotation.y * placement.rotation.y +
                placement.rotation.z * placement.rotation.z +
                placement.rotation.w * placement.rotation.w,
        )
        assertEquals(1f, n, 1e-3f)
    }
}
