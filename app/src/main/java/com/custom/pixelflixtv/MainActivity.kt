package com.custom.pixelflixtv

import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private lateinit var fullscreenContainer: FrameLayout

    private var cursorView: View? = null
    private var isTvDevice = false

    // TV Virtual Mouse Cursor state
    private var cursorActive = false
    private var cursorX = 960f
    private var cursorY = 540f
    private var moveStep = 18f
    private var lastMoveTime = 0L

    // TV Remote trigger state (Double-tap Back & Long-press OK)
    private var lastBackPressTime = 0L
    private var okLongPressHandled = false
    private val handler = Handler(Looper.getMainLooper())
    private val okLongPressRunnable = Runnable {
        okLongPressHandled = true
        toggleCursor()
    }

    // Streaming ad & pop-under domains (applied on TV)
    private val adDomains = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adnxs.com", "popads.net", "popcash.net", "propellerads.com",
        "exoclick.com", "juicyads.com", "trafficjunky.net", "adsterra.com",
        "highperformanceformat.com", "onclickalgo.com", "mc.yandex.ru",
        "histats.com", "acscdn.com", "amung.us", "adtng.com"
    )

    private fun detectAndroidTv(): Boolean {
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTvMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val hasLeanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val hasTvFeature = packageManager.hasSystemFeature("com.google.android.tv")
        val noTouchscreen = !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        return isTvMode || hasLeanback || hasTvFeature || noTouchscreen
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isTvDevice = detectAndroidTv()

        // Enable true edge-to-edge fullscreen on both Mobile and TV
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        val rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        fullscreenContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = true

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                // Block pop-up windows on both Mobile and TV
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false

                // TV-only: force Mobile User-Agent & disable wide-viewport desktop ads
                if (isTvDevice) {
                    userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/AP2A.240805.005; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/128.0.6613.127 Mobile Safari/537.36"
                    useWideViewPort = false
                    loadWithOverviewMode = true
                } else {
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (customView != null) {
                        callback?.onCustomViewHidden()
                        return
                    }
                    customView = view
                    customViewCallback = callback
                    fullscreenContainer.addView(
                        view,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    )
                    fullscreenContainer.visibility = View.VISIBLE
                    webView.visibility = View.GONE
                    hideSystemBars()
                }

                override fun onHideCustomView() {
                    if (customView == null) return
                    fullscreenContainer.removeView(customView)
                    fullscreenContainer.visibility = View.GONE
                    customView = null
                    customViewCallback?.onCustomViewHidden()
                    customViewCallback = null
                    webView.visibility = View.VISIBLE
                    hideSystemBars()
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    if (isTvDevice) {
                        val host = request?.url?.host?.lowercase()
                            ?: return super.shouldInterceptRequest(view, request)
                        if (adDomains.any { host == it || host.endsWith(".$it") }) {
                            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val host = request?.url?.host?.lowercase() ?: ""
                    if (adDomains.any { host == it || host.endsWith(".$it") }) {
                        return true
                    }
                    return false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (isTvDevice) {
                        val adCleanJs = """
                            (function() {
                                const style = document.createElement('style');
                                style.innerHTML = `
                                    iframe[src*="ads"], iframe[src*="pop"], div[id*="ad-"], div[class*="ad-"],
                                    div[class*="popup"], div[class*="banner"], a[target="_blank"][style*="fixed"],
                                    div[style*="z-index: 2147483647"], div[style*="z-index: 999999"] {
                                        display: none !important;
                                        visibility: hidden !important;
                                        pointer-events: none !important;
                                    }
                                `;
                                document.head.appendChild(style);

                                setInterval(() => {
                                    document.querySelectorAll('a[target="_blank"]').forEach(el => {
                                        if (!el.href.includes('pixelflix')) el.remove();
                                    });
                                }, 1500);
                            })();
                        """.trimIndent()
                        view?.evaluateJavascript(adCleanJs, null)
                    }
                }
            }
        }

        rootLayout.addView(webView)
        rootLayout.addView(fullscreenContainer)

        // Only attach the virtual mouse cursor when running on a TV / TV box
        if (isTvDevice) {
            val cursorSize = 28
            val cursor = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(cursorSize, cursorSize)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#FF6D00"))
                    setStroke(3, Color.WHITE)
                }
                elevation = 20f
                visibility = View.GONE
            }
            cursorView = cursor
            rootLayout.addView(cursor)

            rootLayout.post {
                cursorX = rootLayout.width / 2f
                cursorY = rootLayout.height / 2f
                updateCursorPosition()
            }
        }

        setContentView(rootLayout)
        webView.loadUrl("https://pixelflix.cc")
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // On phones/tablets, keep your exact original Back and touch behavior untouched
        if (!isTvDevice) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                if (customView != null) {
                    webView.webChromeClient?.onHideCustomView()
                    return true
                }
                if (webView.canGoBack()) {
                    webView.goBack()
                    return true
                }
            }
            return super.dispatchKeyEvent(event)
        }

        val keyCode = event.keyCode

        // 1. TV: Toggle cursor via Menu (≡) or Info button
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                toggleCursor()
            }
            return true
        }

        // 2. TV: Toggle cursor via Long-Press Center OK (600ms) OR click when cursor is active
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (event.repeatCount == 0) {
                    okLongPressHandled = false
                    handler.postDelayed(okLongPressRunnable, 600L)
                }
                return true
            } else if (event.action == KeyEvent.ACTION_UP) {
                handler.removeCallbacks(okLongPressRunnable)
                if (!okLongPressHandled) {
                    if (cursorActive) {
                        simulateTap(cursorX, cursorY)
                        return true
                    } else {
                        return super.dispatchKeyEvent(event)
                    }
                }
                return true
            }
        }

        // 3. TV: Double-tap Back (←) within 450ms to toggle cursor; single-tap exits fullscreen or goes back
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val now = SystemClock.uptimeMillis()
                if (now - lastBackPressTime < 450L) {
                    lastBackPressTime = 0L
                    toggleCursor()
                    return true
                }
                lastBackPressTime = now

                if (cursorActive) {
                    toggleCursor()
                    return true
                } else if (customView != null) {
                    webView.webChromeClient?.onHideCustomView()
                    return true
                } else if (webView.canGoBack()) {
                    webView.goBack()
                    return true
                }
            }
            return super.dispatchKeyEvent(event)
        }

        // 4. TV: Move cursor with D-pad arrows when active
        if (cursorActive) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        moveCursor(keyCode)
                    }
                    return true
                }
            }
        }

        return super.dispatchKeyEvent(event)
    }

    private fun toggleCursor() {
        val cursor = cursorView ?: return
        cursorActive = !cursorActive
        if (cursorActive) {
            cursor.visibility = View.VISIBLE
            updateCursorPosition()
            Toast.makeText(this, "Cursor ON (OK to click, Back to exit)", Toast.LENGTH_SHORT).show()
        } else {
            cursor.visibility = View.GONE
            Toast.makeText(this, "Cursor OFF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun moveCursor(keyCode: Int) {
        val cursor = cursorView ?: return
        val now = SystemClock.uptimeMillis()
        moveStep = if (now - lastMoveTime < 120L) {
            (moveStep + 4f).coerceAtMost(48f)
        } else {
            18f
        }
        lastMoveTime = now

        val maxWidth = (webView.width - cursor.width).coerceAtLeast(0).toFloat()
        val maxHeight = (webView.height - cursor.height).coerceAtLeast(0).toFloat()

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> cursorY = (cursorY - moveStep).coerceIn(0f, maxHeight)
            KeyEvent.KEYCODE_DPAD_DOWN -> cursorY = (cursorY + moveStep).coerceIn(0f, maxHeight)
            KeyEvent.KEYCODE_DPAD_LEFT -> cursorX = (cursorX - moveStep).coerceIn(0f, maxWidth)
            KeyEvent.KEYCODE_DPAD_RIGHT -> cursorX = (cursorX + moveStep).coerceIn(0f, maxWidth)
        }
        updateCursorPosition()
    }

    private fun updateCursorPosition() {
        val cursor = cursorView ?: return
        cursor.x = cursorX
        cursor.y = cursorY
    }

    private fun simulateTap(x: Float, y: Float) {
        val cursor = cursorView ?: return
        val downTime = SystemClock.uptimeMillis()
        val eventTime = SystemClock.uptimeMillis() + 20
        val centerX = x + (cursor.width / 2f)
        val centerY = y + (cursor.height / 2f)

        val downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, centerX, centerY, 0)
        val upEvent = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_UP, centerX, centerY, 0)

        val targetView: View = if (fullscreenContainer.visibility == View.VISIBLE) fullscreenContainer else webView
        targetView.dispatchTouchEvent(downEvent)
        targetView.dispatchTouchEvent(upEvent)

        downEvent.recycle()
        upEvent.recycle()
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }
}
