package com.az.animewallpaper.storage

import android.content.Context
import com.az.animewallpaper.models.Project

/** Recents are a view of saved projects, never a destructive storage operation. */
class RecentProjects(context: Context) {
    private val prefs = context.getSharedPreferences("recent-projects", Context.MODE_PRIVATE)

    private fun hidden() = prefs.getStringSet("hidden", emptySet())!!.toMutableSet()

    fun hide(id: String) {
        prefs.edit().putStringSet("hidden", hidden().apply { add(id) }).apply()
    }

    fun recordOpen(id: String) {
        prefs
            .edit()
            .putStringSet("hidden", hidden().apply { remove(id) })
            .putLong("opened:$id", System.currentTimeMillis())
            .apply()
    }

    fun visible(projects: List<Project>): List<Project> {
        val hidden = hidden()
        return projects
            .filter { it.id !in hidden }
            .sortedByDescending { maxOf(it.modified, prefs.getLong("opened:${it.id}", 0)) }
    }

    fun hasHidden() = hidden().isNotEmpty()

    fun restore() {
        prefs.edit().remove("hidden").apply()
    }
}
