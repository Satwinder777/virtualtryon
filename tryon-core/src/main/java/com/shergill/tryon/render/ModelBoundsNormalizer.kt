package com.shergill.tryon.render

import com.google.android.filament.Box
import com.google.android.filament.TransformManager
import com.google.android.filament.gltfio.FilamentAsset
import com.shergill.tryon.domain.AccessoryType
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Re-centers a loaded glTF asset at the origin and normalizes it to a consistent unit size.
 *
 * For glasses that are authored flat on the XZ plane (Khronos SunglassesKhronos — thin Y after
 * node transforms), bakes −90° X so the frames face the camera (XY, Z toward viewer).
 */
object ModelBoundsNormalizer {

    data class NormalizeResult(
        val scale: Float,
        val centerX: Float,
        val centerY: Float,
        val centerZ: Float,
        val transform: FloatArray,
        val appliedFaceCameraRx: Boolean,
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
        val centerAndScale = floatArrayOf(
            scale, 0f, 0f, 0f,
            0f, scale, 0f, 0f,
            0f, 0f, scale, 0f,
            -centerX * scale, -centerY * scale, -centerZ * scale, 1f,
        )

        // Flat on XZ (height Y much smaller than depth Z) → need −90° X to face camera.
        val needsFaceCameraRx = accessoryType == AccessoryType.GLASSES &&
            sizeY < sizeZ * 0.85f

        val transform = if (needsFaceCameraRx) {
            multiplyMat4(rotationXDegrees(-90f), centerAndScale)
        } else {
            centerAndScale
        }

        return NormalizeResult(
            scale = scale,
            centerX = centerX,
            centerY = centerY,
            centerZ = centerZ,
            transform = transform,
            appliedFaceCameraRx = needsFaceCameraRx,
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
     * Khronos SunglassesKhronos (and similar) ship **8 independent scene roots**.
     * Filament parents them under [FilamentAsset.getInstance].root — we transform that
     * entity, and re-parent any orphaned transform nodes so temples/frames never drift apart.
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
            // getParent returns 0 when the entity has no parent.
            if (transformManager.getParent(entityInstance) == 0) {
                transformManager.setParent(entityInstance, pivotInstance)
            }
        }
        return pivotEntity
    }

    /** Column-major rotation around X, degrees. */
    internal fun rotationXDegrees(degrees: Float): FloatArray {
        val r = Math.toRadians(degrees.toDouble()).toFloat()
        val c = cos(r)
        val s = sin(r)
        return floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, c, s, 0f,
            0f, -s, c, 0f,
            0f, 0f, 0f, 1f,
        )
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
