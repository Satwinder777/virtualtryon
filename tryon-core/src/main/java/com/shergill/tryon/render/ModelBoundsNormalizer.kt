package com.shergill.tryon.render

import com.google.android.filament.Box
import com.google.android.filament.TransformManager
import com.google.android.filament.gltfio.FilamentAsset
import com.shergill.tryon.domain.AccessoryType
import kotlin.math.max

/**
 * Re-centers a loaded glTF asset at the origin and normalizes it to a consistent unit size.
 *
 * Glasses keep glTF Y-up / front +Z / temples −Z (no RX bake — that tipped Khronos temples up).
 * A −X mirror is applied so wearer's-left (+X on Khronos) matches the mirrored selfie preview.
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
        val sizeX = boundingBox.maxX - boundingBox.minX
        val sizeY = boundingBox.maxY - boundingBox.minY
        val sizeZ = boundingBox.maxZ - boundingBox.minZ
        val maxExtent = max(sizeX, max(sizeY, sizeZ)).coerceAtLeast(1e-6f)
        val scale = targetSize / maxExtent
        val centerX = (boundingBox.minX + boundingBox.maxX) * 0.5f
        val centerY = (boundingBox.minY + boundingBox.maxY) * 0.5f
        val centerZ = (boundingBox.minZ + boundingBox.maxZ) * 0.5f

        // Column-major: S * T(-center)
        var transform = floatArrayOf(
            scale, 0f, 0f, 0f,
            0f, scale, 0f, 0f,
            0f, 0f, scale, 0f,
            -centerX * scale, -centerY * scale, -centerZ * scale, 1f,
        )

        val mirrorX = accessoryType == AccessoryType.GLASSES
        if (mirrorX) {
            // diag(-1,1,1) so wearer's-left (+X) lands on screen-left after selfie mirror.
            transform = multiplyMat4(
                floatArrayOf(
                    -1f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f,
                    0f, 0f, 1f, 0f,
                    0f, 0f, 0f, 1f,
                ),
                transform,
            )
        }

        return NormalizeResult(
            scale = scale,
            centerX = centerX,
            centerY = centerY,
            centerZ = centerZ,
            transform = transform,
            appliedFaceCameraRx = false,
            mirroredX = mirrorX,
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

    /**
     * Khronos SunglassesKhronos ships **8 independent scene roots**.
     * Parent orphans under the Filament instance root so temples/frames move together.
     */
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

    internal fun multiplyMat4(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(16)
        for (col in 0 until 4) {
            for (row in 0 until 4) {
                out[col * 4 + row] =
                    a[0 * 4 + row] * b[col * 4 + 0] +
                        a[1 * 4 + row] * b[col * 4 + 1] +
                        a[2 * 4 + row] * b[col * 4 + 2] +
                        a[3 * 4 + row] * b[col * 4 + 3]
            }
        }
        return out
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
