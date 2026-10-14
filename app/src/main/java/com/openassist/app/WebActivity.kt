package com.openassist.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.Gravity
import android.widget.ScrollView
import android.app.AlertDialog
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener

class WebActivity : Activity(), Speech.Callback {
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var topBar: LinearLayout
    private lateinit var btnMascotChip: LinearLayout
    private lateinit var btnMascot: ImageView
    private lateinit var textMascotName: TextView
    private lateinit var btnModelSwitcher: LinearLayout
    private lateinit var textModelName: TextView
    private var btnPaste: ImageView? = null
    private var btnNewChat: ImageView? = null
    private lateinit var btnLangSwitcher: LinearLayout
    private lateinit var textLangName: TextView
    private lateinit var btnEngineSwitcher: LinearLayout
    private lateinit var textEngineName: TextView
    private lateinit var btnMic: ImageView
    private var btnBrowser: ImageView? = null
    private lateinit var btnSettings: ImageView
    private lateinit var btnMenu: ImageView
    private var btnWebAutoSpeak: ImageView? = null

    // Floating Audio Controller views
    private var layoutAudioController: LinearLayout? = null
    private var textAudioStatus: TextView? = null
    private var btnAudioPlayPause: ImageView? = null
    private var btnAudioStop: ImageView? = null
    private var btnAudioSpeed: TextView? = null
    private var btnAudioMinimize: ImageView? = null
    private var layoutAudioOrb: LinearLayout? = null
    private var btnAudioOrbExpand: ImageView? = null

    // Text-To-Speech engine state
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private var isPlayingAudio = false
    private var currentSpeechText: String? = null
    private val speechRates = floatArrayOf(0.8f, 1.0f, 1.25f, 1.5f)
    private var currentSpeedIndex = 1

    private lateinit var prefs: Prefs
    private lateinit var theme: Ui.Theme
    private var speech: Speech? = null
    private var isDictating = false
    private var currentUrl: String = ""

    // File Chooser & Camera state for WebView
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var cameraPhotoUri: Uri? = null

    // WebRTC Permission state (Microphone & Camera for Gemini / Web AI speech/vision)
    private var pendingPermissionRequest: PermissionRequest? = null

    // Pending data from Overlay (Circle to Search / Dictation)
    private var pendingText: String? = null
    private var pendingFiles: Array<String>? = null
    private var pendingSubmit: Boolean = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        if (prefs.isDarkMode) {
            setTheme(R.style.Theme_OpenAssist_Dark)
        } else {
            setTheme(R.style.Theme_OpenAssist_Light)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web)

        try {
            theme = Ui.theme(this)
            speech = Speech(this, prefs, this)

            val defaultUrl = AiServices.current(prefs).url
            val rawUrl = intent.getStringExtra(EXTRA_URL)
            currentUrl = if (isSafeWebUrl(rawUrl)) rawUrl!! else defaultUrl

            pendingText = intent.getStringExtra(EXTRA_TEXT)
            pendingFiles = intent.getStringArrayExtra(EXTRA_FILES)
            pendingSubmit = intent.getBooleanExtra(EXTRA_SUBMIT, false)

            webView = findViewById(R.id.web_view)
            progressBar = findViewById(R.id.progress_bar)
            topBar = findViewById(R.id.web_top_bar)
            btnMascotChip = findViewById(R.id.btn_web_mascot_chip)
            btnMascot = findViewById(R.id.btn_web_mascot)
            textMascotName = findViewById(R.id.text_mascot_name)
            btnModelSwitcher = findViewById(R.id.btn_model_switcher)
            textModelName = findViewById(R.id.text_model_name)
            btnLangSwitcher = findViewById(R.id.btn_lang_switcher)
            textLangName = findViewById(R.id.text_lang_name)
            btnEngineSwitcher = findViewById(R.id.btn_engine_switcher)
            textEngineName = findViewById(R.id.text_engine_name)
            btnMic = findViewById(R.id.btn_web_mic)
            btnSettings = findViewById(R.id.btn_web_settings)
            btnMenu = findViewById(R.id.btn_web_menu)
            btnWebAutoSpeak = findViewById(R.id.btn_web_auto_speak)

            layoutAudioController = findViewById(R.id.layout_audio_controller)
            textAudioStatus = findViewById(R.id.text_audio_status)
            btnAudioPlayPause = findViewById(R.id.btn_audio_play_pause)
            btnAudioStop = findViewById(R.id.btn_audio_stop)
            btnAudioSpeed = findViewById(R.id.btn_audio_speed)
            btnAudioMinimize = findViewById(R.id.btn_audio_minimize)
            layoutAudioOrb = findViewById(R.id.layout_audio_orb)
            btnAudioOrbExpand = findViewById(R.id.btn_audio_orb_expand)

            btnAudioSpeed?.text = "${prefs.speechRate}x"

            // Initialize on-device Android Text-To-Speech engine
            try {
                textToSpeech = TextToSpeech(this) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        isTtsReady = true
                        textToSpeech?.setSpeechRate(prefs.speechRate)
                        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                runOnUiThread {
                                    isPlayingAudio = true
                                    textAudioStatus?.text = "Speaking aloud..."
                                    btnAudioPlayPause?.setImageResource(R.drawable.ic_pause)
                                }
                            }
                            override fun onDone(utteranceId: String?) {
                                runOnUiThread {
                                    isPlayingAudio = false
                                    textAudioStatus?.text = "Finished speaking"
                                    btnAudioPlayPause?.setImageResource(R.drawable.ic_play)
                                }
                            }
                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                runOnUiThread {
                                    isPlayingAudio = false
                                    textAudioStatus?.text = "Speech paused"
                                    btnAudioPlayPause?.setImageResource(R.drawable.ic_play)
                                }
                            }
                        })
                    }
                }
            } catch (e: Exception) {
                // Ignore TTS initialization error
            }

            val queryExtra = intent.getStringExtra("EXTRA_QUERY") ?: intent.getStringExtra(EXTRA_QUERY_TEXT)
            val activeService = AiServices.current(prefs)
            if (!queryExtra.isNullOrBlank()) {
                if (activeService.category != ServiceCategory.AI) {
                    currentUrl = activeService.getSearchUrl(queryExtra)
                    pendingText = null
                    pendingSubmit = false
                } else {
                    pendingText = queryExtra
                    pendingSubmit = true
                }
            }

            val notInstalledApp = intent.getStringExtra("EXTRA_NOT_INSTALLED_APP")
            val notInstalledId = intent.getStringExtra("EXTRA_NOT_INSTALLED_ID")
            if (!notInstalledApp.isNullOrBlank() && !notInstalledId.isNullOrBlank()) {
                window.decorView.post {
                    showNotInstalledAppNotice(notInstalledApp, notInstalledId)
                }
            }

            applyTheming()
            setupTopBarControls()
            setupWebView()

            webView.loadUrl(currentUrl)
        } catch (t: Throwable) {
            android.util.Log.e("WebActivity", "Error during WebActivity initialization", t)
            try {
                Ui.showMascotToast(this, "Recovered from web assistant initialization issue", prefs.mascotStyle, type = ToastType.GENERAL)
            } catch (e: Exception) {
                // Ignore toast failure
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent != null) {
            val newUrl = intent.getStringExtra(EXTRA_URL)
            val newText = intent.getStringExtra(EXTRA_TEXT) ?: intent.getStringExtra("EXTRA_QUERY")
            val newFiles = intent.getStringArrayExtra(EXTRA_FILES)
            val newSubmit = intent.getBooleanExtra(EXTRA_SUBMIT, false) || intent.hasExtra("EXTRA_QUERY")

            pendingText = newText
            pendingFiles = newFiles
            pendingSubmit = newSubmit

            val activeService = AiServices.current(prefs)
            if (!newText.isNullOrBlank() && activeService.category != ServiceCategory.AI) {
                currentUrl = activeService.getSearchUrl(newText)
                pendingText = null
                pendingSubmit = false
                webView.loadUrl(currentUrl)
            } else if (!newUrl.isNullOrBlank() && isSafeWebUrl(newUrl) && newUrl != currentUrl) {
                currentUrl = newUrl
                webView.loadUrl(currentUrl)
            } else if (!pendingText.isNullOrBlank() || !pendingFiles.isNullOrEmpty()) {
                injectPendingAssistData()
            }

            val notInstalledApp = intent.getStringExtra("EXTRA_NOT_INSTALLED_APP")
            val notInstalledId = intent.getStringExtra("EXTRA_NOT_INSTALLED_ID")
            if (!notInstalledApp.isNullOrBlank() && !notInstalledId.isNullOrBlank()) {
                window.decorView.post {
                    showNotInstalledAppNotice(notInstalledApp, notInstalledId)
                }
            }
        }
    }

    private fun applyTheming() {
        val root = findViewById<View>(R.id.web_root)
        root.setBackgroundColor(theme.BG)
        topBar.setBackgroundColor(theme.SURFACE)

        val padChipH = Ui.dp(this, 10)
        val padChipV = Ui.dp(this, 4)
        btnMascotChip.clipToPadding = false
        btnMascotChip.clipChildren = false
        btnMascotChip.background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this, 16).toFloat())
        btnMascotChip.setPadding(padChipH, padChipV, padChipH, padChipV)

        val padPillH = Ui.dp(this, 8)
        val padPillV = Ui.dp(this, 5)
        btnModelSwitcher.background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this, 18).toFloat())
        btnModelSwitcher.setPadding(padPillH, padPillV, padPillH, padPillV)
        btnLangSwitcher.background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this, 18).toFloat())
        btnLangSwitcher.setPadding(padPillH, padPillV, padPillH, padPillV)
        btnEngineSwitcher.background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this, 18).toFloat())
        btnEngineSwitcher.setPadding(padPillH, padPillV, padPillH, padPillV)

        textMascotName.setTextColor(theme.TEXT)
        textModelName.setTextColor(theme.TEXT)
        textLangName.setTextColor(theme.TEXT)
        textEngineName.setTextColor(theme.TEXT)

        val iconTint = ColorStateList.valueOf(theme.TEXT)
        btnMic.imageTintList = iconTint
        btnSettings.imageTintList = iconTint
        btnMenu.imageTintList = iconTint

        val (mascotDrawable, mascotName) = getMascotInfo(prefs.mascotStyle)
        btnMascot.setImageResource(mascotDrawable)
        textMascotName.text = mascotName

        refreshPills()
        Ui.applySystemBars(this, theme)
    }

    override fun onResume() {
        super.onResume()
        val mascot = MascotHelper.getMascot(prefs.mascotStyle)
        btnMascot.setImageResource(mascot.drawableRes)
        textMascotName.text = mascot.name
        refreshPills()
    }

    private fun refreshPills() {
        val active = AiServices.current(prefs)
        textModelName.text = "${active.name} ▾"
        textLangName.text = prefs.voiceLang.chip
        textEngineName.text = when (prefs.recognizer) {
            Prefs.ENGINE_LOCAL_MODEL -> "Whisper"
            Prefs.ENGINE_GOOGLE_TTS -> "Google"
            Prefs.ENGINE_FUTO_VOICE_INPUT -> "FUTO"
            else -> "Default"
        }
        val (_, mascotName) = getMascotInfo(prefs.mascotStyle)
        textMascotName.text = mascotName

        btnWebAutoSpeak?.setImageResource(if (prefs.isAutoSpeakEnabled) R.drawable.ic_volume_up else R.drawable.ic_volume_off)
        btnWebAutoSpeak?.imageTintList = ColorStateList.valueOf(if (prefs.isAutoSpeakEnabled) theme.ACCENT else theme.MUTED)
    }

    private fun setupTopBarControls() {
        // 1. Mascot Chip (Leftmost, Adaptive Border & Wholesome Phrases)
        btnMascotChip.setOnClickListener {
            val mascotLang = prefs.translationStore.getEffectiveLanguage("mascot")
            val phrase = prefs.mascotDialogueStore.getRandomPhrase(prefs.mascotStyle, mascotLang)
            Ui.showMascotToast(this, phrase, prefs.mascotStyle, duration = Toast.LENGTH_LONG, type = ToastType.GENERAL)
        }

        // 2. AI Model Switcher Pill
        btnModelSwitcher.setOnClickListener {
            showModelSwitcherDialog()
        }

        // 3. Language Switcher Pill
        btnLangSwitcher.setOnClickListener {
            prefs.voiceLang = prefs.voiceLang.next()
            refreshPills()
            Ui.showMascotToast(this, "Language: ${prefs.voiceLang.label}", prefs.mascotStyle, type = ToastType.SPEECH)
        }

        // 4. Voice Engine Switcher Pill
        btnEngineSwitcher.setOnClickListener {
            showEngineSwitcherDialog()
        }

        // 5. Voice Dictation Mic Button
        btnMic.setOnClickListener {
            toggleDictation()
        }

        // 5B. Auto-Speak / Read Aloud Quick Toggle Button
        btnWebAutoSpeak?.setOnClickListener {
            toggleAutoSpeak()
        }

        // 6. Settings Button
        btnSettings.setOnClickListener {
            openSettings()
        }

        // 7. Hamburger Menu Button (Rightmost: Options + Query History)
        btnMenu.setOnClickListener {
            showWebOptionsMenu()
        }

        // Floating Audio Controller controls
        btnAudioPlayPause?.setOnClickListener {
            togglePlayPause()
        }

        btnAudioStop?.setOnClickListener {
            stopAudioPlayback()
        }

        btnAudioSpeed?.setOnClickListener {
            cycleSpeechSpeed()
        }

        btnAudioMinimize?.setOnClickListener {
            minimizeAudioController()
        }

        layoutAudioOrb?.setOnClickListener {
            expandAudioController()
        }

        btnAudioOrbExpand?.setOnClickListener {
            expandAudioController()
        }
    }

    private fun startNewChat() {
        val cleanUrl = AiServices.current(prefs).url
        currentUrl = cleanUrl
        webView.loadUrl(cleanUrl)
        Ui.showMascotToast(this, "Started new chat", prefs.mascotStyle, type = ToastType.ASSISTANT)
    }

    private fun openInBrowser() {
        val urlToOpen = webView.url ?: currentUrl
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen)))
        } catch (e: Exception) {
            Ui.showMascotToast(this, "Could not open browser: ${e.message}", prefs.mascotStyle, type = ToastType.ASSISTANT)
        }
    }

    private fun openSettings() {
        try {
            startActivity(Intent(this, SettingsActivity::class.java))
        } catch (e: Exception) {
            Ui.showMascotToast(this, "Could not open Settings", prefs.mascotStyle, type = ToastType.GENERAL)
        }
    }

    private fun showWebOptionsMenu() {
        var dialog: android.app.AlertDialog? = null
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.card(this@WebActivity, theme)
            val pad = Ui.dp(this@WebActivity, 16)
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Assistant Actions"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(theme.TEXT)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Quick actions and conversation history"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(theme.MUTED)
            setPadding(0, Ui.dp(this@WebActivity, 2), 0, Ui.dp(this@WebActivity, 10))
        })

        fun actionRow(title: String, desc: String, iconRes: Int, onClick: () -> Unit) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val padH = Ui.dp(this@WebActivity, 12)
                val padV = Ui.dp(this@WebActivity, 10)
                setPadding(padH, padV, padH, padV)
                background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@WebActivity, 8).toFloat())
                isClickable = true
                isFocusable = true
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = Ui.dp(this@WebActivity, 6)
                }
                layoutParams = lp
                setOnClickListener {
                    dialog?.dismiss()
                    onClick()
                }
            }

            val icon = ImageView(this).apply {
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(theme.ACCENT)
                val s = Ui.dp(this@WebActivity, 22)
                layoutParams = LinearLayout.LayoutParams(s, s).apply {
                    rightMargin = Ui.dp(this@WebActivity, 10)
                }
            }
            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(this).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(theme.TEXT)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            })
            textCol.addView(TextView(this).apply {
                text = desc
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(theme.MUTED)
            })
            row.addView(icon)
            row.addView(textCol)
            root.addView(row)
        }

        actionRow("Attach Selection", "Paste circled screenshot or clipboard text", R.drawable.ic_paste) {
            attachStagedFilesOrClipboard()
        }
        actionRow("Start New Chat", "Reset to a clean conversational session", R.drawable.ic_chat_new) {
            startNewChat()
        }
        actionRow("Query History", "View past questions & tap to re-ask", R.drawable.ic_sparkle) {
            showFullHistoryDialog()
        }
        actionRow("Open in Browser", "Launch current AI chat in Chrome/browser", R.drawable.ic_browser) {
            openInBrowser()
        }
        actionRow("Settings", "Open OpenAssist companion settings", R.drawable.ic_settings) {
            openSettings()
        }

        val closeBtn = android.widget.Button(this).apply {
            text = "Cancel"
            textSize = 12f
            background = Ui.rounded(android.graphics.Color.TRANSPARENT, Ui.dp(this@WebActivity, 8).toFloat())
            setTextColor(theme.MUTED)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@WebActivity, 6)
            }
            layoutParams = lp
            setOnClickListener { dialog?.dismiss() }
        }
        root.addView(closeBtn)

        val builder = themedBuilder().setView(root)
        dialog = showThemedDialog(builder)
    }

    private fun showFullHistoryDialog() {
        val history = prefs.getAssistantHistory()
        var dialog: android.app.AlertDialog? = null
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.card(this@WebActivity, theme)
            val pad = Ui.dp(this@WebActivity, 16)
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "AI Query History"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(theme.TEXT)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Past questions sent to web assistants • Tap to re-ask"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(theme.MUTED)
            setPadding(0, Ui.dp(this@WebActivity, 2), 0, Ui.dp(this@WebActivity, 12))
        })

        if (history.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No questions in history yet. Dictate or send any question to save it here!"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(theme.MUTED)
                setPadding(0, Ui.dp(this@WebActivity, 16), 0, Ui.dp(this@WebActivity, 16))
                gravity = Gravity.CENTER
            }
            root.addView(emptyTv)
        } else {
            val listLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            for (item in history) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    val padH = Ui.dp(this@WebActivity, 12)
                    val padV = Ui.dp(this@WebActivity, 10)
                    setPadding(padH, padV, padH, padV)
                    background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@WebActivity, 8).toFloat())
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = Ui.dp(this@WebActivity, 6)
                    }
                    layoutParams = lp
                }

                val textCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    layoutParams = lp
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        injectTextIntoWeb(item.query, true)
                        Ui.showMascotToast(this@WebActivity, "Asking: " + item.query.take(25) + "...", prefs.mascotStyle, type = ToastType.ASSISTANT)
                        dialog?.dismiss()
                    }
                }

                textCol.addView(TextView(this).apply {
                    text = item.query
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(theme.TEXT)
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })

                val timeStr = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(item.timestamp))
                textCol.addView(TextView(this).apply {
                    text = "${item.serviceName} • $timeStr"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    setTextColor(theme.MUTED)
                    setPadding(0, Ui.dp(this@WebActivity, 2), 0, 0)
                })

                val delBtn = ImageView(this).apply {
                    setImageResource(R.drawable.ic_close)
                    imageTintList = ColorStateList.valueOf(theme.MUTED)
                    val s = Ui.dp(this@WebActivity, 24)
                    val p = Ui.dp(this@WebActivity, 4)
                    layoutParams = LinearLayout.LayoutParams(s, s)
                    setPadding(p, p, p, p)
                    setOnClickListener {
                        prefs.removeAssistantHistory(item.id)
                        dialog?.dismiss()
                        showFullHistoryDialog()
                    }
                }

                row.addView(textCol)
                row.addView(delBtn)
                listLayout.addView(row)
            }

            val scroll = ScrollView(this).apply {
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                layoutParams = lp
                addView(listLayout)
            }
            root.addView(scroll)
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@WebActivity, 12)
            }
            layoutParams = lp
        }

        if (history.isNotEmpty()) {
            val clearBtn = android.widget.Button(this).apply {
                text = "Clear All"
                textSize = 12f
                background = Ui.rounded(android.graphics.Color.parseColor("#EF4444"), Ui.dp(this@WebActivity, 8).toFloat())
                setTextColor(android.graphics.Color.WHITE)
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    rightMargin = Ui.dp(this@WebActivity, 8)
                }
                layoutParams = lp
                setOnClickListener {
                    prefs.clearAssistantHistory()
                    dialog?.dismiss()
                    Ui.showMascotToast(this@WebActivity, "History cleared", prefs.mascotStyle, type = ToastType.ASSISTANT)
                }
            }
            btnRow.addView(clearBtn)
        }

        val closeBtn = android.widget.Button(this).apply {
            text = "Close"
            textSize = 12f
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@WebActivity, 8).toFloat())
            setTextColor(theme.TEXT)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
            setOnClickListener { dialog?.dismiss() }
        }
        btnRow.addView(closeBtn)
        root.addView(btnRow)

        val builder = themedBuilder().setView(root)
        dialog = showThemedDialog(builder)
    }

    private fun showModelSwitcherDialog() {
        val allConfigured = AiServices.all(prefs)
        var selectedCat: ServiceCategory? = null

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.card(this@WebActivity, theme)
            val pad = Ui.dp(this@WebActivity, 16)
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Search & Assistant Chips"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(theme.TEXT)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Switch active service or tap 'Manage Chips' to customize"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(theme.MUTED)
            setPadding(0, Ui.dp(this@WebActivity, 2), 0, Ui.dp(this@WebActivity, 8))
        })

        var dialog: android.app.AlertDialog? = null
        val listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun populateList() {
            listLayout.removeAllViews()
            val filtered = if (selectedCat == null) allConfigured else allConfigured.filter { it.category == selectedCat }

            if (filtered.isEmpty()) {
                listLayout.addView(TextView(this).apply {
                    text = "No enabled chips in this category. Tap 'Manage Chips' to enable or add them."
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(theme.MUTED)
                    setPadding(0, Ui.dp(this@WebActivity, 12), 0, Ui.dp(this@WebActivity, 12))
                })
                return
            }

            for (srv in filtered) {
                val isCurrent = srv.id == prefs.serviceId
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    val padH = Ui.dp(this@WebActivity, 10)
                    val padV = Ui.dp(this@WebActivity, 8)
                    setPadding(padH, padV, padH, padV)
                    background = Ui.rounded(if (isCurrent) theme.SURFACE_HI else Color.TRANSPARENT, Ui.dp(this@WebActivity, 8).toFloat())
                    isClickable = true
                    isFocusable = true
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = Ui.dp(this@WebActivity, 4)
                    }
                    layoutParams = lp
                    setOnClickListener {
                        prefs.serviceId = srv.id
                        refreshPills()

                        if (srv.openInApp && !srv.packageName.isNullOrBlank()) {
                            val installed = AiServices.isAppInstalled(this@WebActivity, srv.packageName)
                            if (installed) {
                                val deepLink = srv.getAppDeepLinkUri("")
                                val intent = if (!deepLink.isNullOrBlank()) {
                                    Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
                                        setPackage(srv.packageName)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                } else {
                                    packageManager.getLaunchIntentForPackage(srv.packageName)?.apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                }
                                if (intent != null) {
                                    try {
                                        startActivity(intent)
                                        dialog?.dismiss()
                                        return@setOnClickListener
                                    } catch (e: Exception) {
                                        // Fallback
                                    }
                                }
                            } else {
                                showNotInstalledAppNotice(srv.name, srv.id)
                            }
                        }

                        currentUrl = srv.url
                        webView.loadUrl(currentUrl)
                        Ui.showMascotToast(this@WebActivity, "Loaded ${srv.name}", prefs.mascotStyle, type = ToastType.ASSISTANT)
                        dialog?.dismiss()
                    }
                }

                val textCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    layoutParams = lp
                }

                val titleRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                titleRow.addView(TextView(this).apply {
                    text = srv.name
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(if (isCurrent) theme.ACCENT else theme.TEXT)
                    if (isCurrent) typeface = android.graphics.Typeface.DEFAULT_BOLD
                })

                if (srv.openInApp) {
                    titleRow.addView(TextView(this).apply {
                        text = " App ↗ "
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                        setTextColor(if (theme.isDark) Color.parseColor("#93C5FD") else Color.parseColor("#1D4ED8"))
                        background = Ui.rounded(if (theme.isDark) Color.parseColor("#1E3A8A") else Color.parseColor("#DBEAFE"), Ui.dp(this@WebActivity, 4).toFloat())
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            leftMargin = Ui.dp(this@WebActivity, 6)
                        }
                        layoutParams = lp
                    })
                }

                textCol.addView(titleRow)
                textCol.addView(TextView(this).apply {
                    text = "${srv.category.displayName} • ${srv.url}"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    setTextColor(theme.MUTED)
                    isSingleLine = true
                })
                row.addView(textCol)

                if (isCurrent) {
                    val check = TextView(this).apply {
                        text = "✓"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        setTextColor(theme.ACCENT)
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    row.addView(check)
                }
                listLayout.addView(row)
            }
        }

        // Category Filter Tabs Horizontal Row
        val catScroll = android.widget.HorizontalScrollView(this).apply {
            isFillViewport = true
            isHorizontalScrollBarEnabled = false
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = Ui.dp(this@WebActivity, 8)
            }
            layoutParams = lp
        }
        val catRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val categories = listOf(null to "All") + ServiceCategory.values().map { it to it.displayName }
        fun refreshCatTabs() {
            catRow.removeAllViews()
            for ((cat, label) in categories) {
                val isSel = selectedCat == cat
                val tab = TextView(this).apply {
                    text = label
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    val padH = Ui.dp(this@WebActivity, 10)
                    val padV = Ui.dp(this@WebActivity, 4)
                    setPadding(padH, padV, padH, padV)
                    background = Ui.rounded(if (isSel) theme.ACCENT else theme.SURFACE_HI, Ui.dp(this@WebActivity, 6).toFloat())
                    setTextColor(if (isSel) theme.ON_ACCENT else theme.TEXT)
                    typeface = if (isSel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    isClickable = true
                    setOnClickListener {
                        selectedCat = cat
                        refreshCatTabs()
                        populateList()
                    }
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        rightMargin = Ui.dp(this@WebActivity, 6)
                    }
                    layoutParams = lp
                }
                catRow.addView(tab)
            }
        }
        refreshCatTabs()
        catScroll.addView(catRow)
        root.addView(catScroll)

        populateList()

        val scroll = android.widget.ScrollView(this).apply {
            addView(listLayout)
            isFillViewport = true
            val dm = resources.displayMetrics
            val maxH = (dm.heightPixels * 0.40).toInt()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxH)
        }
        root.addView(scroll)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@WebActivity, 12)
            }
            layoutParams = lp
        }
        val manageBtn = android.widget.Button(this).apply {
            text = "⚙ Manage Chips"
            textSize = 12f
            background = Ui.rounded(theme.ACCENT, Ui.dp(this@WebActivity, 8).toFloat())
            setTextColor(theme.ON_ACCENT)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = Ui.dp(this@WebActivity, 6)
            }
            layoutParams = lp
            setOnClickListener {
                dialog?.dismiss()
                val intent = Intent(this@WebActivity, SettingsActivity::class.java).apply {
                    putExtra("EXTRA_OPEN_CHIP_MANAGER", true)
                }
                startActivity(intent)
            }
        }
        val addBtn = android.widget.Button(this).apply {
            text = "+ Add Link"
            textSize = 12f
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@WebActivity, 8).toFloat())
            setTextColor(theme.TEXT)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = Ui.dp(this@WebActivity, 6)
            }
            layoutParams = lp
            setOnClickListener {
                dialog?.dismiss()
                showAddCustomAiDialog()
            }
        }
        val cancelBtn = android.widget.Button(this).apply {
            text = "Close"
            textSize = 12f
            background = Ui.rounded(theme.SURFACE, Ui.dp(this@WebActivity, 8).toFloat())
            setTextColor(theme.MUTED)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f)
            layoutParams = lp
            setOnClickListener { dialog?.dismiss() }
        }
        btnRow.addView(manageBtn)
        btnRow.addView(addBtn)
        btnRow.addView(cancelBtn)
        root.addView(btnRow)

        dialog = android.app.AlertDialog.Builder(this)
            .setView(root)
            .create()
        dialog.show()
        dialog.window?.let { win ->
            win.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            win.decorView.setBackgroundColor(Color.TRANSPARENT)
            val dm = resources.displayMetrics
            win.setLayout(
                (dm.widthPixels * 0.92).toInt().coerceAtMost(Ui.dp(this, 440)),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun showNotInstalledAppNotice(appName: String, serviceId: String) {
        val mascot = MascotHelper.getMascot(prefs.mascotStyle)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.card(this@WebActivity, theme)
            val pad = Ui.dp(this@WebActivity, 16)
            setPadding(pad, pad, pad, pad)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(ImageView(this).apply {
            setImageResource(mascot.drawableRes)
            val s = Ui.dp(this@WebActivity, 32)
            layoutParams = LinearLayout.LayoutParams(s, s).apply { rightMargin = Ui.dp(this@WebActivity, 10) }
        })
        header.addView(TextView(this).apply {
            text = "${mascot.name}'s Note: App Not Installed"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(theme.TEXT)
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(header)

        root.addView(TextView(this).apply {
            text = "Hey! $appName isn\'t installed on your device, so I opened it here in WebView. Would you like to switch this chip to always open in WebView?"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(theme.MUTED)
            setPadding(0, Ui.dp(this@WebActivity, 8), 0, Ui.dp(this@WebActivity, 14))
        })

        var dialog: AlertDialog? = null
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val switchBtn = Button(this).apply {
            text = "Always Use WebView"
            textSize = 12f
            background = Ui.rounded(theme.ACCENT, Ui.dp(this@WebActivity, 8).toFloat())
            setTextColor(theme.ON_ACCENT)
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener {
                prefs.setServiceOpenInApp(serviceId, false)
                Ui.showMascotToast(this@WebActivity, "$appName set to open in WebView", prefs.mascotStyle, type = ToastType.GENERAL)
                dialog?.dismiss()
            }
        }
        val keepBtn = Button(this).apply {
            text = "Keep Setting"
            textSize = 12f
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@WebActivity, 8).toFloat())
            setTextColor(theme.MUTED)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = Ui.dp(this@WebActivity, 8)
            }
            layoutParams = lp
            setOnClickListener { dialog?.dismiss() }
        }
        btnRow.addView(switchBtn)
        btnRow.addView(keepBtn)
        root.addView(btnRow)

        dialog = showThemedDialog(themedBuilder().setView(root))
    }

    private fun showAddCustomAiDialog() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.card(this@WebActivity, theme)
            val pad = Ui.dp(this@WebActivity, 16)
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "Add Custom Website or Search App"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(theme.TEXT)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Enter website name and URL. Include {q} for search queries (e.g. https://domain.com/search?q={q})"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(theme.MUTED)
            setPadding(0, Ui.dp(this@WebActivity, 2), 0, Ui.dp(this@WebActivity, 12))
        })

        val nameInput = android.widget.EditText(this).apply {
            hint = "Website Name (e.g. Reddit, arXiv, Ollama)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(theme.TEXT)
            setHintTextColor(theme.MUTED)
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@WebActivity, 8).toFloat())
            val p = Ui.dp(this@WebActivity, 10)
            setPadding(p, p, p, p)
        }
        val urlInput = android.widget.EditText(this).apply {
            hint = "URL or Search Template (https://... or ...?q={q})"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(theme.TEXT)
            setHintTextColor(theme.MUTED)
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@WebActivity, 8).toFloat())
            val p = Ui.dp(this@WebActivity, 10)
            setPadding(p, p, p, p)
        }
        val pkgInput = android.widget.EditText(this).apply {
            hint = "App Package Name (Optional, e.g. com.example.app)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(theme.TEXT)
            setHintTextColor(theme.MUTED)
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@WebActivity, 8).toFloat())
            val p = Ui.dp(this@WebActivity, 8)
            setPadding(p, p, p, p)
        }
        root.addView(nameInput)
        root.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(1, Ui.dp(this@WebActivity, 8)) })
        root.addView(urlInput)
        root.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(1, Ui.dp(this@WebActivity, 8)) })
        root.addView(pkgInput)

        val openInAppCheck = android.widget.CheckBox(this).apply {
            text = "Open in native app if installed"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(theme.TEXT)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@WebActivity, 8)
            }
            layoutParams = lp
        }
        root.addView(openInAppCheck)

        var dialog: android.app.AlertDialog? = null
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@WebActivity, 14)
            }
            layoutParams = lp
        }
        val addBtn = android.widget.Button(this).apply {
            text = "Add & Select"
            textSize = 12f
            background = Ui.rounded(theme.ACCENT, Ui.dp(this@WebActivity, 8).toFloat())
            setTextColor(theme.ON_ACCENT)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = Ui.dp(this@WebActivity, 8)
            }
            layoutParams = lp
            setOnClickListener {
                val name = nameInput.text.toString().trim()
                val url = urlInput.text.toString().trim()
                val pkg = pkgInput.text.toString().trim().takeIf { it.isNotBlank() }
                val openInApp = openInAppCheck.isChecked
                if (url.isNotBlank()) {
                    val newService = prefs.addCustomService(
                        name = name,
                        url = url,
                        category = ServiceCategory.CUSTOM,
                        openInApp = openInApp,
                        packageName = pkg
                    )
                    prefs.serviceId = newService.id
                    currentUrl = newService.url
                    refreshPills()
                    webView.loadUrl(currentUrl)
                    Ui.showMascotToast(this@WebActivity, "Added and selected ${newService.name}", prefs.mascotStyle, type = ToastType.ASSISTANT)
                    dialog?.dismiss()
                } else {
                    Ui.showMascotToast(this@WebActivity, "Please enter a valid URL", prefs.mascotStyle, type = ToastType.ASSISTANT)
                }
            }
        }
        val cancelBtn = android.widget.Button(this).apply {
            text = "Cancel"
            textSize = 12f
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@WebActivity, 8).toFloat())
            setTextColor(theme.MUTED)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
            setOnClickListener { dialog?.dismiss() }
        }
        btnRow.addView(addBtn)
        btnRow.addView(cancelBtn)
        root.addView(btnRow)

        dialog = android.app.AlertDialog.Builder(this)
            .setView(root)
            .create()
        dialog.show()
        dialog.window?.let { win ->
            win.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            win.decorView.setBackgroundColor(Color.TRANSPARENT)
            val dm = resources.displayMetrics
            win.setLayout(
                (dm.widthPixels * 0.92).toInt().coerceAtMost(Ui.dp(this, 440)),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun showEngineSwitcherDialog() {
        val engines = listOf(
            Triple(Prefs.ENGINE_DEFAULT, "System Default Speech Recognizer", "Android standard framework speech pipeline"),
            Triple(Prefs.ENGINE_LOCAL_MODEL, "Local Model Mode (Whisper)", "100% offline & private multi-model Whisper"),
            Triple(Prefs.ENGINE_GOOGLE_TTS, "Google Speech Recognition", "Native Google cloud typing for Android / ColorOS"),
            Triple(Prefs.ENGINE_FUTO_VOICE_INPUT, "FUTO Voice Input / Keyboard", "Fast on-device neural offline Whisper")
        )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.card(this@WebActivity, theme)
            val pad = Ui.dp(this@WebActivity, 16)
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Select Voice Input Source"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(theme.TEXT)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Choose your speech recognition engine for voice dictation"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(theme.MUTED)
            setPadding(0, Ui.dp(this@WebActivity, 2), 0, Ui.dp(this@WebActivity, 12))
        })

        var dialog: android.app.AlertDialog? = null
        val listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        for ((engineKey, engineName, engineDesc) in engines) {
            val isCurrent = prefs.recognizer == engineKey
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val padH = Ui.dp(this@WebActivity, 12)
                val padV = Ui.dp(this@WebActivity, 10)
                setPadding(padH, padV, padH, padV)
                background = Ui.rounded(if (isCurrent) theme.SURFACE_HI else Color.TRANSPARENT, Ui.dp(this@WebActivity, 8).toFloat())
                isClickable = true
                isFocusable = true
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = Ui.dp(this@WebActivity, 4)
                }
                layoutParams = lp
                setOnClickListener {
                    prefs.recognizer = engineKey
                    refreshPills()
                    Ui.showMascotToast(this@WebActivity, "Voice Engine: $engineName", prefs.mascotStyle, type = ToastType.SPEECH)
                    dialog?.dismiss()
                }
            }
            val titleRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val title = TextView(this).apply {
                text = engineName
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(if (isCurrent) theme.ACCENT else theme.TEXT)
                if (isCurrent) typeface = android.graphics.Typeface.DEFAULT_BOLD
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                layoutParams = lp
            }
            titleRow.addView(title)
            if (isCurrent) {
                val check = TextView(this).apply {
                    text = "✓"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTextColor(theme.ACCENT)
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                titleRow.addView(check)
            }
            row.addView(titleRow)
            val desc = TextView(this).apply {
                text = engineDesc
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setTextColor(theme.MUTED)
                setPadding(0, Ui.dp(this@WebActivity, 2), 0, 0)
            }
            row.addView(desc)
            listLayout.addView(row)
        }
        root.addView(listLayout)

        val cancelBtn = android.widget.Button(this).apply {
            text = "Cancel"
            textSize = 12f
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@WebActivity, 8).toFloat())
            setTextColor(theme.MUTED)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@WebActivity, 10)
            }
            layoutParams = lp
            setOnClickListener { dialog?.dismiss() }
        }
        root.addView(cancelBtn)

        dialog = android.app.AlertDialog.Builder(this)
            .setView(root)
            .create()
        dialog.show()
        dialog.window?.let { win ->
            win.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            win.decorView.setBackgroundColor(Color.TRANSPARENT)
            val dm = resources.displayMetrics
            win.setLayout(
                (dm.widthPixels * 0.92).toInt().coerceAtMost(Ui.dp(this, 440)),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = false
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true

            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    @Suppress("DEPRECATION")
                    forceDark = WebSettings.FORCE_DARK_OFF
                }
                if (Build.VERSION.SDK_INT >= 33) {
                    isAlgorithmicDarkeningAllowed = false
                }
            } catch (e: Exception) {
                // Ignore OEM-specific WebSettings incompatibility
            }
        }

        webView.addJavascriptInterface(WebClipboardBridge(this, prefs), "AndroidClipboard")
        webView.addJavascriptInterface(WebAudioBridge(this, prefs), "AndroidAudioBridge")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }

            // 1. File & Camera Picker: handles Gemini / ChatGPT / Claude file and camera buttons
            override fun onShowFileChooser(
                view: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback

                return try {
                    val allowMultiple = fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE

                    // Prepare Camera intent
                    val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    var captureUri: Uri? = null
                    try {
                        val photoFile = createImageFile()
                        captureUri = SelectionProvider.getUriForFile(photoFile)
                        cameraPhotoUri = captureUri
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, captureUri)
                        takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    } catch (e: Exception) {
                        cameraPhotoUri = null
                    }

                    // Prepare generic File/Document Picker intent
                    val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        val acceptTypes = fileChooserParams?.acceptTypes
                        if (!acceptTypes.isNullOrEmpty() && acceptTypes[0].isNotBlank()) {
                            type = acceptTypes[0]
                            if (acceptTypes.size > 1) {
                                putExtra(Intent.EXTRA_MIME_TYPES, acceptTypes)
                            }
                        } else {
                            type = "*/*"
                        }
                        if (allowMultiple) {
                            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        }
                    }

                    val intentArray: Array<Intent> = if (cameraPhotoUri != null) {
                        arrayOf(takePictureIntent)
                    } else {
                        emptyArray()
                    }

                    val chooserIntent = Intent(Intent.ACTION_CHOOSER).apply {
                        putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
                        putExtra(Intent.EXTRA_TITLE, "Upload File or Take Photo")
                        if (intentArray.isNotEmpty()) {
                            putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray)
                        }
                    }

                    startActivityForResult(chooserIntent, REQUEST_FILE_CHOOSER)
                    true
                } catch (e: Exception) {
                    fileUploadCallback?.onReceiveValue(null)
                    fileUploadCallback = null
                    cameraPhotoUri = null
                    false
                }
            }

            // 2. WebRTC Audio/Video permission request: handles Gemini voice mic button & live camera dictation
            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                val requestedResources = request.resources
                val origin = request.origin?.toString()?.lowercase() ?: ""
                val host = try { Uri.parse(origin).host?.lowercase() ?: "" } catch (_: Exception) { "" }

                val isTrustedAiOrigin = isTrustedOrCustomHost(host)

                val needsAudio = requestedResources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                val needsVideo = requestedResources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)

                val permissionsToAsk = mutableListOf<String>()
                if (needsAudio && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToAsk.add(android.Manifest.permission.RECORD_AUDIO)
                }
                if (needsVideo && checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToAsk.add(android.Manifest.permission.CAMERA)
                }

                fun executeGrant() {
                    if (permissionsToAsk.isEmpty()) {
                        runOnUiThread {
                            try {
                                request.grant(request.resources)
                            } catch (e: Exception) {
                                request.deny()
                            }
                        }
                    } else {
                        pendingPermissionRequest = request
                        requestPermissions(permissionsToAsk.toTypedArray(), REQUEST_WEB_PERMISSIONS)
                    }
                }

                if (isTrustedAiOrigin) {
                    executeGrant()
                } else {
                    runOnUiThread {
                        showWebPermissionPrompt(
                            host = host.ifBlank { "Unknown Website" },
                            resources = requestedResources,
                            onAllow = { executeGrant() },
                            onDeny = {
                                try { request.deny() } catch (_: Exception) {}
                            }
                        )
                    }
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, startUrl: String?, favicon: Bitmap?) {
                applyWebsiteTheme()
                injectClipboardHelper()
                injectAutoSpeakObserver()
            }

            override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                applyWebsiteTheme()
                injectClipboardHelper()
                injectAutoSpeakObserver()
                injectPendingAssistData()
                if (!finishedUrl.isNullOrBlank() && !finishedUrl.equals("about:blank", ignoreCase = true)) {
                    currentUrl = finishedUrl
                    val activeService = AiServices.current(prefs)
                    val activeHost = try { Uri.parse(activeService.url).host?.lowercase() ?: "" } catch (e: Exception) { "" }
                    val finishedHost = try { Uri.parse(finishedUrl).host?.lowercase() ?: "" } catch (e: Exception) { "" }

                    val isIntraChat = activeHost.isNotBlank() && (finishedHost.contains(activeHost) || activeHost.contains(finishedHost))
                    if (isIntraChat || baseChatUrl == null) {
                        baseChatUrl = finishedUrl
                        prefs.updateLatestAssistantHistoryUrl(finishedUrl)
                    }
                }
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                if (!url.isNullOrBlank() && !url.equals("about:blank", ignoreCase = true)) {
                    currentUrl = url
                    val activeService = AiServices.current(prefs)
                    val activeHost = try { Uri.parse(activeService.url).host?.lowercase() ?: "" } catch (e: Exception) { "" }
                    val visitedHost = try { Uri.parse(url).host?.lowercase() ?: "" } catch (e: Exception) { "" }

                    val isIntraChat = activeHost.isNotBlank() && (visitedHost.contains(activeHost) || activeHost.contains(visitedHost))
                    if (isIntraChat || baseChatUrl == null) {
                        baseChatUrl = url
                        prefs.updateLatestAssistantHistoryUrl(url)
                    }
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

            // Crash defense: intercept render process termination to prevent killing the host app
            override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                val didCrash = detail?.didCrash() ?: false
                android.util.Log.e("WebActivity", "WebView render process gone (didCrash: $didCrash)")
                runOnUiThread {
                    try {
                        Ui.showMascotToast(this@WebActivity, "Web view refreshed safely", prefs.mascotStyle, type = ToastType.GENERAL)
                        view?.loadUrl(currentUrl)
                    } catch (e: Exception) {
                        // Ignore reload failure
                    }
                }
                return true
            }
        }
    }

    private var baseChatUrl: String? = null

    fun isTrustedOrCustomHost(host: String): Boolean {
        if (host.isBlank()) return false
        val isPredefinedAi = host.endsWith("google.com") ||
                host.endsWith("openai.com") ||
                host.endsWith("chatgpt.com") ||
                host.endsWith("claude.ai") ||
                host.endsWith("anthropic.com") ||
                host.endsWith("perplexity.ai") ||
                host.endsWith("deepseek.com") ||
                host.endsWith("grok.com") ||
                host.endsWith("x.ai") ||
                host.endsWith("mistral.ai") ||
                host.endsWith("duckduckgo.com") ||
                host.endsWith("wikipedia.org") ||
                host.endsWith("github.com") ||
                host.endsWith("reddit.com")
        if (isPredefinedAi) return true
        val allServices = AiServices.allConfigured(prefs)
        return allServices.any { service ->
            val sHost = try { Uri.parse(service.url).host?.lowercase() ?: "" } catch (_: Exception) { "" }
            sHost.isNotBlank() && (sHost == host || host.endsWith(".$sHost") || sHost.endsWith(".$host"))
        }
    }

    private fun showWebPermissionPrompt(
        host: String,
        resources: Array<String>,
        onAllow: () -> Unit,
        onDeny: () -> Unit
    ) {
        val types = mutableListOf<String>()
        if (resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) types.add("Microphone")
        if (resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) types.add("Camera")
        val reqStr = types.joinToString(" and ")

        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = Ui.dp(this@WebActivity, 20)
            setPadding(pad, pad, pad, pad)
            background = Ui.rounded(theme.SURFACE, Ui.dp(this@WebActivity, 16).toFloat())
        }

        val mascot = MascotHelper.getMascot(prefs.mascotStyle)
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, Ui.dp(this@WebActivity, 12))
        }
        val icon = ImageView(this).apply {
            setImageResource(mascot.drawableRes)
            val s = Ui.dp(this@WebActivity, 32)
            layoutParams = LinearLayout.LayoutParams(s, s).apply { rightMargin = Ui.dp(this@WebActivity, 10) }
        }
        val title = TextView(this).apply {
            text = "Permission Request"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(theme.TEXT)
            typeface = Typeface.DEFAULT_BOLD
        }
        titleRow.addView(icon)
        titleRow.addView(title)
        dialogView.addView(titleRow)

        val desc = TextView(this).apply {
            text = "Website \"$host\" is requesting access to your device $reqStr.\n\nDo you want to grant access to this site?"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(theme.MUTED)
            setLineSpacing(Ui.dp(this@WebActivity, 2).toFloat(), 1.15f)
            setPadding(0, 0, 0, Ui.dp(this@WebActivity, 16))
        }
        dialogView.addView(desc)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        var dialog: AlertDialog? = null

        val denyBtn = Button(this).apply {
            text = "Deny"
            textSize = 12f
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@WebActivity, 8).toFloat())
            setTextColor(theme.MUTED)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                rightMargin = Ui.dp(this@WebActivity, 8)
            }
            layoutParams = lp
            setOnClickListener {
                dialog?.dismiss()
                onDeny()
            }
        }

        val allowBtn = Button(this).apply {
            text = "Allow"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            background = Ui.rounded(theme.ACCENT, Ui.dp(this@WebActivity, 8).toFloat())
            setTextColor(theme.ON_ACCENT)
            setOnClickListener {
                dialog?.dismiss()
                onAllow()
            }
        }
        btnRow.addView(denyBtn)
        btnRow.addView(allowBtn)
        dialogView.addView(btnRow)

        dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    class WebClipboardBridge(private val activity: WebActivity, private val prefs: Prefs) {
        @JavascriptInterface
        fun copyText(text: String?) {
            if (text.isNullOrBlank()) return
            activity.runOnUiThread {
                try {
                    val currentHost = try { Uri.parse(activity.webView.url ?: "").host?.lowercase() ?: "" } catch (_: Exception) { "" }
                    if (activity.isTrustedOrCustomHost(currentHost)) {
                        ClipboardHelper.copyText(activity, text)
                        Ui.showMascotToast(activity, "Copied response to clipboard!", prefs.mascotStyle, type = ToastType.GENERAL)
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    private fun injectClipboardHelper() {
        val clipJs = """
            (function() {
                try {
                    if (window.__oa_clip_hooked) return;
                    window.__oa_clip_hooked = true;
                    
                    const origWriteText = navigator.clipboard && navigator.clipboard.writeText ? navigator.clipboard.writeText.bind(navigator.clipboard) : null;
                    if (!navigator.clipboard) {
                        navigator.clipboard = {};
                    }
                    navigator.clipboard.writeText = function(text) {
                        try {
                            if (window.AndroidClipboard && window.AndroidClipboard.copyText) {
                                window.AndroidClipboard.copyText(text);
                            }
                        } catch(e) {}
                        if (origWriteText) {
                            return origWriteText(text).catch(function() { return Promise.resolve(); });
                        }
                        return Promise.resolve();
                    };

                    const origExec = document.execCommand ? document.execCommand.bind(document) : null;
                    if (origExec) {
                        document.execCommand = function(cmd) {
                            if (cmd === 'copy') {
                                const sel = window.getSelection() ? window.getSelection().toString() : '';
                                if (sel && window.AndroidClipboard) {
                                    try { window.AndroidClipboard.copyText(sel); } catch(e){}
                                }
                            }
                            return origExec.apply(document, arguments);
                        };
                    }
                } catch(e) {}
            })();
        """.trimIndent()
        webView.evaluateJavascript(clipJs, null)
    }

    class WebAudioBridge(private val activity: WebActivity, private val prefs: Prefs) {
        @JavascriptInterface
        fun isAutoSpeakEnabled(): Boolean {
            return prefs.isAutoSpeakEnabled
        }

        @JavascriptInterface
        fun onNativeAudioTriggered() {
            activity.runOnUiThread {
                activity.handleNativeAudioStarted()
            }
        }

        @JavascriptInterface
        fun speakResponse(text: String?) {
            if (text.isNullOrBlank()) return
            activity.runOnUiThread {
                activity.playFallbackSpeech(text)
            }
        }
    }

    private fun injectAutoSpeakObserver() {
        if (!prefs.isAutoSpeakEnabled) return
        val observerJs = """
            (function() {
                try {
                    if (window.__oa_speech_observer_active) return;
                    window.__oa_speech_observer_active = true;

                    let lastSpokenText = '';
                    let streamStableTimer = null;
                    let lastObservedText = '';

                    function getLatestAssistantElement() {
                        const selectors = [
                            'model-response',
                            '.model-response-text',
                            'div[data-message-author-role="assistant"]',
                            '[data-testid*="conversation-turn-assistant"]',
                            '.font-claude-message',
                            '[data-test-render-id]',
                            '.chat-message-assistant',
                            '.prose',
                            'article'
                        ];
                        for (let i = 0; i < selectors.length; i++) {
                            const nodes = document.querySelectorAll(selectors[i]);
                            if (nodes.length > 0) {
                                return nodes[nodes.length - 1];
                            }
                        }
                        return null;
                    }

                    function findNativeSpeakButton(container) {
                        const root = container || document;
                        const buttonSelectors = [
                            'button[aria-label*="Listen" i]',
                            'button[aria-label*="Read aloud" i]',
                            'button[aria-label*="Speak" i]',
                            'button[data-test-id*="listen" i]',
                            'button[data-testid*="read-aloud" i]',
                            'button[title*="Listen" i]',
                            'button[title*="Read aloud" i]'
                        ];
                        for (let i = 0; i < buttonSelectors.length; i++) {
                            const btns = root.querySelectorAll(buttonSelectors[i]);
                            if (btns.length > 0) {
                                return btns[btns.length - 1];
                            }
                        }
                        if (container) {
                            for (let i = 0; i < buttonSelectors.length; i++) {
                                const globalBtns = document.querySelectorAll(buttonSelectors[i]);
                                if (globalBtns.length > 0) {
                                    return globalBtns[globalBtns.length - 1];
                                }
                            }
                        }
                        return null;
                    }

                    function isGeneratingActive() {
                        const stopBtn = document.querySelector('button[aria-label*="Stop" i], button[data-testid*="stop" i], [aria-label*="Stop generating" i]');
                        if (stopBtn && stopBtn.offsetParent !== null) return true;
                        const streamingEl = document.querySelector('.result-streaming, .streaming, [data-is-streaming="true"], .thinking');
                        if (streamingEl) return true;
                        return false;
                    }

                    function extractCleanText(el) {
                        if (!el) return '';
                        const clone = el.cloneNode(true);
                        const toRemove = clone.querySelectorAll('button, svg, script, style, nav, code');
                        for (let i = 0; i < toRemove.length; i++) {
                            toRemove[i].remove();
                        }
                        return (clone.innerText || clone.textContent || '').trim();
                    }

                    function onGenerationFinished() {
                        if (!window.AndroidAudioBridge || !window.AndroidAudioBridge.isAutoSpeakEnabled()) return;
                        const assistantEl = getLatestAssistantElement();
                        const text = extractCleanText(assistantEl);
                        if (!text || text.length < 3 || text === lastSpokenText) return;

                        const nativeBtn = findNativeSpeakButton(assistantEl);
                        if (nativeBtn) {
                            try {
                                nativeBtn.click();
                                lastSpokenText = text;
                                if (window.AndroidAudioBridge.onNativeAudioTriggered) {
                                    window.AndroidAudioBridge.onNativeAudioTriggered();
                                }
                                return;
                            } catch(e) {}
                        }

                        lastSpokenText = text;
                        if (window.AndroidAudioBridge.speakResponse) {
                            window.AndroidAudioBridge.speakResponse(text);
                        }
                    }

                    const observer = new MutationObserver(function() {
                        if (isGeneratingActive()) {
                            if (streamStableTimer) {
                                clearTimeout(streamStableTimer);
                                streamStableTimer = null;
                            }
                            return;
                        }

                        const assistantEl = getLatestAssistantElement();
                        const currentText = extractCleanText(assistantEl);
                        if (currentText && currentText.length > 3 && currentText !== lastObservedText) {
                            lastObservedText = currentText;
                            if (streamStableTimer) clearTimeout(streamStableTimer);
                            streamStableTimer = setTimeout(function() {
                                if (!isGeneratingActive()) {
                                    onGenerationFinished();
                                }
                            }, 1300);
                        }
                    });

                    observer.observe(document.body || document.documentElement, {
                        childList: true,
                        subtree: true,
                        characterData: true
                    });
                } catch(e) {}
            })();
        """.trimIndent()
        webView.evaluateJavascript(observerJs, null)
    }

    fun playFallbackSpeech(text: String) {
        if (!prefs.isAutoSpeakEnabled) return
        currentSpeechText = text
        if (!isTtsReady || textToSpeech == null) {
            Ui.showMascotToast(this, "Speech engine loading...", prefs.mascotStyle, type = ToastType.SPEECH)
            return
        }
        textToSpeech?.stop()
        textToSpeech?.setSpeechRate(prefs.speechRate)
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "OPENASSIST_UTTERANCE_ID")
        isPlayingAudio = true
        showAudioController("Speaking aloud...")
        Ui.showMascotToast(this, "Reading response aloud ✨", prefs.mascotStyle, type = ToastType.SPEECH)
    }

    fun handleNativeAudioStarted() {
        isPlayingAudio = true
        showAudioController("Playing site audio...")
        Ui.showMascotToast(this, "Listening to response ✨", prefs.mascotStyle, type = ToastType.SPEECH)
    }

    private fun showAudioController(status: String = "Speaking aloud...") {
        textAudioStatus?.text = status
        btnAudioPlayPause?.setImageResource(R.drawable.ic_pause)
        if (prefs.isAudioBarMinimized) {
            layoutAudioController?.visibility = View.GONE
            layoutAudioOrb?.visibility = View.VISIBLE
        } else {
            layoutAudioController?.visibility = View.VISIBLE
            layoutAudioOrb?.visibility = View.GONE
        }
    }

    private fun togglePlayPause() {
        if (isPlayingAudio) {
            textToSpeech?.stop()
            isPlayingAudio = false
            btnAudioPlayPause?.setImageResource(R.drawable.ic_play)
            textAudioStatus?.text = "Paused"
        } else {
            val textToPlay = currentSpeechText
            if (!textToPlay.isNullOrBlank()) {
                textToSpeech?.setSpeechRate(prefs.speechRate)
                textToSpeech?.speak(textToPlay, TextToSpeech.QUEUE_FLUSH, null, "OPENASSIST_UTTERANCE_ID")
                isPlayingAudio = true
                btnAudioPlayPause?.setImageResource(R.drawable.ic_pause)
                textAudioStatus?.text = "Speaking aloud..."
            }
        }
    }

    private fun stopAudioPlayback() {
        textToSpeech?.stop()
        isPlayingAudio = false
        currentSpeechText = null
        try {
            webView.evaluateJavascript("try { document.querySelectorAll('audio, video').forEach(el => el.pause()); } catch(e){}", null)
        } catch (e: Exception) {}
        layoutAudioController?.visibility = View.GONE
        layoutAudioOrb?.visibility = View.GONE
        Ui.showMascotToast(this, "Speech stopped", prefs.mascotStyle, type = ToastType.SPEECH)
    }

    private fun cycleSpeechSpeed() {
        currentSpeedIndex = (currentSpeedIndex + 1) % speechRates.size
        val newRate = speechRates[currentSpeedIndex]
        prefs.speechRate = newRate
        btnAudioSpeed?.text = "${newRate}x"
        textToSpeech?.setSpeechRate(newRate)
        if (isPlayingAudio && !currentSpeechText.isNullOrBlank()) {
            textToSpeech?.speak(currentSpeechText, TextToSpeech.QUEUE_FLUSH, null, "OPENASSIST_UTTERANCE_ID")
        }
        Ui.showMascotToast(this, "Speed: ${newRate}x", prefs.mascotStyle, type = ToastType.SPEECH)
    }

    private fun minimizeAudioController() {
        prefs.isAudioBarMinimized = true
        layoutAudioController?.visibility = View.GONE
        layoutAudioOrb?.visibility = View.VISIBLE
        Ui.showMascotToast(this, "Audio bar minimized", prefs.mascotStyle, type = ToastType.GENERAL)
    }

    private fun expandAudioController() {
        prefs.isAudioBarMinimized = false
        layoutAudioOrb?.visibility = View.GONE
        layoutAudioController?.visibility = View.VISIBLE
    }

    private fun toggleAutoSpeak() {
        val newState = !prefs.isAutoSpeakEnabled
        prefs.isAutoSpeakEnabled = newState
        btnWebAutoSpeak?.setImageResource(if (newState) R.drawable.ic_volume_up else R.drawable.ic_volume_off)
        btnWebAutoSpeak?.imageTintList = ColorStateList.valueOf(if (newState) theme.ACCENT else theme.MUTED)
        if (!newState && isPlayingAudio) {
            stopAudioPlayback()
        }
        val label = if (newState) "Auto-Read Aloud: Enabled" else "Auto-Read Aloud: Muted"
        Ui.showMascotToast(this, label, prefs.mascotStyle, type = ToastType.SPEECH)
    }

    private fun applyWebsiteTheme() {
        val isDark = prefs.isDarkMode
        val curUrl = currentUrl.lowercase()
        val isExternalDoc = curUrl.contains("docs.google.com") || curUrl.contains("drive.google.com") || curUrl.contains("sheets.google.com")

        val themeJs = """
            (function() {
                try {
                    const isDark = $isDark;
                    const isExternalDoc = $isExternalDoc;
                    
                    // 1. Responsive styling for code blocks and tables (prevents black block rendering and offscreen cutoffs)
                    if (!document.getElementById('__openassist_style_fix')) {
                        const style = document.createElement('style');
                        style.id = '__openassist_style_fix';
                        style.textContent = `
                            pre, code {
                                max-width: 100% !important;
                                overflow-x: auto !important;
                                white-space: pre-wrap !important;
                                word-break: break-word !important;
                            }
                            table {
                                max-width: 100% !important;
                                overflow-x: auto !important;
                                display: block !important;
                            }
                        `;
                        (document.head || document.documentElement).appendChild(style);
                    }

                    // For external documents like Google Docs, do not invert or force dark classes
                    if (isExternalDoc) {
                        return;
                    }

                    // 2. Meta color-scheme
                    let meta = document.querySelector('meta[name="color-scheme"]');
                    if (!meta) {
                        meta = document.createElement('meta');
                        meta.name = 'color-scheme';
                        document.head.appendChild(meta);
                    }
                    meta.content = isDark ? 'dark' : 'light';
                    
                    // 3. HTML & Body attributes & classes (for recognized web AI chat interfaces)
                    if (isDark) {
                        document.documentElement.classList.add('dark', 'dark-theme', 'theme-dark');
                        document.documentElement.classList.remove('light', 'light-theme', 'theme-light');
                        document.documentElement.setAttribute('data-theme', 'dark');
                        document.documentElement.setAttribute('color-scheme', 'dark');
                        document.documentElement.style.colorScheme = 'dark';
                        if (document.body) {
                            document.body.classList.add('dark', 'dark-theme', 'theme-dark');
                            document.body.classList.remove('light', 'light-theme', 'theme-light');
                            document.body.setAttribute('data-theme', 'dark');
                            document.body.style.colorScheme = 'dark';
                        }
                    } else {
                        document.documentElement.classList.remove('dark', 'dark-theme', 'theme-dark');
                        document.documentElement.classList.add('light', 'light-theme', 'theme-light');
                        document.documentElement.setAttribute('data-theme', 'light');
                        document.documentElement.setAttribute('color-scheme', 'light');
                        document.documentElement.style.colorScheme = 'light';
                        if (document.body) {
                            document.body.classList.remove('dark', 'dark-theme', 'theme-dark');
                            document.body.classList.add('light', 'light-theme', 'theme-light');
                            document.body.setAttribute('data-theme', 'light');
                            document.body.style.colorScheme = 'light';
                        }
                    }

                    // 4. LocalStorage persistence
                    try { localStorage.setItem('theme', isDark ? 'dark' : 'light'); } catch(e){}
                    try { localStorage.setItem('color-theme', isDark ? 'dark' : 'light'); } catch(e){}
                    try { localStorage.setItem('selectedTheme', isDark ? 'dark' : 'light'); } catch(e){}
                } catch(e) {}
            })();
        """.trimIndent()
        webView.evaluateJavascript(themeJs, null)
    }

    private fun injectPendingAssistData() {
        val textToInject = pendingText
        val filesToInject = pendingFiles
        val shouldSubmit = pendingSubmit

        val hasImage = !filesToInject.isNullOrEmpty() || (StagedFiles.lastSelection != null && StagedFiles.lastSelection?.file?.exists() == true && intent?.getStringExtra(EXTRA_MODE) == MODE_IMAGE)
        val firstFilePath = filesToInject?.firstOrNull() ?: if (intent?.getStringExtra(EXTRA_MODE) == MODE_IMAGE) StagedFiles.lastSelection?.file?.absolutePath else null

        if (textToInject.isNullOrBlank() && !hasImage) {
            return
        }

        // 1. Inject image if present
        if (hasImage && firstFilePath != null) {
            try {
                val file = File(firstFilePath)
                if (file.exists() && file.length() < 15 * 1024 * 1024) {
                    val bm = BitmapFactory.decodeFile(file.absolutePath)
                    if (bm != null) {
                        ClipboardHelper.copyImage(this, bm)
                    }
                    val b64 = encodeFileToBase64(file)
                    if (b64 != null) {
                        injectBase64ImageIntoWeb(b64, "image/png")
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.e("WebActivity", "Error staging image attachment", t)
            }
        }

        // 2. Inject text if present (sequenced after image attachment to avoid wiping draft image chips)
        if (!textToInject.isNullOrBlank()) {
            if (hasImage) {
                webView.postDelayed({
                    injectTextIntoWeb(textToInject, shouldSubmit)
                }, 550)
            } else {
                injectTextIntoWeb(textToInject, shouldSubmit)
            }
        }

        // Clear consumed pending values
        pendingText = null
        pendingFiles = null
        pendingSubmit = false
    }

    private fun encodeFileToBase64(file: File): String? {
        return try {
            val bytes = file.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    private fun injectTextIntoWeb(text: String, submit: Boolean) {
        val activeService = AiServices.current(prefs)
        prefs.addAssistantHistory(text, activeService.id, activeService.name, currentUrl)

        val jsonPayload = org.json.JSONObject.quote(text)
        val js = """
            (function() {
                const textPayload = $jsonPayload;
                let attempts = 0;
                function tryInject() {
                    attempts++;
                    const el = document.querySelector('rich-textarea [contenteditable="true"]') ||
                               document.querySelector('div.ql-editor') ||
                               document.querySelector('#prompt-textarea') ||
                               document.querySelector('div[contenteditable="true"][role="textbox"]') ||
                               document.querySelector('div[contenteditable="true"]') ||
                               document.querySelector('textarea#chat-input') ||
                               document.querySelector('textarea') ||
                               document.querySelector('input[type="text"]');
                    if (el) {
                        el.focus();
                        try {
                            document.execCommand('selectAll', false, null);
                            document.execCommand('insertText', false, textPayload);
                        } catch(e) {}
                        
                        if (el.tagName === 'TEXTAREA' || el.tagName === 'INPUT') {
                            if (!el.value || !el.value.includes(textPayload)) {
                                el.value = (el.value ? el.value + ' ' : '') + textPayload;
                            }
                            el.dispatchEvent(new Event('input', { bubbles: true }));
                            el.dispatchEvent(new Event('change', { bubbles: true }));
                        } else if (el.isContentEditable) {
                            if (!el.textContent || !el.textContent.includes(textPayload)) {
                                while (el.firstChild) { el.removeChild(el.firstChild); }
                                const p = document.createElement('p');
                                p.textContent = textPayload;
                                el.appendChild(p);
                            }
                            el.dispatchEvent(new Event('input', { bubbles: true }));
                        }
                        
                        if ($submit) {
                            setTimeout(() => {
                                const btn = document.querySelector('button[aria-label*="Send"], button[aria-label*="Submit"], [data-testid="send-button"], button.send-button, button.send-button-container, button[aria-label*="prompt"]');
                                if (btn && !btn.disabled) btn.click();
                            }, 500);
                        }
                        return true;
                    }
                    if (attempts < 25) {
                        setTimeout(tryInject, 350);
                    }
                    return false;
                }
                tryInject();
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun injectBase64ImageIntoWeb(b64: String, mimeType: String) {
        val js = """
            (function() {
                let attempts = 0;
                function tryInjectImage() {
                    attempts++;
                    try {
                        const byteCharacters = atob("$b64");
                        const byteNumbers = new Uint8Array(byteCharacters.length);
                        for (let i = 0; i < byteCharacters.length; i++) {
                            byteNumbers[i] = byteCharacters.charCodeAt(i);
                        }
                        const file = new File([byteNumbers], "circled_screenshot.png", { type: "$mimeType" });
                        
                        const dt = new DataTransfer();
                        dt.items.add(file);
                        
                        let handled = false;
                        const fileInput = document.querySelector('input[type="file"]');
                        if (fileInput) {
                            try {
                                fileInput.files = dt.files;
                                fileInput.dispatchEvent(new Event('change', { bubbles: true }));
                                fileInput.dispatchEvent(new Event('input', { bubbles: true }));
                                handled = true;
                            } catch(e) {}
                        }

                        const target = document.querySelector('rich-textarea [contenteditable="true"]') ||
                                       document.querySelector('div.ql-editor') ||
                                       document.querySelector('#prompt-textarea') ||
                                       document.querySelector('div[contenteditable="true"][role="textbox"]') ||
                                       document.querySelector('div[contenteditable="true"]') ||
                                       document.querySelector('textarea') ||
                                       document.querySelector('body');
                        if (target) {
                            target.focus();
                            const pasteEvent = new ClipboardEvent('paste', {
                                bubbles: true,
                                cancelable: true,
                                clipboardData: dt
                            });
                            target.dispatchEvent(pasteEvent);
                            
                            const dropEvent = new DragEvent('drop', {
                                bubbles: true,
                                cancelable: true,
                                dataTransfer: dt
                            });
                            target.dispatchEvent(dropEvent);
                            handled = true;
                        }
                        if (handled) return true;
                    } catch(e) {}
                    
                    if (attempts < 25) {
                        setTimeout(tryInjectImage, 350);
                    }
                    return false;
                }
                tryInjectImage();
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun attachStagedFilesOrClipboard() {
        try {
            val last = StagedFiles.lastSelection
            if (last != null && last.file.exists()) {
                val bm = BitmapFactory.decodeFile(last.file.absolutePath)
                if (bm != null) {
                    ClipboardHelper.copyImage(this, bm)
                }
                val b64 = encodeFileToBase64(last.file)
                if (b64 != null) {
                    injectBase64ImageIntoWeb(b64, "image/png")
                }
                Ui.showMascotToast(this, "Circled screenshot attached & copied to clipboard!", prefs.mascotStyle, duration = Toast.LENGTH_LONG, type = ToastType.CIRCLE)
                return
            }

            // Fallback to text on clipboard
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipText = cm?.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
            if (!clipText.isNullOrBlank()) {
                injectTextIntoWeb(clipText, false)
                Ui.showMascotToast(this, "Pasted clipboard text into chat", prefs.mascotStyle, type = ToastType.CIRCLE)
                return
            }

            Ui.showMascotToast(this, "No circled screenshot or clipboard content to attach", prefs.mascotStyle, type = ToastType.CIRCLE)
        } catch (e: Exception) {
            Ui.showMascotToast(this, "Could not attach selection: ${e.message}", prefs.mascotStyle, type = ToastType.CIRCLE)
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
            Ui.showMascotToast(this, "Listening...", prefs.mascotStyle, type = ToastType.SPEECH)
            speech?.start()
        }
    }

    override fun onStarting(engineName: String) {
        runOnUiThread {
            Ui.showMascotToast(this, "Listening via $engineName...", prefs.mascotStyle, type = ToastType.SPEECH)
        }
    }

    override fun onPartial(text: String) {}

    override fun onFinal(text: String) {
        runOnUiThread {
            isDictating = false
            btnMic.imageTintList = ColorStateList.valueOf(theme.TEXT)
            if (text.isNotBlank()) {
                val outText = if (prefs.voiceLang == VoiceLang.HINGLISH) Translit.toHinglish(text) else text
                injectTextIntoWeb(outText, prefs.autoSubmit)
                Ui.showMascotToast(this, "Dictated: $outText", prefs.mascotStyle, type = ToastType.SPEECH)
            }
        }
    }

    override fun onError(message: String) {
        runOnUiThread {
            isDictating = false
            btnMic.imageTintList = ColorStateList.valueOf(theme.TEXT)
            Ui.showMascotToast(this, message, prefs.mascotStyle, type = ToastType.SPEECH)
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

    private fun getMascotInfo(style: String): Pair<Int, String> {
        val m = MascotHelper.getMascot(style)
        return Pair(m.drawableRes, m.name)
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
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
        } catch (e: Exception) {}
    }

    
    private fun themedBuilder(): AlertDialog.Builder {
        val themeRes = if (theme.isDark) R.style.Theme_OpenAssist_Dark else R.style.Theme_OpenAssist_Light
        return AlertDialog.Builder(this, themeRes)
    }

    private fun showThemedDialog(builder: AlertDialog.Builder): AlertDialog {
        val dialog = builder.create()
        dialog.show()
        dialog.window?.let { win ->
            win.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            win.decorView.setBackgroundColor(Color.TRANSPARENT)
            val dm = resources.displayMetrics
            win.setLayout(
                (dm.widthPixels * 0.92).toInt().coerceAtMost(Ui.dp(this, 440)),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        Ui.styleDialog(dialog, theme)
        return dialog
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = File(File(cacheDir, SelectionProvider.DIR), "camera")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        return File.createTempFile(imageFileName, ".jpg", storageDir)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_FILE_CHOOSER) {
            if (fileUploadCallback == null) return

            var results: Array<Uri>? = null
            if (resultCode == Activity.RESULT_OK) {
                if (data == null || (data.data == null && data.clipData == null)) {
                    // Camera photo capture
                    if (cameraPhotoUri != null) {
                        results = arrayOf(cameraPhotoUri!!)
                    }
                } else {
                    val clipData = data.clipData
                    val dataString = data.dataString
                    if (clipData != null) {
                        val uriList = mutableListOf<Uri>()
                        for (i in 0 until clipData.itemCount) {
                            uriList.add(clipData.getItemAt(i).uri)
                        }
                        if (uriList.isNotEmpty()) {
                            results = uriList.toTypedArray()
                        }
                    } else if (dataString != null) {
                        results = arrayOf(Uri.parse(dataString))
                    }
                }
            }

            fileUploadCallback?.onReceiveValue(results)
            fileUploadCallback = null
            cameraPhotoUri = null
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_WEB_PERMISSIONS) {
            val req = pendingPermissionRequest ?: return
            pendingPermissionRequest = null

            val grantedResources = mutableListOf<String>()
            val requested = req.resources.toList()

            var audioGranted = true
            var videoGranted = true

            for (i in permissions.indices) {
                val perm = permissions[i]
                val granted = grantResults.getOrNull(i) == PackageManager.PERMISSION_GRANTED
                if (perm == android.Manifest.permission.RECORD_AUDIO && !granted) {
                    audioGranted = false
                }
                if (perm == android.Manifest.permission.CAMERA && !granted) {
                    videoGranted = false
                }
            }

            if (requested.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) && audioGranted) {
                grantedResources.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
            }
            if (requested.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) && videoGranted) {
                grantedResources.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
            }

            runOnUiThread {
                try {
                    if (grantedResources.isNotEmpty()) {
                        req.grant(grantedResources.toTypedArray())
                    } else {
                        req.deny()
                    }
                } catch (e: Exception) {
                    // Ignore already handled
                }
            }
        }
    }

    companion object {
        const val REQUEST_FILE_CHOOSER = 1002
        const val REQUEST_WEB_PERMISSIONS = 1003

        const val EXTRA_URL = "extra_url"
        const val EXTRA_QUERY_TEXT = "extra_query"
        const val EXTRA_SERVICE_ID = "extra_service_id"
        const val EXTRA_TITLE = "extra_title"
        const val ACTION_ASSIST = "com.openassist.app.ACTION_ASSIST"
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_SUBMIT = "extra_submit"
        const val EXTRA_FILES = "extra_files"
        const val MODE_TEXT = "mode_text"
        const val MODE_IMAGE = "mode_image"

        fun isSafeWebUrl(url: String?): Boolean {
            if (url.isNullOrBlank()) return false
            val trimmed = url.trim()
            if (trimmed.contains('\r') || trimmed.contains('\n')) return false
            val uri = try { Uri.parse(trimmed) } catch (_: Exception) { return false }
            val scheme = uri.scheme?.lowercase() ?: return false
            if (scheme != "http" && scheme != "https") return false
            val host = uri.host?.lowercase() ?: return false
            if (host.isBlank()) return false
            if (scheme == "http") {
                val isLocalHost = host == "localhost" || host == "127.0.0.1" ||
                        host.startsWith("192.168.") || host.startsWith("10.") ||
                        host.startsWith("172.16.") || host.startsWith("172.17.") ||
                        host.startsWith("172.18.") || host.startsWith("172.19.") ||
                        host.startsWith("172.2") || host.startsWith("172.30.") ||
                        host.startsWith("172.31.")
                if (!isLocalHost) return false
            }
            return true
        }
    }
}
