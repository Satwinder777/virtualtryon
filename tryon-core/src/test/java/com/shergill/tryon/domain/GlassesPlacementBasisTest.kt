package com.shergill.tryon.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun autoFit_scalesFromOuterEyeSpan() {
        val placement = GlassesPlacementStrategy(baseScaleFactor = 1.08f).computeTransform(
            faceWithEyes(left = Vec3(-0.2f, 0.1f, 0f), right = Vec3(0.2f, 0.1f, 0f)),
        )
        assertNotNull(placement)
        assertEquals(0.4f * 1.08f, placement!!.scaleMultiplier, 1e-4f)
    }

    @Test
    fun autoFit_tracksFaceOnX() {
        val center = GlassesPlacementStrategy().computeTransform(
            faceWithEyes(left = Vec3(-0.2f, 0.1f, 0f), right = Vec3(0.2f, 0.1f, 0f)),
        )!!
        val right = GlassesPlacementStrategy().computeTransform(
            faceWithEyes(left = Vec3(0.1f, 0.1f, 0f), right = Vec3(0.5f, 0.1f, 0f)),
        )!!
        assertTrue(right.position.x - center.position.x > 0.25f)
    }

    @Test
    fun autoFit_tracksFaceOnY() {
        val high = GlassesPlacementStrategy().computeTransform(
            faceWithEyes(left = Vec3(-0.2f, 0.3f, 0f), right = Vec3(0.2f, 0.3f, 0f)),
        )!!
        val low = GlassesPlacementStrategy().computeTransform(
            faceWithEyes(left = Vec3(-0.2f, -0.2f, 0f), right = Vec3(0.2f, -0.2f, 0f)),
        )!!
        assertTrue(high.position.y - low.position.y > 0.35f)
    }

    @Test
    fun placementSmoother_blendsTowardNext() {
        val smoother = PlacementSmoother(posAlpha = 0.5f, scaleAlpha = 0.5f, rotAlpha = 0.5f)
        val a = Placement(Vec3(0f, 0f, 0f), Quaternion.IDENTITY, 1f)
        val b = Placement(Vec3(2f, 0f, 0f), Quaternion.IDENTITY, 1f)
        assertEquals(0f, smoother.smooth(a)!!.position.x, 1e-4f)
        val mid = smoother.smooth(b)!!
        assertEquals(1f, mid.position.x, 1e-3f)
    }

    @Test
    fun noisyEyeDepth_doesNotTipFramesVertical() {
        val q = GlassesPlacementStrategy().computeTransform(
            faceWithEyes(left = Vec3(-0.2f, 0.1f, 0.8f), right = Vec3(0.2f, 0.1f, -0.8f)),
        )!!.rotation
        // Local +X (temple axis) must stay roughly screen-horizontal.
        val alongFrame = q.rotate(Vec3(1f, 0f, 0f))
        assertTrue(abs(alongFrame.y) < 0.35f)
        assertTrue(abs(alongFrame.x) > 0.7f)
    }
}
