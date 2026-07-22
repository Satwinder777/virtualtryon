package com.shergill.tryon.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

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
    fun frontalFace_rotationNearIdentity_withTemplesAlongMinusZ() {
        val face = faceWithPose(
            left = Vec3(-0.2f, 0.1f, 0f),
            right = Vec3(0.2f, 0.1f, 0f),
            forehead = Vec3(0f, 0.35f, 0f),
            chin = Vec3(0f, -0.25f, 0f),
            bridge = Vec3(0f, 0.08f, 0.02f),
        )
        val placement = GlassesPlacementStrategy().computeTransform(face)!!
        val q = placement.rotation
        // xAxis = left-right = (-1,0,0) → 180° about Y (or similar), not a random shear.
        assertTrue("Quaternion should be unit, got $q", abs(q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w - 1f) < 0.05f)
    }

    @Test
    fun yawedFace_changesOrientationFromFrontal() {
        val frontal = GlassesPlacementStrategy().computeTransform(
            faceWithPose(
                left = Vec3(-0.2f, 0.1f, 0f),
                right = Vec3(0.2f, 0.1f, 0f),
                forehead = Vec3(0f, 0.35f, 0f),
                chin = Vec3(0f, -0.25f, 0f),
            ),
        )!!
        val yawed = GlassesPlacementStrategy().computeTransform(
            faceWithPose(
                left = Vec3(-0.15f, 0.1f, 0.08f),
                right = Vec3(0.25f, 0.1f, -0.05f),
                forehead = Vec3(0.05f, 0.35f, 0.02f),
                chin = Vec3(0.02f, -0.25f, 0f),
            ),
        )!!
        val dq = abs(frontal.rotation.x - yawed.rotation.x) +
            abs(frontal.rotation.y - yawed.rotation.y) +
            abs(frontal.rotation.z - yawed.rotation.z)
        assertTrue("Expected pose to change with yaw, dq=$dq", dq > 0.05f)
    }
}
