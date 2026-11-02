package com.az.animewallpaper
import android.graphics.*
import android.hardware.*
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.*
class MotionWallpaperService: WallpaperService() {
 override fun onCreateEngine(): Engine = MotionEngine()
 inner class MotionEngine: Engine(), SensorEventListener {
  private val handler=Handler(Looper.getMainLooper())
  private val sensorManager=getSystemService(SENSOR_SERVICE) as SensorManager
  private val sensor=sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
  private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
  private val prefs=getSharedPreferences("motion",MODE_PRIVATE)
  private val gravity=FloatArray(3)
  private var visible=false
  private var hair=0f; private var hairVelocity=0f
  private var clothing=0f; private var clothingVelocity=0f
  private var hairImage:Bitmap?=null; private var clothingImage:Bitmap?=null; private var background:Bitmap?=null
  private val frame=object:Runnable { override fun run() { if(!visible)return; update(); draw(); handler.postDelayed(this,33L) } }
  init {
   fun load(name:String):Bitmap? = try { assets.open(name).use { BitmapFactory.decodeStream(it) } } catch (_:Exception) { null }
   background=load("background.png"); hairImage=load("hair.png"); clothingImage=load("chest.png")
  }
  override fun onVisibilityChanged(isVisible:Boolean) { visible=isVisible; handler.removeCallbacks(frame); if(visible){sensor?.let{sensorManager.registerListener(this,it,SensorManager.SENSOR_DELAY_GAME)};handler.post(frame)} else sensorManager.unregisterListener(this) }
  override fun onDestroy() { visible=false;handler.removeCallbacks(frame);sensorManager.unregisterListener(this);background?.recycle();hairImage?.recycle();clothingImage?.recycle();super.onDestroy() }
  override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int){}
  override fun onSensorChanged(event:SensorEvent) {
   if(event.sensor.type!=Sensor.TYPE_ACCELEROMETER)return
   for(i in 0..2)gravity[i]=gravity[i]*0.85f+event.values[i]*0.15f
   val dx=(event.values[0]-gravity[0]).coerceIn(-12f,12f)
   val dy=(event.values[1]-gravity[1]).coerceIn(-12f,12f)
   val sensitivity=prefs.getInt("sensitivity",55)/55f
   val hairScale=prefs.getInt("hair",65)/65f
   val clothingScale=prefs.getInt("clothing",35)/35f
   hairVelocity+=(dx*0.09f+dy*0.015f)*sensitivity*hairScale
   clothingVelocity+=(dy*0.035f+dx*0.01f)*sensitivity*clothingScale
  }
  private fun update() {
   hairVelocity+=(-hair*0.11f-hairVelocity*0.20f)
   clothingVelocity+=(-clothing*0.18f-clothingVelocity*0.30f)
   hair=(hair+hairVelocity).coerceIn(-16f,16f)
   clothing=(clothing+clothingVelocity).coerceIn(-6f,6f)
  }
  private fun draw() {
   val holder=surfaceHolder
   var canvas:Canvas?=null
   try {
    canvas=holder.lockCanvas()?:return
    val c=canvas
    c.drawColor(Color.rgb(15,9,32))
    val w=c.width.toFloat(); val h=c.height.toFloat()
    val dst=RectF(0f,0f,w,h)
    background?.let { c.drawBitmap(it,null,dst,paint) } ?: run {
     paint.color=Color.rgb(35,20,60);c.drawRect(dst,paint)
     paint.color=Color.WHITE;paint.textSize=35f;c.drawText("Add background.png",25f,h/2,paint)
     paint.color=Color.WHITE
    }
    // Each transparent layer must match the full background dimensions.
    clothingImage?.let { c.drawBitmap(it,null,RectF(clothing*0.25f,clothing,w+clothing*0.25f,h+clothing),paint) }
    hairImage?.let { c.drawBitmap(it,null,RectF(hair,0f,w+hair,h),paint) }
   } catch (_:Exception) {} finally { if(canvas!=null)try{holder.unlockCanvasAndPost(canvas)}catch(_:Exception){} }
  }
 }
}
