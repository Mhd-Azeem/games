package com.az.animewallpaper

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {
    private val bg = Color.rgb(22, 23, 27)
    private val surface = Color.rgb(43, 45, 51)
    private val white = Color.rgb(239, 240, 242)
    private val muted = Color.rgb(171, 175, 183)
    private fun shape(color: Int, radius: Float = 26f) =
        GradientDrawable().apply { setColor(color); cornerRadius = radius * resources.displayMetrics.density }
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val density = resources.displayMetrics.density
        val prefs = getSharedPreferences("motion", MODE_PRIVATE)
        val scroll = ScrollView(this).apply { setBackgroundColor(bg); isFillViewport = true }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((22*density).toInt(), (32*density).toInt(), (22*density).toInt(), (28*density).toInt())
        }
        scroll.addView(root)
        root.addView(text("AZ / MOTION", 13f, muted, true))
        root.addView(text("Anime Wallpaper", 32f, white, true).apply { setPadding(0, (10*density).toInt(), 0, (6*density).toInt()) })
        root.addView(text("A responsive wallpaper that moves with your phone.", 15f, muted))
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(surface, 28f)
            setPadding((20*density).toInt(), (24*density).toInt(), (20*density).toInt(), (24*density).toInt())
        }
        val panelParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (28*density).toInt() }
        root.addView(panel, panelParams)
        panel.addView(text("MOTION CONTROLS", 13f, muted, true))
        fun slider(title: String, key: String, default: Int) {
            val label = text(title, 17f, white, true)
            label.setPadding(0, (23*density).toInt(), 0, (4*density).toInt())
            panel.addView(label)
            val value = text("", 13f, muted)
            panel.addView(value)
            panel.addView(SeekBar(this).apply {
                max = 100
                progress = prefs.getInt(key, default)
                value.text = "$progress%"
                setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        value.text = "$progress%"
                        if (fromUser) prefs.edit().putInt(key, progress).apply()
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            })
        }
        slider("Shake sensitivity", "sensitivity", 55)
        slider("Hair movement", "hair", 65)
        slider("Clothing movement", "clothing", 35)
        root.addView(text("Movement pauses when the wallpaper is not visible. Your sensor data stays on your device.", 13f, muted).apply {
            setPadding(0, (24*density).toInt(), 0, (20*density).toInt())
        })
        val button = Button(this).apply {
            text = "PREVIEW & APPLY WALLPAPER"
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.rgb(23,24,27))
            background = shape(Color.rgb(223,225,229), 22f)
            minHeight = (58*density).toInt()
            setOnClickListener {
                startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this@MainActivity, MotionWallpaperService::class.java)))
            }
        }
        root.addView(button, LinearLayout.LayoutParams(-1, (60*density).toInt()))
        setContentView(scroll)
    }
}
