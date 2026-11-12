package com.custom.pixelflixtv

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private lateinit var fullscreenContainer: FrameLayout

    // Virtual Mouse Cursor variables
    private lateinit var cursorView: View
    private var cursorMode = false
    private var cursorX = 500f
    private var cursorY = 300f
    private val cursorStep = 35f

    private val blockedHosts = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adnxs.com", "popads.net", "popcash.net", "propellerads.com",
        "exoclick.com", "juicyads.com", "trafficjunky.net", "mgid.com",
        "taboola.com", "outbrain.com", "adsterra.com", "hilltopads.net"
    )

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.setBackgroundColor(Color.BLACK)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        fullscreenContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        fullscreenContainer.addView(webView)

        setupVirtualCursor()
        setContentView(fullscreenContainer)

        enableImmersiveFullscreen()
        setupWebView()
        webView.loadUrl("https://www.pixelflix.cc")
    }

    private fun setupVirtualCursor() {
        val size = 28
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#FFFF5722"))
            setStroke(4, Color.WHITE)
        }
        cursorView = View(this).apply {
            background = shape
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.START)
        }
        fullscreenContainer.addView(cursorView)
    }

    private fun toggleCursorMode() {
        cursorMode = !cursorMode
        if (cursorMode) {
            cursorX = (fullscreenContainer.width / 2f).coerceAtLeast(100f)
            cursorY = (fullscreenContainer.height / 2f).coerceAtLeast(100f)
            updateCursorPosition()
            cursorView.visibility = View.VISIBLE
            cursorView.bringToFront()
            Toast.makeText(this, "Mouse Mode ON (Menu/Play to exit)", Toast.LENGTH_SHORT).show()
        } else {
            cursorView.visibility = View.GONE
            Toast.makeText(this, "Mouse Mode OFF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateCursorPosition() {
        val maxX = (fullscreenContainer.width - 28).coerceAtLeast(0).toFloat()
        val maxY = (fullscreenContainer.height - 28).coerceAtLeast(0).toFloat()
        cursorX = cursorX.coerceIn(0f, maxX)
        cursorY = cursorY.coerceIn(0f, maxY)
        cursorView.translationX = cursorX
        cursorView.translationY = cursorY
    }

    private fun performCursorClick() {
        val targetView: View = customView ?: webView
        val downTime = SystemClock.uptimeMillis()
        val downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, cursorX + 14f, cursorY + 14f, 0)
        val upEvent = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_UP, cursorX + 14f, cursorY + 14f, 0)
        targetView.dispatchTouchEvent(downEvent)
        targetView.dispatchTouchEvent(upEvent)
        downEvent.recycle()
        upEvent.recycle()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableImmersiveFullscreen()
        }
    }

    private fun enableImmersiveFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.setSupportMultipleWindows(false)
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val host = request?.url?.host?.lowercase() ?: ""
                if (blockedHosts.any { host.contains(it) }) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val host = request?.url?.host?.lowercase() ?: ""
                if (blockedHosts.any { host.contains(it) }) {
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val hideAdsJs = """
                    (function() {
                        var style = document.createElement('style');
                        style.innerHTML = 'html, body { margin: 0 !important; padding: 0 !important; background-color: #000 !important; } iframe[src*="ads"], .adsbygoogle, [id*="ad-"], [class*="popup"], [class*="banner-ad"] { display: none !important; }';
                        document.head.appendChild(style);
                    })();
                """.trimIndent()
                view?.evaluateJavascript(hideAdsJs, null)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                webView.visibility = View.GONE
                fullscreenContainer.addView(customView)
                if (cursorMode) cursorView.bringToFront()
                enableImmersiveFullscreen()
            }

            override fun onHideCustomView() {
                exitFullscreen()
            }
        }
    }

    private fun exitFullscreen() {
        if (customView == null) return
        fullscreenContainer.removeView(customView)
        customView = null
        webView.visibility = View.VISIBLE
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        enableImmersiveFullscreen()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Press Menu, Info, or Media Play/Pause on your TV remote to toggle the mouse pointer
        if (keyCode == KeyEvent.KEYCODE_MENU ||
            keyCode == KeyEvent.KEYCODE_INFO ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        ) {
            toggleCursorMode()
            return true
        }

        if (cursorMode) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    cursorY -= cursorStep
                    updateCursorPosition()
                    if (cursorY <= 20f) webView.scrollBy(0, -120)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    cursorY += cursorStep
                    updateCursorPosition()
                    if (cursorY >= fullscreenContainer.height - 60f) webView.scrollBy(0, 120)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    cursorX -= cursorStep
                    updateCursorPosition()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    cursorX += cursorStep
                    updateCursorPosition()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    performCursorClick()
                    return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    toggleCursorMode()
                    return true
                }
            }
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (customView != null) {
                exitFullscreen()
                return true
            }
            if (webView.canGoBack()) {
                webView.goBack()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
