package com.az.animewallpaper.editor

import android.content.Context
import android.graphics.*
import android.view.*
import com.az.animewallpaper.models.*
import com.az.animewallpaper.models.Region
import com.az.animewallpaper.rendering.ImageFrame
import com.az.animewallpaper.selection.Masks
import kotlin.math.*

enum class Tool {
    SELECT,
    POINT,
    BRUSH,
    ERASER,
    LASSO,
    POLYGON,
    ELLIPSE,
    RECTANGLE,
    WAND,
    PAN,
}

class EditorOverlay(context: Context) : View(context) {
    lateinit var project: Project
    var imageW = 720f
    var imageH = 1280f
    var selectedPoint = ""
    var selectedRegion = ""
    var tool = Tool.SELECT
    var brushRadius = .055f
    var hardness = .6f
    var opacity = .8f
    var tolerance = 35
    var hidden = false
    var checkpoint: () -> Unit = {}
    var changed: () -> Unit = {}
    var selection: () -> Unit = {}
    var pixels: IntArray? = null
    var pixelW = 128
    var pixelH = 128
    private val paint = Paint(3)
    private val path = Path()
    private val outline = mutableListOf<Pair<Float, Float>>()
    private var previous = 0f to 0f
    private var start = 0f to 0f
    private var previousScreen = 0f to 0f
    private var multi = false
    private var maskBitmap = Bitmap.createBitmap(MASK_SIZE, MASK_SIZE, Bitmap.Config.ARGB_8888)
    private val colors = IntArray(MASK_SIZE * MASK_SIZE)
    private var maskDirty = true
    private var vertexIndex = -1
    private val pinch =
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    if (tool == Tool.POINT && selectedPoint.isNotEmpty())
                        project.points
                            .find { it.id == selectedPoint }
                            ?.let {
                                it.physics.radius =
                                    (it.physics.radius * detector.scaleFactor).coerceIn(.001f, .5f)
                            }
                    else project.zoom = (project.zoom * detector.scaleFactor).coerceIn(.25f, 5f)
                    changed()
                    invalidate()
                    return true
                }
            },
        )

    fun dirty() {
        maskDirty = true
        invalidate()
    }

    private fun frame() = ImageFrame(width.toFloat(), height.toFloat(), imageW, imageH, project)

    private fun region(): Region? = project.regions.find { it.id == selectedRegion }

    fun finishPolygon() {
        if (outline.size >= 3) {
            checkpoint()
            region()?.let {
                if (!it.locked) {
                    Masks.polygon(it.mask, outline, opacity)
                    it.vertices = outline.toMutableList()
                }
            }
            outline.clear()
            dirty()
            changed()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!::project.isInitialized || hidden) return
        val f = frame()
        region()?.let { r ->
            if (maskDirty) {
                for (i in colors.indices) colors[i] =
                    Color.argb((r.mask[i] * project.overlay * 255).toInt(), 98, 204, 215)
                maskBitmap.setPixels(colors, 0, MASK_SIZE, 0, 0, MASK_SIZE, MASK_SIZE)
                maskDirty = false
            }
            val src = floatArrayOf(0f, 0f, MASK_SIZE.toFloat(), 0f, 0f, MASK_SIZE.toFloat())
            val a = f.screen(0f, 0f)
            val b = f.screen(1f, 0f)
            val c = f.screen(0f, 1f)
            val dst = floatArrayOf(a.first, a.second, b.first, b.second, c.first, c.second)
            val matrix = Matrix()
            matrix.setPolyToPoly(src, 0, dst, 0, 3)
            canvas.drawBitmap(maskBitmap, matrix, paint)
        }
        region()?.vertices?.forEach { v ->
            val pos = f.screen(v.first, v.second)
            paint.color = 0xffffbe74.toInt()
            paint.style = Paint.Style.FILL
            canvas.drawCircle(pos.first, pos.second, 7 * resources.displayMetrics.density, paint)
        }
        for (point in project.points) {
            val v = f.screen(point.x, point.y)
            val edge = f.screen(point.x + point.physics.radius, point.y)
            paint.color =
                if (point.fixed) 0xffffbe74.toInt()
                else if (point.id == selectedPoint) Color.WHITE else 0xff91dce2.toInt()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            if (point.id == selectedPoint) {
                canvas.drawCircle(
                    v.first,
                    v.second,
                    hypot(edge.first - v.first, edge.second - v.second),
                    paint,
                )
            }
            paint.style = Paint.Style.FILL
            canvas.drawCircle(v.first, v.second, 12f * resources.displayMetrics.density, paint)
            paint.color = Color.BLACK
            paint.textSize = 18f * resources.displayMetrics.density
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(
                if (point.fixed) "×" else "+",
                v.first,
                v.second + 6 * resources.displayMetrics.density,
                paint,
            )
        }
        if (outline.isNotEmpty()) {
            path.reset()
            outline.forEachIndexed { i, v ->
                val a = f.screen(v.first, v.second)
                if (i == 0) path.moveTo(a.first, a.second) else path.lineTo(a.first, a.second)
            }
            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
            outline.forEach {
                val a = f.screen(it.first, it.second)
                canvas.drawCircle(a.first, a.second, 5f, paint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (hidden) return false
        pinch.onTouchEvent(event)
        val pos = frame().image(event.x, event.y)
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            multi = true
            return true
        }
        if (event.pointerCount > 1) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                multi = false
                start = pos
                previous = pos
                previousScreen = event.x to event.y
                when (tool) {
                    Tool.POINT -> {
                        checkpoint()
                        if (project.points.size < 512) {
                            val point =
                                MotionPoint(
                                    name = "Point ${project.points.size+1}",
                                    x = pos.first.coerceIn(0f, 1f),
                                    y = pos.second.coerceIn(0f, 1f),
                                    region = selectedRegion,
                                )
                            project.points.add(point)
                            selectedPoint = point.id
                            selection()
                            changed()
                        }
                    }
                    Tool.SELECT -> {
                        vertexIndex =
                            region()
                                ?.takeUnless { it.locked }
                                ?.vertices
                                ?.indexOfFirst { v ->
                                    val a = frame().screen(v.first, v.second)
                                    hypot(a.first - event.x, a.second - event.y) <
                                        24 * resources.displayMetrics.density
                                } ?: -1
                        selectedPoint =
                            project.points
                                .minByOrNull {
                                    val a = frame().screen(it.x, it.y)
                                    hypot(a.first - event.x, a.second - event.y)
                                }
                                ?.takeIf {
                                    val a = frame().screen(it.x, it.y)
                                    hypot(a.first - event.x, a.second - event.y) <
                                        40 * resources.displayMetrics.density
                                }
                                ?.id ?: ""
                        checkpoint()
                        selection()
                    }
                    Tool.BRUSH,
                    Tool.ERASER -> {
                        checkpoint()
                        paintStroke(pos, pos)
                    }
                    Tool.LASSO -> {
                        checkpoint()
                        outline.clear()
                        outline.add(pos)
                    }
                    Tool.POLYGON -> {
                        outline.add(pos)
                    }
                    Tool.ELLIPSE,
                    Tool.RECTANGLE -> {
                        checkpoint()
                    }
                    Tool.WAND -> {
                        checkpoint()
                        region()
                            ?.takeUnless { it.locked }
                            ?.let { r ->
                                pixels?.let {
                                    Masks.wand(
                                        r.mask,
                                        it,
                                        pixelW,
                                        pixelH,
                                        pos.first,
                                        pos.second,
                                        tolerance,
                                    )
                                }
                            }
                        dirty()
                        changed()
                    }
                    Tool.PAN -> checkpoint()
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (multi) return true
                when (tool) {
                    Tool.SELECT -> {
                        if (vertexIndex >= 0) {
                            region()?.let { r ->
                                r.vertices[vertexIndex] =
                                    pos.first.coerceIn(0f, 1f) to pos.second.coerceIn(0f, 1f)
                                r.mask.fill(0f)
                                Masks.polygon(r.mask, r.vertices, opacity)
                                dirty()
                                changed()
                            }
                        } else
                            project.points
                                .find { it.id == selectedPoint }
                                ?.let {
                                    it.x = pos.first.coerceIn(0f, 1f)
                                    it.y = pos.second.coerceIn(0f, 1f)
                                    changed()
                                }
                    }
                    Tool.BRUSH,
                    Tool.ERASER -> paintStroke(previous, pos)
                    Tool.LASSO -> {
                        if (hypot(pos.first - previous.first, pos.second - previous.second) > .003f)
                            outline.add(pos)
                    }
                    Tool.PAN -> {
                        project.offsetX += (event.x - previousScreen.first) / width
                        project.offsetY += (event.y - previousScreen.second) / height
                        changed()
                    }
                    else -> {}
                }
                previous = pos
                previousScreen = event.x to event.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!multi) {
                    when (tool) {
                        Tool.LASSO -> {
                            region()
                                ?.takeUnless { it.locked }
                                ?.let { Masks.polygon(it.mask, outline, opacity) }
                            outline.clear()
                            dirty()
                            changed()
                        }
                        Tool.ELLIPSE,
                        Tool.RECTANGLE -> {
                            region()
                                ?.takeUnless { it.locked }
                                ?.let {
                                    Masks.shape(
                                        it.mask,
                                        start.first,
                                        start.second,
                                        pos.first,
                                        pos.second,
                                        tool == Tool.ELLIPSE,
                                        opacity,
                                    )
                                }
                            dirty()
                            changed()
                        }
                        else -> {}
                    }
                }
                parent.requestDisallowInterceptTouchEvent(false)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                outline.clear()
                parent.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }

    private fun paintStroke(a: Pair<Float, Float>, b: Pair<Float, Float>) {
        region()
            ?.takeUnless { it.locked }
            ?.let {
                Masks.stroke(
                    it.mask,
                    a.first,
                    a.second,
                    b.first,
                    b.second,
                    brushRadius,
                    hardness,
                    opacity,
                    tool == Tool.ERASER,
                )
                dirty()
                changed()
            }
    }
}
