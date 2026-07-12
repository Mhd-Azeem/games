package com.neonracers

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.*
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var isAtMainMenu = true

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Force landscape
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // Fullscreen immersive mode
        enableImmersiveMode()

        val container = FrameLayout(this)
        setContentView(container)

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                mediaPlaybackRequiresUserGesture = false
                setRenderPriority(WebSettings.RenderPriority.HIGH)
                cacheMode = WebSettings.LOAD_DEFAULT
                // Hardware acceleration
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                    android.util.Log.d("NeonRacer", "${msg.message()} (${msg.sourceId()}:${msg.lineNumber()})")
                    return true
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Inject Android bridge after page load
                    view?.evaluateJavascript("""
                        window.NeonRacer = {
                            exit: function() { Android.exitApp(); },
                            onMainMenu: function() { Android.onMainMenu(); },
                            onInGame: function() { Android.onInGame(); }
                        };
                    """.trimIndent(), null)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    // Block all external navigation
                    return !request?.url.toString().startsWith("file://")
                }

                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    android.util.Log.e("NeonRacer", "WebView renderer gone (crashed=${detail?.didCrash()}), reloading")
                    if (view != null && !isFinishing && !isDestroyed) {
                        view.loadUrl("file:///android_asset/www/index.html")
                    }
                    return true
                }
            }

            addJavascriptInterface(AndroidBridge(), "Android")
        }

        container.addView(webView)
        webView.loadUrl("file:///android_asset/www/index.html")
    }

    private fun enableImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { ctrl ->
                ctrl.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                ctrl.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersiveMode()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (isAtMainMenu) {
                showExitDialog()
            } else {
                // Send back event to JS
                webView.evaluateJavascript(
                    "window.dispatchEvent(new Event('neonracer_back'));",
                    null
                )
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun showExitDialog() {
        AlertDialog.Builder(this)
            .setTitle("Exit Neon Racer?")
            .setMessage("Are you sure you want to exit?")
            .setPositiveButton("Exit") { _, _ -> finish() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
    }

    override fun onPause() {
        webView.pauseTimers()
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun exitApp() {
            runOnUiThread { finish() }
        }

        @JavascriptInterface
        fun onMainMenu() {
            runOnUiThread { isAtMainMenu = true }
        }

        @JavascriptInterface
        fun onInGame() {
            runOnUiThread { isAtMainMenu = false }
        }
    }
}
