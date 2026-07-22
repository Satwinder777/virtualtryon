package com.shergill.tryon.domain

import kotlin.math.sqrt

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: Vec3): Vec3 = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3): Vec3 = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float): Vec3 = Vec3(x * scalar, y * scalar, z * scalar)

    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun normalized(): Vec3 {
        val len = length()
        return if (len < 1e-6f) ZERO else Vec3(x / len, y / len, z / len)
    }

    fun cross(other: Vec3): Vec3 = Vec3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x,
    )

    fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        val UP = Vec3(0f, 1f, 0f)
        val RIGHT = Vec3(1f, 0f, 0f)
        val FORWARD = Vec3(0f, 0f, -1f)
    }
}

/**
 * Unit quaternion (x, y, z, w). Identity = (0, 0, 0, 1).
 */
data class Quaternion(
    val x: Float,
    val y: Float,
    val z: Float,
    val w: Float,
) {
    companion object {
        val IDENTITY = Quaternion(0f, 0f, 0f, 1f)

        /**
         * Builds a quaternion from a column-major 4x4 rotation matrix (first 3x3).
         * [matrix] must contain at least 16 floats in column-major order.
         */
        fun fromRotationMatrix(matrix: FloatArray): Quaternion {
            require(matrix.size >= 16) { "Expected 4x4 matrix" }
            // Column-major: m[col * 4 + row]
            val m00 = matrix[0]
            val m10 = matrix[1]
            val m20 = matrix[2]
            val m01 = matrix[4]
            val m11 = matrix[5]
            val m21 = matrix[6]
            val m02 = matrix[8]
            val m12 = matrix[9]
            val m22 = matrix[10]

            val trace = m00 + m11 + m22
            return if (trace > 0f) {
                val s = sqrt(trace + 1f) * 2f
                Quaternion(
                    x = (m21 - m12) / s,
                    y = (m02 - m20) / s,
                    z = (m10 - m01) / s,
                    w = 0.25f * s,
                )
            } else if (m00 > m11 && m00 > m22) {
                val s = sqrt(1f + m00 - m11 - m22) * 2f
                Quaternion(
                    x = 0.25f * s,
                    y = (m01 + m10) / s,
                    z = (m02 + m20) / s,
                    w = (m21 - m12) / s,
                )
            } else if (m11 > m22) {
                val s = sqrt(1f + m11 - m00 - m22) * 2f
                Quaternion(
                    x = (m01 + m10) / s,
                    y = 0.25f * s,
                    z = (m12 + m21) / s,
                    w = (m02 - m20) / s,
                )
            } else {
                val s = sqrt(1f + m22 - m00 - m11) * 2f
                Quaternion(
                    x = (m02 + m20) / s,
                    y = (m12 + m21) / s,
                    z = 0.25f * s,
                    w = (m10 - m01) / s,
                )
            }
        }
    }
}

/**
 * Column-major 4x4 matrix helpers (pure Kotlin).
 */
object Mat4 {
    fun identity(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f,
    )

    /** Transforms a point by the upper-left 3x3 + translation of a column-major 4x4. */
    fun transformPoint(matrix: FloatArray, point: Vec3): Vec3 {
        val x = matrix[0] * point.x + matrix[4] * point.y + matrix[8] * point.z + matrix[12]
        val y = matrix[1] * point.x + matrix[5] * point.y + matrix[9] * point.z + matrix[13]
        val z = matrix[2] * point.x + matrix[6] * point.y + matrix[10] * point.z + matrix[14]
        return Vec3(x, y, z)
    }

    /** Transforms a direction (ignores translation). */
    fun transformDirection(matrix: FloatArray, dir: Vec3): Vec3 {
        val x = matrix[0] * dir.x + matrix[4] * dir.y + matrix[8] * dir.z
        val y = matrix[1] * dir.x + matrix[5] * dir.y + matrix[9] * dir.z
        val z = matrix[2] * dir.x + matrix[6] * dir.y + matrix[10] * dir.z
        return Vec3(x, y, z)
    }
}
