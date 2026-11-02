package com.az.animewallpaper

import com.az.animewallpaper.models.*
import com.az.animewallpaper.rendering.ImageFrame
import com.az.animewallpaper.storage.Codec
import org.junit.Assert.*
import org.junit.Test

class ProjectTests {
    @Test
    fun roundTripKeepsAllEditorData() {
        val p =
            Project(
                name = "Saved project",
                image = "private/image",
                zoom = 1.3f,
                offsetX = .2f,
                rotation = 90,
            )
        val r = Region(name = "Hair")
        r.mask[200] = .7f
        r.vertices.add(.2f to .3f)
        p.regions.add(r)
        p.points.add(
            MotionPoint(
                name = "Tips",
                region = r.id,
                physics = Physics(intensity = 1.23f, delay = 250),
            )
        )
        val copy = Codec.decode(Codec.encode(p))
        assertEquals(p.id, copy.id)
        assertEquals(p.name, copy.name)
        assertEquals(p.image, copy.image)
        assertEquals(90, copy.rotation)
        assertEquals(1.3f, copy.zoom, 0f)
        assertEquals(1.23f, copy.points[0].physics.intensity, .001f)
        assertEquals(r.id, copy.points[0].region)
        assertEquals(.7f, copy.regions[0].mask[200], .005f)
        assertEquals(.2f, copy.regions[0].vertices[0].first, 0f)
    }

    @Test
    fun pointEditingDoesNotMutateOtherPoints() {
        val p = Project()
        p.points.add(MotionPoint())
        p.points.add(MotionPoint())
        val saved = Codec.encode(p)
        p.points[0].x = .9f
        p.points.removeAt(1)
        val restored = Codec.decode(saved)
        assertEquals(2, restored.points.size)
        assertEquals(.5f, restored.points[0].x, 0f)
    }

    @Test
    fun sourceReferenceAndLayerOrderingSurvive() {
        val p = Project(image = "/private/source")
        p.regions.add(Region(name = "Lower"))
        p.regions.add(Region(name = "Upper"))
        assertEquals(
            listOf("Lower", "Upper"),
            Codec.decode(Codec.encode(p)).regions.map { it.name },
        )
    }

    @Test
    fun wallpaperConfigurationUsesSameCodec() {
        val p = Project(name = "Applied", fps = 20, quality = 32)
        p.points.add(MotionPoint(fixed = true))
        val c = Codec.decode(Codec.encode(p))
        assertEquals(20, c.fps)
        assertEquals(32, c.quality)
        assertTrue(c.points[0].fixed)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsFutureVersion() {
        Codec.decode(Codec.encode(Project()).replace("\"version\":1", "\"version\":500"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTraversalId() {
        Codec.decode(Codec.encode(Project(id = "../../bad")))
    }

    @Test
    fun canvasMappingRoundTripsAfterCropAndRotation() {
        val p = Project(zoom = 1.5f, offsetX = .2f, offsetY = -.1f, rotation = 90)
        val f = ImageFrame(1080f, 1920f, 720f, 1280f, p)
        val s = f.screen(.3f, .7f)
        val v = f.image(s.first, s.second)
        assertEquals(.3f, v.first, .00001f)
        assertEquals(.7f, v.second, .00001f)
    }

    @Test
    fun fitMaintainsImageProportions() {
        val f = ImageFrame(100f, 100f, 200f, 100f, Project(fit = true))
        val a = f.screen(0f, 0f)
        val b = f.screen(1f, 1f)
        assertEquals(100f, b.first - a.first, .001f)
        assertEquals(50f, b.second - a.second, .001f)
    }
}
