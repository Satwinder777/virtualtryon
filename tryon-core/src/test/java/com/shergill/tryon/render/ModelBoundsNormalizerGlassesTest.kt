package com.shergill.tryon.render

import com.shergill.tryon.domain.AccessoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelBoundsNormalizerGlassesTest {

    @Test
    fun glasses_scalesByWidth_andPivotsNearFront() {
        // Khronos-like: wide X, short Y, deep Z from earhooks.
        val bounds = AxisAlignedBounds(-0.075f, 0.0f, -0.16f, 0.075f, 0.058f, 0.005f)
        val glasses = ModelBoundsNormalizer.compute(bounds, accessoryType = AccessoryType.GLASSES)
        val plain = ModelBoundsNormalizer.compute(bounds, accessoryType = null)

        assertFalse(glasses.mirroredX)
        assertFalse(glasses.appliedFaceCameraRx)
        // Width-based scale: 1 / 0.15
        assertEquals(1f / 0.15f, glasses.scale, 1e-4f)
        // Front-biased Z pivot (not mid of earhooks).
        val midZ = (bounds.minZ + bounds.maxZ) * 0.5f
        assertTrue(
            "Expected front-biased centerZ=${glasses.centerZ} ahead of midZ=$midZ",
            glasses.centerZ > midZ,
        )
        // Non-glasses still use max-extent scale (dominated by Z here).
        assertTrue(plain.scale < glasses.scale)
    }
}
