package com.shergill.tryon.render

import com.shergill.tryon.domain.AccessoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelBoundsNormalizerGlassesTest {

    @Test
    fun glasses_scalesByWidth_andPivotsNearFront() {
        // Khronos-like: wide X, short Y, deep Z from earhooks (node RX already applied).
        val bounds = AxisAlignedBounds(-0.075f, 0.0f, -0.16f, 0.075f, 0.058f, 0.005f)
        val glasses = ModelBoundsNormalizer.compute(bounds, accessoryType = AccessoryType.GLASSES)
        val plain = ModelBoundsNormalizer.compute(bounds, accessoryType = null)

        assertFalse(glasses.mirroredX)
        assertFalse(
            "Must never bake RX — glTF node matrices already orient temples",
            glasses.appliedFaceCameraRx,
        )
        assertEquals(1f / 0.15f, glasses.scale, 1e-4f)
        val midZ = (bounds.minZ + bounds.maxZ) * 0.5f
        assertTrue(
            "Expected front-biased centerZ=${glasses.centerZ} ahead of midZ=$midZ",
            glasses.centerZ > midZ,
        )
        assertTrue(plain.scale < glasses.scale)
    }

    @Test
    fun glasses_doesNotBakeRx_evenWhenAabbLooksTall() {
        // Local mesh AABB can look tall (temples along +Y) while node RX already folds
        // them to −Z. Baking another RX(−90°) cancels nodes → temples hang down cheeks.
        val bounds = AxisAlignedBounds(-0.075f, 0.0f, -0.02f, 0.075f, 0.16f, 0.04f)
        val glasses = ModelBoundsNormalizer.compute(bounds, accessoryType = AccessoryType.GLASSES)

        assertFalse(glasses.appliedFaceCameraRx)
        assertEquals(bounds.minY, glasses.centerY - glasses.sizeY * 0.5f, 1e-4f)
    }
}
