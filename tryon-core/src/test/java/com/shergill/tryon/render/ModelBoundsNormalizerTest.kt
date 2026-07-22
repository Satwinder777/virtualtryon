package com.shergill.tryon.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ModelBoundsNormalizerTest {

    @Test
    fun normalize_centersAtOrigin_andScalesToUnitSize() {
        val bounds = AxisAlignedBounds(
            minX = 10f,
            minY = 20f,
            minZ = 30f,
            maxX = 14f,
            maxY = 22f,
            maxZ = 36f,
        )
        // extents: 4, 2, 6 → max = 6 → scale = 1/6
        val result = ModelBoundsNormalizer.compute(bounds, targetSize = 1f)
        assertEquals(1f / 6f, result.scale, 1e-5f)
        assertEquals(12f, result.centerX, 1e-5f)
        assertEquals(21f, result.centerY, 1e-5f)
        assertEquals(33f, result.centerZ, 1e-5f)

        // Translation column should be -center * scale
        assertEquals(-12f / 6f, result.transform[12], 1e-5f)
        assertEquals(-21f / 6f, result.transform[13], 1e-5f)
        assertEquals(-33f / 6f, result.transform[14], 1e-5f)
        // Uniform scale on diagonal
        assertEquals(result.scale, result.transform[0], 1e-5f)
        assertEquals(result.scale, result.transform[5], 1e-5f)
        assertEquals(result.scale, result.transform[10], 1e-5f)
    }

    @Test
    fun normalize_handlesDegenerateTinyBounds() {
        val bounds = AxisAlignedBounds(0f, 0f, 0f, 0f, 0f, 0f)
        val result = ModelBoundsNormalizer.compute(bounds)
        assertTrue(result.scale > 0f)
        assertTrue(abs(result.scale) < 1e7f)
    }
}
