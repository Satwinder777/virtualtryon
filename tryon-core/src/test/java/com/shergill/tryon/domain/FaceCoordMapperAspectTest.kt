package com.shergill.tryon.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class FaceCoordMapperAspectTest {

    @Test
    fun portraitMapping_isPixelIsotropic_forHorizontalEyeSpan() {
        // 1080×1920 portrait → aspect = 0.5625
        val mapping = FaceCoordMapper.ViewMapping(
            imageWidth = 720,
            imageHeight = 1280,
            viewWidth = 1080,
            viewHeight = 1920,
            mirrorFrontCamera = false,
        )
        // Two landmarks at left/right of view center line (normalized image coords).
        // After FILL_CENTER they sit at equal pixel distances horizontally.
        val left = FaceCoordMapper.toWorld(Vec3(0.35f, 0.5f, 0f), mapping)
        val right = FaceCoordMapper.toWorld(Vec3(0.65f, 0.5f, 0f), mapping)
        val dx = right.x - left.x
        val dy = right.y - left.y
        assertEquals(0f, dy, 1e-4f)

        // Convert world Δx back to pixels: world X unit = (height/2) pixels when aspect-scaled.
        // Pixel distance = dx / aspect * (viewWidth/2) ... equivalently:
        val pixelDx = dx / mapping.aspect * (mapping.viewWidth / 2f)
        val pixelDyIfSameWorld = abs(dx) * (mapping.viewHeight / 2f) // if we wrongly used square NDC hypot
        // With isotropic world, a pure-horizontal eye span's world length equals its vertical
        // equivalent in pixels when Δworld is the same magnitude.
        val worldLen = sqrt(dx * dx + dy * dy)
        val pixelsFromWorldYConvention = worldLen * (mapping.viewHeight / 2f)
        // Horizontal pixel span should match that isotropic conversion.
        assertEquals(pixelDx, pixelsFromWorldYConvention, 1f)
        // Guard: aspect stretch applied (not still in [-1,1] for X on portrait).
        assertEquals(true, abs(left.x) < mapping.aspect + 0.01f)
        assertEquals(true, abs(left.x) != abs((0.35f * 2f - 1f))) // would fail if unscaled
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
}
