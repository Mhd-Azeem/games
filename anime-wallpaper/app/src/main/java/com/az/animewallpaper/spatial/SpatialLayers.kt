package com.az.animewallpaper.spatial

import android.graphics.Bitmap
import com.az.animewallpaper.selection.Masks
import kotlin.math.*

/** Approximate local background repair. Hidden detail cannot be recovered from a flat photo. */
object SpatialLayers {
    fun build(source: Bitmap, mask: FloatArray): Pair<Bitmap, Bitmap> {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val foreground =
            IntArray(pixels.size) { i ->
                val a =
                    (Masks.sample(mask, (i % w) / (w - 1f), (i / w) / (h - 1f)) * 255)
                        .toInt()
                        .coerceIn(0, 255)
                (pixels[i] and 0xffffff) or (a shl 24)
            }
        val lw = min(192, w)
        val lh = max(2, (h.toFloat() / w * lw).toInt())
        val small = Bitmap.createScaledBitmap(source, lw, lh, true)
        val fill = IntArray(lw * lh)
        small.getPixels(fill, 0, lw, 0, 0, lw, lh)
        if (small !== source) small.recycle()
        val hole =
            BooleanArray(fill.size) { i ->
                Masks.sample(mask, (i % lw) / (lw - 1f), (i / lw) / (lh - 1f)) > .03f
            }
        // Dilate by the maximum parallax reveal, so the original silhouette is not duplicated.
        val expanded = hole.copyOf()
        val radius = max(2, (lw * .065f).toInt())
        for (y in 0 until lh) for (x in 0 until lw) if (hole[y * lw + x]) {
            for (yy in max(0, y - radius)..min(lh - 1, y + radius)) for (xx in
                max(0, x - radius)..min(lw - 1, x + radius)) expanded[yy * lw + xx] = true
        }
        val known = BooleanArray(fill.size) { !expanded[it] }
        val queue = IntArray(fill.size)
        var head = 0
        var tail = 0
        for (i in fill.indices) if (known[i]) queue[tail++] = i
        if (tail == 0) {
            for (i in fill.indices) fill[i] = 0xff252832.toInt()
        }
        while (head < tail) {
            val i = queue[head++]
            val x = i % lw
            val y = i / lw
            fun visit(j: Int) {
                if (!known[j]) {
                    known[j] = true
                    fill[j] = fill[i]
                    queue[tail++] = j
                }
            }
            if (x > 0) visit(i - 1)
            if (x < lw - 1) visit(i + 1)
            if (y > 0) visit(i - lw)
            if (y < lh - 1) visit(i + lw)
        }
        var smooth = fill
        repeat(20) {
            val next = smooth.copyOf()
            for (y in 1 until lh - 1) for (x in 1 until lw - 1) {
                val i = y * lw + x
                if (expanded[i]) {
                    var c = 0xff000000.toInt()
                    for (shift in intArrayOf(16, 8, 0)) {
                        val sum =
                            (smooth[i - 1] shr shift and 255) +
                                (smooth[i + 1] shr shift and 255) +
                                (smooth[i - lw] shr shift and 255) +
                                (smooth[i + lw] shr shift and 255)
                        c = c or ((sum / 4) shl shift)
                    }
                    next[i] = c
                }
            }
            smooth = next
        }
        val repaired = Bitmap.createBitmap(smooth, lw, lh, Bitmap.Config.ARGB_8888)
        val large = Bitmap.createScaledBitmap(repaired, w, h, true)
        if (large !== repaired) repaired.recycle()
        val repairPixels = IntArray(pixels.size)
        large.getPixels(repairPixels, 0, w, 0, 0, w, h)
        large.recycle()
        for (i in pixels.indices) {
            val x = i % w
            val y = i / w
            // Expansion excludes subject colors from fill sampling only. Never paint that
            // expanded rectangle onto visible background: it creates a broad blurred halo.
            // Keep every original pixel outside the actual cutout, including fine line detail.
            val coverage = Masks.sample(mask, x / max(1f, w - 1f), y / max(1f, h - 1f))
            if (coverage > .03f) pixels[i] = repairPixels[i]
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888) to
            Bitmap.createBitmap(foreground, w, h, Bitmap.Config.ARGB_8888)
    }
}
