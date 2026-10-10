package com.openassist.app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity

class WebActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var titleText: TextView
    private lateinit var urlText: TextView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web)

        val prefs = Prefs(this)
        val defaultUrl = AiServices.current(prefs).url
        val url = intent.getStringExtra(EXTRA_URL) ?: defaultUrl
        val initialTitle = intent.getStringExtra(EXTRA_TITLE) ?: "OpenAssist Browser"

        webView = findViewById(R.id.web_view)
        progressBar = findViewById(R.id.progress_bar)
        titleText = findViewById(R.id.text_title)
        urlText = findViewById(R.id.text_url)

        titleText.text = initialTitle
        urlText.text = url

        findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                finish()
            }
        }

        findViewById<ImageView>(R.id.btn_close).setOnClickListener {
            finish()
        }

        findViewById<ImageView>(R.id.btn_share).setOnClickListener {
            val currentUrl = webView.url ?: url
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, currentUrl)
            }
            startActivity(Intent.createChooser(shareIntent, "Share URL"))
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                if (!title.isNullOrBlank()) {
                    titleText.text = title
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, startUrl: String?, favicon: Bitmap?) {
                urlText.text = startUrl ?: url
            }

            override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                urlText.text = finishedUrl ?: url
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val reqUrl = request?.url ?: return false
                val scheme = reqUrl.scheme ?: ""
                if (scheme == "http" || scheme == "https") {
                    return false
                }
                return try {
                    val intent = Intent(Intent.ACTION_VIEW, reqUrl)
                    startActivity(intent)
                    true
                } catch (e: Exception) {
                    true
                }
            }
        }

        webView.loadUrl(url)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val ACTION_ASSIST = "com.openassist.app.ACTION_ASSIST"
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_SUBMIT = "extra_submit"
        const val EXTRA_FILES = "extra_files"

        const val MODE_TEXT = "mode_text"
        const val MODE_IMAGE = "mode_image"
    }
}
