package com.shergill.tryon.domain

/**
 * Maps MediaPipe normalized landmarks (analysis image, Y-down, [0,1]) into Filament
 * orthographic overlay space that matches the on-screen selfie preview.
 *
 * Mapping is **face-relative / landmark-direct** (not a fixed screen corner):
 * - MediaPipe (0.5, 0.5) → overlay origin (face/camera center)
 * - Face moves down in the image → landmark.y ↑ → overlay Y ↓
 * - Front camera is mirrored on X to match PreviewView
 *
 * World space is pixel-isotropic: Y ∈ [-1, 1], X ∈ [-aspect, aspect]
 * (`aspect = viewWidth / viewHeight`), matching Filament ORTHO(-aspect..aspect, -1..1).
 *
 * We intentionally do **not** simulate a secondary FILL_CENTER crop from analysis→view
 * using mismatched buffer sizes — that drift parked accessories in screen corners
 * while the face moved.
 */
object FaceCoordMapper {

    data class ViewMapping(
        val imageWidth: Int,
        val imageHeight: Int,
        val viewWidth: Int,
        val viewHeight: Int,
        val mirrorFrontCamera: Boolean = true,
    ) {
        val isValid: Boolean
            get() = viewWidth > 0 && viewHeight > 0

        /** Width / height — 1 world-unit X equals 1 world-unit Y in pixels. */
        val aspect: Float
            get() = if (viewHeight > 0) viewWidth.toFloat() / viewHeight.toFloat() else 1f
    }

    /**
     * Converts one landmark into overlay world space (Y-up, pixel-isotropic).
     * Output (x, y) tracks this landmark every frame — no cache inside the mapper.
     */
    fun toWorld(landmark: Vec3, mapping: ViewMapping): Vec3 {
        var x = landmark.x
        if (mapping.mirrorFrontCamera) {
            x = 1f - x
        }
        val nx = x * 2f - 1f
        val ny = 1f - landmark.y * 2f
        return Vec3(nx * mapping.aspect, ny, -landmark.z * 2f)
    }

    fun toWorldLandmarks(landmarks: List<Vec3>, mapping: ViewMapping): List<Vec3> =
        landmarks.map { toWorld(it, mapping) }

    fun toWorldMatrix(columnMajor4x4: FloatArray, mirrorFrontCamera: Boolean): FloatArray {
        require(columnMajor4x4.size >= 16)
        val sx = if (mirrorFrontCamera) -1f else 1f
        val sy = -1f
        val sz = 1f
        val m = columnMajor4x4
        return floatArrayOf(
            m[0], sy * sx * m[1], sz * sx * m[2], 0f,
            sx * sy * m[4], m[5], sz * sy * m[6], 0f,
            sx * sz * m[8], sy * sz * m[9], m[10], 0f,
            sx * m[12], sy * m[13], sz * m[14], 1f,
        )
    }
}

fun FaceFrame.toOverlaySpace(mapping: FaceCoordMapper.ViewMapping): FaceFrame =
    FaceFrame(
        landmarks = FaceCoordMapper.toWorldLandmarks(landmarks, mapping),
        facialTransformationMatrix = FaceCoordMapper.toWorldMatrix(
            facialTransformationMatrix,
            mapping.mirrorFrontCamera,
        ),
        faceDetected = faceDetected,
    )
