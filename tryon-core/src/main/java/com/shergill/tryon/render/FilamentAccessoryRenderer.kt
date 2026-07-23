package com.shergill.tryon.render

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.utils.Utils
import com.shergill.tryon.domain.AccessoryType
import com.shergill.tryon.domain.CalibrationOffsets
import com.shergill.tryon.domain.Placement
import com.shergill.tryon.domain.Quaternion
import com.shergill.tryon.domain.Vec3
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders a GLB accessory on a transparent [SurfaceView] layered over CameraX PreviewView.
 *
 * All Filament Engine APIs run on the main thread (Filament JobSystem adoption requirement).
 * Face-tracker threads only publish the latest [Placement] via [updateTransform].
 */
class FilamentAccessoryRenderer(
    private val context: Context,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val choreographer = Choreographer.getInstance()

    private var engine: Engine? = null
    private var renderer: Renderer? = null
    private var scene: Scene? = null
    private var view: View? = null
    private var camera: Camera? = null
    private var swapChain: SwapChain? = null
    private var uiHelper: UiHelper? = null
    private var displayHelper: DisplayHelper? = null
    private var assetLoader: AssetLoader? = null
    private var resourceLoader: ResourceLoader? = null
    private var materialProvider: UbershaderProvider? = null

    private var asset: FilamentAsset? = null
    /**
     * Empty identity pivot we create in [ModelBoundsNormalizer.bindUnifiedRoot].
     * Placement is applied here — never on glTF nodes (they keep baked RX matrices).
     */
    private var rootEntity: Int = 0
    /** True when [rootEntity] is our invent pivot (not owned by the FilamentAsset). */
    private var ownsPlacementPivot: Boolean = false
    private var normalizeTransform: FloatArray = ModelBoundsNormalizer.compute(
        AxisAlignedBounds(-0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f),
    ).transform
    private var lightEntity: Int = 0
    private var fillLightEntity: Int = 0
    private var surfaceView: SurfaceView? = null

    private var modelVisible: Boolean = false
    private var reducedQuality: Boolean = false
    private val ready = AtomicBoolean(false)
    private val destroyed = AtomicBoolean(false)

    private data class FrameState(
        val placement: Placement?,
        val calibration: CalibrationOffsets,
    )

    private val pendingFrame = AtomicReference(
        FrameState(placement = null, calibration = CalibrationOffsets.IDENTITY),
    )

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (destroyed.get()) return
            choreographer.postFrameCallback(this)
            renderOnMainThread(frameTimeNanos)
        }
    }

    fun attach(surfaceView: SurfaceView) {
        runOnMain {
            if (ready.get() || destroyed.get()) return@runOnMain
            Utils.init()
            this.surfaceView = surfaceView

            val eng = Engine.create()
            engine = eng
            renderer = eng.createRenderer()
            scene = eng.createScene()
            view = eng.createView()
            camera = eng.createCamera(eng.entityManager.create())
            displayHelper = DisplayHelper(context)

            materialProvider = UbershaderProvider(eng)
            assetLoader = AssetLoader(eng, materialProvider!!, EntityManager.get())
            resourceLoader = ResourceLoader(eng, true)

            val filamentView = view!!
            filamentView.blendMode = View.BlendMode.TRANSLUCENT
            renderer!!.clearOptions = Renderer.ClearOptions().apply {
                clear = true
                clearColor = floatArrayOf(0f, 0f, 0f, 0f)
            }
            filamentView.scene = scene
            filamentView.camera = camera

            scene!!.skybox = Skybox.Builder().color(0.15f, 0.15f, 0.18f, 0f).build(eng)
            // White ambient SH so PBR materials aren't pure black without an IBL cubemap.
            scene!!.indirectLight = IndirectLight.Builder()
                .irradiance(1, floatArrayOf(1.0f, 1.0f, 1.0f))
                .intensity(80_000f)
                .build(eng)

            lightEntity = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(1f, 1f, 1f)
                .intensity(120_000f)
                .direction(0f, 0f, -1f)
                .castShadows(false)
                .build(eng, lightEntity)
            scene!!.addEntity(lightEntity)

            // Fill light from above-front so frames aren't a flat silhouette.
            fillLightEntity = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(1f, 1f, 1f)
                .intensity(40_000f)
                .direction(0.3f, -0.6f, -0.7f)
                .castShadows(false)
                .build(eng, fillLightEntity)
            scene!!.addEntity(fillLightEntity)

            camera!!.lookAt(0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)
            // Aspect-correct ortho (updated on resize) — matches FaceCoordMapper world units.

            val helper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply {
                isOpaque = false
            }
            uiHelper = helper
            helper.renderCallback = object : UiHelper.RendererCallback {
                override fun onNativeWindowChanged(surface: Surface) {
                    val currentEngine = engine ?: return
                    val currentRenderer = renderer ?: return
                    swapChain?.let { currentEngine.destroySwapChain(it) }
                    swapChain = currentEngine.createSwapChain(surface, helper.swapChainFlags.toLong())
                    displayHelper?.attach(currentRenderer, surfaceView.display)
                }

                override fun onDetachedFromSurface() {
                    displayHelper?.detach()
                    val currentEngine = engine ?: return
                    swapChain?.let {
                        currentEngine.destroySwapChain(it)
                        currentEngine.flushAndWait()
                        swapChain = null
                    }
                }

                override fun onResized(width: Int, height: Int) {
                    applyViewport(width, height)
                }
            }
            helper.attachTo(surfaceView)
            ready.set(true)
            choreographer.postFrameCallback(frameCallback)
        }
    }

    fun setReducedQuality(enabled: Boolean) {
        runOnMain {
            reducedQuality = enabled
            val sv = surfaceView ?: return@runOnMain
            if (sv.width > 0 && sv.height > 0) {
                applyViewport(sv.width, sv.height)
            }
        }
    }

    fun loadModel(file: File, accessoryType: AccessoryType? = null) {
        runOnMain {
            if (!ready.get() || destroyed.get()) return@runOnMain
            val eng = engine ?: return@runOnMain
            val loader = assetLoader ?: return@runOnMain
            val resLoader = resourceLoader ?: return@runOnMain
            clearAssetLocked()

            val bytes = file.readBytes()
            val buffer = ByteBuffer.allocateDirect(bytes.size).apply {
                put(bytes)
                rewind()
            }
            val loaded = loader.createAsset(buffer) ?: return@runOnMain
            asset = loaded
            resLoader.loadResources(loaded)

            val normalize = ModelBoundsNormalizer.computeFromAsset(loaded, accessoryType = accessoryType)
            normalizeTransform = normalize.transform
            // Bind BEFORE releaseSourceData so instance hierarchy is intact.
            ownsPlacementPivot = false
            rootEntity = try {
                ModelBoundsNormalizer.bindUnifiedRoot(eng.transformManager, loaded)
            } catch (t: Throwable) {
                0
            }
            if (rootEntity != 0) {
                ownsPlacementPivot = true
            } else {
                // Last resort — may wipe baked node matrices on some GLBs.
                rootEntity = loaded.root
                ownsPlacementPivot = false
            }
            loaded.releaseSourceData()

            // Prefer instance entities (all mesh nodes under the primary instance).
            val entities = loaded.instance?.entities ?: loaded.entities
            scene?.addEntities(entities)
            if (ownsPlacementPivot && rootEntity != 0) {
                scene?.addEntity(rootEntity)
            }
            modelVisible = true
        }
    }

    /**
     * Thread-safe: may be called from MediaPipe / camera analyzer threads.
     * Only stores the latest placement; Filament apply happens on the main-thread frame loop.
     */
    fun updateTransform(placement: Placement?, calibration: CalibrationOffsets) {
        pendingFrame.set(FrameState(placement, calibration))
    }

    fun clear() {
        runOnMain { clearAssetLocked() }
    }

    fun destroy() {
        runOnMain {
            if (destroyed.getAndSet(true)) return@runOnMain
            choreographer.removeFrameCallback(frameCallback)
            clearAssetLocked()

            val eng = engine ?: return@runOnMain
            uiHelper?.detach()
            // Detach before destroying renderer — DisplayHelper callbacks NPE if renderer is null.
            displayHelper?.detach()
            displayHelper = null
            swapChain?.let {
                eng.destroySwapChain(it)
                eng.flushAndWait()
            }
            if (lightEntity != 0) {
                scene?.removeEntity(lightEntity)
                eng.destroyEntity(lightEntity)
                EntityManager.get().destroy(lightEntity)
                lightEntity = 0
            }
            if (fillLightEntity != 0) {
                scene?.removeEntity(fillLightEntity)
                eng.destroyEntity(fillLightEntity)
                EntityManager.get().destroy(fillLightEntity)
                fillLightEntity = 0
            }
            resourceLoader?.destroy()
            assetLoader?.destroy()
            materialProvider?.destroyMaterials()
            materialProvider?.destroy()
            view?.let { eng.destroyView(it) }
            scene?.indirectLight?.let { eng.destroyIndirectLight(it) }
            scene?.skybox?.let { eng.destroySkybox(it) }
            scene?.let { eng.destroyScene(it) }
            camera?.let {
                eng.destroyCameraComponent(it.entity)
                eng.entityManager.destroy(it.entity)
            }
            renderer?.let { eng.destroyRenderer(it) }
            eng.destroy()

            engine = null
            renderer = null
            scene = null
            view = null
            camera = null
            swapChain = null
            uiHelper = null
            assetLoader = null
            resourceLoader = null
            materialProvider = null
            ready.set(false)
        }
    }

    private fun renderOnMainThread(frameTimeNanos: Long) {
        if (!ready.get() || destroyed.get()) return
        val helper = uiHelper ?: return
        if (!helper.isReadyToRender) return
        val eng = engine ?: return
        val rend = renderer ?: return
        val sc = swapChain ?: return
        val filamentView = view ?: return

        applyPendingTransformLocked(eng)

        if (rend.beginFrame(sc, frameTimeNanos)) {
            rend.render(filamentView)
            rend.endFrame()
        }
    }

    private fun applyPendingTransformLocked(eng: Engine) {
        if (rootEntity == 0 || asset == null) return
        val frame = pendingFrame.get()
        val tm = eng.transformManager
        val instance = tm.getInstance(rootEntity)
        if (instance == 0) return

        val loaded = asset!!
        val entities = loaded.instance?.entities ?: loaded.entities
        val placement = frame.placement
        if (placement == null) {
            if (modelVisible) {
                scene?.removeEntities(entities)
                if (ownsPlacementPivot && rootEntity != 0) {
                    scene?.removeEntity(rootEntity)
                }
                modelVisible = false
            }
            return
        }

        if (!modelVisible) {
            scene?.addEntities(entities)
            if (ownsPlacementPivot && rootEntity != 0) {
                scene?.addEntity(rootEntity)
            }
            modelVisible = true
        }
        // Column-major world matrix: T * R * (s * Normalize) on the empty pivot only.
        tm.setTransform(instance, composeTransform(normalizeTransform, placement, frame.calibration))
    }

    private fun clearAssetLocked() {
        val eng = engine ?: return
        val loaded = asset ?: return
        val entities = loaded.instance?.entities ?: loaded.entities
        scene?.removeEntities(entities)
        if (ownsPlacementPivot && rootEntity != 0) {
            scene?.removeEntity(rootEntity)
            val tm = eng.transformManager
            val ti = tm.getInstance(rootEntity)
            if (ti != 0) {
                tm.destroy(ti)
            }
            eng.destroyEntity(rootEntity)
            EntityManager.get().destroy(rootEntity)
        }
        assetLoader?.destroyAsset(loaded)
        asset = null
        rootEntity = 0
        ownsPlacementPivot = false
        modelVisible = false
    }

    private fun applyViewport(width: Int, height: Int) {
        // Always cover the full SurfaceView so landmarks stay registered with the preview.
        // LQ flag is retained for UI; shrinking the viewport used to park the overlay in a corner.
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        view?.viewport = Viewport(0, 0, w, h)
        // Pixel-isotropic world: Y ∈ [-1,1], X ∈ [-aspect, aspect] — same as FaceCoordMapper.
        val aspect = w.toDouble() / h.toDouble()
        camera?.setProjection(
            Camera.Projection.ORTHO,
            -aspect, aspect,
            -1.0, 1.0,
            -10.0, 10.0,
        )
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    companion object {
        /**
         * Column-major [TransformManager.setTransform] matrix:
         * `T(pos) * R * (s * Normalize)`.
         */
        fun composeTransform(
            normalize: FloatArray,
            placement: Placement,
            calibration: CalibrationOffsets,
        ): FloatArray {
            val s = placement.scaleMultiplier * calibration.scale
            val pos = Vec3(
                placement.position.x + calibration.offsetX,
                placement.position.y + calibration.offsetY,
                placement.position.z + calibration.offsetZ,
            )
            val rot = multiplyQuaternions(
                placement.rotation,
                eulerYawPitchRoll(
                    calibration.rotationYawDeg,
                    calibration.rotationPitchDeg,
                    calibration.rotationRollDeg,
                ),
            )
            val scaled = scaleMat4(normalize, s)
            val rotated = multiplyMat4(quaternionToMat4(rot), scaled)
            return translateMat4(rotated, pos)
        }

        private fun scaleMat4(m: FloatArray, s: Float): FloatArray = floatArrayOf(
            m[0] * s, m[1] * s, m[2] * s, m[3],
            m[4] * s, m[5] * s, m[6] * s, m[7],
            m[8] * s, m[9] * s, m[10] * s, m[11],
            m[12] * s, m[13] * s, m[14] * s, m[15],
        )

        private fun translateMat4(m: FloatArray, t: Vec3): FloatArray {
            val out = m.copyOf()
            out[12] += t.x
            out[13] += t.y
            out[14] += t.z
            return out
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

        private fun quaternionToMat4(q: Quaternion): FloatArray {
            val x = q.x
            val y = q.y
            val z = q.z
            val w = q.w
            val xx = x * x
            val yy = y * y
            val zz = z * z
            val xy = x * y
            val xz = x * z
            val yz = y * z
            val wx = w * x
            val wy = w * y
            val wz = w * z
            return floatArrayOf(
                1f - 2f * (yy + zz), 2f * (xy + wz), 2f * (xz - wy), 0f,
                2f * (xy - wz), 1f - 2f * (xx + zz), 2f * (yz + wx), 0f,
                2f * (xz + wy), 2f * (yz - wx), 1f - 2f * (xx + yy), 0f,
                0f, 0f, 0f, 1f,
            )
        }

        private fun eulerYawPitchRoll(yawDeg: Float, pitchDeg: Float, rollDeg: Float): Quaternion {
            val yaw = Math.toRadians(yawDeg.toDouble()).toFloat() * 0.5f
            val pitch = Math.toRadians(pitchDeg.toDouble()).toFloat() * 0.5f
            val roll = Math.toRadians(rollDeg.toDouble()).toFloat() * 0.5f
            val cy = cos(yaw)
            val sy = sin(yaw)
            val cp = cos(pitch)
            val sp = sin(pitch)
            val cr = cos(roll)
            val sr = sin(roll)
            return Quaternion(
                x = sr * cp * cy - cr * sp * sy,
                y = cr * sp * cy + sr * cp * sy,
                z = cr * cp * sy - sr * sp * cy,
                w = cr * cp * cy + sr * sp * sy,
            )
        }

        private fun multiplyQuaternions(a: Quaternion, b: Quaternion): Quaternion = Quaternion(
            x = a.w * b.x + a.x * b.w + a.y * b.z - a.z * b.y,
            y = a.w * b.y - a.x * b.z + a.y * b.w + a.z * b.x,
            z = a.w * b.z + a.x * b.y - a.y * b.x + a.z * b.w,
            w = a.w * b.w - a.x * b.x - a.y * b.y - a.z * b.z,
        )
    }
}
