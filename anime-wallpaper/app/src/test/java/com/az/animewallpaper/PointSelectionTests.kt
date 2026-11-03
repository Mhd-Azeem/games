package com.az.animewallpaper

import com.az.animewallpaper.editor.PointSelection
import com.az.animewallpaper.models.*
import org.junit.Assert.*
import org.junit.Test

class PointSelectionTests {
    @Test
    fun togglingKeepsMultiplePointsIndependent() {
        val a = MotionPoint(physics = Physics(intensity = .2f))
        val b = MotionPoint(physics = Physics(intensity = 1.4f))
        val s = PointSelection()
        s.toggle(a.id)
        s.toggle(b.id)
        assertEquals(setOf(a.id, b.id), s.selected)
        s.toggle(a.id)
        assertEquals(setOf(b.id), s.selected)
        assertEquals(.2f, a.physics.intensity, 0f)
        assertEquals(1.4f, b.physics.intensity, 0f)
    }

    @Test
    fun draggingGroupPreservesSpacingAndUnselectedPoints() {
        val a = MotionPoint(x = .2f, y = .3f)
        val b = MotionPoint(x = .6f, y = .4f)
        val c = MotionPoint(x = .4f, y = .8f)
        val points = listOf(a, b, c)
        val s = PointSelection().apply { replace(listOf(a.id, b.id)) }
        s.move(points, s.origins(points), .1f, .2f)
        assertEquals(.3f, a.x, .0001f)
        assertEquals(.7f, b.x, .0001f)
        assertEquals(.4f, b.x - a.x, .0001f)
        assertEquals(.4f, c.x, 0f)
        assertEquals(.8f, c.y, 0f)
    }

    @Test
    fun groupClampsAtEdgeWithoutSquashing() {
        val a = MotionPoint(x = .2f, y = .1f)
        val b = MotionPoint(x = .8f, y = .8f)
        val p = listOf(a, b)
        val s = PointSelection().apply { all(p) }
        val origins = s.origins(p)
        s.move(p, origins, 4f, -4f)
        assertEquals(.4f, a.x, .0001f)
        assertEquals(1f, b.x, .0001f)
        assertEquals(0f, a.y, .0001f)
        assertEquals(.7f, b.y, .0001f)
        s.move(p, origins, 0f, 0f)
        assertEquals(.2f, a.x, 0f)
        assertEquals(.8f, b.x, 0f)
    }

    @Test
    fun removeDeletesOnlySelectedAndPreservesRegions() {
        val p = Project()
        val a = MotionPoint()
        val b = MotionPoint()
        val c = MotionPoint(fixed = true)
        p.points.addAll(listOf(a, b, c))
        p.regions.add(Region())
        val s = PointSelection().apply { replace(listOf(a.id, b.id)) }
        assertEquals(2, s.remove(p))
        assertEquals(listOf(c), p.points)
        assertEquals(1, p.regions.size)
        assertTrue(s.selected.isEmpty())
    }

    @Test
    fun selectionCanPruneRestoreAndSelectAllIncludingAnchors() {
        val a = MotionPoint()
        val b = MotionPoint(fixed = true)
        val s = PointSelection()
        s.all(listOf(a, b))
        assertEquals(2, s.selected.size)
        s.replace(s.selected)
        assertEquals(2, s.selected.size)
        s.prune(listOf(b))
        assertEquals(setOf(b.id), s.selected)
        s.single(a.id)
        assertEquals(setOf(a.id), s.selected)
        s.clear()
        assertTrue(s.selected.isEmpty())
    }
}
