package com.arena.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.arena.app.databinding.ActivityMainBinding

const val ARENA_URL = "https://arena.ai/"

/**
 * A single-purpose app that only opens arena.ai.
 * - No address bar / browser UI
 * - Only the arena.ai domain is allowed (all other sites are blocked)
 * - Full-screen WebView
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full screen, no title/action bar
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        webView = binding.webView

        // --- WebView settings ---
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadsImagesAutomatically = true
        webView.settings.setSupportZoom(false)
        webView.settings.builtInZoomControls = false
        webView.settings.displayZoomControls = false
        webView.settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        webView.settings.appCacheEnabled = true
        webView.isHorizontalScrollBarEnabled = false
        webView.isVerticalScrollBarEnabled = false

        // --- Only allow arena.ai — block every other site ---
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (isArenaAllowed(url)) {
                    return false // let it load
                }
                // Block anything that isn't arena.ai
                return true
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: android.webkit.WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                // Keep showing the last good page; do not navigate away
            }
        }

        webView.webChromeClient = object : android.webkit.WebChromeClient()

        // Load arena.ai
        webView.loadUrl(ARENA_URL)
    }

    /**
     * Returns true only if the URL belongs to arena.ai (any subdomain).
     * Everything else is rejected so the user cannot leave the site.
     */
    private fun isArenaAllowed(url: String): Boolean {
        return url.startsWith("https://arena.ai/") ||
               url.startsWith("http://arena.ai/") ||
               url.startsWith("https://www.arena.ai/") ||
               url.startsWith("http://www.arena.ai/") ||
               url.startsWith("https://app.arena.ai/") ||
               url.startsWith("https://api.arena.ai/")
    }

    // Handle back button: go back in WebView history if possible, else exit app
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
