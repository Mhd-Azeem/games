package com.az.animewallpaper.spatial

import android.content.Context
import android.graphics.Bitmap
import com.az.animewallpaper.models.*
import com.az.animewallpaper.selection.Masks
import com.az.animewallpaper.storage.ProjectStore
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import org.tensorflow.lite.Interpreter

/** On-device Pascal VOC segmentation. No network, accounts, or telemetry SDK. */
class SpatialAnalyzer(private val context: Context) {
    fun analyze(path: String): SpatialSettings {
        val source = ProjectStore(context).decode(path, 1024)
        try {
            val scaled = Bitmap.createScaledBitmap(source, 257, 257, true)
            val pixels = IntArray(257 * 257)
            scaled.getPixels(pixels, 0, 257, 0, 0, 257, 257)
            if (scaled !== source) scaled.recycle()
            val input = ByteBuffer.allocateDirect(pixels.size * 12).order(ByteOrder.nativeOrder())
            pixels.forEach { c ->
                for (shift in intArrayOf(16, 8, 0)) input.putFloat(
                    ((c shr shift and 255) - 127.5f) / 127.5f
                )
            }
            input.rewind()
            val output =
                ByteBuffer.allocateDirect(pixels.size * 21 * 4).order(ByteOrder.nativeOrder())
            val bytes = context.assets.open("spatial_subject.tflite").use { it.readBytes() }
            val model =
                ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {
                    put(bytes)
                    rewind()
                }
            Interpreter(model, Interpreter.Options().setNumThreads(2)).use { it.run(input, output) }
            output.rewind()
            val scores = output.asFloatBuffer()
            val labels = IntArray(pixels.size)
            val counts = IntArray(21)
            for (i in pixels.indices) {
                var best = 0
                var value = -Float.MAX_VALUE
                for (c in 0..20) {
                    val score = scores[i * 21 + c]
                    if (score > value) {
                        value = score
                        best = c
                    }
                }
                labels[i] = best
                counts[best]++
            }
            val chosen = if (counts[15] > pixels.size * .025f) 15 else (1..20).maxBy { counts[it] }
            val fraction = counts[chosen].toFloat() / pixels.size
            if (fraction in 0.025f..0.88f) {
                val mask =
                    FloatArray(MASK_SIZE * MASK_SIZE) { i ->
                        val x = i % MASK_SIZE * 257 / MASK_SIZE
                        val y = i / MASK_SIZE * 257 / MASK_SIZE
                        if (labels[y * 257 + x] == chosen) 1f else 0f
                    }
                return SpatialSettings(true, "subject", depth = Masks.feather(mask, 1))
            }
            // A flat image has no hidden geometry: this is explicitly labeled a scene estimate.
            val depth =
                FloatArray(MASK_SIZE * MASK_SIZE) { i ->
                    val x = i % MASK_SIZE / (MASK_SIZE - 1f)
                    val y = i / MASK_SIZE / (MASK_SIZE - 1f)
                    val c = pixels[(y * 256).toInt() * 257 + (x * 256).toInt()]
                    val contrast = abs((c shr 16 and 255) - (c and 255)) / 255f
                    (.12f + .7f * y * y + .12f * contrast).coerceIn(0f, 1f)
                }
            return SpatialSettings(true, "scene", depth = Masks.feather(depth, 3))
        } finally {
            source.recycle()
        }
    }
}
