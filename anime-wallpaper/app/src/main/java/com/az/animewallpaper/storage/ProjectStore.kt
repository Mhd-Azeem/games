package com.az.animewallpaper.storage

import android.content.Context
import android.graphics.*
import android.net.Uri
import com.az.animewallpaper.models.*
import com.az.animewallpaper.models.Region
import com.az.animewallpaper.rendering.ImageFrame
import com.az.animewallpaper.selection.Masks
import java.io.*
import java.util.Base64
import java.util.zip.*
import kotlin.math.*
import org.json.*

object Codec {
    const val VERSION = 2

    fun physics(p: Physics) =
        JSONObject()
            .put("intensity", p.intensity)
            .put("sensitivity", p.sensitivity)
            .put("stiffness", p.stiffness)
            .put("damping", p.damping)
            .put("radius", p.radius)
            .put("max", p.maxDisplacement)
            .put("horizontal", p.horizontal)
            .put("vertical", p.vertical)
            .put("delay", p.delay)
            .put("feather", p.feather)
            .put("direction", p.direction)

    fun physics(o: JSONObject): Physics {
        fun f(k: String, d: Float, min: Float, max: Float) =
            o.optDouble(k, d.toDouble()).toFloat().let {
                if (it.isFinite()) it.coerceIn(min, max) else d
            }
        return Physics(
            f("intensity", 0.65f, 0f, 2f),
            f("sensitivity", 1f, 0f, 2f),
            f("stiffness", 40f, 0f, 100f),
            f("damping", 55f, 0f, 100f),
            f("radius", 0.15f, 0.001f, 0.5f),
            f("max", 0.045f, 0f, 0.1f),
            f("horizontal", 1f, 0f, 2f),
            f("vertical", 1f, 0f, 2f),
            o.optInt("delay").coerceIn(0, 500),
            f("feather", 0.4f, 0f, 1f),
            o.optInt("direction").coerceIn(0, 2),
        )
    }

    fun encode(p: Project): String {
        val points = JSONArray()
        p.points.forEach {
            points.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("x", it.x)
                    .put("y", it.y)
                    .put("enabled", it.enabled)
                    .put("fixed", it.fixed)
                    .put("region", it.region)
                    .put("group", it.group)
                    .put("physics", physics(it.physics))
            )
        }
        val regions = JSONArray()
        p.regions.forEach { r ->
            val bytes =
                ByteArray(r.mask.size) { (r.mask[it].coerceIn(0f, 1f) * 255).toInt().toByte() }
            regions.put(
                JSONObject()
                    .put("id", r.id)
                    .put("name", r.name)
                    .put("enabled", r.enabled)
                    .put("locked", r.locked)
                    .put("group", r.group)
                    .put("physics", physics(r.physics))
                    .put(
                        "vertices",
                        JSONArray(r.vertices.map { JSONArray(listOf(it.first, it.second)) }),
                    )
                    .put("mask", Base64.getEncoder().encodeToString(bytes))
            )
        }
        return JSONObject()
            .put("version", VERSION)
            .put("id", p.id)
            .put("name", p.name)
            .put("image", p.image)
            .put("created", p.created)
            .put("modified", p.modified)
            .put("points", points)
            .put("regions", regions)
            .put("global", physics(p.global))
            .put("zoom", p.zoom)
            .put("offsetX", p.offsetX)
            .put("offsetY", p.offsetY)
            .put("rotation", p.rotation)
            .put("fit", p.fit)
            .put("fps", p.fps)
            .put("quality", p.quality)
            .put("overlay", p.overlay)
            .put(
                "spatial",
                JSONObject()
                    .put("enabled", p.spatial.enabled)
                    .put("kind", p.spatial.kind)
                    .put("strong", p.spatial.strong)
                    .put("strength", p.spatial.strength)
                    .put("smoothing", p.spatial.smoothing)
                    .put(
                        "depth",
                        Base64.getEncoder()
                            .encodeToString(
                                ByteArray(p.spatial.depth.size) {
                                    (p.spatial.depth[it].coerceIn(0f, 1f) * 255).toInt().toByte()
                                }
                            ),
                    ),
            )
            .toString()
    }

    fun decode(json: String): Project {
        val o = JSONObject(json)
        require(o.getInt("version") in 1..VERSION) { "Unsupported project version" }
        val p =
            Project(
                id = o.getString("id"),
                name = o.getString("name"),
                image = o.getString("image"),
                created = o.getLong("created"),
                modified = o.getLong("modified"),
            )
        require(p.id.matches(Regex("[A-Za-z0-9-]{1,80}"))) { "Invalid project ID" }
        p.global = physics(o.getJSONObject("global"))
        p.zoom = o.optDouble("zoom", 1.0).toFloat().coerceIn(0.25f, 5f)
        p.offsetX = o.optDouble("offsetX").toFloat().coerceIn(-1f, 1f)
        p.offsetY = o.optDouble("offsetY").toFloat().coerceIn(-1f, 1f)
        p.rotation = (o.optInt("rotation") / 90 * 90) % 360
        p.fit = o.optBoolean("fit")
        p.fps = o.optInt("fps", 30).coerceIn(15, 60)
        p.quality = o.optInt("quality", 64).coerceIn(24, 96)
        p.overlay = o.optDouble("overlay", 0.4).toFloat().coerceIn(0f, 1f)
        o.optJSONObject("spatial")?.let { a ->
            val bytes = Base64.getDecoder().decode(a.getString("depth"))
            require(bytes.size == MASK_SIZE * MASK_SIZE) { "Invalid spatial map" }
            val kind = a.optString("kind", "none")
            require(kind in listOf("none", "subject", "scene")) { "Invalid spatial mode" }
            val strength = a.optDouble("strength", .7).toFloat()
            val smoothing = a.optDouble("smoothing", .7).toFloat()
            require(strength.isFinite() && smoothing.isFinite())
            p.spatial =
                SpatialSettings(
                    a.optBoolean("enabled") && kind != "none",
                    kind,
                    strength.coerceIn(0f, 1.5f),
                    smoothing.coerceIn(0f, 1f),
                    FloatArray(bytes.size) { (bytes[it].toInt() and 255) / 255f },
                    a.optBoolean("strong"),
                )
        }
        val points = o.getJSONArray("points")
        require(points.length() <= 512) { "Maximum 512 points" }
        for (i in 0 until points.length()) {
            val a = points.getJSONObject(i)
            p.points.add(
                MotionPoint(
                    a.getString("id"),
                    a.getString("name"),
                    a.getDouble("x").toFloat().coerceIn(0f, 1f),
                    a.getDouble("y").toFloat().coerceIn(0f, 1f),
                    a.optBoolean("enabled", true),
                    a.optBoolean("fixed"),
                    a.optString("region"),
                    a.optString("group"),
                    physics(a.getJSONObject("physics")),
                )
            )
        }
        val regions = o.getJSONArray("regions")
        require(regions.length() <= 32) { "Maximum 32 regions" }
        for (i in 0 until regions.length()) {
            val a = regions.getJSONObject(i)
            val bytes = Base64.getDecoder().decode(a.getString("mask"))
            require(bytes.size == MASK_SIZE * MASK_SIZE) { "Invalid mask dimensions" }
            p.regions.add(
                Region(
                    a.getString("id"),
                    a.getString("name"),
                    a.optBoolean("enabled", true),
                    a.optBoolean("locked"),
                    a.optString("group"),
                    physics(a.getJSONObject("physics")),
                    FloatArray(bytes.size) { (bytes[it].toInt() and 255) / 255f },
                )
            )
        }
        for (i in 0 until regions.length()) {
            val v = regions.getJSONObject(i).optJSONArray("vertices") ?: continue
            require(v.length() <= 1024)
            for (j in 0 until v.length()) {
                val a = v.getJSONArray(j)
                p.regions[i]
                    .vertices
                    .add(
                        a.getDouble(0).toFloat().coerceIn(0f, 1f) to
                            a.getDouble(1).toFloat().coerceIn(0f, 1f)
                    )
            }
        }
        require(listOf(p.zoom, p.offsetX, p.offsetY, p.overlay).all { it.isFinite() }) {
            "Invalid framing values"
        }
        require(p.points.all { it.x.isFinite() && it.y.isFinite() }) { "Invalid point coordinates" }
        require(
            p.points.map { it.id }.distinct().size == p.points.size &&
                p.regions.map { it.id }.distinct().size == p.regions.size
        ) {
            "Duplicate identifiers"
        }
        return p
    }
}

class ProjectStore(val context: Context) {
    val root = File(context.filesDir, "projects").apply { mkdirs() }

    private fun directory(id: String): File {
        require(id.matches(Regex("[A-Za-z0-9-]{1,80}")))
        return File(root, id).apply { mkdirs() }
    }

    fun save(p: Project) {
        p.modified = System.currentTimeMillis()
        val dir = directory(p.id)
        val tmp = File(dir, "project.tmp")
        tmp.writeText(Codec.encode(p))
        check(tmp.renameTo(File(dir, "project.json"))) { "Unable to save project" }
        val source = decode(p.image, 256)
        val thumb = Bitmap.createBitmap(160, 240, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(thumb)
        canvas.drawColor(Color.rgb(21, 22, 25))
        val frame = ImageFrame(160f, 240f, source.width.toFloat(), source.height.toFloat(), p)
        val a = frame.screen(0f, 0f)
        val b = frame.screen(1f, 0f)
        val c = frame.screen(0f, 1f)
        val matrix = Matrix()
        matrix.setPolyToPoly(
            floatArrayOf(0f, 0f, source.width.toFloat(), 0f, 0f, source.height.toFloat()),
            0,
            floatArrayOf(a.first, a.second, b.first, b.second, c.first, c.second),
            0,
            3,
        )
        canvas.drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        File(dir, "thumbnail.png").outputStream().use {
            thumb.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        source.recycle()
        thumb.recycle()
    }

    fun thumbnail(p: Project): Bitmap =
        BitmapFactory.decodeFile(File(directory(p.id), "thumbnail.png").absolutePath)
            ?: decode(p.image, 160)

    fun setActive(p: Project) {
        val dir = File(context.filesDir, "active-wallpaper").apply { mkdirs() }
        val copy = Codec.decode(Codec.encode(p))
        val image = File(dir, "image")
        File(p.image).copyTo(File(dir, "image.tmp"), true)
        check(File(dir, "image.tmp").renameTo(image))
        copy.image = image.absolutePath
        val tmp = File(dir, "project.tmp")
        tmp.writeText(Codec.encode(copy))
        check(tmp.renameTo(File(dir, "project.json")))
    }

    fun active(): Project? {
        val file = File(context.filesDir, "active-wallpaper/project.json")
        return if (file.isFile) Codec.decode(file.readText()) else null
    }

    fun list(): List<Project> =
        root
            .listFiles()
            ?.mapNotNull {
                try {
                    load(it.name)
                } catch (_: Exception) {
                    null
                }
            }
            ?.sortedByDescending { it.modified } ?: emptyList()

    fun load(id: String): Project =
        Codec.decode(File(directory(id), "project.json").readText()).also {
            require(File(directory(id), "image").isFile) { "Source image missing" }
            it.image = File(directory(id), "image").absolutePath
        }

    fun delete(p: Project) {
        directory(p.id).deleteRecursively()
    }

    fun duplicate(p: Project): Project {
        val copy = Codec.decode(Codec.encode(p))
        val n = Project(name = p.name + " copy")
        val dst = directory(n.id)
        File(p.image).copyTo(File(dst, "image"), true)
        val o =
            JSONObject(Codec.encode(copy))
                .put("id", n.id)
                .put("name", n.name)
                .put("created", n.created)
                .put("image", File(dst, "image").absolutePath)
        return Codec.decode(o.toString()).also { save(it) }
    }

    fun importImage(uri: Uri): Project {
        val p = Project(name = "Gallery wallpaper")
        val file = File(directory(p.id), "image")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { copyBounded(input, it, 80L * 1024 * 1024) }
            } ?: error("Image cannot be opened")
            decode(file.absolutePath, 2048).recycle()
            p.image = file.absolutePath
            save(p)
            return p
        } catch (e: Exception) {
            directory(p.id).deleteRecursively()
            throw e
        }
    }

    fun decode(path: String, maxSide: Int = 2048): Bitmap {
        val src = ImageDecoder.createSource(File(path))
        return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            val s = min(1f, maxSide.toFloat() / max(info.size.width, info.size.height))
            require(info.size.width > 0 && info.size.height > 0)
            decoder.setTargetSize(
                max(1, (info.size.width * s).toInt()),
                max(1, (info.size.height * s).toInt()),
            )
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    fun export(p: Project, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("project.json"))
            zip.write(Codec.encode(p).toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("image"))
            File(p.image).inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    fun importProject(input: InputStream): Project {
        val tmp = File(context.cacheDir, "import-${id()}").apply { mkdirs() }
        try {
            var total = 0L
            val seen = mutableSetOf<String>()
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(entry.name in setOf("project.json", "image") && seen.add(entry.name)) {
                        "Invalid project archive"
                    }
                    File(tmp, entry.name).outputStream().use {
                        total += copyBounded(zip, it, 90L * 1024 * 1024 - total)
                    }
                    zip.closeEntry()
                }
            }
            require(seen.size == 2)
            val json = File(tmp, "project.json")
            require(json.length() < 3_000_000)
            val p = Codec.decode(json.readText())
            decode(File(tmp, "image").absolutePath).recycle()
            val o = JSONObject(Codec.encode(p)).put("id", id())
            val result = Codec.decode(o.toString())
            val dst = directory(result.id)
            File(tmp, "image").copyTo(File(dst, "image"))
            result.image = File(dst, "image").absolutePath
            save(result)
            return result
        } finally {
            tmp.deleteRecursively()
        }
    }

    private fun copyBounded(input: InputStream, out: OutputStream, limit: Long): Long {
        var total = 0L
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            require(total <= limit) { "File exceeds size limit" }
            out.write(buffer, 0, n)
        }
        return total
    }

    fun starter(index: Int): Project {
        val names =
            listOf(
                "Moonlit anime silhouette",
                "Wind in the forest",
                "Graphite aurora",
                "Fantasy peaks",
                "Quiet constellations",
                "Minimal charcoal",
            )
        val p = Project(name = names[index])
        val file = File(directory(p.id), "image")
        val bitmap = Artwork.make(index)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        p.image = file.absolutePath
        p.points.add(
            MotionPoint(
                name = "Soft motion",
                x = 0.55f,
                y = 0.35f,
                physics = Physics(radius = 0.28f),
            )
        )
        if (index == 0) {
            p.points[0].name = "Hair tips"
            p.points[0].x = 0.58f
            p.points[0].y = 0.52f
            p.points.add(
                MotionPoint(
                    name = "Fixed hair root",
                    x = 0.5f,
                    y = 0.27f,
                    fixed = true,
                    physics = Physics(radius = 0.09f),
                )
            )
        }
        val region = Region(name = if (index == 0) "Hair sway" else "Gentle selected motion")
        if (index == 0) {
            Masks.polygon(
                region.mask,
                listOf(.28f to .26f, .42f to .24f, .39f to .5f, .26f to .7f, .17f to .71f),
                1f,
            )
            p.points[0].x = .31f
            p.points[0].y = .51f
            p.points[0].physics.radius = .3f
        } else Masks.shape(region.mask, .15f, .15f, .85f, .7f, true, 1f)
        p.regions.add(region)
        p.points[0].region = region.id
        save(p)
        return p
    }
}
