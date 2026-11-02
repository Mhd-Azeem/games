package com.az.animewallpaper

import com.az.animewallpaper.models.*
import com.az.animewallpaper.physics.*
import com.az.animewallpaper.selection.Masks
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class MotionTests {
    @Test
    fun pointsHaveUniqueIdentifiers() {
        assertNotEquals(MotionPoint().id, MotionPoint().id)
    }

    @Test
    fun pointsMoveAndDeleteIndependently() {
        val p = Project()
        val a = MotionPoint()
        val b = MotionPoint()
        p.points.addAll(listOf(a, b))
        a.x = .7f
        a.y = .2f
        assertEquals(.5f, b.x, 0f)
        p.points.remove(a)
        assertEquals(listOf(b), p.points)
    }

    @Test
    fun independentIntensity() {
        val a = Spring()
        val b = Spring()
        repeat(300) {
            a.step(1f, 0f, .016f, Physics(intensity = .2f), Physics(intensity = 1f))
            b.step(1f, 0f, .016f, Physics(intensity = 1f), Physics(intensity = 1f))
        }
        assertTrue(abs(b.x) > abs(a.x) * 3)
    }

    @Test
    fun boundedUnderExtremeInput() {
        val s = Spring()
        val p = Physics(intensity = 2f, damping = 0f, stiffness = 0f, maxDisplacement = .1f)
        repeat(10000) {
            s.step(if (it % 2 == 0) 1000f else -1000f, 1000f, .05f, p, Physics(intensity = 2f))
        }
        assertTrue(s.x.isFinite())
        assertTrue(abs(s.x) <= .2f)
        assertTrue(abs(s.y) <= .2f)
    }

    @Test
    fun returnsToEquilibrium() {
        val s = Spring()
        val p = Physics()
        val g = Physics(intensity = 1f)
        repeat(100) { s.step(2f, 1f, .016f, p, g) }
        repeat(1000) { s.step(0f, 0f, .016f, p, g) }
        assertEquals(0f, s.x, .0001f)
        assertEquals(0f, s.y, .0001f)
    }

    @Test
    fun zeroIntensityRemainsStill() {
        val s = Spring()
        repeat(100) { s.step(2f, 2f, .016f, Physics(intensity = 0f), Physics()) }
        assertEquals(0f, s.x, 0f)
    }

    @Test
    fun directionConstraint() {
        val s = Spring()
        repeat(100) { s.step(2f, 2f, .016f, Physics(direction = 1), Physics()) }
        assertEquals(0f, s.y, 0f)
        assertTrue(s.x > 0f)
    }

    @Test
    fun delayDefersResponse() {
        val s = Spring()
        repeat(8) { s.step(1f, 0f, .016f, Physics(delay = 300), Physics()) }
        assertEquals(0f, s.x, 0f)
    }

    @Test
    fun brushPaintsLocally() {
        val m = FloatArray(MASK_SIZE * MASK_SIZE)
        Masks.brush(m, .5f, .5f, .1f, .5f, 1f, false)
        assertTrue(Masks.sample(m, .5f, .5f) > .9f)
        assertEquals(0f, Masks.sample(m, .1f, .1f), 0f)
    }

    @Test
    fun eraseRemovesPaint() {
        val m = FloatArray(MASK_SIZE * MASK_SIZE) { 1f }
        Masks.brush(m, .5f, .5f, .15f, 1f, 1f, true)
        assertEquals(0f, Masks.sample(m, .5f, .5f), 0f)
        assertEquals(1f, m[0], 0f)
    }

    @Test
    fun lassoClosesAutomatically() {
        val m = FloatArray(MASK_SIZE * MASK_SIZE)
        Masks.polygon(m, listOf(.2f to .2f, .8f to .2f, .5f to .8f), 1f)
        assertTrue(Masks.sample(m, .5f, .4f) > .9f)
        assertEquals(0f, m[0], 0f)
    }

    @Test
    fun invalidPolygonDoesNothing() {
        val m = FloatArray(MASK_SIZE * MASK_SIZE)
        Masks.polygon(m, listOf(.5f to .5f), 1f)
        assertTrue(m.all { it == 0f })
    }

    @Test
    fun rectangleSelection() {
        val m = FloatArray(MASK_SIZE * MASK_SIZE)
        Masks.shape(m, .2f, .2f, .8f, .8f, false, 1f)
        assertEquals(1f, Masks.sample(m, .5f, .5f), 0f)
        assertEquals(0f, m[0], 0f)
    }

    @Test
    fun ellipseSelection() {
        val m = FloatArray(MASK_SIZE * MASK_SIZE)
        Masks.shape(m, .2f, .2f, .8f, .8f, true, 1f)
        assertTrue(Masks.sample(m, .5f, .5f) > .9f)
        assertEquals(0f, Masks.sample(m, .21f, .21f), 0f)
    }

    @Test
    fun strokeHasNoGaps() {
        val m = FloatArray(MASK_SIZE * MASK_SIZE)
        Masks.stroke(m, .1f, .5f, .9f, .5f, .03f, 1f, 1f, false)
        for (i in 1..9) assertTrue(Masks.sample(m, i / 10f, .5f) > .9f)
    }

    @Test
    fun featherIsBounded() {
        val m = FloatArray(MASK_SIZE * MASK_SIZE)
        Masks.shape(m, .2f, .2f, .8f, .8f, false, 1f)
        val f = Masks.feather(m, 5)
        assertTrue(f.all { it in 0f..1f })
        assertTrue(f.any { it > 0 && it < 1 })
        assertEquals(1f, Masks.sample(f, .5f, .5f), .001f)
    }

    @Test
    fun transformMovesMask() {
        val m = FloatArray(MASK_SIZE * MASK_SIZE)
        Masks.brush(m, .3f, .3f, .05f, 1f, 1f, false)
        val t = Masks.transform(m, .2f, .2f, 1f, 0f)
        assertTrue(Masks.sample(t, .5f, .5f) > .9f)
        assertEquals(0f, Masks.sample(t, .3f, .3f), 0f)
    }

    @Test
    fun wandDoesNotCrossDifferentColor() {
        val pixels = IntArray(16) { if (it % 4 < 2) 0xff000000.toInt() else 0xffffffff.toInt() }
        val m = FloatArray(MASK_SIZE * MASK_SIZE)
        Masks.wand(m, pixels, 4, 4, 0f, 0f, 0)
        assertTrue(m[0] > 0)
        assertEquals(0f, m[127], 0f)
    }

    @Test
    fun radialInfluenceStopsAtRadius() {
        val p = MotionPoint(x = .5f, y = .5f, physics = Physics(radius = .1f))
        assertEquals(1f, Influence.radial(.5f, .5f, p), 0f)
        assertEquals(0f, Influence.radial(.7f, .5f, p), 0f)
    }

    @Test
    fun stationarySensorDoesNotDrift() {
        val f = SensorFilter()
        repeat(100) {
            val (x, y) = f.update(floatArrayOf(0f, 9.81f, 0f), 0)
            assertEquals(0f, x, .001f)
            assertEquals(0f, y, .001f)
        }
    }

    @Test
    fun sensorRotationMapsAxes() {
        val a = SensorFilter()
        val b = SensorFilter()
        a.update(floatArrayOf(0f, 9.81f, 0f), 0)
        b.update(floatArrayOf(0f, 9.81f, 0f), 1)
        val x = a.update(floatArrayOf(2f, 9.81f, 0f), 0)
        val y = b.update(floatArrayOf(2f, 9.81f, 0f), 1)
        assertEquals(x.first, -y.second, .001f)
    }
}
