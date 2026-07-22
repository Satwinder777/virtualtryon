package com.shergill.tryon.render

import com.shergill.tryon.domain.AccessoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelBoundsNormalizerGlassesTest {

    @Test
    fun glasses_flatOnXz_bakesMinus90X() {
        // Khronos-like: thin Y, larger Z (after node transforms).
        val bounds = AxisAlignedBounds(-0.08f, -0.03f, -0.08f, 0.08f, 0.03f, 0.08f)
        val plain = ModelBoundsNormalizer.compute(bounds, accessoryType = null)
        val glasses = ModelBoundsNormalizer.compute(bounds, accessoryType = AccessoryType.GLASSES)
        assertTrue(glasses.appliedFaceCameraRx)
        assertFalse(plain.appliedFaceCameraRx)
        assertTrue(kotlin.math.abs(plain.transform[5] - glasses.transform[5]) > 1e-5f)
    }

    @Test
    fun glasses_alreadyFacingCamera_skipsExtraRx() {
        // Already XY-facing: large X/Y, thin Z.
        val bounds = AxisAlignedBounds(-0.5f, -0.2f, -0.05f, 0.5f, 0.2f, 0.05f)
        val glasses = ModelBoundsNormalizer.compute(bounds, accessoryType = AccessoryType.GLASSES)
        assertFalse(glasses.appliedFaceCameraRx)
    }

    @Test
    fun rotationX_minus90_mapsYToMinusZ() {
        val r = ModelBoundsNormalizer.rotationXDegrees(-90f)
        assertEquals(0f, r[4], 1e-5f)
        assertEquals(0f, r[5], 1e-5f)
        assertEquals(-1f, r[6], 1e-5f)
    }
}
