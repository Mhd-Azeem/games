package com.az.animewallpaper

import android.opengl.*
import android.os.*
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.az.animewallpaper.rendering.MotionRenderer
import com.az.animewallpaper.sensors.MotionSensors
import com.az.animewallpaper.storage.ProjectStore

class MotionWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = MotionEngine()

    inner class MotionEngine : Engine() {
        private val thread = HandlerThread("AZ Wallpaper GPU").apply { start() }
        private val handler = Handler(thread.looper)
        private val renderer = MotionRenderer(this@MotionWallpaperService)
        private val sensors =
            MotionSensors(this@MotionWallpaperService) { x, y ->
                renderer.inputX = x
                renderer.inputY = y
            }
        private var display = EGL14.EGL_NO_DISPLAY
        private var eglContext = EGL14.EGL_NO_CONTEXT
        private var surface = EGL14.EGL_NO_SURFACE
        @Volatile private var shown = false
        private var ready = false
        private var fps = 30
        private val frame =
            object : Runnable {
                override fun run() {
                    if (!shown || !ready) return
                    try {
                        renderer.onDrawFrame(null)
                        if (!EGL14.eglSwapBuffers(display, surface))
                            throw IllegalStateException("EGL surface lost")
                    } catch (e: Exception) {
                        android.util.Log.e("AZWallpaper", "Render failed", e)
                        ready = false
                    }
                    if (ready) handler.postDelayed(this, if (renderer.moving) 1000L / fps else 150L)
                }
            }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            setTouchEventsEnabled(false)
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            handler.post {
                try {
                    display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
                    check(EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 0))
                    val configs = arrayOfNulls<EGLConfig>(1)
                    val n = IntArray(1)
                    check(
                        EGL14.eglChooseConfig(
                            display,
                            intArrayOf(
                                EGL14.EGL_RENDERABLE_TYPE,
                                EGL14.EGL_OPENGL_ES2_BIT,
                                EGL14.EGL_SURFACE_TYPE,
                                EGL14.EGL_WINDOW_BIT,
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
                            n,
                            0,
                        ) && n[0] > 0
                    )
                    eglContext =
                        EGL14.eglCreateContext(
                            display,
                            configs[0],
                            EGL14.EGL_NO_CONTEXT,
                            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
                            0,
                        )
                    surface =
                        EGL14.eglCreateWindowSurface(
                            display,
                            configs[0],
                            holder,
                            intArrayOf(EGL14.EGL_NONE),
                            0,
                        )
                    check(EGL14.eglMakeCurrent(display, surface, surface, eglContext))
                    val store = ProjectStore(this@MotionWallpaperService)
                    val p = store.active() ?: store.list().firstOrNull() ?: store.starter(0)
                    fps = p.fps
                    renderer.onSurfaceCreated(null, null)
                    renderer.pending = p
                    renderer.onSurfaceChanged(
                        null,
                        holder.surfaceFrame.width(),
                        holder.surfaceFrame.height(),
                    )
                    ready = true
                    if (shown) {
                        handler.removeCallbacks(frame)
                        handler.post(frame)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AZWallpaper", "Initialization failed", e)
                    cleanup()
                }
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            handler.post { if (ready) renderer.onSurfaceChanged(null, width, height) }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            shown = visible
            handler.removeCallbacks(frame)
            if (visible) {
                sensors.start(fps <= 20)
                handler.post {
                    try {
                        ProjectStore(this@MotionWallpaperService).active()?.let {
                            fps = it.fps
                            renderer.pending = it
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AZWallpaper", "Configuration reload failed", e)
                    }
                    handler.removeCallbacks(frame)
                    handler.post(frame)
                }
            } else sensors.stop()
        }

        private fun cleanup() {
            ready = false
            handler.removeCallbacks(frame)
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(
                    display,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT,
                )
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglContext)
                EGL14.eglTerminate(display)
            }
            display = EGL14.EGL_NO_DISPLAY
            surface = EGL14.EGL_NO_SURFACE
            eglContext = EGL14.EGL_NO_CONTEXT
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            handler.post { cleanup() }
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            shown = false
            sensors.stop()
            handler.post {
                cleanup()
                thread.quitSafely()
            }
            super.onDestroy()
        }
    }
}
