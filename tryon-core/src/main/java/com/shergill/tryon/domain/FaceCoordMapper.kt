package com.shergill.tryon.domain

import kotlin.math.max

/**
 * Maps MediaPipe normalized landmarks (upright analysis image, Y-down)
 * into Filament orthographic NDC that matches a [PreviewView]-sized overlay
 * using the same FILL_CENTER crop + front-camera mirror as the live preview.
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
            get() = imageWidth > 0 && imageHeight > 0 && viewWidth > 0 && viewHeight > 0
    }

    /**
     * Converts one landmark into overlay world space (approx NDC: x/y in [-1,1], Y-up).
     */
    fun toWorld(landmark: Vec3, mapping: ViewMapping): Vec3 {
        if (!mapping.isValid) {
            // Fallback: naive normalized mapping.
            var x = landmark.x * 2f - 1f
            val y = 1f - landmark.y * 2f
            if (mapping.mirrorFrontCamera) x = -x
            return Vec3(x, y, -landmark.z * 2f)
        }

        val scale = max(
            mapping.viewWidth.toFloat() / mapping.imageWidth.toFloat(),
            mapping.viewHeight.toFloat() / mapping.imageHeight.toFloat(),
        )
        val scaledW = mapping.imageWidth * scale
        val scaledH = mapping.imageHeight * scale
        val offsetX = (mapping.viewWidth - scaledW) * 0.5f
        val offsetY = (mapping.viewHeight - scaledH) * 0.5f

        var viewX = landmark.x * mapping.imageWidth * scale + offsetX
        val viewY = landmark.y * mapping.imageHeight * scale + offsetY
        if (mapping.mirrorFrontCamera) {
            viewX = mapping.viewWidth - viewX
        }

        val ndcX = (viewX / mapping.viewWidth) * 2f - 1f
        val ndcY = 1f - (viewY / mapping.viewHeight) * 2f
        val ndcZ = -landmark.z * 2f
        return Vec3(ndcX, ndcY, ndcZ)
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
