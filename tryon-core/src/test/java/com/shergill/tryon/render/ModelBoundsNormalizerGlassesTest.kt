package com.shergill.tryon.render

import com.shergill.tryon.domain.AccessoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelBoundsNormalizerGlassesTest {

    @Test
    fun glasses_scalesByWidth_andPivotsNearFront_whenAlreadyDeep() {
        // Khronos-like after node RX: wide X, short Y, deep Z from earhooks.
        val bounds = AxisAlignedBounds(-0.075f, 0.0f, -0.16f, 0.075f, 0.058f, 0.005f)
        val glasses = ModelBoundsNormalizer.compute(bounds, accessoryType = AccessoryType.GLASSES)
        val plain = ModelBoundsNormalizer.compute(bounds, accessoryType = null)

        assertFalse(glasses.mirroredX)
        assertFalse(
            "Must not bake RX when temples already along Z (sizeZ > sizeY)",
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
    fun glasses_bakesRxMinus90_whenAabbTallerThanDeep() {
        // Local / face-plane temples (earhooks along +Y) — needs fold to −Z.
        val bounds = AxisAlignedBounds(-0.075f, 0.0f, -0.02f, 0.075f, 0.16f, 0.04f)
        val glasses = ModelBoundsNormalizer.compute(bounds, accessoryType = AccessoryType.GLASSES)

        assertTrue(glasses.appliedFaceCameraRx)
        // After RX(−90°), depth should dominate height.
        assertTrue(
            "Expected sizeZ > sizeY after temple fold, got Y=${glasses.sizeY} Z=${glasses.sizeZ}",
            glasses.sizeZ > glasses.sizeY,
        )
    }
}
