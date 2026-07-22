package com.shergill.tryon.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PlacementStrategyTest {

    private fun identityMatrix(): FloatArray = Mat4.identity()

    private fun mockFace(
        detected: Boolean = true,
        landmarks: Map<Int, Vec3> = emptyMap(),
        matrix: FloatArray = identityMatrix(),
    ): FaceFrame {
        val list = MutableList(FaceLandmarks.LANDMARK_COUNT) { Vec3.ZERO }
        landmarks.forEach { (index, point) -> list[index] = point }
        // Sensible defaults for eye / face measurements if not provided.
        if (FaceLandmarks.LEFT_EYE_INNER !in landmarks) {
            list[FaceLandmarks.LEFT_EYE_INNER] = Vec3(0.05f, 0f, 0f)
        }
        if (FaceLandmarks.RIGHT_EYE_INNER !in landmarks) {
            list[FaceLandmarks.RIGHT_EYE_INNER] = Vec3(-0.05f, 0f, 0f)
        }
        if (FaceLandmarks.FOREHEAD_TOP !in landmarks) {
            list[FaceLandmarks.FOREHEAD_TOP] = Vec3(0f, 0.12f, 0f)
        }
        if (FaceLandmarks.CHIN !in landmarks) {
            list[FaceLandmarks.CHIN] = Vec3(0f, -0.12f, 0f)
        }
        return FaceFrame(
            landmarks = list,
            facialTransformationMatrix = matrix,
            faceDetected = detected,
        )
    }

    private fun assertNear(expected: Float, actual: Float, eps: Float = 1e-4f) {
        assertTrue(
            "Expected $expected but was $actual",
            abs(expected - actual) <= eps,
        )
    }

    private fun assertVecNear(expected: Vec3, actual: Vec3, eps: Float = 1e-4f) {
        assertNear(expected.x, actual.x, eps)
        assertNear(expected.y, actual.y, eps)
        assertNear(expected.z, actual.z, eps)
    }

    @Test
    fun glasses_anchorsNearEyes_andScalesWithInterEye() {
        val left = Vec3(0.05f, 0.02f, 0f)
        val right = Vec3(-0.05f, 0.02f, 0f)
        val bridge = Vec3(0.01f, 0.02f, -0.03f)
        val face = mockFace(
            landmarks = mapOf(
                FaceLandmarks.NOSE_BRIDGE to bridge,
                FaceLandmarks.LEFT_EYE_INNER to left,
                FaceLandmarks.RIGHT_EYE_INNER to right,
                FaceLandmarks.LEFT_EYE_OUTER to left,
                FaceLandmarks.RIGHT_EYE_OUTER to right,
            ),
        )
        val placement = GlassesPlacementStrategy(baseScaleFactor = 2f).computeTransform(face)
        assertNotNull(placement)
        // Nose bridge in screen XY (Z flattened).
        assertNear(0.01f, placement!!.position.x)
        assertNear(0.02f, placement.position.y)
        assertNear(0f, placement.position.z)
        // eyeSpan = 0.10, scale = 0.20
        assertNear(0.20f, placement.scaleMultiplier)
        val q = placement.rotation
        val norm = kotlin.math.sqrt(q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w)
        assertNear(1f, norm, eps = 1e-3f)
    }

    @Test
    fun glasses_returnsNullWhenNoFace() {
        val face = mockFace(detected = false)
        assertNull(GlassesPlacementStrategy().computeTransform(face))
    }

    @Test
    fun cap_offsetsUpwardAlongLocalY_proportionalToInterEye() {
        val forehead = Vec3(0f, 0.1f, 0f)
        val face = mockFace(
            landmarks = mapOf(FaceLandmarks.FOREHEAD_TOP to forehead),
        )
        val strategy = CapPlacementStrategy(upwardOffsetFactor = 0.5f, baseScaleFactor = 2f)
        val placement = strategy.computeTransform(face)
        assertNotNull(placement)
        // eyeSpan = 0.10, offset = 0.05 along +Y → y = 0.15
        assertVecNear(Vec3(0f, 0.15f, 0f), placement!!.position)
        assertNear(0.20f, placement.scaleMultiplier)
    }

    @Test
    fun earrings_offsetsOutwardAndDown_fromJawCorners() {
        val leftJaw = Vec3(0.08f, -0.02f, 0f)
        val rightJaw = Vec3(-0.08f, -0.02f, 0f)
        val face = mockFace(
            landmarks = mapOf(
                FaceLandmarks.JAW_LEFT to leftJaw,
                FaceLandmarks.JAW_RIGHT to rightJaw,
            ),
        )
        val strategy = EarringsPlacementStrategy(
            outwardOffsetFactor = 0.5f,
            downwardOffsetFactor = 0.2f,
            baseScaleFactor = 0.4f,
        )
        val left = strategy.computeLeft(face)!!
        val right = strategy.computeRight(face)!!
        // eyeSpan = 0.10 → outward 0.05, down 0.02
        assertVecNear(Vec3(0.13f, -0.04f, 0f), left.position)
        assertVecNear(Vec3(-0.13f, -0.04f, 0f), right.position)
        assertNear(0.04f, left.scaleMultiplier)
    }

    @Test
    fun locket_offsetsDownAndBack_fromChin_proportionalToFaceHeight() {
        val chin = Vec3(0f, -0.1f, 0f)
        val forehead = Vec3(0f, 0.1f, 0f)
        val face = mockFace(
            landmarks = mapOf(
                FaceLandmarks.CHIN to chin,
                FaceLandmarks.FOREHEAD_TOP to forehead,
            ),
        )
        val strategy = LocketPlacementStrategy(
            downOffsetFactor = 0.5f,
            backOffsetFactor = 0.1f,
            baseScaleFactor = 0.5f,
        )
        val placement = strategy.computeTransform(face)
        assertNotNull(placement)
        // faceHeight = 0.20 → down 0.10, back 0.02 → (0, -0.2, -0.02)
        assertVecNear(Vec3(0f, -0.2f, -0.02f), placement!!.position)
        assertNear(0.10f, placement.scaleMultiplier)
    }

    @Test
    fun allStrategies_returnNullWhenFaceNotDetected() {
        val face = mockFace(detected = false)
        assertNull(CapPlacementStrategy().computeTransform(face))
        assertNull(GlassesPlacementStrategy().computeTransform(face))
        assertNull(EarringsPlacementStrategy().computeTransform(face))
        assertNull(LocketPlacementStrategy().computeTransform(face))
    }
}
