package com.shergill.tryon.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GlbRepositoryTest {

    @Test
    fun isHttpUrl_acceptsHttpAndHttps_only() {
        assertTrue(GlbRepository.isHttpUrl("https://example.com/a.glb"))
        assertTrue(GlbRepository.isHttpUrl("http://example.com/a.glb"))
        assertFalse(GlbRepository.isHttpUrl("ftp://example.com/a.glb"))
        assertFalse(GlbRepository.isHttpUrl("file:///tmp/a.glb"))
        assertFalse(GlbRepository.isHttpUrl("not-a-url"))
    }

    @Test
    fun hasGlbMagic_detectsHeader() {
        val tmp = File.createTempFile("test", ".glb")
        try {
            tmp.writeBytes(byteArrayOf('g'.code.toByte(), 'l'.code.toByte(), 'T'.code.toByte(), 'F'.code.toByte(), 0, 0))
            assertTrue(GlbRepository.hasGlbMagic(tmp))
            tmp.writeBytes("PNG!".toByteArray())
            assertFalse(GlbRepository.hasGlbMagic(tmp))
        } finally {
            tmp.delete()
        }
    }
}
