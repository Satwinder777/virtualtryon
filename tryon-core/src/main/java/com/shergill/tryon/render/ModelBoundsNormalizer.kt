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
 * Temple orientation: do **not** bake RX(±90°) here. Khronos Sunglasses (and similar)
 * already bake ≈RX(90°) on glTF nodes; [bindUnifiedRoot] preserves those matrices.
 * An extra RX(−90°) based on a tall local AABB cancels the node RX → temples hang
 * down the cheeks (the screenshot “dandian neeche” bug). Placement stays on the empty
 * pivot only.
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

    fun compute(
        boundingBox: AxisAlignedBounds,
        targetSize: Float = TARGET_UNIT_SIZE,
        accessoryType: AccessoryType? = null,
    ): NormalizeResult {
        val minX = boundingBox.minX
        val minY = boundingBox.minY
        val minZ = boundingBox.minZ
        val maxX = boundingBox.maxX
        val maxY = boundingBox.maxY
        val maxZ = boundingBox.maxZ

        val sizeX = (maxX - minX).coerceAtLeast(1e-6f)
        val sizeY = (maxY - minY).coerceAtLeast(1e-6f)
        val sizeZ = (maxZ - minZ).coerceAtLeast(1e-6f)

        val isGlasses = accessoryType == AccessoryType.GLASSES

        // Glasses: unit = width. Others: unit = max extent.
        val scale = if (isGlasses) {
            targetSize / sizeX
        } else {
            targetSize / max(sizeX, max(sizeY, sizeZ))
        }

        val centerX = (minX + maxX) * 0.5f
        val centerY = (minY + maxY) * 0.5f
        // Prefer the lens/front of the AABB (maxZ toward camera) as the placement pivot.
        val centerZ = if (isGlasses) {
            minZ * 0.15f + maxZ * 0.85f
        } else {
            (minZ + maxZ) * 0.5f
        }

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
            sizeX = sizeX,
            sizeY = sizeY,
            sizeZ = sizeZ,
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
