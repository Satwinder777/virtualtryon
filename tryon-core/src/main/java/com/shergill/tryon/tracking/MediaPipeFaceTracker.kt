package com.shergill.tryon.tracking

import android.content.Context
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
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.shergill.tryon.domain.FaceCoordMapper
import com.shergill.tryon.domain.FaceFrame
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
 * - Rotation is applied via [ImageProcessingOptions] (no expensive bitmap re-encode).
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
    // High alpha = stick to the live face; low alpha made accessories trail / float.
    private val landmarkSmoother = LandmarkSmoother(alpha = 0.82f)

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
            val degrees = imageProxy.imageInfo.rotationDegrees
            // Landmark space is post-rotation (upright).
            val uprightW = if (degrees == 90 || degrees == 270) imageProxy.height else imageProxy.width
            val uprightH = if (degrees == 90 || degrees == 270) imageProxy.width else imageProxy.height
            pendingFrameSize.set(FrameSize(uprightW, uprightH))

            val bitmap = imageProxy.toBitmap()
            val mpImage = BitmapImageBuilder(bitmap).build()
            val imageOptions = ImageProcessingOptions.builder()
                .setRotationDegrees(degrees)
                .build()
            landmarker.detectAsync(mpImage, imageOptions, SystemClock.uptimeMillis())
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
        val rawLandmarks = faces[0].map { Vec3(it.x(), it.y(), it.z()) }
        val smoothed = landmarkSmoother.smooth(rawLandmarks)

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

        val frameSize = pendingFrameSize.get()
        val preview = previewView
        val mapping = FaceCoordMapper.ViewMapping(
            imageWidth = frameSize?.width ?: 0,
            imageHeight = frameSize?.height ?: 0,
            viewWidth = preview?.width ?: 0,
            viewHeight = preview?.height ?: 0,
            mirrorFrontCamera = true,
        )

        val overlayFrame = FaceFrame(
            landmarks = smoothed,
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
