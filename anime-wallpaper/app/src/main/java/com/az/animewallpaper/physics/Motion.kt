package com.az.animewallpaper.physics

import com.az.animewallpaper.models.*
import kotlin.math.*

class Spring {
    var x = 0f
    var y = 0f
    private var vx = 0f
    private var vy = 0f
    private val historyX = FloatArray(128)
    private val historyY = FloatArray(128)
    private var cursor = 0

    fun reset() {
        x = 0f
        y = 0f
        vx = 0f
        vy = 0f
        historyX.fill(0f)
        historyY.fill(0f)
    }

    fun step(inputX: Float, inputY: Float, dt: Float, p: Physics, global: Physics) {
        val time = dt.coerceIn(0.001f, 0.05f)
        historyX[cursor] = inputX
        historyY[cursor] = inputY
        val delay = ((p.delay + global.delay) / 1000f / time).toInt().coerceIn(0, 127)
        val idx = (cursor - delay + 128) % 128
        cursor = (cursor + 1) % 128
        val k = 12f + p.stiffness * 2f * (global.stiffness / 40f).coerceAtLeast(.05f)
        val force = p.sensitivity * global.sensitivity * k
        val damping = 1f + p.damping * 0.32f * (global.damping / 55f)
        val limit =
            (p.maxDisplacement * (global.maxDisplacement / .045f) * global.intensity * p.intensity)
                .coerceIn(0f, 0.2f)
        repeat(4) {
            val h = time / 4
            val tx =
                if (p.direction == 2 || global.direction == 2) 0f
                else historyX[idx] * force * p.horizontal * global.horizontal * limit
            val ty =
                if (p.direction == 1 || global.direction == 1) 0f
                else historyY[idx] * force * p.vertical * global.vertical * limit
            vx = (vx + (tx - k * x - damping * vx) * h).coerceIn(-1f, 1f)
            vy = (vy + (ty - k * y - damping * vy) * h).coerceIn(-1f, 1f)
            x = (x + vx * h).coerceIn(-limit, limit)
            y = (y + vy * h).coerceIn(-limit, limit)
        }
    }
}

object Influence {
    fun radial(x: Float, y: Float, point: MotionPoint): Float {
        if (!point.enabled) return 0f
        val d = hypot(x - point.x, y - point.y) / point.physics.radius.coerceAtLeast(0.001f)
        val t = (1 - d).coerceIn(0f, 1f)
        return (t * t * (3 - 2 * t)).pow(.5f + point.physics.feather * 2f)
    }
}

class SensorFilter {
    private val gravity = FloatArray(3)
    private val baseline = FloatArray(3)
    private var initialized = false

    fun update(values: FloatArray, rotation: Int): Pair<Float, Float> {
        if (!initialized) {
            for (i in 0..2) gravity[i] = values[i]
            for (i in 0..2) baseline[i] = values[i]
            initialized = true
        }
        for (i in 0..2) gravity[i] = gravity[i] * 0.9f + values[i] * 0.1f
        val x =
            (-(values[0] - gravity[0]) * 0.3f - (gravity[0] - baseline[0]) / 9.81f).coerceIn(
                -3f,
                3f,
            )
        val y =
            ((values[1] - gravity[1]) * 0.3f + (gravity[1] - baseline[1]) / 9.81f).coerceIn(-3f, 3f)
        return when (rotation) {
            1 -> Pair(y, -x)
            2 -> Pair(-x, -y)
            3 -> Pair(-y, x)
            else -> Pair(x, y)
        }
    }
}
