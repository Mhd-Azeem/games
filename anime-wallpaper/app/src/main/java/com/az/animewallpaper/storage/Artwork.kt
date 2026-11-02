package com.az.animewallpaper.storage

import android.graphics.*
import java.util.Random
import kotlin.math.*

/** Original vector artwork, generated locally. No network or third-party assets. */
object Artwork {
    fun make(kind: Int): Bitmap {
        val b = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(3)
        val rnd = Random(430L + kind)
        val palettes =
            arrayOf(
                intArrayOf(0xff12182e.toInt(), 0xff695e91.toInt()),
                intArrayOf(0xff0c2829.toInt(), 0xff59876f.toInt()),
                intArrayOf(0xff12151c.toInt(), 0xff63768b.toInt()),
                intArrayOf(0xff231a3c.toInt(), 0xffab7596.toInt()),
                intArrayOf(0xff060f21.toInt(), 0xff294f7c.toInt()),
                intArrayOf(0xff111214.toInt(), 0xff45484c.toInt()),
            )
        p.shader = LinearGradient(0f, 0f, 720f, 1280f, palettes[kind], null, Shader.TileMode.CLAMP)
        c.drawPaint(p)
        p.shader = null
        if (kind == 0 || kind == 3 || kind == 4) {
            p.color = 0xfff6ebd6.toInt()
            c.drawCircle(535f, 230f, 78f, p)
            repeat(110) {
                p.color = Color.argb(80 + rnd.nextInt(160), 230, 237, 255)
                c.drawCircle(
                    rnd.nextFloat() * 720,
                    rnd.nextFloat() * 760,
                    1 + rnd.nextFloat() * 2,
                    p,
                )
            }
        }
        if (kind == 0 || kind == 3) {
            repeat(5) { layer ->
                p.color = Color.rgb(30 + layer * 8, 29 + layer * 8, 53 + layer * 9)
                val path = Path()
                path.moveTo(0f, 1280f)
                path.lineTo(0f, 650f + layer * 95)
                for (x in 0..8) path.lineTo(x * 100f, 580f + layer * 95 + rnd.nextFloat() * 180)
                path.lineTo(720f, 1280f)
                path.close()
                c.drawPath(path, p)
            }
        }
        if (kind == 0) {
            p.color = 0xff171929.toInt()
            val hair = Path()
            hair.moveTo(330f, 320f)
            hair.cubicTo(160f, 310f, 190f, 690f, 110f, 900f)
            hair.cubicTo(310f, 800f, 470f, 820f, 580f, 910f)
            hair.cubicTo(440f, 610f, 540f, 350f, 390f, 320f)
            hair.close()
            c.drawPath(hair, p)
            p.color = 0xffdfc3bb.toInt()
            c.drawOval(292f, 330f, 414f, 480f, p)
            p.color = 0xff1d2135.toInt()
            val bangs = Path()
            bangs.moveTo(275f, 383f)
            bangs.cubicTo(295f, 270f, 445f, 305f, 430f, 435f)
            bangs.lineTo(360f, 351f)
            bangs.lineTo(335f, 423f)
            bangs.lineTo(321f, 365f)
            bangs.close()
            c.drawPath(bangs, p)
            p.color = 0xffa39bbd.toInt()
            val dress = Path()
            dress.moveTo(306f, 480f)
            dress.lineTo(403f, 480f)
            dress.cubicTo(440f, 650f, 500f, 865f, 540f, 1080f)
            dress.cubicTo(410f, 1140f, 230f, 1130f, 160f, 1075f)
            dress.close()
            c.drawPath(dress, p)
            p.color = 0xff4a435f.toInt()
            p.strokeWidth = 4f
            for (i in 0..5) c.drawLine(350f, 510f, 190f + i * 62, 1070f, p)
            p.color = 0xff302b46.toInt()
            c.drawOval(342f, 395f, 351f, 405f, p)
            c.drawOval(390f, 395f, 399f, 405f, p)
        }
        if (kind == 1) {
            repeat(20) { i ->
                val x = i * 42f - 20
                p.color = Color.rgb(12 + i % 4 * 8, 35 + i % 3 * 10, 33)
                c.drawRect(x, 210f + rnd.nextFloat() * 300, x + 12, 1280f, p)
                repeat(6) { j -> c.drawOval(x - 80, 90f + j * 95, x + 100, 280f + j * 95, p) }
            }
        }
        if (kind == 2 || kind == 5) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = if (kind == 5) 2f else 5f
            repeat(18) { i ->
                p.color = Color.argb(50 + i * 7, 150, 170, 190)
                val path = Path()
                path.moveTo(-100f, 280f + i * 45)
                path.cubicTo(280f, 150f + i * 32, 300f, 940f - i * 8, 850f, 700f + i * 27)
                c.drawPath(path, p)
            }
            p.style = Paint.Style.FILL
        }
        if (kind == 4) {
            p.color = 0xff94bde1.toInt()
            p.strokeWidth = 2f
            val stars = listOf(100f to 400f, 260f to 330f, 360f to 500f, 510f to 420f)
            stars.zipWithNext().forEach {
                c.drawLine(it.first.first, it.first.second, it.second.first, it.second.second, p)
            }
            stars.forEach { c.drawCircle(it.first, it.second, 5f, p) }
        }
        return b
    }
}
