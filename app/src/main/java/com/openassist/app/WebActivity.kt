package com.openassist.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileInputStream

class WebActivity : Activity(), Speech.Callback {
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var titleText: TextView
    private lateinit var urlText: TextView
    private lateinit var editPrompt: EditText
    private lateinit var btnMic: ImageView
    private lateinit var btnMascot: ImageView
    private lateinit var prefs: Prefs
    private lateinit var theme: Ui.Theme
    private var speech: Speech? = null
    private var isDictating = false
    private var currentUrl: String = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web)

        prefs = Prefs(this)
        theme = Ui.theme(this)
        speech = Speech(this, prefs, this)

        val defaultUrl = AiServices.current(prefs).url
        currentUrl = intent.getStringExtra(EXTRA_URL) ?: defaultUrl
        val initialTitle = intent.getStringExtra(EXTRA_TITLE) ?: AiServices.current(prefs).name

        webView = findViewById(R.id.web_view)
        progressBar = findViewById(R.id.progress_bar)
        titleText = findViewById(R.id.text_title)
        urlText = findViewById(R.id.text_url)
        editPrompt = findViewById(R.id.edit_web_prompt)
        btnMic = findViewById(R.id.btn_web_mic)
        btnMascot = findViewById(R.id.btn_web_mascot)

        titleText.text = initialTitle
        urlText.text = currentUrl

        applyTheming()
        setupTopBar()
        setupBottomAssistantDock()
        setupWebView()

        val incomingText = intent.getStringExtra(EXTRA_TEXT)
        if (!incomingText.isNullOrBlank()) {
            editPrompt.setText(incomingText)
        }

        webView.loadUrl(currentUrl)
    }

    private fun applyTheming() {
        val root = findViewById<View>(R.id.web_root)
        val topBar = findViewById<View>(R.id.web_top_bar)
        val bottomDock = findViewById<View>(R.id.web_bottom_dock)
        val promptBar = findViewById<View>(R.id.web_prompt_bar)

        root.setBackgroundColor(theme.BG)
        topBar.setBackgroundColor(theme.SURFACE)
        bottomDock.setBackgroundColor(theme.SURFACE)

        titleText.setTextColor(theme.TEXT)
        urlText.setTextColor(theme.MUTED)
        editPrompt.setTextColor(theme.TEXT)
        editPrompt.setHintTextColor(theme.MUTED)

        promptBar.background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this, 20).toFloat())

        val iconTint = ColorStateList.valueOf(theme.TEXT)
        findViewById<ImageView>(R.id.btn_back).imageTintList = iconTint
        findViewById<ImageView>(R.id.btn_share).imageTintList = iconTint
        findViewById<ImageView>(R.id.btn_close).imageTintList = iconTint
        findViewById<ImageView>(R.id.btn_web_settings).imageTintList = iconTint
        findViewById<ImageView>(R.id.btn_web_paste).imageTintList = iconTint
        findViewById<ImageView>(R.id.btn_web_attach).imageTintList = iconTint
        findViewById<ImageView>(R.id.btn_web_send).imageTintList = ColorStateList.valueOf(theme.ACCENT)

        val (mascotDrawable, _) = getMascotInfo(prefs.mascotStyle)
        btnMascot.setImageResource(mascotDrawable)

        Ui.applySystemBars(this, theme)
    }

    private fun setupTopBar() {
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
            val shareUrl = webView.url ?: currentUrl
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareUrl)
            }
            startActivity(Intent.createChooser(shareIntent, "Share URL"))
        }
    }

    private fun setupBottomAssistantDock() {
        findViewById<ImageView>(R.id.btn_web_settings).setOnClickListener {
            try {
                startActivity(Intent(this, SettingsActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open Settings", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<ImageView>(R.id.btn_web_paste).setOnClickListener {
            pasteClipboardIntoWebChat()
        }
        findViewById<ImageView>(R.id.btn_web_attach).setOnClickListener {
            attachStagedFilesIntoWebChat()
        }
        btnMascot.setOnClickListener {
            val phrases = listOf(
                "Need help? Ask anything above!",
                "I can paste your circled screenshot anytime!",
                "Tap the mic to dictate hands-free",
                "Switching AI is easy in Settings!"
            )
            Toast.makeText(this, phrases.random(), Toast.LENGTH_SHORT).show()
        }
        btnMic.setOnClickListener {
            toggleDictation()
        }
        findViewById<ImageView>(R.id.btn_web_send).setOnClickListener {
            submitWebPrompt()
        }
        editPrompt.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                submitWebPrompt()
                true
            } else {
                false
            }
        }
    }

    private fun toggleDictation() {
        if (isDictating) {
            speech?.stop()
            isDictating = false
            btnMic.imageTintList = ColorStateList.valueOf(theme.TEXT)
        } else {
            isDictating = true
            btnMic.imageTintList = ColorStateList.valueOf(Color.parseColor("#EF4444"))
            Toast.makeText(this, "Listening...", Toast.LENGTH_SHORT).show()
            speech?.start()
        }
    }

    override fun onStarting(engineName: String) {
        runOnUiThread {
            editPrompt.hint = "Listening via $engineName..."
        }
    }

    override fun onPartial(text: String) {
        runOnUiThread {
            if (text.isNotBlank()) {
                val current = editPrompt.text.toString()
                val updated = if (current.isBlank()) text else "$current $text"
                editPrompt.setText(updated)
                editPrompt.setSelection(updated.length)
            }
        }
    }

    override fun onFinal(text: String) {
        runOnUiThread {
            if (text.isNotBlank()) {
                val current = editPrompt.text.toString()
                val updated = if (current.isBlank()) text else "$current $text"
                editPrompt.setText(updated)
                editPrompt.setSelection(updated.length)
            }
            isDictating = false
            btnMic.imageTintList = ColorStateList.valueOf(theme.TEXT)
            editPrompt.hint = "Ask AI..."
        }
    }

    override fun onError(message: String) {
        runOnUiThread {
            isDictating = false
            btnMic.imageTintList = ColorStateList.valueOf(theme.TEXT)
            editPrompt.hint = "Ask AI..."
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStateChanged(listening: Boolean, engineName: String) {
        runOnUiThread {
            isDictating = listening
            btnMic.imageTintList = if (listening) {
                ColorStateList.valueOf(Color.parseColor("#EF4444"))
            } else {
                ColorStateList.valueOf(theme.TEXT)
            }
        }
    }

    private fun submitWebPrompt() {
        val query = editPrompt.text.toString().trim()
        if (query.isBlank()) {
            Toast.makeText(this, "Type a query or tap the mic first", Toast.LENGTH_SHORT).show()
            return
        }
        injectTextAndSubmit(query)
        editPrompt.setText("")
    }

    private fun pasteClipboardIntoWebChat() {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipText = cm?.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
            if (!clipText.isNullOrBlank()) {
                injectTextAndSubmit(clipText)
                Toast.makeText(this, "Pasted into chat", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No text on clipboard", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not paste", Toast.LENGTH_SHORT).show()
        }
    }

    private fun attachStagedFilesIntoWebChat() {
        try {
            val last = StagedFiles.lastSelection
            if (last != null && last.file.exists()) {
                val b64 = encodeFileToBase64(last.file)
                if (b64 != null) {
                    injectBase64Image(b64, "image/png")
                    Toast.makeText(this, "Attached circled image!", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            Toast.makeText(this, "No circled screenshot staged to attach", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not attach image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun encodeFileToBase64(file: File): String? {
        return try {
            val bytes = file.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    private fun injectTextAndSubmit(text: String) {
        val escaped = text.replace("\\", "\\\\").replace(""", "\\"").replace("\n", "\\n").replace("\r", "")
        val js = """
            (function() {
                try {
                    const txt = "$escaped";
                    const el = document.querySelector('rich-textarea, textarea, [contenteditable="true"], input[type="text"]');
                    if (el) {
                        el.focus();
                        if (el.tagName === 'TEXTAREA' || el.tagName === 'INPUT') {
                            el.value = (el.value ? el.value + ' ' : '') + txt;
                            el.dispatchEvent(new Event('input', { bubbles: true }));
                            el.dispatchEvent(new Event('change', { bubbles: true }));
                        } else if (el.isContentEditable) {
                            el.innerText = (el.innerText ? el.innerText + ' ' : '') + txt;
                            el.dispatchEvent(new Event('input', { bubbles: true }));
                        }
                        setTimeout(() => {
                            const btn = document.querySelector('button[aria-label*="Send"], button[aria-label*="Submit"], [data-testid="send-button"], button.send-button');
                            if (btn) btn.click();
                        }, 300);
                        return true;
                    }
                } catch(e) {}
                return false;
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun injectBase64Image(b64: String, mimeType: String) {
        val js = """
            (function() {
                try {
                    const byteCharacters = atob("$b64");
                    const byteNumbers = new Array(byteCharacters.length);
                    for (let i = 0; i < byteCharacters.length; i++) {
                        byteNumbers[i] = byteCharacters.charCodeAt(i);
                    }
                    const byteArray = new Uint8Array(byteNumbers);
                    const file = new File([byteArray], "openassist_capture.png", { type: "$mimeType" });
                    
                    const dt = new DataTransfer();
                    dt.items.add(file);
                    
                    const dropTarget = document.querySelector('rich-textarea, textarea, [contenteditable="true"], [role="presentation"], body');
                    if (dropTarget) {
                        const event = new DragEvent('drop', {
                            bubbles: true,
                            cancelable: true,
                            dataTransfer: dt
                        });
                        dropTarget.dispatchEvent(event);
                        return true;
                    }
                } catch(e) {}
                return false;
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
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
                urlText.text = startUrl ?: currentUrl
            }

            override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                urlText.text = finishedUrl ?: currentUrl
                val autoSubmit = intent.getBooleanExtra(EXTRA_SUBMIT, false)
                val autoText = intent.getStringExtra(EXTRA_TEXT)
                if (autoSubmit && !autoText.isNullOrBlank()) {
                    injectTextAndSubmit(autoText)
                }
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
    }

    private fun getMascotInfo(style: String): Pair<Int, String> {
        val s = style.lowercase()
        return when {
            s in listOf("owl-orbital", "owl", "default", "") -> Pair(R.drawable.ic_mascot_owl_orbital, "Ozzy")
            s == "owl-talons" -> Pair(R.drawable.ic_mascot_owl_talons, "Ozzy Perch")
            s == "owl-radar" -> Pair(R.drawable.ic_mascot_owl_radar, "Ozzy Radar")
            s == "owl-wings" -> Pair(R.drawable.ic_mascot_owl_wings, "Ozzy Shield")
            s == "owl-monocle" -> Pair(R.drawable.ic_mascot_owl_monocle, "Ozzy Scholar")
            s == "owl-minimal" -> Pair(R.drawable.ic_mascot_owl_minimal, "Ozzy Minimal")
            s == "fox-orbital" -> Pair(R.drawable.ic_mascot_fox_orbital, "Pip Orbit")
            s == "fox-cyber" -> Pair(R.drawable.ic_mascot_fox_cyber, "Pip Cyber")
            s == "fox-paws" -> Pair(R.drawable.ic_mascot_fox_paws, "Pip Paws")
            s == "fox-firefly" -> Pair(R.drawable.ic_mascot_fox_firefly, "Pip Radar")
            s == "fox-halo" -> Pair(R.drawable.ic_mascot_fox_halo, "Pip Halo")
            s == "fox-headset" -> Pair(R.drawable.ic_mascot_fox_headset, "Pip Voice")
            s == "fox-monocle" -> Pair(R.drawable.ic_mascot_fox_monocle, "Pip Loupe")
            s == "fox-minimal" -> Pair(R.drawable.ic_mascot_fox_minimal, "Pip Minimal")
            s in listOf("fox", "pip", "fennec") -> Pair(R.drawable.ic_mascot_fox, "Pip")
            s in listOf("cat", "kitten", "byte", "luna") -> Pair(R.drawable.ic_mascot_kitten, "Byte")
            s in listOf("sprite", "ollie", "bolt") -> Pair(R.drawable.ic_mascot_sprite, "Ollie")
            s in listOf("bat", "echo") -> Pair(R.drawable.ic_mascot_bat, "Echo")
            s in listOf("otter", "nova") -> Pair(R.drawable.ic_mascot_otter, "Nova")
            s in listOf("axolotl", "kiko") -> Pair(R.drawable.ic_mascot_axolotl, "Kiko")
            s in listOf("tanuki", "taro", "momo") -> Pair(R.drawable.ic_mascot_tanuki, "Taro")
            s in listOf("chameleon", "pico") -> Pair(R.drawable.ic_mascot_chameleon, "Pico")
            s in listOf("firefly", "lumi") -> Pair(R.drawable.ic_mascot_firefly, "Lumi")
            s == "orbital" -> Pair(R.drawable.ic_mascot_orbital, "Iris")
            s == "whale" -> Pair(R.drawable.ic_mascot_whale, "Moby")
            else -> Pair(R.drawable.ic_mascot_owl_orbital, "Ozzy")
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speech?.destroy()
        speech = null
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
