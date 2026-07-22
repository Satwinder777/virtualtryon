package com.shergill.tryon.render

import com.shergill.tryon.domain.AccessoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelBoundsNormalizerGlassesTest {

    @Test
    fun glasses_mirrorsX_butDoesNotBakeRx() {
        val bounds = AxisAlignedBounds(-0.08f, -0.03f, -0.16f, 0.08f, 0.03f, 0.02f)
        val glasses = ModelBoundsNormalizer.compute(bounds, accessoryType = AccessoryType.GLASSES)
        val plain = ModelBoundsNormalizer.compute(bounds, accessoryType = null)
        assertFalse(glasses.appliedFaceCameraRx)
        assertTrue(glasses.mirroredX)
        assertFalse(plain.mirroredX)
        // Mirror flips X scale sign.
        assertEquals(-plain.transform[0], glasses.transform[0], 1e-5f)
        assertEquals(plain.transform[5], glasses.transform[5], 1e-5f)
        assertEquals(plain.transform[10], glasses.transform[10], 1e-5f)
    }
}
