package com.az.animewallpaper

import com.az.animewallpaper.models.*
import com.az.animewallpaper.spatial.SpatialMotion
import com.az.animewallpaper.storage.Codec
import kotlin.math.abs
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SpatialTests {
    @Test fun threeTapCyclePreservesStrengthAndSavedMode() {
        val p=Project();p.spatial.kind="subject";p.spatial.strength=1.1f
        p.spatial.cycle();assertTrue(p.spatial.enabled);assertFalse(p.spatial.strong)
        val normal=p.spatial.motionGain
        p.spatial.cycle();assertTrue(p.spatial.enabled);assertTrue(p.spatial.motionGain>normal)
        assertTrue(Codec.decode(Codec.encode(p)).spatial.strong)
        p.spatial.cycle();assertFalse(p.spatial.enabled)
        p.spatial.cycle();assertFalse(p.spatial.strong);assertEquals(1.1f,p.spatial.strength,.001f)
    }

    @Test
    fun migrationAndArchivePreserveAnalysis() {
        val p = Project()
        p.spatial =
            SpatialSettings(true, "subject", depth = FloatArray(128 * 128) { it % 128 / 127f })
        val copy = Codec.decode(Codec.encode(p))
        assertTrue(copy.spatial.enabled)
        assertEquals("subject", copy.spatial.kind)
        assertArrayEquals(p.spatial.depth, copy.spatial.depth, .004f)
        val old = JSONObject(Codec.encode(p))
        old.put("version", 1)
        old.remove("spatial")
        assertFalse(Codec.decode(old.toString()).spatial.enabled)
    }

    @Test
    fun boundedMotionSeparatesDepthAndSettles() {
        val m = SpatialMotion()
        val near = FloatArray(2)
        val far = FloatArray(2)
        m.transform(.2f, .8f, .7f, 1.5f, .7f, near)
        assertEquals(.2f, near[0], .00001f)
        assertEquals(.8f, near[1], .00001f)
        repeat(10000) { m.step(100f, -100f, .05f, .7f) }
        m.transform(.5f, .5f, .7f, 1.5f, .7f, near)
        m.transform(.5f, .5f, -.18f, 1.5f, .7f, far)
        assertTrue(near[0] > far[0])
        assertTrue(near[1] > far[1])
        assertTrue(near.all { it.isFinite() && it in 0.4f..0.6f })
        repeat(400) { m.step(0f, 0f, .05f, .7f) }
        assertTrue(abs(m.x) + abs(m.y) < .00001f)
        m.step(Float.NaN, Float.POSITIVE_INFINITY, .05f, 1f)
        assertTrue(m.x.isFinite() && m.y.isFinite())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBadDepthMap() {
        val p = JSONObject(Codec.encode(Project()))
        p.getJSONObject("spatial").put("depth", "AA==")
        Codec.decode(p.toString())
    }
}
