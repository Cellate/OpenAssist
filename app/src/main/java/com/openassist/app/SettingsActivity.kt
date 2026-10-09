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
import android.widget.EditText
import android.widget.HorizontalScrollView
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

    private val RC_MODEL_PICK = 42
    private val RC_TEST_SPEECH = 43
    private val RC_MIC_PERMISSION = 101

    private var testSpeech: Speech? = null
    private var isTesting = false
    private var isRecordingLocal = false
    private var isTranscribing = false
    private var baseTestTextBeforeSpeech = ""

    private fun safeString(resId: Int, fallback: String): String {
        return try {
            getString(resId)
        } catch (t: Throwable) {
            fallback
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            initUi()
        } catch (t: Throwable) {
            android.util.Log.e("SettingsActivity", "Fatal error initializing SettingsActivity", t)
            renderSafeFallbackUi(t)
        }
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
            text = "OpenAssist recovered safely from a startup configuration issue:\n${error.localizedMessage ?: error.javaClass.simpleName}"
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
                    Toast.makeText(this@SettingsActivity, "Could not launch WebActivity", Toast.LENGTH_SHORT).show()
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

        // Apply system bars safely after content view is established
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

        fun toggle(title: String, desc: String, initial: Boolean, onChange: (Boolean) -> Unit) {
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
            col.addView(row)
        }

        // --- 0. Appearance / Theme Section ---
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

        // --- 1. Default Assistant Section ---
        header(safeString(R.string.st_assistant, "Digital assistant"))
        val assistantCard = cardLayout()
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
                    Toast.makeText(this@SettingsActivity, "Open Settings > Default apps > Assistant", Toast.LENGTH_LONG).show()
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
        col.addView(assistantCard)

        // --- 1B. AI Web Assistant & Custom AI Services ---
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

        aiCard.addView(TextView(this).apply {
            text = "Select any built-in assistant (Gemini, ChatGPT, Claude, Perplexity, DeepSeek, Grok, Mistral) or add your own custom web assistant (Ollama, Open WebUI, or newly released AI models)."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(theme.MUTED)
            setPadding(0, Ui.dp(this@SettingsActivity, 8), 0, Ui.dp(this@SettingsActivity, 6))
        })

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
        aiActionsRow.addView(openActiveAiBtn)
        aiCard.addView(aiActionsRow)
        col.addView(aiCard)

        // --- 2. Voice Input Engine & Whisper Models ---
        header("Voice Input Engine & Whisper Local Mode")

        // 2A: Main Voice Input Engine Selector Card
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

        // 2B: Local Multi-Model Management (WhisperModelPicker v2 logic)
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

        // List of all loaded models
        localModelsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, Ui.dp(this@SettingsActivity, 8), 0, 0)
        }
        modelHeaderCard.addView(localModelsContainer)

        // Model Actions: Load from storage or pre-load recommended Hinglish model
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

        // 2B.2: Processing Mode Switcher (Post-Processing vs Real-Time)
        val modeCard = cardLayout()
        modeCard.addView(TextView(this).apply {
            text = "Whisper Processing Mode"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(theme.TEXT)
        })
        modeCard.addView(TextView(this).apply {
            text = "Post-Processing (Default) records audio first, then runs Whisper for maximum model accuracy and stability without dropped words. Switch to Real-Time for live streaming words."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(theme.MUTED)
            setPadding(0, Ui.dp(this@SettingsActivity, 2), 0, Ui.dp(this@SettingsActivity, 8))
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
                Toast.makeText(this@SettingsActivity, "Set to Post-Processing Mode (Default)", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this@SettingsActivity, "Set to Real-Time Streaming Mode", Toast.LENGTH_SHORT).show()
            }
        }
        modeSwitchRow.addView(postProcBtn)
        modeSwitchRow.addView(realTimeBtn)
        modeCard.addView(modeSwitchRow)
        col.addView(modeCard)

        // 2C: Per-Language Model Binding (Hinglish default, Hindi secondary, English)
        subheader("Per-Language Model Binding")
        val langMapCard = cardLayout()
        langMapCard.addView(TextView(this).apply {
            text = "Assign specific Whisper models for each language:"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(theme.MUTED)
            setPadding(0, 0, 0, Ui.dp(this@SettingsActivity, 8))
        })
        langMappingContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        langMapCard.addView(langMappingContainer)
        col.addView(langMapCard)

        // 2D: Live Testing Sandbox with Testing Text Box & Privacy Listening Indicator
        header("Test Voice Dictation & Transcription")
        val testCard = cardLayout()

        // Language toggle chips for testing
        testCard.addView(TextView(this).apply {
            text = "Target Language (Dictation will transliterate/format accordingly):"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(theme.MUTED)
        })

        val langScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, Ui.dp(this@SettingsActivity, 6), 0, Ui.dp(this@SettingsActivity, 8))
        }
        langChipsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        langScroll.addView(langChipsRow)
        testCard.addView(langScroll)

        // Pipeline info banner
        pipelineInfoText = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(if (theme.isDark) Color.parseColor("#93C5FD") else Color.parseColor("#1D4ED8"))
            background = Ui.rounded(if (theme.isDark) Color.parseColor("#1E293B") else Color.parseColor("#EFF6FF"), Ui.dp(this@SettingsActivity, 6).toFloat())
            val pad = Ui.dp(this@SettingsActivity, 8)
            setPadding(pad, pad, pad, pad)
        }
        testCard.addView(pipelineInfoText)

        // Privacy Live Listening Status Banner
        testStatusBanner = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#EF4444"))
            typeface = Typeface.DEFAULT_BOLD
            visibility = View.GONE
            val pad = Ui.dp(this@SettingsActivity, 8)
            setPadding(pad, pad, pad, pad)
            text = "  LIVE MIC ACTIVE   Listening... Speak now (Audio is never stored)"
        }
        testCard.addView(testStatusBanner)

        // Multi-line Testing Text Box
        testTextBox = EditText(this).apply {
            hint = "Recognized transcription will appear here in real time... You can also test typing or editing here."
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

        // Test Action Controls: Tap to Speak / Clear / Copy
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
                    Toast.makeText(this@SettingsActivity, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
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

        // 2E: FUTO Helpers Card
        val futoCard = cardLayout()
        futoCard.addView(TextView(this).apply {
            text = "FUTO Voice Input & Keyboard (Offline Option)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(theme.TEXT)
        })

        val futoSettingsBtn = Button(this).apply {
            text = "Configure FUTO Keyboard Voice Settings"
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.TEXT)
            setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                } catch (e: Exception) {
                    val launch = packageManager.getLaunchIntentForPackage("org.futo.inputmethod.latin")
                        ?: packageManager.getLaunchIntentForPackage("org.futo.voiceinput")
                    if (launch != null) startActivity(launch)
                    else Toast.makeText(this@SettingsActivity, "Open Keyboard settings", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val getFutoBtn = Button(this).apply {
            text = "Get FUTO Voice Input (Free Offline Whisper APK)"
            background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 8).toFloat())
            setTextColor(theme.ACCENT)
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://voiceinput.futo.org/")))
                } catch (e: Exception) {
                    Toast.makeText(this@SettingsActivity, "Visit https://voiceinput.futo.org/", Toast.LENGTH_SHORT).show()
                }
            }
        }

        futoCard.addView(futoSettingsBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(this@SettingsActivity, 6) })

        futoCard.addView(getFutoBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(this@SettingsActivity, 6) })
        col.addView(futoCard)

        // --- 3. Behaviour Settings ---
        header(safeString(R.string.st_behaviour, "Behaviour"))
        toggle("Auto-Listen on Launch", "Immediately open microphone when overlay opens so you can speak right away.", prefs.autoStartMic) { prefs.autoStartMic = it }
        toggle(safeString(R.string.st_new_chat, "Start a new chat each time"), safeString(R.string.st_new_chat_desc, "Reloads the AI page on every assist so your question is not mixed into an old chat. Turn off to keep the current chat."), prefs.newChatOnAssist) { prefs.newChatOnAssist = it }
        toggle(safeString(R.string.st_auto_attach, "Attach circled image automatically"), safeString(R.string.st_auto_attach_desc, "Pastes the copied selection into the chat box after the page opens."), prefs.autoAttach) { prefs.autoAttach = it }
        toggle(safeString(R.string.st_auto_submit, "Send the question automatically"), safeString(R.string.st_auto_submit_desc, "After you type or dictate in the overlay, press the chat's send button for you."), prefs.autoSubmit) { prefs.autoSubmit = it }
        toggle(safeString(R.string.st_circle_compose, "Stay in the overlay after circling"), safeString(R.string.st_circle_compose_desc, "Lets you type or dictate your question right after circling. Off: jump straight to the AI so you can paste and ask there."), prefs.composeAfterCircle) { prefs.composeAfterCircle = it }

        // --- 4. About OpenAssist ---
        header(safeString(R.string.st_about, "About OpenAssist"))
        val aboutCard = cardLayout()
        aboutCard.addView(TextView(this).apply {
            text = "OpenAssist v0.2.0   Offline Multi-Model Whisper Engine\nPrivacy First: Audio stays on your device when using Local or FUTO modes."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(theme.MUTED)
        })
        col.addView(aboutCard)

        // Now that all UI elements are instantiated, refresh all view states safely!
        try {
            updateModeButtonsUi()
            refreshAll()
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

        customAiContainer.addView(TextView(this).apply {
            text = "Custom Web Assistants (${customServices.size}):"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(if (theme.isDark) Color.parseColor("#93C5FD") else Color.parseColor("#1D4ED8"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, Ui.dp(this@SettingsActivity, 6), 0, Ui.dp(this@SettingsActivity, 4))
        })

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
                    Toast.makeText(this@SettingsActivity, "Activated ${cs.name}", Toast.LENGTH_SHORT).show()
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
                    AlertDialog.Builder(this@SettingsActivity)
                        .setTitle("Remove ${cs.name}")
                        .setMessage("Remove this custom AI assistant URL from OpenAssist?")
                        .setPositiveButton("Remove") { _, _ ->
                            prefs.removeCustomService(cs.id)
                            refreshAiServices()
                            Toast.makeText(this@SettingsActivity, "Removed ${cs.name}", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            row.addView(deleteBtn)
            customAiContainer.addView(row)
        }
    }

    private fun showAiServiceSelectorDialog() {
        val allServices = AiServices.all(prefs)
        val names = allServices.map {
            if (it.id == prefs.serviceId) "  ${it.name} (Active)" else it.name
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select AI Web Assistant")
            .setItems(names) { _, which ->
                val chosen = allServices[which]
                prefs.serviceId = chosen.id
                refreshAiServices()
                Toast.makeText(this, "Active Assistant: ${chosen.name}", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("+ Add Custom AI") { _, _ ->
                showAddCustomAiDialog()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddCustomAiDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = Ui.dp(this@SettingsActivity, 16)
            setPadding(pad, pad, pad, pad)
        }
        val nameInput = EditText(this).apply {
            hint = "AI Name (e.g. Open WebUI, Ollama, Grok 3)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(theme.TEXT)
            setHintTextColor(theme.MUTED)
        }
        val urlInput = EditText(this).apply {
            hint = "Web URL (e.g. https://... or http://192.168.1.x:3000)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(theme.TEXT)
            setHintTextColor(theme.MUTED)
        }
        layout.addView(nameInput)
        val sp = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, Ui.dp(this@SettingsActivity, 8)) }
        layout.addView(sp)
        layout.addView(urlInput)

        AlertDialog.Builder(this)
            .setTitle("Add Custom AI Web Assistant")
            .setView(layout)
            .setPositiveButton("Add & Select") { _, _ ->
                val name = nameInput.text.toString().trim()
                val url = urlInput.text.toString().trim()
                if (url.isNotBlank()) {
                    val newService = prefs.addCustomService(name, url)
                    refreshAiServices()
                    Toast.makeText(this, "Added & Selected ${newService.name}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Please enter a valid URL", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
                text = "No custom Whisper models loaded yet. Tap 'Load Model Binary' or 'Add Hinglish Demo' to add your offline .bin/.onnx model."
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
                text = (if (isDefault) "* " else "") + m.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(if (isDefault) (if (theme.isDark) Color.parseColor("#93C5FD") else Color.parseColor("#1D4ED8")) else theme.TEXT)
                typeface = if (isDefault) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            })
            val details = StringBuilder()
            if (m.sizeMb.isNotBlank()) details.append(m.sizeMb)
            if (isDefault) details.append(" - Default Active Model")
            infoCol.addView(TextView(this).apply {
                text = details.toString()
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(theme.MUTED)
            })
            row.addView(infoCol)

            // Select / Active Button
            val selectBtn = Button(this).apply {
                text = if (isDefault) "Active" else "Select"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                background = Ui.rounded(if (isDefault) Color.parseColor("#2563EB") else theme.SURFACE, Ui.dp(this@SettingsActivity, 6).toFloat())
                setTextColor(if (isDefault) Color.WHITE else theme.TEXT)
                setOnClickListener {
                    modelStore.setDefaultModelId(m.id)
                    modelStore.setModelIdForLang(VoiceLang.HINGLISH, m.id)
                    modelStore.setModelIdForLang(VoiceLang.HINDI, m.id)
                    modelStore.setModelIdForLang(VoiceLang.ENGLISH, m.id)
                    modelStore.setModelIdForLang(prefs.voiceLang, m.id)
                    prefs.recognizer = Prefs.ENGINE_LOCAL_MODEL
                    refreshAll()
                    Toast.makeText(this@SettingsActivity, "Activated ${m.name}", Toast.LENGTH_SHORT).show()
                }
            }
            row.addView(selectBtn)

            // Delete button (if more than 1 model loaded)
            if (models.size > 1) {
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
                        confirmDeleteModel(m)
                    }
                }
                row.addView(deleteBtn)
            }
            localModelsContainer.addView(row)
        }
    }

    private fun confirmDeleteModel(m: WhisperModel) {
        AlertDialog.Builder(this)
            .setTitle("Remove Model")
            .setMessage("Do you want to remove ${m.name} from the loaded models list?")
            .setPositiveButton("Remove") { _, _ ->
                modelStore.removeModel(m.id)
                refreshAll()
                Toast.makeText(this, "Removed ${m.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun cycleToNextModel() {
        val next = modelStore.cycleModel()
        if (next != null) {
            modelStore.setModelIdForLang(VoiceLang.HINGLISH, next.id)
            modelStore.setModelIdForLang(VoiceLang.HINDI, next.id)
            modelStore.setModelIdForLang(VoiceLang.ENGLISH, next.id)
            modelStore.setModelIdForLang(prefs.voiceLang, next.id)
            prefs.recognizer = Prefs.ENGINE_LOCAL_MODEL
            refreshAll()
            Toast.makeText(this, "Switched to: ${next.name}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No models loaded to switch", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshLangMappings() {
        if (!::langMappingContainer.isInitialized) return
        langMappingContainer.removeAllViews()
        val models = modelStore.getModels()

        val langs = listOf(
            Triple(VoiceLang.HINGLISH, "Hinglish (Default)", "English script for Hindi speech (e.g., 'kya haal hai')"),
            Triple(VoiceLang.HINDI, "Hindi (Secondary)", "Devanagari script (e.g., 'नमस्ते')"),
            Triple(VoiceLang.ENGLISH, "English", "Standard English speech dictation")
        )

        for ((lang, title, desc) in langs) {
            val assignedId = modelStore.getModelIdForLang(lang)
            val assignedModel = modelStore.getModel(assignedId) ?: modelStore.getDefaultModel()
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val pad = Ui.dp(this@SettingsActivity, 8)
                setPadding(0, pad, 0, pad)
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
            textCol.addView(TextView(this).apply {
                text = desc
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(theme.MUTED)
            })
            val modelLabel = TextView(this).apply {
                text = "Model: ${assignedModel?.name ?: "Default Model"}"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(if (theme.isDark) Color.parseColor("#93C5FD") else Color.parseColor("#1D4ED8"))
            }
            textCol.addView(modelLabel)
            row.addView(textCol)

            val chooseBtn = Button(this).apply {
                text = "Change"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                background = Ui.rounded(theme.SURFACE_HI, Ui.dp(this@SettingsActivity, 6).toFloat())
                setTextColor(theme.ACCENT)
                setOnClickListener {
                    showLangModelPickerDialog(lang)
                }
            }
            row.addView(chooseBtn)
            langMappingContainer.addView(row)
        }
    }

    private fun showLangModelPickerDialog(lang: VoiceLang) {
        val models = modelStore.getModels()
        if (models.isEmpty()) {
            Toast.makeText(this, "Load a model binary first", Toast.LENGTH_SHORT).show()
            return
        }
        val names = models.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select Whisper Model for ${lang.label}")
            .setItems(names) { _, which ->
                val chosen = models[which]
                modelStore.setModelIdForLang(lang, chosen.id)
                refreshLangMappings()
                refreshPipelineInfo()
                Toast.makeText(this, "Set ${chosen.name} for ${lang.chip}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    rightMargin = Ui.dp(this@SettingsActivity, 8)
                }
                layoutParams = lp
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
        val pm = packageManager
        val list = try {
            pm.queryIntentServices(Intent("android.speech.RecognitionService"), PackageManager.MATCH_ALL)
        } catch (e: Exception) {
            emptyList()
        }

        val names = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()

        // 1. System Default
        names.add("System Default Speech Recognizer")
        actions.add {
            prefs.recognizer = Prefs.ENGINE_DEFAULT
            refreshAll()
            Toast.makeText(this, "Using System Default Voice Recognizer", Toast.LENGTH_SHORT).show()
        }

        // 2. Local Model Mode
        val currentModel = prefs.getActiveLocalModel()
        val localLabel = if (currentModel != null) {
            "Local Model Mode (Offline Whisper: ${currentModel.name})"
        } else {
            "Local Model Mode (Offline Multi-Model Whisper)"
        }
        names.add(localLabel)
        actions.add {
            prefs.recognizer = Prefs.ENGINE_LOCAL_MODEL
            refreshAll()
            Toast.makeText(this, "Local Model Mode activated", Toast.LENGTH_SHORT).show()
        }

        // 3. Google Speech Recognition & Synthesis
        names.add("Google Speech Recognition & Synthesis (Google TTS / Oppo)")
        actions.add {
            prefs.recognizer = Prefs.ENGINE_GOOGLE_TTS
            refreshAll()
            Toast.makeText(this, "Google Speech Services activated", Toast.LENGTH_SHORT).show()
        }

        // 4. FUTO Voice Input
        names.add("FUTO Voice Input / Keyboard (Offline Whisper)")
        actions.add {
            prefs.recognizer = Prefs.ENGINE_FUTO_VOICE_INPUT
            refreshAll()
            Toast.makeText(this, "FUTO Voice Input activated", Toast.LENGTH_SHORT).show()
        }

        // 5. Installed recognition services on device
        for (info in list) {
            val s = info.serviceInfo ?: continue
            if (s.packageName == packageName || s.packageName == "com.google.android.as") continue
            val cn = android.content.ComponentName(s.packageName, s.name).flattenToString()
            if (cn == prefs.recognizer) continue
            val label = info.loadLabel(pm).toString()
            names.add(label)
            actions.add {
                prefs.recognizer = cn
                refreshAll()
                Toast.makeText(this, "Selected $label", Toast.LENGTH_SHORT).show()
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Select Voice Input Source")
            .setItems(names.toTypedArray()) { _, which ->
                actions[which].invoke()
            }
            .show()
    }

    private fun pickCustomModel() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/octet-stream", "application/x-binary", "application/*", "*/*"
            ))
        }
        try {
            startActivityForResult(intent, RC_MODEL_PICK)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open file picker", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleTestSpeech() {
        if (isTesting) {
            if (isRecordingLocal && prefs.isPostProcessing) {
                // In post-processing mode, tapping the button stops recording and executes transcription
                stopLocalRecordingAndTranscribe()
            } else {
                stopTestSpeech()
            }
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
        isTranscribing = false
        val activeModel = prefs.getActiveLocalModel()
        val engineLabel = prefs.getEngineChipLabel()
        val lang = prefs.voiceLang
        val isPost = prefs.isPostProcessing && prefs.isLocalModelMode
        isRecordingLocal = prefs.isLocalModelMode

        testMicBtn.isEnabled = true
        if (isPost) {
            testMicBtn.text = "Stop & Transcribe"
        } else {
            testMicBtn.text = "Stop Listening"
        }
        testMicBtn.background = Ui.rounded(Color.parseColor("#EF4444"), Ui.dp(this, 8).toFloat())
        testMicBtn.setTextColor(Color.WHITE)
        testStatusBanner.visibility = View.VISIBLE

        if (prefs.isLocalModelMode) {
            testStatusBanner.text = if (isPost) {
                "RECORDING AUDIO ($engineLabel) -> Speak now! Tap 'Stop & Transcribe' when done."
            } else {
                "LIVE MIC ACTIVE ($engineLabel) -> Listening for ${lang.label}... Speak now!"
            }
        } else {
            testStatusBanner.text = "CONNECTING MIC ($engineLabel)..."
        }

        baseTestTextBeforeSpeech = testTextBox.text.toString().trim()

        val speech = Speech(this, prefs, object : Speech.Callback {
            override fun onStarting(engineName: String) {
                if (isTranscribing) {
                    testStatusBanner.text = "TRANSCRIBING ($engineName)..."
                } else if (!prefs.isLocalModelMode) {
                    testStatusBanner.text = "CONNECTING MIC ($engineName)..."
                }
            }

            override fun onReadyForSpeech(engineName: String) {
                if (isPost) {
                    testStatusBanner.text = "RECORDING AUDIO ($engineName) -> Speak now! Tap 'Stop & Transcribe' when done."
                } else {
                    testStatusBanner.text = "LIVE MIC ACTIVE ($engineName) -> Listening for ${lang.label}... Speak now!"
                }
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (isTesting && !isTranscribing) {
                    val bars = " ▂▃▄▅▆▇█"
                    val idx = ((rmsdB / 100f) * bars.length).toInt().coerceIn(0, bars.length - 1)
                    val symbol = bars[idx]
                    if (isPost) {
                        testStatusBanner.text = "RECORDING [$symbol] ($engineLabel) -> Speak now! Tap 'Stop & Transcribe' when done."
                    }
                }
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
                isTranscribing = false
                val out = text.trim()
                val prefix = baseTestTextBeforeSpeech.trim()
                val combined = if (out.isEmpty()) prefix else if (prefix.isEmpty()) out else "$prefix $out"
                testTextBox.setText(combined)
                testTextBox.setSelection(combined.length)
                baseTestTextBeforeSpeech = combined
                stopTestSpeech()
                Toast.makeText(this@SettingsActivity, "Transcription complete!", Toast.LENGTH_SHORT).show()
            }

            override fun onError(message: String) {
                isTranscribing = false
                testStatusBanner.text = "Microphone ended: $message"
                handler.postDelayed({ testStatusBanner.visibility = View.GONE }, 3500)
                stopTestSpeech()
                Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_SHORT).show()
            }

            override fun onStateChanged(listening: Boolean, engineName: String) {
                if (!listening && !isRecordingLocal && !isTranscribing) {
                    stopTestSpeech()
                }
            }

            override fun onLaunchSpeechIntent(intent: Intent) {
                try {
                    startActivityForResult(intent, RC_TEST_SPEECH)
                } catch (e: Exception) {
                    onFutoNotFound()
                }
            }

            override fun onFutoNotFound() {
                stopTestSpeech()
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("FUTO Voice Input Not Found")
                    .setMessage("FUTO Voice Input APK is not installed yet. Would you like to download it, or use Google Speech?")
                    .setPositiveButton("Get FUTO (Free APK)") { _, _ ->
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://voiceinput.futo.org/")))
                        } catch (e: Exception) {}
                    }
                    .setNeutralButton("Use Google Speech") { _, _ ->
                        prefs.recognizer = Prefs.ENGINE_GOOGLE_TTS
                        refreshAll()
                        startTestSpeech()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            override fun onKeyboardVoiceRequested(modelName: String?) {
                stopTestSpeech()
                Toast.makeText(this@SettingsActivity, "Keyboard voice mode active. Tap mic on keyboard to dictate.", Toast.LENGTH_SHORT).show()
            }
        })
        testSpeech = speech
        speech.start()
    }

    private fun stopLocalRecordingAndTranscribe() {
        isTranscribing = true
        testMicBtn.isEnabled = false
        testMicBtn.text = "Transcribing..."
        testMicBtn.background = Ui.rounded(Color.parseColor("#3B82F6"), Ui.dp(this, 8).toFloat())
        testStatusBanner.text = "STOPPED RECORDING -> Processing audio with Whisper..."
        testSpeech?.stop()
    }

    private fun stopTestSpeech() {
        isTesting = false
        isRecordingLocal = false
        isTranscribing = false
        testMicBtn.isEnabled = true
        testMicBtn.text = "Tap to Speak / Test"
        testMicBtn.background = Ui.rounded(theme.ACCENT, Ui.dp(this, 8).toFloat())
        testMicBtn.setTextColor(theme.ON_ACCENT)
        testStatusBanner.visibility = View.GONE
        testSpeech?.stop()
        testSpeech = null
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_TEST_SPEECH && resultCode == RESULT_OK && data != null) {
            val matches = data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            val raw = matches?.firstOrNull()?.trim() ?: ""
            val out = if (prefs.voiceLang == VoiceLang.HINGLISH) Translit.toHinglish(raw) else raw
            val current = testTextBox.text.toString().trim()
            val combined = when {
                current.isEmpty() -> out
                current == out || current.endsWith(out) || current.contains(out) -> current
                else -> "$current $out"
            }
            testTextBox.setText(combined)
            testTextBox.setSelection(testTextBox.text.length)
            baseTestTextBeforeSpeech = combined
            stopTestSpeech()
            return
        }
        if (requestCode == RC_MODEL_PICK && resultCode == RESULT_OK && data != null) {
            val uri = data.data ?: return
            val takeFlags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            try {
                contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {}

            var fileName = "whisper-custom-model.bin"
            var fileSize = 0L
            try {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex >= 0) fileName = cursor.getString(nameIndex) ?: fileName
                        if (sizeIndex >= 0) fileSize = cursor.getLong(sizeIndex)
                    }
                }
            } catch (e: Exception) {}

            Toast.makeText(this, "Importing $fileName into offline storage...", Toast.LENGTH_SHORT).show()
            Thread({
                val newModel = modelStore.importModelFromUri(this@SettingsActivity, uri, fileName, fileSize)
                prefs.recognizer = Prefs.ENGINE_LOCAL_MODEL
                handler.post {
                    refreshAll()
                    Toast.makeText(this@SettingsActivity, "Loaded and activated: ${newModel.name}", Toast.LENGTH_LONG).show()
                }
            }).start()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_MIC_PERMISSION) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                startTestSpeech()
            } else {
                Toast.makeText(this, "Microphone permission required for voice test", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        testSpeech?.destroy()
        testSpeech = null
    }
}
