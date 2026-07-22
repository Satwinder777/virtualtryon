package com.shergill.tryon.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class GlbRepository(
    private val context: Context,
    private val client: OkHttpClient = defaultClient(),
    private val maxFileBytes: Long = DEFAULT_MAX_BYTES,
) {
    fun downloadAndValidate(url: String): Flow<GlbDownloadState> = flow {
        emit(GlbDownloadState.Idle)

        val trimmed = url.trim()
        if (!isHttpUrl(trimmed)) {
            emit(GlbDownloadState.Error("URL must start with http:// or https://"))
            return@flow
        }

        val cacheFile = cacheFileFor(trimmed)
        if (cacheFile.exists() && cacheFile.length() > 4 && hasGlbMagic(cacheFile)) {
            emit(GlbDownloadState.Success(cacheFile))
            return@flow
        }

        emit(GlbDownloadState.Downloading(0f))

        val request = Request.Builder().url(trimmed).get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(GlbDownloadState.Error("HTTP ${response.code}: ${response.message}"))
                    return@flow
                }
                val body = response.body
                    ?: run {
                        emit(GlbDownloadState.Error("Empty response body"))
                        return@flow
                    }
                val contentLength = body.contentLength()
                if (contentLength > maxFileBytes) {
                    emit(
                        GlbDownloadState.Error(
                            "File too large (${contentLength / (1024 * 1024)}MB). Max is ${maxFileBytes / (1024 * 1024)}MB.",
                        ),
                    )
                    return@flow
                }

                val tmp = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
                tmp.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > maxFileBytes) {
                                tmp.delete()
                                emit(
                                    GlbDownloadState.Error(
                                        "File exceeded max size of ${maxFileBytes / (1024 * 1024)}MB.",
                                    ),
                                )
                                return@flow
                            }
                            out.write(buffer, 0, read)
                            val progress = if (contentLength > 0) {
                                (total.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            emit(GlbDownloadState.Downloading(progress))
                        }
                    }
                }

                if (!hasGlbMagic(tmp)) {
                    tmp.delete()
                    emit(GlbDownloadState.Error("File is not a valid GLB (missing 'glTF' magic header)."))
                    return@flow
                }

                if (cacheFile.exists()) cacheFile.delete()
                if (!tmp.renameTo(cacheFile)) {
                    tmp.copyTo(cacheFile, overwrite = true)
                    tmp.delete()
                }
                emit(GlbDownloadState.Success(cacheFile))
            }
        } catch (e: IOException) {
            emit(GlbDownloadState.Error(e.message ?: "Network error"))
        } catch (e: IllegalArgumentException) {
            emit(GlbDownloadState.Error(e.message ?: "Invalid URL"))
        }
    }.flowOn(Dispatchers.IO)

    fun cacheFileFor(url: String): File {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val hash = sha256(url).take(32)
        return File(dir, "$hash.glb")
    }

    companion object {
        const val DEFAULT_MAX_BYTES: Long = 30L * 1024L * 1024L
        private const val CACHE_DIR = "glb_cache"
        private const val DEFAULT_BUFFER = 8 * 1024
        private val GLB_MAGIC = byteArrayOf('g'.code.toByte(), 'l'.code.toByte(), 'T'.code.toByte(), 'F'.code.toByte())

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

        fun isHttpUrl(url: String): Boolean =
            url.startsWith("http://", ignoreCase = true) ||
                url.startsWith("https://", ignoreCase = true)

        fun hasGlbMagic(file: File): Boolean {
            if (!file.exists() || file.length() < 4) return false
            val header = ByteArray(4)
            file.inputStream().use { stream ->
                val read = stream.read(header)
                if (read < 4) return false
            }
            return header.contentEquals(GLB_MAGIC)
        }

        private fun sha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
