package com.az.animewallpaper.editor

import com.az.animewallpaper.models.MotionPoint
import com.az.animewallpaper.models.Project

/** Editing state only: selecting points never changes their saved physics. */
class PointSelection {
    private val ids = linkedSetOf<String>()
    val selected: Set<String>
        get() = ids

    val primary: String
        get() = ids.lastOrNull() ?: ""

    fun single(id: String) {
        ids.clear()
        if (id.isNotEmpty()) ids.add(id)
    }

    fun replace(values: Collection<String>) {
        val copy = values.filter { it.isNotEmpty() }
        ids.clear()
        ids.addAll(copy)
    }

    fun toggle(id: String) {
        if (!ids.remove(id)) ids.add(id)
    }

    fun clear() = ids.clear()

    fun all(points: List<MotionPoint>) = replace(points.map { it.id })

    fun prune(points: List<MotionPoint>) {
        ids.retainAll(points.map { it.id }.toSet())
    }

    fun origins(points: List<MotionPoint>): Map<String, Pair<Float, Float>> =
        points.filter { it.id in ids }.associate { it.id to (it.x to it.y) }

    fun move(
        points: List<MotionPoint>,
        origins: Map<String, Pair<Float, Float>>,
        dx: Float,
        dy: Float,
    ) {
        if (origins.isEmpty()) return
        // Clamp one shared delta; individual clamping would squash the group at an edge.
        val x =
            dx.coerceIn(-origins.values.minOf { it.first }, 1f - origins.values.maxOf { it.first })
        val y =
            dy.coerceIn(
                -origins.values.minOf { it.second },
                1f - origins.values.maxOf { it.second },
            )
        points.forEach { p ->
            origins[p.id]?.let {
                p.x = it.first + x
                p.y = it.second + y
            }
        }
    }

    fun remove(project: Project): Int {
        val before = project.points.size
        project.points.removeAll { it.id in ids }
        clear()
        return before - project.points.size
    }
}
