package com.shergill.tryon.render

import com.google.android.filament.Box
import com.google.android.filament.EntityManager
import com.google.android.filament.TransformManager
import com.google.android.filament.gltfio.FilamentAsset
import com.shergill.tryon.domain.AccessoryType
import kotlin.math.max

/**
 * Re-centers and unit-scales a glTF accessory.
 *
 * Glasses: pivot near the **front** (lenses/frames), not the earhook tips — otherwise
 * rotation swings the frame off the face. Scale by frame **width (X)** so eye-span
 * maps 1:1.
 *
 * Temple orientation: after glTF node transforms, temples should lie along **−Z** (toward
 * the ears). If the bind-pose AABB is taller than it is deep (`sizeY > sizeZ`), node
 * matrices were effectively skipped / local meshes still point along **+Y**. Bake
 * **RX(−90°)** so +Y → −Z. Never bake when `sizeZ > sizeY` (already correct) — that was
 * the old “temples above head / vertical sticks” regression.
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
        val sizeX: Float = 0f,
        val sizeY: Float = 0f,
        val sizeZ: Float = 0f,
    )

    const val TARGET_UNIT_SIZE: Float = 1f

    /** Column-major RX(−90°): (x,y,z) → (x, z, −y). Maps local +Y temples to −Z. */
    private val RX_MINUS_90 = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 0f, -1f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f,
    )

    fun compute(
        boundingBox: AxisAlignedBounds,
        targetSize: Float = TARGET_UNIT_SIZE,
        accessoryType: AccessoryType? = null,
    ): NormalizeResult {
        var minX = boundingBox.minX
        var minY = boundingBox.minY
        var minZ = boundingBox.minZ
        var maxX = boundingBox.maxX
        var maxY = boundingBox.maxY
        var maxZ = boundingBox.maxZ

        var sizeX = (maxX - minX).coerceAtLeast(1e-6f)
        var sizeY = (maxY - minY).coerceAtLeast(1e-6f)
        var sizeZ = (maxZ - minZ).coerceAtLeast(1e-6f)

        val isGlasses = accessoryType == AccessoryType.GLASSES
        // Local / face-plane temples (tall AABB) → fold back to −Z.
        val applyTempleRx = isGlasses && sizeY > sizeZ * 1.05f
        if (applyTempleRx) {
            val corners = listOf(
                floatArrayOf(minX, minY, minZ),
                floatArrayOf(minX, minY, maxZ),
                floatArrayOf(minX, maxY, minZ),
                floatArrayOf(minX, maxY, maxZ),
                floatArrayOf(maxX, minY, minZ),
                floatArrayOf(maxX, minY, maxZ),
                floatArrayOf(maxX, maxY, minZ),
                floatArrayOf(maxX, maxY, maxZ),
            )
            var nMinX = Float.POSITIVE_INFINITY
            var nMinY = Float.POSITIVE_INFINITY
            var nMinZ = Float.POSITIVE_INFINITY
            var nMaxX = Float.NEGATIVE_INFINITY
            var nMaxY = Float.NEGATIVE_INFINITY
            var nMaxZ = Float.NEGATIVE_INFINITY
            for (c in corners) {
                // (x,y,z) → (x, z, -y)
                val x = c[0]
                val y = c[2]
                val z = -c[1]
                nMinX = minOf(nMinX, x)
                nMinY = minOf(nMinY, y)
                nMinZ = minOf(nMinZ, z)
                nMaxX = maxOf(nMaxX, x)
                nMaxY = maxOf(nMaxY, y)
                nMaxZ = maxOf(nMaxZ, z)
            }
            minX = nMinX
            minY = nMinY
            minZ = nMinZ
            maxX = nMaxX
            maxY = nMaxY
            maxZ = nMaxZ
            sizeX = (maxX - minX).coerceAtLeast(1e-6f)
            sizeY = (maxY - minY).coerceAtLeast(1e-6f)
            sizeZ = (maxZ - minZ).coerceAtLeast(1e-6f)
        }

        // Glasses: unit = width. Others: unit = max extent.
        val scale = if (isGlasses) {
            targetSize / sizeX
        } else {
            targetSize / max(sizeX, max(sizeY, sizeZ))
        }

        val centerX = (minX + maxX) * 0.5f
        val centerY = (minY + maxY) * 0.5f
        val centerZ = if (isGlasses) {
            minZ * 0.15f + maxZ * 0.85f
        } else {
            (minZ + maxZ) * 0.5f
        }

        // Column-major: S * T(-center) * [R] — R first when folding temples.
        val translateScale = floatArrayOf(
            scale, 0f, 0f, 0f,
            0f, scale, 0f, 0f,
            0f, 0f, scale, 0f,
            -centerX * scale, -centerY * scale, -centerZ * scale, 1f,
        )
        val transform = if (applyTempleRx) {
            multiplyMat4(translateScale, RX_MINUS_90)
        } else {
            translateScale
        }

        return NormalizeResult(
            scale = scale,
            centerX = centerX,
            centerY = centerY,
            centerZ = centerZ,
            transform = transform,
            appliedFaceCameraRx = applyTempleRx,
            mirroredX = false,
            sizeX = sizeX,
            sizeY = sizeY,
            sizeZ = sizeZ,
        )
    }

    private fun multiplyMat4(a: FloatArray, b: FloatArray): FloatArray {
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
     * Creates a **fresh identity pivot** and parents every orphan glTF root under it.
     *
     * Critical for Khronos Sunglasses (8 scene roots, each with a baked ≈RX(90°) matrix):
     * never call [TransformManager.setTransform] on a glTF node. That overwrites the baked
     * orientation → temples stick vertical, scale/position blow up, frames hang near the chin.
     * Placement/normalize matrices are applied only on the returned empty pivot.
     *
     * Must never parent an ancestor under a descendant (Filament stack overflow / SIGSEGV).
     *
     * @return pivot entity (not owned by the asset — caller must destroy it), or 0 on failure.
     */
    fun bindUnifiedRoot(
        transformManager: TransformManager,
        asset: FilamentAsset,
    ): Int {
        val filamentInstance = asset.instance
        val entities = filamentInstance?.entities ?: asset.entities
        val assetRoot = asset.root
        val instanceRoot = filamentInstance?.root?.takeIf { it != 0 }

        fun isOrphanRoot(entity: Int): Boolean {
            if (entity == 0) return false
            val ti = transformManager.getInstance(entity)
            return ti != 0 && transformManager.getParent(ti) == 0
        }

        fun isAncestorOf(ancestor: Int, descendant: Int): Boolean {
            var current = descendant
            var guard = 0
            while (current != 0 && guard++ < 256) {
                if (current == ancestor) return true
                val ti = transformManager.getInstance(current)
                if (ti == 0) return false
                current = transformManager.getParent(ti)
            }
            return false
        }

        val orphanRoots = LinkedHashSet<Int>()
        for (entity in entities) {
            if (isOrphanRoot(entity)) orphanRoots.add(entity)
        }
        if (isOrphanRoot(assetRoot)) orphanRoots.add(assetRoot)
        if (instanceRoot != null && isOrphanRoot(instanceRoot)) orphanRoots.add(instanceRoot)

        // Always invent an empty pivot — never reuse Earhook/Temple/Frames as the setTransform target.
        val pivotEntity = EntityManager.get().create()
        transformManager.create(pivotEntity)
        val pivotInstance = transformManager.getInstance(pivotEntity)
        if (pivotInstance == 0) {
            EntityManager.get().destroy(pivotEntity)
            return 0
        }

        val toReparent = LinkedHashSet<Int>()
        // Prefer reparenting the existing asset hierarchy as one unit.
        if (assetRoot != 0 && isOrphanRoot(assetRoot)) {
            toReparent.add(assetRoot)
        }
        toReparent.addAll(orphanRoots)

        for (entity in toReparent) {
            if (entity == pivotEntity) continue
            val entityInstance = transformManager.getInstance(entity)
            if (entityInstance == 0) continue
            if (isAncestorOf(entity, pivotEntity)) continue
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
