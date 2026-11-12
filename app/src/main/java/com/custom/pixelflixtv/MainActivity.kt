package com.custom.pixelflixtv

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.*
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private lateinit var fullscreenContainer: FrameLayout

    // Common ad & tracking domains to block
    private val blockedHosts = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adnxs.com", "popads.net", "popcash.net", "propellerads.com",
        "exoclick.com", "juicyads.com", "trafficjunky.net", "mgid.com",
        "taboola.com", "outbrain.com", "adsterra.com", "hilltopads.net"
    )

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fullscreenContainer = FrameLayout(this)
        webView = WebView(this)
        fullscreenContainer.addView(webView)
        setContentView(fullscreenContainer)

        setupWebView()
        webView.loadUrl("https://www.pixelflix.cc")
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
                        style.innerHTML = 'iframe[src*="ads"], .adsbygoogle, [id*="ad-"], [class*="popup"], [class*="banner-ad"] { display: none !important; }';
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
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
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
