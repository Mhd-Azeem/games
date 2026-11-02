package com.az.animewallpaper.models

import java.util.UUID

const val MASK_SIZE = 128

fun id() = UUID.randomUUID().toString()

data class Physics(
    var intensity: Float = 0.65f,
    var sensitivity: Float = 1f,
    var stiffness: Float = 40f,
    var damping: Float = 55f,
    var radius: Float = 0.15f,
    var maxDisplacement: Float = 0.045f,
    var horizontal: Float = 1f,
    var vertical: Float = 1f,
    var delay: Int = 0,
    var feather: Float = 0.4f,
    var direction: Int = 0,
)

data class MotionPoint(
    val id: String = id(),
    var name: String = "Movement point",
    var x: Float = 0.5f,
    var y: Float = 0.5f,
    var enabled: Boolean = true,
    var fixed: Boolean = false,
    var region: String = "",
    var group: String = "",
    var physics: Physics = Physics(),
)

data class Region(
    val id: String = id(),
    var name: String = "Region",
    var enabled: Boolean = true,
    var locked: Boolean = false,
    var group: String = "",
    var physics: Physics = Physics(),
    var mask: FloatArray = FloatArray(MASK_SIZE * MASK_SIZE),
    var vertices: MutableList<Pair<Float, Float>> = mutableListOf(),
)

data class SpatialSettings(
    var enabled: Boolean = false,
    var kind: String = "none",
    var strength: Float = 0.7f,
    var smoothing: Float = 0.7f,
    var depth: FloatArray = FloatArray(MASK_SIZE * MASK_SIZE),
    var strong: Boolean = false,
) {
    fun cycle() {
        when {
            !enabled -> { enabled = true; strong = false }
            !strong -> strong = true
            else -> { enabled = false; strong = false }
        }
    }
    val motionGain: Float get() = if (enabled && strong) 3.2f else 1.8f
}

data class Project(
    val id: String = id(),
    var name: String = "Untitled wallpaper",
    var image: String = "",
    var created: Long = System.currentTimeMillis(),
    var modified: Long = created,
    var points: MutableList<MotionPoint> = mutableListOf(),
    var regions: MutableList<Region> = mutableListOf(),
    var global: Physics = Physics(intensity = 1f),
    var zoom: Float = 1f,
    var offsetX: Float = 0f,
    var offsetY: Float = 0f,
    var rotation: Int = 0,
    var fit: Boolean = false,
    var fps: Int = 30,
    var quality: Int = 64,
    var overlay: Float = 0.4f,
    var spatial: SpatialSettings = SpatialSettings(),
)
