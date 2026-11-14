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
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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

    private lateinit var rootLayout: FrameLayout
    private lateinit var webView: WebView
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private lateinit var fullscreenContainer: FrameLayout

    private var cursorView: View? = null
    private var isTvDevice = false

    private var cursorActive = false
    private var cursorX = 960f
    private var cursorY = 540f
    private var moveStep = 28f
    private var lastMoveTime = 0L

    private var lastBackPressTime = 0L
    private var okLongPressHandled = false
    private val handler = Handler(Looper.getMainLooper())
    private val okLongPressRunnable = Runnable {
        okLongPressHandled = true
        toggleCursor()
    }

    private val adKeywords = listOf(
        "doubleclick", "googlesyndication", "googleadservices", "adnxs",
        "popads", "popcash", "propellerads", "exoclick", "juicyads",
        "trafficjunky", "adsterra", "highperformanceformat", "onclickalgo",
        "mc.yandex", "histats", "acscdn", "amung.us", "adtng", "ad-maven",
        "admaven", "hilltopads", "clickadu", "pushground", "richpush",
        "evadav", "galaksion", "monetag", "usrpub", "clksite", "onclkds",
        "realwap", "tsyndicate", "realsrv", "bidgear", "mgid", "taboola",
        "outbrain", "revcontent", "criteo", "pubmatic", "rubiconproject",
        "openx", "smartadserver", "yandex", "scorecardresearch", "bet365",
        "1xbet", "mostbet", "parimatch", "melbet", "casino", "banner", "popunder"
    )

    private fun detectAndroidTv(): Boolean {
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTvMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val hasLeanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val hasTvFeature = packageManager.hasSystemFeature("com.google.android.tv")
        val noTouchscreen = !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        val isLargeLandscape = resources.configuration.screenWidthDp >= 960 &&
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return isTvMode || hasLeanback || hasTvFeature || noTouchscreen || isLargeLandscape
    }

    private fun isAdUrl(urlString: String?): Boolean {
        if (urlString.isNullOrBlank()) return false
        val lower = urlString.lowercase()
        return adKeywords.any { lower.contains(it) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        isTvDevice = detectAndroidTv()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        fullscreenContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
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
            keepScreenOn = true
            isFocusable = true
            isFocusableInTouchMode = true
            isVerticalScrollBarEnabled = true

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false

                userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/AP2A.240805.005; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/128.0.6613.127 Mobile Safari/537.36"
                useWideViewPort = !isTvDevice
                loadWithOverviewMode = true
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
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    hideSystemBars()

                    if (cursorActive) {
                        setCursorVisibility(false, showToast = false)
                    }
                    handler.postDelayed({
                        clearHoverAndFocusWithoutClicking()
                    }, 350L)
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
                    if (isAdUrl(request?.url?.toString())) {
                        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val urlStr = request?.url?.toString() ?: return false
                    if (isAdUrl(urlStr)) return true
                    if (request.isForMainFrame) {
                        val host = request.url?.host?.lowercase() ?: ""
                        if (!host.contains("pixelflix")) return true
                    }
                    return false
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    injectAdShield(view)
                    if (isTvDevice) injectTvHelpers(view)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    injectAdShield(view)
                    if (isTvDevice) injectTvHelpers(view)
                }
            }
        }

        rootLayout.addView(webView)
        rootLayout.addView(fullscreenContainer)

        if (isTvDevice) {
            ensureCursorAttached()
        }

        setContentView(rootLayout)
        webView.loadUrl("https://pixelflix.cc")
    }

    private fun ensureCursorAttached() {
        if (cursorView != null) return
        val cursorSize = 32
        val cursor = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(cursorSize, cursorSize)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FF6D00"))
                setStroke(3, Color.WHITE)
            }
            elevation = 30f
            visibility = View.GONE
        }
        cursorView = cursor
        rootLayout.addView(cursor)

        rootLayout.post {
            if (rootLayout.width > 0 && rootLayout.height > 0) {
                cursorX = rootLayout.width / 2f
                cursorY = rootLayout.height / 2f
                updateCursorPosition()
            }
        }
    }

    private fun activateTvModeIfNeeded() {
        if (!isTvDevice) {
            isTvDevice = true
            webView.settings.useWideViewPort = false
            ensureCursorAttached()
            injectTvHelpers(webView)
        } else {
            ensureCursorAttached()
        }
    }

    private fun injectAdShield(view: WebView?) {
        val adJs = """
            (function() {
                if (window.__pfAdShieldInstalled) return;
                window.__pfAdShieldInstalled = true;

                window.open = function() { return null; };

                const style = document.createElement('style');
                style.innerHTML = `
                    iframe[src*="ads"], iframe[src*="pop"], iframe[src*="banner"],
                    ins.adsbygoogle, [id^="ad_"], [id*="-ad-"], [class*="ad-banner"],
                    [class*="popup"], [class*="overlay-ad"], a[target="_blank"][style*="fixed"],
                    div[style*="z-index: 2147483647"], div[style*="z-index: 999999"],
                    div[style*="z-index:999999"], div[style*="position: fixed"][style*="bottom: 0"] {
                        display: none !important;
                        visibility: hidden !important;
                        height: 0 !important;
                        pointer-events: none !important;
                    }
                `;
                (document.head || document.documentElement).appendChild(style);

                setInterval(() => {
                    document.querySelectorAll('a[target="_blank"]').forEach(el => {
                        if (el.href && !el.href.includes('pixelflix')) el.remove();
                    });
                    document.querySelectorAll('div, ins').forEach(el => {
                        const cs = window.getComputedStyle(el);
                        if ((cs.position === 'fixed' || cs.position === 'absolute') && parseInt(cs.zIndex, 10) > 9000) {
                            if (!el.querySelector('video') && !el.closest('video')) el.remove();
                        }
                    });
                }, 1000);
            })();
        """.trimIndent()
        view?.evaluateJavascript(adJs, null)
    }

    private fun injectTvHelpers(view: WebView?) {
        val tvJs = """
            (function() {
                if (window.__pfTvHelpersInstalled) return;
                window.__pfTvHelpersInstalled = true;

                try {
                    Object.defineProperty(navigator, 'maxTouchPoints', { get: () => 5 });
                    window.ontouchstart = window.ontouchstart || null;
                } catch (e) {}

                const style = document.createElement('style');
                style.innerHTML = `
                    .__pf-tv-focus {
                        outline: 3px solid #FF6D00 !important;
                        outline-offset: 3px !important;
                        box-shadow: 0 0 14px rgba(255, 109, 0, 0.85) !important;
                        border-radius: 6px !important;
                        transition: transform 0.12s ease !important;
                    }
                    .__pf-hud-idle-hidden {
                        opacity: 0 !important;
                        transition: opacity 0.4s ease !important;
                    }
                `;
                (document.head || document.documentElement).appendChild(style);

                // Spatial D-pad focus engine for cursor-free remote navigation
                window.__pfCurrentFocus = null;

                function getFocusables() {
                    const selectors = 'a[href], button, input, select, textarea, [role="button"], [tabindex]:not([tabindex="-1"]), [onclick]';
                    const raw = Array.from(document.querySelectorAll(selectors));
                    return raw.filter(el => {
                        if (el.disabled || el.getAttribute('aria-hidden') === 'true') return false;
                        const cs = window.getComputedStyle(el);
                        if (cs.display === 'none' || cs.visibility === 'hidden' || parseFloat(cs.opacity) === 0) return false;
                        const r = el.getBoundingClientRect();
                        return r.width >= 16 && r.height >= 16;
                    });
                }

                window.__pfSetFocus = function(el) {
                    if (window.__pfCurrentFocus) {
                        window.__pfCurrentFocus.classList.remove('__pf-tv-focus');
                    }
                    window.__pfCurrentFocus = el;
                    if (el) {
                        el.classList.add('__pf-tv-focus');
                        try { el.focus({ preventScroll: true }); } catch (e) {}
                        el.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'nearest' });
                    }
                };

                window.__pfClearFocus = function() {
                    if (window.__pfCurrentFocus) {
                        window.__pfCurrentFocus.classList.remove('__pf-tv-focus');
                        window.__pfCurrentFocus = null;
                    }
                };

                window.__pfScrollSmart = function(deltaY) {
                    let scrolled = false;
                    document.querySelectorAll('div, main, section, .scrollable, #root').forEach(el => {
                        if (el.scrollHeight > el.clientHeight + 40) {
                            const cs = window.getComputedStyle(el);
                            if (cs.overflowY === 'auto' || cs.overflowY === 'scroll') {
                                const before = el.scrollTop;
                                el.scrollTop += deltaY;
                                if (el.scrollTop !== before) scrolled = true;
                            }
                        }
                    });
                    if (!scrolled) {
                        window.scrollBy({ top: deltaY, behavior: 'smooth' });
                    }
                };

                window.__pfMoveSpatialFocus = function(dir) {
                    const list = getFocusables();
                    if (!list.length) {
                        if (dir === 'DOWN') window.__pfScrollSmart(320);
                        if (dir === 'UP') window.__pfScrollSmart(-320);
                        return;
                    }

                    let curr = window.__pfCurrentFocus;
                    if (!curr || !document.body.contains(curr)) {
                        const visible = list.find(el => {
                            const r = el.getBoundingClientRect();
                            return r.top >= 0 && r.bottom <= window.innerHeight;
                        }) || list[0];
                        window.__pfSetFocus(visible);
                        return;
                    }

                    const cRect = curr.getBoundingClientRect();
                    const cx = cRect.left + cRect.width / 2;
                    const cy = cRect.top + cRect.height / 2;

                    let best = null;
                    let bestScore = Infinity;

                    for (const el of list) {
                        if (el === curr) continue;
                        const r = el.getBoundingClientRect();
                        const ex = r.left + r.width / 2;
                        const ey = r.top + r.height / 2;
                        const dx = ex - cx;
                        const dy = ey - cy;

                        let valid = false;
                        let primary = 0;
                        let secondary = 0;

                        if (dir === 'RIGHT' && dx > 10 && Math.abs(dy) < Math.max(cRect.height * 1.4, 90)) {
                            valid = true; primary = dx; secondary = Math.abs(dy) * 2.5;
                        } else if (dir === 'LEFT' && dx < -10 && Math.abs(dy) < Math.max(cRect.height * 1.4, 90)) {
                            valid = true; primary = -dx; secondary = Math.abs(dy) * 2.5;
                        } else if (dir === 'DOWN' && dy > 10) {
                            valid = true; primary = dy; secondary = Math.abs(dx) * 1.5;
                        } else if (dir === 'UP' && dy < -10) {
                            valid = true; primary = -dy; secondary = Math.abs(dx) * 1.5;
                        }

                        if (valid) {
                            const score = primary + secondary;
                            if (score < bestScore) {
                                bestScore = score;
                                best = el;
                            }
                        }
                    }

                    if (best) {
                        window.__pfSetFocus(best);
                    } else if (dir === 'DOWN') {
                        window.__pfScrollSmart(350);
                    } else if (dir === 'UP') {
                        window.__pfScrollSmart(-350);
                    }
                };

                window.__pfClickFocused = function() {
                    const el = window.__pfCurrentFocus || document.activeElement;
                    if (el && el !== document.body && el !== document.documentElement) {
                        el.click();
                        return true;
                    }
                    return false;
                };
            })();
        """.trimIndent()
        view?.evaluateJavascript(tvJs, null)
    }

    private fun clearHoverAndFocusWithoutClicking() {
        val targetView: View = if (fullscreenContainer.visibility == View.VISIBLE) fullscreenContainer else webView
        val now = SystemClock.uptimeMillis()

        val hoverExit = MotionEvent.obtain(now, now, MotionEvent.ACTION_HOVER_EXIT, 0f, 0f, 0).apply {
            source = InputDevice.SOURCE_MOUSE
        }
        targetView.dispatchGenericMotionEvent(hoverExit)
        hoverExit.recycle()

        val js = """
            (function() {
                if (window.__pfClearFocus) window.__pfClearFocus();
                function cleanDoc(doc) {
                    try {
                        if (doc.activeElement && doc.activeElement !== doc.body) {
                            doc.activeElement.blur();
                        }
                        doc.querySelectorAll('button, [tabindex], a, input').forEach(el => {
                            try { el.blur(); } catch (e) {}
                        });
                        doc.querySelectorAll('video, [class*="player"], [class*="control"], [class*="overlay"], [id*="player"]').forEach(el => {
                            ['mouseleave', 'mouseout', 'pointerleave', 'pointerout'].forEach(evt => {
                                el.dispatchEvent(new MouseEvent(evt, { bubbles: true }));
                                if (window.PointerEvent) {
                                    el.dispatchEvent(new PointerEvent(evt, { bubbles: true }));
                                }
                            });
                        });

                        const video = doc.querySelector('video');
                        if (video && !doc.__pfIdleWatcherInstalled) {
                            doc.__pfIdleWatcherInstalled = true;
                            let idleTimer = null;

                            const hideOverlays = () => {
                                if (video.paused || window.__pfCursorIsOn) return;
                                const vRect = video.getBoundingClientRect();
                                if (vRect.width < 300) return;
                                doc.querySelectorAll('div, header, footer, nav').forEach(el => {
                                    if (el === video || el.contains(video)) return;
                                    const cs = window.getComputedStyle(el);
                                    const r = el.getBoundingClientRect();
                                    const isOverlay = (cs.position === 'absolute' || cs.position === 'fixed') &&
                                        r.width > vRect.width * 0.5 && r.height > 24 && r.height < vRect.height * 0.45;
                                    if (isOverlay) {
                                        el.classList.add('__pf-hud-idle-hidden');
                                    }
                                });
                            };

                            const showOverlays = () => {
                                doc.querySelectorAll('.__pf-hud-idle-hidden').forEach(el => {
                                    el.classList.remove('__pf-hud-idle-hidden');
                                });
                                clearTimeout(idleTimer);
                                idleTimer = setTimeout(hideOverlays, 3500);
                            };

                            window.__pfShowPlayerHud = showOverlays;
                            window.__pfHidePlayerHud = hideOverlays;

                            ['pointerdown', 'keydown', 'touchstart'].forEach(ev => {
                                doc.addEventListener(ev, showOverlays, true);
                            });
                            video.addEventListener('pause', showOverlays);
                            video.addEventListener('play', () => {
                                clearTimeout(idleTimer);
                                idleTimer = setTimeout(hideOverlays, 3500);
                            });
                            idleTimer = setTimeout(hideOverlays, 3500);
                        } else if (doc.defaultView && doc.defaultView.__pfHidePlayerHud) {
                            setTimeout(doc.defaultView.__pfHidePlayerHud, 3500);
                        }
                    } catch (e) {}
                }

                window.__pfCursorIsOn = ${cursorActive};
                cleanDoc(document);
                document.querySelectorAll('iframe').forEach(frame => {
                    try {
                        if (frame.contentDocument) {
                            frame.contentWindow.__pfCursorIsOn = ${cursorActive};
                            cleanDoc(frame.contentDocument);
                        }
                    } catch (e) {}
                });
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun notifyCursorStateToPlayer(active: Boolean) {
        val js = """
            (function() {
                window.__pfCursorIsOn = $active;
                if ($active) {
                    if (window.__pfClearFocus) window.__pfClearFocus();
                    if (window.__pfShowPlayerHud) window.__pfShowPlayerHud();
                }
                document.querySelectorAll('iframe').forEach(frame => {
                    try {
                        if (frame.contentWindow) {
                            frame.contentWindow.__pfCursorIsOn = $active;
                            if ($active && frame.contentWindow.__pfShowPlayerHud) {
                                frame.contentWindow.__pfShowPlayerHud();
                            }
                        }
                    } catch (e) {}
                });
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action

        val isRemoteKey = keyCode == KeyEvent.KEYCODE_DPAD_UP ||
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
            keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_MENU ||
            keyCode == KeyEvent.KEYCODE_INFO

        if (isRemoteKey) {
            activateTvModeIfNeeded()
        }

        if (!isTvDevice) {
            if (keyCode == KeyEvent.KEYCODE_BACK && action == KeyEvent.ACTION_DOWN) {
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

        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO) {
            if (action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                toggleCursor()
            }
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (event.repeatCount == 0) {
                    okLongPressHandled = false
                    handler.postDelayed(okLongPressRunnable, 600L)
                }
                return true
            } else if (action == KeyEvent.ACTION_UP) {
                handler.removeCallbacks(okLongPressRunnable)
                if (!okLongPressHandled) {
                    if (cursorActive) {
                        val centerX = cursorX + ((cursorView?.width ?: 32) / 2f)
                        val centerY = cursorY + ((cursorView?.height ?: 32) / 2f)
                        simulateTap(centerX, centerY)
                        if (customView != null) {
                            handler.postDelayed({
                                setCursorVisibility(false, showToast = false)
                                clearHoverAndFocusWithoutClicking()
                            }, 350L)
                        }
                    } else {
                        performSpatialClick()
                    }
                }
                return true
            }
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val now = SystemClock.uptimeMillis()
                if (now - lastBackPressTime < 450L) {
                    lastBackPressTime = 0L
                    activateTvModeIfNeeded()
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

        if (keyCode == KeyEvent.KEYCODE_DPAD_UP ||
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
        ) {
            if (cursorActive) {
                if (action == KeyEvent.ACTION_DOWN) {
                    moveCursor(keyCode)
                }
                return true
            } else if (customView == null) {
                if (action == KeyEvent.ACTION_DOWN) {
                    val dir = when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> "UP"
                        KeyEvent.KEYCODE_DPAD_DOWN -> "DOWN"
                        KeyEvent.KEYCODE_DPAD_LEFT -> "LEFT"
                        else -> "RIGHT"
                    }
                    webView.evaluateJavascript("if (window.__pfMoveSpatialFocus) window.__pfMoveSpatialFocus('$dir');", null)
                }
                return true
            }
        }

        return super.dispatchKeyEvent(event)
    }

    private fun performSpatialClick() {
        webView.evaluateJavascript("if (window.__pfClickFocused) window.__pfClickFocused(); else false;") { result ->
            if (result != "true") {
                val centerX = webView.width / 2f
                val centerY = webView.height / 2f
                simulateTap(centerX, centerY)
            }
        }
    }

    private fun scrollPage(deltaY: Int) {
        webView.evaluateJavascript("if (window.__pfScrollSmart) window.__pfScrollSmart($deltaY);", null)
    }

    private fun setCursorVisibility(active: Boolean, showToast: Boolean = true) {
        ensureCursorAttached()
        val cursor = cursorView ?: return
        cursorActive = active
        notifyCursorStateToPlayer(active)
        if (cursorActive) {
            cursor.visibility = View.VISIBLE
            updateCursorPosition()
            if (showToast) {
                Toast.makeText(this, "Cursor ON (OK to click, Back to exit)", Toast.LENGTH_SHORT).show()
            }
        } else {
            cursor.visibility = View.GONE
            clearHoverAndFocusWithoutClicking()
            if (showToast) {
                Toast.makeText(this, "Cursor OFF", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleCursor() {
        setCursorVisibility(!cursorActive, showToast = true)
    }

    private fun moveCursor(keyCode: Int) {
        val cursor = cursorView ?: return
        val now = SystemClock.uptimeMillis()
        moveStep = if (now - lastMoveTime < 120L) {
            (moveStep + 6f).coerceAtMost(60f)
        } else {
            28f
        }
        lastMoveTime = now

        val maxWidth = (rootLayout.width - cursor.width).coerceAtLeast(0).toFloat()
        val maxHeight = (rootLayout.height - cursor.height).coerceAtLeast(0).toFloat()

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (cursorY <= 40f) scrollPage(-350)
                cursorY = (cursorY - moveStep).coerceIn(0f, maxHeight)
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (cursorY >= maxHeight - 40f) scrollPage(350)
                cursorY = (cursorY + moveStep).coerceIn(0f, maxHeight)
            }
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
        val targetView: View = if (fullscreenContainer.visibility == View.VISIBLE) fullscreenContainer else webView

        val downTime = SystemClock.uptimeMillis()
        val downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)

        val upTime = SystemClock.uptimeMillis() + 50
        val upEvent = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, x, y, 0)

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
        if (hasFocus) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            hideSystemBars()
        }
    }
}
