package com.az.animewallpaper
import android.app.Activity
import android.app.WallpaperManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
class MainActivity: Activity() {
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
 val layout=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(36,60,36,36) }
 layout.addView(TextView(this).apply { text="AZ Anime Motion Wallpaper\nShake to move hair and clothing.\nReplace placeholder artwork with layered PNGs for full effect."; textSize=19f })
 layout.addView(Button(this).apply { text="Set Live Wallpaper"; setOnClickListener { startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,android.content.ComponentName(this@MainActivity,MotionWallpaperService::class.java))) } })
 setContentView(layout)
 }
}
