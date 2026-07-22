package com.shergill.tryon.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class GlbRepositoryInstrumentedTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: GlbRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Clear cache between tests
        context.cacheDir.resolve("glb_cache").deleteRecursively()
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
        repository = GlbRepository(context, client)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun downloadAndValidate_success_emitsSuccessWithCachedFile() = runBlocking {
        val glbBytes = glbPayload(size = 128)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(glbBytes)),
        )
        val url = server.url("/model.glb").toString()
        val states = repository.downloadAndValidate(url).toList()
        assertTrue(states.any { it is GlbDownloadState.Downloading })
        val success = states.last() as GlbDownloadState.Success
        assertTrue(success.file.exists())
        assertTrue(GlbRepository.hasGlbMagic(success.file))
    }

    @Test
    fun downloadAndValidate_nonGlb_emitsError() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("not a glb file"),
        )
        val url = server.url("/fake.glb").toString()
        val states = repository.downloadAndValidate(url).toList()
        val error = states.last() as GlbDownloadState.Error
        assertTrue(error.message.contains("glTF", ignoreCase = true) || error.message.contains("GLB"))
    }

    @Test
    fun downloadAndValidate_networkFailure_emitsError() = runBlocking {
        server.enqueue(
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START),
        )
        val url = server.url("/timeout.glb").toString()
        val states = repository.downloadAndValidate(url).toList()
        assertTrue(states.last() is GlbDownloadState.Error)
    }

    private fun glbPayload(size: Int): ByteArray {
        val bytes = ByteArray(size.coerceAtLeast(4))
        bytes[0] = 'g'.code.toByte()
        bytes[1] = 'l'.code.toByte()
        bytes[2] = 'T'.code.toByte()
        bytes[3] = 'F'.code.toByte()
        return bytes
    }
}
