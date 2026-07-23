package com.shergill.tryon.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LandmarkOrientationTest {

    @Test
    fun ensureEyeLineHorizontal_rotatesVerticalEyes() {
        val list = MutableList(FaceLandmarks.FULL_MESH_WITH_IRIS) { Vec3(0.5f, 0.5f, 0f) }
        // Eyes stacked on X≈0.5 (sideways buffer symptom).
        list[FaceLandmarks.RIGHT_EYE_OUTER] = Vec3(0.48f, 0.25f, 0f)
        list[FaceLandmarks.LEFT_EYE_OUTER] = Vec3(0.52f, 0.75f, 0f)
        val fixed = LandmarkOrientation.ensureEyeLineHorizontal(list)
        val a = fixed[FaceLandmarks.RIGHT_EYE_OUTER]
        val b = fixed[FaceLandmarks.LEFT_EYE_OUTER]
        assertTrue(abs(b.x - a.x) > abs(b.y - a.y))
    }

    @Test
    fun ensureEyeLineHorizontal_leavesHorizontalEyes() {
        val list = MutableList(FaceLandmarks.FULL_MESH_WITH_IRIS) { Vec3(0.5f, 0.5f, 0f) }
        list[FaceLandmarks.RIGHT_EYE_OUTER] = Vec3(0.30f, 0.40f, 0f)
        list[FaceLandmarks.LEFT_EYE_OUTER] = Vec3(0.70f, 0.42f, 0f)
        val fixed = LandmarkOrientation.ensureEyeLineHorizontal(list)
        assertEquals(0.30f, fixed[FaceLandmarks.RIGHT_EYE_OUTER].x, 1e-5f)
        assertEquals(0.70f, fixed[FaceLandmarks.LEFT_EYE_OUTER].x, 1e-5f)
    }

    @Test
    fun effectiveBitmapRotation_forces90_whenPortraitPreviewLandscapeBufferDeg0() {
        assertEquals(
            90,
            LandmarkOrientation.effectiveBitmapRotationDegrees(
                bufferWidth = 1280,
                bufferHeight = 720,
                rotationDegrees = 0,
                previewWidth = 1080,
                previewHeight = 1920,
            ),
        )
    }

    @Test
    fun effectiveBitmapRotation_skipsDoubleRotate_whenBufferAlreadyPortrait() {
        assertEquals(
            0,
            LandmarkOrientation.effectiveBitmapRotationDegrees(
                bufferWidth = 720,
                bufferHeight = 1280,
                rotationDegrees = 90,
                previewWidth = 1080,
                previewHeight = 1920,
            ),
        )
    }

    @Test
    fun effectiveBitmapRotation_keepsMetadata90_forLandscapeBuffer() {
        assertEquals(
            90,
            LandmarkOrientation.effectiveBitmapRotationDegrees(
                bufferWidth = 1280,
                bufferHeight = 720,
                rotationDegrees = 90,
                previewWidth = 1080,
                previewHeight = 1920,
            ),
        )
    }
}
