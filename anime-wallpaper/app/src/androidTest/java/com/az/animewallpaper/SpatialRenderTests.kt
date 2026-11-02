package com.az.animewallpaper

import android.graphics.*
import android.net.Uri
import android.opengl.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.az.animewallpaper.models.SpatialSettings
import com.az.animewallpaper.rendering.MotionRenderer
import com.az.animewallpaper.rendering.WallpaperEgl
import com.az.animewallpaper.storage.ProjectStore
import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpatialRenderTests {
    @Test
    fun bothSpatialPathsRenderAndRespondToTilt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = Bitmap.createBitmap(160, 200, Bitmap.Config.ARGB_8888)
        val pixels =
            IntArray(160 * 200) { i ->
                if (i % 160 in 55..105 && i / 160 in 40..160) Color.RED
                else Color.rgb(i % 160 * 255 / 160, i / 160 * 255 / 200, 150)
            }
        source.setPixels(pixels, 0, 160, 0, 0, 160, 200)
        val file = File(context.cacheDir, "spatial-gpu.png")
        file.outputStream().use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }
        source.recycle()
        val store = ProjectStore(context)
        val p = store.importImage(Uri.fromFile(file))
        val display = WallpaperEgl.acquire()
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val count = IntArray(1)
        check(
            EGL14.eglChooseConfig(
                display,
                intArrayOf(
                    EGL14.EGL_RENDERABLE_TYPE,
                    EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE,
                    EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_RED_SIZE,
                    8,
                    EGL14.EGL_GREEN_SIZE,
                    8,
                    EGL14.EGL_BLUE_SIZE,
                    8,
                    EGL14.EGL_NONE,
                ),
                0,
                configs,
                0,
                1,
                count,
                0,
            )
        )
        val eglContext =
            EGL14.eglCreateContext(
                display,
                configs[0],
                EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
                0,
            )
        val surface =
            EGL14.eglCreatePbufferSurface(
                display,
                configs[0],
                intArrayOf(EGL14.EGL_WIDTH, 160, EGL14.EGL_HEIGHT, 200, EGL14.EGL_NONE),
                0,
            )
        try {
            check(EGL14.eglMakeCurrent(display, surface, surface, eglContext))
            val renderer = MotionRenderer(context)
            renderer.onSurfaceCreated(null, null)
            renderer.onSurfaceChanged(null, 160, 200)
            fun read(): ByteArray {
                val buffer = ByteBuffer.allocateDirect(160 * 200 * 4)
                GLES20.glReadPixels(0, 0, 160, 200, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
                val bytes = ByteArray(buffer.capacity())
                buffer.rewind()
                buffer.get(bytes)
                return bytes
            }
            for (kind in listOf("subject", "scene")) {
                p.spatial =
                    SpatialSettings(
                        true,
                        kind,
                        depth =
                            FloatArray(128 * 128) { i ->
                                if (kind == "subject") {
                                    if (i % 128 in 44..84 && i / 128 in 25..103) 1f else 0f
                                } else i / 128 / 127f
                            },
                    )
                renderer.inputX = 0f
                renderer.inputY = 0f
                renderer.reset()
                renderer.pending = p
                renderer.onDrawFrame(null)
                val rest = read()
                renderer.inputX = 1f
                renderer.inputY = .8f
                repeat(300) { renderer.onDrawFrame(null) }
                val moved = read()
                assertEquals("GL error in $kind", GLES20.GL_NO_ERROR, GLES20.glGetError())
                assertTrue(
                    "No visible parallax in $kind",
                    rest.indices.count { rest[it] != moved[it] } > 200,
                )
                renderer.inputX = 0f
                renderer.inputY = 0f
                renderer.reset()
                renderer.onDrawFrame(null)
                assertArrayEquals("Reset did not restore $kind", rest, read())
            }
        } finally {
            EGL14.eglMakeCurrent(
                display,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT,
            )
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, eglContext)
            WallpaperEgl.release(display)
            store.delete(p)
            file.delete()
        }
    }
}
