package com.az.animewallpaper.sensors

import android.content.Context
import android.hardware.*
import android.view.WindowManager
import com.az.animewallpaper.physics.SensorFilter

class MotionSensors(context: Context, val output: (Float, Float) -> Unit) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val window = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var filter = SensorFilter()
    val available
        get() = sensor != null

    fun start(battery: Boolean = false) {
        stop()
        filter = SensorFilter()
        sensor?.let {
            manager.registerListener(
                this,
                it,
                if (battery) SensorManager.SENSOR_DELAY_UI else SensorManager.SENSOR_DELAY_GAME,
            )
        }
    }

    fun stop() {
        manager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y) = filter.update(event.values, window.defaultDisplay.rotation)
        output(x, y)
    }
}
