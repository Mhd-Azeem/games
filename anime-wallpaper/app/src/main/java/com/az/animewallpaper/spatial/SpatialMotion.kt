package com.az.animewallpaper.spatial

import kotlin.math.*

/** Bounded perspective camera; the resting transform is exactly the input position. */
class SpatialMotion {
    var x = 0f
        private set

    var y = 0f
        private set

    fun reset() {
        x = 0f
        y = 0f
    }

    fun step(horizontal: Float, vertical: Float, dt: Float, smoothing: Float) {
        val a = 1f - exp(-dt.coerceIn(0f, .05f) / (.08f + smoothing.coerceIn(0f, 1f) * .4f))
        x += ((if (horizontal.isFinite()) horizontal else 0f).coerceIn(-1f, 1f) - x) * a
        y += ((if (vertical.isFinite()) vertical else 0f).coerceIn(-1f, 1f) - y) * a
    }

    fun transform(
        u: Float,
        v: Float,
        depth: Float,
        aspect: Float,
        strength: Float,
        out: FloatArray,
    ) {
        val yaw = x * .065f * strength
        val pitch = y * .05f * strength
        val px = u - .5f
        val py = (v - .5f) * aspect
        val rx = px * cos(yaw) + depth * sin(yaw)
        val z = -px * sin(yaw) + depth * cos(yaw)
        val ry = py * cos(pitch) - z * sin(pitch)
        val rz = py * sin(pitch) + z * cos(pitch)
        val perspective = 1f / (1f - (rz - depth) * .2f)
        out[0] = .5f + rx * perspective
        out[1] = .5f + ry * perspective / aspect
    }
}
