package com.openassist.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream

/**
 * Shows the chosen AI's website (Gemini, ChatGPT, Claude, etc.).
 * Automatically injects any attached selection or file (images, PDFs, documents, spreadsheets)
 * using synthetic paste events and direct file delivery.
 */
class WebActivity : Activity(), Speech.Callback {
    companion object {
        const val ACTION_ASSIST = "com.openassist.app.action.ASSIST"
        const val EXTRA_MODE = "mode"
        const val EXTRA_TEXT = "text"
        const val EXTRA_SUBMIT = "submit"
        const val EXTRA_FILES = "files"

        const val MODE_ASK = "ask"
        const val MODE_IMAGE = "image"
        const val MODE_TEXT = "text"
        const val MODE_VOICE = "voice"

        private const val RC_FILE = 11
        private const val RC_MIC = 12
        private const val RC_WEB_MIC = 13
        private const val RC_SPEECH = 25

        private val AUTH_HOSTS = listOf(
            "google.com", "gstatic.com", "googleusercontent.com", "googleapis.com",
            "openai.com", "auth0.com", "microsoftonline.com", "live.com", "microsoft.com",
            "anthropic.com", "apple.com", "github.com", "x.com", "twitter.com", "facebook.com"
        )
    }

    private class Pending(
        val mode: String,
        val text: String?,
        val submit: Boolean,
        val files: List<Staged>,
        val stamp: Long
    )

    private lateinit var prefs: Prefs
    private lateinit var speech: Speech
    private lateinit var web: ChatWebView
    private lateinit var serviceBtn: TextView
    private lateinit var langBtn: TextView
    private lateinit var engineBtn: TextView
    private lateinit var micBtn: ImageButton
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private val handler = Handler(Looper.getMainLooper())

    private var service: AiService = AiServices.builtIn.first()
    private var pending: Pending? = null
    private var generation = 0
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var webPermission: PermissionRequest? = null
    private var submitAfterVoice = false
    private var loadFailed = false
    private lateinit var theme: Ui.Theme

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            prefs = Prefs(this)
            theme = Ui.theme(this)
            speech = Speech(this, prefs, this)

            window.statusBarColor = theme.BAR
            window.navigationBarColor = theme.BAR
            Ui.applySystemBars(this, theme)

            buildUi()
            configureWebView()
            handleIntent(intent, initial = true)

            if (!prefs.firstRunDone && intent?.getStringExtra(EXTRA_MODE) == null) {
                prefs.firstRunDone = true
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        } catch (t: Throwable) {
            android.util.Log.e("WebActivity", "Fatal in WebActivity onCreate", t)
            Toast.makeText(this, "Could not open web assistant", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::theme.isInitialized && theme.isDark != prefs.isDarkMode) {
            recreate()
            return
        }
        handleIntent(intent, initial = false)
    }

    private fun dp(v: Int) = Ui.dp(this, v)

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.BG)
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(theme.BAR)
            setPadding(dp(8), 0, dp(4), 0)
        }

        serviceBtn = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(theme.TEXT)
            background = Ui.pill(this@WebActivity, theme.SURFACE_HI)
            setPadding(dp(14), dp(7), dp(14), dp(7))
            setOnClickListener { chooseService() }
        }
        bar.addView(serviceBtn)

        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))

        langBtn = Ui.chip(this, "", false, theme) { cycleLang() }
        bar.addView(langBtn)

        val sp = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(4), 1) }
        bar.addView(sp)

        engineBtn = Ui.chip(this, "", false, theme) { toggleEngineMethod() }
        bar.addView(engineBtn)

        micBtn = Ui.iconButton(this, R.drawable.ic_mic, getString(R.string.cd_mic), theme = theme) { toggleVoice(false) }
        bar.addView(micBtn)

        bar.addView(Ui.iconButton(this, R.drawable.ic_paste, getString(R.string.web_paste), theme = theme) { pasteNow() })

        val menu = Ui.iconButton(this, R.drawable.ic_settings, getString(R.string.web_settings), theme = theme) {}
        menu.setOnClickListener { showMenu(menu) }
        bar.addView(menu)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
            progressTintList = ColorStateList.valueOf(theme.ACCENT)
        }

        status = TextView(this).apply {
            visibility = View.GONE
            setTextColor(theme.TEXT)
            setBackgroundColor(theme.SURFACE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }

        web = ChatWebView(this).apply {
            onFiles = { files, failed ->
                if (failed > 0) Toast.makeText(this@WebActivity, "Failed to attach file", Toast.LENGTH_SHORT).show()
                if (files.isNotEmpty()) attachFilesViaJs(files)
            }
        }

        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)))
        root.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
        updateToolbar()
    }

    private fun updateToolbar() {
        serviceBtn.text = "${service.name} \u25be"
        langBtn.text = prefs.voiceLang.chip
        engineBtn.text = prefs.getEngineChipLabel()
        val isFuto = prefs.isFutoActive()
        engineBtn.background = Ui.pill(this, if (isFuto) theme.ACCENT else theme.SURFACE_HI)
        engineBtn.setTextColor(if (isFuto) theme.ON_ACCENT else theme.TEXT)
    }

    private fun toggleEngineMethod() {
        val newEngine = prefs.toggleEngine()
        updateToolbar()
        val msg = if (newEngine == "FUTO") {
            "Switched to FUTO Method (Offline Whisper)"
        } else {
            "Switched to Google Speech Recognition & Synthesis"
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun showStatus(msg: String) {
        status.text = msg
        status.visibility = View.VISIBLE
        status.setOnClickListener(null)
    }

    private fun hideStatus() {
        status.visibility = View.GONE
    }

    private fun chooseService() {
        val list = AiServices.all(prefs)
        val items = list.map { it.name }.toMutableList()
        items.add("+ Add Custom AI Web Assistant...")
        AlertDialog.Builder(this)
            .setTitle(R.string.web_choose_ai)
            .setItems(items.toTypedArray()) { _, which: Int ->
                if (which < list.size) {
                    switchService(list[which])
                } else {
                    showAddCustomAiDialog()
                }
            }
            .show()
    }

    private fun showAddCustomAiDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(16)
            setPadding(pad, pad, pad, pad)
        }
        val nameInput = android.widget.EditText(this).apply {
            hint = "AI Name (e.g. Open WebUI, Ollama, Grok 3)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(theme.TEXT)
            setHintTextColor(theme.MUTED)
        }
        val urlInput = android.widget.EditText(this).apply {
            hint = "Web URL (e.g. https://... or http://192.168.1.x:3000)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(theme.TEXT)
            setHintTextColor(theme.MUTED)
        }
        layout.addView(nameInput)
        val sp = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(8)) }
        layout.addView(sp)
        layout.addView(urlInput)

        AlertDialog.Builder(this)
            .setTitle("Add Custom AI Web Assistant")
            .setView(layout)
            .setPositiveButton("Add & Switch") { _, _ ->
                val name = nameInput.text.toString().trim()
                val url = urlInput.text.toString().trim()
                if (url.isNotBlank()) {
                    val newService = prefs.addCustomService(name, url)
                    switchService(newService)
                    Toast.makeText(this, "Switched to ${newService.name}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Please enter a valid URL", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun switchService(s: AiService) {
        prefs.serviceId = s.id
        service = s
        updateToolbar()
        loadService()
    }

    private fun cycleLang() {
        prefs.voiceLang = prefs.voiceLang.next()
        updateToolbar()
        Toast.makeText(this, prefs.voiceLang.label, Toast.LENGTH_SHORT).show()
    }

    private fun showMenu(anchor: View) {
        val m = PopupMenu(this, anchor)
        m.menu.add(0, 1, 0, R.string.web_new_chat)
        m.menu.add(0, 2, 1, R.string.web_attach_alt)
        m.menu.add(0, 3, 2, R.string.web_open_browser)
        m.menu.add(0, 4, 3, "Switch Engine (${prefs.getEngineChipLabel()})")
        val modeText = if (prefs.isPostProcessing) "Mode: Post-Processing" else "Mode: Real-Time"
        m.menu.add(0, 5, 4, "Switch Whisper ($modeText)")
        val themeLabel = if (prefs.isDarkMode) "Theme: Dark Mode" else "Theme: Light Mode"
        m.menu.add(0, 7, 5, "Switch Theme ($themeLabel)")
        m.menu.add(0, 6, 6, R.string.web_settings)
        m.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> loadService()
                2 -> StagedFiles.lastSelection?.let { s -> attachFilesViaJs(listOf(s)) }
                3 -> openExternal(Uri.parse(web.url ?: service.url))
                4 -> toggleEngineMethod()
                5 -> {
                    prefs.processingMode = if (prefs.isPostProcessing) Prefs.MODE_REAL_TIME else Prefs.MODE_POST_PROCESSING
                    val updated = if (prefs.isPostProcessing) "Post-Processing (Default)" else "Real-Time Streaming"
                    Toast.makeText(this, "Set Whisper to $updated", Toast.LENGTH_SHORT).show()
                }
                6 -> startActivity(Intent(this, SettingsActivity::class.java))
                7 -> {
                    prefs.isDarkMode = !prefs.isDarkMode
                    recreate()
                }
            }
            true
        }
        m.show()
    }

    private fun modUserAgent(): String {
        val d = WebSettings.getDefaultUserAgent(this)
        val end = d.indexOf(')')
        val tail = if (end > 0) d.substring(end + 1) else d
        return "Mozilla/5.0 (X11; Linux ${System.getProperty("os.arch")})" +
            tail.replace("Version/4.0 ", "")
    }

    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            try {
                web.settings.isAlgorithmicDarkeningAllowed = theme.isDark
            } catch (e: Throwable) {}
        }
        @Suppress("DEPRECATION")
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            try {
                web.settings.forceDark = if (theme.isDark) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
            } catch (e: Throwable) {}
        }
        web.setBackgroundColor(theme.BG)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            allowFileAccess = true
            allowContentAccess = true
            javaScriptCanOpenWindowsAutomatically = true
            // Cache UI so Gemini and other AI models load snappily with local cache revalidation
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = modUserAgent()
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }
        web.setDownloadListener { url, _, _, _, _ ->
            if (url.startsWith("http")) openExternal(Uri.parse(url))
            else Toast.makeText(this, R.string.web_no_downloads, Toast.LENGTH_SHORT).show()
        }

        web.webViewClient = object : WebViewClient() {
            override fun onRenderProcessGone(
                view: WebView?, detail: android.webkit.RenderProcessGoneDetail?
            ): Boolean {
                // Return true to prevent host app crash when WebView renderer runs out of memory or restarts
                loadFailed = true
                progress.visibility = View.GONE
                showStatus("Web session restored. Tap to reload.")
                status.setOnClickListener {
                    hideStatus()
                    loadService()
                }
                return true
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme ?: return false
                if (scheme != "https" && scheme != "http") {
                    if (request.hasGesture()) openExternal(uri)
                    return true
                }
                if (isInternal(uri.host)) return false
                if (request.hasGesture() && request.isForMainFrame) {
                    openExternal(uri)
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                loadFailed = false
                progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = View.GONE
                CookieManager.getInstance().flush()
                if (!loadFailed) {
                    hideStatusIfRetry()
                    if (pending != null && isOnService(url)) runPending()
                }
            }

            override fun onReceivedError(
                view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    loadFailed = true
                    progress.visibility = View.GONE
                    showStatus(getString(R.string.web_retry))
                    status.setOnClickListener {
                        hideStatus()
                        web.reload()
                    }
                }
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                return try {
                    startActivityForResult(fileChooserParams?.createIntent(), RC_FILE)
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    false
                }
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                runOnUiThread {
                    val hasAudio = request.resources.any { it == PermissionRequest.RESOURCE_AUDIO_CAPTURE }
                    if (hasAudio) {
                        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            request.grant(request.resources)
                        } else {
                            webPermission?.deny()
                            webPermission = request
                            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), RC_WEB_MIC)
                        }
                    } else {
                        request.grant(request.resources)
                    }
                }
            }
        }
    }

    private fun hideStatusIfRetry() {
        if (status.text == getString(R.string.web_retry)) hideStatus()
    }

    private fun hostRoot(host: String?): String? {
        val parts = host?.split('.') ?: return null
        return if (parts.size >= 2) parts.takeLast(2).joinToString(".") else host
    }

    private fun isOnService(url: String?): Boolean {
        val h = url?.let { Uri.parse(it).host } ?: return false
        return hostRoot(h) == hostRoot(Uri.parse(service.url).host)
    }

    private fun isInternal(host: String?): Boolean {
        if (host == null) return false
        if (hostRoot(host) == hostRoot(Uri.parse(service.url).host)) return true
        return AUTH_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    private fun openExternal(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {}
    }

    private fun loadService() {
        web.loadUrl(service.url)
    }

    private fun handleIntent(i: Intent?, initial: Boolean) {
        service = AiServices.current(prefs)
        updateToolbar()
        val mode = i?.getStringExtra(EXTRA_MODE)
        val filePaths = i?.getStringArrayExtra(EXTRA_FILES) ?: emptyArray()
        val stagedList = filePaths.mapNotNull { StagedFiles.fromPath(this, it) }

        if (mode == null && stagedList.isEmpty()) {
            if (initial || web.url == null) loadService()
            return
        }

        pending = Pending(
            mode ?: MODE_TEXT,
            i?.getStringExtra(EXTRA_TEXT),
            i?.getBooleanExtra(EXTRA_SUBMIT, false) ?: false,
            stagedList,
            System.currentTimeMillis()
        )

        val needLoad = web.url == null || !isOnService(web.url)
        if (needLoad) {
            loadService()
        } else {
            if (prefs.newChatOnAssist) {
                try {
                    web.evaluateJavascript("""
                        (function() {
                            var btns = document.querySelectorAll('button[aria-label*="New chat" i], a[aria-label*="New chat" i], [data-testid="new-chat-button"]');
                            for (var i = 0; i < btns.length; i++) {
                                if (btns[i].getClientRects().length > 0) { btns[i].click(); return true; }
                            }
                            return false;
                        })()
                    """.trimIndent(), null)
                } catch (e: Exception) {}
            }
            runPending()
        }
    }

    private fun runPending() {
        val p = pending ?: return
        pending = null
        if (System.currentTimeMillis() - p.stamp > 90_000) return
        val gen = ++generation
        hideStatus()
        focusWithRetry(gen, 0) { focused ->
            if (isFinishing || isDestroyed || gen != generation) return@focusWithRetry
            if (!focused) Toast.makeText(this, R.string.web_no_chatbox, Toast.LENGTH_LONG).show()

            var delay = 0L
            if (p.files.isNotEmpty() && prefs.autoAttach) {
                handler.postDelayed({ if (!isFinishing && !isDestroyed && gen == generation) attachFilesViaJs(p.files) }, 350)
                delay = 1000
            }

            if (p.text != null) {
                handler.postDelayed({
                    if (!isFinishing && !isDestroyed && gen == generation) insertText(p.text)
                }, delay)
                if (p.submit) handler.postDelayed({
                    if (!isFinishing && !isDestroyed && gen == generation) trySubmit(gen, 0)
                }, delay + 1200)
            } else {
                showKeyboard()
            }
        }
    }

    private fun focusWithRetry(gen: Int, tries: Int, done: (Boolean) -> Unit) {
        if (isFinishing || isDestroyed) return
        try {
            web.evaluateJavascript(WebJs.FOCUS) { r ->
                if (isFinishing || isDestroyed || gen != generation) return@evaluateJavascript
                if (r == "true") done(true)
                else if (tries < 40) handler.postDelayed({ focusWithRetry(gen, tries + 1, done) }, 350)
                else done(false)
            }
        } catch (e: Exception) {
            done(false)
        }
    }

    private fun showKeyboard() {
        if (isFinishing || isDestroyed) return
        web.requestFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(web, 0)
    }

    private fun insertText(text: String) {
        if (isFinishing || isDestroyed) return
        try {
            web.evaluateJavascript(WebJs.insertText(JSONObject.quote(text)), null)
        } catch (e: Exception) {}
    }

    private fun trySubmit(gen: Int, tries: Int) {
        if (isFinishing || isDestroyed) return
        try {
            web.evaluateJavascript(WebJs.SUBMIT) { r ->
                if (isFinishing || isDestroyed || gen != generation) return@evaluateJavascript
                if (r == "true") return@evaluateJavascript
                if (tries < 30) handler.postDelayed({ trySubmit(gen, tries + 1) }, 500)
                else web.evaluateJavascript(WebJs.ENTER, null)
            }
        } catch (e: Exception) {}
    }

    private fun pasteNow() {
        if (isFinishing || isDestroyed) return
        web.requestFocus()
        val now = android.os.SystemClock.uptimeMillis()
        val meta = android.view.KeyEvent.META_CTRL_ON or android.view.KeyEvent.META_CTRL_LEFT_ON
        for (action in intArrayOf(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.ACTION_UP)) {
            web.dispatchKeyEvent(
                android.view.KeyEvent(now, now, action, android.view.KeyEvent.KEYCODE_V, 0, meta)
            )
        }
    }

    private fun attachFilesViaJs(files: List<Staged>) {
        Thread {
            for (staged in files) {
                try {
                    val file = staged.file
                    if (!file.exists() || !file.canRead()) continue

                    val bytes: ByteArray
                    if (staged.isImage) {
                        // Always safely decode and scale to max 1280px and compressed JPEG (< 300KB)
                        // to prevent Android Binder TransactionTooLargeException & Chromium IPC crash
                        val bmp = try {
                            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
                            val maxDim = 1280
                            var inSampleSize = 1
                            val largestDim = maxOf(bounds.outWidth, bounds.outHeight)
                            while (largestDim / inSampleSize > maxDim) {
                                inSampleSize *= 2
                            }
                            val opts = android.graphics.BitmapFactory.Options().apply {
                                this.inSampleSize = inSampleSize
                                inPreferredConfig = Bitmap.Config.RGB_565
                            }
                            android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
                        } catch (e: Throwable) {
                            null
                        }

                        if (bmp != null) {
                            val bos = java.io.ByteArrayOutputStream()
                            bmp.compress(Bitmap.CompressFormat.JPEG, 80, bos)
                            bytes = bos.toByteArray()
                            bmp.recycle()
                        } else {
                            bytes = FileInputStream(file).use { it.readBytes() }
                        }
                    } else {
                        // Non-image file (PDF, doc, etc.) with safe buffer limit
                        val maxFileBytes = 2 * 1024 * 1024
                        if (file.length() > maxFileBytes) continue
                        bytes = FileInputStream(file).use { it.readBytes() }
                    }

                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            try {
                                web.evaluateJavascript(WebJs.attachFile(staged.name, staged.mime, b64), null)
                            } catch (e: Throwable) {}
                        }
                    }
                    Thread.sleep(250)
                } catch (t: Throwable) {}
            }
        }.start()
    }

    private fun toggleVoice(submit: Boolean) {
        if (speech.listening) {
            speech.stop()
            hideStatus()
            return
        }
        submitAfterVoice = submit
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), RC_MIC)
            return
        }
        speech.start()
    }

    override fun onStarting(engineName: String) {
        if (prefs.isLocalModelMode && prefs.isPostProcessing) {
            showStatus("  RECORDING AUDIO   Speak now... [Tap mic to transcribe]")
        } else if (prefs.isLocalModelMode) {
            showStatus("  LIVE MIC ACTIVE ($engineName)... [Tap mic to stop]")
        } else {
            showStatus("  LIVE MIC ACTIVE   Connecting to $engineName... [Tap mic to stop]")
        }
    }

    override fun onReadyForSpeech(engineName: String) {
        if (prefs.isLocalModelMode && prefs.isPostProcessing) {
            showStatus("  RECORDING AUDIO   Speak now! [Tap mic to stop & transcribe]")
        } else {
            showStatus("  LIVE MIC ACTIVE ($engineName)   Listening... Speak now [Tap mic to stop]")
        }
    }

    override fun onPartial(text: String) {
        showStatus("  LIVE MIC (${speech.currentEngineName})   Heard: \"$text\"")
    }

    override fun onFinal(text: String) {
        showStatus("Transcribed: \"$text\"")
        handler.postDelayed({ hideStatus() }, 1200)
        val submit = submitAfterVoice || prefs.autoSubmit
        submitAfterVoice = false
        val gen = ++generation
        focusWithRetry(gen, 0) {
            insertText(text)
            if (submit) handler.postDelayed({ if (gen == generation) trySubmit(gen, 0) }, 400)
        }
    }

    override fun onLaunchSpeechIntent(intent: Intent) {
        showStatus("Opening FUTO Voice Input...")
        try {
            startActivityForResult(intent, RC_SPEECH)
        } catch (e: Exception) {
            hideStatus()
            onFutoNotFound()
        }
    }

    override fun onFutoNotFound() {
        hideStatus()
        AlertDialog.Builder(this)
            .setTitle("FUTO Voice Input Not Installed")
            .setMessage("FUTO Voice Input (offline Whisper) is not installed on this device yet.\n\nWould you like to install FUTO Voice Input, or use Google Speech Services?")
            .setPositiveButton("Get FUTO (Free APK)") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://voiceinput.futo.org/")))
                } catch (e: Exception) {
                    Toast.makeText(this, "Visit https://voiceinput.futo.org/", Toast.LENGTH_LONG).show()
                }
            }
            .setNeutralButton("Use Google Speech") { _, _ ->
                prefs.recognizer = Prefs.ENGINE_GOOGLE_TTS
                updateToolbar()
                toggleVoice(submitAfterVoice)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onError(message: String) {
        showStatus(message)
        handler.postDelayed({ hideStatus() }, 3000)
    }

    override fun onKeyboardVoiceRequested(modelName: String?) {
        hideStatus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(web, InputMethodManager.SHOW_IMPLICIT)
        val name = modelName ?: prefs.customModelName
        val isFuto = try {
            packageManager.getPackageInfo("org.futo.inputmethod.latin", 0) != null
        } catch (e: Exception) { false }

        val msg = when {
            isFuto && name.isNotBlank() -> "FUTO Voice active ($name). Tap mic on keyboard to dictate."
            isFuto -> "Tap mic on FUTO Keyboard to dictate."
            name.isNotBlank() -> "Voice active ($name). Tap mic on keyboard to dictate."
            else -> "Tap mic on keyboard to dictate."
        }
        showStatus(msg)
        handler.postDelayed({ hideStatus() }, 3500)
    }

    override fun onStateChanged(listening: Boolean, engineName: String) {
        micBtn.imageTintList = ColorStateList.valueOf(if (listening) Color.parseColor("#EF4444") else theme.TEXT)
        if (listening) {
            showStatus("  LIVE MIC ACTIVE ($engineName)   Listening... Speak now [Tap mic to stop]")
        } else {
            handler.postDelayed({ if (!speech.listening) hideStatus() }, 500)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        when (requestCode) {
            RC_MIC -> if (granted) {
                speech.start()
            } else {
                showStatus(getString(R.string.err_mic_permission))
            }
            RC_WEB_MIC -> {
                val req = webPermission
                webPermission = null
                if (granted) req?.grant(req.resources) else req?.deny()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SPEECH) {
            hideStatus()
            if (resultCode == RESULT_OK && data != null) {
                val matches = data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                    ?: data.getStringArrayListExtra("android.speech.extra.RESULTS")
                val raw = matches?.firstOrNull()?.trim()
                    ?: data.getStringExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.trim()
                    ?: data.getStringExtra("result")?.trim()
                    ?: data.getStringExtra("text")?.trim()
                    ?: data.getStringExtra("query")?.trim()
                if (!raw.isNullOrBlank()) {
                    val out = if (prefs.voiceLang == VoiceLang.HINGLISH) Translit.toHinglish(raw) else raw
                    onFinal(out)
                }
            }
            return
        }
        if (requestCode == RC_FILE) {
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            fileCallback = null
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else moveTaskToBack(true)
    }

    override fun onResume() {
        super.onResume()
        if (::theme.isInitialized && theme.isDark != prefs.isDarkMode) {
            recreate()
            return
        }
        web.onResume()
        updateToolbar()
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        web.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        try { speech.destroy() } catch (e: Throwable) {}
        handler.removeCallbacksAndMessages(null)
        try {
            (web.parent as? ViewGroup)?.removeView(web)
            web.stopLoading()
            web.clearHistory()
            web.removeAllViews()
            web.destroy()
        } catch (e: Throwable) {}
        super.onDestroy()
    }
}

private object WebJs {
    private const val FIND_INPUT = """
        function findInput() {
          var sels = ['rich-textarea .ql-editor', 'div[contenteditable="true"][role="textbox"]',
                      'textarea', 'div[contenteditable="true"]', '[role="textbox"]'];
          for (var i = 0; i < sels.length; i++) {
            var el = document.querySelector(sels[i]);
            if (el && el.getClientRects().length > 0) return el;
          }
          return null;
        }
    """

    val FOCUS = """
        (function() {
          $FIND_INPUT
          var el = findInput();
          if (!el) return false;
          el.focus();
          return document.activeElement === el || el.contains(document.activeElement);
        })()
    """.trimIndent()

    fun insertText(quoted: String) = """
        (function(t) {
          $FIND_INPUT
          var el = findInput();
          if (!el) return false;
          el.focus();
          if (el.tagName === 'TEXTAREA' || el.tagName === 'INPUT') {
            var proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
            var cur = el.value || '';
            var next = (cur.trim().length > 0 ? cur.trimEnd() + ' ' : '') + t;
            Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, next);
            el.dispatchEvent(new Event('input', {bubbles: true}));
          } else {
            var sel = window.getSelection();
            if (sel) {
              var range = document.createRange();
              range.selectNodeContents(el);
              range.collapse(false);
              sel.removeAllRanges();
              sel.addRange(range);
            }
            var curText = (el.innerText || el.textContent || '').trim();
            var prefix = (curText.length > 0 ? ' ' : '');
            document.execCommand('insertText', false, prefix + t);
          }
          return true;
        })($quoted)
    """.trimIndent()

    val SUBMIT = """
        (function() {
          var sels = ['button[data-testid="send-button"]', 'button[aria-label*="Send" i]',
                      'button[aria-label*="Submit" i]', 'button.send-button', 'button[type="submit"]'];
          for (var i = 0; i < sels.length; i++) {
            var els = document.querySelectorAll(sels[i]);
            for (var j = 0; j < els.length; j++) {
              var b = els[j];
              if (!b.disabled && b.getAttribute('aria-disabled') !== 'true' && b.getClientRects().length > 0) {
                b.click();
                return true;
              }
            }
          }
          return false;
        })()
    """.trimIndent()

    val ENTER = """
        (function() {
          var el = document.activeElement;
          if (!el) return false;
          ['keydown', 'keypress', 'keyup'].forEach(function(type) {
            el.dispatchEvent(new KeyboardEvent(type, {key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true}));
          });
          return true;
        })()
    """.trimIndent()

    fun attachFile(fileName: String, mime: String, b64: String) = """
        (function(name, mime, b64) {
          $FIND_INPUT
          function inject() {
            var el = findInput();
            var bin = atob(b64), n = bin.length, u8 = new Uint8Array(n);
            for (var i = 0; i < n; i++) u8[i] = bin.charCodeAt(i);
            var f = new File([u8], name, {type: mime});
            var dt = new DataTransfer();
            dt.items.add(f);
            var ok = false;
            var target = el || document.activeElement || document.body;
            target.focus();
            try {
              var evPaste = new ClipboardEvent('paste', {clipboardData: dt, bubbles: true, cancelable: true});
              target.dispatchEvent(evPaste);
              ok = true;
            } catch(e) {}
            try {
              var evDrop = new DragEvent('drop', {dataTransfer: dt, bubbles: true, cancelable: true});
              target.dispatchEvent(evDrop);
              ok = true;
            } catch(e) {}
            var fileInputs = document.querySelectorAll('input[type="file"]');
            for (var k = 0; k < fileInputs.length; k++) {
              try {
                fileInputs[k].files = dt.files;
                fileInputs[k].dispatchEvent(new Event('change', {bubbles: true}));
                ok = true;
              } catch(e) {}
            }
            return ok;
          }
          if (!inject()) {
            var tries = 0;
            var timer = setInterval(function() {
              tries++;
              if (inject() || tries > 20) clearInterval(timer);
            }, 500);
          }
          return true;
        })(${JSONObject.quote(fileName)}, ${JSONObject.quote(mime)}, ${JSONObject.quote(b64)})
    """.trimIndent()
}
