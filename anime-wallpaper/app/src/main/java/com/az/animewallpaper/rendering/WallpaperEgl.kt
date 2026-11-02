package com.az.animewallpaper.rendering

import android.opengl.EGL14
import android.opengl.EGLDisplay

/** Native picker and installed wallpaper can own engines at the same time. */
object WallpaperEgl {
    private var clients = 0
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY

    @Synchronized
    fun acquire(): EGLDisplay {
        if (clients == 0) {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 0))
        }
        clients++
        return display
    }

    @Synchronized
    fun release(owned: EGLDisplay) {
        if (owned != display || clients == 0) return
        clients--
        if (clients == 0) {
            EGL14.eglTerminate(display)
            display = EGL14.EGL_NO_DISPLAY
        }
    }
}
