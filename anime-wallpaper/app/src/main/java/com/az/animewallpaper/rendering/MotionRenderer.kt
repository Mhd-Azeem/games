package com.az.animewallpaper.rendering

import android.content.Context
import android.opengl.*
import com.az.animewallpaper.models.*
import com.az.animewallpaper.physics.*
import com.az.animewallpaper.selection.Masks
import com.az.animewallpaper.spatial.*
import com.az.animewallpaper.storage.ProjectStore
import java.nio.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

class ImageFrame(
    val viewW: Float,
    val viewH: Float,
    val imageW: Float,
    val imageH: Float,
    val p: Project,
    val forceFit: Boolean = false,
) {
    private val rotated = p.rotation % 180 != 0
    private val rw = if (rotated) imageH else imageW
    private val rh = if (rotated) imageW else imageH
    private val scale =
        (if (forceFit || p.fit) min(viewW / rw, viewH / rh) else max(viewW / rw, viewH / rh)) *
            (if (forceFit) 1f else p.zoom) *
            (if (p.spatial.enabled && !forceFit) 1.07f else 1f)
    private val offsetX = if (forceFit) 0f else p.offsetX
    private val offsetY = if (forceFit) 0f else p.offsetY
    private val angle = p.rotation * PI.toFloat() / 180
    private val c = cos(angle)
    private val s = sin(angle)

    fun writeScreen(x: Float, y: Float, out: FloatArray) {
        val a = (x - .5f) * imageW * scale
        val b = (y - .5f) * imageH * scale
        out[0] = viewW * (.5f + offsetX) + a * c - b * s
        out[1] = viewH * (.5f + offsetY) + a * s + b * c
    }

    fun screen(x: Float, y: Float): Pair<Float, Float> {
        val out = FloatArray(2)
        writeScreen(x, y, out)
        return out[0] to out[1]
    }

    fun image(x: Float, y: Float): Pair<Float, Float> {
        val a = x - viewW * (.5f + offsetX)
        val b = y - viewH * (.5f + offsetY)
        return Pair(
            (a * c + b * s) / (imageW * scale) + .5f,
            (-a * s + b * c) / (imageH * scale) + .5f,
        )
    }
}

class MotionRenderer(val context: Context) : GLSurfaceView.Renderer {
    @Volatile var pending: Project? = null
    @Volatile var fitPreview = false
    @Volatile var demoX = 0f
    @Volatile var demoY = 0f
    @Volatile var inputX = 0f
    @Volatile var inputY = 0f
    @Volatile var paused = false
    @Volatile var slow = false
    @Volatile var fps = 0f
    @Volatile var renderMs = 0f
    private var p = Project()
    private var path = ""
    private var imageStamp = 0L
    @Volatile var moving = false
    private var springCache = emptyMap<String, Spring>()
    private var imageW = 720f
    private var imageH = 1280f
    private var program = 0
    private var foregroundTexture = 0
    private var spatialStamp = 0
    private val spatialMotion = SpatialMotion()
    private var texture = 0
    private var width = 1
    private var height = 1
    private var last = 0L
    private var grid = 64
    private var springs = emptyList<Spring>()
    private var params = emptyList<Physics>()
    private var weights = emptyList<FloatArray>()
    private lateinit var vertices: FloatBuffer
    private lateinit var uv: FloatBuffer
    private lateinit var indices: ShortBuffer
    private var indexCount = 0
    private val paintLock = Any()
    private val position = FloatArray(2)

    fun reset() {
        synchronized(paintLock) {
            springs.forEach { it.reset() }
            spatialMotion.reset()
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        fun shader(type: Int, source: String): Int {
            val id = GLES20.glCreateShader(type)
            GLES20.glShaderSource(id, source)
            GLES20.glCompileShader(id)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { GLES20.glGetShaderInfoLog(id) }
            return id
        }
        val vs =
            shader(
                GLES20.GL_VERTEX_SHADER,
                "attribute vec2 aPosition;attribute vec2 aUv;varying vec2 vUv;void main(){vUv=aUv;gl_Position=vec4(aPosition,0.0,1.0);}",
            )
        val fs =
            shader(
                GLES20.GL_FRAGMENT_SHADER,
                "precision mediump float;varying vec2 vUv;uniform sampler2D uImage;void main(){gl_FragColor=texture2D(uImage,vUv);}",
            )
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, ok, 0)
        check(ok[0] != 0) { GLES20.glGetProgramInfoLog(program) }
        val ids = IntArray(2)
        GLES20.glGenTextures(2, ids, 0)
        texture = ids[0]
        foregroundTexture = ids[1]
        for (id in ids) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR,
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR,
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE,
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE,
            )
        }
        path = ""
        if (pending == null && p.image.isNotEmpty()) pending = p
        last = 0
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w
        height = h
        GLES20.glViewport(0, 0, w, h)
    }

    private fun configure(next: Project) {
        p = next
        val stamp = java.io.File(p.image).lastModified() xor p.id.hashCode().toLong()
        val layered = p.spatial.enabled && p.spatial.kind == "subject"
        val layerStamp = if (layered) p.spatial.depth.contentHashCode() else 0
        if (path != p.image || imageStamp != stamp || spatialStamp != layerStamp) {
            val b = ProjectStore(context).decode(p.image, if (layered) 1600 else 2048)
            imageW = b.width.toFloat()
            imageH = b.height.toFloat()
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            if (layered) {
                val (background, foreground) = SpatialLayers.build(b, p.spatial.depth)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, background, 0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, foregroundTexture)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, foreground, 0)
                background.recycle()
                foreground.recycle()
            } else {
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, b, 0)
                val empty =
                    android.graphics.Bitmap.createBitmap(
                        1,
                        1,
                        android.graphics.Bitmap.Config.ARGB_8888,
                    )
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, foregroundTexture)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, empty, 0)
                empty.recycle()
            }
            spatialStamp = layerStamp
            b.recycle()
            path = p.image
            imageStamp = stamp
        }
        grid = p.quality
        val count = (grid + 1) * (grid + 1)
        fun floats(n: Int) =
            ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertices = floats(count * 2)
        uv = floats(count * 2)
        indices =
            ByteBuffer.allocateDirect(grid * grid * 6 * 2)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer()
        indexCount = grid * grid * 6
        for (y in 0..grid) for (x in 0..grid) {
            uv.put(x.toFloat() / grid)
            uv.put(y.toFloat() / grid)
        }
        uv.position(0)
        for (y in 0 until grid) for (x in 0 until grid) {
            val a = y * (grid + 1) + x
            intArrayOf(a, a + grid + 1, a + 1, a + 1, a + grid + 1, a + grid + 2).forEach {
                indices.put(it.toShort())
            }
        }
        indices.position(0)
        val configs = mutableListOf<Physics>()
        val keys = mutableListOf<String>()
        val fields = mutableListOf<FloatArray>()
        val masks =
            p.regions.associate {
                it.id to
                    Masks.feather(
                        it.mask,
                        (it.physics.feather * (p.global.feather / .4f) * 10).toInt(),
                    )
            }
        val fixedPoints = p.points.filter { it.fixed && it.enabled }
        val fixed =
            FloatArray(count) { i ->
                val x = (i % (grid + 1)).toFloat() / grid
                val y = (i / (grid + 1)).toFloat() / grid
                var a = 1f
                fixedPoints.forEach { a *= 1 - Influence.radial(x, y, it) }
                a
            }
        fun field(key: String, config: Physics, weight: (Float, Float) -> Float) {
            keys.add(key)
            configs.add(config)
            fields.add(
                FloatArray(count) { i ->
                    val x = (i % (grid + 1)).toFloat() / grid
                    val y = (i / (grid + 1)).toFloat() / grid
                    val edge = minOf(x, y, 1 - x, 1 - y)
                    weight(x, y) * fixed[i] * (edge * 40).coerceIn(0f, 1f)
                }
            )
        }
        fun pointField(point: MotionPoint, r: Region?) {
            val scaled =
                point.copy(
                    physics =
                        point.physics.copy(radius = point.physics.radius * (p.global.radius / .15f))
                )
            val config =
                point.physics.copy(
                    intensity = point.physics.intensity * (r?.physics?.intensity ?: 1f),
                    sensitivity = point.physics.sensitivity * (r?.physics?.sensitivity ?: 1f),
                    stiffness = point.physics.stiffness * ((r?.physics?.stiffness ?: 40f) / 40f),
                    damping = point.physics.damping * ((r?.physics?.damping ?: 55f) / 55f),
                    maxDisplacement =
                        point.physics.maxDisplacement *
                            ((r?.physics?.maxDisplacement ?: .045f) / .045f),
                    horizontal = point.physics.horizontal * (r?.physics?.horizontal ?: 1f),
                    vertical = point.physics.vertical * (r?.physics?.vertical ?: 1f),
                    delay = (point.physics.delay + (r?.physics?.delay ?: 0)).coerceAtMost(500),
                    direction =
                        if (point.physics.direction == 0) r?.physics?.direction ?: 0
                        else point.physics.direction,
                )
            field(point.id, config) { x, y ->
                Influence.radial(x, y, scaled) *
                    (r?.let { Masks.sample(masks.getValue(it.id), x, y) } ?: 1f)
            }
        }
        p.regions
            .filter { it.enabled }
            .forEach { r ->
                val assigned = p.points.filter { it.region == r.id && it.enabled && !it.fixed }
                if (assigned.isEmpty())
                    field(r.id, r.physics) { x, y -> Masks.sample(masks.getValue(r.id), x, y) }
                else assigned.forEach { pointField(it, r) }
            }
        p.points
            .filter { it.enabled && !it.fixed && it.region.isEmpty() }
            .forEach { pointField(it, null) }
        synchronized(paintLock) {
            params = configs
            weights = fields
            springs = keys.map { springCache[it] ?: Spring() }
            springCache = keys.zip(springs).toMap()
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        val start = System.nanoTime()
        pending?.let {
            pending = null
            configure(it)
        }
        GLES20.glClearColor(.05f, .055f, .065f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (path.isEmpty()) return
        val dt = if (last == 0L) 1f / 30 else ((start - last) / 1e9f).coerceIn(.001f, .05f)
        last = start
        fps = .9f * fps + .1f / dt
        val frame = ImageFrame(width.toFloat(), height.toFloat(), imageW, imageH, p, fitPreview)
        GLES20.glUseProgram(program)
        val pos = GLES20.glGetAttribLocation(program, "aPosition")
        val tex = GLES20.glGetAttribLocation(program, "aUv")
        GLES20.glEnableVertexAttribArray(pos)
        GLES20.glEnableVertexAttribArray(tex)
        GLES20.glVertexAttribPointer(tex, 2, GLES20.GL_FLOAT, false, 0, uv)
        synchronized(paintLock) {
            if (!paused) {
                springs.forEachIndexed { i, s ->
                    s.step(
                        inputX + demoX,
                        inputY + demoY,
                        if (slow) dt * .25f else dt,
                        params[i],
                        p.global,
                    )
                }
                if (p.spatial.enabled) {
                    val angle = -p.rotation * PI.toFloat() / 180
                    val sx = inputX + demoX
                    val sy = inputY + demoY
                    spatialMotion.step(
                        sx * cos(angle) - sy * sin(angle),
                        sx * sin(angle) + sy * cos(angle),
                        if (slow) dt * .25f else dt,
                        p.spatial.smoothing,
                    )
                }
            }
            val layered = p.spatial.enabled && p.spatial.kind == "subject"
            for (layer in 0 until if (layered) 2 else 1) {
                vertices.position(0)
                for (y in 0..grid) for (x in 0..grid) {
                    val k = y * (grid + 1) + x
                    val u = x.toFloat() / grid
                    val v = y.toFloat() / grid
                    var dx = 0f
                    var dy = 0f
                    for (i in springs.indices) {
                        val w = weights[i][k]
                        dx = dx * (1 - w) + springs[i].x * w
                        dy = dy * (1 - w) + springs[i].y * w
                    }
                    if (p.spatial.enabled) {
                        val depth =
                            if (layered) {
                                if (layer == 0) -.18f else .7f
                            } else Masks.sample(p.spatial.depth, u, v) * .8f - .2f
                        if (layered && layer == 0) {
                            val a = 1 - Masks.sample(p.spatial.depth, u, v)
                            dx *= a
                            dy *= a
                        }
                        spatialMotion.transform(
                            .5f + (u + dx - .5f),
                            .5f + (v + dy - .5f),
                            depth,
                            imageH / imageW,
                            p.spatial.strength,
                            position,
                        )
                        frame.writeScreen(position[0], position[1], position)
                    } else frame.writeScreen(u + dx, v + dy, position)
                    vertices.put(position[0] / width * 2 - 1)
                    vertices.put(1 - position[1] / height * 2)
                }
                vertices.position(0)
                if (layer == 1) {
                    GLES20.glEnable(GLES20.GL_BLEND)
                    GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
                }
                GLES20.glBindTexture(
                    GLES20.GL_TEXTURE_2D,
                    if (layer == 0) texture else foregroundTexture,
                )
                GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 0, vertices)
                GLES20.glDrawElements(
                    GLES20.GL_TRIANGLES,
                    indexCount,
                    GLES20.GL_UNSIGNED_SHORT,
                    indices,
                )
            }
            GLES20.glDisable(GLES20.GL_BLEND)
            moving =
                !paused &&
                    (abs(inputX + demoX) > .03f ||
                        abs(inputY + demoY) > .03f ||
                        (p.spatial.enabled &&
                            abs(spatialMotion.x) + abs(spatialMotion.y) > .0001f) ||
                        springs.any { abs(it.x) + abs(it.y) > .0001f })
        }
        renderMs = (System.nanoTime() - start) / 1e6f
    }
}
