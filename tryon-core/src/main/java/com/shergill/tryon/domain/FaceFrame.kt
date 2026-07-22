package com.shergill.tryon.domain

/**
 * One tracked face frame from the face landmarker.
 *
 * @param landmarks 468 (or more) landmark points in model / image space.
 * @param facialTransformationMatrix Column-major 4x4 facial transformation matrix
 *        from MediaPipe (`outputFacialTransformationMatrixes`).
 * @param faceDetected Whether a face was present in this frame.
 */
data class FaceFrame(
    val landmarks: List<Vec3>,
    val facialTransformationMatrix: FloatArray,
    val faceDetected: Boolean,
) {
    fun landmarkOrNull(index: Int): Vec3? = landmarks.getOrNull(index)

    fun requireLandmark(index: Int): Vec3 =
        landmarkOrNull(index) ?: error("Missing landmark $index")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FaceFrame) return false
        return faceDetected == other.faceDetected &&
            landmarks == other.landmarks &&
            facialTransformationMatrix.contentEquals(other.facialTransformationMatrix)
    }

    override fun hashCode(): Int {
        var result = landmarks.hashCode()
        result = 31 * result + facialTransformationMatrix.contentHashCode()
        result = 31 * result + faceDetected.hashCode()
        return result
    }
}
