package com.shergill.tryon.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GlassesPlacementBasisTest {

    private fun faceWithEyeCorners(
        rightOuter: Vec3, // 33
        rightInner: Vec3, // 133
        leftInner: Vec3, // 362
        leftOuter: Vec3, // 263
        bridge: Vec3? = null,
    ): FaceFrame {
        val list = MutableList(FaceLandmarks.FULL_MESH_WITH_IRIS) { Vec3.ZERO }
        list[FaceLandmarks.RIGHT_EYE_OUTER] = rightOuter
        list[FaceLandmarks.RIGHT_EYE_INNER] = rightInner
        list[FaceLandmarks.LEFT_EYE_INNER] = leftInner
        list[FaceLandmarks.LEFT_EYE_OUTER] = leftOuter
        list[FaceLandmarks.FOREHEAD_TOP] = Vec3(0f, 0.35f, 0f)
        list[FaceLandmarks.CHIN] = Vec3(0f, -0.25f, 0f)
        if (bridge != null) list[FaceLandmarks.NOSE_BRIDGE] = bridge
        return FaceFrame(list, Mat4.identity(), faceDetected = true)
    }

    @Test
    fun scale_usesOuterCornerDistance_33_to_263() {
        val face = faceWithEyeCorners(
            rightOuter = Vec3(-0.3f, 0.1f, 0f),
            rightInner = Vec3(-0.1f, 0.1f, 0f),
            leftInner = Vec3(0.1f, 0.1f, 0f),
            leftOuter = Vec3(0.3f, 0.1f, 0f),
        )
        val placement = GlassesPlacementStrategy(
            glassesReferenceWidth = 1f,
            framePadding = 1f,
        ).computeTransform(face)
        assertNotNull(placement)
        assertEquals(0.6f, placement!!.scaleMultiplier, 1e-4f)
    }

    @Test
    fun anchor_tracksLiveEyeMidpoint_notScreenCorner() {
        val high = GlassesPlacementStrategy(noseBridgeBlendY = 0f).computeTransform(
            faceWithEyeCorners(
                rightOuter = Vec3(-0.2f, 0.4f, 0f),
                rightInner = Vec3(-0.1f, 0.4f, 0f),
                leftInner = Vec3(0.1f, 0.4f, 0f),
                leftOuter = Vec3(0.2f, 0.4f, 0f),
            ),
        )!!
        val low = GlassesPlacementStrategy(noseBridgeBlendY = 0f).computeTransform(
            faceWithEyeCorners(
                rightOuter = Vec3(-0.2f, -0.3f, 0f),
                rightInner = Vec3(-0.1f, -0.3f, 0f),
                leftInner = Vec3(0.1f, -0.3f, 0f),
                leftOuter = Vec3(0.2f, -0.3f, 0f),
            ),
        )!!
        assertEquals(0.4f, high.position.y, 1e-3f)
        assertEquals(-0.3f, low.position.y, 1e-3f)
        assertTrue(high.position.y - low.position.y > 0.6f)
    }

    @Test
    fun anchor_tracksLiveEyeMidpoint_onX() {
        val left = GlassesPlacementStrategy(noseBridgeBlendY = 0f).computeTransform(
            faceWithEyeCorners(
                rightOuter = Vec3(-0.5f, 0.1f, 0f),
                rightInner = Vec3(-0.4f, 0.1f, 0f),
                leftInner = Vec3(-0.2f, 0.1f, 0f),
                leftOuter = Vec3(-0.1f, 0.1f, 0f),
            ),
        )!!
        val right = GlassesPlacementStrategy(noseBridgeBlendY = 0f).computeTransform(
            faceWithEyeCorners(
                rightOuter = Vec3(0.1f, 0.1f, 0f),
                rightInner = Vec3(0.2f, 0.1f, 0f),
                leftInner = Vec3(0.4f, 0.1f, 0f),
                leftOuter = Vec3(0.5f, 0.1f, 0f),
            ),
        )!!
        assertTrue(right.position.x - left.position.x > 0.5f)
    }

    @Test
    fun rotation_followsEyeLineTilt() {
        val q = GlassesPlacementStrategy().computeTransform(
            faceWithEyeCorners(
                rightOuter = Vec3(-0.25f, 0.20f, 0f),
                rightInner = Vec3(-0.15f, 0.20f, 0f),
                leftInner = Vec3(0.15f, 0.0f, 0f),
                leftOuter = Vec3(0.25f, 0.0f, 0f),
            ),
        )!!.rotation
        // Flat Z → yaw≈0; roll from tilted eye line dominates.
        assertTrue(abs(q.z) > 0.1f)
    }

    @Test
    fun rotation_yawsWhenNoseTipOffsetsFromEyeMid() {
        var captured = GlassesPlacementDebug(
            landmark33 = Vec3.ZERO,
            landmark133 = Vec3.ZERO,
            landmark362 = Vec3.ZERO,
            landmark263 = Vec3.ZERO,
            landmark168 = null,
            anchor = Vec3.ZERO,
            scale = 0f,
            rollDeg = 0f,
        )
        val list = MutableList(FaceLandmarks.FULL_MESH_WITH_IRIS) { Vec3.ZERO }
        list[FaceLandmarks.RIGHT_EYE_OUTER] = Vec3(-0.3f, 0.1f, 0f)
        list[FaceLandmarks.RIGHT_EYE_INNER] = Vec3(-0.1f, 0.1f, 0f)
        list[FaceLandmarks.LEFT_EYE_INNER] = Vec3(0.1f, 0.1f, 0f)
        list[FaceLandmarks.LEFT_EYE_OUTER] = Vec3(0.3f, 0.1f, 0f)
        list[FaceLandmarks.FOREHEAD_TOP] = Vec3(0f, 0.35f, 0f)
        list[FaceLandmarks.CHIN] = Vec3(0f, -0.25f, 0f)
        // Nose tip shifted toward +X → positive yaw.
        list[FaceLandmarks.NOSE_TIP] = Vec3(0.18f, 0.0f, 0f)
        GlassesPlacementStrategy(onDebug = { captured = it })
            .computeTransform(FaceFrame(list, Mat4.identity(), faceDetected = true))
        assertTrue(
            "Expected yaw from nose tip offset, got ${captured.yawDeg}",
            captured.yawDeg > 5f,
        )
        assertTrue(abs(captured.yawDeg) <= 58f + 1e-3f)
    }

    @Test
    fun rotation_dampsEyeLineRollWhenYawedSideways() {
        // Profile-like: large nose offset (yaw) + tilted eye line that would otherwise
        // produce ~45° false roll and tip temples onto the face.
        var captured = GlassesPlacementDebug(
            landmark33 = Vec3.ZERO,
            landmark133 = Vec3.ZERO,
            landmark362 = Vec3.ZERO,
            landmark263 = Vec3.ZERO,
            landmark168 = null,
            anchor = Vec3.ZERO,
            scale = 0f,
            rollDeg = 0f,
        )
        val list = MutableList(FaceLandmarks.FULL_MESH_WITH_IRIS) { Vec3.ZERO }
        list[FaceLandmarks.RIGHT_EYE_OUTER] = Vec3(-0.25f, 0.20f, 0f)
        list[FaceLandmarks.RIGHT_EYE_INNER] = Vec3(-0.15f, 0.20f, 0f)
        list[FaceLandmarks.LEFT_EYE_INNER] = Vec3(0.15f, 0.0f, 0f)
        list[FaceLandmarks.LEFT_EYE_OUTER] = Vec3(0.25f, 0.0f, 0f)
        list[FaceLandmarks.FOREHEAD_TOP] = Vec3(0f, 0.35f, 0f)
        list[FaceLandmarks.CHIN] = Vec3(0f, -0.25f, 0f)
        list[FaceLandmarks.NOSE_TIP] = Vec3(0.35f, 0.05f, 0f)
        GlassesPlacementStrategy(onDebug = { captured = it })
            .computeTransform(FaceFrame(list, Mat4.identity(), faceDetected = true))
        assertTrue("Expected substantial yaw, got ${captured.yawDeg}", abs(captured.yawDeg) > 20f)
        assertTrue(
            "Side-face roll must be damped (was tipping temples onto face), got ${captured.rollDeg}",
            abs(captured.rollDeg) < 15f,
        )
    }

    @Test
    fun rotation_ignoresBiasedEyeDepthForYaw() {
        var capturedYaw = 0f
        // Large opposite Z on eyes (the bug that pinned yaw≈−34°) — must NOT drive yaw.
        val face = faceWithEyeCorners(
            rightOuter = Vec3(-0.3f, 0.1f, 0.25f),
            rightInner = Vec3(-0.1f, 0.1f, 0.25f),
            leftInner = Vec3(0.1f, 0.1f, -0.25f),
            leftOuter = Vec3(0.3f, 0.1f, -0.25f),
            bridge = Vec3(0f, 0.08f, 0f),
        )
        GlassesPlacementStrategy(onDebug = { capturedYaw = it.yawDeg }).computeTransform(face)
        assertTrue(
            "Yaw must not follow MediaPipe eye-Z bias, got $capturedYaw",
            abs(capturedYaw) < 3f,
        )
    }

    @Test
    fun rotation_pitchesWhenForeheadChinDepthDiffers() {
        // Default pitchGain is 0 (temple stability); opt-in for this cue.
        var capturedPitch = 0f
        val list = MutableList(FaceLandmarks.FULL_MESH_WITH_IRIS) { Vec3.ZERO }
        list[FaceLandmarks.RIGHT_EYE_OUTER] = Vec3(-0.3f, 0.1f, 0f)
        list[FaceLandmarks.RIGHT_EYE_INNER] = Vec3(-0.1f, 0.1f, 0f)
        list[FaceLandmarks.LEFT_EYE_INNER] = Vec3(0.1f, 0.1f, 0f)
        list[FaceLandmarks.LEFT_EYE_OUTER] = Vec3(0.3f, 0.1f, 0f)
        list[FaceLandmarks.FOREHEAD_TOP] = Vec3(0f, 0.35f, 0.20f)
        list[FaceLandmarks.CHIN] = Vec3(0f, -0.25f, -0.10f)
        val face = FaceFrame(list, Mat4.identity(), faceDetected = true)
        GlassesPlacementStrategy(
            pitchGain = 0.35f,
            onDebug = { capturedPitch = it.pitchDeg },
        ).computeTransform(face)
        assertTrue(
            "Expected some pitch from forehead/chin depth, got $capturedPitch",
            abs(capturedPitch) > 1f,
        )
        assertTrue(abs(capturedPitch) <= 18f + 1e-3f)
    }

    @Test
    fun defaultScale_isLargerThanRawEyeSpan() {
        val face = faceWithEyeCorners(
            rightOuter = Vec3(-0.3f, 0.1f, 0f),
            rightInner = Vec3(-0.1f, 0.1f, 0f),
            leftInner = Vec3(0.1f, 0.1f, 0f),
            leftOuter = Vec3(0.3f, 0.1f, 0f),
        )
        val placement = GlassesPlacementStrategy().computeTransform(face)!!
        // eye span = 0.6; default padding 1.7 → scale 1.02
        assertEquals(0.6f * 1.7f, placement.scaleMultiplier, 1e-3f)
    }

    @Test
    fun faceCoordMapper_faceDown_movesOverlayYDown() {
        val mapping = FaceCoordMapper.ViewMapping(
            imageWidth = 720,
            imageHeight = 1280,
            viewWidth = 1080,
            viewHeight = 1920,
            mirrorFrontCamera = true,
        )
        val high = FaceCoordMapper.toWorld(Vec3(0.5f, 0.3f, 0f), mapping)
        val low = FaceCoordMapper.toWorld(Vec3(0.5f, 0.7f, 0f), mapping)
        assertTrue(high.y > low.y)
    }
}
