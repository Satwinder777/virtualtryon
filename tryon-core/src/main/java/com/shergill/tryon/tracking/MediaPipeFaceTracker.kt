package com.shergill.tryon.tracking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.shergill.tryon.domain.FaceCoordMapper
import com.shergill.tryon.domain.FaceFrame
import com.shergill.tryon.domain.LandmarkOrientation
import com.shergill.tryon.domain.Mat4
import com.shergill.tryon.domain.Vec3
import com.shergill.tryon.domain.toOverlaySpace
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
/**
 * MediaPipe Face Landmarker tracker.
 *
 * Landmark count: **478 is the full mesh** (468 face contour/iris-ready points + 10 iris points).
 * MediaPipe does not provide more than 478 for this task — that is not a "low quality" mesh.
 *
 * Camera notes for try-on:
 * - Uses the **front** camera (required for selfie try-on; back camera would break UX).
 * - Analysis targets 1280×720+ via ResolutionSelector for sharper landmarks.
 * - Bitmap is rotated to upright **before** MediaPipe (same as the official sample).
 *   Relying only on ImageProcessingOptions left landmarks in sideways buffer space →
 *   eye line along Y → glasses roll ≈90° (vertical frames on a frontal face).
 * - Front-camera mirror is applied later in [FaceCoordMapper] (do not flip the bitmap here).
 */
class MediaPipeFaceTracker(
    private val context: Context,
    private val modelAssetPath: String = DEFAULT_MODEL_ASSET,
) : FaceTracker {

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var landmarker: FaceLandmarker? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var frameCallback: ((FaceFrame?) -> Unit)? = null
    private var previewView: PreviewView? = null
    private val running = AtomicBoolean(false)

    private data class FrameSize(val width: Int, val height: Int)

    private val pendingFrameSize = AtomicReference<FrameSize?>(null)
    // Passthrough — any landmark EMA made overlays lag / look pinned off-face.
    private val landmarkSmoother = LandmarkSmoother(alpha = 1f)

    override fun start(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onFrame: (FaceFrame?) -> Unit,
    ) {
        if (running.getAndSet(true)) return
        this.previewView = previewView
        frameCallback = onFrame
        landmarkSmoother.reset()
        ensureLandmarker()

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                val provider = future.get()
                cameraProvider = provider
                provider.unbindAll()

                val rotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0
                val resolutionSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        ),
                    )
                    .build()

                val preview = Preview.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setTargetRotation(rotation)
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setResolutionSelector(resolutionSelector)
                    .setTargetRotation(rotation)
                    .build()
                    .also { it.setAnalyzer(cameraExecutor, ::analyzeFrame) }

                val selector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build()

                try {
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                } catch (e: IllegalArgumentException) {
                    frameCallback?.invoke(null)
                    throw NoFrontCameraException(e.message ?: "No front camera", e)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    override fun stop() {
        running.set(false)
        cameraProvider?.unbindAll()
        cameraProvider = null
        landmarker?.close()
        landmarker = null
        frameCallback = null
        previewView = null
        pendingFrameSize.set(null)
        landmarkSmoother.reset()
    }

    fun release() {
        stop()
        cameraExecutor.shutdown()
    }

    private fun ensureLandmarker() {
        if (landmarker != null) return
        landmarker = createLandmarker(Delegate.GPU)
            ?: createLandmarker(Delegate.CPU)
            ?: error("Failed to create FaceLandmarker")
    }

    private fun createLandmarker(delegate: Delegate): FaceLandmarker? {
        return try {
            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath(modelAssetPath)
                        .setDelegate(delegate)
                        .build(),
                )
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumFaces(1)
                .setMinFaceDetectionConfidence(MIN_DETECTION_CONFIDENCE)
                .setMinFacePresenceConfidence(MIN_PRESENCE_CONFIDENCE)
                .setMinTrackingConfidence(MIN_TRACKING_CONFIDENCE)
                .setOutputFacialTransformationMatrixes(true)
                .setResultListener { result, _ -> onLandmarkerResult(result) }
                .setErrorListener { error ->
                    frameCallback?.invoke(null)
                    error.printStackTrace()
                }
                .build()
            FaceLandmarker.createFromOptions(context, options)
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun analyzeFrame(imageProxy: ImageProxy) {
        if (!running.get()) {
            imageProxy.close()
            return
        }
        val landmarker = landmarker
        if (landmarker == null) {
            imageProxy.close()
            return
        }
        try {
            val metaDegrees = imageProxy.imageInfo.rotationDegrees
            val raw = imageProxy.toBitmap()
            val bufW = raw.width
            val bufH = raw.height
            val preview = previewView
            val degrees = LandmarkOrientation.effectiveBitmapRotationDegrees(
                bufferWidth = bufW,
                bufferHeight = bufH,
                rotationDegrees = metaDegrees,
                previewWidth = preview?.width ?: 0,
                previewHeight = preview?.height ?: 0,
            )
            // Rotate into preview-upright space before inference so landmark (x,y)
            // match portrait PreviewView (eyes separated on X, not Y).
            val upright = rotateToUpright(raw, degrees)
            if (upright !== raw) {
                raw.recycle()
            }
            pendingFrameSize.set(FrameSize(upright.width, upright.height))

            val mpImage = BitmapImageBuilder(upright).build()
            landmarker.detectAsync(mpImage, SystemClock.uptimeMillis())
        } catch (_: Throwable) {
            frameCallback?.invoke(null)
        } finally {
            imageProxy.close()
        }
    }

    private fun onLandmarkerResult(result: FaceLandmarkerResult) {
        val faces = result.faceLandmarks()
        if (faces.isNullOrEmpty()) {
            landmarkSmoother.reset()
            frameCallback?.invoke(null)
            return
        }
        // Safety net: if canthi are still stacked vertically, rotate landmark space.
        val oriented = LandmarkOrientation.ensureEyeLineHorizontal(
            faces[0].map { Vec3(it.x(), it.y(), it.z()) },
        )
        val landmarks = landmarkSmoother.smooth(oriented)

        val matricesOptional = result.facialTransformationMatrixes()
        val matrix = if (matricesOptional.isPresent) {
            val matrices = matricesOptional.get()
            if (matrices.isNotEmpty() && matrices[0].size >= 16) {
                matrices[0].copyOf()
            } else {
                Mat4.identity()
            }
        } else {
            Mat4.identity()
        }

        val preview = previewView
        val viewW = preview?.width ?: 0
        val viewH = preview?.height ?: 0
        // Wait until PreviewView has a real size so aspect matches the Filament surface.
        if (viewW <= 0 || viewH <= 0) {
            return
        }
        val frameSize = pendingFrameSize.get()
        val mapping = FaceCoordMapper.ViewMapping(
            imageWidth = frameSize?.width ?: viewW,
            imageHeight = frameSize?.height ?: viewH,
            viewWidth = viewW,
            viewHeight = viewH,
            mirrorFrontCamera = true,
        )

        val overlayFrame = FaceFrame(
            landmarks = landmarks,
            facialTransformationMatrix = matrix,
            faceDetected = true,
        ).toOverlaySpace(mapping)

        frameCallback?.invoke(overlayFrame)
    }

    companion object {
        const val DEFAULT_MODEL_ASSET = "face_landmarker.task"

        /** Full MediaPipe Face Landmarker mesh with iris refinement. */
        const val FULL_MESH_LANDMARK_COUNT = 478

        private const val ANALYSIS_WIDTH = 1280
        private const val ANALYSIS_HEIGHT = 720

        // Slightly below defaults → fewer dropouts while still rejecting junk.
        private const val MIN_DETECTION_CONFIDENCE = 0.4f
        private const val MIN_PRESENCE_CONFIDENCE = 0.4f
        private const val MIN_TRACKING_CONFIDENCE = 0.4f

        /**
         * Rotates a CameraX analysis bitmap so it matches display upright orientation.
         * [rotationDegrees] is [androidx.camera.core.ImageInfo.getRotationDegrees] (clockwise).
         */
        internal fun rotateToUpright(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
            val degrees = ((rotationDegrees % 360) + 360) % 360
            if (degrees == 0) return bitmap
            val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    }
}

/**
 * Exponential moving average over landmark positions to reduce jitter without
 * adding multi-frame lag that feels "sticky".
 */
class LandmarkSmoother(
    private val alpha: Float = 0.55f,
) {
    private var previous: List<Vec3>? = null

    fun reset() {
        previous = null
    }

    fun smooth(current: List<Vec3>): List<Vec3> {
        val prev = previous
        if (prev == null || prev.size != current.size) {
            previous = current
            return current
        }
        val out = List(current.size) { i ->
            val p = prev[i]
            val c = current[i]
            Vec3(
                x = p.x + alpha * (c.x - p.x),
                y = p.y + alpha * (c.y - p.y),
                z = p.z + alpha * (c.z - p.z),
            )
        }
        previous = out
        return out
    }
}

class NoFrontCameraException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)
