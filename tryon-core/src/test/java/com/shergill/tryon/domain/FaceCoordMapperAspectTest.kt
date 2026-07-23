package com.shergill.tryon.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class FaceCoordMapperAspectTest {

    @Test
    fun portraitMapping_isPixelIsotropic_forHorizontalEyeSpan() {
        val mapping = FaceCoordMapper.ViewMapping(
            imageWidth = 720,
            imageHeight = 1280,
            viewWidth = 1080,
            viewHeight = 1920,
            mirrorFrontCamera = false,
        )
        val left = FaceCoordMapper.toWorld(Vec3(0.35f, 0.5f, 0f), mapping)
        val right = FaceCoordMapper.toWorld(Vec3(0.65f, 0.5f, 0f), mapping)
        val dx = right.x - left.x
        val dy = right.y - left.y
        assertEquals(0f, dy, 1e-4f)

        val pixelDx = dx / mapping.aspect * (mapping.viewWidth / 2f)
        val worldLen = sqrt(dx * dx + dy * dy)
        val pixelsFromWorldYConvention = worldLen * (mapping.viewHeight / 2f)
        assertEquals(pixelDx, pixelsFromWorldYConvention, 1f)
        assertEquals(true, abs(left.x) < mapping.aspect + 0.01f)
        // Aspect stretch applied (not raw [-1,1] NDC on X).
        assertEquals(true, abs(left.x) != abs(0.35f * 2f - 1f))
    }

    @Test
    fun centerLandmark_mapsNearOrigin() {
        val mapping = FaceCoordMapper.ViewMapping(
            imageWidth = 100,
            imageHeight = 100,
            viewWidth = 200,
            viewHeight = 400,
            mirrorFrontCamera = false,
        )
        val p = FaceCoordMapper.toWorld(Vec3(0.5f, 0.5f, 0f), mapping)
        assertEquals(0f, p.x, 1e-3f)
        assertEquals(0f, p.y, 1e-3f)
    }

    @Test
    fun mirroredFrontCamera_flipsXOnly() {
        val mapping = FaceCoordMapper.ViewMapping(
            imageWidth = 100,
            imageHeight = 100,
            viewWidth = 100,
            viewHeight = 100,
            mirrorFrontCamera = true,
        )
        val p = FaceCoordMapper.toWorld(Vec3(0.25f, 0.5f, 0f), mapping)
        // 1-0.25=0.75 → nx=0.5
        assertEquals(0.5f, p.x, 1e-3f)
        assertEquals(0f, p.y, 1e-3f)
    }
}
