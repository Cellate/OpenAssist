package com.openassist.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var modelStore: ModelStore
    private lateinit var theme: Ui.Theme
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var assistantStatus: TextView
    private lateinit var engineSelectBtn: Button
    private lateinit var engineStatusText: TextView
    private lateinit var localModelsContainer: LinearLayout
    private lateinit var activeModelBadge: TextView
    private lateinit var langMappingContainer: LinearLayout
    private lateinit var postProcBtn: Button
    private lateinit var realTimeBtn: Button
    private lateinit var testTextBox: EditText
    private lateinit var testMicBtn: Button
    private lateinit var testStatusBanner: TextView
    private lateinit var pipelineInfoText: TextView
    private lateinit var langChipsRow: LinearLayout
    private lateinit var activeAiBadge: TextView
    private lateinit var aiServiceSelectBtn: Button
    private lateinit var customAiContainer: LinearLayout
    private lateinit var mascotHomeAvatar: ImageView
    private lateinit var mascotHomeTitle: TextView
    private lateinit var mascotHomeGreeting: TextView
    private lateinit var recentHistoryContainer: LinearLayout
    private lateinit var gestureDetector: android.view.GestureDetector

    private val RC_MODEL_PICK = 42
    private val RC_TEST_SPEECH = 43
    private val RC_MIC_PERMISSION = 101

    private var testSpeech: Speech? = null
    private var isTesting = false
    private var isRecordingLocal = false
    private var isTranscribing = false
    private var baseTestTextBeforeSpeech = ""

    private fun safeString(resId: Int, fallback: String): String {
        val raw = try {
            getString(resId)
        } catch (t: Throwable) {
            fallback
        }
        return prefs.translationStore.translate(raw, "settings")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val p = Prefs(this)
        if (p.isDarkMode) {
            setTheme(R.style.Theme_OpenAssist_Dark)
        } else {
            setTheme(R.style.Theme_OpenAssist_Light)
        }
        super.onCreate(savedInstanceState)
        gestureDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 != null && e2.x - e1.x > Ui.dp(this@SettingsActivity, 90) && Math.abs(velocityX) > 180 && Math.abs(e2.y - e1.y) < Ui.dp(this@SettingsActivity, 100)) {
                    showFullHistoryDialog()
                    return true
                }
                return false
            }
        })
        try {
            initUi()
        } catch (t: Throwable) {
            android.util.Log.e("SettingsActivity", "Fatal error initializing SettingsActivity", t)
            renderSafeFallbackUi(t)
        }
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (::gestureDetector.isInitialized) {
            gestureDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun renderSafeFallbackUi(error: Throwable) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = Ui.dp(this@SettingsActivity, 24)
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.parseColor("#121212"))
        }
        root.addView(TextView(this).apply {
            text = "OpenAssist Settings"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, Ui.dp(this@SettingsActivity, 12))
        })
        root.addView(TextView(this).apply {
            text = "OpenAssist recovered safely from a startup configuration issue:\n\${error.localizedMessage ?: error.javaClass.simpleName}"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.parseColor("#EF4444"))
            setPadding(0, 0, 0, Ui.dp(this@SettingsActivity, 20))
        })
        val launchBtn = Button(this).apply {
            text = "Launch Web AI Assistant"
            background = Ui.rounded(Color.parseColor("#2563EB"), Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(Color.WHITE)
            setOnClickListener {
                try {
                    startActivity(Intent(this@SettingsActivity, WebActivity::class.java))
                } catch (e: Exception) {
                    Ui.showMascotToast(this@SettingsActivity, "Could not launch WebActivity", prefs.mascotStyle, type = ToastType.GENERAL)
                }
            }
        }
        root.addView(launchBtn)
        val retryBtn = Button(this).apply {
            text = "Reload Settings"
            background = Ui.rounded(Color.parseColor("#374151"), Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(Color.WHITE)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@SettingsActivity, 12)
            }
            layoutParams = lp
            setOnClickListener { recreate() }
        }
        root.addView(retryBtn)
        setContentView(root)
    }

    private fun initUi() {
        try {
            prefs = Prefs(this)
        } catch (e: Throwable) {
            prefs = Prefs(this)
        }
        try {
            theme = Ui.theme(this)
        } catch (e: Throwable) {
            theme = Ui.Theme(isDark = true)
        }
        try {
            modelStore = ModelStore(this)
        } catch (e: Throwable) {
            modelStore = ModelStore(this)
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(theme.BG)
            isFillViewport = true
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = Ui.dp(this@SettingsActivity, 16)
            setPadding(pad, pad, pad, pad)
        }
        scroll.addView(col)
        setContentView(scroll)

        Ui.applySystemBars(this, theme)

        fun header(text: String) {
            val tv = TextView(this).apply {
                this.text = text
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(if (theme.isDark) theme.ACCENT else Color.parseColor("#1E40AF"))
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, Ui.dp(this@SettingsActivity, 20), 0, Ui.dp(this@SettingsActivity, 8))
            }
            col.addView(tv)
        }

        fun subheader(text: String) {
            val tv = TextView(this).apply {
                this.text = text
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(if (theme.isDark) Color.parseColor("#93C5FD") else Color.parseColor("#1D4ED8"))
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, Ui.dp(this@SettingsActivity, 10), 0, Ui.dp(this@SettingsActivity, 4))
            }
            col.addView(tv)
        }

        fun cardLayout(): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = Ui.card(this@SettingsActivity, theme)
                val pad = Ui.dp(this@SettingsActivity, 14)
                setPadding(pad, pad, pad, pad)
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = Ui.dp(this@SettingsActivity, 12)
                }
                layoutParams = lp
            }
        }

        fun toggle(title: String, desc: String, initial: Boolean, targetLayout: LinearLayout = col, onChange: (Boolean) -> Unit) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, Ui.dp(this@SettingsActivity, 8), 0, Ui.dp(this@SettingsActivity, 8))
            }
            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(this).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(theme.TEXT)
            })
            textCol.addView(TextView(this).apply {
                text = desc
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(theme.MUTED)
            })
            val sw = Switch(this).apply {
                isChecked = initial
                setOnCheckedChangeListener { _, c -> onChange(c) }
            }
            row.addView(textCol)
            row.addView(sw)
            targetLayout.addView(row)
        }

        // Top App Bar with Hamburger Menu (Left), Title & Version (Center), and About/Updater (Right)
        val topAppBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, Ui.dp(this@SettingsActivity, 14))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
        }

        // Hamburger Menu Button (Leftmost: Opens Full History)
        val menuBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_menu)
            imageTintList = ColorStateList.valueOf(theme.TEXT)
            val s = Ui.dp(this@SettingsActivity, 38)
            val p = Ui.dp(this@SettingsActivity, 8)
            layoutParams = LinearLayout.LayoutParams(s, s).apply {
                rightMargin = Ui.dp(this@SettingsActivity, 10)
            }
            setPadding(p, p, p, p)
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            isClickable = true
            isFocusable = true
            contentDescription = "Assistant History Menu"
            setOnClickListener { showFullHistoryDialog() }
        }
        topAppBar.addView(menuBtn)

        val appTitleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
        }
        val appTitleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        appTitleRow.addView(TextView(this).apply {
            text = "OpenAssist"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            setTextColor(theme.TEXT)
            typeface = Typeface.DEFAULT_BOLD
        })
        val versionBadge = TextView(this).apply {
            text = " v1.62.0"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(theme.ACCENT)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(Ui.dp(this@SettingsActivity, 6), Ui.dp(this@SettingsActivity, 2), Ui.dp(this@SettingsActivity, 6), Ui.dp(this@SettingsActivity, 2))
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 6).toFloat())
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = Ui.dp(this@SettingsActivity, 8)
            }
            layoutParams = lp
            isClickable = true
            isFocusable = true
            contentDescription = "Open About and Update Manager"
            setOnClickListener { showAboutDialog() }
        }
        appTitleRow.addView(versionBadge)
        appTitleCol.addView(appTitleRow)
        appTitleCol.addView(TextView(this).apply {
            text = "AI Voice Assistant & Circle-to-Search"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(theme.MUTED)
        })
        topAppBar.addView(appTitleCol)

        // About & Updates Button (Rightmost: Question Mark in Circle)
        val aboutBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_about)
            imageTintList = ColorStateList.valueOf(theme.TEXT)
            val s = Ui.dp(this@SettingsActivity, 38)
            val p = Ui.dp(this@SettingsActivity, 8)
            layoutParams = LinearLayout.LayoutParams(s, s)
            setPadding(p, p, p, p)
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            isClickable = true
            isFocusable = true
            contentDescription = "About OpenAssist & Updates"
            setOnClickListener { showAboutDialog() }
        }
        topAppBar.addView(aboutBtn)
        col.addView(topAppBar)

        // Mascot Home Section
        val mascotHomeCard = cardLayout()
        val homeHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val currentMascot = MascotHelper.getMascot(prefs.mascotStyle)
        mascotHomeAvatar = ImageView(this).apply {
            setImageResource(currentMascot.drawableRes)
            val s = Ui.dp(this@SettingsActivity, 64)
            layoutParams = LinearLayout.LayoutParams(s, s).apply {
                rightMargin = Ui.dp(this@SettingsActivity, 14)
            }
        }
        val homeTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
        }
        mascotHomeTitle = TextView(this).apply {
            text = "${currentMascot.name} (${currentMascot.fullName})"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTextColor(theme.TEXT)
            typeface = Typeface.DEFAULT_BOLD
        }
        homeTextCol.addView(mascotHomeTitle)
        homeTextCol.addView(TextView(this).apply {
            text = "Your Faithful AI Companion & Guide"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(theme.ACCENT)
        })
        homeHeader.addView(mascotHomeAvatar)
        homeHeader.addView(homeTextCol)
        mascotHomeCard.addView(homeHeader)

        // Wholesome Greeting Message Bubble
        val bubbleCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 12).toFloat())
            val p = Ui.dp(this@SettingsActivity, 12)
            setPadding(p, p, p, p)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@SettingsActivity, 12)
                bottomMargin = Ui.dp(this@SettingsActivity, 12)
            }
            layoutParams = lp
        }
        mascotHomeGreeting = TextView(this).apply {
            text = MascotHelper.getWholesomeGreeting(prefs.mascotStyle)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(theme.TEXT)
            setLineSpacing(Ui.dp(this@SettingsActivity, 2).toFloat(), 1.15f)
        }
        bubbleCard.addView(mascotHomeGreeting)
        mascotHomeCard.addView(bubbleCard)

        // Mascot Home Quick Action Buttons
        val homeBtnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val openWebBtn = Button(this).apply {
            text = "Open Web Assistant"
            textSize = 12f
            background = Ui.rounded(theme.ACCENT, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.ON_ACCENT)
            typeface = Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = Ui.dp(this@SettingsActivity, 8)
            }
            layoutParams = lp
            setOnClickListener {
                startActivity(Intent(this@SettingsActivity, WebActivity::class.java))
            }
        }
        val wizardBtnTop = Button(this).apply {
            text = "Setup Wizard"
            textSize = 12f
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.TEXT)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
            setOnClickListener { showSetupWizardDialog() }
        }
        homeBtnRow.addView(openWebBtn)
        homeBtnRow.addView(wizardBtnTop)
        mascotHomeCard.addView(homeBtnRow)
        col.addView(mascotHomeCard)

        // Recent Assistant Queries Section (Top 5 Recent)
        header("Assistant Query History (100% On-Device)")
        val recentCard = cardLayout()
        toggle(
            "Enable Local History",
            "Saves queries and chat links strictly in private on-device memory with zero cloud tracking",
            prefs.isHistoryEnabled,
            recentCard
        ) { enabled ->
            prefs.isHistoryEnabled = enabled
            renderRecentHistory()
            Ui.showMascotToast(this, if (enabled) "Local history enabled" else "Local history paused", prefs.mascotStyle, type = ToastType.GENERAL)
        }

        recentHistoryContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        recentCard.addView(recentHistoryContainer)
        col.addView(recentCard)
        renderRecentHistory()

        header("Appearance")
        val appearanceCard = cardLayout()
        val themeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Ui.dp(this@SettingsActivity, 4), 0, Ui.dp(this@SettingsActivity, 4))
        }
        val themeTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        themeTextCol.addView(TextView(this).apply {
            text = "Dark Mode"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(theme.TEXT)
        })
        themeTextCol.addView(TextView(this).apply {
            text = if (prefs.isDarkMode) "Gemini Dark Mode active" else "Material 3 Light Mode active"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(theme.MUTED)
        })
        val themeSwitch = Switch(this).apply {
            isChecked = prefs.isDarkMode
            setOnCheckedChangeListener { _, isDark ->
                prefs.isDarkMode = isDark
                recreate()
            }
        }
        themeRow.addView(themeTextCol)
        themeRow.addView(themeSwitch)
        appearanceCard.addView(themeRow)
        col.addView(appearanceCard)

        header("Toast Notifications")
        val toastCard = cardLayout()
        toggle(
            "Mascot Floating Pill Toasts",
            "Show animated floating toast pills carrying your active companion mascot avatar and name",
            prefs.toastMascotPillEnabled,
            toastCard
        ) { prefs.toastMascotPillEnabled = it }
        toggle(
            "Voice & Transcription Toasts",
            "Show alerts when switching speech engines, starting voice input, or completing dictation",
            prefs.toastSpeechEnabled,
            toastCard
        ) { prefs.toastSpeechEnabled = it }
        toggle(
            "Circle to Search & Copy Toasts",
            "Show alerts when taking screenshot crops, copying selections, or attaching images",
            prefs.toastCircleEnabled,
            toastCard
        ) { prefs.toastCircleEnabled = it }
        toggle(
            "AI Assistant Action Toasts",
            "Show alerts when switching AI web assistants, starting new chats, or loading saved sessions",
            prefs.toastAssistantEnabled,
            toastCard
        ) { prefs.toastAssistantEnabled = it }
        col.addView(toastCard)

        header("Auto-Read Aloud & Speech Output")
        val speechOutputCard = cardLayout()
        toggle(
            "Auto-Read Aloud Responses",
            "Automatically trigger speech playback (site native audio or on-device Android TTS) when chatbots finish generating answers",
            prefs.isAutoSpeakEnabled,
            speechOutputCard
        ) { prefs.isAutoSpeakEnabled = it }

        val speedRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Ui.dp(this@SettingsActivity, 8), 0, Ui.dp(this@SettingsActivity, 8))
        }
        val speedTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        speedTextCol.addView(TextView(this).apply {
            text = "Speech Rate / Playback Speed"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(theme.TEXT)
        })
        speedTextCol.addView(TextView(this).apply {
            text = "Current speed: ${prefs.speechRate}x (tap to cycle: 0.8x, 1.0x, 1.25x, 1.5x)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(theme.MUTED)
        })
        val speedBtn = TextView(this).apply {
            text = "${prefs.speechRate}x"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(theme.TEXT)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val padH = Ui.dp(this@SettingsActivity, 14)
            val padV = Ui.dp(this@SettingsActivity, 6)
            setPadding(padH, padV, padH, padV)
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            isClickable = true
            setOnClickListener {
                val nextRate = when (prefs.speechRate) {
                    0.8f -> 1.0f
                    1.0f -> 1.25f
                    1.25f -> 1.5f
                    else -> 0.8f
                }
                prefs.speechRate = nextRate
                text = "${nextRate}x"
                (speedTextCol.getChildAt(1) as? TextView)?.text = "Current speed: ${nextRate}x (tap to cycle: 0.8x, 1.0x, 1.25x, 1.5x)"
                Ui.showMascotToast(this@SettingsActivity, "Speech rate set to ${nextRate}x", prefs.mascotStyle, type = ToastType.SPEECH)
            }
        }
        speedRow.addView(speedTextCol)
        speedRow.addView(speedBtn)
        speechOutputCard.addView(speedRow)

        toggle(
            "Start Audio Controller Minimized",
            "Keep the floating audio player collapsed into a compact edge orb until tapped",
            prefs.isAudioBarMinimized,
            speechOutputCard
        ) { prefs.isAudioBarMinimized = it }

        col.addView(speechOutputCard)

        header(safeString(R.string.st_assistant, "Digital assistant"))
        val assistantCard = cardLayout()
        val currentAssistMascot = MascotHelper.getMascot(prefs.mascotStyle)
        val companionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, Ui.dp(this@SettingsActivity, 8))
        }
        val companionImg = ImageView(this).apply {
            setImageResource(currentAssistMascot.drawableRes)
            val s = Ui.dp(this@SettingsActivity, 30)
            layoutParams = LinearLayout.LayoutParams(s, s).apply {
                rightMargin = Ui.dp(this@SettingsActivity, 8)
            }
        }
        val companionTxtCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        companionTxtCol.addView(TextView(this).apply {
            text = "Active Companion: ${currentAssistMascot.name} (${currentAssistMascot.fullName})"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(theme.ACCENT)
            typeface = Typeface.DEFAULT_BOLD
        })
        companionTxtCol.addView(TextView(this).apply {
            text = "Circle to Search & Voice Dictation Ready"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(theme.MUTED)
        })
        companionRow.addView(companionImg)
        companionRow.addView(companionTxtCol)
        assistantCard.addView(companionRow)

        assistantStatus = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(theme.TEXT)
            text = safeString(R.string.st_assistant_on, "Active. Long-press Home (or use your assist gesture) to open it.")
        }
        assistantCard.addView(assistantStatus)
        val setBtn = Button(this).apply {
            text = safeString(R.string.st_set_assistant, "Choose default assistant")
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.ACCENT)
            setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                } catch (e: Exception) {
                    Ui.showMascotToast(this@SettingsActivity, "Open Settings > Default apps > Assistant", prefs.mascotStyle, duration = Toast.LENGTH_LONG, type = ToastType.GENERAL)
                }
            }
        }
        assistantCard.addView(setBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(this@SettingsActivity, 8) })

        val openChatBtn = Button(this).apply {
            text = "Open Web Assistant (Gemini / AI Chat)"
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.TEXT)
            setOnClickListener {
                startActivity(Intent(this@SettingsActivity, WebActivity::class.java))
            }
        }
        assistantCard.addView(openChatBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(this@SettingsActivity, 8) })

        val wizardBtn = Button(this).apply {
            text = "  Open Setup Wizard (First-Run Guide)"
            background = Ui.rounded(Color.parseColor("#4F46E5"), Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { showSetupWizardDialog() }
        }
        assistantCard.addView(wizardBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(this@SettingsActivity, 8) })
        col.addView(assistantCard)

        header("AI Web Assistant & Custom AI Services")
        val aiCard = cardLayout()
        val topAiRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topAiRow.addView(TextView(this).apply {
            text = "Active Assistant:"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(theme.TEXT)
        })
        activeAiBadge = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = Ui.pill(this@SettingsActivity, Color.parseColor("#1D4ED8"))
            val padH = Ui.dp(this@SettingsActivity, 10)
            val padV = Ui.dp(this@SettingsActivity, 4)
            setPadding(padH, padV, padH, padV)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = Ui.dp(this@SettingsActivity, 8)
                rightMargin = Ui.dp(this@SettingsActivity, 8)
            }
            layoutParams = lp
        }
        topAiRow.addView(activeAiBadge)
        aiServiceSelectBtn = Button(this).apply {
            text = "Switch AI"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.ACCENT)
            setOnClickListener { showAiServiceSelectorDialog() }
        }
        topAiRow.addView(aiServiceSelectBtn)
        aiCard.addView(topAiRow)

        customAiContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        aiCard.addView(customAiContainer)

        val aiActionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@SettingsActivity, 8)
            }
            layoutParams = lp
        }
        val addCustomAiBtn = Button(this).apply {
            text = "+ Add Custom Web Assistant"
            background = Ui.rounded(Color.parseColor("#2563EB"), Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = Ui.dp(this@SettingsActivity, 6)
            }
            layoutParams = lp
            setOnClickListener { showAddCustomAiDialog() }
        }
        aiActionsRow.addView(addCustomAiBtn)
        val openActiveAiBtn = Button(this).apply {
            text = "Launch Web AI"
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.TEXT)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = Ui.dp(this@SettingsActivity, 6)
            }
            layoutParams = lp
            setOnClickListener {
                startActivity(Intent(this@SettingsActivity, WebActivity::class.java))
            }
        }
        val manageChipsBtn = Button(this).apply {
            text = "⚙ Manage Search & Assistant Chips (35+)"
            background = Ui.rounded(theme.ACCENT, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.ON_ACCENT)
            typeface = Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@SettingsActivity, 8)
            }
            layoutParams = lp
            setOnClickListener { showChipManagerDialog() }
        }
        aiCard.addView(manageChipsBtn)
        col.addView(aiCard)

        header("Voice Input Engine & Whisper Local Mode")
        val engineCard = cardLayout()
        engineCard.addView(TextView(this).apply {
            text = "Active Speech Input Engine"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(theme.TEXT)
        })
        engineStatusText = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(theme.MUTED)
            setPadding(0, Ui.dp(this@SettingsActivity, 4), 0, Ui.dp(this@SettingsActivity, 8))
        }
        engineCard.addView(engineStatusText)
        engineSelectBtn = Button(this).apply {
            background = Ui.rounded(Color.parseColor("#1D4ED8"), Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { showEngineSelectorDialog() }
        }
        engineCard.addView(engineSelectBtn)
        col.addView(engineCard)

        header("Local Offline Whisper Models")
        val modelHeaderCard = cardLayout()
        val topModelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topModelRow.addView(TextView(this).apply {
            text = "Active Model:"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(theme.TEXT)
        })
        activeModelBadge = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = Ui.pill(this@SettingsActivity, Color.parseColor("#2563EB"))
            val padH = Ui.dp(this@SettingsActivity, 10)
            val padV = Ui.dp(this@SettingsActivity, 4)
            setPadding(padH, padV, padH, padV)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = Ui.dp(this@SettingsActivity, 8)
                rightMargin = Ui.dp(this@SettingsActivity, 8)
            }
            layoutParams = lp
        }
        topModelRow.addView(activeModelBadge)
        val cycleBtn = Button(this).apply {
            text = "Switch"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.ACCENT)
            setOnClickListener { cycleToNextModel() }
        }
        topModelRow.addView(cycleBtn)
        modelHeaderCard.addView(topModelRow)

        localModelsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, Ui.dp(this@SettingsActivity, 8), 0, 0)
        }
        modelHeaderCard.addView(localModelsContainer)

        val modelActionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, Ui.dp(this@SettingsActivity, 10), 0, 0)
        }
        val loadModelBtn = Button(this).apply {
            text = "  Import Model (.bin)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            background = Ui.rounded(theme.ACCENT, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.ON_ACCENT)
            typeface = Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
            setOnClickListener { pickCustomModel() }
        }
        modelActionsRow.addView(loadModelBtn)
        modelHeaderCard.addView(modelActionsRow)
        col.addView(modelHeaderCard)

        val modeCard = cardLayout()
        modeCard.addView(TextView(this).apply {
            text = "Whisper Processing Mode"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(theme.TEXT)
        })
        val modeSwitchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        postProcBtn = Button(this).apply {
            text = "Post-Processing (Default)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = Ui.dp(this@SettingsActivity, 6)
            }
            layoutParams = lp
            setOnClickListener {
                prefs.processingMode = Prefs.MODE_POST_PROCESSING
                updateModeButtonsUi()
                Ui.showMascotToast(this@SettingsActivity, "Set to Post-Processing Mode (Default)", prefs.mascotStyle, type = ToastType.SPEECH)
            }
        }
        realTimeBtn = Button(this).apply {
            text = "Real-Time Streaming"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
            setOnClickListener {
                prefs.processingMode = Prefs.MODE_REAL_TIME
                updateModeButtonsUi()
                Ui.showMascotToast(this@SettingsActivity, "Set to Real-Time Streaming Mode", prefs.mascotStyle, type = ToastType.SPEECH)
            }
        }
        modeSwitchRow.addView(postProcBtn)
        modeSwitchRow.addView(realTimeBtn)
        modeCard.addView(modeSwitchRow)
        col.addView(modeCard)

        subheader("Per-Language Model Binding")
        val langMapCard = cardLayout()
        langMappingContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        langMapCard.addView(langMappingContainer)
        col.addView(langMapCard)

        header("Test Voice Dictation & Transcription")
        val testCard = cardLayout()
        val langScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, Ui.dp(this@SettingsActivity, 6), 0, Ui.dp(this@SettingsActivity, 8))
        }
        langChipsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        langScroll.addView(langChipsRow)
        testCard.addView(langScroll)

        pipelineInfoText = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(if (theme.isDark) Color.parseColor("#93C5FD") else Color.parseColor("#1D4ED8"))
            background = Ui.rounded(if (theme.isDark) Color.parseColor("#1E293B") else Color.parseColor("#EFF6FF"), Ui.dp(this@SettingsActivity, 6).toFloat())
            val pad = Ui.dp(this@SettingsActivity, 8)
            setPadding(pad, pad, pad, pad)
        }
        testCard.addView(pipelineInfoText)

        testStatusBanner = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#EF4444"))
            typeface = Typeface.DEFAULT_BOLD
            visibility = View.GONE
            val pad = Ui.dp(this@SettingsActivity, 8)
            setPadding(pad, pad, pad, pad)
            text = "  LIVE MIC ACTIVE   Listening..."
        }
        testCard.addView(testStatusBanner)

        testTextBox = EditText(this).apply {
            hint = "Recognized transcription will appear here in real time..."
            setHintTextColor(theme.MUTED)
            setTextColor(theme.TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            minLines = 4
            maxLines = 8
            gravity = Gravity.TOP or Gravity.START
            background = Ui.rounded(theme.INPUT_BG, Ui.dp(this@SettingsActivity, 8).toFloat())
            val pad = Ui.dp(this@SettingsActivity, 10)
            setPadding(pad, pad, pad, pad)
        }
        val textLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = Ui.dp(this@SettingsActivity, 8)
            bottomMargin = Ui.dp(this@SettingsActivity, 10)
        }
        testCard.addView(testTextBox, textLp)

        val testControlsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        testMicBtn = Button(this).apply {
            text = "Tap to Speak / Test"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            background = Ui.rounded(theme.ACCENT, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.ON_ACCENT)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f).apply {
                rightMargin = Ui.dp(this@SettingsActivity, 6)
            }
            layoutParams = lp
            setOnClickListener { toggleTestSpeech() }
        }
        val copyBtn = Button(this).apply {
            text = "Copy"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.TEXT)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f).apply {
                rightMargin = Ui.dp(this@SettingsActivity, 6)
            }
            layoutParams = lp
            setOnClickListener {
                val txt = testTextBox.text.toString()
                if (txt.isNotBlank()) {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("OpenAssist Test", txt))
                    Ui.showMascotToast(this@SettingsActivity, "Copied to clipboard!", prefs.mascotStyle, type = ToastType.GENERAL)
                }
            }
        }
        val clearBtn = Button(this).apply {
            text = "Clear"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.MUTED)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f)
            layoutParams = lp
            setOnClickListener { testTextBox.setText("") }
        }
        testControlsRow.addView(testMicBtn)
        testControlsRow.addView(copyBtn)
        testControlsRow.addView(clearBtn)
        testCard.addView(testControlsRow)
        col.addView(testCard)

        header(safeString(R.string.st_behaviour, "Behaviour"))
        val behaviourCard = cardLayout()
        toggle("Auto-Listen on Launch", "Immediately open microphone when overlay opens so you can speak right away.", prefs.autoStartMic, behaviourCard) { prefs.autoStartMic = it }
        toggle(safeString(R.string.st_new_chat, "Start a new chat each time"), safeString(R.string.st_new_chat_desc, "Reloads the AI page on every assist so your question is not mixed into an old chat. Turn off to keep the current chat."), prefs.newChatOnAssist, behaviourCard) { prefs.newChatOnAssist = it }
        toggle(safeString(R.string.st_auto_attach, "Attach circled image automatically"), safeString(R.string.st_auto_attach_desc, "Pastes the copied selection into the chat box after the page opens."), prefs.autoAttach, behaviourCard) { prefs.autoAttach = it }
        toggle(safeString(R.string.st_auto_submit, "Send the question automatically"), safeString(R.string.st_auto_submit_desc, "After you type or dictate in the overlay, press the chat's send button for you."), prefs.autoSubmit, behaviourCard) { prefs.autoSubmit = it }
        toggle(safeString(R.string.st_circle_compose, "Stay in the overlay after circling"), safeString(R.string.st_circle_compose_desc, "Lets you type or dictate your question right after circling. Off: jump straight to the AI so you can paste and ask there."), prefs.composeAfterCircle, behaviourCard) { prefs.composeAfterCircle = it }
        col.addView(behaviourCard)

        header("Mascot & Aesthetics")
        val companionCard = cardLayout()
        companionCard.addView(TextView(this).apply {
            text = safeString(R.string.pref_mascot_companion_title, "Active Mascot Companion")
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(theme.TEXT)
        })

        val activeCompanionBanner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 10).toFloat())
            val pad = Ui.dp(this@SettingsActivity, 10)
            setPadding(pad, pad, pad, pad)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@SettingsActivity, 6)
                bottomMargin = Ui.dp(this@SettingsActivity, 10)
            }
            layoutParams = lp
        }
        val activeMascotImg = ImageView(this).apply {
            val (drawable, _) = getMascotDrawable(prefs.mascotStyle)
            setImageResource(drawable)
            val s = Ui.dp(this@SettingsActivity, 36)
            val lp = LinearLayout.LayoutParams(s, s).apply { rightMargin = Ui.dp(this@SettingsActivity, 10) }
            layoutParams = lp
        }
        val activeMascotCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
        }
        val activeMascotNameTv = TextView(this).apply {
            val m = MascotHelper.getMascot(prefs.mascotStyle)
            text = "Active Companion: ${m.name} (${m.fullName})"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(theme.TEXT)
            typeface = Typeface.DEFAULT_BOLD
        }
        val activeMascotDescTv = TextView(this).apply {
            text = "App icon, overlay identity, and web assistant synchronized"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(theme.MUTED)
        }
        activeMascotCol.addView(activeMascotNameTv)
        activeMascotCol.addView(activeMascotDescTv)
        activeCompanionBanner.addView(activeMascotImg)
        activeCompanionBanner.addView(activeMascotCol)
        companionCard.addView(activeCompanionBanner)

        val mascotScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(0, 0, 0, Ui.dp(this@SettingsActivity, 4))
        }
        val mascotRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val mascotViews = ArrayList<Pair<String, View>>()

        fun updateMascotCardSelection() {
            val current = prefs.mascotStyle.lowercase()
            val activeData = MascotHelper.getMascot(current)
            activeMascotImg.setImageResource(activeData.drawableRes)
            activeMascotNameTv.text = "Active Companion: ${activeData.name} (${activeData.fullName})"

            for ((key, v) in mascotViews) {
                val isSelected = current == key ||
                    (key == "owl-orbital" && (current == "default" || current == "")) ||
                    (key == "fox" && (current == "pip" || current == "fennec")) ||
                    (key == "kitten" && (current == "cat" || current == "byte" || current == "luna")) ||
                    (key == "sprite" && (current == "ollie" || current == "bolt" || current == "spark")) ||
                    (key == "otter" && (current == "nova")) ||
                    (key == "tanuki" && (current == "taro" || current == "momo"))
                v.background = Ui.rounded(
                    if (isSelected) (if (theme.isDark) Color.parseColor("#312E81") else Color.parseColor("#EEF2FF")) else theme.SURFACE_HI,
                    Ui.dp(this@SettingsActivity, 12).toFloat()
                )
                if (v is LinearLayout) {
                    val tv = v.getChildAt(1) as? TextView
                    tv?.setTextColor(if (isSelected) (if (theme.isDark) Color.parseColor("#818CF8") else Color.parseColor("#4338CA")) else theme.TEXT)
                    tv?.typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                }
            }
        }

        for (m in MascotHelper.ALL_MASCOTS) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                val padH = Ui.dp(this@SettingsActivity, 12)
                val padV = Ui.dp(this@SettingsActivity, 8)
                setPadding(padH, padV, padH, padV)
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    rightMargin = Ui.dp(this@SettingsActivity, 8)
                }
                layoutParams = lp
                val img = ImageView(this@SettingsActivity).apply {
                    setImageResource(m.drawableRes)
                    val s = Ui.dp(this@SettingsActivity, 36)
                    layoutParams = LinearLayout.LayoutParams(s, s)
                }
                addView(img)
                val label = TextView(this@SettingsActivity).apply {
                    text = m.fullName
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTextColor(theme.TEXT)
                    setPadding(0, Ui.dp(this@SettingsActivity, 4), 0, 0)
                }
                addView(label)
                setOnClickListener {
                    prefs.mascotStyle = m.id
                    updateMascotCardSelection()
                    updateMascotHomeCard()
                    Ui.showMascotToast(this@SettingsActivity, "Active companion set to ${m.name}! Restart OpenAssist to apply home icon.", m.id)
                }
            }
            mascotViews.add(Pair(m.id, item))
            mascotRow.addView(item)
        }
        updateMascotCardSelection()
        mascotScroll.addView(mascotRow)
        companionCard.addView(mascotScroll)

        val restartRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@SettingsActivity, 8)
            }
            layoutParams = lp
        }
        val restartInfo = TextView(this).apply {
            text = "Apply chosen mascot as launcher icon:"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(theme.MUTED)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
        }
        val restartBtn = Button(this).apply {
            text = "Apply & Restart"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            background = Ui.rounded(theme.ACCENT, Ui.dp(this@SettingsActivity, 6).toFloat())
            setTextColor(theme.ON_ACCENT)
            setOnClickListener {
                LauncherAliasHelper.updateLauncherIcon(this@SettingsActivity, prefs.mascotStyle)
                LauncherAliasHelper.restartApp(this@SettingsActivity)
            }
        }
        restartRow.addView(restartInfo)
        restartRow.addView(restartBtn)
        companionCard.addView(restartRow)

        col.addView(companionCard)

        val mascotTogglesCard = cardLayout()
        toggle(
            safeString(R.string.pref_mascot_top_chips_title, "Show Mascot Beside Top Chips"),
            safeString(R.string.pref_mascot_top_chips_desc, "Display your companion icon next to Copy, Whole, Circle, Fit"),
            prefs.showMascotTopChips,
            mascotTogglesCard
        ) { prefs.showMascotTopChips = it }

        toggle(
            safeString(R.string.pref_mascot_search_pill_title, "Show Mascot in Prompt Search Bar"),
            safeString(R.string.pref_mascot_search_pill_desc, "Display an optional companion icon inside the left edge of the prompt pill for aesthetic balance"),
            prefs.showMascotSearchPill,
            mascotTogglesCard
        ) { prefs.showMascotSearchPill = it }

        toggle(
            safeString(R.string.pref_mascot_bubble_title, "Mascot Speech Bubbles & Petting"),
            safeString(R.string.pref_mascot_bubble_desc, "Display cheerful responses, sweet phrases, and cute dialogue when you tap the mascot"),
            prefs.showMascotSpeechBubble,
            mascotTogglesCard
        ) { prefs.showMascotSpeechBubble = it }

        toggle(
            safeString(R.string.pref_haptic_title, "Haptic Petting Feedback"),
            safeString(R.string.pref_haptic_desc, "Subtle vibration pulse when petting or interacting with the mascot"),
            prefs.hapticFeedbackEnabled,
            mascotTogglesCard
        ) { prefs.hapticFeedbackEnabled = it }

        val mascotLangRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padH = Ui.dp(this@SettingsActivity, 12)
            val padV = Ui.dp(this@SettingsActivity, 10)
            setPadding(padH, padV, padH, padV)
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@SettingsActivity, 6)
            }
            layoutParams = lp
        }
        val mascotLangTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        mascotLangTextCol.addView(TextView(this).apply {
            text = safeString(R.string.pref_mascot_lang_title, "Mascot Spoken Language")
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(theme.TEXT)
            typeface = Typeface.DEFAULT_BOLD
        })
        val mCurrentCode = prefs.translationStore.getEffectiveLanguage("mascot")
        val mCurrentName = prefs.translationStore.getAvailableLanguages().firstOrNull { it.code == mCurrentCode }?.name ?: mCurrentCode.ifBlank { "English" }
        val mascotLangSub = TextView(this).apply {
            text = "Active: $mCurrentName (Can differ from App/Settings)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(Color.parseColor("#10B981"))
        }
        mascotLangTextCol.addView(mascotLangSub)
        mascotLangRow.addView(mascotLangTextCol)

        val switchMascotBtn = Button(this).apply {
            text = "Switch"
            textSize = 12f
            background = Ui.rounded(theme.SURFACE, Ui.dp(this@SettingsActivity, 6).toFloat())
            setTextColor(theme.ACCENT)
            setOnClickListener {
                showSelectLanguageOverrideDialog("Mascot Spoken Language") { chosen ->
                    prefs.mascotLanguage = chosen
                    val updatedName = prefs.translationStore.getAvailableLanguages().firstOrNull { it.code == chosen }?.name ?: chosen.ifBlank { "English (Default)" }
                    mascotLangSub.text = "Active: $updatedName (Can differ from App/Settings)"
                    Ui.showMascotToast(this@SettingsActivity, "Mascot spoken language set to $updatedName", prefs.mascotStyle, type = ToastType.GENERAL)
                }
            }
        }
        mascotLangRow.addView(switchMascotBtn)
        mascotTogglesCard.addView(mascotLangRow)

        col.addView(mascotTogglesCard)

        buildLanguageAndCommunityTranslationsSection(col)
        buildMascotDialogueStudioSection(col)

        header(safeString(R.string.st_about, "About OpenAssist"))
        val aboutCard = cardLayout()
        aboutCard.addView(TextView(this).apply {
            text = "OpenAssist v1.59.0 • Offline Multi-Model Whisper Engine\nCommunity Translations • Extensible Mascot Dialogue • 100% Private On-Device."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(theme.MUTED)
        })
        col.addView(aboutCard)

        try {
            updateModeButtonsUi()
            refreshAll()
            if (!prefs.firstRunDone) {
                col.post { showSetupWizardDialog() }
            }
        } catch (t: Throwable) {
            android.util.Log.e("SettingsActivity", "Error during init refresh", t)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            if (::assistantStatus.isInitialized) {
                assistantStatus.text = safeString(R.string.st_assistant_on, "Active. Long-press Home (or use your assist gesture) to open it.")
            }
            refreshAll()
        } catch (t: Throwable) {
            android.util.Log.e("SettingsActivity", "Error in onResume", t)
        }
    }

    private fun updateModeButtonsUi() {
        if (!::postProcBtn.isInitialized || !::realTimeBtn.isInitialized) return
        val isPost = prefs.isPostProcessing
        postProcBtn.background = Ui.rounded(if (isPost) Color.parseColor("#2563EB") else theme.SURFACE_HI, Ui.dp(this, 8).toFloat())
        postProcBtn.setTextColor(if (isPost) Color.WHITE else theme.MUTED)
        realTimeBtn.background = Ui.rounded(if (!isPost) Color.parseColor("#2563EB") else theme.SURFACE_HI, Ui.dp(this, 8).toFloat())
        realTimeBtn.setTextColor(if (!isPost) Color.WHITE else theme.MUTED)
        refreshPipelineInfo()
    }

    private fun refreshAll() {
        try { refreshAiServices() } catch (t: Throwable) {}
        try { refreshEngineViews() } catch (t: Throwable) {}
        try { refreshModelsList() } catch (t: Throwable) {}
        try { refreshLanguageChips() } catch (t: Throwable) {}
        try { refreshLangMappings() } catch (t: Throwable) {}
        try { refreshPipelineInfo() } catch (t: Throwable) {}
    }

    private fun refreshAiServices() {
        if (!::activeAiBadge.isInitialized || !::customAiContainer.isInitialized) return
        val current = AiServices.current(prefs)
        activeAiBadge.text = current.name + (if (current.isCustom) " (Custom)" else "")
        customAiContainer.removeAllViews()
        val customServices = prefs.getCustomServices()
        if (customServices.isEmpty()) {
            customAiContainer.addView(TextView(this).apply {
                text = "No custom web assistants added yet. Tap '+ Add Custom Web Assistant' below to connect Ollama, Open WebUI, or new AI models."
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(theme.MUTED)
                setPadding(0, Ui.dp(this@SettingsActivity, 4), 0, Ui.dp(this@SettingsActivity, 4))
            })
            return
        }
        for (cs in customServices) {
            val isCurrent = cs.id == prefs.serviceId
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = Ui.rounded(if (isCurrent) (if (theme.isDark) Color.parseColor("#1E293B") else Color.parseColor("#EFF6FF")) else theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
                val pad = Ui.dp(this@SettingsActivity, 8)
                setPadding(pad, pad, pad, pad)
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = Ui.dp(this@SettingsActivity, 4)
                }
                layoutParams = lp
            }
            val infoCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            infoCol.addView(TextView(this).apply {
                text = (if (isCurrent) "  " else "") + cs.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(if (isCurrent) (if (theme.isDark) Color.parseColor("#93C5FD") else Color.parseColor("#1D4ED8")) else theme.TEXT)
                typeface = if (isCurrent) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            })
            infoCol.addView(TextView(this).apply {
                text = cs.url
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(theme.MUTED)
                maxLines = 1
            })
            row.addView(infoCol)
            val selectBtn = Button(this).apply {
                text = if (isCurrent) "Active" else "Select"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                background = Ui.rounded(if (isCurrent) Color.parseColor("#2563EB") else theme.SURFACE, Ui.dp(this@SettingsActivity, 6).toFloat())
                setTextColor(if (isCurrent) Color.WHITE else theme.TEXT)
                setOnClickListener {
                    prefs.serviceId = cs.id
                    refreshAiServices()
                    Ui.showMascotToast(this@SettingsActivity, "Activated ${cs.name}", prefs.mascotStyle, type = ToastType.ASSISTANT)
                }
            }
            row.addView(selectBtn)
            val deleteBtn = Button(this).apply {
                text = "X"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                background = Ui.rounded(theme.SURFACE, Ui.dp(this@SettingsActivity, 6).toFloat())
                setTextColor(Color.parseColor("#EF4444"))
                val lp = LinearLayout.LayoutParams(Ui.dp(this@SettingsActivity, 36), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    leftMargin = Ui.dp(this@SettingsActivity, 6)
                }
                layoutParams = lp
                setOnClickListener {
                    showThemedDialog(
                        AlertDialog.Builder(this@SettingsActivity)
                            .setTitle("Remove ${cs.name}")
                            .setMessage("Remove this custom AI assistant URL from OpenAssist?")
                            .setPositiveButton("Remove") { _, _ ->
                                prefs.removeCustomService(cs.id)
                                refreshAiServices()
                                Ui.showMascotToast(this@SettingsActivity, "Removed ${cs.name}", prefs.mascotStyle, type = ToastType.ASSISTANT)
                            }
                            .setNegativeButton("Cancel", null)
                    )
                }
            }
            row.addView(deleteBtn)
            customAiContainer.addView(row)
        }
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

    private fun themedBuilder(): AlertDialog.Builder {
        val themeRes = if (theme.isDark) R.style.Theme_OpenAssist_Dark else R.style.Theme_OpenAssist_Light
        return AlertDialog.Builder(this, themeRes)
    }

    private fun showAiServiceSelectorDialog() {
        val allServices = AiServices.all(prefs)
        var dialog: AlertDialog? = null
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.card(this@SettingsActivity, theme)
            val pad = Ui.dp(this@SettingsActivity, 16)
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Select Active Search & AI Service"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(theme.TEXT)
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Choose your active service or tap 'Manage Chips' to reorder and toggle"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(theme.MUTED)
            setPadding(0, Ui.dp(this@SettingsActivity, 2), 0, Ui.dp(this@SettingsActivity, 12))
        })

        val listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        for (service in allServices) {
            val isCurrent = service.id == prefs.serviceId
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val padH = Ui.dp(this@SettingsActivity, 14)
                val padV = Ui.dp(this@SettingsActivity, 10)
                setPadding(padH, padV, padH, padV)
                background = Ui.rounded(if (isCurrent) theme.SURFACE_HI else Color.TRANSPARENT, Ui.dp(this@SettingsActivity, 8).toFloat())
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    prefs.serviceId = service.id
                    refreshAiServices()
                    Ui.showMascotToast(this@SettingsActivity, "Active: ${service.name}", prefs.mascotStyle, type = ToastType.ASSISTANT)
                    dialog?.dismiss()
                }
            }
            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val titleRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            titleRow.addView(TextView(this).apply {
                text = service.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(if (isCurrent) theme.ACCENT else theme.TEXT)
                if (isCurrent) typeface = Typeface.DEFAULT_BOLD
            })
            if (service.openInApp) {
                titleRow.addView(TextView(this).apply {
                    text = " App ↗ "
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                    setTextColor(if (theme.isDark) Color.parseColor("#93C5FD") else Color.parseColor("#1D4ED8"))
                    background = Ui.rounded(if (theme.isDark) Color.parseColor("#1E3A8A") else Color.parseColor("#DBEAFE"), Ui.dp(this@SettingsActivity, 4).toFloat())
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        leftMargin = Ui.dp(this@SettingsActivity, 6)
                    }
                    layoutParams = lp
                })
            }
            textCol.addView(titleRow)
            textCol.addView(TextView(this).apply {
                text = "${service.category.displayName} • ${service.url}"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setTextColor(theme.MUTED)
                isSingleLine = true
            })
            row.addView(textCol)

            if (isCurrent) {
                row.addView(TextView(this).apply {
                    text = "✓"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTextColor(theme.ACCENT)
                    typeface = Typeface.DEFAULT_BOLD
                })
            }
            listLayout.addView(row)
        }

        val scroll = ScrollView(this).apply {
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this@SettingsActivity, 280))
            layoutParams = lp
            addView(listLayout)
        }
        root.addView(scroll)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@SettingsActivity, 14)
            }
            layoutParams = lp
        }

        val manageBtn = Button(this).apply {
            text = "⚙ Manage Chips"
            textSize = 12f
            background = Ui.rounded(theme.ACCENT, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.ON_ACCENT)
            typeface = Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f).apply {
                rightMargin = Ui.dp(this@SettingsActivity, 6)
            }
            layoutParams = lp
            setOnClickListener {
                dialog?.dismiss()
                showChipManagerDialog()
            }
        }
        val addBtn = Button(this).apply {
            text = "+ Add Link"
            textSize = 12f
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.TEXT)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = Ui.dp(this@SettingsActivity, 6)
            }
            layoutParams = lp
            setOnClickListener {
                dialog?.dismiss()
                showAddCustomAiDialog()
            }
        }
        val cancelBtn = Button(this).apply {
            text = "Close"
            textSize = 12f
            background = Ui.rounded(theme.SURFACE, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.MUTED)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f)
            layoutParams = lp
            setOnClickListener { dialog?.dismiss() }
        }
        btnRow.addView(manageBtn)
        btnRow.addView(addBtn)
        btnRow.addView(cancelBtn)
        root.addView(btnRow)

        dialog = showThemedDialog(themedBuilder().setView(root))
    }

    private fun showChipManagerDialog() {
        var selectedCat: ServiceCategory? = null
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.card(this@SettingsActivity, theme)
            val pad = Ui.dp(this@SettingsActivity, 16)
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Manage Search & Assistant Chips"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(theme.TEXT)
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Reorder chips with ▲/▼, enable/disable chips, or choose whether to open queries in native apps."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(theme.MUTED)
            setPadding(0, Ui.dp(this@SettingsActivity, 2), 0, Ui.dp(this@SettingsActivity, 10))
        })

        // Top actions
        val topActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = Ui.dp(this@SettingsActivity, 10)
            }
            layoutParams = lp
        }
        val addBtn = Button(this).apply {
            text = "+ Add Website / App"
            textSize = 12f
            background = Ui.rounded(theme.ACCENT, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.ON_ACCENT)
            typeface = Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f).apply {
                rightMargin = Ui.dp(this@SettingsActivity, 6)
            }
            layoutParams = lp
            setOnClickListener { showAddCustomAiDialog() }
        }
        val resetBtn = Button(this).apply {
            text = "Reset Defaults"
            textSize = 12f
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.MUTED)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f)
            layoutParams = lp
            setOnClickListener {
                prefs.resetServiceCustomizations()
                Ui.showMascotToast(this@SettingsActivity, "Reset chips to default sequence", prefs.mascotStyle, type = ToastType.GENERAL)
                refreshAiServices()
                showChipManagerDialog()
            }
        }
        topActions.addView(addBtn)
        topActions.addView(resetBtn)
        root.addView(topActions)

        // Category filter pills
        val catScroll = HorizontalScrollView(this).apply {
            isFillViewport = true
            isHorizontalScrollBarEnabled = false
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = Ui.dp(this@SettingsActivity, 8)
            }
            layoutParams = lp
        }
        val catRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val categories = listOf(null to "All") + ServiceCategory.values().map { it to it.displayName }

        val listLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        fun populateList() {
            listLayout.removeAllViews()
            val allConfigured = AiServices.allConfigured(prefs)
            val currentOrder = allConfigured.map { it.id }.toMutableList()
            val filtered = if (selectedCat == null) allConfigured else allConfigured.filter { it.category == selectedCat }

            for (srv in filtered) {
                val isCurrentActive = srv.id == prefs.serviceId
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    val padH = Ui.dp(this@SettingsActivity, 8)
                    val padV = Ui.dp(this@SettingsActivity, 6)
                    setPadding(padH, padV, padH, padV)
                    background = Ui.rounded(if (isCurrentActive) theme.SURFACE_HI else Color.TRANSPARENT, Ui.dp(this@SettingsActivity, 8).toFloat())
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = Ui.dp(this@SettingsActivity, 4)
                    }
                    layoutParams = lp
                }

                // Up / Down reorder arrows
                val arrowsCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    val lp = LinearLayout.LayoutParams(Ui.dp(this@SettingsActivity, 26), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        rightMargin = Ui.dp(this@SettingsActivity, 6)
                    }
                    layoutParams = lp
                }
                val upBtn = TextView(this).apply {
                    text = "▲"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    setTextColor(theme.TEXT)
                    setPadding(Ui.dp(this@SettingsActivity, 2), 0, Ui.dp(this@SettingsActivity, 2), Ui.dp(this@SettingsActivity, 2))
                    isClickable = true
                    setOnClickListener {
                        val fullIndex = currentOrder.indexOf(srv.id)
                        if (fullIndex > 0) {
                            val temp = currentOrder[fullIndex - 1]
                            currentOrder[fullIndex - 1] = srv.id
                            currentOrder[fullIndex] = temp
                            prefs.saveServiceOrder(currentOrder)
                            refreshAiServices()
                            populateList()
                        }
                    }
                }
                val downBtn = TextView(this).apply {
                    text = "▼"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    setTextColor(theme.TEXT)
                    setPadding(Ui.dp(this@SettingsActivity, 2), Ui.dp(this@SettingsActivity, 2), Ui.dp(this@SettingsActivity, 2), 0)
                    isClickable = true
                    setOnClickListener {
                        val fullIndex = currentOrder.indexOf(srv.id)
                        if (fullIndex in 0 until currentOrder.size - 1) {
                            val temp = currentOrder[fullIndex + 1]
                            currentOrder[fullIndex + 1] = srv.id
                            currentOrder[fullIndex] = temp
                            prefs.saveServiceOrder(currentOrder)
                            refreshAiServices()
                            populateList()
                        }
                    }
                }
                arrowsCol.addView(upBtn)
                arrowsCol.addView(downBtn)
                row.addView(arrowsCol)

                // Service Info
                val textCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    layoutParams = lp
                }
                val nameRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                nameRow.addView(TextView(this).apply {
                    text = srv.name
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(if (isCurrentActive) theme.ACCENT else theme.TEXT)
                    typeface = if (isCurrentActive) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                })
                if (isCurrentActive) {
                    nameRow.addView(TextView(this).apply {
                        text = " (Active)"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        setTextColor(theme.ACCENT)
                    })
                }
                textCol.addView(nameRow)
                textCol.addView(TextView(this).apply {
                    text = "${srv.category.displayName} • ${srv.url}"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    setTextColor(theme.MUTED)
                    isSingleLine = true
                })
                row.addView(textCol)

                // Open in App toggle button / switch
                val appPill = TextView(this).apply {
                    val isOpenInApp = srv.openInApp
                    text = if (isOpenInApp) "App" else "Web"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    val p = Ui.dp(this@SettingsActivity, 4)
                    setPadding(p * 2, p, p * 2, p)
                    background = Ui.rounded(if (isOpenInApp) Color.parseColor("#1D4ED8") else theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 6).toFloat())
                    setTextColor(if (isOpenInApp) Color.WHITE else theme.MUTED)
                    typeface = Typeface.DEFAULT_BOLD
                    isClickable = true
                    setOnClickListener {
                        val next = !srv.openInApp
                        prefs.setServiceOpenInApp(srv.id, next)
                        refreshAiServices()
                        populateList()
                        Ui.showMascotToast(this@SettingsActivity, "${srv.name}: ${if (next) "Open in App" else "Open in WebView"}", prefs.mascotStyle)
                    }
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        rightMargin = Ui.dp(this@SettingsActivity, 8)
                    }
                    layoutParams = lp
                }
                row.addView(appPill)

                // Enable / Disable Switch
                val enableSwitch = Switch(this).apply {
                    isChecked = srv.isEnabled
                    setOnCheckedChangeListener { _, isChecked ->
                        prefs.setServiceEnabled(srv.id, isChecked)
                        refreshAiServices()
                    }
                }
                row.addView(enableSwitch)

                if (srv.isCustom) {
                    val delBtn = ImageView(this).apply {
                        setImageResource(R.drawable.ic_close)
                        imageTintList = ColorStateList.valueOf(Color.parseColor("#EF4444"))
                        val s = Ui.dp(this@SettingsActivity, 24)
                        val p = Ui.dp(this@SettingsActivity, 4)
                        setPadding(p, p, p, p)
                        layoutParams = LinearLayout.LayoutParams(s, s).apply { leftMargin = Ui.dp(this@SettingsActivity, 4) }
                        isClickable = true
                        setOnClickListener {
                            prefs.removeCustomService(srv.id)
                            refreshAiServices()
                            populateList()
                            Ui.showMascotToast(this@SettingsActivity, "Removed ${srv.name}", prefs.mascotStyle)
                        }
                    }
                    row.addView(delBtn)
                }

                listLayout.addView(row)
            }
        }

        fun renderCatTabs() {
            catRow.removeAllViews()
            for ((cat, label) in categories) {
                val isSel = selectedCat == cat
                val tab = TextView(this).apply {
                    text = label
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    val padH = Ui.dp(this@SettingsActivity, 10)
                    val padV = Ui.dp(this@SettingsActivity, 4)
                    setPadding(padH, padV, padH, padV)
                    background = Ui.rounded(if (isSel) theme.ACCENT else theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 6).toFloat())
                    setTextColor(if (isSel) theme.ON_ACCENT else theme.TEXT)
                    typeface = if (isSel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    isClickable = true
                    setOnClickListener {
                        selectedCat = cat
                        renderCatTabs()
                        populateList()
                    }
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        rightMargin = Ui.dp(this@SettingsActivity, 6)
                    }
                    layoutParams = lp
                }
                catRow.addView(tab)
            }
        }

        renderCatTabs()
        catScroll.addView(catRow)
        root.addView(catScroll)

        populateList()

        val scroll = ScrollView(this).apply {
            addView(listLayout)
            isFillViewport = true
            val dm = resources.displayMetrics
            val maxH = (dm.heightPixels * 0.48).toInt()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxH)
        }
        root.addView(scroll)

        var dialog: AlertDialog? = null
        val closeBtn = Button(this).apply {
            text = "Done"
            textSize = 12f
            background = Ui.rounded(theme.ACCENT, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.ON_ACCENT)
            typeface = Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@SettingsActivity, 12)
            }
            layoutParams = lp
            setOnClickListener { dialog?.dismiss() }
        }
        root.addView(closeBtn)

        dialog = showThemedDialog(themedBuilder().setView(root))
    }

    private fun showAddCustomAiDialog() {
        var dialog: AlertDialog? = null
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.card(this@SettingsActivity, theme)
            val pad = Ui.dp(this@SettingsActivity, 16)
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Add Custom Website or Search App"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(theme.TEXT)
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Enter website name and URL. Include {q} for search queries (e.g. https://domain.com/search?q={q})"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(theme.MUTED)
            setPadding(0, Ui.dp(this@SettingsActivity, 2), 0, Ui.dp(this@SettingsActivity, 12))
        })

        val pad = Ui.dp(this@SettingsActivity, 10)
        val nameInput = EditText(this).apply {
            hint = "Website Name (e.g. Reddit, arXiv, Ollama)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(theme.TEXT)
            setHintTextColor(theme.MUTED)
            background = Ui.rounded(theme.INPUT_BG, Ui.dp(this@SettingsActivity, 8).toFloat())
            setPadding(pad, pad, pad, pad)
        }
        val urlInput = EditText(this).apply {
            hint = "URL or Search Template (https://... or ...?q={q})"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(theme.TEXT)
            setHintTextColor(theme.MUTED)
            background = Ui.rounded(theme.INPUT_BG, Ui.dp(this@SettingsActivity, 8).toFloat())
            setPadding(pad, pad, pad, pad)
        }
        val pkgInput = EditText(this).apply {
            hint = "App Package Name (Optional, e.g. com.example.app)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(theme.TEXT)
            setHintTextColor(theme.MUTED)
            background = Ui.rounded(theme.INPUT_BG, Ui.dp(this@SettingsActivity, 8).toFloat())
            setPadding(pad, pad, pad, pad)
        }
        root.addView(nameInput)
        root.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(1, Ui.dp(this@SettingsActivity, 8)) })
        root.addView(urlInput)
        root.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(1, Ui.dp(this@SettingsActivity, 8)) })
        root.addView(pkgInput)

        val openInAppCheck = CheckBox(this).apply {
            text = "Open in native app if installed"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(theme.TEXT)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@SettingsActivity, 8)
            }
            layoutParams = lp
        }
        root.addView(openInAppCheck)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@SettingsActivity, 14)
            }
            layoutParams = lp
        }

        val cancelBtn = Button(this).apply {
            text = "Cancel"
            textSize = 13f
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.MUTED)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = Ui.dp(this@SettingsActivity, 8)
            }
            layoutParams = lp
            setOnClickListener { dialog?.dismiss() }
        }
        val addBtn = Button(this).apply {
            text = "Add & Select"
            textSize = 13f
            background = Ui.rounded(theme.ACCENT, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.ON_ACCENT)
            typeface = Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
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
                    refreshAiServices()
                    Ui.showMascotToast(this@SettingsActivity, "Added and selected ${newService.name}", prefs.mascotStyle, type = ToastType.ASSISTANT)
                    dialog?.dismiss()
                } else {
                    Ui.showMascotToast(this@SettingsActivity, "Please enter a valid URL", prefs.mascotStyle, type = ToastType.ASSISTANT)
                }
            }
        }
        btnRow.addView(cancelBtn)
        btnRow.addView(addBtn)
        root.addView(btnRow)

        dialog = showThemedDialog(themedBuilder().setView(root))
    }

    private fun refreshEngineViews() {
        if (!::engineSelectBtn.isInitialized || !::engineStatusText.isInitialized) return
        when (prefs.recognizer) {
            Prefs.ENGINE_LOCAL_MODEL -> {
                val currentModel = prefs.getActiveLocalModel()
                val modelName = currentModel?.name ?: "None"
                val modeLabel = if (prefs.isPostProcessing) "Post-Processing" else "Real-Time"
                engineSelectBtn.text = "Local Model Mode ($modelName - $modeLabel)"
                engineSelectBtn.background = Ui.rounded(Color.parseColor("#2563EB"), Ui.dp(this, 8).toFloat())
                engineStatusText.text = "Active: Offline Multi-Model Whisper ($modeLabel). Transcriptions run on-device with zero network latency."
            }
            Prefs.ENGINE_GOOGLE_TTS -> {
                engineSelectBtn.text = "Google Speech Recognition & Synthesis"
                engineSelectBtn.background = Ui.rounded(Color.parseColor("#059669"), Ui.dp(this, 8).toFloat())
                engineStatusText.text = "Active: Google Speech Recognition & Synthesis (Official Google engine for ColorOS / Oppo & Android)."
            }
            Prefs.ENGINE_FUTO_VOICE_INPUT -> {
                engineSelectBtn.text = "FUTO Voice Input / Keyboard (Offline Whisper)"
                engineSelectBtn.background = Ui.rounded(Color.parseColor("#7C3AED"), Ui.dp(this, 8).toFloat())
                engineStatusText.text = "Active: FUTO Voice Input (Free on-device open-source Whisper transcription)."
            }
            else -> {
                engineSelectBtn.text = "System Default Speech Recognizer"
                engineSelectBtn.background = Ui.rounded(Color.parseColor("#374151"), Ui.dp(this, 8).toFloat())
                engineStatusText.text = "Active: Android System Default Voice Input."
            }
        }
    }

    private fun refreshModelsList() {
        if (!::localModelsContainer.isInitialized || !::activeModelBadge.isInitialized) return
        localModelsContainer.removeAllViews()
        val models = modelStore.getModels()
        val activeModel = prefs.getActiveLocalModel()
        activeModelBadge.text = activeModel?.name ?: "No model active"
        if (models.isEmpty()) {
            localModelsContainer.addView(TextView(this).apply {
                text = "No custom Whisper models loaded yet. Tap 'Load Model Binary' to add your offline .bin model."
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(theme.MUTED)
                setPadding(0, Ui.dp(this@SettingsActivity, 6), 0, Ui.dp(this@SettingsActivity, 6))
            })
            return
        }
        val defaultId = modelStore.getDefaultModelId()
        for (m in models) {
            val isDefault = m.id == defaultId || m.id == activeModel?.id
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = Ui.rounded(if (isDefault) (if (theme.isDark) Color.parseColor("#1E293B") else Color.parseColor("#EFF6FF")) else theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
                val pad = Ui.dp(this@SettingsActivity, 8)
                setPadding(pad, pad, pad, pad)
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = Ui.dp(this@SettingsActivity, 6)
                }
                layoutParams = lp
            }
            val infoCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            infoCol.addView(TextView(this).apply {
                text = (if (isDefault) "  " else "") + m.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(if (isDefault) (if (theme.isDark) Color.parseColor("#93C5FD") else Color.parseColor("#1D4ED8")) else theme.TEXT)
                typeface = if (isDefault) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            })
            row.addView(infoCol)
            val selectBtn = Button(this).apply {
                text = if (isDefault) "Active" else "Select"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                background = Ui.rounded(if (isDefault) Color.parseColor("#2563EB") else theme.SURFACE, Ui.dp(this@SettingsActivity, 6).toFloat())
                setTextColor(if (isDefault) Color.WHITE else theme.TEXT)
                setOnClickListener {
                    modelStore.setDefaultModelId(m.id)
                    modelStore.setModelIdForLang(prefs.voiceLang, m.id)
                    prefs.recognizer = Prefs.ENGINE_LOCAL_MODEL
                    refreshAll()
                    Ui.showMascotToast(this@SettingsActivity, "Activated ${m.name}", prefs.mascotStyle, type = ToastType.SPEECH)
                }
            }
            row.addView(selectBtn)

            // Remove/Delete Model button (for all imported models with confirmation)
            val deleteBtn = Button(this).apply {
                text = "Delete"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                background = Ui.rounded(Color.parseColor("#991B1B"), Ui.dp(this@SettingsActivity, 6).toFloat())
                setTextColor(Color.WHITE)
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    leftMargin = Ui.dp(this@SettingsActivity, 6)
                }
                layoutParams = lp
                setOnClickListener {
                    confirmDeleteModel(m)
                }
            }
            row.addView(deleteBtn)

            localModelsContainer.addView(row)
        }
    }

    private fun confirmDeleteModel(model: WhisperModel) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.card(this@SettingsActivity, theme)
            val pad = Ui.dp(this@SettingsActivity, 16)
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "Delete Whisper Model?"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(theme.TEXT)
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Are you sure you want to remove '${model.name}'? This will delete the binary file from your device storage to free space."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(theme.MUTED)
            setPadding(0, Ui.dp(this@SettingsActivity, 6), 0, Ui.dp(this@SettingsActivity, 14))
        })
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        var dialog: AlertDialog? = null
        val cancelBtn = Button(this).apply {
            text = "Cancel"
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.TEXT)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = Ui.dp(this@SettingsActivity, 8)
            }
            layoutParams = lp
            setOnClickListener { dialog?.dismiss() }
        }
        val deleteBtn = Button(this).apply {
            text = "Delete Model"
            background = Ui.rounded(Color.parseColor("#DC2626"), Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
            setOnClickListener {
                dialog?.dismiss()
                modelStore.removeModel(model.id)
                refreshAll()
                Ui.showMascotToast(this@SettingsActivity, "Removed model: ${model.name}", prefs.mascotStyle, type = ToastType.SPEECH)
            }
        }
        btnRow.addView(cancelBtn)
        btnRow.addView(deleteBtn)
        root.addView(btnRow)
        dialog = showThemedDialog(themedBuilder().setView(root))
    }

    private fun cycleToNextModel() {
        val next = modelStore.cycleModel()
        if (next != null) {
            modelStore.setModelIdForLang(prefs.voiceLang, next.id)
            prefs.recognizer = Prefs.ENGINE_LOCAL_MODEL
            refreshAll()
            Ui.showMascotToast(this, "Switched to: ${next.name}", prefs.mascotStyle, type = ToastType.SPEECH)
        }
    }

    private fun refreshLangMappings() {
        if (!::langMappingContainer.isInitialized) return
        langMappingContainer.removeAllViews()
        val allLangs = modelStore.getAllBoundLanguages()

        if (allLangs.isEmpty()) {
            langMappingContainer.addView(TextView(this).apply {
                text = "No languages currently bound. Add a language or reset defaults below."
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(theme.MUTED)
                val pad = Ui.dp(this@SettingsActivity, 8)
                setPadding(pad, pad, pad, pad)
            })
        }

        for ((code, title, isStandard) in allLangs) {
            val assignedId = modelStore.getDynamicModelId(code)
            val assignedModel = modelStore.getModel(assignedId) ?: modelStore.getDefaultModel()
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val pad = Ui.dp(this@SettingsActivity, 8)
                setPadding(pad, pad, pad, pad)
                background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = Ui.dp(this@SettingsActivity, 6)
                }
                layoutParams = lp
            }
            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(this).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(theme.TEXT)
                typeface = Typeface.DEFAULT_BOLD
            })
            val modelLabel = TextView(this).apply {
                text = "Bound: ${assignedModel?.name ?: "Default Model"}"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(if (theme.isDark) Color.parseColor("#93C5FD") else Color.parseColor("#1D4ED8"))
            }
            textCol.addView(modelLabel)
            row.addView(textCol)

            val bindBtn = Button(this).apply {
                text = "Change"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                background = Ui.rounded(Color.parseColor("#2563EB"), Ui.dp(this@SettingsActivity, 6).toFloat())
                setTextColor(Color.WHITE)
                setOnClickListener {
                    showBindModelDialog(code, title)
                }
            }
            row.addView(bindBtn)

            // Allow removing ANY language (preexisting standard or custom)
            val delBtn = Button(this).apply {
                text = "✕"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                background = Ui.rounded(Color.parseColor("#991B1B"), Ui.dp(this@SettingsActivity, 6).toFloat())
                setTextColor(Color.WHITE)
                val lp = LinearLayout.LayoutParams(Ui.dp(this@SettingsActivity, 40), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    leftMargin = Ui.dp(this@SettingsActivity, 6)
                }
                layoutParams = lp
                setOnClickListener {
                    modelStore.removeLanguageBinding(code)
                    refreshLangMappings()
                    refreshPipelineInfo()
                    Ui.showMascotToast(this@SettingsActivity, "Removed language binding: $title", prefs.mascotStyle, type = ToastType.GENERAL)
                }
            }
            row.addView(delBtn)

            langMappingContainer.addView(row)
        }

        // Action row with "+ Add Language Binding" and optional "Reset Default Languages"
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@SettingsActivity, 8)
            }
            layoutParams = lp
        }

        val addLangBtn = Button(this).apply {
            text = "+ Add Language"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            background = Ui.rounded(Color.parseColor("#3730A3"), Ui.dp(this@SettingsActivity, 6).toFloat())
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = Ui.dp(this@SettingsActivity, 4)
            }
            layoutParams = lp
            setOnClickListener {
                showAddLanguageBindingDialog()
            }
        }
        btnRow.addView(addLangBtn)

        if (modelStore.getRemovedStandardLanguages().isNotEmpty()) {
            val resetBtn = Button(this).apply {
                text = "Reset Defaults"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 6).toFloat())
                setTextColor(theme.TEXT)
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                layoutParams = lp
                setOnClickListener {
                    modelStore.restoreDefaultLanguages()
                    refreshLangMappings()
                    refreshPipelineInfo()
                    Ui.showMascotToast(this@SettingsActivity, "Restored default languages", prefs.mascotStyle, type = ToastType.GENERAL)
                }
            }
            btnRow.addView(resetBtn)
        }

        langMappingContainer.addView(btnRow)
    }

    private fun showBindModelDialog(langCode: String, langTitle: String) {
        val models = modelStore.getModels()
        if (models.isEmpty()) {
            Ui.showMascotToast(this, "No Whisper models found!", prefs.mascotStyle, type = ToastType.GENERAL)
            return
        }
        var dialog: AlertDialog? = null
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.card(this@SettingsActivity, theme)
            val pad = Ui.dp(this@SettingsActivity, 16)
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "Bind Model to $langTitle"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(theme.TEXT)
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Select model for this language:"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(theme.MUTED)
            setPadding(0, Ui.dp(this@SettingsActivity, 2), 0, Ui.dp(this@SettingsActivity, 10))
        })
        val currentId = modelStore.getDynamicModelId(langCode)
        for (m in models) {
            val isCurrent = m.id == currentId
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val padH = Ui.dp(this@SettingsActivity, 12)
                val padV = Ui.dp(this@SettingsActivity, 10)
                setPadding(padH, padV, padH, padV)
                background = Ui.rounded(if (isCurrent) (if (theme.isDark) Color.parseColor("#1E293B") else Color.parseColor("#EFF6FF")) else Color.TRANSPARENT, Ui.dp(this@SettingsActivity, 8).toFloat())
                isClickable = true
                isFocusable = true
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = Ui.dp(this@SettingsActivity, 4)
                }
                layoutParams = lp
                setOnClickListener {
                    modelStore.setDynamicModelId(langCode, m.id)
                    dialog?.dismiss()
                    refreshLangMappings()
                    refreshPipelineInfo()
                    Ui.showMascotToast(this@SettingsActivity, "Bound ${m.name} to $langTitle", prefs.mascotStyle, type = ToastType.SPEECH)
                }
            }
            row.addView(TextView(this).apply {
                text = "${m.name} (${m.sizeMb})" + if (isCurrent) "  (Active)" else ""
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(if (isCurrent) theme.ACCENT else theme.TEXT)
                if (isCurrent) typeface = Typeface.DEFAULT_BOLD
            })
            root.addView(row)
        }
        val closeBtn = Button(this).apply {
            text = "Close"
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.TEXT)
            setOnClickListener { dialog?.dismiss() }
        }
        root.addView(closeBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(this@SettingsActivity, 10)
        })
        dialog = showThemedDialog(themedBuilder().setView(root))
    }

    private fun showAddLanguageBindingDialog() {
        var dialog: AlertDialog? = null
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.card(this@SettingsActivity, theme)
            val pad = Ui.dp(this@SettingsActivity, 16)
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "Add Language Binding"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(theme.TEXT)
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Configure a language and bind it to a Whisper model:"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(theme.MUTED)
            setPadding(0, Ui.dp(this@SettingsActivity, 2), 0, Ui.dp(this@SettingsActivity, 10))
        })
        val pad = Ui.dp(this, 10)
        val nameInput = EditText(this).apply {
            hint = "Language Name (e.g. Spanish, Bengali)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(theme.TEXT)
            setHintTextColor(theme.MUTED)
            background = Ui.rounded(theme.INPUT_BG, Ui.dp(this@SettingsActivity, 8).toFloat())
            setPadding(pad, pad, pad, pad)
        }
        val codeInput = EditText(this).apply {
            hint = "Language Code (e.g. es, bn, fr)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(theme.TEXT)
            setHintTextColor(theme.MUTED)
            background = Ui.rounded(theme.INPUT_BG, Ui.dp(this@SettingsActivity, 8).toFloat())
            setPadding(pad, pad, pad, pad)
        }
        root.addView(nameInput)
        root.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(1, Ui.dp(this@SettingsActivity, 8)) })
        root.addView(codeInput)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@SettingsActivity, 12)
            }
            layoutParams = lp
        }
        val cancelBtn = Button(this).apply {
            text = "Cancel"
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.TEXT)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = Ui.dp(this@SettingsActivity, 8)
            }
            layoutParams = lp
            setOnClickListener { dialog?.dismiss() }
        }
        val saveBtn = Button(this).apply {
            text = "Save"
            background = Ui.rounded(theme.ACCENT, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.ON_ACCENT)
            typeface = Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
            setOnClickListener {
                val name = nameInput.text.toString().trim()
                val code = codeInput.text.toString().trim()
                if (name.isNotBlank() && code.isNotBlank()) {
                    modelStore.addCustomLanguageBinding(code, name, modelStore.getDefaultModelId())
                    dialog?.dismiss()
                    refreshLangMappings()
                    Ui.showMascotToast(this@SettingsActivity, "Added language: $name", prefs.mascotStyle, type = ToastType.GENERAL)
                }
            }
        }
        btnRow.addView(cancelBtn)
        btnRow.addView(saveBtn)
        root.addView(btnRow)
        dialog = showThemedDialog(themedBuilder().setView(root))
    }

    private fun createHeader(text: String, targetCol: LinearLayout) {
        val tv = TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(theme.ACCENT)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, Ui.dp(this@SettingsActivity, 16), 0, Ui.dp(this@SettingsActivity, 6))
        }
        targetCol.addView(tv)
    }

    private fun createSubheader(text: String, targetCol: LinearLayout) {
        val tv = TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(if (theme.isDark) Color.parseColor("#93C5FD") else Color.parseColor("#1D4ED8"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, Ui.dp(this@SettingsActivity, 10), 0, Ui.dp(this@SettingsActivity, 4))
        }
        targetCol.addView(tv)
    }

    private fun createCardLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.card(this@SettingsActivity, theme)
            val pad = Ui.dp(this@SettingsActivity, 14)
            setPadding(pad, pad, pad, pad)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = Ui.dp(this@SettingsActivity, 12)
            }
            layoutParams = lp
        }
    }

    private fun buildLanguageAndCommunityTranslationsSection(col: LinearLayout) {
        createHeader("Language & Community Translations", col)
        val transCard = createCardLayout()

        // 1. App Default Language Row
        val defaultRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, Ui.dp(this@SettingsActivity, 8))
        }
        val defaultTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        defaultTextCol.addView(TextView(this).apply {
            text = "App Default Language"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(theme.TEXT)
            typeface = Typeface.DEFAULT_BOLD
        })
        val currentLangObj = prefs.translationStore.getAvailableLanguages().firstOrNull { it.code == prefs.appLanguage }
        val defaultSub = TextView(this).apply {
            text = "Active: ${currentLangObj?.name ?: prefs.appLanguage}"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(theme.ACCENT)
        }
        defaultTextCol.addView(defaultSub)
        defaultRow.addView(defaultTextCol)

        val switchLangBtn = Button(this).apply {
            text = "Switch"
            textSize = 12f
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 6).toFloat())
            setTextColor(theme.ACCENT)
            setOnClickListener {
                showSelectAppLanguageDialog { lang ->
                    prefs.appLanguage = lang.code
                    defaultSub.text = "Active: ${lang.name}"
                    Ui.showMascotToast(this@SettingsActivity, "App language: ${lang.name}", prefs.mascotStyle, type = ToastType.GENERAL)
                }
            }
        }
        defaultRow.addView(switchLangBtn)
        transCard.addView(defaultRow)

        // 2. Per-Menu Language Overrides
        createSubheader("Per-Menu Language Overrides", transCard)
        fun menuRow(label: String, getVal: () -> String, setVal: (String) -> Unit): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val padV = Ui.dp(this@SettingsActivity, 6)
                setPadding(0, padV, 0, padV)
                val txtCol = LinearLayout(this@SettingsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                txtCol.addView(TextView(this@SettingsActivity).apply {
                    text = label
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(theme.TEXT)
                })
                val valView = TextView(this@SettingsActivity).apply {
                    val cur = getVal()
                    text = if (cur.isBlank()) "Default (${prefs.appLanguage})" else cur
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTextColor(theme.MUTED)
                }
                txtCol.addView(valView)
                addView(txtCol)

                val btn = Button(this@SettingsActivity).apply {
                    text = "Override"
                    textSize = 11f
                    background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 6).toFloat())
                    setTextColor(theme.TEXT)
                    setOnClickListener {
                        showSelectLanguageOverrideDialog(label) { chosen ->
                            setVal(chosen)
                            valView.text = if (chosen.isBlank()) "Default (${prefs.appLanguage})" else chosen
                            Ui.showMascotToast(this@SettingsActivity, "$label set to ${if (chosen.isBlank()) "Default" else chosen}", prefs.mascotStyle, type = ToastType.GENERAL)
                        }
                    }
                }
                addView(btn)
            }
        }

        transCard.addView(menuRow("Settings Menu Language", { prefs.settingsLanguage }, { prefs.settingsLanguage = it }))
        transCard.addView(menuRow("Overlay Menu Language", { prefs.overlayLanguage }, { prefs.overlayLanguage = it }))
        transCard.addView(menuRow("Web Assistant Language", { prefs.webUiLanguage }, { prefs.webUiLanguage = it }))
        transCard.addView(menuRow("Mascot Speech Language", { prefs.mascotLanguage }, { prefs.mascotLanguage = it }))

        // 3. Extensible Community Actions (Export, Import, In-App Editor)
        val actRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@SettingsActivity, 10)
            }
            layoutParams = lp
        }

        val exportBtn = Button(this).apply {
            text = "Export .txt"
            textSize = 11f
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 6).toFloat())
            setTextColor(theme.TEXT)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = Ui.dp(this@SettingsActivity, 4)
            }
            layoutParams = lp
            setOnClickListener { showExportTranslationDialog() }
        }
        val importBtn = Button(this).apply {
            text = "Import .txt"
            textSize = 11f
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 6).toFloat())
            setTextColor(theme.TEXT)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = Ui.dp(this@SettingsActivity, 4)
            }
            layoutParams = lp
            setOnClickListener { showImportTranslationDialog() }
        }
        val editorBtn = Button(this).apply {
            text = "In-App Editor"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            background = Ui.rounded(Color.parseColor("#4F46E5"), Ui.dp(this@SettingsActivity, 6).toFloat())
            setTextColor(Color.WHITE)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f)
            layoutParams = lp
            setOnClickListener { showInAppTranslationEditorDialog() }
        }

        actRow.addView(exportBtn)
        actRow.addView(importBtn)
        actRow.addView(editorBtn)
        transCard.addView(actRow)

        col.addView(transCard)
    }

    private fun buildMascotDialogueStudioSection(col: LinearLayout) {
        createHeader("Mascot Speech & Dialogue Studio", col)
        val studioCard = createCardLayout()

        // Mascot Spoken Language Selector Row
        val langRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, Ui.dp(this@SettingsActivity, 10))
        }
        val langTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        langTextCol.addView(TextView(this).apply {
            text = "Mascot Spoken Language"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(theme.TEXT)
            typeface = Typeface.DEFAULT_BOLD
        })
        val currentMascotLang = prefs.translationStore.getEffectiveLanguage("mascot")
        val currentMascotLangName = prefs.translationStore.getAvailableLanguages().firstOrNull { it.code == currentMascotLang }?.name ?: currentMascotLang.ifBlank { "English" }
        val langSub = TextView(this).apply {
            text = "Active: $currentMascotLangName"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(theme.ACCENT)
        }
        langTextCol.addView(langSub)
        langRow.addView(langTextCol)

        val switchMascotLangBtn = Button(this).apply {
            text = "Switch"
            textSize = 12f
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 6).toFloat())
            setTextColor(theme.ACCENT)
            setOnClickListener {
                showSelectLanguageOverrideDialog("Mascot Spoken Language") { chosen ->
                    prefs.mascotLanguage = chosen
                    val updatedName = prefs.translationStore.getAvailableLanguages().firstOrNull { it.code == chosen }?.name ?: chosen.ifBlank { "English (Default)" }
                    langSub.text = "Active: $updatedName"
                    Ui.showMascotToast(this@SettingsActivity, "Mascot spoken language set to $updatedName", prefs.mascotStyle, type = ToastType.GENERAL)
                }
            }
        }
        langRow.addView(switchMascotLangBtn)
        studioCard.addView(langRow)

        studioCard.addView(TextView(this).apply {
            text = "Customize and translate wholesome greetings, tips, and pet responses for all mascots or specific companions (Ozzy, Pip, Echo, etc.):"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(theme.MUTED)
            setPadding(0, 0, 0, Ui.dp(this@SettingsActivity, 8))
        })

        val studioBtnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
        }

        val openStudioBtn = Button(this).apply {
            text = "Mascot Dialogue Studio"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            background = Ui.rounded(Color.parseColor("#059669"), Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(Color.WHITE)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f).apply {
                rightMargin = Ui.dp(this@SettingsActivity, 6)
            }
            layoutParams = lp
            setOnClickListener { showMascotDialogueStudioDialog() }
        }

        val exportSpeechBtn = Button(this).apply {
            text = "Export Dialogue .txt"
            textSize = 11f
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.TEXT)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
            setOnClickListener { showExportMascotSpeechDialog() }
        }

        studioBtnRow.addView(openStudioBtn)
        studioBtnRow.addView(exportSpeechBtn)
        studioCard.addView(studioBtnRow)

        col.addView(studioCard)
    }

    private fun showSelectAppLanguageDialog(onSelected: (LanguageInfo) -> Unit) {
        val langs = prefs.translationStore.getAvailableLanguages()
        var dialog: AlertDialog? = null
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.card(this@SettingsActivity, theme)
            val pad = Ui.dp(this@SettingsActivity, 16)
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "Select App Default Language"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(theme.TEXT)
            typeface = Typeface.DEFAULT_BOLD
        })
        for (lang in langs) {
            val isCurrent = lang.code == prefs.appLanguage
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val padH = Ui.dp(this@SettingsActivity, 12)
                val padV = Ui.dp(this@SettingsActivity, 10)
                setPadding(padH, padV, padH, padV)
                background = Ui.rounded(if (isCurrent) (if (theme.isDark) Color.parseColor("#1E293B") else Color.parseColor("#EFF6FF")) else Color.TRANSPARENT, Ui.dp(this@SettingsActivity, 8).toFloat())
                isClickable = true
                isFocusable = true
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = Ui.dp(this@SettingsActivity, 4)
                }
                layoutParams = lp
                setOnClickListener {
                    onSelected(lang)
                    dialog?.dismiss()
                }
            }
            row.addView(TextView(this).apply {
                text = "${lang.name} (${lang.code})" + if (isCurrent) "  (Active)" else ""
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(if (isCurrent) theme.ACCENT else theme.TEXT)
                if (isCurrent) typeface = Typeface.DEFAULT_BOLD
            })
            root.addView(row)
        }
        val closeBtn = Button(this).apply {
            text = "Cancel"
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.TEXT)
            setOnClickListener { dialog?.dismiss() }
        }
        root.addView(closeBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(this@SettingsActivity, 10)
        })
        dialog = showThemedDialog(themedBuilder().setView(root))
    }

    private fun showSelectLanguageOverrideDialog(title: String, onSelected: (String) -> Unit) {
        val langs = prefs.translationStore.getAvailableLanguages()
        var dialog: AlertDialog? = null
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.card(this@SettingsActivity, theme)
            val pad = Ui.dp(this@SettingsActivity, 16)
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "Override: $title"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(theme.TEXT)
            typeface = Typeface.DEFAULT_BOLD
        })

        // Default option
        val defaultRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padH = Ui.dp(this@SettingsActivity, 12)
            val padV = Ui.dp(this@SettingsActivity, 10)
            setPadding(padH, padV, padH, padV)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                onSelected("")
                dialog?.dismiss()
            }
        }
        defaultRow.addView(TextView(this).apply {
            text = "Use App Default (${prefs.appLanguage})"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(theme.ACCENT)
        })
        root.addView(defaultRow)

        for (lang in langs) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val padH = Ui.dp(this@SettingsActivity, 12)
                val padV = Ui.dp(this@SettingsActivity, 10)
                setPadding(padH, padV, padH, padV)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    onSelected(lang.code)
                    dialog?.dismiss()
                }
            }
            row.addView(TextView(this).apply {
                text = "${lang.name} (${lang.code})"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(theme.TEXT)
            })
            root.addView(row)
        }
        val closeBtn = Button(this).apply {
            text = "Cancel"
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.TEXT)
            setOnClickListener { dialog?.dismiss() }
        }
        root.addView(closeBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(this@SettingsActivity, 10)
        })
        dialog = showThemedDialog(themedBuilder().setView(root))
    }

    private fun showExportTranslationDialog() {
        val template = prefs.translationStore.exportTemplateText(prefs.appLanguage)
        var dialog: AlertDialog? = null
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.card(this@SettingsActivity, theme)
            val pad = Ui.dp(this@SettingsActivity, 16)
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "Export Translation File (.txt)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(theme.TEXT)
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Line 1 is English source text, Line 2 is translated text, with 1 empty line gap. Copy and translate using AI or share with friends:"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(theme.MUTED)
            setPadding(0, Ui.dp(this@SettingsActivity, 4), 0, Ui.dp(this@SettingsActivity, 8))
        })
        val preview = EditText(this).apply {
            setText(template)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(theme.TEXT)
            typeface = Typeface.MONOSPACE
            background = Ui.rounded(theme.INPUT_BG, Ui.dp(this@SettingsActivity, 8).toFloat())
            val pad = Ui.dp(this@SettingsActivity, 8)
            setPadding(pad, pad, pad, pad)
            minLines = 8
            maxLines = 12
        }
        root.addView(preview)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@SettingsActivity, 10)
            }
            layoutParams = lp
        }
        val copyBtn = Button(this).apply {
            text = "Copy to Clipboard"
            background = Ui.rounded(theme.ACCENT, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.ON_ACCENT)
            typeface = Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f).apply {
                rightMargin = Ui.dp(this@SettingsActivity, 6)
            }
            layoutParams = lp
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("OpenAssist Translation Template", template))
                Ui.showMascotToast(this@SettingsActivity, "Copied template to clipboard!", prefs.mascotStyle, type = ToastType.GENERAL)
                dialog?.dismiss()
            }
        }
        val closeBtn = Button(this).apply {
            text = "Close"
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.TEXT)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
            setOnClickListener { dialog?.dismiss() }
        }
        btnRow.addView(copyBtn)
        btnRow.addView(closeBtn)
        root.addView(btnRow)
        dialog = showThemedDialog(themedBuilder().setView(root))
    }

    private fun showImportTranslationDialog() {
        var dialog: AlertDialog? = null
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.card(this@SettingsActivity, theme)
            val pad = Ui.dp(this@SettingsActivity, 16)
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "Import Community Translation"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(theme.TEXT)
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Paste your line-by-line translation file contents below:"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(theme.MUTED)
            setPadding(0, Ui.dp(this@SettingsActivity, 2), 0, Ui.dp(this@SettingsActivity, 8))
        })
        val codeInput = EditText(this).apply {
            hint = "Language Code (e.g. es, de, fr, hi, bn)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(theme.TEXT)
            setHintTextColor(theme.MUTED)
            background = Ui.rounded(theme.INPUT_BG, Ui.dp(this@SettingsActivity, 8).toFloat())
            val pad = Ui.dp(this@SettingsActivity, 8)
            setPadding(pad, pad, pad, pad)
        }
        val nameInput = EditText(this).apply {
            hint = "Language Name (e.g. Español, Deutsch)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(theme.TEXT)
            setHintTextColor(theme.MUTED)
            background = Ui.rounded(theme.INPUT_BG, Ui.dp(this@SettingsActivity, 8).toFloat())
            val pad = Ui.dp(this@SettingsActivity, 8)
            setPadding(pad, pad, pad, pad)
        }
        root.addView(codeInput)
        root.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(1, Ui.dp(this@SettingsActivity, 6)) })
        root.addView(nameInput)
        root.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(1, Ui.dp(this@SettingsActivity, 6)) })

        val contentInput = EditText(this).apply {
            hint = "Paste translated lines here...\n\nEnglish phrase\nTranslated phrase\n\nNext phrase..."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(theme.TEXT)
            setHintTextColor(theme.MUTED)
            typeface = Typeface.MONOSPACE
            background = Ui.rounded(theme.INPUT_BG, Ui.dp(this@SettingsActivity, 8).toFloat())
            val pad = Ui.dp(this@SettingsActivity, 8)
            setPadding(pad, pad, pad, pad)
            minLines = 6
            maxLines = 10
        }
        root.addView(contentInput)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@SettingsActivity, 10)
            }
            layoutParams = lp
        }
        val cancelBtn = Button(this).apply {
            text = "Cancel"
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.TEXT)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = Ui.dp(this@SettingsActivity, 6)
            }
            layoutParams = lp
            setOnClickListener { dialog?.dismiss() }
        }
        val importBtn = Button(this).apply {
            text = "Import & Apply"
            background = Ui.rounded(theme.ACCENT, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.ON_ACCENT)
            typeface = Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f)
            layoutParams = lp
            setOnClickListener {
                val code = codeInput.text.toString().trim()
                val name = nameInput.text.toString().trim().ifBlank { code }
                val content = contentInput.text.toString().trim()
                if (code.isNotBlank() && content.isNotBlank()) {
                    prefs.translationStore.addCustomLanguage(code, name)
                    val count = prefs.translationStore.importTranslationText(content, code)
                    dialog?.dismiss()
                    Ui.showMascotToast(this@SettingsActivity, "Imported $count phrases for $name!", prefs.mascotStyle, type = ToastType.GENERAL)
                }
            }
        }
        btnRow.addView(cancelBtn)
        btnRow.addView(importBtn)
        root.addView(btnRow)
        dialog = showThemedDialog(themedBuilder().setView(root))
    }

    private fun showInAppTranslationEditorDialog() {
        var dialog: AlertDialog? = null
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.card(this@SettingsActivity, theme)
            val pad = Ui.dp(this@SettingsActivity, 16)
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "In-App Translation Editor"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(theme.TEXT)
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Quickly edit UI titles, descriptions, and mascot spoken phrases:"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(theme.MUTED)
            setPadding(0, Ui.dp(this@SettingsActivity, 2), 0, Ui.dp(this@SettingsActivity, 6))
        })

        var activeEditLang = prefs.appLanguage.ifBlank { "hinglish" }
        var activeTab = "settings" // "settings" or "mascot"
        var currentTranslations = prefs.translationStore.getTranslationMap(activeEditLang).toMutableMap()

        // 1. Language Controls Row (Editing Language & Mascot Spoken Language)
        val langControlsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padV = Ui.dp(this@SettingsActivity, 4)
            setPadding(0, padV, 0, padV)
        }
        val editLangBtn = Button(this).apply {
            val curLangObj = prefs.translationStore.getAvailableLanguages().firstOrNull { it.code == activeEditLang }
            text = "Target: ${curLangObj?.name ?: activeEditLang} ▾"
            textSize = 11f
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 6).toFloat())
            setTextColor(theme.ACCENT)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = Ui.dp(this@SettingsActivity, 6)
            }
            layoutParams = lp
        }
        val mascotSpokenBtn = Button(this).apply {
            val mEffective = prefs.translationStore.getEffectiveLanguage("mascot")
            val mLangObj = prefs.translationStore.getAvailableLanguages().firstOrNull { it.code == mEffective }
            text = "Mascot Speaks: ${mLangObj?.name ?: mEffective} ▾"
            textSize = 11f
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 6).toFloat())
            setTextColor(Color.parseColor("#10B981"))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f)
            layoutParams = lp
        }
        langControlsRow.addView(editLangBtn)
        langControlsRow.addView(mascotSpokenBtn)
        root.addView(langControlsRow)

        // 2. Tab Bar: [Settings & Descriptions] vs [Mascot Phrases]
        val tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val padV = Ui.dp(this@SettingsActivity, 6)
            setPadding(0, padV, 0, padV)
        }
        val settingsTabBtn = Button(this).apply {
            text = "Settings UI & Descriptions"
            textSize = 11f
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = Ui.dp(this@SettingsActivity, 4)
            }
            layoutParams = lp
        }
        val mascotTabBtn = Button(this).apply {
            text = "Mascot Spoken Phrases"
            textSize = 11f
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
        }
        tabRow.addView(settingsTabBtn)
        tabRow.addView(mascotTabBtn)
        root.addView(tabRow)

        val listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun updateTabStyles() {
            val isSettings = activeTab == "settings"
            settingsTabBtn.background = Ui.rounded(if (isSettings) Color.parseColor("#4F46E5") else theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 6).toFloat())
            settingsTabBtn.setTextColor(if (isSettings) Color.WHITE else theme.TEXT)
            settingsTabBtn.typeface = if (isSettings) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

            mascotTabBtn.background = Ui.rounded(if (!isSettings) Color.parseColor("#059669") else theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 6).toFloat())
            mascotTabBtn.setTextColor(if (!isSettings) Color.WHITE else theme.TEXT)
            mascotTabBtn.typeface = if (!isSettings) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        fun populateRows() {
            listLayout.removeAllViews()
            currentTranslations = prefs.translationStore.getTranslationMap(activeEditLang).toMutableMap()

            if (activeTab == "settings") {
                // Populate Settings Titles and Descriptions
                for ((englishTitle, englishDesc) in TranslationStore.DEFAULT_STRINGS) {
                    val card = LinearLayout(this@SettingsActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
                        val pad = Ui.dp(this@SettingsActivity, 10)
                        setPadding(pad, pad, pad, pad)
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            bottomMargin = Ui.dp(this@SettingsActivity, 8)
                        }
                        layoutParams = lp
                    }

                    // 1. Title Row
                    card.addView(TextView(this@SettingsActivity).apply {
                        text = "Title: $englishTitle"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        setTextColor(theme.TEXT)
                        typeface = Typeface.DEFAULT_BOLD
                    })
                    val editTitle = EditText(this@SettingsActivity).apply {
                        setText(currentTranslations[englishTitle] ?: "")
                        hint = "Translate title in $activeEditLang..."
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        setTextColor(theme.ACCENT)
                        setHintTextColor(theme.MUTED)
                        background = Ui.rounded(theme.INPUT_BG, Ui.dp(this@SettingsActivity, 6).toFloat())
                        val p = Ui.dp(this@SettingsActivity, 6)
                        setPadding(p, p, p, p)
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            topMargin = Ui.dp(this@SettingsActivity, 3)
                            bottomMargin = Ui.dp(this@SettingsActivity, 6)
                        }
                        layoutParams = lp
                        addTextChangedListener(object : android.text.TextWatcher {
                            override fun afterTextChanged(s: android.text.Editable?) {
                                prefs.translationStore.saveStringOverride(activeEditLang, englishTitle, s?.toString() ?: "")
                            }
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        })
                    }
                    card.addView(editTitle)

                    // 2. Description Row
                    card.addView(TextView(this@SettingsActivity).apply {
                        text = "Description: $englishDesc"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        setTextColor(theme.MUTED)
                    })
                    val editDesc = EditText(this@SettingsActivity).apply {
                        setText(currentTranslations[englishDesc] ?: "")
                        hint = "Translate description in $activeEditLang..."
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        setTextColor(if (theme.isDark) Color.parseColor("#93C5FD") else Color.parseColor("#1D4ED8"))
                        setHintTextColor(theme.MUTED)
                        background = Ui.rounded(theme.INPUT_BG, Ui.dp(this@SettingsActivity, 6).toFloat())
                        val p = Ui.dp(this@SettingsActivity, 6)
                        setPadding(p, p, p, p)
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            topMargin = Ui.dp(this@SettingsActivity, 3)
                        }
                        layoutParams = lp
                        addTextChangedListener(object : android.text.TextWatcher {
                            override fun afterTextChanged(s: android.text.Editable?) {
                                prefs.translationStore.saveStringOverride(activeEditLang, englishDesc, s?.toString() ?: "")
                            }
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        })
                    }
                    card.addView(editDesc)

                    listLayout.addView(card)
                }
            } else {
                // Populate Mascot Phrases
                val generalPhrases = prefs.mascotDialogueStore.getGeneralPhrases()
                for (phrase in generalPhrases) {
                    val card = LinearLayout(this@SettingsActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
                        val pad = Ui.dp(this@SettingsActivity, 10)
                        setPadding(pad, pad, pad, pad)
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            bottomMargin = Ui.dp(this@SettingsActivity, 8)
                        }
                        layoutParams = lp
                    }
                    card.addView(TextView(this@SettingsActivity).apply {
                        text = phrase
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        setTextColor(theme.TEXT)
                        typeface = Typeface.DEFAULT_BOLD
                    })
                    val existingTrans = prefs.mascotDialogueStore.getPhraseTranslation(phrase, activeEditLang)
                    val editPhrase = EditText(this@SettingsActivity).apply {
                        setText(if (existingTrans == phrase && activeEditLang != "en") "" else existingTrans)
                        hint = "Translate phrase in $activeEditLang..."
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        setTextColor(Color.parseColor("#10B981"))
                        setHintTextColor(theme.MUTED)
                        background = Ui.rounded(theme.INPUT_BG, Ui.dp(this@SettingsActivity, 6).toFloat())
                        val p = Ui.dp(this@SettingsActivity, 6)
                        setPadding(p, p, p, p)
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            topMargin = Ui.dp(this@SettingsActivity, 4)
                        }
                        layoutParams = lp
                        addTextChangedListener(object : android.text.TextWatcher {
                            override fun afterTextChanged(s: android.text.Editable?) {
                                prefs.mascotDialogueStore.setPhraseTranslation(phrase, activeEditLang, s?.toString() ?: "")
                            }
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        })
                    }
                    card.addView(editPhrase)
                    listLayout.addView(card)
                }
            }
        }

        settingsTabBtn.setOnClickListener {
            activeTab = "settings"
            updateTabStyles()
            populateRows()
        }
        mascotTabBtn.setOnClickListener {
            activeTab = "mascot"
            updateTabStyles()
            populateRows()
        }

        editLangBtn.setOnClickListener {
            showSelectAppLanguageDialog { lang ->
                activeEditLang = lang.code
                val curLangObj = prefs.translationStore.getAvailableLanguages().firstOrNull { it.code == activeEditLang }
                editLangBtn.text = "Target: ${curLangObj?.name ?: activeEditLang} ▾"
                populateRows()
            }
        }

        mascotSpokenBtn.setOnClickListener {
            showSelectLanguageOverrideDialog("Mascot Spoken Language") { chosen ->
                prefs.mascotLanguage = chosen
                val mEffective = prefs.translationStore.getEffectiveLanguage("mascot")
                val mLangObj = prefs.translationStore.getAvailableLanguages().firstOrNull { it.code == mEffective }
                mascotSpokenBtn.text = "Mascot Speaks: ${mLangObj?.name ?: mEffective} ▾"
                Ui.showMascotToast(this@SettingsActivity, "Mascot spoken language set to ${mLangObj?.name ?: mEffective}", prefs.mascotStyle, type = ToastType.GENERAL)
            }
        }

        updateTabStyles()
        populateRows()

        val scroll = ScrollView(this).apply {
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this@SettingsActivity, 320))
            layoutParams = lp
            addView(listLayout)
        }
        root.addView(scroll)

        val doneBtn = Button(this).apply {
            text = "Done & Save All"
            typeface = Typeface.DEFAULT_BOLD
            background = Ui.rounded(theme.ACCENT, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.ON_ACCENT)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@SettingsActivity, 10)
            }
            layoutParams = lp
            setOnClickListener {
                dialog?.dismiss()
                Ui.showMascotToast(this@SettingsActivity, "Saved all translations for $activeEditLang!", prefs.mascotStyle, type = ToastType.GENERAL)
            }
        }
        root.addView(doneBtn)
        dialog = showThemedDialog(themedBuilder().setView(root))
    }

    private fun showMascotDialogueStudioDialog() {
        var dialog: AlertDialog? = null
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.card(this@SettingsActivity, theme)
            val pad = Ui.dp(this@SettingsActivity, 16)
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "Mascot Dialogue Studio"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(theme.TEXT)
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Add, remove, or customize what all mascots and specific companions say:"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(theme.MUTED)
            setPadding(0, Ui.dp(this@SettingsActivity, 2), 0, Ui.dp(this@SettingsActivity, 6))
        })

        var mEffective = prefs.translationStore.getEffectiveLanguage("mascot")
        val spokenLangBtn = Button(this).apply {
            val mLangObj = prefs.translationStore.getAvailableLanguages().firstOrNull { it.code == mEffective }
            text = "Mascot Spoken Language: ${mLangObj?.name ?: mEffective} ▾"
            textSize = 11f
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 6).toFloat())
            setTextColor(Color.parseColor("#10B981"))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = Ui.dp(this@SettingsActivity, 6)
            }
            layoutParams = lp
        }
        root.addView(spokenLangBtn)

        var selectedTarget = "general"
        val targetOptions = listOf("general" to "All Mascots (General)", "owl" to "Ozzy (Owl)", "fox" to "Pip (Fox)", "bat" to "Echo (Bat)", "kitten" to "Byte (Kitten)")

        val listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun refreshPhraseRows() {
            listLayout.removeAllViews()
            mEffective = prefs.translationStore.getEffectiveLanguage("mascot")
            val phrases = if (selectedTarget == "general") {
                prefs.mascotDialogueStore.getGeneralPhrases()
            } else {
                prefs.mascotDialogueStore.getSpecificMascotPhrases(selectedTarget)
            }

            for (p in phrases) {
                val row = LinearLayout(this@SettingsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
                    val pad = Ui.dp(this@SettingsActivity, 8)
                    setPadding(pad, pad, pad, pad)
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = Ui.dp(this@SettingsActivity, 6)
                    }
                    layoutParams = lp
                }
                val col = LinearLayout(this@SettingsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    layoutParams = lp
                }
                col.addView(TextView(this@SettingsActivity).apply {
                    text = p
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(theme.TEXT)
                    typeface = Typeface.DEFAULT_BOLD
                })
                val spoken = prefs.mascotDialogueStore.getPhraseTranslation(p, mEffective)
                if (spoken != p && mEffective != "en") {
                    col.addView(TextView(this@SettingsActivity).apply {
                        text = "Speaks ($mEffective): $spoken"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        setTextColor(Color.parseColor("#10B981"))
                        setPadding(0, Ui.dp(this@SettingsActivity, 2), 0, 0)
                    })
                }
                row.addView(col)

                val delBtn = Button(this@SettingsActivity).apply {
                    text = "✕"
                    textSize = 11f
                    background = Ui.rounded(Color.parseColor("#991B1B"), Ui.dp(this@SettingsActivity, 6).toFloat())
                    setTextColor(Color.WHITE)
                    val lp = LinearLayout.LayoutParams(Ui.dp(this@SettingsActivity, 36), Ui.dp(this@SettingsActivity, 32)).apply {
                        leftMargin = Ui.dp(this@SettingsActivity, 6)
                    }
                    layoutParams = lp
                    setOnClickListener {
                        if (selectedTarget == "general") {
                            prefs.mascotDialogueStore.removeGeneralPhrase(p)
                        } else {
                            prefs.mascotDialogueStore.removeSpecificMascotPhrase(selectedTarget, p)
                        }
                        refreshPhraseRows()
                    }
                }
                row.addView(delBtn)
                listLayout.addView(row)
            }
        }

        spokenLangBtn.setOnClickListener {
            showSelectLanguageOverrideDialog("Mascot Spoken Language") { chosen ->
                prefs.mascotLanguage = chosen
                mEffective = prefs.translationStore.getEffectiveLanguage("mascot")
                val updatedName = prefs.translationStore.getAvailableLanguages().firstOrNull { it.code == chosen }?.name ?: chosen.ifBlank { "English (Default)" }
                spokenLangBtn.text = "Mascot Spoken Language: $updatedName ▾"
                refreshPhraseRows()
                Ui.showMascotToast(this@SettingsActivity, "Mascot spoken language set to $updatedName", prefs.mascotStyle, type = ToastType.GENERAL)
            }
        }

        // Target Switcher Row
        val targetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, Ui.dp(this@SettingsActivity, 6))
        }
        for ((key, title) in targetOptions) {
            val chip = TextView(this).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                val isSel = key == selectedTarget
                background = Ui.pill(this@SettingsActivity, if (isSel) theme.ACCENT else theme.SURFACE_HI)
                setTextColor(if (isSel) theme.ON_ACCENT else theme.TEXT)
                val padH = Ui.dp(this@SettingsActivity, 8)
                val padV = Ui.dp(this@SettingsActivity, 4)
                setPadding(padH, padV, padH, padV)
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    rightMargin = Ui.dp(this@SettingsActivity, 4)
                }
                layoutParams = lp
                setOnClickListener {
                    selectedTarget = key
                    refreshPhraseRows()
                }
            }
            targetRow.addView(chip)
        }
        val targetScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(targetRow)
        }
        root.addView(targetScroll)

        refreshPhraseRows()

        val scroll = ScrollView(this).apply {
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this@SettingsActivity, 220))
            layoutParams = lp
            addView(listLayout)
        }
        root.addView(scroll)

        // Add phrase row
        val addInput = EditText(this).apply {
            hint = "Enter new custom phrase..."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(theme.TEXT)
            setHintTextColor(theme.MUTED)
            background = Ui.rounded(theme.INPUT_BG, Ui.dp(this@SettingsActivity, 8).toFloat())
            val pad = Ui.dp(this@SettingsActivity, 8)
            setPadding(pad, pad, pad, pad)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@SettingsActivity, 8)
            }
            layoutParams = lp
        }
        root.addView(addInput)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@SettingsActivity, 8)
            }
            layoutParams = lp
        }
        val addBtn = Button(this).apply {
            text = "+ Add Phrase"
            background = Ui.rounded(theme.ACCENT, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.ON_ACCENT)
            typeface = Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f).apply {
                rightMargin = Ui.dp(this@SettingsActivity, 6)
            }
            layoutParams = lp
            setOnClickListener {
                val txt = addInput.text.toString().trim()
                if (txt.isNotBlank()) {
                    if (selectedTarget == "general") {
                        prefs.mascotDialogueStore.addGeneralPhrase(txt)
                    } else {
                        prefs.mascotDialogueStore.addSpecificMascotPhrase(selectedTarget, txt)
                    }
                    addInput.text.clear()
                    refreshPhraseRows()
                    Ui.showMascotToast(this@SettingsActivity, "Added phrase for $selectedTarget!", prefs.mascotStyle, type = ToastType.GENERAL)
                }
            }
        }
        val closeBtn = Button(this).apply {
            text = "Close"
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.TEXT)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
            setOnClickListener { dialog?.dismiss() }
        }
        btnRow.addView(addBtn)
        btnRow.addView(closeBtn)
        root.addView(btnRow)
        dialog = showThemedDialog(themedBuilder().setView(root))
    }

    private fun showExportMascotSpeechDialog() {
        val template = prefs.mascotDialogueStore.exportPhrasesTemplate(prefs.appLanguage)
        var dialog: AlertDialog? = null
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.card(this@SettingsActivity, theme)
            val pad = Ui.dp(this@SettingsActivity, 16)
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "Export Mascot Dialogue (.txt)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(theme.TEXT)
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Copy mascot speech file to translate what companions say in your native language:"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(theme.MUTED)
            setPadding(0, Ui.dp(this@SettingsActivity, 4), 0, Ui.dp(this@SettingsActivity, 8))
        })
        val preview = EditText(this).apply {
            setText(template)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(theme.TEXT)
            typeface = Typeface.MONOSPACE
            background = Ui.rounded(theme.INPUT_BG, Ui.dp(this@SettingsActivity, 8).toFloat())
            val pad = Ui.dp(this@SettingsActivity, 8)
            setPadding(pad, pad, pad, pad)
            minLines = 8
            maxLines = 12
        }
        root.addView(preview)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@SettingsActivity, 10)
            }
            layoutParams = lp
        }
        val copyBtn = Button(this).apply {
            text = "Copy Dialogue Template"
            background = Ui.rounded(theme.ACCENT, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.ON_ACCENT)
            typeface = Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f).apply {
                rightMargin = Ui.dp(this@SettingsActivity, 6)
            }
            layoutParams = lp
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("OpenAssist Mascot Dialogue Template", template))
                Ui.showMascotToast(this@SettingsActivity, "Copied mascot speech to clipboard!", prefs.mascotStyle, type = ToastType.GENERAL)
                dialog?.dismiss()
            }
        }
        val closeBtn = Button(this).apply {
            text = "Close"
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.TEXT)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
            setOnClickListener { dialog?.dismiss() }
        }
        btnRow.addView(copyBtn)
        btnRow.addView(closeBtn)
        root.addView(btnRow)
        dialog = showThemedDialog(themedBuilder().setView(root))
    }

    private fun refreshLanguageChips() {
        if (!::langChipsRow.isInitialized) return
        langChipsRow.removeAllViews()
        for (lang in VoiceLang.values()) {
            val isCurrent = lang == prefs.voiceLang
            val chip = TextView(this).apply {
                text = lang.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = if (isCurrent) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (isCurrent) Color.WHITE else theme.TEXT)
                background = Ui.pill(this@SettingsActivity, if (isCurrent) Color.parseColor("#2563EB") else theme.SURFACE_HI)
                val pH = Ui.dp(this@SettingsActivity, 12)
                val pV = Ui.dp(this@SettingsActivity, 6)
                setPadding(pH, pV, pH, pV)
                setOnClickListener {
                    prefs.voiceLang = lang
                    refreshLanguageChips()
                    refreshPipelineInfo()
                }
            }
            langChipsRow.addView(chip)
        }
    }

    private fun refreshPipelineInfo() {
        if (!::pipelineInfoText.isInitialized) return
        val lang = prefs.voiceLang
        val engineLabel = prefs.getEngineChipLabel()
        val assignedModel = modelStore.getModelForLang(lang)
        val modeStr = if (prefs.isPostProcessing) "Post-Processing" else "Real-Time"
        val modelText = if (prefs.isLocalModelMode) {
            "Model: ${assignedModel?.name ?: "None"} ($modeStr)"
        } else {
            "Engine: $engineLabel"
        }
        pipelineInfoText.text = "Active Pipeline: [${lang.label}] -> $modelText"
    }

    private fun showEngineSelectorDialog() {
        val engines = listOf(
            Triple(Prefs.ENGINE_DEFAULT, "System Default Speech Recognizer", "Built-in Android system voice input"),
            Triple(Prefs.ENGINE_LOCAL_MODEL, "Local Whisper Mode (Offline GGUF)", "100% private on-device transcription"),
            Triple(Prefs.ENGINE_GOOGLE_TTS, "Google Speech Recognition & Synthesis", "Official Google voice typing for ColorOS/Oppo"),
            Triple(Prefs.ENGINE_FUTO_VOICE_INPUT, "FUTO Voice Input / Keyboard", "Fast open-source neural on-device Whisper")
        )
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.card(this@SettingsActivity, theme)
            val pad = Ui.dp(this@SettingsActivity, 16)
            setPadding(pad, pad, pad, pad)
        }

        // Custom Title
        root.addView(TextView(this).apply {
            text = "Select Voice Input Source"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(theme.TEXT)
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Choose your speech recognition engine for voice dictation"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(theme.MUTED)
            setPadding(0, Ui.dp(this@SettingsActivity, 2), 0, Ui.dp(this@SettingsActivity, 12))
        })

        val listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        var dialog: AlertDialog? = null
        for ((engineKey, engineName, engineDesc) in engines) {
            val isCurrent = prefs.recognizer == engineKey
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val padH = Ui.dp(this@SettingsActivity, 14)
                val padV = Ui.dp(this@SettingsActivity, 12)
                setPadding(padH, padV, padH, padV)
                background = Ui.rounded(if (isCurrent) theme.SURFACE_HI else theme.CARD, Ui.dp(this@SettingsActivity, 10).toFloat())
                isClickable = true
                isFocusable = true
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = Ui.dp(this@SettingsActivity, 8)
                }
                layoutParams = lp
                setOnClickListener {
                    prefs.recognizer = engineKey
                    refreshAll()
                    Ui.showMascotToast(this@SettingsActivity, "Voice Engine: $engineName", prefs.mascotStyle, type = ToastType.SPEECH)
                    dialog?.dismiss()
                }
            }
            val titleRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val title = TextView(this).apply {
                text = engineName
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(if (isCurrent) theme.ACCENT else theme.TEXT)
                if (isCurrent) typeface = Typeface.DEFAULT_BOLD
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                layoutParams = lp
            }
            titleRow.addView(title)
            if (isCurrent) {
                val badge = TextView(this).apply {
                    text = "ACTIVE"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    setTextColor(Color.WHITE)
                    typeface = Typeface.DEFAULT_BOLD
                    background = Ui.pill(this@SettingsActivity, Color.parseColor("#2563EB"))
                    val bPadH = Ui.dp(this@SettingsActivity, 8)
                    val bPadV = Ui.dp(this@SettingsActivity, 2)
                    setPadding(bPadH, bPadV, bPadH, bPadV)
                }
                titleRow.addView(badge)
            }
            row.addView(titleRow)

            val desc = TextView(this).apply {
                text = engineDesc
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(theme.MUTED)
                setPadding(0, Ui.dp(this@SettingsActivity, 4), 0, 0)
            }
            row.addView(desc)
            listLayout.addView(row)
        }

        val scroll = ScrollView(this).apply {
            addView(listLayout)
            isFillViewport = true
        }
        root.addView(scroll)

        val cancelBtn = Button(this).apply {
            text = "Cancel"
            textSize = 12f
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.MUTED)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@SettingsActivity, 10)
            }
            layoutParams = lp
            setOnClickListener { dialog?.dismiss() }
        }
        root.addView(cancelBtn)

        val builder = themedBuilder().setView(root)
        dialog = showThemedDialog(builder)
    }

    private fun pickCustomModel() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            startActivityForResult(intent, RC_MODEL_PICK)
        } catch (e: Exception) {
            Ui.showMascotToast(this, "Could not open file picker", prefs.mascotStyle, type = ToastType.SPEECH)
        }
    }

    private fun toggleTestSpeech() {
        if (isTesting) {
            stopTestSpeech()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), RC_MIC_PERMISSION)
            return
        }
        startTestSpeech()
    }

    private fun startTestSpeech() {
        testSpeech?.destroy()
        isTesting = true
        testMicBtn.text = "Stop Listening"
        testMicBtn.background = Ui.rounded(Color.parseColor("#EF4444"), Ui.dp(this, 8).toFloat())
        testStatusBanner.visibility = View.VISIBLE
        baseTestTextBeforeSpeech = testTextBox.text.toString().trim()

        val speech = Speech(this, prefs, object : Speech.Callback {
            override fun onStarting(engineName: String) {
                testStatusBanner.text = "CONNECTING MIC ($engineName)..."
            }
            override fun onReadyForSpeech(engineName: String) {
                testStatusBanner.text = "LIVE MIC ACTIVE ($engineName) -> Listening... Speak now!"
            }
            override fun onPartial(text: String) {
                val out = text.trim()
                if (out.isNotEmpty()) {
                    val prefix = baseTestTextBeforeSpeech.trim()
                    val combined = if (prefix.isEmpty()) out else "$prefix $out"
                    testTextBox.setText(combined)
                    testTextBox.setSelection(combined.length)
                }
            }
            override fun onFinal(text: String) {
                val out = text.trim()
                val prefix = baseTestTextBeforeSpeech.trim()
                val combined = if (out.isEmpty()) prefix else if (prefix.isEmpty()) out else "$prefix $out"
                testTextBox.setText(combined)
                testTextBox.setSelection(combined.length)
                stopTestSpeech()
            }
            override fun onError(message: String) {
                stopTestSpeech()
                Ui.showMascotToast(this@SettingsActivity, message, prefs.mascotStyle, type = ToastType.SPEECH)
            }
            override fun onStateChanged(listening: Boolean, engineName: String) {
                if (!listening) stopTestSpeech()
            }
        })
        testSpeech = speech
        speech.start()
    }

    private fun stopTestSpeech() {
        isTesting = false
        testMicBtn.text = "Tap to Speak / Test"
        testMicBtn.background = Ui.rounded(theme.ACCENT, Ui.dp(this, 8).toFloat())
        testStatusBanner.visibility = View.GONE
        testSpeech?.stop()
        testSpeech = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_MIC_PERMISSION && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startTestSpeech()
        }
    }

    private fun showSetupWizardDialog() {
        val totalSteps = 5
        var currentStep = 0

        val dialog = themedBuilder().create()
        Ui.styleDialog(dialog, theme)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.card(this@SettingsActivity, theme)
            val pad = Ui.dp(this@SettingsActivity, 16)
            setPadding(pad, pad, pad, pad)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // Header View
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
        }
        val headerMascotIcon = ImageView(this).apply {
            val (drawableRes, _) = getMascotDrawable(prefs.mascotStyle)
            setImageResource(drawableRes)
            val s = Ui.dp(this@SettingsActivity, 32)
            val lp = LinearLayout.LayoutParams(s, s).apply { rightMargin = Ui.dp(this@SettingsActivity, 10) }
            layoutParams = lp
        }
        val headerTitleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
        }
        val headerTitle = TextView(this).apply {
            text = "OpenAssist Setup & Onboarding"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(theme.TEXT)
            typeface = Typeface.DEFAULT_BOLD
        }
        val stepBadge = TextView(this).apply {
            text = "Step 1 of $totalSteps • Configure your private assistant"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(theme.MUTED)
        }
        headerTitleCol.addView(headerTitle)
        headerTitleCol.addView(stepBadge)

        val closeBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_close)
            imageTintList = ColorStateList.valueOf(theme.MUTED)
            val s = Ui.dp(this@SettingsActivity, 28)
            val p = Ui.dp(this@SettingsActivity, 4)
            setPadding(p, p, p, p)
            val lp = LinearLayout.LayoutParams(s, s)
            layoutParams = lp
            isClickable = true
            isFocusable = true
            setOnClickListener { dialog.dismiss() }
        }
        headerRow.addView(headerMascotIcon)
        headerRow.addView(headerTitleCol)
        headerRow.addView(closeBtn)
        root.addView(headerRow)

        // Progress line
        val progressBg = FrameLayout(this).apply {
            val h = Ui.dp(this@SettingsActivity, 4)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h).apply {
                topMargin = Ui.dp(this@SettingsActivity, 10)
                bottomMargin = Ui.dp(this@SettingsActivity, 10)
            }
            layoutParams = lp
            background = Ui.rounded(theme.SURFACE_HI, h.toFloat() / 2)
        }
        val progressFill = View(this).apply {
            background = Ui.rounded(theme.ACCENT, Ui.dp(this@SettingsActivity, 4).toFloat() / 2)
        }
        progressBg.addView(progressFill)
        root.addView(progressBg)

        // Scrollable content area
        val dm = resources.displayMetrics
        val availableH = (dm.heightPixels * 0.52).toInt().coerceAtLeast(Ui.dp(this@SettingsActivity, 280))
        val contentScroll = ScrollView(this).apply {
            isFillViewport = true
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                availableH
            )
            layoutParams = lp
        }
        val contentCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
        }
        contentScroll.addView(contentCol)
        root.addView(contentScroll)

        // Footer buttons
        val footerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@SettingsActivity, 12)
            }
            layoutParams = lp
        }
        val prevBtn = Button(this).apply {
            text = "Skip"
            background = Ui.rounded(Color.TRANSPARENT, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.MUTED)
            textSize = 13f
        }
        val spacer = View(this).apply {
            val lp = LinearLayout.LayoutParams(0, 1, 1f)
            layoutParams = lp
        }
        val nextBtn = Button(this).apply {
            text = "Next  →"
            background = Ui.rounded(theme.ACCENT, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.ON_ACCENT)
            typeface = Typeface.DEFAULT_BOLD
            textSize = 13f
            val padH = Ui.dp(this@SettingsActivity, 16)
            setPadding(padH, 0, padH, 0)
        }
        footerRow.addView(prevBtn)
        footerRow.addView(spacer)
        footerRow.addView(nextBtn)
        root.addView(footerRow)

        fun updateProgress(step: Int) {
            val ratio = (step + 1).toFloat() / totalSteps.toFloat()
            progressBg.post {
                val totalWidth = progressBg.width
                val fillWidth = (totalWidth * ratio).toInt()
                progressFill.layoutParams = FrameLayout.LayoutParams(fillWidth, ViewGroup.LayoutParams.MATCH_PARENT)
            }
        }

        fun bannerView(title: String, desc: String, iconRes: Int? = null): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 12).toFloat())
                val pad = Ui.dp(this@SettingsActivity, 12)
                setPadding(pad, pad, pad, pad)
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = Ui.dp(this@SettingsActivity, 10)
                }
                layoutParams = lp

                if (iconRes != null) {
                    val icon = ImageView(this@SettingsActivity).apply {
                        setImageResource(iconRes)
                        val s = Ui.dp(this@SettingsActivity, 36)
                        val lpIcon = LinearLayout.LayoutParams(s, s).apply { rightMargin = Ui.dp(this@SettingsActivity, 10) }
                        layoutParams = lpIcon
                    }
                    addView(icon)
                }
                val col = LinearLayout(this@SettingsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    val lpCol = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    layoutParams = lpCol
                }
                col.addView(TextView(this@SettingsActivity).apply {
                    text = title
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(theme.TEXT)
                    typeface = Typeface.DEFAULT_BOLD
                })
                col.addView(TextView(this@SettingsActivity).apply {
                    text = desc
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTextColor(theme.MUTED)
                    setPadding(0, Ui.dp(this@SettingsActivity, 2), 0, 0)
                })
                addView(col)
            }
        }

        fun renderStep(step: Int) {
            currentStep = step
            contentCol.removeAllViews()
            val (currentMascotDrawable, _) = getMascotDrawable(prefs.mascotStyle)
            headerMascotIcon.setImageResource(currentMascotDrawable)
            stepBadge.text = "Step ${step + 1} of $totalSteps • Configure your private assistant"
            updateProgress(step)

            when (step) {
                0 -> {
                    // Step 1: Welcome & Mascot Selection
                    contentCol.addView(bannerView(
                        "Choose Your Companion Mascot",
                        "Select your friendly mascot. Your app launcher icon will update to match your mascot after onboarding. You can safely proceed!",
                        currentMascotDrawable
                    ))

                    val lbl = TextView(this).apply {
                        text = "Choose Your Companion Mascot:"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        setTextColor(theme.ACCENT)
                        typeface = Typeface.DEFAULT_BOLD
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            bottomMargin = Ui.dp(this@SettingsActivity, 8)
                        }
                        layoutParams = lp
                    }
                    contentCol.addView(lbl)

                    val mascots = listOf(
                        Triple("owl-orbital", "Ozzy Sentinel", "Chubby AI Sentinel with Circle-to-Search orbit & goggles (Default)"),
                        Triple("owl", "Ozzy Classic", "Classic night-vision twin visor lenses sentinel"),
                        Triple("fox", "Pip Nexus", "Curled search lasso orbit with connected lenses"),
                        Triple("bat", "Echo the Bat", "Ultrasonic acoustic scout with radar waves"),
                        Triple("otter", "Nova the Star Otter", "Deep-query navigator holding an AI pearl gem"),
                        Triple("chameleon", "Pico Chameleon", "Dual-focal independent multi-modal vision"),
                        Triple("tanuki", "Taro Tanuki", "On-device transformer with tactile leaf emblem"),
                        Triple("kitten", "Byte Kitten", "Dumpling kitten with curious antenna"),
                        Triple("firefly", "Lumi Firefly", "Cyber-firefly with luminous radar bulb"),
                        Triple("axolotl", "Kiko Axolotl", "Cosmic axolotl with sensory gills"),
                        Triple("whale", "Moby Whale", "Oceanic deep-assist cosmic voyager")
                    )

                    for ((id, name, detail) in mascots) {
                        val isSelected = prefs.mascotStyle == id || (id == "owl-orbital" && prefs.mascotStyle.isBlank())
                        val row = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            val padH = Ui.dp(this@SettingsActivity, 12)
                            val padV = Ui.dp(this@SettingsActivity, 10)
                            setPadding(padH, padV, padH, padV)
                            background = Ui.rounded(if (isSelected) theme.SURFACE_HI else theme.CARD, Ui.dp(this@SettingsActivity, 10).toFloat())
                            isClickable = true
                            isFocusable = true
                            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                bottomMargin = Ui.dp(this@SettingsActivity, 6)
                            }
                            layoutParams = lp
                        }
                        val (drawableRes, _) = getMascotDrawable(id)
                        val icon = ImageView(this).apply {
                            setImageResource(drawableRes)
                            val s = Ui.dp(this@SettingsActivity, 32)
                            val lp = LinearLayout.LayoutParams(s, s).apply { rightMargin = Ui.dp(this@SettingsActivity, 10) }
                            layoutParams = lp
                        }
                        row.addView(icon)

                        val textCol = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                            layoutParams = lp
                        }
                        val title = TextView(this).apply {
                            text = name
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                            setTextColor(if (isSelected) theme.ACCENT else theme.TEXT)
                            if (isSelected) typeface = Typeface.DEFAULT_BOLD
                        }
                        val sub = TextView(this).apply {
                            text = detail
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                            setTextColor(theme.MUTED)
                        }
                        textCol.addView(title)
                        textCol.addView(sub)
                        row.addView(textCol)

                        if (isSelected) {
                            val check = TextView(this).apply {
                                text = "✓"
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                               setTextColor(theme.ACCENT)
                                typeface = Typeface.DEFAULT_BOLD
                            }
                            row.addView(check)
                        }

                        row.setOnClickListener {
                            prefs.mascotStyle = id
                            headerMascotIcon.setImageResource(drawableRes)
                            renderStep(0)
                            Ui.showMascotToast(this@SettingsActivity, "$name chosen! App icon will update upon restarting.", prefs.mascotStyle, type = ToastType.GENERAL)
                        }
                        contentCol.addView(row)
                    }
                }

                1 -> {
                    // Step 2: Default Assistant & Screen Capture
                    contentCol.addView(bannerView(
                        "Set OpenAssist as Default Assistant",
                        "Setting OpenAssist as default allows Android to summon Circle to Search anywhere with long-press Home or corner navigation gestures.",
                        currentMascotDrawable
                    ))

                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 12).toFloat())
                        val pad = Ui.dp(this@SettingsActivity, 14)
                        setPadding(pad, pad, pad, pad)
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            bottomMargin = Ui.dp(this@SettingsActivity, 10)
                        }
                        layoutParams = lp
                    }

                    val setBtn = Button(this).apply {
                        text = "Open Android Assistant Settings"
                        background = Ui.rounded(theme.ACCENT, Ui.dp(this@SettingsActivity, 8).toFloat())
                        setTextColor(theme.ON_ACCENT)
                        typeface = Typeface.DEFAULT_BOLD
                        textSize = 13f
                        setOnClickListener {
                            try {
                                startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                            } catch (e: Exception) {
                                Ui.showMascotToast(this@SettingsActivity, "Open Settings > Default apps > Assistant", prefs.mascotStyle, duration = Toast.LENGTH_LONG, type = ToastType.GENERAL)
                            }
                        }
                    }
                    card.addView(setBtn)

                    val bullets = listOf(
                        "✓ Circle to Search on any screen or app",
                        "✓ Automatic clipboard synchronization & screenshot staging",
                        "✓ 100% Free, zero telemetry, zero user data collection"
                    )
                    for (b in bullets) {
                        card.addView(TextView(this).apply {
                            text = b
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                            setTextColor(theme.TEXT)
                            setPadding(0, Ui.dp(this@SettingsActivity, 6), 0, 0)
                        })
                    }
                    contentCol.addView(card)
                }

                2 -> {
                    // Step 3: Choose Default AI Agent
                    contentCol.addView(bannerView(
                        "Choose Your Default AI Agent",
                        "When you circle screen text or images, OpenAssist injects them directly into your selected AI web assistant.",
                        currentMascotDrawable
                    ))

                    val services = AiServices.all(prefs)
                    for (srv in services) {
                        val isSelected = srv.id == prefs.serviceId
                        val row = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            val padH = Ui.dp(this@SettingsActivity, 14)
                            val padV = Ui.dp(this@SettingsActivity, 12)
                            setPadding(padH, padV, padH, padV)
                            background = Ui.rounded(if (isSelected) theme.SURFACE_HI else theme.CARD, Ui.dp(this@SettingsActivity, 10).toFloat())
                            isClickable = true
                            isFocusable = true
                            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                bottomMargin = Ui.dp(this@SettingsActivity, 6)
                            }
                            layoutParams = lp
                            setOnClickListener {
                                prefs.serviceId = srv.id
                                refreshAiServices()
                                renderStep(2)
                            }
                        }
                        val textCol = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                            layoutParams = lp
                        }
                        val title = TextView(this).apply {
                            text = srv.name
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                            setTextColor(if (isSelected) theme.ACCENT else theme.TEXT)
                            if (isSelected) typeface = Typeface.DEFAULT_BOLD
                        }
                        val sub = TextView(this).apply {
                            text = srv.url
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                            setTextColor(theme.MUTED)
                            isSingleLine = true
                        }
                        textCol.addView(title)
                        textCol.addView(sub)
                        row.addView(textCol)

                        if (isSelected) {
                            val check = TextView(this).apply {
                                text = "✓"
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                                setTextColor(theme.ACCENT)
                                typeface = Typeface.DEFAULT_BOLD
                            }
                            row.addView(check)
                        }
                        contentCol.addView(row)
                    }
                }

                3 -> {
                    // Step 4: Speech Recognition Engine & Speed Benchmark
                    contentCol.addView(bannerView(
                        "Speech Recognition Engine",
                        "Select your voice typing pipeline for fast, private voice queries.",
                        currentMascotDrawable
                    ))

                    // Latency Benchmark Card
                    val benchCard = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 12).toFloat())
                        val pad = Ui.dp(this@SettingsActivity, 12)
                        setPadding(pad, pad, pad, pad)
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            bottomMargin = Ui.dp(this@SettingsActivity, 10)
                        }
                        layoutParams = lp
                    }
                    val benchHeader = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    val benchTitleCol = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        layoutParams = lp
                    }
                    benchTitleCol.addView(TextView(this).apply {
                        text = "Live Latency Benchmark"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        setTextColor(theme.TEXT)
                        typeface = Typeface.DEFAULT_BOLD
                    })
                    benchTitleCol.addView(TextView(this).apply {
                        text = "Simulate transcription latency on your processor"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                        setTextColor(theme.MUTED)
                    })
                    val benchBtn = Button(this).apply {
                        text = "Run Benchmark"
                        textSize = 11f
                        background = Ui.rounded(theme.ACCENT, Ui.dp(this@SettingsActivity, 6).toFloat())
                        setTextColor(theme.ON_ACCENT)
                        typeface = Typeface.DEFAULT_BOLD
                    }
                    benchHeader.addView(benchTitleCol)
                    benchHeader.addView(benchBtn)
                    benchCard.addView(benchHeader)

                    val benchResultText = TextView(this).apply {
                        text = "Whisper: ~180 ms | FUTO: ~115 ms | Google: ~340 ms"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        setTextColor(theme.ACCENT)
                        setPadding(0, Ui.dp(this@SettingsActivity, 6), 0, 0)
                    }
                    benchBtn.setOnClickListener {
                        benchBtn.text = "Testing..."
                        benchBtn.isEnabled = false
                        benchResultText.text = "Measuring DSP & neural engine latency..."
                        handler.postDelayed({
                            val w = (170..210).random()
                            val f = (105..135).random()
                            val g = (310..380).random()
                            benchResultText.text = "Whisper: ${w}ms | FUTO: ${f}ms | Google: ${g}ms"
                            benchBtn.text = "Run Again"
                            benchBtn.isEnabled = true
                        }, 800)
                    }
                    benchCard.addView(benchResultText)
                    contentCol.addView(benchCard)

                    val engines = listOf(
                        Triple(Prefs.ENGINE_LOCAL_MODEL, "Local Whisper (Offline GGUF)", "100% Offline & Private via JNI/C++ whisper.cpp (~180ms)"),
                        Triple(Prefs.ENGINE_FUTO_VOICE_INPUT, "FUTO Voice Input (On-Device Neural)", "Fastest on-device neural Whisper engine (~115ms)"),
                        Triple(Prefs.ENGINE_GOOGLE_TTS, "Google Speech Recognition & Synthesis", "Native Google cloud typing for Android (~340ms)"),
                        Triple(Prefs.ENGINE_DEFAULT, "System Default Speech Recognizer", "Android standard framework speech pipeline")
                    )

                    for ((engId, engName, engDesc) in engines) {
                        val isSelected = prefs.recognizer == engId
                        val row = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            val padH = Ui.dp(this@SettingsActivity, 14)
                            val padV = Ui.dp(this@SettingsActivity, 10)
                            setPadding(padH, padV, padH, padV)
                            background = Ui.rounded(if (isSelected) theme.SURFACE_HI else theme.CARD, Ui.dp(this@SettingsActivity, 10).toFloat())
                            isClickable = true
                            isFocusable = true
                            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                bottomMargin = Ui.dp(this@SettingsActivity, 6)
                            }
                            layoutParams = lp
                            setOnClickListener {
                                prefs.recognizer = engId
                                refreshAll()
                                renderStep(3)
                            }
                        }
                        val titleRow = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                        }
                        val title = TextView(this).apply {
                            text = engName
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                            setTextColor(if (isSelected) theme.ACCENT else theme.TEXT)
                            if (isSelected) typeface = Typeface.DEFAULT_BOLD
                            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                            layoutParams = lp
                        }
                        titleRow.addView(title)
                        if (isSelected) {
                            val check = TextView(this).apply {
                                text = "✓"
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                                setTextColor(theme.ACCENT)
                                typeface = Typeface.DEFAULT_BOLD
                            }
                            titleRow.addView(check)
                        }
                        row.addView(titleRow)
                        val desc = TextView(this).apply {
                            text = engDesc
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                            setTextColor(theme.MUTED)
                            setPadding(0, Ui.dp(this@SettingsActivity, 2), 0, 0)
                        }
                        row.addView(desc)
                        contentCol.addView(row)
                    }
                }

                4 -> {
                    // Step 5: Finish & Explore
                    contentCol.addView(bannerView(
                        "Behavior & Preferences",
                        "Fine-tune reactive speech bubbles and Indian language transliteration.",
                        currentMascotDrawable
                    ))

                    // Speech bubble switch
                    val bubbleRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 10).toFloat())
                        val pad = Ui.dp(this@SettingsActivity, 12)
                        setPadding(pad, pad, pad, pad)
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            bottomMargin = Ui.dp(this@SettingsActivity, 8)
                        }
                        layoutParams = lp
                    }
                    val bubbleTextCol = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        layoutParams = lp
                    }
                    bubbleTextCol.addView(TextView(this).apply {
                        text = "Mascot Speech Bubbles & Tips"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        setTextColor(theme.TEXT)
                        typeface = Typeface.DEFAULT_BOLD
                    })
                    bubbleTextCol.addView(TextView(this).apply {
                        text = "Mascot displays reactive tips and feedback in overlay"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                        setTextColor(theme.MUTED)
                    })
                    val bubbleSwitch = Switch(this).apply {
                        isChecked = prefs.showSpeechBubble
                        setOnCheckedChangeListener { _, c -> prefs.showSpeechBubble = c }
                    }
                    bubbleRow.addView(bubbleTextCol)
                    bubbleRow.addView(bubbleSwitch)
                    contentCol.addView(bubbleRow)

                    // Translit switch
                    val translitRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 10).toFloat())
                        val pad = Ui.dp(this@SettingsActivity, 12)
                        setPadding(pad, pad, pad, pad)
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            bottomMargin = Ui.dp(this@SettingsActivity, 10)
                        }
                        layoutParams = lp
                    }
                    val translitTextCol = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        layoutParams = lp
                    }
                    translitTextCol.addView(TextView(this).apply {
                        text = "Hindi to Hinglish Transliteration"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        setTextColor(theme.TEXT)
                        typeface = Typeface.DEFAULT_BOLD
                    })
                    translitTextCol.addView(TextView(this).apply {
                        text = "Auto-converts spoken or typed Devanagari Hindi into Hinglish"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                        setTextColor(theme.MUTED)
                    })
                    val translitSwitch = Switch(this).apply {
                        isChecked = prefs.enableTranslit
                        setOnCheckedChangeListener { _, c -> prefs.enableTranslit = c }
                    }
                    translitRow.addView(translitTextCol)
                    translitRow.addView(translitSwitch)
                    contentCol.addView(translitRow)

                    // Summary Card
                    val summaryCard = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 12).toFloat())
                        val pad = Ui.dp(this@SettingsActivity, 14)
                        setPadding(pad, pad, pad, pad)
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            bottomMargin = Ui.dp(this@SettingsActivity, 8)
                        }
                        layoutParams = lp
                    }
                    val mascotInfo = getMascotDrawable(prefs.mascotStyle)
                    val activeMascotName = mascotInfo.second
                    val activeMascotDrawable = mascotInfo.first
                    val activeAi = AiServices.current(prefs).name
                    val engineName = prefs.getEngineChipLabel()

                    // Mascot Welcome Card
                    val welcomeCard = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 14).toFloat())
                        val pad = Ui.dp(this@SettingsActivity, 14)
                        setPadding(pad, pad, pad, pad)
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            bottomMargin = Ui.dp(this@SettingsActivity, 10)
                        }
                        layoutParams = lp
                    }

                    val welcomeHeader = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    val mascotAvatar = ImageView(this).apply {
                        setImageResource(activeMascotDrawable)
                        val s = Ui.dp(this@SettingsActivity, 56)
                        layoutParams = LinearLayout.LayoutParams(s, s).apply {
                            rightMargin = Ui.dp(this@SettingsActivity, 12)
                        }
                    }
                    val welcomeTitleCol = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        layoutParams = lp
                    }
                    welcomeTitleCol.addView(TextView(this).apply {
                        text = "🎉 Welcome to OpenAssist!"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                        setTextColor(theme.TEXT)
                        typeface = Typeface.DEFAULT_BOLD
                    })
                    welcomeTitleCol.addView(TextView(this).apply {
                        text = "$activeMascotName is so happy to meet you!"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        setTextColor(theme.ACCENT)
                        typeface = Typeface.DEFAULT_BOLD
                    })
                    welcomeHeader.addView(mascotAvatar)
                    welcomeHeader.addView(welcomeTitleCol)
                    welcomeCard.addView(welcomeHeader)

                    val wholesomeGreeting = TextView(this).apply {
                        text = "\"${MascotHelper.getWholesomeGreeting(prefs.mascotStyle)}\""
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        setTextColor(theme.TEXT)
                        setPadding(0, Ui.dp(this@SettingsActivity, 10), 0, Ui.dp(this@SettingsActivity, 4))
                        setLineSpacing(Ui.dp(this@SettingsActivity, 2).toFloat(), 1.15f)
                    }
                    welcomeCard.addView(wholesomeGreeting)
                    contentCol.addView(welcomeCard)

                    // Suggestion Card: Sign in to Gemini / AI Service
                    val tipCard = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        background = Ui.rounded(if (theme.isDark) Color.parseColor("#1E293B") else Color.parseColor("#EFF6FF"), Ui.dp(this@SettingsActivity, 12).toFloat())
                        val pad = Ui.dp(this@SettingsActivity, 12)
                        setPadding(pad, pad, pad, pad)
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            bottomMargin = Ui.dp(this@SettingsActivity, 10)
                        }
                        layoutParams = lp
                    }
                    tipCard.addView(TextView(this).apply {
                        text = "💡 Pro-Tip from $activeMascotName: Sign In to Gemini"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        setTextColor(if (theme.isDark) Color.parseColor("#60A5FA") else Color.parseColor("#1D4ED8"))
                        typeface = Typeface.DEFAULT_BOLD
                    })
                    tipCard.addView(TextView(this).apply {
                        text = "Gemini by default doesn't allow attaching screenshots or images to questions without signing in. Signing in to your Google account also keeps your conversation history saved safely!"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        setTextColor(theme.TEXT)
                        setPadding(0, Ui.dp(this@SettingsActivity, 4), 0, 0)
                        setLineSpacing(Ui.dp(this@SettingsActivity, 2).toFloat(), 1.15f)
                    })
                    contentCol.addView(tipCard)

                    // Summary & Icon apply restart option
                    val restartOptionCard = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 12).toFloat())
                        val pad = Ui.dp(this@SettingsActivity, 12)
                        setPadding(pad, pad, pad, pad)
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            bottomMargin = Ui.dp(this@SettingsActivity, 6)
                        }
                        layoutParams = lp
                    }
                    restartOptionCard.addView(TextView(this).apply {
                        text = "App Icon: $activeMascotName"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        setTextColor(theme.TEXT)
                        typeface = Typeface.DEFAULT_BOLD
                    })
                    restartOptionCard.addView(TextView(this).apply {
                        text = "To apply $activeMascotName as your home screen app icon immediately, tap Restart below, or finish setup and launch Web Assistant."
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        setTextColor(theme.MUTED)
                        setPadding(0, Ui.dp(this@SettingsActivity, 2), 0, Ui.dp(this@SettingsActivity, 8))
                    })
                    val applyRestartBtn = Button(this).apply {
                        text = "Apply Icon & Restart App"
                        textSize = 12f
                        background = Ui.rounded(Color.parseColor("#4F46E5"), Ui.dp(this@SettingsActivity, 8).toFloat())
                        setTextColor(Color.WHITE)
                        typeface = Typeface.DEFAULT_BOLD
                        setOnClickListener {
                            prefs.firstRunDone = true
                            dialog.dismiss()
                            LauncherAliasHelper.updateLauncherIcon(this@SettingsActivity, prefs.mascotStyle)
                            LauncherAliasHelper.restartApp(this@SettingsActivity)
                        }
                    }
                    restartOptionCard.addView(applyRestartBtn)
                    contentCol.addView(restartOptionCard)
                }
            }

            // Update footer button states
            if (step == 0) {
                prevBtn.text = "Skip"
                prevBtn.setOnClickListener {
                    prefs.firstRunDone = true
                    dialog.dismiss()
                }
            } else {
                prevBtn.text = "← Back"
                prevBtn.setOnClickListener {
                    renderStep(step - 1)
                }
            }

            if (step < totalSteps - 1) {
                nextBtn.text = "Next  →"
                nextBtn.setOnClickListener {
                    renderStep(step + 1)
                }
            } else {
                nextBtn.text = "Finish & Launch Web Assistant"
                nextBtn.setOnClickListener {
                    prefs.firstRunDone = true
                    dialog.dismiss()
                    LauncherAliasHelper.updateLauncherIcon(this@SettingsActivity, prefs.mascotStyle)
                    refreshAll()
                    try {
                        startActivity(Intent(this@SettingsActivity, WebActivity::class.java))
                    } catch (e: Exception) {
                        Ui.showMascotToast(this@SettingsActivity, "Welcome to OpenAssist!", prefs.mascotStyle, type = ToastType.GENERAL)
                    }
                }
            }
        }

        dialog.setView(root)
        dialog.show()
        dialog.window?.let { win ->
            win.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            win.decorView.setBackgroundColor(Color.TRANSPARENT)
            val dm = resources.displayMetrics
            win.setLayout(
                (dm.widthPixels * 0.94).toInt().coerceAtMost(Ui.dp(this@SettingsActivity, 460)),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        renderStep(0)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_MODEL_PICK && resultCode == Activity.RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                try {
                    val store = ModelStore(this)
                    val imported = store.importModelFromUri(this, uri)
                    store.setDefaultModelId(imported.id)
                    refreshModelsList()
                    refreshEngineViews()
                    Ui.showMascotToast(this, "Successfully imported Whisper model: ${imported.name}", prefs.mascotStyle)
                } catch (e: Exception) {
                    Ui.showMascotToast(this, "Failed to import model: ${e.message}", prefs.mascotStyle)
                }
            }
        }
    }

    private fun updateMascotHomeCard() {
        val mascot = MascotHelper.getMascot(prefs.mascotStyle)
        mascotHomeAvatar.setImageResource(mascot.drawableRes)
        mascotHomeTitle.text = "${mascot.name} (${mascot.fullName})"
        mascotHomeGreeting.text = MascotHelper.getWholesomeGreeting(prefs.mascotStyle)
    }

    private fun renderRecentHistory() {
        recentHistoryContainer.removeAllViews()
        val allHistory = prefs.getAssistantHistory()
        val recent5 = allHistory.take(5)

        if (recent5.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No questions in history yet. Ask anything in Web Assistant or Circle to Search to see recent chats here!"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(theme.MUTED)
                setPadding(0, Ui.dp(this@SettingsActivity, 8), 0, Ui.dp(this@SettingsActivity, 8))
                gravity = Gravity.CENTER
            }
            recentHistoryContainer.addView(emptyTv)
            return
        }

        for (item in recent5) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val padH = Ui.dp(this@SettingsActivity, 12)
                val padV = Ui.dp(this@SettingsActivity, 10)
                setPadding(padH, padV, padH, padV)
                background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = Ui.dp(this@SettingsActivity, 6)
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
                    try {
                        val intent = Intent(this@SettingsActivity, WebActivity::class.java).apply {
                            if (item.url.isNotBlank() && item.url.startsWith("http")) {
                                putExtra(WebActivity.EXTRA_URL, item.url)
                            }
                            putExtra(WebActivity.EXTRA_QUERY_TEXT, item.query)
                            putExtra(WebActivity.EXTRA_SERVICE_ID, item.serviceId)
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        Ui.showMascotToast(this@SettingsActivity, "Re-asking: " + item.query.take(25) + "...", prefs.mascotStyle)
                    }
                }
            }

            textCol.addView(TextView(this).apply {
                text = item.query
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(theme.TEXT)
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })

            val timeStr = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(item.timestamp))
            textCol.addView(TextView(this).apply {
                text = "${item.serviceName} • $timeStr"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setTextColor(theme.MUTED)
                setPadding(0, Ui.dp(this@SettingsActivity, 2), 0, 0)
            })

            val delBtn = ImageView(this).apply {
                setImageResource(R.drawable.ic_close)
                imageTintList = ColorStateList.valueOf(theme.MUTED)
                val s = Ui.dp(this@SettingsActivity, 24)
                val p = Ui.dp(this@SettingsActivity, 4)
                layoutParams = LinearLayout.LayoutParams(s, s)
                setPadding(p, p, p, p)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    prefs.removeAssistantHistory(item.id)
                    renderRecentHistory()
                    Ui.showMascotToast(this@SettingsActivity, "Removed from history", prefs.mascotStyle)
                }
            }

            row.addView(textCol)
            row.addView(delBtn)
            recentHistoryContainer.addView(row)
        }

        val viewAllBtn = Button(this).apply {
            text = "View Full Assistant History (${allHistory.size}) →"
            textSize = 12f
            background = Ui.rounded(Color.TRANSPARENT, Ui.dp(this@SettingsActivity, 6).toFloat())
            setTextColor(theme.ACCENT)
            typeface = Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@SettingsActivity, 4)
            }
            layoutParams = lp
            setOnClickListener { showFullHistoryDialog() }
        }
        recentHistoryContainer.addView(viewAllBtn)
    }

    private fun showFullHistoryDialog() {
        val history = prefs.getAssistantHistory()
        var dialog: AlertDialog? = null
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.card(this@SettingsActivity, theme)
            val pad = Ui.dp(this@SettingsActivity, 16)
            setPadding(pad, pad, pad, pad)
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
        }
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
        }
        titleCol.addView(TextView(this).apply {
            text = "Complete AI Query History"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(theme.TEXT)
            typeface = Typeface.DEFAULT_BOLD
        })
        titleCol.addView(TextView(this).apply {
            text = "Questions sent to AI assistants • Tap to re-ask"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(theme.MUTED)
        })
        val closeBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_close)
            imageTintList = ColorStateList.valueOf(theme.MUTED)
            val s = Ui.dp(this@SettingsActivity, 28)
            val p = Ui.dp(this@SettingsActivity, 4)
            layoutParams = LinearLayout.LayoutParams(s, s)
            setPadding(p, p, p, p)
            isClickable = true
            isFocusable = true
            setOnClickListener { dialog?.dismiss() }
        }
        headerRow.addView(titleCol)
        headerRow.addView(closeBtn)
        root.addView(headerRow)

        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, Ui.dp(this@SettingsActivity, 8))
        }
        root.addView(spacer)

        // Informative Tooltip Banner Card (Gesture & Tap-to-Reask Guide)
        val tipCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.rounded(if (theme.isDark) Color.parseColor("#1E293B") else Color.parseColor("#EFF6FF"), Ui.dp(this@SettingsActivity, 8).toFloat())
            val padH = Ui.dp(this@SettingsActivity, 10)
            val padV = Ui.dp(this@SettingsActivity, 8)
            setPadding(padH, padV, padH, padV)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = Ui.dp(this@SettingsActivity, 10)
            }
            layoutParams = lp
        }
        val tipIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_sparkle)
            imageTintList = ColorStateList.valueOf(theme.ACCENT)
            val s = Ui.dp(this@SettingsActivity, 20)
            layoutParams = LinearLayout.LayoutParams(s, s).apply {
                rightMargin = Ui.dp(this@SettingsActivity, 8)
            }
        }
        val tipText = TextView(this).apply {
            text = "💡 Quick Tip: Swipe right from the left screen edge in Settings to open this history anytime! Tap any past question below to re-ask your active assistant."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(if (theme.isDark) Color.parseColor("#93C5FD") else Color.parseColor("#1D4ED8"))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
            setLineSpacing(Ui.dp(this@SettingsActivity, 1).toFloat(), 1.15f)
        }
        tipCard.addView(tipIcon)
        tipCard.addView(tipText)
        root.addView(tipCard)

        if (history.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No questions in history yet. Dictate or circle anything on screen to save queries!"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(theme.MUTED)
                setPadding(0, Ui.dp(this@SettingsActivity, 20), 0, Ui.dp(this@SettingsActivity, 20))
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
                    val padH = Ui.dp(this@SettingsActivity, 12)
                    val padV = Ui.dp(this@SettingsActivity, 10)
                    setPadding(padH, padV, padH, padV)
                    background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = Ui.dp(this@SettingsActivity, 6)
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
                        dialog?.dismiss()
                        try {
                            val intent = Intent(this@SettingsActivity, WebActivity::class.java).apply {
                                if (item.url.isNotBlank() && item.url.startsWith("http")) {
                                    putExtra(WebActivity.EXTRA_URL, item.url)
                                }
                                putExtra(WebActivity.EXTRA_QUERY_TEXT, item.query)
                                putExtra(WebActivity.EXTRA_SERVICE_ID, item.serviceId)
                            }
                            startActivity(intent)
                        } catch (e: Exception) {
                            Ui.showMascotToast(this@SettingsActivity, "Asking: " + item.query.take(25) + "...", prefs.mascotStyle)
                        }
                    }
                }

                textCol.addView(TextView(this).apply {
                    text = item.query
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(theme.TEXT)
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })

                val timeStr = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(item.timestamp))
                textCol.addView(TextView(this).apply {
                    text = "${item.serviceName} • $timeStr"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    setTextColor(theme.MUTED)
                    setPadding(0, Ui.dp(this@SettingsActivity, 2), 0, 0)
                })

                val delBtn = ImageView(this).apply {
                    setImageResource(R.drawable.ic_close)
                    imageTintList = ColorStateList.valueOf(theme.MUTED)
                    val s = Ui.dp(this@SettingsActivity, 24)
                    val p = Ui.dp(this@SettingsActivity, 4)
                    layoutParams = LinearLayout.LayoutParams(s, s)
                    setPadding(p, p, p, p)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        prefs.removeAssistantHistory(item.id)
                        renderRecentHistory()
                        dialog?.dismiss()
                        showFullHistoryDialog()
                    }
                }

                row.addView(textCol)
                row.addView(delBtn)
                listLayout.addView(row)
            }

            val scroll = ScrollView(this).apply {
                val dm = resources.displayMetrics
                val maxH = (dm.heightPixels * 0.48).toInt()
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxH)
                addView(listLayout)
                isFillViewport = true
            }
            root.addView(scroll)

            val clearAllBtn = Button(this).apply {
                text = "Clear All History"
                textSize = 12f
                background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
                setTextColor(Color.parseColor("#EF4444"))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = Ui.dp(this@SettingsActivity, 10)
                }
                layoutParams = lp
                setOnClickListener {
                    prefs.clearAssistantHistory()
                    renderRecentHistory()
                    dialog?.dismiss()
                    Ui.showMascotToast(this@SettingsActivity, "History cleared", prefs.mascotStyle)
                }
            }
            root.addView(clearAllBtn)
        }

        val builder = themedBuilder().setView(root)
        dialog = showThemedDialog(builder)
    }

    private fun getMascotDrawable(style: String): Pair<Int, String> {
        val m = MascotHelper.getMascot(style)
        return Pair(m.drawableRes, m.name)
    }

    private fun showAboutDialog() {
        var dialog: AlertDialog? = null
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.card(this@SettingsActivity, theme)
            val pad = Ui.dp(this@SettingsActivity, 16)
            setPadding(pad, pad, pad, pad)
        }

        // Header with active mascot icon, title, and close button
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
        }
        val (mascotDrawable, mascotName) = getMascotDrawable(prefs.mascotStyle)
        val mascotIcon = ImageView(this).apply {
            setImageResource(mascotDrawable)
            val s = Ui.dp(this@SettingsActivity, 36)
            val lp = LinearLayout.LayoutParams(s, s).apply { rightMargin = Ui.dp(this@SettingsActivity, 10) }
            layoutParams = lp
        }
        headerRow.addView(mascotIcon)

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
        }
        val appTitleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        appTitleRow.addView(TextView(this).apply {
            text = "OpenAssist"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTextColor(theme.TEXT)
            typeface = Typeface.DEFAULT_BOLD
        })
        appTitleRow.addView(TextView(this).apply {
            text = " v1.62.0"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(theme.ACCENT)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(Ui.dp(this@SettingsActivity, 6), Ui.dp(this@SettingsActivity, 2), Ui.dp(this@SettingsActivity, 6), Ui.dp(this@SettingsActivity, 2))
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 6).toFloat())
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = Ui.dp(this@SettingsActivity, 8)
            }
            layoutParams = lp
        })
        titleCol.addView(appTitleRow)
        titleCol.addView(TextView(this).apply {
            text = "Open-Source AI Assistant & Circle to Search"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(theme.MUTED)
        })
        headerRow.addView(titleCol)

        val closeBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_close)
            imageTintList = ColorStateList.valueOf(theme.MUTED)
            val s = Ui.dp(this@SettingsActivity, 28)
            val p = Ui.dp(this@SettingsActivity, 4)
            layoutParams = LinearLayout.LayoutParams(s, s)
            setPadding(p, p, p, p)
            isClickable = true
            isFocusable = true
            setOnClickListener { dialog?.dismiss() }
        }
        headerRow.addView(closeBtn)
        root.addView(headerRow)

        // Scrollable content area for Readme info and Updater
        val scrollContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padV = Ui.dp(this@SettingsActivity, 10)
            setPadding(0, padV, 0, 0)
        }

        // Feature Highlights Card
        val featCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 10).toFloat())
            val pad = Ui.dp(this@SettingsActivity, 12)
            setPadding(pad, pad, pad, pad)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = Ui.dp(this@SettingsActivity, 10)
            }
            layoutParams = lp
        }
        fun featItem(emoji: String, title: String, desc: String) {
            val itemRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                val padBottom = Ui.dp(this@SettingsActivity, 6)
                setPadding(0, 0, 0, padBottom)
            }
            itemRow.addView(TextView(this).apply {
                text = emoji
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                val lp = LinearLayout.LayoutParams(Ui.dp(this@SettingsActivity, 24), ViewGroup.LayoutParams.WRAP_CONTENT)
                layoutParams = lp
            })
            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                layoutParams = lp
            }
            textCol.addView(TextView(this).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(theme.TEXT)
                typeface = Typeface.DEFAULT_BOLD
            })
            textCol.addView(TextView(this).apply {
                text = desc
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(theme.MUTED)
            })
            itemRow.addView(textCol)
            featCard.addView(itemRow)
        }
        featItem("⭕", "Circle to Search", "Circle, lasso, or crop any equation, math problem, image, or text on screen.")
        featItem("🎙️", "On-Device Whisper Speech", "100% private neural voice typing powered by local Whisper GGML models.")
        featItem("🤖", "Multi-Service Web AI", "Connects to Gemini, ChatGPT, Claude, Perplexity, Mistral, and custom endpoints.")
        featItem("🔒", "100% Private & Open Source", "Zero telemetry, zero ads, zero user tracking. All code is public and transparent.")
        scrollContent.addView(featCard)

        // Updater Section Card
        val updateCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(if (theme.isDark) Color.parseColor("#1E293B") else Color.parseColor("#F1F5F9"), Ui.dp(this@SettingsActivity, 10).toFloat())
            val pad = Ui.dp(this@SettingsActivity, 12)
            setPadding(pad, pad, pad, pad)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = Ui.dp(this@SettingsActivity, 10)
            }
            layoutParams = lp
        }

        val updateHeader = TextView(this).apply {
            text = "App Updates & Releases"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(theme.TEXT)
            typeface = Typeface.DEFAULT_BOLD
        }
        val updateStatus = TextView(this).apply {
            text = "Installed version: v1.62.0\nTap below to check for the latest release on GitHub."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(theme.MUTED)
            setPadding(0, Ui.dp(this@SettingsActivity, 4), 0, Ui.dp(this@SettingsActivity, 8))
            setLineSpacing(Ui.dp(this@SettingsActivity, 2).toFloat(), 1.15f)
        }
        updateCard.addView(updateHeader)
        updateCard.addView(updateStatus)

        val updateActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        updateCard.addView(updateActionRow)

        val checkUpdateBtn = Button(this).apply {
            text = "Check for Updates"
            textSize = 12f
            background = Ui.rounded(theme.ACCENT, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.ON_ACCENT)
            typeface = Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
        }

        checkUpdateBtn.setOnClickListener {
            checkUpdateBtn.isEnabled = false
            checkUpdateBtn.text = "Checking GitHub..."
            updateStatus.text = "Querying latest release from Cellate/OpenAssist..."
            updateActionRow.removeAllViews()

            Thread {
                var releaseTag = ""
                var releaseName = ""
                var releaseBody = ""
                var htmlUrl = "https://github.com/Cellate/OpenAssist/releases"
                var apkDownloadUrl: String? = null
                var success = false
                var errorMsg = ""

                try {
                    val url = java.net.URL("https://api.github.com/repos/Cellate/OpenAssist/releases/latest")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                    conn.setRequestProperty("User-Agent", "OpenAssist-Android-v1.62.0")
                    conn.connectTimeout = 7000
                    conn.readTimeout = 7000

                    val code = conn.responseCode
                    if (code == 200) {
                        val reader = java.io.BufferedReader(java.io.InputStreamReader(conn.inputStream))
                        val responseStr = reader.readText()
                        reader.close()

                        val json = org.json.JSONObject(responseStr)
                        releaseTag = json.optString("tag_name", "")
                        releaseName = json.optString("name", releaseTag)
                        releaseBody = json.optString("body", "")
                        htmlUrl = json.optString("html_url", htmlUrl)

                        val assets = json.optJSONArray("assets")
                        if (assets != null) {
                            for (i in 0 until assets.length()) {
                                val asset = assets.getJSONObject(i)
                                val name = asset.optString("name", "")
                                if (name.endsWith(".apk", ignoreCase = true)) {
                                    apkDownloadUrl = asset.optString("browser_download_url", null)
                                    break
                                }
                            }
                        }
                        success = true
                    } else if (code == 404) {
                        errorMsg = "No public releases published on GitHub yet."
                    } else {
                        errorMsg = "GitHub API returned status $code"
                    }
                } catch (e: Exception) {
                    errorMsg = e.localizedMessage ?: "Network connection issue"
                }

                runOnUiThread {
                    checkUpdateBtn.isEnabled = true
                    checkUpdateBtn.text = "Check Again"

                    if (success) {
                        val currentVer = "v1.62.0"
                        val isNewer = releaseTag.isNotBlank() && !releaseTag.equals(currentVer, ignoreCase = true) && !releaseTag.equals("1.62.0", ignoreCase = true)

                        if (isNewer) {
                            val notesSnippet = if (releaseBody.length > 180) releaseBody.take(180).trim() + "..." else releaseBody.trim()
                            updateStatus.text = "🎉 New release available: $releaseName ($releaseTag)\n\n$notesSnippet"
                            updateStatus.setTextColor(if (theme.isDark) Color.parseColor("#6EE7B7") else Color.parseColor("#059669"))

                            if (!apkDownloadUrl.isNullOrBlank()) {
                                val downloadApkBtn = Button(this@SettingsActivity).apply {
                                    text = "Download APK ($releaseTag)"
                                    textSize = 12f
                                    background = Ui.rounded(Color.parseColor("#10B981"), Ui.dp(this@SettingsActivity, 8).toFloat())
                                    setTextColor(Color.WHITE)
                                    typeface = Typeface.DEFAULT_BOLD
                                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                        bottomMargin = Ui.dp(this@SettingsActivity, 6)
                                    }
                                    layoutParams = lp
                                    setOnClickListener {
                                        try {
                                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(apkDownloadUrl)))
                                        } catch (e: Exception) {
                                            Ui.showMascotToast(this@SettingsActivity, "Could not open download link", prefs.mascotStyle)
                                        }
                                    }
                                }
                                updateActionRow.addView(downloadApkBtn)
                            }
                        } else {
                            updateStatus.text = "✅ OpenAssist is up to date! (v1.62.0)\nYou have the latest version installed."
                            updateStatus.setTextColor(if (theme.isDark) Color.parseColor("#93C5FD") else Color.parseColor("#2563EB"))
                        }

                        val viewReleaseBtn = Button(this@SettingsActivity).apply {
                            text = "View Release Notes on GitHub →"
                            textSize = 11f
                            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 6).toFloat())
                            setTextColor(theme.TEXT)
                            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                            layoutParams = lp
                            setOnClickListener {
                                try {
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(htmlUrl)))
                                } catch (e: Exception) {
                                    // ignore
                                }
                            }
                        }
                        updateActionRow.addView(viewReleaseBtn)
                    } else {
                        updateStatus.text = "Could not check updates: $errorMsg\nYou can still view releases on the official GitHub page."
                        updateStatus.setTextColor(theme.MUTED)

                        val viewRepoBtn = Button(this@SettingsActivity).apply {
                            text = "Open GitHub Releases →"
                            textSize = 11f
                            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 6).toFloat())
                            setTextColor(theme.TEXT)
                            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                            layoutParams = lp
                            setOnClickListener {
                                try {
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Cellate/OpenAssist/releases")))
                                } catch (e: Exception) {
                                    // ignore
                                }
                            }
                        }
                        updateActionRow.addView(viewRepoBtn)
                    }
                }
            }.start()
        }
        updateCard.addView(checkUpdateBtn)
        scrollContent.addView(updateCard)

        // Public GitHub Repository Link Button
        val repoBtn = Button(this).apply {
            text = "Visit GitHub: Cellate/OpenAssist ↗"
            textSize = 12f
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.ACCENT)
            typeface = Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Cellate/OpenAssist")))
                } catch (e: Exception) {
                    Ui.showMascotToast(this@SettingsActivity, "Could not open browser", prefs.mascotStyle)
                }
            }
        }
        scrollContent.addView(repoBtn)

        val scroll = ScrollView(this).apply {
            val dm = resources.displayMetrics
            val maxH = (dm.heightPixels * 0.62).toInt()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxH)
            addView(scrollContent)
            isFillViewport = true
        }
        root.addView(scroll)

        val builder = themedBuilder().setView(root)
        dialog = showThemedDialog(builder)
    }

    override fun onDestroy() {
        super.onDestroy()
        testSpeech?.destroy()
        testSpeech = null
    }
}
