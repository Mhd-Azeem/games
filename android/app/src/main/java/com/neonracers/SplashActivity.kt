package com.neonracers

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        enableImmersive()

        // Build splash layout programmatically (no XML dependency)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(0xFF050515.toInt())
        }

        val title = TextView(this).apply {
            text = "NEON RACER"
            textSize = 42f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(0xFF00F0FF.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 8)
            // Glow via shadow layer
            setShadowLayer(20f, 0f, 0f, 0xFF00F0FF.toInt())
        }

        val subtitle = TextView(this).apply {
            text = "TURBO EDITION"
            textSize = 14f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(0xFFFF00FF.toInt())
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.3f
            setPadding(0, 0, 0, 48)
        }

        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                resources.displayMetrics.widthPixels / 2,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = android.view.Gravity.CENTER_HORIZONTAL }
            max = 100
            progressDrawable?.setColorFilter(
                0xFF00F0FF.toInt(),
                android.graphics.PorterDuff.Mode.SRC_IN
            )
        }

        val loadText = TextView(this).apply {
            text = "LOADING…"
            textSize = 10f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(0x88C8E0FF.toInt())
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.15f
            setPadding(0, 12, 0, 0)
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(progress)
        root.addView(loadText)
        setContentView(root)

        // Animate progress, then launch MainActivity
        val handler = Handler(Looper.getMainLooper())
        val totalMs = 1500L
        val steps = 30
        val stepMs = totalMs / steps

        for (i in 0..steps) {
            handler.postDelayed({
                val pct = (i * 100 / steps)
                progress.progress = pct
                loadText.text = when {
                    pct < 30 -> "INITIALIZING…"
                    pct < 60 -> "LOADING ASSETS…"
                    pct < 90 -> "BUILDING TRACKS…"
                    else -> "READY!"
                }
            }, i * stepMs)
        }

        handler.postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }, totalMs + 200)
    }

    private fun enableImmersive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
    }
}
