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
        // Joint color/spatial upsampling refines the low-resolution segmentation only
        // near its boundary. The photo itself is never filtered or blurred.
        val coverage = FloatArray(pixels.size) { i ->
            val x=i%w; val y=i/w
            val u=x/max(1f,w-1f); val v=y/max(1f,h-1f)
            val initial=Masks.sample(mask,u,v)
            if(initial < .001f || initial > .999f) initial else {
                val mx=(u*127).toInt(); val my=(v*127).toInt(); val color=pixels[i]
                var sum=0f;var weight=0f
                for(yy in max(0,my-2)..min(127,my+2)) for(xx in max(0,mx-2)..min(127,mx+2)) {
                    val sx=(xx*(w-1)/127).coerceIn(0,w-1);val sy=(yy*(h-1)/127).coerceIn(0,h-1)
                    val other=pixels[sy*w+sx]
                    val dr=(color shr 16 and 255)-(other shr 16 and 255)
                    val dg=(color shr 8 and 255)-(other shr 8 and 255)
                    val db=(color and 255)-(other and 255)
                    val distance=(xx-u*127)*(xx-u*127)+(yy-v*127)*(yy-v*127)
                    val a=1f/(1f+(dr*dr+dg*dg+db*db)/600f)/(1f+distance)
                    sum+=mask[yy*128+xx]*a;weight+=a
                }
                val t=((sum/max(.00001f,weight)-.18f)/.64f).coerceIn(0f,1f)
                t*t*(3-2*t)
            }
        }
        val foreground=IntArray(pixels.size) { i ->
            (pixels[i] and 0xffffff) or ((coverage[i]*255).toInt().coerceIn(0,255) shl 24)
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
        val radius = max(2, (lw * .02f).toInt())
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
            // Foreground refinement can contract a mask. Removing only refined coverage
            // leaves the original hair/shoulder fringe behind and produces a second outline.
            // Remove the coarse silhouette plus a narrow 2% guard, not the old wide halo.
            val cleanup = expanded[min(lh - 1, y * lh / h) * lw + min(lw - 1, x * lw / w)]
            if (cleanup || coverage[i] > .03f) pixels[i] = repairPixels[i]
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888) to
            Bitmap.createBitmap(foreground, w, h, Bitmap.Config.ARGB_8888)
    }
}
