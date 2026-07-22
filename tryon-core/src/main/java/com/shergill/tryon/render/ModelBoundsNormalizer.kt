package com.shergill.tryon.render

import com.google.android.filament.Box
import com.google.android.filament.TransformManager
import com.google.android.filament.gltfio.FilamentAsset
import com.shergill.tryon.domain.AccessoryType
import kotlin.math.max

/**
 * Re-centers and unit-scales a glTF accessory.
 *
 * Glasses: pivot near the **front** (lenses/frames), not the earhook tips — otherwise
 * rotation swings the frame off the face. Scale by frame **width (X)** so eye-span
 * maps 1:1. Keep Y-up / +Z front / temples −Z (no RX bake, no X-mirror).
 */
object ModelBoundsNormalizer {

    data class NormalizeResult(
        val scale: Float,
        val centerX: Float,
        val centerY: Float,
        val centerZ: Float,
        val transform: FloatArray,
        val appliedFaceCameraRx: Boolean = false,
        val mirroredX: Boolean = false,
    )

    const val TARGET_UNIT_SIZE: Float = 1f

    fun compute(
        boundingBox: AxisAlignedBounds,
        targetSize: Float = TARGET_UNIT_SIZE,
        accessoryType: AccessoryType? = null,
    ): NormalizeResult {
        val sizeX = (boundingBox.maxX - boundingBox.minX).coerceAtLeast(1e-6f)
        val sizeY = (boundingBox.maxY - boundingBox.minY).coerceAtLeast(1e-6f)
        val sizeZ = (boundingBox.maxZ - boundingBox.minZ).coerceAtLeast(1e-6f)

        val isGlasses = accessoryType == AccessoryType.GLASSES
        // Glasses: unit = width. Others: unit = max extent.
        val scale = if (isGlasses) {
            targetSize / sizeX
        } else {
            targetSize / max(sizeX, max(sizeY, sizeZ))
        }

        // Glasses: unit width. Pivot at lens/nosepad height (front + slightly above geometric mid-Y
        // so the bridge — not the bottom rim — sits on the nose landmark).
        val centerX = (boundingBox.minX + boundingBox.maxX) * 0.5f
        val centerY = if (isGlasses) {
            boundingBox.minY * 0.35f + boundingBox.maxY * 0.65f
        } else {
            (boundingBox.minY + boundingBox.maxY) * 0.5f
        }
        val centerZ = if (isGlasses) {
            boundingBox.minZ * 0.12f + boundingBox.maxZ * 0.88f
        } else {
            (boundingBox.minZ + boundingBox.maxZ) * 0.5f
        }

        // Column-major: S * T(-center)
        val transform = floatArrayOf(
            scale, 0f, 0f, 0f,
            0f, scale, 0f, 0f,
            0f, 0f, scale, 0f,
            -centerX * scale, -centerY * scale, -centerZ * scale, 1f,
        )

        return NormalizeResult(
            scale = scale,
            centerX = centerX,
            centerY = centerY,
            centerZ = centerZ,
            transform = transform,
            appliedFaceCameraRx = false,
            mirroredX = false,
        )
    }

    fun computeFromAsset(
        asset: FilamentAsset,
        targetSize: Float = TARGET_UNIT_SIZE,
        accessoryType: AccessoryType? = null,
    ): NormalizeResult {
        val box: Box = asset.boundingBox
        val half = box.halfExtent
        val center = box.center
        val bounds = AxisAlignedBounds(
            minX = center[0] - half[0],
            minY = center[1] - half[1],
            minZ = center[2] - half[2],
            maxX = center[0] + half[0],
            maxY = center[1] + half[1],
            maxZ = center[2] + half[2],
        )
        return compute(bounds, targetSize, accessoryType)
    }

    fun bindUnifiedRoot(
        transformManager: TransformManager,
        asset: FilamentAsset,
    ): Int {
        val instance = asset.instance
        val pivotEntity = when {
            instance != null && instance.root != 0 -> instance.root
            else -> asset.root
        }
        val pivotInstance = transformManager.getInstance(pivotEntity)
        if (pivotInstance == 0) return asset.root

        val assetRoot = asset.root
        val entities = instance?.entities ?: asset.entities
        for (entity in entities) {
            if (entity == pivotEntity || entity == assetRoot) continue
            val entityInstance = transformManager.getInstance(entity)
            if (entityInstance == 0) continue
            if (transformManager.getParent(entityInstance) == 0) {
                transformManager.setParent(entityInstance, pivotInstance)
            }
        }
        return pivotEntity
    }
}

data class AxisAlignedBounds(
    val minX: Float,
    val minY: Float,
    val minZ: Float,
    val maxX: Float,
    val maxY: Float,
    val maxZ: Float,
)
