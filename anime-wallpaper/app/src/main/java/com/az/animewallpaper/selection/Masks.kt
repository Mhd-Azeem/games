package com.az.animewallpaper.selection

import com.az.animewallpaper.models.*
import kotlin.math.*

object Masks {
    fun sample(mask: FloatArray, x: Float, y: Float): Float {
        if (x !in 0f..1f || y !in 0f..1f) return 0f
        val fx = x * (MASK_SIZE - 1)
        val fy = y * (MASK_SIZE - 1)
        val ix = fx.toInt()
        val iy = fy.toInt()
        val jx = min(ix + 1, MASK_SIZE - 1)
        val jy = min(iy + 1, MASK_SIZE - 1)
        val a = fx - ix
        val b = fy - iy
        return (mask[iy * MASK_SIZE + ix] * (1 - a) + mask[iy * MASK_SIZE + jx] * a) * (1 - b) +
            (mask[jy * MASK_SIZE + ix] * (1 - a) + mask[jy * MASK_SIZE + jx] * a) * b
    }

    fun brush(
        mask: FloatArray,
        x: Float,
        y: Float,
        radius: Float,
        hardness: Float,
        opacity: Float,
        erase: Boolean,
    ) {
        val r = radius.coerceAtLeast(1f / MASK_SIZE)
        val left = max(0, ((x - r) * MASK_SIZE).toInt())
        val right = min(MASK_SIZE - 1, ((x + r) * MASK_SIZE).toInt())
        val top = max(0, ((y - r) * MASK_SIZE).toInt())
        val bottom = min(MASK_SIZE - 1, ((y + r) * MASK_SIZE).toInt())
        for (j in top..bottom) for (i in left..right) {
            val d = hypot(i.toFloat() / (MASK_SIZE - 1) - x, j.toFloat() / (MASK_SIZE - 1) - y) / r
            val weight =
                if (d <= hardness) opacity
                else ((1 - d) / (1 - hardness).coerceAtLeast(0.001f)).coerceIn(0f, 1f) * opacity
            val k = j * MASK_SIZE + i
            mask[k] = if (erase) mask[k] * (1 - weight) else mask[k] + (1 - mask[k]) * weight
        }
    }

    fun stroke(
        mask: FloatArray,
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
        r: Float,
        h: Float,
        o: Float,
        erase: Boolean,
    ) {
        val steps = max(1, ceil(hypot(bx - ax, by - ay) / (r / 4).coerceAtLeast(0.002f)).toInt())
        for (s in 0..steps) {
            val t = s.toFloat() / steps
            brush(mask, ax + (bx - ax) * t, ay + (by - ay) * t, r, h, o, erase)
        }
    }

    fun polygon(mask: FloatArray, vertices: List<Pair<Float, Float>>, opacity: Float) {
        if (vertices.size < 3) return
        for (y in 0 until MASK_SIZE) for (x in 0 until MASK_SIZE) {
            val px = x.toFloat() / (MASK_SIZE - 1)
            val py = y.toFloat() / (MASK_SIZE - 1)
            var inside = false
            var j = vertices.lastIndex
            for (i in vertices.indices) {
                val a = vertices[i]
                val b = vertices[j]
                if (
                    (a.second > py) != (b.second > py) &&
                        px < (b.first - a.first) * (py - a.second) / (b.second - a.second) + a.first
                )
                    inside = !inside
                j = i
            }
            if (inside) mask[y * MASK_SIZE + x] = max(mask[y * MASK_SIZE + x], opacity)
        }
    }

    fun shape(
        mask: FloatArray,
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
        ellipse: Boolean,
        opacity: Float,
    ) {
        val cx = (ax + bx) / 2
        val cy = (ay + by) / 2
        val rx = abs(bx - ax) / 2
        val ry = abs(by - ay) / 2
        if (rx < 0.001f || ry < 0.001f) return
        for (y in 0 until MASK_SIZE) for (x in 0 until MASK_SIZE) {
            val dx = abs(x.toFloat() / (MASK_SIZE - 1) - cx) / rx
            val dy = abs(y.toFloat() / (MASK_SIZE - 1) - cy) / ry
            if (if (ellipse) dx * dx + dy * dy <= 1 else dx <= 1 && dy <= 1)
                mask[y * MASK_SIZE + x] = max(mask[y * MASK_SIZE + x], opacity)
        }
    }

    fun feather(mask: FloatArray, passes: Int): FloatArray {
        var src = mask.copyOf()
        var dst = FloatArray(src.size)
        repeat(passes.coerceIn(0, 12)) {
            for (y in 0 until MASK_SIZE) for (x in 0 until MASK_SIZE) {
                var total = 0f
                var n = 0
                for (j in max(0, y - 1)..min(MASK_SIZE - 1, y + 1)) for (i in
                    max(0, x - 1)..min(MASK_SIZE - 1, x + 1)) {
                    total += src[j * MASK_SIZE + i]
                    n++
                }
                dst[y * MASK_SIZE + x] = total / n
            }
            val t = src
            src = dst
            dst = t
        }
        return src
    }

    fun transform(mask: FloatArray, dx: Float, dy: Float, scale: Float, angle: Float): FloatArray {
        val out = FloatArray(mask.size)
        val c = cos(angle)
        val s = sin(angle)
        for (y in 0 until MASK_SIZE) for (x in 0 until MASK_SIZE) {
            val px = x.toFloat() / (MASK_SIZE - 1) - 0.5f - dx
            val py = y.toFloat() / (MASK_SIZE - 1) - 0.5f - dy
            out[y * MASK_SIZE + x] =
                sample(mask, (px * c + py * s) / scale + 0.5f, (-px * s + py * c) / scale + 0.5f)
        }
        return out
    }

    fun wand(
        mask: FloatArray,
        pixels: IntArray,
        width: Int,
        height: Int,
        x: Float,
        y: Float,
        tolerance: Int,
    ) {
        require(width * height == pixels.size)
        val sx = (x * (width - 1)).toInt().coerceIn(0, width - 1)
        val sy = (y * (height - 1)).toInt().coerceIn(0, height - 1)
        val target = pixels[sy * width + sx]
        val seen = BooleanArray(pixels.size)
        val queue = IntArray(pixels.size)
        var head = 0
        var tail = 1
        queue[0] = sy * width + sx
        seen[queue[0]] = true
        while (head < tail) {
            val k = queue[head++]
            val color = pixels[k]
            val delta =
                maxOf(
                    abs((color shr 16 and 255) - (target shr 16 and 255)),
                    abs((color shr 8 and 255) - (target shr 8 and 255)),
                    abs((color and 255) - (target and 255)),
                )
            if (delta > tolerance) continue
            val px = k % width
            val py = k / width
            mask[
                (py * (MASK_SIZE - 1) / max(1, height - 1)) * MASK_SIZE +
                    px * (MASK_SIZE - 1) / max(1, width - 1)] = 1f
            fun visit(n: Int) {
                if (!seen[n]) {
                    seen[n] = true
                    queue[tail++] = n
                }
            }
            if (px > 0) visit(k - 1)
            if (px + 1 < width) visit(k + 1)
            if (py > 0) visit(k - width)
            if (py + 1 < height) visit(k + width)
        }
    }
}
